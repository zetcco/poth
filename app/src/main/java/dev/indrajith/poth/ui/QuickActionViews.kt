package dev.indrajith.poth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.indrajith.poth.R
import dev.indrajith.poth.actions.ActionKind
import dev.indrajith.poth.actions.QuickAction
import dev.indrajith.poth.actions.QuickActions
import dev.indrajith.poth.actions.Templates

/**
 * The quick actions as their apps' icons, and a button to change them. Holding an icon shows
 * the action's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionRow(
    actions: List<QuickAction>,
    store: QuickActions,
    onAction: (QuickAction) -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (action in actions) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(action.label) } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = { onAction(action) }, modifier = Modifier.size(52.dp)) {
                    ActionIcon(action, store, Modifier.size(30.dp), label = action.label)
                }
            }
        }
        IconButton(onClick = onConfigure) { Icon(painterResource(R.drawable.ic_tune), contentDescription = "Change quick actions") }
    }
}

@Composable
private fun ActionIcon(action: QuickAction, store: QuickActions, modifier: Modifier, label: String? = null) {
    val bitmap = remember(action.packageName, action.activity) { store.icon(action)?.toBitmap(120, 120)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = label, modifier = modifier)
    } else {
        Icon(painterResource(if (action.kind == ActionKind.SEARCH) R.drawable.ic_search else R.drawable.ic_share), contentDescription = label, modifier = modifier)
    }
}

/**
 * Changing the quick actions: their names, what each sends (a template), their order, and
 * adding any app that takes text. Saved as they change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionsPanel(open: Boolean, store: QuickActions, onClose: () -> Unit) {
    AnimatedVisibility(
        visible = open,
        enter = slideInVertically { it / 4 } + fadeIn(),
        exit = slideOutVertically { it / 4 } + fadeOut(),
    ) {
        BackHandler(onBack = onClose)
        val colors = MaterialTheme.colorScheme
        // Edited here and saved on every change; not read back, so typing is not disturbed.
        var actions by remember { mutableStateOf(store.actions.value) }
        val targets = remember { store.targets() }
        fun update(new: List<QuickAction>) {
            actions = new
            store.set(new)
        }

        Surface(color = colors.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.navigationBarsIgnoringVisibility))
                    .imePadding(),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                    Text("Quick actions", style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Icons on the dictionary card and editor; hold one to see its name. What an action sends can use " +
                            Templates.placeholders.joinToString(", ") { "{$it}" } + ".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    actions.forEachIndexed { index, action ->
                        Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ActionIcon(action, store, Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(kindLabel(action.kind), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { update(actions.moved(index, index - 1)) }, enabled = index > 0) {
                                        Icon(painterResource(R.drawable.ic_up), contentDescription = "Move up")
                                    }
                                    IconButton(onClick = { update(actions.moved(index, index + 1)) }, enabled = index < actions.lastIndex) {
                                        Icon(painterResource(R.drawable.ic_down), contentDescription = "Move down")
                                    }
                                    IconButton(onClick = { update(actions.filterIndexed { i, _ -> i != index }) }) {
                                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove")
                                    }
                                }
                                OutlinedTextField(
                                    action.label,
                                    { label -> update(actions.replaced(index, action.copy(label = label))) },
                                    label = { Text("Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    action.template,
                                    { template -> update(actions.replaced(index, action.copy(template = template))) },
                                    label = { Text("Sends") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Text("Add", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    val extras = listOf(QuickAction("Web search", ActionKind.SEARCH), QuickAction("Share…", ActionKind.SHEET))
                    for (extra in extras) {
                        AddRow(extra.label, kindLabel(extra.kind)) { update(actions + extra) }
                    }
                    for (target in targets) {
                        AddRow(target.label, kindLabel(target.kind)) {
                            update(actions + QuickAction(target.label, target.kind, target.packageName, target.activity, store.templateFor(target)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRow(label: String, kind: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun kindLabel(kind: ActionKind) = when (kind) {
    ActionKind.SHARE -> "Share to the app"
    ActionKind.TEXT -> "The app's text-selection action"
    ActionKind.SEARCH -> "Web search"
    ActionKind.SHEET -> "Pick an app each time"
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }

private fun <T> List<T>.replaced(index: Int, item: T): List<T> = toMutableList().apply { set(index, item) }
