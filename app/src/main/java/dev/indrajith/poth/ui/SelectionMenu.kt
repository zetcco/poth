package dev.indrajith.poth.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.R
import dev.indrajith.poth.actions.QuickAction
import dev.indrajith.poth.actions.QuickActions
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What can be done with highlighted words, beside them, as a row of icons: write about them in
 * the dictionary ([addLabel] names it), copy them, or look them up with a quick action. Above
 * [anchor], the words on the screen, if there is room; below them and their handles if not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionMenu(
    anchor: Rect,
    addLabel: String,
    @DrawableRes addIcon: Int,
    actions: List<QuickAction>,
    actionStore: QuickActions,
    onAdd: () -> Unit,
    onAction: (QuickAction) -> Unit,
    onCopy: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(140)) }
    val statusBar = WindowInsets.statusBarsIgnoringVisibility
    Layout(
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier.graphicsLayer {
                    alpha = appear.value
                    scaleX = 0.92f + 0.08f * appear.value
                    scaleY = scaleX
                },
            ) {
                // One row of icons, each named while held: the dictionary first, then copying,
                // then the quick actions.
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Labelled(addLabel) {
                        FilledTonalIconButton(onClick = onAdd) { Icon(painterResource(addIcon), contentDescription = addLabel) }
                    }
                    Labelled("Copy") {
                        IconButton(onClick = onCopy) { Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy") }
                    }
                    if (actions.isNotEmpty()) {
                        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                        QuickActionRow(actions, actionStore, onAction, onConfigure = null, buttonSize = 44.dp, iconSize = 26.dp)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val handles = 30.dp.roundToPx() // the drops hanging below the last line
        val menu = measurables.first().measure(Constraints(maxWidth = max(0, constraints.maxWidth - 2 * margin), maxHeight = constraints.maxHeight))
        val x = (anchor.center.x - menu.width / 2f).roundToInt().coerceIn(margin, max(margin, constraints.maxWidth - menu.width - margin))
        val above = (anchor.top - gap - menu.height).roundToInt()
        val y = if (above >= statusBar.getTop(this) + margin) {
            above
        } else {
            (anchor.bottom.roundToInt() + handles).coerceAtMost(constraints.maxHeight - menu.height - margin)
        }
        layout(constraints.maxWidth, constraints.maxHeight) { menu.place(x, y) }
    }
}
