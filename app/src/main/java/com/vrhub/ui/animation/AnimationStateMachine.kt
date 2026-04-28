package com.vrhub.ui.animation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State machine for managing stickman animation states.
 *
 * This class provides thread-safe state transitions and ensures that
 * only valid state transitions are allowed. It follows the single
 * responsibility principle, keeping animation state separate from
 * the backend InstallStatus.
 *
 * Thread Safety:
 * - All state transitions are protected by a Mutex to ensure atomicity
 * - StateFlow emissions are thread-safe
 */
class AnimationStateMachine {

    companion object {
        private const val TAG = "AnimationStateMachine"

        /**
         * Checks if a transition from current state to target state is valid.
         */
        fun isValidTransition(from: AnimationState, to: AnimationState): Boolean {
            return when (from) {
                is AnimationState.Idle -> when (to) {
                    is AnimationState.Downloading, is AnimationState.Extracting, is AnimationState.Installing -> true
                    else -> false
                }
                is AnimationState.Downloading -> when (to) {
                    is AnimationState.Extracting, is AnimationState.Paused, is AnimationState.Idle -> true
                    else -> false
                }
                is AnimationState.Extracting -> when (to) {
                    is AnimationState.Installing, is AnimationState.Paused, is AnimationState.Idle -> true
                    else -> false
                }
                is AnimationState.Installing -> when (to) {
                    is AnimationState.Idle, is AnimationState.Paused -> true
                    else -> false
                }
                is AnimationState.Paused -> when (to) {
                    is AnimationState.Downloading, is AnimationState.Extracting,
                    is AnimationState.Installing, is AnimationState.Idle -> true
                    else -> false
                }
            }
        }
    }

    private val mutex = Mutex()

    private val _state = MutableStateFlow<AnimationState>(AnimationState.Idle())
    val state: StateFlow<AnimationState> = _state.asStateFlow()

    /**
     * Current state (non-flow access)
     */
    val currentState: AnimationState
        get() = _state.value

    /**
     * Attempts to transition to a new state.
     *
     * @param newState The target state to transition to
     * @return true if transition was successful, false if invalid
     */
    suspend fun transitionTo(newState: AnimationState): Boolean = mutex.withLock {
        val currentState = _state.value

        if (!isValidTransition(currentState, newState)) {
            logWarning(TAG, "Invalid transition attempted: ${currentState::class.simpleName} -> ${newState::class.simpleName}")
            rejectTransition(newState)
            return@withLock false
        }

        val previousState = _state.value
        _state.value = newState

        logDebug(TAG, "State transition: ${previousState::class.simpleName} -> ${newState::class.simpleName}")
        true
    }

    /**
     * Attempts to transition to a new state synchronously.
     * Uses a simple synchronized block for thread-safety.
     *
     * @param newState The target state to transition to
     * @return true if transition was successful, false if invalid
     */
    @Synchronized
    fun tryTransitionTo(newState: AnimationState): Boolean {
        val currentState = _state.value

        if (!isValidTransition(currentState, newState)) {
            logDebug(TAG, "Invalid transition attempted: ${currentState::class.simpleName} -> ${newState::class.simpleName}")
            return false
        }

        val previousState = _state.value
        _state.value = newState

        logDebug(TAG, "State transition: ${previousState::class.simpleName} -> ${newState::class.simpleName}")
        return true
    }

    /**
     * Updates the progress of the current state.
     * Only states with progress (Downloading, Extracting, Installing) support this.
     *
     * @param progress New progress value (0.0 to 1.0)
     */
    suspend fun updateProgress(progress: Float): Boolean = mutex.withLock {
        val clampedProgress = progress.coerceIn(0f, 1f)

        val newState = when (val current = _state.value) {
            is AnimationState.Downloading -> current.copy(progress = clampedProgress)
            is AnimationState.Extracting -> current.copy(progress = clampedProgress)
            is AnimationState.Installing -> current.copy(progress = clampedProgress)
            else -> {
                logWarning(TAG, "Cannot update progress on state: ${_state.value::class.simpleName}")
                return@withLock false
            }
        }

        _state.value = newState
        true
    }

    /**
     * Resets the state machine to Idle.
     */
    suspend fun reset() = mutex.withLock {
        _state.value = AnimationState.Idle()
        logDebug(TAG, "State machine reset to Idle")
    }

    /**
     * Handles invalid transition logging.
     */
    private fun rejectTransition(attemptedState: AnimationState) {
        logError(TAG, "REJECTED invalid transition to: ${attemptedState::class.simpleName}")
    }

    /**
     * Checks if a transition to the given state would be valid.
     *
     * @param targetState The target state to check
     * @return true if transition would be valid
     */
    fun canTransitionTo(targetState: AnimationState): Boolean {
        return isValidTransition(_state.value, targetState)
    }

    // ========== Safe Logging Wrappers ==========

    /**
     * Wrapper for Log.d that can be safely called in unit tests without mocking Android Log.
     */
    private fun logDebug(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (e: Exception) {
            // Ignore logging errors in test environments
        }
    }

    /**
     * Wrapper for Log.w that can be safely called in unit tests without mocking Android Log.
     */
    private fun logWarning(tag: String, message: String) {
        try {
            Log.w(tag, message)
        } catch (e: Exception) {
            // Ignore logging errors in test environments
        }
    }

    /**
     * Wrapper for Log.e that can be safely called in unit tests without mocking Android Log.
     */
    private fun logError(tag: String, message: String) {
        try {
            Log.e(tag, message)
        } catch (e: Exception) {
            // Log warning when error logging fails - helps detect production issues
            System.err.println("WARNING: Failed to log error to Android Log: $message")
        }
    }
}
