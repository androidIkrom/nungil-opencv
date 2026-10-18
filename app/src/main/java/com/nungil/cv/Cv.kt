package com.nungil.cv

import android.util.Log
import androidx.annotation.VisibleForTesting
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

/**
 * OpenCV 5's native library, loaded once. Every OpenCV step in the app asks [ensure] first and takes its old
 * pure-Kotlin path when the answer is false (an ABI the APK does not carry, a broken install): nothing the
 * user hears depends on the library being there.
 */
object Cv {
    private const val TAG = "NungilCv"

    /** Tests of the fallback paths: while true, [ensure] says false. */
    @VisibleForTesting
    @Volatile
    var forceOff: Boolean = false

    private var loaded: Boolean? = null

    /** [Core.VERSION] after a true [ensure], else null. */
    var version: String? = null
        private set

    @Synchronized
    fun ensure(): Boolean {
        if (forceOff) return false
        loaded?.let { return it }
        val ok = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            Log.w(TAG, "OpenCV native library did not load", t)
            false
        }
        if (ok) {
            version = Core.VERSION
            Log.i(TAG, "OpenCV ${Core.VERSION} loaded")
        } else {
            Log.w(TAG, "OpenCV not available: pure-Kotlin paths in use")
        }
        loaded = ok
        return ok
    }
}
