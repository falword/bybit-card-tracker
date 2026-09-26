package com.sai.cardtrack.ui.lock

import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unlock is ready and not locked`() {
        val vm = LockViewModel(
            hasCredentials = { true },
            clock = { 0L },
            biometricAvailable = { true }
        )
        vm.unlock()
        assertFalse(vm.lockedState.value)
        assertTrue(vm.lockReady.value)
    }

    @Test
    fun `lock is not ready before onStart`() {
        val vm = LockViewModel(
            hasCredentials = { true },
            clock = { 0L },
            biometricAvailable = { true }
        )
        assertFalse(vm.lockReady.value)
    }

    @Test
    fun `no credentials is not locked`() = runTest {
        val vm = LockViewModel(
            hasCredentials = { false },
            clock = { 0L },
            biometricAvailable = { true }
        )
        vm.onStart()
        advanceUntilIdle()
        assertFalse(vm.lockedState.value)
        assertTrue(vm.lockReady.value)
    }

    @Test
    fun `credentials and 31s since onStop is locked`() = runTest {
        var now = 0L
        val vm = LockViewModel(
            hasCredentials = { true },
            clock = { now },
            biometricAvailable = { true }
        )
        vm.unlock()
        vm.onStop()
        now = 31_000L
        vm.onStart()
        advanceUntilIdle()
        assertTrue(vm.lockedState.value)
    }

    @Test
    fun `credentials and 10s since onStop stays unlocked if previously unlocked`() = runTest {
        var now = 0L
        val vm = LockViewModel(
            hasCredentials = { true },
            clock = { now },
            biometricAvailable = { true }
        )
        vm.unlock()
        vm.onStop()
        now = 10_000L
        vm.onStart()
        advanceUntilIdle()
        assertFalse(vm.lockedState.value)
    }

    @Test
    fun `biometric unavailable stays locked when credentials exist`() = runTest {
        val vm = LockViewModel(
            hasCredentials = { true },
            clock = { 0L },
            biometricAvailable = { false }
        )
        vm.onStart()
        advanceUntilIdle()
        assertTrue(vm.lockedState.value)
    }

    @Test
    fun `no credentials stays unlocked even without biometric`() = runTest {
        val vm = LockViewModel(
            hasCredentials = { false },
            clock = { 0L },
            biometricAvailable = { false }
        )
        vm.onStart()
        advanceUntilIdle()
        assertFalse(vm.lockedState.value)
    }

    @Test
    fun `credentials never unlocked this process is locked and ready`() = runTest {
        val vm = LockViewModel(
            hasCredentials = { true },
            clock = { 0L },
            biometricAvailable = { true }
        )
        vm.onStart()
        advanceUntilIdle()
        assertTrue(vm.lockedState.value)
        assertTrue(vm.lockReady.value)
    }

    @Test
    fun `prompt title is the product name`() {
        assertEquals("CardTrack", LockViewModel.TITLE)
    }

    @Test
    fun `keyProtectionReset copy is exact`() {
        assertEquals(
            "Защита ключа сброшена. Введи ключ Bybit снова.",
            UiCopy.Ru.keyProtectionReset
        )
        assertEquals(
            "Device key protection was reset. Enter the Bybit key again.",
            UiCopy.En.keyProtectionReset
        )
    }
}
