package com.nendo.argosy.data.emulator

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchRetryTracker @Inject constructor() {
    private var pendingIntent: Intent? = null
    private var launchTimeMs: Long = 0
    private var focusLostTimeMs: Long = 0
    private var hasRetried: Boolean = false

    private val _retryEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val retryEvents: SharedFlow<Intent> = _retryEvents.asSharedFlow()

    fun onLaunchStarted(intent: Intent) {
        pendingIntent = intent
        launchTimeMs = System.currentTimeMillis()
        focusLostTimeMs = 0
        hasRetried = false
    }

    fun onFocusLost() {
        if (pendingIntent != null) {
            focusLostTimeMs = System.currentTimeMillis()
        }
    }

    fun onFocusGained(): Boolean {
        val intent = pendingIntent ?: return false
        val now = System.currentTimeMillis()

        val timeSinceLaunch = now - launchTimeMs
        val timeSinceFocusLost = if (focusLostTimeMs > 0) now - focusLostTimeMs else timeSinceLaunch

        val isQuickReturn = timeSinceLaunch < QUICK_RETURN_THRESHOLD_MS ||
            (focusLostTimeMs > 0 && timeSinceFocusLost < QUICK_RETURN_THRESHOLD_MS)

        if (isQuickReturn && !hasRetried) {
            hasRetried = true
            _retryEvents.tryEmit(intent)
            return true
        }

        clear()
        return false
    }

    fun clear() {
        pendingIntent = null
        launchTimeMs = 0
        focusLostTimeMs = 0
        hasRetried = false
    }

    companion object {
        private const val QUICK_RETURN_THRESHOLD_MS = 3000L
    }
}
