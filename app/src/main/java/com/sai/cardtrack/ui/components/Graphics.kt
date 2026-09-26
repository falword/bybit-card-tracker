package com.sai.cardtrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.R
import com.sai.cardtrack.ui.theme.CardTrackColors
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object GraphicsMath {
    const val BRAND_HUE = 40f

    fun merchantHue(name: String): Float {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return BRAND_HUE
        var hash = 0x811C9DC5.toInt()
        for (c in key) {
            hash = (hash xor c.code) * 0x01000193
        }
        return ((hash.toLong() and 0xFFFFFFFFL) % 360L).toFloat()
    }

    fun inflowShare(income: BigDecimal, expenses: BigDecimal): Float? {
        val inflow = income.abs()
        val total = inflow + expenses.abs()
        if (total.signum() == 0) return null
        return inflow.divide(total, 4, RoundingMode.HALF_UP).toFloat()
    }
}

private val Ember = Color(0xFFFF6A3D)
private val GoldLight = Color(0xFFFFD66B)
private val GoldDeep = Color(0xFFB87900)

/** Shared aurora strength for home and secondary screens (see [ScreenScaffold]). */
object AuroraIntensity {
    const val screen = 0.6f
    const val full = 1f
}

fun CardTrackColors.isDark(): Boolean = pageBg.luminance() < 0.5f

/** Drifting light blobs and rising sparks behind a screen. */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val colors = LocalCardTrackColors.current
    val strength = intensity * if (colors.isDark()) 1f else 0.6f
    val phase by rememberLoopPhase(26_000, "aurora")
    val sparks = remember { sparkField(26) }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        glow(
            colors.brand.copy(alpha = 0.20f * strength),
            Offset(w * (0.18f + 0.14f * cos(phase)), h * (0.06f + 0.05f * sin(2 * phase))),
            w * 0.85f
        )
        glow(
            colors.income.copy(alpha = 0.11f * strength),
            Offset(w * (0.92f + 0.10f * sin(phase)), h * (0.30f + 0.08f * cos(phase))),
            w * 0.65f
        )
        glow(
            Ember.copy(alpha = 0.08f * strength),
            Offset(w * (0.15f + 0.12f * sin(phase + 1.3f)), h * (0.78f + 0.06f * cos(2 * phase))),
            w * 0.75f
        )
        for (s in sparks) {
            val y = ((s.y - phase / TAU * s.laps) % 1f + 1f) % 1f
            val twinkle = 0.35f + 0.65f * ((sin(phase * s.blink + s.offset) + 1f) / 2f)
            drawCircle(
                colors.brand.copy(alpha = s.alpha * twinkle * strength),
                radius = s.radius.dp.toPx(),
                center = Offset((s.x + 0.015f * sin(phase * s.laps + s.offset)) * w, y * h)
            )
        }
    }
}

private class Spark(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val laps: Int,
    val blink: Int,
    val offset: Float
)

private fun sparkField(count: Int): List<Spark> {
    val rnd = Random(20260926)
    return List(count) {
        Spark(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            radius = 0.8f + rnd.nextFloat() * 1.6f,
            alpha = 0.25f + rnd.nextFloat() * 0.45f,
            laps = 1 + rnd.nextInt(2),
            blink = 2 + rnd.nextInt(5),
            offset = rnd.nextFloat() * TAU
        )
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

/** The app mark floating inside a spinning halo with coins orbiting it in 3D. */
@Composable
fun CardOrbit(modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    val mark = painterResource(R.drawable.ic_app_mark)
    val phase by rememberLoopPhase(6_000, "orbit")
    Canvas(modifier) {
        val c = center
        val s = size.minDimension
        val pulse = 0.5f + 0.5f * sin(phase * 2)
        glow(colors.brand.copy(alpha = 0.26f + 0.14f * pulse), c, s * 0.5f)
        rotate(degrees = phase / TAU * 360f, pivot = c) {
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        colors.brand.copy(alpha = 0.9f),
                        Color.Transparent,
                        colors.income.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    c
                ),
                radius = s * 0.42f,
                center = c,
                style = Stroke(width = s * 0.012f, cap = StrokeCap.Round)
            )
        }
        val coins = List(3) { i ->
            val a = phase + i * TAU / 3f
            Offset(c.x + cos(a) * s * 0.42f, c.y + sin(a) * s * 0.13f) to sin(a)
        }
        coins.filter { it.second < 0f }.forEach { (at, depth) -> drawCoin(at, s * 0.055f, depth) }
        val markSize = s * 0.5f
        val bob = s * 0.025f * sin(phase * 2)
        rotate(degrees = 7f * sin(phase), pivot = c) {
            translate(left = c.x - markSize / 2, top = c.y - markSize / 2 + bob) {
                with(mark) { draw(Size(markSize, markSize)) }
            }
        }
        coins.filter { it.second >= 0f }.forEach { (at, depth) -> drawCoin(at, s * 0.055f, depth) }
    }
}

private fun DrawScope.drawCoin(center: Offset, radius: Float, depth: Float) {
    val r = radius * (0.72f + 0.28f * (depth + 1f) / 2f)
    val dim = 0.55f + 0.45f * (depth + 1f) / 2f
    drawCircle(
        Brush.radialGradient(
            listOf(GoldLight.copy(alpha = dim), GoldDeep.copy(alpha = dim)),
            center = center - Offset(r * 0.35f, r * 0.35f),
            radius = r * 1.6f
        ),
        r,
        center
    )
    drawCircle(Color.White.copy(alpha = 0.35f * dim), r * 0.62f, center, style = Stroke(r * 0.14f))
}

/** One clear tap target: app branding stays outside; this is only “use biometrics”. */
@Composable
fun BiometricUnlockTarget(
    active: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalCardTrackColors.current
    val icon = painterResource(R.drawable.ic_fingerprint_unlock)
    val borderAlpha by animateFloatAsState(
        targetValue = if (active) 0.55f else 0.28f,
        animationSpec = tween(400),
        label = "bioBorder"
    )
    val pulseTransition = rememberInfiniteTransition(label = "bioPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (active) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier
            .size(72.dp)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.bouncyClick(pressedScale = 0.96f, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(colors.areaBg)
            .border(
                width = 1.5.dp,
                color = colors.brand.copy(alpha = borderAlpha + pulse * 0.2f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(colors.brand)
        )
    }
}

/** A bank card bobbing above its shadow with sparkles twinkling around it. */
@Composable
fun FloatingCardArt(modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    val phase by rememberLoopPhase(4_200, "float")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lift = (sin(phase) + 1f) / 2f
        val cardW = w * 0.62f
        val cardH = cardW * 0.63f
        val shadowW = cardW * (0.9f - 0.2f * lift)
        drawOval(
            colors.textMute.copy(alpha = 0.22f - 0.1f * lift),
            topLeft = Offset((w - shadowW) / 2, h * 0.86f),
            size = Size(shadowW, h * 0.07f)
        )
        val top = h * 0.18f - lift * h * 0.08f
        rotate(degrees = -8f + 3f * sin(phase), pivot = Offset(w / 2, top + cardH / 2)) {
            drawBankCard(Offset((w - cardW) / 2, top), Size(cardW, cardH), colors)
        }
        val stars = listOf(
            Triple(Offset(w * 0.12f, h * 0.22f), 0.06f, 0f),
            Triple(Offset(w * 0.9f, h * 0.12f), 0.08f, 1.6f),
            Triple(Offset(w * 0.86f, h * 0.7f), 0.05f, 3.1f),
            Triple(Offset(w * 0.2f, h * 0.72f), 0.045f, 4.4f)
        )
        for ((at, rel, offset) in stars) {
            val twinkle = (sin(phase * 2 + offset) + 1f) / 2f
            drawSparkle(at, w * rel * (0.5f + 0.5f * twinkle), colors.brand.copy(alpha = 0.35f + 0.65f * twinkle))
        }
    }
}

private fun DrawScope.drawBankCard(topLeft: Offset, cardSize: Size, colors: CardTrackColors) {
    val corner = CornerRadius(cardSize.width * 0.09f)
    drawRoundRect(
        Brush.linearGradient(
            listOf(GoldLight, colors.brand, GoldDeep),
            start = topLeft,
            end = topLeft + Offset(cardSize.width, cardSize.height)
        ),
        topLeft,
        cardSize,
        corner
    )
    drawRoundRect(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
            start = topLeft,
            end = topLeft + Offset(cardSize.width * 0.5f, cardSize.height * 0.6f)
        ),
        topLeft,
        cardSize,
        corner
    )
    val chip = Size(cardSize.width * 0.2f, cardSize.height * 0.24f)
    drawRoundRect(
        colors.onBrand.copy(alpha = 0.85f),
        topLeft + Offset(cardSize.width * 0.12f, cardSize.height * 0.3f),
        chip,
        CornerRadius(chip.width * 0.2f)
    )
    val stripeY = topLeft.y + cardSize.height * 0.78f
    for (i in 0 until 4) {
        val x = topLeft.x + cardSize.width * (0.12f + i * 0.19f)
        drawLine(
            colors.onBrand.copy(alpha = 0.4f),
            Offset(x, stripeY),
            Offset(x + cardSize.width * 0.13f, stripeY),
            strokeWidth = cardSize.height * 0.05f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val waist = radius * 0.22f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + waist, center.y - waist, center.x + radius, center.y)
        quadraticTo(center.x + waist, center.y + waist, center.x, center.y + radius)
        quadraticTo(center.x - waist, center.y + waist, center.x - radius, center.y)
        quadraticTo(center.x - waist, center.y - waist, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/** Donut of money in (green) versus money out (red); sweeps in on first show. */
@Composable
fun FlowRing(inflowShare: Float?, modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    val share by animateFloatAsState(
        targetValue = inflowShare ?: 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 90f),
        label = "share"
    )
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val stroke = size.minDimension * 0.15f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(colors.line.copy(alpha = 0.5f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (inflowShare == null) return@Canvas
        val total = 360f * reveal.value
        val capDeg = (stroke / 2) / (arcSize.width / 2) * (180f / PI.toFloat())
        val gap = 2 * capDeg + 5f
        val inSweep = total * share
        val outSweep = total - inSweep
        val round = Stroke(stroke, cap = StrokeCap.Round)
        when {
            share >= 0.995f -> drawArc(colors.income, -90f, total, false, topLeft, arcSize, style = round)
            share <= 0.005f -> drawArc(colors.expense, -90f, total, false, topLeft, arcSize, style = round)
            else -> {
                if (inSweep > gap) {
                    drawArc(colors.income, -90f + gap / 2, inSweep - gap, false, topLeft, arcSize, style = round)
                }
                if (outSweep > gap) {
                    drawArc(colors.expense, -90f + inSweep + gap / 2, outSweep - gap, false, topLeft, arcSize, style = round)
                }
            }
        }
    }
}

/** Layered sine waves drifting sideways; meant as a quiet card background. */
@Composable
fun WaveLines(modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    val phase by rememberLoopPhase(9_000, "waves")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val step = 6.dp.toPx()
        val layers = listOf(
            Triple(0.62f, 0.16f, 1),
            Triple(0.72f, 0.11f, -1),
            Triple(0.82f, 0.08f, 2)
        )
        layers.forEachIndexed { index, (base, amp, speed) ->
            val path = Path()
            var x = 0f
            while (x <= w + step) {
                val y = h * base + h * amp * sin(x / w * TAU * 1.25f + phase * speed + index)
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            if (index == 0) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(w + step, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(colors.brand.copy(alpha = 0.10f), Color.Transparent), h * base, h)
                )
            }
            drawPath(
                path,
                colors.brand.copy(alpha = 0.28f - index * 0.08f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/** Pull-to-refresh coin that flips as you drag and spins while syncing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.CoinPullIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val colors = LocalCardTrackColors.current
    val spin = if (refreshing) {
        rememberInfiniteTransition(label = "coin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "spin"
        )
    } else {
        null
    }
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .size(44.dp)
            .graphicsLayer {
                val pull = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1.4f)
                translationY = pull * 64.dp.toPx() - 48.dp.toPx()
                alpha = min(pull * 1.5f, 1f)
                val grow = 0.6f + 0.4f * min(pull, 1f)
                scaleX = grow
                scaleY = grow
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val angle = spin?.value ?: (state.distanceFraction * 540f)
            drawCircle(colors.cardBg, size.minDimension / 2)
            drawCircle(colors.line, size.minDimension / 2, style = Stroke(1.dp.toPx()))
            val flip = max(abs(cos(angle * PI.toFloat() / 180f)), 0.12f)
            scale(scaleX = flip, scaleY = 1f, pivot = center) {
                drawCoin(center, size.minDimension * 0.3f, depth = 1f)
            }
        }
    }
}

enum class GlyphKind { Categories, Declines, Subscriptions }

/** Small hand-drawn icons for the home shortcuts; two of them turn slowly. */
@Composable
fun ShortcutGlyph(kind: GlyphKind, modifier: Modifier = Modifier, animated: Boolean = true) {
    val colors = LocalCardTrackColors.current
    val phaseState = rememberLoopPhase(16_000, "glyph")
    val phase = if (animated) phaseState.value else 0f
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.13f
        val arcSize = Size(s - stroke, s - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        when (kind) {
            GlyphKind.Categories -> rotate(phase / TAU * 360f) {
                var start = -90f
                for ((sweep, color) in listOf(160f to colors.brand, 115f to colors.income, 85f to colors.expense)) {
                    drawArc(color, start, sweep - 34f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    start += sweep
                }
            }
            GlyphKind.Declines -> {
                val card = Size(s * 0.9f, s * 0.62f)
                val origin = Offset((s - card.width) / 2, (s - card.height) / 2)
                drawRoundRect(colors.textMute, origin, card, CornerRadius(s * 0.1f), style = Stroke(stroke * 0.8f))
                drawLine(
                    colors.expense,
                    Offset(s * 0.18f, s * 0.9f),
                    Offset(s * 0.82f, s * 0.1f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            GlyphKind.Subscriptions -> rotate(phase / TAU * 720f) {
                val sweep = 280f
                drawArc(colors.brand, 0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val theta = sweep * PI.toFloat() / 180f
                val r = arcSize.width / 2
                val tip = Offset(center.x + r * cos(theta), center.y + r * sin(theta))
                val tangent = Offset(-sin(theta), cos(theta))
                val radial = Offset(cos(theta), sin(theta))
                val head = s * 0.2f
                val arrow = Path().apply {
                    moveTo(tip.x + tangent.x * head, tip.y + tangent.y * head)
                    lineTo(tip.x + radial.x * head * 0.8f, tip.y + radial.y * head * 0.8f)
                    lineTo(tip.x - radial.x * head * 0.8f, tip.y - radial.y * head * 0.8f)
                    close()
                }
                drawPath(arrow, colors.brand)
            }
        }
    }
}
