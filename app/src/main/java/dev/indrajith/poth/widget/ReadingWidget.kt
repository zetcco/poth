package dev.indrajith.poth.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.indrajith.poth.MainActivity
import dev.indrajith.poth.R
import dev.indrajith.poth.graph
import dev.indrajith.poth.stats.ReadingStats
import dev.indrajith.poth.stats.ReadingSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Padding = 14.dp
private val HeaderHeight = 22.dp

/** Below this height the widget shows one row of recent days instead of the calendar. */
private val CalendarMinHeight = 96.dp

/**
 * Reading days on the home screen, GitHub style. The calendar is drawn as a picture at the
 * widget's exact size (a widget's rows and columns cannot hold a year of days), so it shows as
 * many weeks as fit; one row tall, it shows the last days in a line. Tapping it opens the stats.
 */
class ReadingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
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
                .padding(Padding)
                .clickable(actionStartActivity(open)),
        ) {
            Row(GlanceModifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (minutes > 0) "$minutes min today" else "Not read today",
                    style = TextStyle(color = ColorProvider(R.color.widget_on_surface), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                if (streak > 0) {
                    Text(
                        if (streak == 1) "1 day" else "$streak days",
                        style = TextStyle(color = ColorProvider(R.color.widget_on_surface_variant), fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            val area = DpSize(size.width - Padding * 2, size.height - Padding * 2 - HeaderHeight - 8.dp)
            if (area.width > 0.dp && area.height > 0.dp) {
                val bitmap = render(context, summary, today, area, calendar = size.height >= CalendarMinHeight)
                Image(
                    ImageProvider(bitmap),
                    contentDescription = "Reading days",
                    modifier = GlanceModifier.size(area.width, area.height),
                )
            }
        }
    }
}

class ReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadingWidget()
}

/**
 * The days as rounded squares: a calendar (a week a column, Monday at the top, this week on the
 * right) or, when [calendar] is false, a single row of the last days, today on the right.
 */
private fun render(context: Context, summary: ReadingSummary, today: LocalDate, area: DpSize, calendar: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    fun px(dp: Dp) = dp.value * density
    val width = max(1, px(area.width).toInt())
    val height = max(1, px(area.height).toInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val colors = listOf(
        R.color.widget_heat_0, R.color.widget_heat_1, R.color.widget_heat_2, R.color.widget_heat_3, R.color.widget_heat_4,
    ).map(context::getColor)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val gap = px(3.dp)

    fun square(left: Float, top: Float, cell: Float, date: LocalDate) {
        paint.color = colors[ReadingStats.level(summary.minutesOn(date))]
        val radius = min(px(3.dp), cell / 4)
        canvas.drawRoundRect(RectF(left, top, left + cell, top + cell), radius, radius, paint)
    }

    if (calendar) {
        val cell = min((height - 6 * gap) / 7, px(18.dp))
        val weeks = floor((width + gap) / (cell + gap)).toInt().coerceIn(1, 53)
        val left = width - (weeks * (cell + gap) - gap) // the latest week against the right edge
        val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeks - 1L)
        for (week in 0 until weeks) {
            for (day in 0..6) {
                val date = firstMonday.plusWeeks(week.toLong()).plusDays(day.toLong())
                if (date.isAfter(today)) continue
                square(left + week * (cell + gap), day * (cell + gap), cell, date)
            }
        }
    } else {
        val cell = min(height.toFloat(), px(22.dp))
        val days = floor((width + gap) / (cell + gap)).toInt().coerceAtLeast(1)
        val left = width - (days * (cell + gap) - gap)
        val top = (height - cell) / 2
        for (i in 0 until days) square(left + i * (cell + gap), top, cell, today.minusDays((days - 1 - i).toLong()))
    }
    return bitmap
}
