package com.sai.cardtrack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.ui.theme.LocalCardTrackColors

private val PrimaryGlow = Color(0xFFFFC53D)
private val PrimaryDeep = Color(0xFFE89500)

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val colors = LocalCardTrackColors.current
    val active = enabled && !loading
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "press"
    )
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        enabled = active,
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(PrimaryGlow, colors.brand, PrimaryDeep)),
                alpha = if (active) 1f else 0.35f
            )
            .then(if (active) Modifier.sheen(Color.White.copy(alpha = 0.22f), periodMillis = 5200) else Modifier),
        shape = shape,
        interactionSource = source,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onBrand,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onBrand.copy(alpha = 0.45f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onBrand,
                strokeWidth = 2.dp
            )
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onBrand)
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCardTrackColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.brandText
        ),
        border = BorderStroke(1.dp, colors.line)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.brandText)
    }
}

@Composable
fun DangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCardTrackColors.current
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.expense,
            contentColor = colors.onBrand
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onBrand)
    }
}
