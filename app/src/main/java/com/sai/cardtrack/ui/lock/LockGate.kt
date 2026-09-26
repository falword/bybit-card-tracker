package com.sai.cardtrack.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.R
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.KeystoreCredentialsStore
import com.sai.cardtrack.data.LegacyEspKeys
import com.sai.cardtrack.data.LocalWipe
import com.sai.cardtrack.data.PassphraseStore
import com.sai.cardtrack.ui.components.AuroraBackdrop
import com.sai.cardtrack.ui.components.AuroraIntensity
import com.sai.cardtrack.ui.components.BiometricUnlockTarget
import com.sai.cardtrack.ui.components.PrimaryButton
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore

private const val LOCK_AUTHENTICATORS: Int =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
fun LockGate(
    viewModel: LockViewModel,
    onOpen: () -> Unit,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onStart()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val locked by viewModel.lockedState.collectAsStateWithLifecycle()
    val ready by viewModel.lockReady.collectAsStateWithLifecycle()
    LaunchedEffect(ready, locked) {
        if (ready && locked) {
            onOpen()
        }
    }
    val showLock = !ready || locked
    Box(Modifier.fillMaxSize()) {
        if (!showLock) content()
        AnimatedVisibility(
            visible = showLock,
            enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 8 },
            exit = fadeOut(tween(420)) + scaleOut(tween(420), targetScale = 1.04f)
        ) {
            LockBackdrop(
                ready = ready,
                onUnlock = if (ready && locked) onOpen else null
            )
        }
    }
}

@Composable
private fun LockBackdrop(
    ready: Boolean,
    onUnlock: (() -> Unit)?
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AuroraBackdrop(intensity = AuroraIntensity.screen)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.cardBg)
                    .border(1.dp, colors.line.copy(alpha = 0.65f), shape)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_mark),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    copy.appTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textMain
                )
                Spacer(Modifier.height(8.dp))
                if (!ready) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = colors.brand,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        copy.lockPreparing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMute,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        copy.lockUnlockBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMute,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    BiometricUnlockTarget(
                        active = onUnlock != null,
                        contentDescription = copy.openLock,
                        onClick = onUnlock
                    )
                    if (onUnlock != null) {
                        Spacer(Modifier.height(24.dp))
                        PrimaryButton(
                            label = copy.openLock,
                            onClick = onUnlock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 280.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                copy.lockHint,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMute,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

fun lockUsesDeviceCredentialAllowed(sdk: Int): Boolean = sdk < 30

fun lockPromptInfo(sdk: Int, title: String = LockViewModel.TITLE): BiometricPrompt.PromptInfo {
    val builder = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
    return if (lockUsesDeviceCredentialAllowed(sdk)) {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true).build()
    } else {
        builder.setAllowedAuthenticators(LOCK_AUTHENTICATORS).build()
    }
}

fun deviceUnlockAvailable(context: Context): Boolean {
    val biometric = BiometricManager.from(context)
    if (biometric.canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS) {
        return true
    }
    val sdk = Build.VERSION.SDK_INT
    if (sdk < 30) {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure == true) return true
    }
    // BIOMETRIC_WEAK|DEVICE_CREDENTIAL is legal on API 30+; else canAuthenticate is ERROR_UNSUPPORTED.
    val weakOrCredential = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return sdk >= 30 &&
        biometric.canAuthenticate(weakOrCredential) == BiometricManager.BIOMETRIC_SUCCESS
}

fun isKeyPermanentlyInvalidated(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is KeyPermanentlyInvalidatedException) return true
        current = current.cause
    }
    return false
}

fun isUserNotAuthenticated(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is UserNotAuthenticatedException) return true
        current = current.cause
    }
    return false
}

fun clearVaultSecrets(context: Context) {
    context.getSharedPreferences(KeystoreCredentialsStore.PREFS_NAME, Context.MODE_PRIVATE)
        .edit(commit = true) {
            remove(KeystoreCredentialsStore.KEY_API)
            remove(KeystoreCredentialsStore.KEY_SECRET)
            remove(PassphraseStore.PREFS_KEY)
        }
    try {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val esp = EncryptedSharedPreferences.create(
            context,
            LegacyEspKeys.PREFS_NAME,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        esp.edit(commit = true) {
            remove(LegacyEspKeys.KEY_API)
            remove(LegacyEspKeys.KEY_SECRET)
        }
    } catch (_: Exception) {
    }
    try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        for (alias in LocalWipe.KEYSTORE_ALIASES) {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    } catch (_: Exception) {
    }
    LocalWipe.deleteDatabaseArtifacts(
        deleteDatabase = { name -> context.deleteDatabase(name) },
        databaseFile = { name -> context.getDatabasePath(name) }
    )
}

fun launchBiometricUnlock(
    activity: FragmentActivity,
    credentialsStore: CredentialsStore,
    lockViewModel: LockViewModel,
    onInvalidated: () -> Unit,
    onUnlocked: () -> Unit = {}
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                activity.lifecycleScope.launch {
                    val probe = withContext(Dispatchers.IO) {
                        runCatching { credentialsStore.get() }
                    }
                    if (probe.isSuccess) {
                        lockViewModel.unlock()
                        onUnlocked()
                        return@launch
                    }
                    val error = probe.exceptionOrNull() ?: return@launch
                    if (isKeyPermanentlyInvalidated(error)) {
                        clearVaultSecrets(activity)
                        onInvalidated()
                        return@launch
                    }
                    if (!isUserNotAuthenticated(error)) return@launch
                    val retry = withContext(Dispatchers.IO) {
                        runCatching { credentialsStore.get() }
                    }
                    if (retry.isSuccess || retry.exceptionOrNull()?.let(::isUserNotAuthenticated) == true) {
                        lockViewModel.unlock()
                        onUnlocked()
                        return@launch
                    }
                    val retryError = retry.exceptionOrNull() ?: return@launch
                    if (isKeyPermanentlyInvalidated(retryError)) {
                        clearVaultSecrets(activity)
                        onInvalidated()
                    }
                }
            }
        }
    )
    val info = lockPromptInfo(Build.VERSION.SDK_INT, activity.getString(R.string.app_name))
    try {
        prompt.authenticate(info)
    } catch (error: Exception) {
        if (isKeyPermanentlyInvalidated(error)) {
            clearVaultSecrets(activity)
            onInvalidated()
        }
    }
}
