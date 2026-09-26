package com.sai.cardtrack.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LockViewModel(
    private val hasCredentials: suspend () -> Boolean,
    private val clock: () -> Long,
    private val biometricAvailable: () -> Boolean
) : ViewModel() {
    private var lastOnStop: Long? = null
    private var unlockedThisProcess: Boolean = false

    private val _locked = MutableStateFlow(true)
    val lockedState: StateFlow<Boolean> = _locked

    private val _lockReady = MutableStateFlow(false)
    val lockReady: StateFlow<Boolean> = _lockReady

    fun onStart() {
        viewModelScope.launch {
            if (!hasCredentials()) {
                _locked.value = false
                _lockReady.value = true
                return@launch
            }
            if (!biometricAvailable()) {
                _locked.value = true
                _lockReady.value = true
                return@launch
            }
            val stop = lastOnStop
            val timedOut = stop != null && clock() - stop >= LOCK_AFTER_MS
            _locked.value = !unlockedThisProcess || timedOut
            _lockReady.value = true
        }
    }

    fun onStop() {
        lastOnStop = clock()
    }

    fun unlock() {
        unlockedThisProcess = true
        lastOnStop = null
        _locked.value = false
        _lockReady.value = true
    }

    companion object {
        const val LOCK_AFTER_MS: Long = 30_000L
        const val TITLE: String = "CardTrack"
    }
}
