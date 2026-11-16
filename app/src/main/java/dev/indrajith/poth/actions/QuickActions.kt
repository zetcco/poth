package dev.indrajith.poth.actions

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An app that can take text, offered when adding a quick action. */
data class Target(val label: String, val kind: ActionKind, val packageName: String, val activity: String)

/**
 * The quick actions, kept in SharedPreferences as JSON, and handing text to other apps. The
 * first time, they are made from the assistants and Google apps that are installed.
 */
class QuickActions(private val context: Context) {
    private val packages: PackageManager = context.packageManager
    private val sp = context.getSharedPreferences("poth", Context.MODE_PRIVATE)
    private val _actions = MutableStateFlow(load() ?: defaults())

    val actions: StateFlow<List<QuickAction>> = _actions

    fun set(actions: List<QuickAction>) {
        _actions.value = actions
        sp.edit().putString(KEY, JSONArray(actions.map(::toJson)).toString()).apply()
    }

    /** Every app that takes shared text or has a text-selection action, by name. */
    fun targets(): List<Target> {
        fun query(intent: Intent, kind: ActionKind) = packages.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { Target(it.loadLabel(packages).toString(), kind, it.activityInfo.packageName, it.activityInfo.name) }
        return (query(shareIntent(), ActionKind.SHARE) + query(textIntent(), ActionKind.TEXT))
            .sortedWith(compareBy({ it.label.lowercase() }, { it.kind }))
    }

    /** A sensible template for a new action for [target]: the question for assistants, the word otherwise. */
    fun templateFor(target: Target) = if (target.packageName in ASSISTANTS) Templates.ASK else Templates.WORD

    fun icon(action: QuickAction): Drawable? {
        val pkg = action.packageName ?: return null
        return try {
            if (action.activity != null) {
                packages.getActivityIcon(android.content.ComponentName(pkg, action.activity))
            } else {
                packages.getApplicationIcon(pkg)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Hands [text] to [action]'s app. False if that app is gone. */
    fun launch(from: Context, action: QuickAction, text: String): Boolean {
        val intent = when (action.kind) {
            ActionKind.SHARE -> shareIntent().putExtra(Intent.EXTRA_TEXT, text)
            ActionKind.TEXT -> textIntent()
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            ActionKind.SEARCH -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
            ActionKind.SHEET -> Intent.createChooser(shareIntent().putExtra(Intent.EXTRA_TEXT, text), null)
        }
        val pkg = action.packageName
        if (pkg != null && action.kind != ActionKind.SHEET) {
            if (action.activity != null) intent.setClassName(pkg, action.activity) else intent.setPackage(pkg)
        }
        return try {
            from.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun defaults(): List<QuickAction> {
        val targets = targets()
        fun share(pkg: String) = targets.firstOrNull { it.packageName == pkg && it.kind == ActionKind.SHARE }
        val actions = mutableListOf<QuickAction>()
        for ((pkg, label) in ASSISTANTS) {
            share(pkg)?.let { actions += QuickAction(label, ActionKind.SHARE, pkg, it.activity, Templates.ASK) }
        }
        val search = packages.queryIntentActivities(Intent(Intent.ACTION_WEB_SEARCH), 0)
        if (search.isNotEmpty()) {
            val google = search.firstOrNull { it.activityInfo.packageName == GOOGLE }
            actions += QuickAction("Google", ActionKind.SEARCH, google?.activityInfo?.packageName)
        }
        targets.firstOrNull { it.packageName == TRANSLATE && it.kind == ActionKind.TEXT }?.let {
            actions += QuickAction("Translate", ActionKind.TEXT, it.packageName, it.activity)
        }
        actions += QuickAction("Share…", ActionKind.SHEET)
        return actions
    }

    private fun load(): List<QuickAction>? {
        val json = sp.getString(KEY, null) ?: return null
        return try {
            val array = JSONArray(json)
            List(array.length()) { fromJson(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            null
        }
    }

    private fun toJson(action: QuickAction) = JSONObject()
        .put("label", action.label)
        .put("kind", action.kind.name)
        .put("package", action.packageName)
        .put("activity", action.activity)
        .put("template", action.template)

    private fun fromJson(json: JSONObject) = QuickAction(
        label = json.getString("label"),
        kind = ActionKind.valueOf(json.getString("kind")),
        packageName = json.optString("package").ifEmpty { null },
        activity = json.optString("activity").ifEmpty { null },
        template = json.optString("template", Templates.WORD),
    )

    private fun shareIntent() = Intent(Intent.ACTION_SEND).setType("text/plain")

    private fun textIntent() = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")

    private companion object {
        const val KEY = "quickActions"
        const val GOOGLE = "com.google.android.googlequicksearchbox"
        const val TRANSLATE = "com.google.android.apps.translate"
        val ASSISTANTS = linkedMapOf(
            "com.anthropic.claude" to "Claude",
            "com.google.android.apps.bard" to "Gemini",
            "com.openai.chatgpt" to "ChatGPT",
        )
    }
}
