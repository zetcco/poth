package dev.indrajith.poth.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.indrajith.poth.R

/** Pauses or resumes tracking reading; it shows which is on, accented while paused. */
@Composable
fun TrackingButton(paused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(if (paused) R.drawable.ic_timer_off else R.drawable.ic_timer),
            contentDescription = if (paused) "Reading not tracked: resume" else "Reading tracked: pause",
            tint = if (paused) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
        )
    }
}

/** Says that reading is not being tracked, with the way to start again. */
@Composable
fun TrackingPaused(onResume: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_timer_off), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                "Tracking paused: pages read now aren't counted",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp),
            )
            TextButton(onClick = onResume) { Text("Resume") }
        }
    }
}

/** Asks before tracking is paused ([pause]) or resumed. */
@Composable
fun ConfirmTracking(pause: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pause) "Pause tracking?" else "Resume tracking?") },
        text = {
            Text(
                if (pause) {
                    "Pages you read won't be counted in your reading stats until you resume."
                } else {
                    "Pages you read will be counted in your reading stats again."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (pause) "Pause" else "Resume") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
