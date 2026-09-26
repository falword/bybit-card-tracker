package com.sai.cardtrack.ui.credentials

import android.content.Intent
import android.view.View
import android.view.autofill.AutofillManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sai.cardtrack.data.AppearancePrefs
import com.sai.cardtrack.ui.LanguageMode
import com.sai.cardtrack.ui.ThemeMode
import com.sai.cardtrack.ui.components.AppTopBar
import com.sai.cardtrack.ui.components.DangerButton
import com.sai.cardtrack.ui.components.PrimaryButton
import com.sai.cardtrack.ui.components.ScreenScaffold
import com.sai.cardtrack.ui.components.SecondaryButton
import com.sai.cardtrack.ui.components.SectionLabel
import com.sai.cardtrack.ui.components.appFieldColors
import com.sai.cardtrack.ui.export.ExportViewModel
import com.sai.cardtrack.ui.export.ShareCsv
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CredentialsScreen(
    viewModel: CredentialsViewModel,
    firstLaunch: Boolean,
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
    appearance: AppearancePrefs? = null,
    onLanguage: (LanguageMode) -> Unit = {},
    onTheme: (ThemeMode) -> Unit = {},
    export: ExportViewModel? = null,
    onWipe: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    val context = LocalContext.current
    val hostView = LocalView.current
    val autofill = remember(context) { context.getSystemService(AutofillManager::class.java) }
    SideEffect {
        hostView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }
    var replaceKey by rememberSaveable { mutableStateOf(firstLaunch) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            replaceKey = false
            onSaved()
        }
    }
    val fieldColors = appFieldColors()
    ScreenScaffold(consumeIme = true, scrollable = true) {
        if (onBack != null) {
            AppTopBar(title = copy.settings, onBack = onBack)
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                copy.bybitKey,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textMain
            )
            Spacer(Modifier.height(8.dp))
        }
        if (firstLaunch) {
            Text(
                copy.keyHintShort,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMute
            )
            Spacer(Modifier.height(16.dp))
            KeyGuideCard()
            Spacer(Modifier.height(20.dp))
            KeyFields(state, viewModel, fieldColors, autofill)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                label = copy.checkAndSignIn,
                onClick = { viewModel.submit() },
                enabled = !state.busy && state.apiKey.isNotBlank() && state.apiSecret.isNotBlank(),
                loading = state.busy,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            if (state.lastSyncLabel != null) {
                Text(
                    copy.lastSyncPrefix + state.lastSyncLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMute
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.keyHint != null) {
                Text(
                    "••••${state.keyHint}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMute
                )
                Spacer(Modifier.height(12.dp))
            }
            if (!replaceKey) {
                SecondaryButton(
                    label = copy.replaceKey,
                    onClick = { replaceKey = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    copy.keyHintShort,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMute
                )
                Spacer(Modifier.height(12.dp))
                KeyFields(state, viewModel, fieldColors, autofill)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    label = copy.saveKey,
                    onClick = { viewModel.submit() },
                    enabled = !state.busy && state.apiKey.isNotBlank() && state.apiSecret.isNotBlank(),
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (!firstLaunch) {
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    copy.includeP2p,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textMain,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.p2pAccounting,
                    onCheckedChange = viewModel::onP2pAccounting,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onBrand,
                        checkedTrackColor = colors.brand,
                        uncheckedThumbColor = colors.textMute,
                        uncheckedTrackColor = colors.areaBg
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                copy.includeP2pHint,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMute
            )
        }
        if (!firstLaunch && appearance != null) {
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(20.dp))
            SectionLabel(copy.appearance)
            Spacer(Modifier.height(12.dp))
            SectionLabel(copy.language)
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                options = listOf(
                    LanguageMode.System to copy.system,
                    LanguageMode.Ru to copy.russian,
                    LanguageMode.En to copy.english
                ),
                selected = appearance.language,
                onSelect = onLanguage
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(copy.theme)
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                options = listOf(
                    ThemeMode.System to copy.system,
                    ThemeMode.Dark to copy.dark,
                    ThemeMode.Light to copy.light
                ),
                selected = appearance.theme,
                onSelect = onTheme
            )
        }
        if (!firstLaunch && export != null) {
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(20.dp))
            SectionLabel(copy.export)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                label = copy.exportCsv,
                onClick = {
                    scope.launch {
                        val csv = export.exportCsv()
                        val share = withContext(Dispatchers.IO) {
                            ShareCsv.writeAndIntent(context, csv, ShareCsv.TABLE_FILENAME)
                        }
                        context.startActivity(
                            Intent.createChooser(share, copy.exportCsv)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                copy.exportCsvHint,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMute
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                label = copy.exportKoinly,
                onClick = {
                    scope.launch {
                        val csv = export.exportKoinly()
                        val share = withContext(Dispatchers.IO) {
                            ShareCsv.writeAndIntent(context, csv, ShareCsv.KOINLY_FILENAME)
                        }
                        context.startActivity(
                            Intent.createChooser(share, copy.exportKoinly)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                copy.exportKoinlyHint,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMute
            )
        }
        if (!firstLaunch && onWipe != null) {
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(20.dp))
            DangerButton(
                label = copy.wipe,
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(copy.wipe, style = MaterialTheme.typography.titleMedium) },
            text = { Text(copy.wipeConfirm, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWipe = false
                        onWipe?.invoke()
                    }
                ) {
                    Text(copy.wipe, style = MaterialTheme.typography.labelLarge, color = colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text(copy.back, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun KeyFields(
    state: CredentialsUiState,
    viewModel: CredentialsViewModel,
    fieldColors: TextFieldColors,
    autofill: AutofillManager?
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = viewModel::onKeyChange,
        label = { Text(copy.apiKeyLabel, style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier
            .fillMaxWidth()
            .disableAutofill(autofill),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = fieldColors
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.apiSecret,
        onValueChange = viewModel::onSecretChange,
        label = { Text(copy.apiSecretLabel, style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier
            .fillMaxWidth()
            .disableAutofill(autofill),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(8.dp),
        colors = fieldColors
    )
    if (state.error != null) {
        Spacer(Modifier.height(12.dp))
        Text(state.error ?: "", style = MaterialTheme.typography.bodySmall, color = colors.expense)
    }
}

@Composable
private fun KeyGuideCard() {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.areaBg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                copy.keyGuideTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMain,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textMute
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                copy.keyGuideSteps.forEachIndexed { index, step ->
                    Row(Modifier.padding(top = if (index == 0) 0.dp else 10.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMute,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMute,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val colors = LocalCardTrackColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.areaBg)
            .selectableGroup()
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (on) colors.textMain else colors.textMute,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) colors.cardBg else colors.areaBg)
                    .then(
                        if (on) Modifier.border(1.dp, colors.brand.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .selectable(
                        selected = on,
                        role = Role.RadioButton,
                        onClick = { onSelect(value) }
                    )
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun Modifier.disableAutofill(autofill: AutofillManager?): Modifier {
    return this
        .semantics { }
        .onFocusChanged { focus ->
            if (focus.isFocused) {
                autofill?.cancel()
            }
        }
}
