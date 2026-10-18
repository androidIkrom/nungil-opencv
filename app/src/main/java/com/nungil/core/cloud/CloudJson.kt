package com.nungil.core.cloud

import com.nungil.contract.Box
import com.nungil.contract.Lang
import org.json.JSONArray
import org.json.JSONObject

/** What the cloud said: the sentence to speak, a QR code's text when it saw one, and its own time in ms. */
data class CloudAnswer(val text: String, val qr: String?, val ms: Long)

/**
 * The look-closer wire format, matching cloud/app/handler.py: a request is {"image": base64 JPEG, "question",
 * "lang": "en"|"ko", "box": [l, t, r, b]} (box only when there is one); an answer is {"answer", "qr", "ms", "model"}.
 * Pure (org.json and its own base64), so the JVM tests pin it.
 */
object CloudJson {
    fun request(jpeg: ByteArray, question: String, lang: Lang, box: Box?): String {
        val json = JSONObject()
            .put("image", base64(jpeg))
            .put("question", question)
            .put("lang", if (lang == Lang.KO) "ko" else "en")
        if (box != null) json.put("box", JSONArray().put(box.left.toDouble()).put(box.top.toDouble()).put(box.right.toDouble()).put(box.bottom.toDouble()))
        return json.toString()
    }

    /** Throws IllegalArgumentException when [json] carries no answer text (an error body, or something else). */
    fun answer(json: String): CloudAnswer {
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("not an answer", e)
        }
        val text = o.optString("answer", "")
        require(text.isNotBlank()) { "no answer in the reply" }
        val qr = if (o.isNull("qr")) null else o.optString("qr").takeIf { it.isNotBlank() }
        return CloudAnswer(text, qr, o.optLong("ms", 0L))
    }

    private const val TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Standard base64 with padding; android.util.Base64 is a stub on the JVM and java.util.Base64 needs API 26. */
    fun base64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            out.append(TABLE[b0 shr 2])
            out.append(TABLE[(b0 and 0x03 shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            out.append(if (b1 >= 0) TABLE[(b1 and 0x0F shl 2) or (if (b2 >= 0) b2 shr 6 else 0)] else '=')
            out.append(if (b2 >= 0) TABLE[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}
