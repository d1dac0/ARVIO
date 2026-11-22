package com.arvio.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TvPlaybackBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun playbackWithBaselineProfile() = measurePlayback(CompilationMode.Partial())

    @Test
    fun playbackWithoutCompilation() = measurePlayback(CompilationMode.None())

    private fun measurePlayback(mode: CompilationMode) {
        val fixture = TvPlaybackFixture.fromArguments()
        assumeNotNull(fixture)
        checkNotNull(fixture)
        benchmarkRule.measureRepeated(
            packageName = ARVIO_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                fixture.awaitHome(device)
            }
        ) {
            fixture.playFromHome(device)
        }
    }
}
