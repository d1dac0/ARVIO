package com.arflix.tv.updater

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.arflix.tv.R
import java.io.File

object ApkInstaller {
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
    }

    /**
     * Check if installing the given APK would cause a signature conflict.
     * Returns a user-friendly error message if there's a conflict, or null if OK.
     */
    fun checkSignatureConflict(context: Context, apkFile: File): String? {
        try {
            val pm = context.packageManager

            // Get the signing info of the APK to install
            val apkInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }

            val apkPackage = apkInfo?.packageName ?: return null

            // Get the signing info of the currently installed app
            val installedInfo: PackageInfo? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(apkPackage, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(apkPackage, PackageManager.GET_SIGNATURES)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                return null // Not installed, no conflict possible
            }

            // Compare signatures
            val installedSigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo?.signingInfo?.apkContentsSigners?.map { it.toByteArray().contentHashCode() } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                installedInfo?.signatures?.map { it.toByteArray().contentHashCode() } ?: emptyList()
            }

            val apkSigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                apkInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray().contentHashCode() } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                apkInfo.signatures?.map { it.toByteArray().contentHashCode() } ?: emptyList()
            }

            if (installedSigs.isNotEmpty() && apkSigs.isNotEmpty() && installedSigs != apkSigs) {
                return context.getString(
                    R.string.update_error_signature_conflict,
                    context.applicationInfo.loadLabel(pm)
                )
            }
        } catch (_: Exception) {
            // Can't check — proceed with install attempt
        }
        return null
    }

    /**
     * Build an intent to uninstall the current app (user must confirm).
     */
    fun buildUninstallIntent(context: Context): Intent {
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Hands the downloaded APK to Android's package installer, the same way installing an APK
     * from a file manager does. The system installer then shows its own screens: a
     * confirmation, an "Installing..." progress screen, and when it finishes "App installed"
     * with an Open button, so the viewer can see the update finish and relaunch ARVIO.
     *
     * A PackageInstaller session (used before) could also install the baseline profile as
     * base.dm, but on a session the system shows only the confirmation: ARVIO closed with no
     * sign of when the install had finished.
     *
     * Returns false when no installer accepted the APK.
     */
    fun launchInstall(context: Context, apkFile: File, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        val target = installerTarget(sdkInt)
        val uri = try {
            when (target.uriKind) {
                InstallerUriKind.FILE_PROVIDER -> FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apkFile
                )
                InstallerUriKind.LEGACY_FILE -> Uri.fromFile(copyToSharedCache(context, apkFile))
            }
        } catch (e: Exception) {
            System.err.println("[ApkInstaller] Could not expose the APK to the installer: ${e.message}")
            return false
        }
        for (action in target.actions) {
            val intent = Intent(action)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (target.uriKind == InstallerUriKind.FILE_PROVIDER) {
                intent.clipData = ClipData.newRawUri("ARVIO update", uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                System.err.println("[ApkInstaller] Installer rejected $action: ${e.message}")
            }
        }
        return false
    }

    /**
     * Android 6's installer only accepts APKs through `file:` URIs, from storage it can read;
     * Android 7+ takes `content:` URIs and rejects exposed `file:` ones. OEM installers differ
     * on which action they register, so ACTION_VIEW is tried first, then ACTION_INSTALL_PACKAGE.
     */
    @Suppress("DEPRECATION")
    internal fun installerTarget(sdkInt: Int): InstallerTarget = InstallerTarget(
        uriKind = if (sdkInt <= Build.VERSION_CODES.M) InstallerUriKind.LEGACY_FILE else InstallerUriKind.FILE_PROVIDER,
        actions = listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE),
    )

    internal enum class InstallerUriKind { FILE_PROVIDER, LEGACY_FILE }

    internal data class InstallerTarget(val uriKind: InstallerUriKind, val actions: List<String>)

    /** The app's cache is private, so on Android 6 the installer reads a copy in external cache. */
    private fun copyToSharedCache(context: Context, apkFile: File): File {
        val root = context.externalCacheDir ?: error("External cache is unavailable")
        val dir = File(root, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return apkFile.copyTo(File(dir, apkFile.name), overwrite = true)
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
