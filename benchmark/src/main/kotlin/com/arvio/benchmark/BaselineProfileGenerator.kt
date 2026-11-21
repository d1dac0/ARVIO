package com.arvio.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generate() {
        val fixture = requireNotNull(TvPlaybackFixture.fromArguments()) {
            "Set baselineFixtureTitle and baselineFixtureSource for the prepared Android TV fixture."
        }
        baselineRule.collect(
            packageName = ARVIO_PACKAGE,
            maxIterations = 5
        ) {
            pressHome()
            startActivityAndWait()
            fixture.awaitHome(device)
            fixture.moveAcrossHome(device)
            fixture.playFromHome(device)
            fixture.visitTopLevelScreens(device)
        }
    }
}
