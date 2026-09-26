package com.sai.cardtrack.bybit

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

enum class BybitThrottle {
    Fast,
    Card,
    Transfer
}

class BybitCallGate(
    private val maxInFlight: Int = 5,
    private val transferMinGapMs: Long = 1_000L,
    private val cardMinGapMs: Long = 400L,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    private val inflight = Semaphore(maxInFlight)
    private val transferLock = Mutex()
    private val cardLock = Mutex()
    private var lastTransferAt = 0L
    private var lastCardAt = 0L

    suspend fun <T> withPermit(transfer: Boolean = false, block: suspend () -> T): T {
        return withPermit(if (transfer) BybitThrottle.Transfer else BybitThrottle.Fast, block)
    }

    suspend fun <T> withPermit(throttle: BybitThrottle, block: suspend () -> T): T {
        return when (throttle) {
            BybitThrottle.Fast -> inflight.withPermit { block() }
            BybitThrottle.Transfer -> spaced(transferLock, { lastTransferAt }, { lastTransferAt = it }, transferMinGapMs, block)
            BybitThrottle.Card -> spaced(cardLock, { lastCardAt }, { lastCardAt = it }, cardMinGapMs, block)
        }
    }

    private suspend fun <T> spaced(
        lock: Mutex,
        lastAt: () -> Long,
        setLastAt: (Long) -> Unit,
        minGapMs: Long,
        block: suspend () -> T
    ): T {
        return lock.withLock {
            val wait = lastAt() + minGapMs - now()
            if (lastAt() > 0L && wait > 0L) {
                pause(wait)
            }
            inflight.withPermit {
                try {
                    block()
                } finally {
                    setLastAt(now())
                }
            }
        }
    }
}
