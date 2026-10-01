package com.zhiyin.data

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.zhiyin.logic.net.ApiGateway
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

object LocalArchive {

    data class Favorite(
        val id: Long,
        val sessionId: String,
        val role: String,
        val content: String,
        val time: Long,
    )

    data class DownloadRecord(
        val id: Long,
        val name: String,
        val kind: String,
        val location: String,
        val time: Long,
    )

    private fun favPrefs(ctx: Context) = ctx.getSharedPreferences("zhiyin_favorites", Context.MODE_PRIVATE)
    private fun dlPrefs(ctx: Context) = ctx.getSharedPreferences("zhiyin_downloads", Context.MODE_PRIVATE)

    fun listFavorites(ctx: Context): List<Favorite> {
        return try {
            val arr = JSONArray(favPrefs(ctx).getString("items", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Favorite(
                    id = o.optLong("id"),
                    sessionId = o.optString("sessionId", ""),
                    role = o.optString("role", "ai"),
                    content = o.optString("content", ""),
                    time = o.optLong("time"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addFavorite(ctx: Context, sessionId: String, role: String, content: String, time: Long) {
        try {
            val arr = JSONArray(favPrefs(ctx).getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("content") == content && o.optString("sessionId") == sessionId) return
            }
            arr.put(
                JSONObject()
                    .put("id", System.currentTimeMillis())
                    .put("sessionId", sessionId)
                    .put("role", role)
                    .put("content", content)
                    .put("time", time)
            )
            favPrefs(ctx).edit().putString("items", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun removeFavorite(ctx: Context, id: Long) {
        try {
            val arr = JSONArray(favPrefs(ctx).getString("items", "[]"))
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optLong("id") != id) out.put(arr.get(i))
            }
            favPrefs(ctx).edit().putString("items", out.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun clearFavorites(ctx: Context) {
        favPrefs(ctx).edit().remove("items").apply()
    }

    fun listDownloads(ctx: Context): List<DownloadRecord> {
        return try {
            val arr = JSONArray(dlPrefs(ctx).getString("items", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadRecord(
                    id = o.optLong("id"),
                    name = o.optString("name", ""),
                    kind = o.optString("kind", "file"),
                    location = o.optString("location", ""),
                    time = o.optLong("time"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recordDownload(ctx: Context, name: String, kind: String, location: String) {
        try {
            val arr = JSONArray(dlPrefs(ctx).getString("items", "[]"))
            val out = JSONArray()
            out.put(
                JSONObject()
                    .put("id", System.currentTimeMillis())
                    .put("name", name)
                    .put("kind", kind)
                    .put("location", location)
                    .put("time", System.currentTimeMillis())
            )
            for (i in 0 until arr.length()) out.put(arr.get(i))
            val trimmed = JSONArray()
            for (i in 0 until minOf(out.length(), 200)) trimmed.put(out.get(i))
            dlPrefs(ctx).edit().putString("items", trimmed.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun removeDownload(ctx: Context, id: Long) {
        try {
            val arr = JSONArray(dlPrefs(ctx).getString("items", "[]"))
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optLong("id") != id) out.put(arr.get(i))
            }
            dlPrefs(ctx).edit().putString("items", out.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun saveImageToGallery(ctx: Context, sourcePath: String, onDone: (String?) -> Unit) {
        thread {
            var result: String? = null
            try {
                val bmp = BitmapFactory.decodeFile(sourcePath)
                if (bmp != null) {
                    val displayName = "lingxin_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/灵心")
                        }
                        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            ctx.contentResolver.openOutputStream(uri)?.use {
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                            }
                            result = uri.toString()
                        }
                    } else {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "灵心")
                        if (!dir.exists()) dir.mkdirs()
                        val f = File(dir, displayName)
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                        MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                        result = f.absolutePath
                    }
                    bmp.recycle()
                    if (result != null) recordDownload(ctx, displayName, "image", result)
                }
            } catch (_: Exception) {
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }
    }

    fun saveFile(ctx: Context, name: String, url: String?, onDone: (String?) -> Unit) {
        thread {
            var result: String? = null
            try {
                val cached = File(File(ctx.cacheDir, "downloads"), name)
                val bytes: ByteArray? = if (cached.exists() && cached.length() > 0) {
                    cached.readBytes()
                } else if (url != null) {
                    ApiGateway.getRaw(url, ctx)
                } else null

                if (bytes != null && bytes.isNotEmpty()) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/灵心")
                        }
                        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            result = uri.toString()
                        }
                    } else {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "灵心")
                        if (!dir.exists()) dir.mkdirs()
                        val f = File(dir, name)
                        f.writeBytes(bytes)
                        MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("application/octet-stream"), null)
                        result = f.absolutePath
                    }
                    if (result != null) recordDownload(ctx, name, "file", result)
                }
            } catch (_: Exception) {
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }
    }

    fun loadSavedImage(ctx: Context, location: String): android.graphics.Bitmap? {
        return try {
            if (location.startsWith("content:")) {
                ctx.contentResolver.openInputStream(android.net.Uri.parse(location))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                BitmapFactory.decodeFile(location)
            }
        } catch (_: Exception) {
            null
        }
    }
}
