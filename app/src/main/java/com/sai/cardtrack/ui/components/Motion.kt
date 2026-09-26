package com.sai.cardtrack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.ui.theme.tabular
import kotlinx.coroutines.delay

/** Clickable that squashes on press and springs back. */
fun Modifier.bouncyClick(
    role: Role = Role.Button,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            role = role,
            onClick = onClick
        )
}

/** Fades and lifts the element into place once, staggered by [order]. */
fun Modifier.riseIn(order: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (order in 0 until STAGGER_LIMIT) delay(order * 40L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 28.dp.toPx()
        scaleX = 0.97f + 0.03f * p
        scaleY = 0.97f + 0.03f * p
    }
}

private const val STAGGER_LIMIT = 10

/** A light band that glides across the content every few seconds. */
fun Modifier.sheen(color: Color = Color.White.copy(alpha = 0.07f), periodMillis: Int = 7000): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "sheen")
    val sweep by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = -0.4f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMillis
                -0.4f at 0
                -0.4f at periodMillis - 1800 using FastOutSlowInEasing
                1.4f at periodMillis
            }
        ),
        label = "sweep"
    )
    drawWithContent {
        drawContent()
        val x = size.width * sweep
        val band = size.width * 0.35f
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, color, Color.Transparent),
                start = Offset(x - band, 0f),
                end = Offset(x + band, size.height)
            )
        )
    }
}

/** Odometer text: every changed character rolls up or down into place. */
@Composable
fun RollingText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val up = targetState > initialState
                        val enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { h ->
                            if (up) h else -h
                        } + fadeIn(tween(220))
                        val exit = slideOutVertically(tween(260)) { h -> if (up) -h else h } + fadeOut(tween(180))
                        (enter togetherWith exit).using(SizeTransform(clip = true))
                    },
                    label = "digit"
                ) { shown ->
                    Text(shown.toString(), style = style.tabular(), color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Slow looping phase in radians, read inside draw lambdas to avoid recomposition. */
@Composable
fun rememberLoopPhase(periodMillis: Int, label: String): State<Float> {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = label
    )
}

const val TAU: Float = (Math.PI * 2).toFloat()
