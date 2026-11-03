package com.nova.anime

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Persistent app-private copies prevent expired content:// grants from breaking episode playback. */
object NativeVideoStore {
    private const val PREFS = "nova_native_media_v2"
    private const val LEGACY_PREFS = "nova_episode_media"
    private const val LEGACY_PREFIX = "episode_uri:"
    private val lock = Any()

    fun importVideo(context: Context, uri: Uri, key: String): File = synchronized(lock) {
        val name = displayName(context, uri)
        val folder = File(context.filesDir, "nova_media")
        if (!folder.exists() && !folder.mkdirs()) error("NOVA ভিডিও ফোল্ডার তৈরি করতে পারেনি")
        val estimated = sizeOf(context, uri)
        val available = StatFs(folder.absolutePath).availableBytes
        if (estimated > 0L && estimated > available - 8L * 1024L * 1024L) {
            error("ফোনের ফাঁকা জায়গা কম। ভিডিওটির জন্য আরও জায়গা খালি করো")
        }
        val clean = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().takeLast(100).ifBlank { "video" }
        val target = File(folder, "${UUID.randomUUID()}_$clean")
        val input = context.contentResolver.openInputStream(uri) ?: error("Android ভিডিও ফাইলটি পড়তে পারেনি")
        var success = false
        try {
            input.use { source ->
                FileOutputStream(target).use { output ->
                    source.copyTo(output, 1024 * 1024)
                    output.fd.sync()
                }
            }
            success = target.isFile && target.length() > 0L
            if (!success) error("কপি করা ভিডিও ফাইলটি খালি")
        } finally {
            if (!success) target.delete()
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldPath = prefs.getString(key, null)
        prefs.edit().putString(key, target.absolutePath).apply()
        deleteOwnedFile(context, oldPath)
        target
    }

    fun find(context: Context, key: String): File? {
        val path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return null
        return File(path).takeIf { it.isFile && it.length() > 0L }
    }

    fun findOrMigrateLegacyEpisode(context: Context, episodeKey: String): File? {
        find(context, "episode:$episodeKey")?.let { return it }
        synchronized(lock) {
            find(context, "episode:$episodeKey")?.let { return it }
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .getString(LEGACY_PREFIX + episodeKey, null) ?: return null
            // Migrate a private path from a previous NOVA build, or copy its stored URI.
            if (legacy.startsWith("/")) {
                val oldFile = File(legacy)
                if (oldFile.isFile && oldFile.length() > 0L) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("episode:$episodeKey", oldFile.absolutePath).apply()
                    return oldFile
                }
            }
            return try {
                importVideo(context, Uri.parse(legacy), "episode:$episodeKey")
            } catch (e: Exception) {
                android.util.Log.w("NOVA", "Legacy episode video could not be migrated", e)
                null
            }
        }
    }

    fun delete(context: Context, key: String) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldPath = prefs.getString(key, null)
        prefs.edit().remove(key).apply()
        deleteOwnedFile(context, oldPath)
    }

    fun bytes(context: Context): Long {
        val dir = File(context.filesDir, "nova_media")
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    private fun deleteOwnedFile(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path).canonicalFile
            val root = File(context.filesDir, "nova_media").canonicalFile
            if (file.parentFile == root && file.isFile) file.delete()
        } catch (_: Exception) { }
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
        return name?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "video")
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }
}
