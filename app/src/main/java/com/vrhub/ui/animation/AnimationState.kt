package com.vrhub.ui.animation

/**
 * Represents the animation state for stickman animations in the UI.
 *
 * This sealed class defines all possible states that the stickman animation
 * can be in, along with progress information for each active state.
 */
sealed class AnimationState {

    /**
     * Idle state - no active download/install operation
     * @param reason The reason why the animation is idle
     */
    data class Idle(val reason: IdleReason = IdleReason.NO_ACTIVE_TASK) : AnimationState()

    /**
     * Reasons why the animation might be idle
     */
    enum class IdleReason {
        NO_ACTIVE_TASK,      // Queue is empty or has no processing tasks
        ALL_TASKS_COMPLETED  // All tasks in queue are completed
    }

    /**
     * Downloading state with progress tracking
     * @param progress Progress from 0.0 to 1.0
     */
    data class Downloading(val progress: Float = 0f) : AnimationState()

    /**
     * Extracting state with progress tracking
     * @param progress Progress from 0.0 to 1.0
     */
    data class Extracting(val progress: Float = 0f) : AnimationState()

    /**
     * Installing state with progress tracking
     * @param progress Progress from 0.0 to 1.0
     */
    data class Installing(val progress: Float = 0f) : AnimationState()

    /**
     * Paused state with reason for pause
     * @param reason The reason why the operation is paused
     */
    data class Paused(val reason: PausedReason = PausedReason.USER_REQUESTED) : AnimationState()

    /**
     * Reasons why an operation might be paused
     */
    enum class PausedReason {
        USER_REQUESTED,
        NETWORK_INTERRUPTED,
        LOW_STORAGE,
        APP_BACKGROUNDED,
        ERROR
    }
}
