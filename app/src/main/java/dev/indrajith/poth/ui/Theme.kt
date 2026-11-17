package dev.indrajith.poth.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
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
 * background is pure black so the screens around the reader match its page. Motion is the
 * standard scheme's: quicker than the expressive one, and without its bounce.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PothTheme(content: @Composable () -> Unit) {
    val colors = dynamicDarkColorScheme(LocalContext.current)
        .copy(background = Page.background, surface = Page.background)
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.standard(), content = content)
}

// Bars and panels come and go with the theme's fast motion: sliding by [offset] (from their
// height) and fading, or only fading.

@Composable
fun quickSlideIn(offset: (height: Int) -> Int): EnterTransition =
    slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec(), offset) + quickFadeIn()

@Composable
fun quickSlideOut(offset: (height: Int) -> Int): ExitTransition =
    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec(), offset) + quickFadeOut()

@Composable
fun quickFadeIn(): EnterTransition = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec())

@Composable
fun quickFadeOut(): ExitTransition = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
