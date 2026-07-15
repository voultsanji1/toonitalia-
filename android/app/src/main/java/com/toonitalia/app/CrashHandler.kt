package com.toonitalia.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val crashFile: File = File(context.filesDir, "crash_log.txt")
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    init {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val timestamp = formatter.format(Date())

        try {
            FileWriter(crashFile).use { writer ->
                writer.write("=== CRASH REPORT ===\n")
                writer.write("Time: $timestamp\n")
                writer.write("Thread: ${thread.name}\n")
                writer.write("Exception: ${ex.javaClass.name}\n")
                writer.write("Message: ${ex.message}\n\n")
                writer.write("=== STACK TRACE ===\n")
                ex.printStackTrace(printWriter(writer))
                writer.write("\n=== CAUSE CHAIN ===\n")
                var cause = ex.cause
                while (cause != null) {
                    writer.write("Caused by: ${cause.javaClass.name}: ${cause.message}\n")
                    cause.printStackTrace(printWriter(writer))
                    cause = cause.cause
                }
            }
        } catch (e: Exception) {
            Log.e("CrashHandler", "Failed to save crash", e)
        }

        defaultHandler?.uncaughtException(thread, ex)
    }

    private fun printWriter(writer: FileWriter): java.io.PrintWriter {
        return java.io.PrintWriter(writer)
    }
}