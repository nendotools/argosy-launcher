package com.nendo.argosy.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalState { NORMAL, THROTTLED, PAUSED }

data class ThermalStatus(
    val state: ThermalState = ThermalState.NORMAL,
    val cpuTemp: Float = 0f,
    val batteryTemp: Float = 0f,
    val throttleMultiplier: Float = 1.0f
)

@Singleton
class DownloadThermalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) {
    private val _thermalStatus = MutableStateFlow(ThermalStatus())
    val thermalStatus: StateFlow<ThermalStatus> = _thermalStatus.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var fanRefreshJob: Job? = null
    private var isScreenOff = false
    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(screenReceiver, filter)
            isReceiverRegistered = true
        }
        observeDownloads()
    }

    fun stop() {
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(screenReceiver) }
            isReceiverRegistered = false
        }
        monitorJob?.cancel()
        fanRefreshJob?.cancel()
        scope.cancel()
    }

    private fun observeDownloads() {
        scope.launch {
            downloadManager.state.collect { state ->
                val hasActiveDownloads = state.activeDownloads.any {
                    it.state == DownloadState.DOWNLOADING
                }
                if (hasActiveDownloads && monitorJob?.isActive != true) {
                    startThermalMonitoring()
                    if (isScreenOff) startFanRefresh()
                } else if (!hasActiveDownloads) {
                    stopThermalMonitoring()
                    fanRefreshJob?.cancel()
                }
            }
        }
    }

    private fun startThermalMonitoring() {
        monitorJob = scope.launch {
            while (isActive) {
                val cpuTemp = readMaxCpuTemp()
                val batteryTemp = readBatteryTemp()
                val newStatus = calculateThermalStatus(cpuTemp, batteryTemp)
                _thermalStatus.value = newStatus
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun stopThermalMonitoring() {
        monitorJob?.cancel()
        _thermalStatus.value = ThermalStatus()
    }

    private fun onScreenOff() {
        isScreenOff = true
        if (hasActiveDownloads()) startFanRefresh()
    }

    private fun onScreenOn() {
        isScreenOff = false
        fanRefreshJob?.cancel()
    }

    private fun startFanRefresh() {
        if (fanRefreshJob?.isActive == true) return
        fanRefreshJob = scope.launch {
            while (isActive && isScreenOff && hasActiveDownloads()) {
                forceFanOn()
                delay(FAN_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun forceFanOn() {
        if (!fanControlAvailable()) return
        runCatching {
            File(FAN_STATE_PATH).writeText("1")
            File(FAN_DUTY_PATH).writeText(FAN_DUTY_DOWNLOAD.toString())
        }
    }

    private fun readMaxCpuTemp(): Float {
        return runCatching {
            File(THERMAL_PATH)
                .listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.mapNotNull { zone ->
                    val type = File(zone, "type").readText().trim()
                    if (type.startsWith("cpu")) {
                        File(zone, "temp").readText().trim().toFloatOrNull()?.div(1000)
                    } else null
                }
                ?.maxOrNull() ?: 0f
        }.getOrDefault(0f)
    }

    private fun readBatteryTemp(): Float {
        return runCatching {
            File(BATTERY_TEMP_PATH).readText().trim().toFloat() / 10f
        }.getOrDefault(0f)
    }

    private fun calculateThermalStatus(cpuTemp: Float, batteryTemp: Float): ThermalStatus {
        val cpuState = when {
            cpuTemp >= CPU_PAUSE_TEMP -> ThermalState.PAUSED
            cpuTemp >= CPU_THROTTLE_TEMP -> ThermalState.THROTTLED
            else -> ThermalState.NORMAL
        }
        val batteryState = when {
            batteryTemp >= BATTERY_PAUSE_TEMP -> ThermalState.PAUSED
            batteryTemp >= BATTERY_THROTTLE_TEMP -> ThermalState.THROTTLED
            else -> ThermalState.NORMAL
        }
        val worstState = maxOf(cpuState, batteryState)
        val multiplier = when (worstState) {
            ThermalState.PAUSED -> 0f
            ThermalState.THROTTLED -> 0.5f
            ThermalState.NORMAL -> 1.0f
        }
        return ThermalStatus(worstState, cpuTemp, batteryTemp, multiplier)
    }

    private fun hasActiveDownloads(): Boolean {
        return downloadManager.state.value.activeDownloads.isNotEmpty()
    }

    private fun fanControlAvailable() = File(FAN_STATE_PATH).exists()

    companion object {
        private const val MONITOR_INTERVAL_MS = 5000L
        private const val FAN_REFRESH_INTERVAL_MS = 30_000L

        private const val CPU_THROTTLE_TEMP = 85f
        private const val CPU_PAUSE_TEMP = 90f
        private const val BATTERY_THROTTLE_TEMP = 38f
        private const val BATTERY_PAUSE_TEMP = 43f

        private const val THERMAL_PATH = "/sys/class/thermal/"
        private const val FAN_STATE_PATH = "/sys/class/gpio5_pwm2/state"
        private const val FAN_DUTY_PATH = "/sys/class/gpio5_pwm2/duty"
        private const val FAN_DUTY_DOWNLOAD = 30000
        private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"
    }
}
