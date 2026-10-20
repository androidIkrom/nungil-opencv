package com.nungil.contract.app

import com.nungil.contract.Box
import com.nungil.contract.Facing

/**
 * A screen with a camera. The shell takes the camera commands for it the same way on every such screen (front or
 * back camera, what and who is this, stop and start: CameraCommandPolicy), before the screen's own VoiceHandler.
 */
interface CameraScreen {
    /** The last analysed frame, for "what is this" / "who is this"; null before the first. Any thread. */
    fun lastFrame(): VisionFrame?

    /**
     * The box of the thing the screen is following in its last frames (Find's target), so "look closer" sends
     * that part only; null when there is none, or on a screen that follows nothing. Any thread.
     */
    fun lastTarget(): Box? = null

    /** The camera to switch; null when it is not open (yet), or on a screen that has the back camera only. */
    val switchable: SwitchableCamera?

    /** The screen uses the back camera only (Walk: ARCore owns it; items are learned with it). */
    val backCameraOnly: Boolean get() = false

    /** Scanning, searching, reading, walking or learning is going on (or about to start on its own). */
    val isWorking: Boolean

    /** Pause the work and say so; stay on the screen. */
    fun pause()

    /** Start or resume the work (the screen's "Start"). */
    fun resume()
}

interface SwitchableCamera {
    val facing: Facing

    /** Turn to [to], or to the other camera when null. */
    fun useCamera(to: Facing?)
}
