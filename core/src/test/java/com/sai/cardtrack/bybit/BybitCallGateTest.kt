package com.sai.cardtrack.bybit

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BybitCallGateTest {

    @Test
    fun `transfer permits wait at least one second after the previous transfer`() = runTest {
        // official-v5/rate-limit/rate-limit.mdx: query-inter-transfer-list 60 req/min
        val pauses = mutableListOf<Long>()
        var nowMs = 10_000L
        val gate = BybitCallGate(
            now = { nowMs },
            pause = { ms ->
                pauses.add(ms)
                nowMs += ms
            }
        )
        gate.withPermit(transfer = true) { nowMs += 1 }
        gate.withPermit(transfer = true) { }
        assertEquals(listOf(1_000L), pauses)
    }

    @Test
    fun `card permits wait at least 400ms after the previous card call`() = runTest {
        // official-v5/rate-limit/rate-limit.mdx has no row for /v5/card/*
        // CONSTRAINTS.md: undocumented card POSTs are spaced 400 ms
        val pauses = mutableListOf<Long>()
        var nowMs = 10_000L
        val gate = BybitCallGate(
            now = { nowMs },
            pause = { ms ->
                pauses.add(ms)
                nowMs += ms
            }
        )
        gate.withPermit(BybitThrottle.Card) { nowMs += 1 }
        gate.withPermit(BybitThrottle.Card) { }
        assertEquals(listOf(400L), pauses)
    }

    @Test
    fun `inflight cap is five`() = runTest {
        val inFlight = AtomicInteger(0)
        var maxInFlight = 0
        val gate = BybitCallGate(maxInFlight = 5)
        coroutineScope {
            repeat(8) {
                async {
                    gate.withPermit {
                        val n = inFlight.incrementAndGet()
                        if (n > maxInFlight) {
                            maxInFlight = n
                        }
                        delay(20)
                        inFlight.decrementAndGet()
                    }
                }
            }
        }
        assertTrue(maxInFlight <= 5)
        assertEquals(5, maxInFlight)
    }
}
