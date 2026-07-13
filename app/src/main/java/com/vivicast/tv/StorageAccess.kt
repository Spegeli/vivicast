package com.vivicast.tv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.vivicast.tv.core.designsystem.R
import java.io.File

/**
 * Filesystem access for the in-app file picker (SAF's document picker is unreliable on Android TV).
 * Plain [File] access, staggered by API level:
 *  - API ≤ 29: legacy storage + runtime WRITE_EXTERNAL_STORAGE (read+write) unlocks the whole filesystem.
 *  - API 30+: MANAGE_EXTERNAL_STORAGE ("All files access") unlocks it; the grant screen can be absent
 *    on some TVs, so [getExternalMediaDirs]'s Android/media dir is always offered as a permission-free
 *    fallback (a file manager / PC can drop files there, and exports can land there too).
 */
object StorageAccess {

    /** A browsable root: a localized label (resolved by the caller) + its directory. [permissionFree]
     * roots (Android/media) are always readable; the others need storage permission first. */
    data class Root(val labelRes: Int, val dir: File, val permissionFree: Boolean = false)

    // On legacy storage (API ≤29) WRITE_EXTERNAL_STORAGE grants read AND write to all external storage,
    // so it's the single permission covering both import (read) and export (write) — READ alone can't
    // write, which is why a picked export folder silently fell back to the app-specific dir. API 30+
    // uses MANAGE_EXTERNAL_STORAGE instead.

    /** True when the whole filesystem is reachable (read+write) via the File API right now. */
    fun hasFullFileAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** API ≤ 29 only: whether we should ask for the runtime storage permission before browsing/writing. */
    fun needsRuntimeStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /** API 30+ only: whether an All-files-access grant screen exists to send the user to. */
    fun canRequestAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return allFilesAccessIntent(context).resolveActivity(context.packageManager) != null ||
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).resolveActivity(context.packageManager) != null
    }

    /** Opens the system "All files access" screen (best effort; some TVs lack it — guard with [canRequestAllFilesAccess]). */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val perApp = allFilesAccessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(perApp) }.isFailure) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun allFilesAccessIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * Permission-free export/import folder under Android/media/<pkg> (unlike Android/data, not hidden
     * from file managers). Falls back to the app-specific external dir if media dirs are unavailable.
     */
    fun mediaFallbackDir(context: Context): File {
        val media = context.externalMediaDirs.firstOrNull()
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(media, "Vivicast").apply { mkdirs() }
    }

    /** Top-level browsable roots for the picker (label resolved by the caller). */
    fun storageRoots(context: Context): List<Root> {
        val roots = LinkedHashMap<String, Root>()
        val internal = Environment.getExternalStorageDirectory()
        if (internal != null && internal.exists()) {
            roots[internal.absolutePath] = Root(R.string.file_picker_root_internal, internal)
        }
        // Removable volumes: each app-specific dir sits at …/<volume>/Android/data/<pkg>/files — walk up 4.
        context.getExternalFilesDirs(null).forEach { f ->
            val vol = f?.parentFile?.parentFile?.parentFile?.parentFile
            if (vol != null && vol.exists() && vol.absolutePath != internal?.absolutePath) {
                roots.getOrPut(vol.absolutePath) { Root(R.string.file_picker_root_removable, vol) }
            }
        }
        val media = mediaFallbackDir(context)
        roots.getOrPut(media.absolutePath) { Root(R.string.file_picker_root_media, media, permissionFree = true) }
        return roots.values.toList()
    }
}
