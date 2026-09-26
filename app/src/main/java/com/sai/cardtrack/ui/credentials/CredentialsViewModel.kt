package com.sai.cardtrack.ui.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.sync.VerifyAndSaveCredentials
import com.sai.cardtrack.sync.VerifyResult
import com.sai.cardtrack.sync.ioExceptionBanner
import com.sai.cardtrack.ui.LastSyncLabel
import com.sai.cardtrack.ui.UiCopy
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

data class CredentialsUiState(
    val apiKey: String = "",
    val apiSecret: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    val saved: Boolean = false,
    val lastSyncLabel: String? = null,
    val keyHint: String? = null,
    val p2pAccounting: Boolean = false
)

private const val MAX_CREDENTIAL_LENGTH: Int = 256

class CredentialsViewModel(
    private val verify: VerifyAndSaveCredentials,
    private val store: CredentialsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val copy: UiCopy = UiCopy.Ru,
    private val deviceSecure: () -> Boolean = { true },
    private val onUserNotAuthenticated: () -> Unit = {},
    private val userNotAuthenticated: (Throwable) -> Boolean = { false }
) : ViewModel() {
    private val _state = MutableStateFlow(CredentialsUiState())
    val state: StateFlow<CredentialsUiState> = _state

    init {
        viewModelScope.launch {
            val hint = try {
                store.apiKeyHint()
            } catch (error: Exception) {
                if (userNotAuthenticated(error)) {
                    null
                } else {
                    throw error
                }
            }
            _state.value = _state.value.copy(
                lastSyncLabel = LastSyncLabel.format(store.lastSyncAt(), clock(), zone, copy.locale),
                keyHint = hint,
                p2pAccounting = store.p2pAccountingEnabled()
            )
        }
    }

    fun onP2pAccounting(value: Boolean) {
        _state.value = _state.value.copy(p2pAccounting = value)
        viewModelScope.launch { store.setP2pAccountingEnabled(value) }
    }

    fun onKeyChange(value: String) {
        _state.value = _state.value.copy(
            apiKey = value.take(MAX_CREDENTIAL_LENGTH),
            error = null,
            saved = false
        )
    }

    fun onSecretChange(value: String) {
        _state.value = _state.value.copy(
            apiSecret = value.take(MAX_CREDENTIAL_LENGTH),
            error = null,
            saved = false
        )
    }

    fun submit() {
        if (!deviceSecure()) {
            _state.value = _state.value.copy(error = copy.lockScreenRequired)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            val key = _state.value.apiKey.trim().take(MAX_CREDENTIAL_LENGTH)
            val secret = _state.value.apiSecret.trim().take(MAX_CREDENTIAL_LENGTH)
            try {
                when (val result = verify.execute(
                    Credentials(key, secret),
                    copy.locale
                )) {
                    VerifyResult.Ok -> _state.value = _state.value.copy(
                        busy = false,
                        saved = true,
                        error = null,
                        apiSecret = "",
                        keyHint = key.takeLast(4).ifEmpty { _state.value.keyHint }
                    )
                    is VerifyResult.Failed -> _state.value = _state.value.copy(
                        busy = false,
                        saved = false,
                        error = result.banner
                    )
                }
            } catch (error: Exception) {
                if (userNotAuthenticated(error)) {
                    onUserNotAuthenticated()
                    _state.value = _state.value.copy(busy = false)
                    return@launch
                }
                val why = if (error is IOException) {
                    ioExceptionBanner(error, copy.locale)
                } else {
                    copy.verifyInternalError
                }
                _state.value = _state.value.copy(
                    busy = false,
                    saved = false,
                    error = why
                )
            }
        }
    }
}
