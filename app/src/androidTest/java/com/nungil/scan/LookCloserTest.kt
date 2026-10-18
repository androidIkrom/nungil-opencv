package com.nungil.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.BuildConfig
import com.nungil.cloud.CloudLook
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.app.VisionFrame
import com.nungil.core.cloud.CloudPhrases
import com.nungil.debug.ReplayHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** "Look closer" end to end from FrameAnswers: not set up, offline, and (when a URL is built in) answered. */
@RunWith(AndroidJUnit4::class)
class LookCloserTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun frame(): VisionFrame {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val name = assets.list("desks").orEmpty().sorted().first()
        val photo = assets.open("desks/$name").use { BitmapFactory.decodeStream(it) }!!
        return VisionFrame(emptyList(), photo, photo.width, photo.height, Facing.BACK, null, 60f, System.currentTimeMillis(), 0L)
    }

    /** Runs closer() on the activity and collects what it would say, until [count] phrases or 40 s. */
    private fun spokenBy(cloud: CloudLook?, count: Int): List<String> {
        val spoken = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(count)
        var answers: FrameAnswers? = null
        val frame = frame()
        host.scenario.onActivity { activity ->
            answers = FrameAnswers(activity, StartedOwner(), { spoken += it; latch.countDown() }, cloud)
            answers!!.closer(frame, Lang.EN)
        }
        latch.await(40, TimeUnit.SECONDS)
        host.scenario.onActivity { answers?.close() }
        return spoken
    }

    /** A screen that is on: FrameAnswers speaks only while its owner is started. Made on the main thread. */
    private class StartedOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun connected(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.activeNetwork != null
    }

    @Test fun notSetUpWhenNoCloud() {
        assertEquals(listOf(CloudPhrases.notSetUp(Lang.EN)), spokenBy(null, 1))
    }

    @Test fun offlineWithoutNetwork() {
        assumeTrue("needs the emulator's network off (svc wifi disable; svc data disable)", !connected())
        val spoken = spokenBy(CloudLook("http://10.255.255.1:9", "k", connectTimeoutMs = 1500), 2)
        assertEquals(listOf(CloudPhrases.looking(Lang.EN), CloudPhrases.offline(Lang.EN)), spoken)
    }

    @Test fun answersWhenDeployed() {
        assumeTrue("needs NUNGIL_CLOUD_URL in local.properties", BuildConfig.NUNGIL_CLOUD_URL.isNotBlank())
        assumeTrue("needs the network", connected())
        val spoken = spokenBy(CloudLook.fromBuildConfig(), 2)
        assertEquals(2, spoken.size)
        assertEquals(CloudPhrases.looking(Lang.EN), spoken[0])
        assertNotEquals(CloudPhrases.failed(Lang.EN), spoken[1])
        assertNotEquals(CloudPhrases.offline(Lang.EN), spoken[1])
        assertTrue(spoken[1], spoken[1].isNotBlank())
        val traces = File(context.filesDir, "trace").listFiles().orEmpty().flatMap { it.readLines() }
        assertTrue(traces.any { it.contains("\"mode\":\"cloud\"") })
    }
}
