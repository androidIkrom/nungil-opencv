package com.nungil.scan

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.nungil.cloud.CloudFrame
import com.nungil.cloud.CloudLook
import com.nungil.contract.Box
import com.nungil.contract.Lang
import com.nungil.contract.app.VisionFrame
import com.nungil.core.cloud.CloudPhrases
import com.nungil.core.lang.LabelNames
import com.nungil.core.scan.ModelShelf
import com.nungil.core.scan.ScanPhrases
import com.nungil.core.scan.SceneRules
import com.nungil.core.trace.TraceEvent
import com.nungil.people.FaceIdentifier
import com.nungil.trace.DecisionTrace
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.hypot

/**
 * "What is this" and "who is this" from a camera screen's last frame, on any camera screen, without leaving it:
 * said in Walk or Find, they used to open Live (the logs). Work runs on its own thread; the models are made on
 * the first question. The answer is said only while [owner] (the screen's view) is still started.
 */
class FrameAnswers(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val speak: (String) -> Unit,
    /** The look-closer cloud, or null when none is set up (local.properties). */
    private val cloud: CloudLook? = CloudLook.fromBuildConfig(),
) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var classifier: ModelShelf.Lease<SceneClassifier>? = null
    private var faces: FaceIdentifier? = null

    /** The detection nearest the middle, else the classifier's name for the middle of the picture. */
    fun what(frame: VisionFrame?, lang: Lang) {
        if (frame == null) return answer(ScanPhrases.nothingYet(lang))
        SceneRules.centerThing(frame.detections)?.let {
            return answer(ScanPhrases.looksLike(LabelNames.name(it.label, lang), lang))
        }
        val bitmap = frame.bitmap ?: return answer(ScanPhrases.unknownThing(lang))
        work {
            val guess = try {
                (classifier ?: SharedModels.classifier(context).also { classifier = it }).model.nameCenter(bitmap)
            } catch (t: Throwable) {
                Log.i(TAG, "Scene classifier failed: ${t.message}")
                null
            }
            if (guess != null) ScanPhrases.looksLike(guess, lang) else ScanPhrases.unknownThing(lang)
        }
    }

    /** The face nearest the middle: its saved name, "I don't know this person", or "I don't see anyone". */
    fun who(frame: VisionFrame?, lang: Lang) {
        val bitmap = frame?.bitmap ?: return answer(ScanPhrases.nothingYet(lang))
        work {
            val seen = try {
                (faces ?: FaceIdentifier(context).also { faces = it }).faces(bitmap)
            } catch (t: Throwable) {
                Log.i(TAG, "Face check failed: ${t.message}")
                null
            }
            val nearest = seen?.minByOrNull { hypot(it.centerX - 0.5f, it.centerY - 0.5f) }
            when {
                seen == null -> ScanPhrases.unknownPerson(lang)
                nearest == null -> ScanPhrases.nobody(lang)
                nearest.name != null -> ScanPhrases.thisIs(nearest.name, lang)
                else -> ScanPhrases.unknownPerson(lang)
            }
        }
    }

    /**
     * "Look closer": the frame goes to the cloud (cloud/) and its two sentences are said. In Find, with the target
     * in sight, only the target [box] with a margin goes (CloudFrame): a smaller upload about the thing itself.
     * Says why not when there is no cloud set up or no internet; Nungil itself keeps working offline either way.
     */
    fun closer(frame: VisionFrame?, lang: Lang, box: Box? = null) {
        val cloud = cloud ?: return answer(CloudPhrases.notSetUp(lang))
        val bitmap = frame?.bitmap ?: return answer(ScanPhrases.nothingYet(lang))
        val rotation = frame.rotation
        answer(CloudPhrases.looking(lang))
        work {
            val jpeg = CloudFrame.jpeg(bitmap, rotation, box)
            val result = cloud.ask(jpeg, CloudPhrases.question(lang), lang, null)
            val spoken = when (result) {
                is CloudLook.Result.Ok -> result.answer.text.ifBlank { CloudPhrases.failed(lang) }
                CloudLook.Result.Offline -> CloudPhrases.offline(lang)
                is CloudLook.Result.Failed -> CloudPhrases.failed(lang)
            }
            val sent = box?.let { String.format(Locale.ROOT, "box %.2f,%.2f-%.2f,%.2f, ", it.left, it.top, it.right, it.bottom) } ?: ""
            DecisionTrace.shared(context).record(
                TraceEvent(
                    System.currentTimeMillis(), "cloud", "frame ${bitmap.width}x${bitmap.height}, $sent${jpeg.size} bytes",
                    when (result) {
                        is CloudLook.Result.Ok -> "answered in ${result.answer.ms} ms" + (result.answer.qr?.let { ", qr $it" } ?: "")
                        CloudLook.Result.Offline -> "offline"
                        is CloudLook.Result.Failed -> "failed: ${result.why}"
                    },
                    "say $spoken",
                ),
            )
            spoken
        }
    }

    private fun work(answer: () -> String) {
        try {
            worker.execute {
                val text = answer()
                main.post { answer(text) }
            }
        } catch (e: RejectedExecutionException) {
            // Closed: the screen is gone.
        }
    }

    private fun answer(text: String) {
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) speak(text)
    }

    override fun close() {
        if (worker.isShutdown) return
        // The models are closed on the worker, after a question still being answered.
        worker.execute {
            runCatching { classifier?.close() }
            runCatching { faces?.close() }
        }
        worker.shutdown()
    }

    private companion object {
        const val TAG = "Nungil"
    }
}
