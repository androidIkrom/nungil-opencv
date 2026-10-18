package com.nungil.core.trace

/**
 * One step of the perception-to-action loop, for the decision trace: what the camera or depth showed ([seen]),
 * what the policy made of it ([decision]) and what the user got ([action]: a beep rate, a buzz, a sentence).
 * [mode] is "find", "walk" or "cloud". [atMs] is wall-clock time.
 */
data class TraceEvent(val atMs: Long, val mode: String, val seen: String, val decision: String, val action: String)

/** A [TraceEvent] as one line of JSON (JSON Lines), with the strings escaped. Pure, so a unit test can pin it. */
object TraceLine {
    fun json(e: TraceEvent): String =
        "{\"at\":${e.atMs},\"mode\":${quote(e.mode)},\"seen\":${quote(e.seen)},\"decision\":${quote(e.decision)},\"action\":${quote(e.action)}}"

    private fun quote(s: String): String {
        val b = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when (c) {
                '"' -> b.append("\\\"")
                '\\' -> b.append("\\\\")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                '\t' -> b.append("\\t")
                else -> if (c < ' ') b.append(String.format("\\u%04x", c.code)) else b.append(c)
            }
        }
        return b.append('"').toString()
    }
}
