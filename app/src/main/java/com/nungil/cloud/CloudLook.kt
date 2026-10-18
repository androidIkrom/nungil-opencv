package com.nungil.cloud

import com.nungil.BuildConfig
import com.nungil.contract.Box
import com.nungil.contract.Lang
import com.nungil.core.cloud.CloudAnswer
import com.nungil.core.cloud.CloudJson
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * One question to the look-closer Lambda (cloud/): POST [url]/look with the shared key. Blocking: call on a
 * worker thread. No connection at all is [Result.Offline] (Nungil says so and goes on offline); anything else
 * that is not a 200 with an answer is [Result.Failed].
 */
class CloudLook(
    url: String,
    private val key: String,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    sealed interface Result {
        data class Ok(val answer: CloudAnswer) : Result
        data object Offline : Result
        data class Failed(val why: String) : Result
    }

    private val endpoint = url.trimEnd('/') + "/look"

    fun ask(jpeg: ByteArray, question: String, lang: Lang, box: Box?): Result {
        val body = CloudJson.request(jpeg, question, lang, box).toByteArray(Charsets.UTF_8)
        val connection = try {
            URL(endpoint).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Failed("bad url")
        }
        var connected = false
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json; charset=utf-8")
            connection.setRequestProperty("x-nungil-key", key)
            connection.setFixedLengthStreamingMode(body.size)
            connection.connect()
            connected = true
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code != 200) return Result.Failed("http $code")
            return Result.Ok(CloudJson.answer(text))
        } catch (e: UnknownHostException) {
            return Result.Offline
        } catch (e: ConnectException) {
            return Result.Offline
        } catch (e: SocketTimeoutException) {
            return if (connected) Result.Failed("timeout") else Result.Offline
        } catch (e: IOException) {
            return if (connected) Result.Failed(e.javaClass.simpleName) else Result.Offline
        } catch (e: IllegalArgumentException) {
            return Result.Failed("bad answer")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000

        /** The client for the URL and key in local.properties, or null when no URL is set (then "look closer" says so). */
        fun fromBuildConfig(): CloudLook? =
            BuildConfig.NUNGIL_CLOUD_URL.takeIf { it.isNotBlank() }?.let { CloudLook(it, BuildConfig.NUNGIL_CLOUD_KEY) }
    }
}
