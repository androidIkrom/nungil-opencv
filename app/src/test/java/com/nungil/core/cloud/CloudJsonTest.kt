package com.nungil.core.cloud

import com.nungil.contract.Box
import com.nungil.contract.Lang
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CloudJsonTest {
    private val jpeg = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70)

    @Test fun requestCarriesImageQuestionLangAndBox() {
        val json = JSONObject(CloudJson.request(jpeg, "What is here?", Lang.KO, Box(0.1f, 0.2f, 0.3f, 0.4f)))
        assertArrayEquals(jpeg, Base64.getDecoder().decode(json.getString("image")))
        assertEquals("What is here?", json.getString("question"))
        assertEquals("ko", json.getString("lang"))
        val box = json.getJSONArray("box")
        assertEquals(4, box.length())
        assertEquals(0.1, box.getDouble(0), 1e-6)
        assertEquals(0.4, box.getDouble(3), 1e-6)
    }

    @Test fun requestWithoutABoxHasNoBoxKey() {
        val json = JSONObject(CloudJson.request(jpeg, "q", Lang.EN, null))
        assertEquals("en", json.getString("lang"))
        assertFalse(json.has("box"))
    }

    @Test fun answerParsesTextQrAndMs() {
        val a = CloudJson.answer("""{"answer":"A door ahead.","qr":null,"ms":1234,"model":"amazon.nova-lite-v1:0"}""")
        assertEquals("A door ahead.", a.text)
        assertNull(a.qr)
        assertEquals(1234L, a.ms)
        val b = CloudJson.answer("""{"answer":"Exit sign.","qr":"https://x.example","ms":2}""")
        assertEquals("https://x.example", b.qr)
    }

    @Test fun answerWithoutTextIsAnError() {
        val thrown = runCatching { CloudJson.answer("""{"error":"bad key"}""") }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }
}
