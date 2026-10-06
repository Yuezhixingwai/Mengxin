package com.zhiyin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 应用内更新：下载 APK 并直接拉起系统安装界面（替代"跳浏览器"）。
 *
 * 流程：canInstall() 检查授权 → download() 下载 → install() 拉起安装
 *
 * ⚠️ 覆盖安装成立的前提（三条缺一不可）：
 *   1. 包名一致（com.zhiyin）
 *   2. 签名一致（同一个 zhiyin-release.keystore；一旦"加固后重签"就装不上，必须卸载重装）
 *   3. versionCode 大于线上已装版本
 *   装不上时系统报"应用未安装"，多半是签名或 versionCode 的问题。
 */
object AppUpdater {

    sealed interface State {
        /** 空闲：无进行中的更新流程 */
        object Idle : State

        /** 下载中，percent = 0..100（服务端未返回 Content-Length 时固定为 0） */
        data class Downloading(val percent: Int) : State

        /** 已下载完成，等待安装 */
        data class Ready(val file: File) : State

        /** 失败，message 面向用户 */
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun reset() {
        _state.value = State.Idle
    }

    /** 是否已获得"安装未知应用"权限（Android 8.0 起才有这个开关） */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** 跳系统「安装未知应用」授权页（带 package: 直达本应用那一项） */
    fun openInstallPermission(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        for (base in candidates) {
            try {
                context.startActivity(base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // 试下一个
            }
        }
        return false
    }

    /**
     * 规范化下载地址：服务端正常下发绝对地址，但老版本可能给相对路径（如 /api/version/download），
     * 直接丢给 OkHttp 会抛 "Expected URL scheme"，这里统一补上域名。
     */
    private fun normalizeUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) return s
        val base = ApiGateway.ZHIYIN_BASE.trimEnd('/')
        if (base.isEmpty()) return null
        return if (s.startsWith("/")) base + s else "$base/$s"
    }

    /**
     * 下载 APK 到 cacheDir/downloads/。
     * @return 下载完成的文件；失败时返回 null，并已把 state 置为 Failed
     */
    suspend fun download(context: Context, url: String, version: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val fullUrl = normalizeUrl(url)
                if (fullUrl == null) {
                    _state.value = State.Failed("下载地址无效，请稍后重试")
                    return@withContext null
                }
                _state.value = State.Downloading(0)
                val dir = File(context.cacheDir, "downloads")
                if (!dir.exists()) dir.mkdirs()

                val safe = version.replace(Regex("[^0-9A-Za-z._-]"), "_").ifEmpty { "latest" }
                val out = File(dir, "lingxin_$safe.apk")
                if (out.exists() && !out.delete()) {
                    // 删不掉就换个名字写，避免写坏上一个包
                }

                val req = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "LingXin/Android")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _state.value = State.Failed("下载失败（HTTP ${resp.code}）")
                        return@withContext null
                    }
                    val body = resp.body
                    if (body == null) {
                        _state.value = State.Failed("下载失败（响应为空）")
                        return@withContext null
                    }
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        _state.value = State.Downloading(pct)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!out.exists() || out.length() < 1024L) {
                    _state.value = State.Failed("安装包不完整，请重试")
                    return@withContext null
                }
                _state.value = State.Ready(out)
                out
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消：不要把取消当成失败弹出来
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed("下载失败：${e.message ?: "网络异常"}")
                null
            }
        }

    /**
     * 用 FileProvider 把 APK 交给系统安装器。
     * @return true = 已拉起安装界面；false = 缺权限或无法拉起（调用方应降级到浏览器）
     */
    fun install(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        if (!canInstall(context)) return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 降级通道：直接开浏览器下载（国产 ROM 拦截应用内安装时用） */
    fun openInBrowser(context: Context, url: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }
}
