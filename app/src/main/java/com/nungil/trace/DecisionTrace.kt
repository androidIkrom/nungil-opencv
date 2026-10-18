package com.nungil.trace

import android.content.Context
import android.util.Log
import com.nungil.core.trace.TraceEvent
import com.nungil.core.trace.TraceLine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The decision trace: every perception-to-action step of Find, Walk and "look closer" appended as one JSON line
 * to `files/trace/<yyyy-MM-dd>.jsonl`, off the caller's thread. Pulled with
 * `adb exec-out run-as com.nungil cat files/trace/<date>.jsonl` and drawn by `tools/trace/plot.py`. Never throws
 * at the caller: a full disk costs a trace line, not a beep.
 */
class DecisionTrace private constructor(private val dir: File) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "nungil-trace").apply { isDaemon = true } }
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var pruned = false

    fun record(e: TraceEvent) {
        val line = TraceLine.json(e)
        val name = day.format(Date(e.atMs)) + ".jsonl"
        try {
            executor.execute {
                try {
                    dir.mkdirs()
                    if (!pruned) {
                        pruned = true
                        prune()
                    }
                    File(dir, name).appendText(line + "\n")
                } catch (t: Throwable) {
                    Log.w(TAG, "trace line lost", t)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "trace line lost", t)
        }
    }

    /** Waits for the lines recorded so far to be on disk (tests). */
    fun flush() {
        try {
            executor.submit {}.get(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            Log.w(TAG, "trace flush", t)
        }
    }

    /** Trace files older than [KEEP_DAYS] are deleted: a blind user's phone is not a log server. */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600 * 1000
        dir.listFiles { f -> f.name.endsWith(".jsonl") && f.lastModified() < cutoff }?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "NungilTrace"
        const val KEEP_DAYS = 7
        private var shared: DecisionTrace? = null

        /** A trace of its own in [dir] (tests). */
        fun at(dir: File): DecisionTrace = DecisionTrace(dir)

        @Synchronized
        fun shared(context: Context): DecisionTrace =
            shared ?: DecisionTrace(File(context.applicationContext.filesDir, "trace")).also { shared = it }
    }
}
