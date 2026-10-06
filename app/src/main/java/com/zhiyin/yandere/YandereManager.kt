package com.zhiyin.yandere

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 病娇模式管理器（2026-10-05，参考"爱语"App 设计）。
 *
 * 功能：
 * 1. 开关持久化（SharedPreferences）
 * 2. 读取手机App使用情况 → 作为AI上下文（嫉妒触发）
 * 3. 设备管理器锁屏（AI输出 [LOCK] 时触发）
 * 4. [LOCK] 标记检测与清除
 *
 * 工作流：
 *   用户开启病娇模式 → 请求使用情况权限（必选）+ 设备管理器（可选）
 *   → 聊天时把 yandere_mode=true + usage_stats 摘要随请求发出
 *   → 服务端在 system prompt 追加病娇人格覆盖
 *   → AI 回复末尾可能带 [LOCK] → 客户端检测到就锁屏 → 清除标记
 */
object YandereManager {

    private const val TAG = "Yandere"
    private const val PREFS = "yandere_prefs"
    private const val KEY_ENABLED = "yandere_enabled"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, v).apply()

    // ==================== 使用情况访问权限 ====================

    fun hasUsageStatsPermission(ctx: Context): Boolean = try {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            ctx.packageName
        )
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    /**
     * 跳系统「使用情况访问」授权页。
     *
     * ⚠️ 前提：AndroidManifest 里必须声明 `PACKAGE_USAGE_STATS`。
     * 该权限是 signature|privileged 级，普通应用拿不到"自动授予"，
     * 但**不声明的话，系统的使用情况访问列表里根本不会列出本应用**，
     * 用户就会怎么找都找不到"灵心" → 永远授权不了（2026-10-06 实测踩到）。
     *
     * 先试带 `package:` 的（HyperOS / 部分 ROM 能直达本应用那一项，
     * 省掉"在长列表里找灵心"这一步），失败再退回整个列表。
     */
    fun requestUsageStatsPermission(ctx: Context): Boolean {
        val tries = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        for (base in tries) {
            try {
                ctx.startActivity(base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // 该 ROM 不认这个形式，试下一个
            }
        }
        Log.w(TAG, "requestUsageStatsPermission: 所有跳转形式都失败")
        return false
    }

    /** 读取今天的App使用情况摘要，格式："微信:2h15m, 抖音:45m, ..." */
    fun getUsageStatsSummary(ctx: Context): String {
        if (!hasUsageStatsPermission(ctx)) return "（未授权使用情况访问，无法读取）"
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            val start = cal.timeInMillis
            val end = System.currentTimeMillis()

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            if (stats == null || stats.isEmpty()) return "今天暂无使用记录"

            // 按总使用时间排序，取前8个
            val sorted = stats.sortedByDescending { it.totalTimeInForeground }
            val sb = StringBuilder()
            var count = 0
            for (s in sorted) {
                if (s.totalTimeInForeground < 60_000) continue // <1分钟跳过
                if (count >= 8) break
                val pkg = s.packageName ?: continue
                val name = getAppName(ctx, pkg)
                val minutes = s.totalTimeInForeground / 60_000
                val h = minutes / 60
                val m = minutes % 60
                val time = if (h > 0) "${h}h${m}m" else "${m}m"
                if (sb.isNotEmpty()) sb.append(", ")
                sb.append("$name:$time")
                count++
            }
            if (sb.isEmpty()) "今天暂无显著使用记录" else sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "getUsageStats failed: ${e.message}")
            "（读取失败）"
        }
    }

    private val nameCache = HashMap<String, String>()

    private fun getAppName(ctx: Context, pkg: String): String {
        nameCache[pkg]?.let { return it }
        return try {
            val pm = ctx.packageManager
            val name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            nameCache[pkg] = name
            name
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    // ==================== 设备管理器锁屏 ====================

    fun isDeviceAdminActive(ctx: Context): Boolean = try {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        dpm.isAdminActive(android.content.ComponentName(ctx, YandereDeviceAdminReceiver::class.java))
    } catch (e: Exception) {
        false
    }

    /**
     * 请求激活设备管理器：系统会弹出「要激活设备管理权限吗？」确认框。
     *
     * ⚠️ 注意：少数 ROM 不弹确认框，而是直接打开「设备管理应用」列表页，
     * 用户需要在列表里自己找「灵心病娇模式」再点激活。所以 UI 侧要给出兜底文案。
     *
     * @return true = 已成功拉起系统界面；false = 所有跳转都失败（调用方应给出手动路径提示）
     */
    fun requestDeviceAdmin(ctx: Context): Boolean {
        val comp = android.content.ComponentName(ctx, YandereDeviceAdminReceiver::class.java)
        try {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                .putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "开启后，病娇模式下的 Ta 可以在生气时锁住你的手机屏幕。可随时在系统设置里停用。"
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            Log.i(TAG, "requestDeviceAdmin: 已拉起系统确认框")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "requestDeviceAdmin failed: ${e.message}")
        }
        // 兜底：直接跳系统安全设置，用户在「设备管理应用」里手动开
        return try {
            ctx.startActivity(
                Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Log.i(TAG, "requestDeviceAdmin: 已改为打开系统安全设置")
            true
        } catch (e: Exception) {
            Log.w(TAG, "openSecuritySettings failed: ${e.message}")
            false
        }
    }

    /** 锁屏。需要设备管理器权限。 */
    fun lockScreen(ctx: Context): Boolean {
        if (!isDeviceAdminActive(ctx)) {
            Log.w(TAG, "lockScreen: 设备管理器未激活")
            return false
        }
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            dpm.lockNow()
            Log.i(TAG, "lockScreen: 已锁屏")
            true
        } catch (e: Exception) {
            Log.w(TAG, "lockScreen failed: ${e.message}")
            false
        }
    }

    // ==================== [LOCK] 标记检测 ====================

    private val LOCK_MARKER = Regex("\\[LOCK\\]")

    /** 检测AI回复中是否包含 [LOCK]，返回（清除标记后的文本, 是否需要锁屏） */
    fun processLockMarker(reply: String): Pair<String, Boolean> {
        val hasLock = LOCK_MARKER.containsMatchIn(reply)
        val clean = LOCK_MARKER.replace(reply, "").trim()
        return clean to hasLock
    }

    // ==================== 构建聊天上下文 ====================

    /** 构建随聊天请求发出的病娇上下文 */
    fun buildYandereContext(ctx: Context): String {
        if (!isEnabled(ctx)) return ""
        val stats = getUsageStatsSummary(ctx)
        val admin = isDeviceAdminActive(ctx)
        return buildString {
            append("今日App使用：$stats")
            if (!admin) append("\n（未授予设备管理器，无法锁屏——只能口头威胁）")
        }
    }
}
