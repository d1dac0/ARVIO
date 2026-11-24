package com.arflix.tv.updater

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ApkInstallerTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var apk: File
    private lateinit var context: Context
    private val contentUri = Uri.parse("content://com.arvio.tv.fileprovider/updates/ARVIO.apk")
    private val started = mutableListOf<Intent>()

    @Before fun setUp() {
        apk = File.createTempFile("updater-test", ".apk", app.cacheDir)
        context = mockk(relaxed = true)
        every { context.packageName } returns "com.arvio.tv"
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(context, "com.arvio.tv.fileprovider", apk) } returns contentUri
    }

    @After fun tearDown() {
        unmockkAll()
        apk.delete()
    }

    @Test fun `opens the system installer with a readable content uri`() {
        every { context.startActivity(capture(started)) } returns Unit

        assertThat(ApkInstaller.launchInstall(context, apk)).isTrue()

        val intent = started.single()
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data).isEqualTo(contentUri)
        assertThat(intent.type).isEqualTo("application/vnd.android.package-archive")
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(intent.clipData?.getItemAt(0)?.uri).isEqualTo(contentUri)
    }

    @Test fun `falls back to ACTION_INSTALL_PACKAGE when no app handles ACTION_VIEW`() {
        every { context.startActivity(match { it.action == Intent.ACTION_VIEW }) } throws ActivityNotFoundException()
        @Suppress("DEPRECATION")
        every { context.startActivity(match { it.action == Intent.ACTION_INSTALL_PACKAGE }) } returns Unit

        assertThat(ApkInstaller.launchInstall(context, apk)).isTrue()
        @Suppress("DEPRECATION")
        verify(exactly = 1) { context.startActivity(match { it.action == Intent.ACTION_INSTALL_PACKAGE }) }
    }

    @Test fun `reports failure when no installer accepts the apk`() {
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        assertThat(ApkInstaller.launchInstall(context, apk)).isFalse()
    }

    @Test fun `reports failure when the apk cannot be shared`() {
        every { FileProvider.getUriForFile(context, any(), apk) } throws IllegalArgumentException("not in file_paths")

        assertThat(ApkInstaller.launchInstall(context, apk)).isFalse()
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test fun `android 6 uses file uris and android 7 onwards uses the file provider`() {
        assertThat(ApkInstaller.installerTarget(23).uriKind).isEqualTo(ApkInstaller.InstallerUriKind.LEGACY_FILE)
        assertThat(ApkInstaller.installerTarget(24).uriKind).isEqualTo(ApkInstaller.InstallerUriKind.FILE_PROVIDER)
        assertThat(ApkInstaller.installerTarget(34).uriKind).isEqualTo(ApkInstaller.InstallerUriKind.FILE_PROVIDER)
    }

    @Test fun `android 6 hands the installer a copy in external cache`() {
        val external = File(app.cacheDir, "external-test").apply { mkdirs() }
        every { context.externalCacheDir } returns external
        every { context.startActivity(capture(started)) } returns Unit
        apk.writeBytes(byteArrayOf(1, 2, 3))

        assertThat(ApkInstaller.launchInstall(context, apk, sdkInt = 23)).isTrue()

        val uri = started.single().data!!
        assertThat(uri.scheme).isEqualTo("file")
        assertThat(File(uri.path!!).readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(File(uri.path!!).parentFile).isEqualTo(File(external, "updates"))
        external.deleteRecursively()
    }
}
