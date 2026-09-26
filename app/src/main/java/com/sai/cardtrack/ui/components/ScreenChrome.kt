package com.sai.cardtrack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    consumeIme: Boolean = false,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalCardTrackColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(colors.pageBg)
    ) {
        AuroraBackdrop(intensity = AuroraIntensity.screen)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .then(if (consumeIme) Modifier.imePadding() else Modifier)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = copy.back,
                    tint = colors.textMain
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textMain,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

@Composable
fun MonthSwitcher(
    monthLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = copy.back,
                    tint = colors.textMain
                )
            }
        }
        var forward by remember { mutableStateOf(true) }
        IconButton(onClick = {
            forward = false
            onPrev()
        }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = copy.previousMonth,
                tint = colors.textMain
            )
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = monthLabel,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 400f)) { it / 2 * dir } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 2 * dir } + fadeOut())
                },
                label = "month"
            ) { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMute,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = {
            forward = true
            onNext()
        }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = copy.nextMonth,
                tint = colors.textMain
            )
        }
    }
}

@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalCardTrackColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .riseIn(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingCardArt(Modifier.size(width = 140.dp, height = 110.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMute,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onAction != null) colors.brandText else colors.textMute,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .then(
                        if (onAction != null) {
                            Modifier
                                .heightIn(min = 48.dp)
                                .bouncyClick(onClick = onAction)
                                .semantics { contentDescription = action }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textMute,
        modifier = modifier
    )
}

@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Brand
) {
    val colors = LocalCardTrackColors.current
    val (fg, bg) = when (tone) {
        BannerTone.Brand -> colors.brandText to colors.brandSoft
        BannerTone.Danger -> colors.expense to colors.expense.copy(alpha = 0.12f)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = fg,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

enum class BannerTone { Brand, Danger }

@Composable
fun CardNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    val colors = LocalCardTrackColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .bouncyClick(pressedScale = 0.97f, onClick = onClick)
            .padding(vertical = 12.dp)
            .semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.brandText
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMute
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.brandText
        )
    }
}

@Composable
fun PageLoading(modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(colors.pageBg),
        contentAlignment = Alignment.Center
    ) {
        AuroraBackdrop(intensity = AuroraIntensity.full)
        CardOrbit(Modifier.size(140.dp))
    }
}
