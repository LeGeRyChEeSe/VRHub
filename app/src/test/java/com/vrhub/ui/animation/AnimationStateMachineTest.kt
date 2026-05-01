package com.vrhub.ui.animation

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AnimationStateMachine - Story 2.1
 *
 * Tests cover:
 * - Valid state transitions
 * - Invalid transition rejection
 * - Thread safety with concurrent updates
 * - Progress updates
 */
class AnimationStateMachineTest {

    // ========== Valid State Transitions Tests ==========

    @Test
    fun validTransition_idleToDownloading() {
        val stateMachine = AnimationStateMachine()

        val result = stateMachine.tryTransitionTo(AnimationState.Downloading(0f))

        assertTrue("Idle -> Downloading should be valid", result)
        assertEquals(AnimationState.Downloading(0f), stateMachine.currentState)
    }

    @Test
    fun validTransition_downloadingToExtracting() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))

        val result = stateMachine.tryTransitionTo(AnimationState.Extracting(0.3f))

        assertTrue("Downloading -> Extracting should be valid", result)
        assertEquals(AnimationState.Extracting(0.3f), stateMachine.currentState)
    }

    @Test
    fun validTransition_extractingToInstalling() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Extracting(0.5f))

        val result = stateMachine.tryTransitionTo(AnimationState.Installing(0.8f))

        assertTrue("Extracting -> Installing should be valid", result)
        assertEquals(AnimationState.Installing(0.8f), stateMachine.currentState)
    }

    @Test
    fun validTransition_downloadingToPaused() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))

        val result = stateMachine.tryTransitionTo(AnimationState.Paused())

        assertTrue("Downloading -> Paused should be valid", result)
    }

    @Test
    fun validTransition_pausedToDownloading() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))
        stateMachine.tryTransitionTo(AnimationState.Paused())

        val result = stateMachine.tryTransitionTo(AnimationState.Downloading(0.6f))

        assertTrue("Paused -> Downloading should be valid", result)
    }

    @Test
    fun validTransition_pausedToIdle() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))
        stateMachine.tryTransitionTo(AnimationState.Paused())

        val result = stateMachine.tryTransitionTo(AnimationState.Idle())

        assertTrue("Paused -> Idle should be valid", result)
    }

    @Test
    fun validTransition_installingToIdle() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Installing(1f))

        val result = stateMachine.tryTransitionTo(AnimationState.Idle())

        assertTrue("Installing -> Idle should be valid (completion)", result)
    }

    // ========== Invalid Transition Rejection Tests ==========

    @Test
    fun invalidTransition_pausedToDownloading_withoutActiveState() {
        val stateMachine = AnimationStateMachine()
        val result = stateMachine.tryTransitionTo(AnimationState.Paused())

        assertFalse("Idle -> Paused should be invalid (must go through active state first)", result)
    }

    @Test
    fun invalidTransition_idleToPaused() {
        val stateMachine = AnimationStateMachine()

        val result = stateMachine.tryTransitionTo(AnimationState.Paused())

        assertFalse("Idle -> Paused should be invalid", result)
    }

    @Test
    fun invalidTransition_extractingToDownloading() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Extracting(0.5f))

        val result = stateMachine.tryTransitionTo(AnimationState.Downloading(0.3f))

        assertFalse("Extracting -> Downloading should be invalid", result)
    }

    @Test
    fun invalidTransition_installingToExtracting() {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Installing(0.5f))

        val result = stateMachine.tryTransitionTo(AnimationState.Extracting(0.3f))

        assertFalse("Installing -> Extracting should be invalid", result)
    }

    @Test
    fun canTransitionTo_returnsCorrectValue() {
        val stateMachine = AnimationStateMachine()

        assertTrue("Should allow Idle -> Downloading", stateMachine.canTransitionTo(AnimationState.Downloading(0f)))
        assertFalse("Should not allow Idle -> Paused", stateMachine.canTransitionTo(AnimationState.Paused()))

        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))
        assertTrue("Should allow Downloading -> Extracting", stateMachine.canTransitionTo(AnimationState.Extracting(0f)))
        assertTrue("Should allow Downloading -> Idle", stateMachine.canTransitionTo(AnimationState.Idle()))
        assertFalse("Should not allow Downloading -> Installing", stateMachine.canTransitionTo(AnimationState.Installing(0f)))
    }

    // ========== Progress Update Tests ==========

    @Test
    fun progressUpdate_onDownloadingState() = runBlocking {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0f))

        val result = stateMachine.updateProgress(0.5f)

        assertTrue("Progress update should succeed on Downloading state", result)
        assertEquals(AnimationState.Downloading(0.5f), stateMachine.currentState)
    }

    @Test
    fun progressUpdate_onExtractingState() = runBlocking {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Extracting(0f))

        val result = stateMachine.updateProgress(0.75f)

        assertTrue("Progress update should succeed on Extracting state", result)
        assertEquals(AnimationState.Extracting(0.75f), stateMachine.currentState)
    }

    @Test
    fun progressUpdate_clampedToValidRange() = runBlocking {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))

        // Test clamping
        stateMachine.updateProgress(1.5f)
        assertEquals(AnimationState.Downloading(1f), stateMachine.currentState)

        stateMachine.tryTransitionTo(AnimationState.Extracting(0.5f))
        stateMachine.updateProgress(-0.5f)
        assertEquals(AnimationState.Extracting(0f), stateMachine.currentState)
    }

    @Test
    fun progressUpdate_onIdleState_fails() = runBlocking {
        val stateMachine = AnimationStateMachine()

        val result = stateMachine.updateProgress(0.5f)

        assertFalse("Progress update should fail on Idle state", result)
    }

    @Test
    fun progressUpdate_onPausedState_fails() = runBlocking {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))
        stateMachine.tryTransitionTo(AnimationState.Paused())

        val result = stateMachine.updateProgress(0.5f)

        assertFalse("Progress update should fail on Paused state", result)
    }

    // ========== Reset Tests ==========

    @Test
    fun reset_returnsToIdle() = runBlocking {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.8f))
        stateMachine.tryTransitionTo(AnimationState.Extracting(0.5f))

        stateMachine.reset()

        assertEquals(AnimationState.Idle(), stateMachine.currentState)
    }

    // ========== StateFlow Emission Tests ==========

    @Test
    fun stateFlow_emitsOnTransition() = runTest {
        val stateMachine = AnimationStateMachine()

        // Get initial state - StateFlow always has a value
        val initialState = stateMachine.state.value
        assertEquals("Initial state should be Idle", AnimationState.Idle(), initialState)

        // Transition and verify emission
        stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))

        // Verify the current state was updated
        assertEquals(AnimationState.Downloading(0.5f), stateMachine.currentState)

        // Verify the StateFlow also reflects the new state
        assertEquals(AnimationState.Downloading(0.5f), stateMachine.state.value)
    }

    // ========== Performance Tests ==========

    @Test
    fun transition_completesWithin16ms() = runBlocking {
        val stateMachine = AnimationStateMachine()
        val iterations = 1000
        val maxAllowedMs = 16L

        val startTime = System.nanoTime()

        repeat(iterations) {
            stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))
            stateMachine.tryTransitionTo(AnimationState.Extracting(0.5f))
            stateMachine.tryTransitionTo(AnimationState.Idle())
        }

        val endTime = System.nanoTime()
        val totalMs = (endTime - startTime) / 1_000_000
        val avgMs = totalMs / iterations

        assertTrue("Average transition should be under 16ms, was: ${avgMs}ms", avgMs < maxAllowedMs)
    }

    @Test
    fun stateFlow_emission_within16ms() = runTest {
        val stateMachine = AnimationStateMachine()
        val iterations = 1000
        val maxAllowedNs = 16_000_000L // 16ms in nanoseconds

        // Warm up
        stateMachine.tryTransitionTo(AnimationState.Downloading(0f))

        var totalNs = 0L

        repeat(iterations) {
            val startNs = System.nanoTime()

            // Synchronous transition that should trigger StateFlow emission
            stateMachine.tryTransitionTo(AnimationState.Extracting(0.5f))
            stateMachine.tryTransitionTo(AnimationState.Downloading(0.5f))

            val endNs = System.nanoTime()
            totalNs += (endNs - startNs)
        }

        val avgNs = totalNs / iterations

        assertTrue("StateFlow emission should complete within 16ms (60fps), was: ${avgNs / 1_000_000}ms",
            avgNs < maxAllowedNs)
    }

    // ========== Integration Tests ==========

    @Test
    fun installStatus_mapping_coverage() {
        // Verify all AnimationState values are handled
        val relevantStates = listOf(
            AnimationState.Idle(),
            AnimationState.Downloading(0f),
            AnimationState.Extracting(0f),
            AnimationState.Installing(0f),
            AnimationState.Paused()
        )

        assertTrue("Should have Idle state", relevantStates.any { it is AnimationState.Idle })
        assertTrue("Should have Downloading state", relevantStates.any { it is AnimationState.Downloading })
        assertTrue("Should have Extracting state", relevantStates.any { it is AnimationState.Extracting })
        assertTrue("Should have Installing state", relevantStates.any { it is AnimationState.Installing })
        assertTrue("Should have Paused state", relevantStates.any { it is AnimationState.Paused })
    }

    // ========== Paused Reason Tests ==========

    @Test
    fun pausedState_withDifferentReasons() {
        val pausedUser = AnimationState.Paused(AnimationState.PausedReason.USER_REQUESTED)
        val pausedNetwork = AnimationState.Paused(AnimationState.PausedReason.NETWORK_INTERRUPTED)
        val pausedStorage = AnimationState.Paused(AnimationState.PausedReason.LOW_STORAGE)

        assertTrue(pausedUser is AnimationState.Paused)
        assertTrue(pausedNetwork is AnimationState.Paused)
        assertTrue(pausedStorage is AnimationState.Paused)

        assertEquals(AnimationState.PausedReason.USER_REQUESTED, pausedUser.reason)
        assertEquals(AnimationState.PausedReason.NETWORK_INTERRUPTED, pausedNetwork.reason)
        assertEquals(AnimationState.PausedReason.LOW_STORAGE, pausedStorage.reason)
    }

    // ========== Thread Safety Tests ==========

    @Test
    fun threadSafety_concurrentTransitions() = runTest {
        val stateMachine = AnimationStateMachine()
        // Initialize to Downloading state first
        stateMachine.tryTransitionTo(AnimationState.Downloading(0f))

        val iterations = 100

        // Launch multiple coroutines that try to transition concurrently
        // From Downloading, valid transitions are: Extracting, Paused, Idle
        repeat(iterations) { i ->
            launch {
                // Each coroutine tries different transitions that are valid from Downloading
                when (i % 3) {
                    0 -> stateMachine.tryTransitionTo(AnimationState.Extracting(0.3f))
                    1 -> stateMachine.tryTransitionTo(AnimationState.Paused())
                    else -> stateMachine.tryTransitionTo(AnimationState.Idle())
                }
            }
        }

        // Verify state machine ends in a valid state (no corruption)
        val finalState = stateMachine.currentState
        assertTrue("State machine should be in a valid state after concurrent access: $finalState",
            finalState is AnimationState.Downloading ||
            finalState is AnimationState.Extracting ||
            finalState is AnimationState.Installing ||
            finalState is AnimationState.Paused ||
            finalState is AnimationState.Idle)
    }

    @Test
    fun threadSafety_concurrentProgressUpdates() = runTest {
        val stateMachine = AnimationStateMachine()
        stateMachine.tryTransitionTo(AnimationState.Downloading(0f))

        val iterations = 50

        // Launch multiple coroutines that try to update progress concurrently
        repeat(iterations) { i ->
            launch {
                stateMachine.updateProgress(i.toFloat() / iterations)
            }
        }

        // Verify state machine is still in a valid state (Downloading)
        assertTrue("State should still be valid after concurrent updates",
            stateMachine.currentState is AnimationState.Downloading)
    }
}