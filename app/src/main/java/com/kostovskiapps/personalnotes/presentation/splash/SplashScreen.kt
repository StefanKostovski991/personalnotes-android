package com.kostovskiapps.personalnotes.presentation.splash

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kostovskiapps.personalnotes.presentation.components.NotesLogo
import com.kostovskiapps.personalnotes.presentation.theme.LocalNotesGradients
import com.kostovskiapps.personalnotes.presentation.theme.NotesPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The animated splash.
 *
 * A three stage entrance (mark, title, tagline) over the signature yellow gradient, plus two
 * slow "sunbeam" sweeps in the background. Everything animates on the UI thread with
 * [Animatable]: no third-party animation library, and the timings are chosen to finish inside
 * [TotalDurationMillis] so the user never waits on the animation.
 */
object SplashTiming {
    const val TotalDurationMillis: Long = 1_750L
}

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradients = LocalNotesGradients.current

    val markScale = remember { Animatable(0.55f) }
    val markAlpha = remember { Animatable(0f) }
    val markRotation = remember { Animatable(-14f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(28f) }
    val taglineAlpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0.15f) }

    val breathe = rememberInfiniteTransition(label = "breathe")
    val breatheScale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = EaseOutCubic), RepeatMode.Reverse),
        label = "breatheScale",
    )
    val sweep by breathe.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    LaunchedEffect(Unit) {
        launch {
            markAlpha.animateTo(1f, tween(520, easing = EaseOutCubic))
        }
        launch {
            markScale.animateTo(1f, tween(760, easing = EaseOutBack))
        }
        launch {
            markRotation.animateTo(0f, tween(760, easing = EaseOutBack))
        }
        launch {
            delay(320)
            titleAlpha.animateTo(1f, tween(460, easing = EaseOutCubic))
        }
        launch {
            delay(320)
            titleOffset.animateTo(0f, tween(520, easing = EaseOutCubic))
        }
        launch {
            delay(620)
            taglineAlpha.animateTo(1f, tween(420, easing = EaseOutCubic))
        }
        launch {
            glow.animateTo(0.55f, tween(1_200, easing = EaseOutCubic))
        }
        // Hand over slightly before the last animation ends so the transition feels seamless.
        delay(SplashTiming.TotalDurationMillis - 260)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradients.signature),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative light sweeps.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.35f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(1f, 1f),
                    ),
                )
                .graphicsLayer { translationX = sweep * 900f },
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(markScale.value * breatheScale)
                    .rotate(markRotation.value)
                    .alpha(markAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                // Warm disc behind the mark so the logo sits on the gradient the way the
                // launcher icon sits on its background.
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(42.dp))
                        .background(NotesPalette.Caramel.copy(alpha = 0.20f + glow.value * 0.4f)),
                )
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(NotesPalette.HoneyDeep, NotesPalette.Caramel),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    NotesLogo(size = 92.dp)
                }
            }

            Text(
                text = "Personal Notes",
                style = MaterialTheme.typography.displaySmall,
                color = NotesPalette.Ink,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(0, titleOffset.value.toInt()) }
                    .alpha(titleAlpha.value),
            )

            Text(
                text = "Write it. Say it. Keep it.",
                style = MaterialTheme.typography.titleMedium,
                color = NotesPalette.Ink.copy(alpha = 0.72f),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .alpha(taglineAlpha.value),
            )
        }
    }
}

/** Guard for effects that are only available on newer platform versions. */
internal fun supportsAdvancedBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
