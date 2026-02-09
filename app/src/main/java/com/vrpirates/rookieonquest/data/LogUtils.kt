package com.vrpirates.rookieonquest.data

import android.util.Log
import com.vrpirates.rookieonquest.BuildConfig

/**
 * Centralized logging utility to prevent log pollution in production.
 * These methods only log when BuildConfig.DEBUG is true, reducing overhead in release builds.
 *
 * Provides consistent logging across the application with conditional execution.
 */
object LogUtils {

    /**
     * Log a debug message.
     * @param tag The tag for the log message.
     * @param message The message to log.
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Log an info message.
     * @param tag The tag for the log message.
     * @param message The message to log.
     */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    /**
     * Log an error message.
     * @param tag The tag for the log message.
     * @param message The message to log.
     * @param throwable Optional throwable for stack trace logging.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
