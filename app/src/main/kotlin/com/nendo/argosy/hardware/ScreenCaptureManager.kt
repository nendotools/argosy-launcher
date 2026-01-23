package com.nendo.argosy.hardware

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var pendingResultCode: Int? = null
    private var pendingData: Intent? = null

    fun requestPermission(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        val projectionManager = activity.getSystemService(MediaProjectionManager::class.java)
        launcher.launch(projectionManager.createScreenCaptureIntent())
    }

    fun onPermissionResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            pendingResultCode = resultCode
            pendingData = data
            _hasPermission.value = true
            Log.i(TAG, "Screen capture permission granted")
        } else {
            _hasPermission.value = false
            Log.w(TAG, "Screen capture permission denied")
        }
    }

    fun startCapture() {
        val resultCode = pendingResultCode
        val data = pendingData

        if (resultCode != null && data != null) {
            ScreenCaptureService.start(context, resultCode, data)
            _isCapturing.value = true
            Log.i(TAG, "Screen capture started")
        } else {
            Log.w(TAG, "Cannot start capture: no permission data available")
        }
    }

    fun stopCapture() {
        if (_isCapturing.value) {
            ScreenCaptureService.stop(context)
            _isCapturing.value = false
            Log.i(TAG, "Screen capture stopped")
        }
    }

    fun clearPermission() {
        pendingResultCode = null
        pendingData = null
        _hasPermission.value = false
    }

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }
}
