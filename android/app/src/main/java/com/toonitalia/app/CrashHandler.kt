package com.toonitalia.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    init {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val timestamp = formatter.format(Date())
        val trace = buildString {
            append("=== CRASH REPORT ===\n")
            append("Time: $timestamp\n")
            append("Thread: ${thread.name}\n")
            append("Exception: ${ex.javaClass.name}\n")
            append("Message: ${ex.message}\n\n")
            append("=== STACK TRACE ===\n")
            val sw = StringWriter()
            ex.printStackTrace(java.io.PrintWriter(sw))
            append(sw.toString())
            append("\n=== CAUSE CHAIN ===\n")
            var cause = ex.cause
            while (cause != null) {
                append("Caused by: ${cause.javaClass.name}: ${cause.message}\n")
                val csw = StringWriter()
                cause.printStackTrace(java.io.PrintWriter(csw))
                append(csw.toString())
                cause = cause.cause
            }
        }

        try {
            // Write to internal storage
            writeToFile(File(context.filesDir, "crash_log.txt"), trace)
        } catch (e: Exception) {
            Log.e("CrashHandler", "Failed to write internal", e)
        }

        try {
            // Write to external storage (accessible via file manager)
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) {
                writeToFile(File(extDir, "crash_log.txt"), trace)
            }
        } catch (e: Exception) {
            Log.e("CrashHandler", "Failed to write external", e)
        }

        try {
            // Also try Downloads-like location
            val dlDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (dlDir != null) {
                dlDir.mkdirs()
                writeToFile(File(dlDir, "toonitalia_crash.txt"), trace)
            }
        } catch (e: Exception) {
            Log.e("CrashHandler", "Failed to write documents", e)
        }

        try {
            Toast.makeText(context, "App crashata: ${ex.message}", Toast.LENGTH_LONG).show()
            Thread.sleep(2000)
        } catch (e: Exception) {
            // ignore
        }

        defaultHandler?.uncaughtException(thread, ex)
    }

    private fun writeToFile(file: File, content: String) {
        FileWriter(file).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    private class StringWriter : java.io.StringWriter()
}
