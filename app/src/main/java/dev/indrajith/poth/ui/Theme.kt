package dev.indrajith.poth.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The reader's page: black, with grey text that is easy on the eyes at night. */
object Page {
    val background = Color.Black
    val text = Color(0xFFC4C4C4)
}

/**
 * Material 3 Expressive, always dark, with the wallpaper's colours (dynamic colour). The
 * background is pure black so the screens around the reader match its page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PothTheme(content: @Composable () -> Unit) {
    val colors = dynamicDarkColorScheme(LocalContext.current)
        .copy(background = Page.background, surface = Page.background)
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
