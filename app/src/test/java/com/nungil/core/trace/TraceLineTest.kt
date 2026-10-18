package com.nungil.core.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TraceLineTest {
    @Test fun oneLineOfEscapedJson() {
        val s = TraceLine.json(TraceEvent(12, "find", "bag 0.4,0.5", "pulse 300", "say \"Lost it\"\nnow"))
        assertEquals("""{"at":12,"mode":"find","seen":"bag 0.4,0.5","decision":"pulse 300","action":"say \"Lost it\"\nnow"}""", s)
        assertFalse(s.contains('\n'))
    }

    @Test fun backslashesAreEscapedToo() {
        val backslash = '\\'.toString()
        val s = TraceLine.json(TraceEvent(0, "walk", "a" + backslash + "b", "", ""))
        assertEquals("""{"at":0,"mode":"walk","seen":"a""" + backslash + backslash + """b","decision":"","action":""}""", s)
    }
}
