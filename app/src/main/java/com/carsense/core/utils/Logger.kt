package com.carsense.core.utils

import android.util.Log

/** Utility class for logging messages across the app */
object Logger {
    private const val DEFAULT_TAG = "CarSense"
    private var isDebugMode = true

    /** Set whether debug logging is enabled */
    fun setDebugMode(isDebug: Boolean) {
        isDebugMode = isDebug
    }

    /** Log a debug message */
    fun d(message: String, tag: String = DEFAULT_TAG) {
        if (isDebugMode) {
            Log.d(tag, message)
        }
    }

    /** Log an info message */
    fun i(message: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, message)
    }

    /** Log a warning message */
    fun w(message: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, message)
    }

    /** Log an error message */
    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        Log.e(tag, message, throwable)
    }

    /** Log a verbose message */
    fun v(message: String, tag: String = DEFAULT_TAG) {
        if (isDebugMode) {
            Log.v(tag, message)
        }
    }

    /** Log an OBD2 message */
    fun obd(message: String) {
        d(message, "CarSense-OBD2")
    }

    /** Log a Bluetooth message */
    fun bluetooth(message: String) {
        d(message, "CarSense-BT")
    }
}
