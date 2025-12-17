package com.nendo.argosy.util

import android.util.Log as AndroidLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Logger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logChannel = Channel<LogEntry>(Channel.BUFFERED)

    private var fileWriter: FileWriter? = null
    private var currentLogDate: LocalDate? = null
    private var logDirectory: String? = null
    private var versionName: String = "unknown"
    private var fileLogLevel: LogLevel = LogLevel.INFO

    @Volatile
    private var fileLoggingEnabled = false

    private val timestampFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        scope.launch {
            logChannel.receiveAsFlow().collect { entry ->
                writeToFile(entry)
            }
        }
    }

    fun configure(
        versionName: String,
        logDirectory: String?,
        enabled: Boolean,
        level: LogLevel
    ) {
        this.versionName = versionName
        this.logDirectory = logDirectory
        this.fileLoggingEnabled = enabled && logDirectory != null
        this.fileLogLevel = level

        if (!fileLoggingEnabled) {
            closeCurrentFile()
        }
    }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        when (level) {
            LogLevel.DEBUG -> if (throwable != null) AndroidLog.d(tag, message, throwable) else AndroidLog.d(tag, message)
            LogLevel.INFO -> if (throwable != null) AndroidLog.i(tag, message, throwable) else AndroidLog.i(tag, message)
            LogLevel.WARN -> if (throwable != null) AndroidLog.w(tag, message, throwable) else AndroidLog.w(tag, message)
            LogLevel.ERROR -> if (throwable != null) AndroidLog.e(tag, message, throwable) else AndroidLog.e(tag, message)
        }

        if (fileLoggingEnabled && level.ordinal >= fileLogLevel.ordinal) {
            val entry = LogEntry(
                timestamp = LocalDateTime.now(),
                level = level,
                tag = tag,
                message = message,
                throwable = throwable
            )
            scope.launch { logChannel.send(entry) }
        }
    }

    private fun writeToFile(entry: LogEntry) {
        val dir = logDirectory ?: return

        val today = entry.timestamp.toLocalDate()
        if (today != currentLogDate) {
            rotateLogFile(dir, today)
        }

        val writer = fileWriter ?: return

        val line = buildString {
            append(entry.timestamp.format(timestampFormat))
            append(" ")
            append(entry.level.name.padEnd(5))
            append(" [")
            append(entry.tag)
            append("] ")
            append(entry.message)
        }

        writer.appendLine(line)

        entry.throwable?.let { t ->
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            writer.appendLine(sw.toString())
        }

        writer.flush()
    }

    private fun rotateLogFile(directory: String, date: LocalDate) {
        closeCurrentFile()

        val fileName = "$versionName-${date.format(dateFormat)}.log"
        val logFile = File(directory, fileName)

        try {
            logFile.parentFile?.mkdirs()
            fileWriter = FileWriter(logFile, true)
            currentLogDate = date

            fileWriter?.appendLine("=== Argosy $versionName - ${date.format(dateFormat)} ===")
            fileWriter?.flush()
        } catch (e: Exception) {
            AndroidLog.e("Logger", "Failed to create log file", e)
            fileWriter = null
        }
    }

    private fun closeCurrentFile() {
        try {
            fileWriter?.close()
        } catch (_: Exception) { }
        fileWriter = null
        currentLogDate = null
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    fun next(): LogLevel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromString(value: String?): LogLevel =
            entries.find { it.name == value } ?: INFO
    }
}

private data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)
