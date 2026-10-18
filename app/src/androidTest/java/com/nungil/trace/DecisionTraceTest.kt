package com.nungil.trace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nungil.core.trace.TraceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The trace lands in the app's files as JSON Lines, one event per line, in order, and never throws at the caller. */
@RunWith(AndroidJUnit4::class)
class DecisionTraceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun eventsAreAppendedAsJsonLines() {
        val trace = DecisionTrace.shared(context)
        val dir = File(context.filesDir, "trace")
        dir.listFiles()?.forEach { it.delete() }
        val mark = System.currentTimeMillis()
        trace.record(TraceEvent(mark, "find", "bag 0.4,0.5", "pulse 300", "beep"))
        trace.record(TraceEvent(mark + 1, "walk", "depth ok", "wall:ahead", "say Wall ahead, 3 steps"))
        trace.flush()
        val file = dir.listFiles()!!.single { it.name.endsWith(".jsonl") }
        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("{\"at\":$mark,\"mode\":\"find\""))
        assertTrue(lines[1], lines[1].contains("\"action\":\"say Wall ahead, 3 steps\""))
    }
}
