package dev.indrajith.poth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.indrajith.poth.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture in the notes, as wide as it fits and no taller than a third of a phone; tapping it shows it whole. */
@Composable
fun NoteImage(name: String, description: String, modifier: Modifier = Modifier) {
    val width = LocalWindowInfo.current.containerSize.width
    val picture = notePicture(name, width)
    var viewing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    when (val shown = picture.value) {
        Picture.Loading -> Box(modifier.fillMaxWidth().height(160.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Picture.Missing -> Text(
            "Picture missing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(16.dp),
        )
        is Picture.Shown -> Image(
            shown.bitmap,
            contentDescription = description.ifEmpty { "Picture" },
            contentScale = ContentScale.Fit,
            modifier = modifier
                .heightIn(max = 360.dp)
                .aspectRatio(shown.ratio)
                .clip(shape)
                .clickable { viewing = true },
        )
    }
    if (viewing) ImageViewer(name, description) { viewing = false }
}

private sealed interface Picture {
    data object Loading : Picture
    data object Missing : Picture
    class Shown(bitmap: Bitmap) : Picture {
        val bitmap = bitmap.asImageBitmap()
        val ratio = bitmap.width / bitmap.height.toFloat()
    }
}

/** The picture [name] for [width] pixels, read off the main thread. */
@Composable
private fun notePicture(name: String, width: Int): State<Picture> {
    val images = LocalContext.current.graph().images
    return produceState<Picture>(Picture.Loading, name, width) {
        value = withContext(Dispatchers.IO) { images.load(name, width)?.let(Picture::Shown) } ?: Picture.Missing
    }
}

/**
 * The picture over the whole screen, on black: pinch or double-tap to zoom in, drag to look
 * around; a tap or Back closes it.
 */
@Composable
private fun ImageViewer(name: String, description: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val picture = notePicture(name, LocalWindowInfo.current.containerSize.width * 2)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClose() },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = picture.value as? Picture.Shown ?: return@Box
            Image(
                shown.bitmap,
                contentDescription = description.ifEmpty { "Picture" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}
