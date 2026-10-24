package com.zhiyin.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType
import com.zhiyin.data.AppSession
import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** The system owns this service. A task only runs after a signed-in user starts it in Settings. */
class PhoneAgentService : AccessibilityService() {
    @Volatile private var lockOverlay: View? = null
    @Volatile private var pendingUnlockPackage: String? = null
    private var protectedPackage: String? = null
    private var unlockedPackage: String? = null
    private var failedPins = 0
    private var retryAfter = 0L
    @Volatile private var taskLeftApp = false

    companion object {
        @Volatile var current: PhoneAgentService? = null
            private set
        val status = MutableStateFlow("请先开启无障碍权限")
        private val running = AtomicBoolean(false)
        private val generation = AtomicInteger(0)

        fun stopTask() {
            generation.incrementAndGet()
            running.set(false)
            status.value = "已停止"
        }
    }

    override fun onServiceConnected() {
        AppSession.init(this)
        current = this
        status.value = "已就绪，输入要完成的任务"
        Handler(Looper.getMainLooper()).postDelayed({
            rootInActiveWindow?.packageName?.toString()?.let { pkg ->
                if (AppLockStore.isLocked(this, pkg)) showLockOverlay(pkg)
            }
        }, 300L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") {
            unlockedPackage = null
            removeLockOverlay()
            return
        }
        if (pkg == packageName) {
            if (running.get() && taskLeftApp) {
                stopTask()
                status.value = "已回到灵心，手机操作已停止"
            }
            return
        }
        if (pkg.contains("inputmethod")) return
        if (running.get()) taskLeftApp = true
        if (AppLockStore.isLocked(this, pkg)) {
            if (unlockedPackage != pkg) showLockOverlay(pkg)
        } else {
            if (unlockedPackage != null && unlockedPackage != pkg) unlockedPackage = null
            if (pendingUnlockPackage != null) {
                pendingUnlockPackage = null
                if (running.get()) {
                    stopTask()
                    status.value = "未解锁应用，手机任务已停止"
                }
            }
            if (protectedPackage != null) removeLockOverlay()
        }
    }
    override fun onInterrupt() = stopTask()

    override fun onDestroy() {
        stopTask()
        removeLockOverlay()
        current = null
        super.onDestroy()
    }

    fun refreshAppLock() {
        unlockedPackage = null
        val pkg = protectedPackage ?: rootInActiveWindow?.packageName?.toString() ?: return
        if (AppLockStore.isLocked(this, pkg)) {
            showLockOverlay(pkg)
            return
        }
        if (!AppLockStore.isLocked(this, pkg)) {
            pendingUnlockPackage = null
            removeLockOverlay()
        }
    }

    private fun showLockOverlay(pkg: String) {
        if (protectedPackage == pkg && lockOverlay != null) return
        removeLockOverlay()
        protectedPackage = pkg
        pendingUnlockPackage = pkg
        if (running.get()) status.value = "请先解锁 $pkg，随后继续任务"
        val name = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
        val d = resources.displayMetrics.density
        fun dp(value: Int) = (value * d).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(30), dp(30), dp(30))
            setBackgroundColor(Color.rgb(21, 24, 34))
        }
        panel.addView(TextView(this).apply {
            val owner = AppLockStore.lockDescription(this@PhoneAgentService, pkg)
            text = if (owner != null) "${owner.first}给你的$name 上锁了" else "$name 已锁定"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        panel.addView(TextView(this).apply {
            val reason = AppLockStore.lockDescription(this@PhoneAgentService, pkg)?.second.orEmpty()
            text = (if (reason.isNotBlank()) "理由：$reason\n" else "") + "输入在灵心设置的应用锁 PIN"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(18))
        })
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4–8 位数字 PIN"
            textSize = 20f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            isSingleLine = true
        }
        panel.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(55)))
        val error = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(255, 130, 130))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        panel.addView(error)
        panel.addView(Button(this).apply {
            text = "解锁应用"
            setOnClickListener {
                if (SystemClock.elapsedRealtime() < retryAfter) {
                    error.text = "尝试过多，请稍后再试"
                } else if (AppLockStore.verify(this@PhoneAgentService, input.text.toString())) {
                    failedPins = 0
                    unlockedPackage = pkg
                    pendingUnlockPackage = null
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(input.windowToken, 0)
                    removeLockOverlay()
                } else {
                    input.text.clear()
                    failedPins++
                    if (failedPins >= 5) {
                        retryAfter = SystemClock.elapsedRealtime() + 30_000L
                        failedPins = 0
                    }
                    error.text = "PIN 错误，请重试"
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        panel.addView(Button(this).apply {
            text = "返回桌面"
            setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); removeLockOverlay() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        panel.addView(Button(this).apply {
            text = "打开灵心设置"
            setOnClickListener {
                pendingUnlockPackage = null
                if (running.get()) stopTask()
                removeLockOverlay()
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE,
            android.graphics.PixelFormat.OPAQUE,
        )
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(panel, params)
            lockOverlay = panel
        } catch (_: Exception) {
            protectedPackage = null
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun removeLockOverlay() {
        lockOverlay?.let {
            try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
            catch (_: Exception) { }
        }
        lockOverlay = null
        protectedPackage = null
    }

    fun startTask(
        goal: String,
        personaName: String = "",
        personaDesc: String = "",
        onFinished: ((String) -> Unit)? = null,
    ): Boolean {
        if (goal.isBlank() || !running.compareAndSet(false, true)) return false
        taskLeftApp = rootInActiveWindow?.packageName?.toString()?.let { it != packageName } ?: false
        val taskGeneration = generation.incrementAndGet()
        val token = AppSession.token()
        if (token.isBlank()) {
            stopTask()
            status.value = "请先登录"
            return false
        }
        thread(name = "phone-agent") {
            val history = JSONArray()
            var lastLaunchPackage = ""
            var repeatedLaunches = 0
            try {
                for (step in 0 until 40) {
                    if (!running.get() || generation.get() != taskGeneration) break
                    val unlockDeadline = SystemClock.elapsedRealtime() + 120_000L
                    while (pendingUnlockPackage != null && running.get() && generation.get() == taskGeneration &&
                        SystemClock.elapsedRealtime() < unlockDeadline) {
                        Thread.sleep(300)
                    }
                    if (pendingUnlockPackage != null) {
                        status.value = "等待应用解锁超时，任务已结束"
                        break
                    }
                    val screen = readScreen()
                    if (isSensitiveScreen(screen)) {
                        status.value = "已到支付或验证界面，请手动完成"
                        break
                    }
                    val request = JSONObject()
                        .put("goal", goal.trim())
                        .put("persona_name", personaName.take(80))
                        .put("persona_desc", personaDesc.take(2000))
                        .put("mode", "act")
                        .put("step", step)
                        .put("screen", screen)
                        .put("history", history)
                        .put("screenMeta", JSONObject().put("width", resources.displayMetrics.widthPixels)
                            .put("height", resources.displayMetrics.heightPixels))
                        .put("tools", JSONArray(listOf("read_screen", "click", "long_press", "swipe",
                            "input_text", "clear_text", "press_key", "launch_app", "open_url",
                            "open_settings", "wait", "device_info", "ask_user", "finish", "fail")))
                    status.value = "正在查看界面 · 第 ${step + 1} 步"
                    val response = JSONObject(ApiGateway.postSync(
                        ApiGateway.ZHIYIN_BASE + "/api/agent/step", request.toString(), token))
                    if (!running.get() || generation.get() != taskGeneration) break
                    if (!response.optBoolean("ok", false)) {
                        status.value = response.optString("error", response.optString("parse_error", "操作失败"))
                        break
                    }
                    if (response.optBoolean("done", false)) {
                        status.value = response.optString("result", "任务完成")
                        break
                    }
                    val action = response.optJSONObject("action")
                    if (action == null) {
                        status.value = "助手没有给出下一步操作"
                        break
                    }
                    val name = action.optString("name")
                    val args = action.optJSONObject("args") ?: JSONObject()
                    if (name == "launch_app") {
                        val target = args.optString("pkg")
                        repeatedLaunches = if (target == lastLaunchPackage) repeatedLaunches + 1 else 1
                        lastLaunchPackage = target
                        if (repeatedLaunches >= 2) {
                            status.value = "目标应用已打开，但助手没有继续操作；任务已停止"
                            break
                        }
                    } else {
                        repeatedLaunches = 0
                    }
                    if (name == "ask_user") {
                        status.value = args.optString("question", "需要你确认后手动继续")
                        break
                    }
                    if (name == "finish" || name == "fail") {
                        status.value = args.optString(if (name == "finish") "summary" else "reason", "任务结束")
                        break
                    }
                    val result = execute(name, args)
                    history.put(JSONObject().put("action", name).put("result", result))
                    status.value = "${response.optString("note", name)} · $result"
                    if (result.startsWith("不支持") || result.startsWith("失败")) break
                    Thread.sleep(700)
                }
            } catch (e: Exception) {
                if (running.get() && generation.get() == taskGeneration)
                    status.value = "操作中断：${e.message ?: "网络或系统错误"}"
            } finally {
                val outcome = if (generation.get() == taskGeneration) status.value else "操作已停止"
                if (generation.get() == taskGeneration) running.set(false)
                onFinished?.invoke(outcome)
            }
        }
        return true
    }

    private fun isSensitiveScreen(screen: String): Boolean {
        val currentApp = screen.substringBefore('\n')
        if (Regex("alipay|wallet|bank|payment|authenticator", RegexOption.IGNORE_CASE).containsMatchIn(currentApp)) return true
        return Regex("支付密码|银行卡密码|短信验证码|人脸验证|指纹验证|收银台|立即支付|确认付款|提交订单").containsMatchIn(screen)
    }

    private fun readScreen(): String {
        val root = rootInActiveWindow ?: return "【当前应用】未知\n（暂时无法读取界面）"
        val lines = mutableListOf("【当前应用】${root.packageName ?: "未知"}")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var index = 0
        while (queue.isNotEmpty() && index < 80) {
            val node = queue.removeFirst()
            val label = (node.text ?: node.contentDescription ?: "").toString().replace('\n', ' ').take(120)
            val bounds = Rect().also(node::getBoundsInScreen)
            if (node.isVisibleToUser && (label.isNotBlank() || node.isClickable || node.isEditable)) {
                lines.add("n$index ${if (node.isClickable) "[可点]" else ""}${if (node.isEditable) "[输入]" else ""} $label (${bounds.centerX()},${bounds.centerY()})")
                index++
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return lines.joinToString("\n").take(12000)
    }

    private fun findNode(id: String): AccessibilityNodeInfo? {
        val wanted = id.removePrefix("n").toIntOrNull() ?: return null
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var index = 0
        while (queue.isNotEmpty() && index < 80) {
            val node = queue.removeFirst()
            val label = (node.text ?: node.contentDescription ?: "").toString()
            if (node.isVisibleToUser && (label.isNotBlank() || node.isClickable || node.isEditable)) {
                if (index == wanted) return node
                index++
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun execute(name: String, args: JSONObject): String = when (name) {
        "read_screen", "device_info" -> readScreen().take(120)
        "click", "long_press" -> {
            val node = findNode(args.optString("id"))
            val success = if (node != null && name == "click") {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            } else {
                val bounds = Rect()
                node?.getBoundsInScreen(bounds)
                val x = if (node != null) bounds.centerX() else args.optInt("x", -1)
                val y = if (node != null) bounds.centerY() else args.optInt("y", -1)
                gesture(x, y, x, y, if (name == "long_press") 700 else 80)
            }
            if (success) "完成" else "失败：找不到可操作元素"
        }
        "input_text", "clear_text" -> {
            val target = findNode(args.optString("id"))
                ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (target == null || !target.isEditable || target.isPassword) "失败：未找到可输入的普通文本框"
            else {
                val value = if (name == "clear_text") "" else args.optString("text")
                val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) })
                if (ok) "完成" else "失败：无法输入"
            }
        }
        "swipe" -> {
            val d = resources.displayMetrics
            val cx = d.widthPixels / 2
            val cy = d.heightPixels / 2
            val distance = args.optInt("distance", d.heightPixels / 3).coerceIn(100, d.heightPixels / 2)
            val (x2, y2) = when (args.optString("direction")) {
                "up" -> cx to cy - distance
                "down" -> cx to cy + distance
                "left" -> cx - distance to cy
                "right" -> cx + distance to cy
                else -> cx to cy
            }
            if (gesture(cx, cy, x2, y2, 400)) "完成" else "失败：无法滑动"
        }
        "press_key" -> {
            val key = when (args.optString("key")) {
                "home" -> GLOBAL_ACTION_HOME
                "recents" -> GLOBAL_ACTION_RECENTS
                else -> GLOBAL_ACTION_BACK
            }
            if (performGlobalAction(key)) "完成" else "失败：系统按键不可用"
        }
        "launch_app" -> {
            val pkg = args.optString("pkg")
            if (!pkg.matches(Regex("[a-zA-Z0-9_.]+"))) "失败：应用包名无效"
            else if (rootInActiveWindow?.packageName?.toString() == pkg) "目标应用已打开"
            else {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch == null) "失败：应用未安装" else {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "完成"
                }
            }
        }
        "open_url" -> {
            val url = args.optString("url")
            if (!url.startsWith("https://")) "失败：仅支持 HTTPS 链接" else {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "完成"
            }
        }
        "open_settings" -> {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "完成"
        }
        "wait" -> {
            Thread.sleep(args.optLong("ms", 800L).coerceIn(100L, 5000L))
            "完成"
        }
        else -> "不支持：$name"
    }

    private fun gesture(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): Boolean {
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) return false
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong())).build(), null, null)
    }
}
