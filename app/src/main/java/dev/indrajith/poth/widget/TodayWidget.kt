package dev.indrajith.poth.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.indrajith.poth.MainActivity
import dev.indrajith.poth.R
import dev.indrajith.poth.graph
import dev.indrajith.poth.stats.ReadingSummary
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.TextStyle as DateStyle

private val HorizontalPadding = 16.dp
private val VerticalPadding = 12.dp
private val HeaderHeight = 32.dp
private val LetterHeight = 14.dp

/** Bars shorter than this would say nothing; below it the widget is only today's minutes and the streak. */
private val MinBarSpace = 12.dp

/**
 * Today's minutes and the streak over the last 7 days as bars, each as tall as that day was
 * read and today in the light shade on the right: no goal, the days around today are what it
 * stands against. Tapping it opens the stats.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        // Far enough back for a long streak.
        val yearAgo = System.currentTimeMillis() - 400L * 24 * 60 * 60 * 1000
        val summary = withContext(Dispatchers.IO) { context.graph().readingSummary(zone, since = yearAgo) }
        val today = LocalDate.now(zone)
        provideContent { Content(summary, today) }
    }

    @Composable
    private fun Content(summary: ReadingSummary, today: LocalDate) {
        val context = LocalContext.current
        val size = LocalSize.current
        val minutes = summary.minutesOn(today)
        val streak = summary.streak(today).current
        val open = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_STATS)

        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(ColorProvider(R.color.widget_surface))
                .padding(horizontal = HorizontalPadding, vertical = VerticalPadding)
                .clickable(actionStartActivity(open)),
        ) {
            Row(GlanceModifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                val hours = minutes / 60
                val (big, small) = when {
                    hours == 0L -> "$minutes" to "min today"
                    minutes % 60 == 0L -> "$hours h" to "today"
                    else -> "$hours h ${minutes % 60}" to "today"
                }
                Text(big, style = TextStyle(color = ColorProvider(R.color.widget_on_surface), fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                // Nudged down to sit on the big number's baseline.
                Text(
                    small,
                    style = TextStyle(color = ColorProvider(R.color.widget_on_surface_variant), fontSize = 14.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 5.dp, top = 6.dp),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (streak > 0) {
                    Image(
                        ImageProvider(R.drawable.ic_streak),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(R.color.widget_heat_3)),
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Text(
                        "$streak",
                        style = TextStyle(color = ColorProvider(R.color.widget_on_surface_variant), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .padding(start = 3.dp)
                            .semantics { contentDescription = if (streak == 1) "1 day streak" else "$streak day streak" },
                    )
                }
            }
            val barSpace = size.height - VerticalPadding * 2 - HeaderHeight - 6.dp - 2.dp - LetterHeight
            if (barSpace >= MinBarSpace) {
                Spacer(GlanceModifier.height(6.dp))
                Bars(summary, today, barSpace, size.width - HorizontalPadding * 2)
            }
        }
    }

    /** The last 7 days, today on the right: a bar each, as tall as it was read, over the day's letter. */
    @Composable
    private fun ColumnScope.Bars(summary: ReadingSummary, today: LocalDate, barSpace: Dp, width: Dp) {
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        // Scaled to the most read day, but a short one doesn't fill the height.
        val scale = max(30L, days.maxOf(summary::minutesOn)).toFloat()
        val barWidth = min(24.dp.value, width.value / 7 * 0.55f).dp
        val described = days.joinToString(", ") { "${it.dayOfWeek.getDisplayName(DateStyle.FULL, Locale.getDefault())} ${summary.minutesOn(it)} minutes" }
        Row(
            GlanceModifier.fillMaxWidth().defaultWeight().semantics { contentDescription = "Minutes read the last 7 days: $described" },
            verticalAlignment = Alignment.Bottom,
        ) {
            for (date in days) {
                val minutes = summary.minutesOn(date)
                val isToday = date == today
                Column(
                    GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val height = if (minutes == 0L) 2.dp else max(3f, barSpace.value * minutes / scale).dp
                    Box(
                        GlanceModifier
                            .width(barWidth)
                            .height(height)
                            .cornerRadius(if (minutes == 0L) 1.dp else 4.dp)
                            .background(
                                ColorProvider(
                                    when {
                                        minutes == 0L -> R.color.widget_heat_0
                                        isToday -> R.color.widget_heat_4
                                        else -> R.color.widget_heat_2
                                    },
                                ),
                            ),
                    ) {}
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        date.dayOfWeek.getDisplayName(DateStyle.NARROW, Locale.getDefault()),
                        style = TextStyle(
                            color = ColorProvider(if (isToday) R.color.widget_on_surface else R.color.widget_on_surface_variant),
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.height(LetterHeight),
                    )
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
