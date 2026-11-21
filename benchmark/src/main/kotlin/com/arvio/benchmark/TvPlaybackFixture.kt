package com.arvio.benchmark

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val ARVIO_PACKAGE = "com.arvio.tv"

/**
 * Use a prepared, English-language Android TV with an adult profile, no IPTV providers,
 * and a seekable test video (at least three minutes) visible in the first Home rail.
 * Set baselineFixtureTitle to its Home card label and baselineFixtureSource to the exact
 * label of its Sources row. Set baselineFixtureProfile when Home requires profile selection.
 */
internal class TvPlaybackFixture private constructor(
    private val title: String,
    private val source: String,
    private val profile: String?
) {
    fun awaitHome(device: UiDevice) {
        if (profile != null && !device.wait(Until.hasObject(homeCard()), 2_000L)) {
            device.wait(Until.findObject(By.desc(profile)), 8_000L)?.click()
        }
        device.requireVisible(homeCard(), "the fixture's Home card")
    }

    fun moveAcrossHome(device: UiDevice) {
        device.move(KeyEvent.KEYCODE_DPAD_UP, 3)
        device.move(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        device.move(KeyEvent.KEYCODE_DPAD_RIGHT, 6)
        device.move(KeyEvent.KEYCODE_DPAD_LEFT, 6)
        device.move(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        device.move(KeyEvent.KEYCODE_DPAD_UP, 3)
        device.requireVisible(homeCard(), "the fixture's Home card after rail navigation")
    }

    fun playFromHome(device: UiDevice) {
        device.requireVisible(homeCard(), "the fixture's Home card").click()
        device.requireVisible(detailsAction(), "the Details actions")
        device.pressDPadRight()
        device.requireVisible(By.text("Sources"), "the focused Details Sources action")
        device.pressDPadCenter()
        device.requireVisible(By.text("Filter sources"), "the populated Sources picker")
        device.requireVisible(By.text(source), "the fixture source row").click()
        device.requireVisible(By.desc("Pause"), "active playback")
        SystemClock.sleep(2_000L)
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        SystemClock.sleep(750L)

        // Back may first close an overlay and then arm the player's exit confirmation.
        repeat(4) {
            if (!device.hasObject(detailsAction())) {
                device.pressBack()
                SystemClock.sleep(300L)
            }
        }
        device.requireVisible(detailsAction(), "Details after leaving playback")
        device.pressBack()
        awaitHome(device)
    }

    fun visitTopLevelScreens(device: UiDevice) {
        // The right edge of the top bar is Settings, followed by TV, Library, Home and Search.
        visitTopLevel(device, fromRight = 0, marker = By.textStartsWith("ARVIO V"))
        visitTopLevel(device, fromRight = 1, marker = By.text("No IPTV playlist configured."))
        visitTopLevel(device, fromRight = 4, marker = By.desc("Search by voice"))
    }

    private fun visitTopLevel(device: UiDevice, fromRight: Int, marker: BySelector) {
        device.move(KeyEvent.KEYCODE_DPAD_UP, 8)
        device.move(KeyEvent.KEYCODE_DPAD_RIGHT, 7)
        device.move(KeyEvent.KEYCODE_DPAD_LEFT, fromRight)
        device.pressDPadCenter()
        device.requireVisible(marker, "the destination at top-bar offset $fromRight")
        device.pressBack()
        awaitHome(device)
    }

    private fun homeCard(): BySelector = By.desc(title).clickable(true)

    private fun detailsAction(): BySelector =
        By.text(Pattern.compile("^(Sources|Play|Resume|Continue)(\\b.*)?$")).clickable(true)

    private fun UiDevice.requireVisible(selector: BySelector, description: String) =
        checkNotNull(wait(Until.findObject(selector), 15_000L)) { "Timed out waiting for $description." }

    private fun UiDevice.move(key: Int, count: Int) {
        repeat(count) {
            pressKeyCode(key)
            SystemClock.sleep(150L)
        }
    }

    companion object {
        fun fromArguments(): TvPlaybackFixture? {
            val args = InstrumentationRegistry.getArguments()
            val title = args.getString("baselineFixtureTitle") ?: return null
            val source = args.getString("baselineFixtureSource") ?: return null
            val uiMode = InstrumentationRegistry.getInstrumentation().context
                .getSystemService(UiModeManager::class.java)
            check(uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                "Use a connected Android TV or an Android TV AVD."
            }
            return TvPlaybackFixture(title, source, args.getString("baselineFixtureProfile"))
        }
    }
}
