package com.zhiyin.logic.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 设备信息上报（2026-11-21）
 *
 * 为什么需要：服务端审计日志要能回答「出事的那个账号，是在哪台手机、什么机型、什么系统版本上操作的」。
 * 原生 App 的 User-Agent 里没有机型（HttpURLConnection 默认只写 Dalvik/x.x），
 * 单靠服务端解析 UA 拿不到机型，必须由客户端显式带上。
 *
 * 上报请求头（服务端 src/utils/audit_log.js 读取后入库 audit_logs）：
 *   X-Device-Id     设备指纹：ANDROID_ID 加盐 SHA-256 前 16 位，稳定且不暴露原始硬件标识
 *   X-Device-Model  机型   Build.MODEL              如 SM-G991B
 *   X-Device-Brand  品牌   Build.BRAND              如 samsung
 *   X-Os-Ver        系统   Build.VERSION.RELEASE    如 13
 *   X-App-Ver       客户端版本                       如 2.3.52
 *
 * 说明：HTTP 头只能是 ASCII，机型名里的中文会被剔除。这不影响定位——
 * 设备指纹是主键，机型/品牌只是辅助描述。
 */
public final class DeviceInfo {

    private static volatile boolean inited = false;
    private static volatile String deviceId = "";
    private static String model = "";
    private static String brand = "";
    private static String osVer = "";
    private static String appVer = "";

    private DeviceInfo() {}

    /** 在 ApiGateway.init() 里调用一次即可 */
    public static synchronized void init(Context ctx) {
        if (inited || ctx == null) return;
        inited = true;
        Context app = ctx.getApplicationContext();
        try { model = ascii(Build.MODEL); } catch (Throwable ignored) {}
        try { brand = ascii(Build.BRAND); } catch (Throwable ignored) {}
        try { osVer = ascii(Build.VERSION.RELEASE); } catch (Throwable ignored) {}
        try {
            appVer = ascii(String.valueOf(app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0).versionName));
        } catch (Throwable ignored) {}

        try {
            SharedPreferences sp = app.getSharedPreferences("zhiyin_config", Context.MODE_PRIVATE);
            String cached = sp.getString("device_fp", "");
            if (cached != null && !cached.isEmpty()) {
                deviceId = cached;
            } else {
                String aid = null;
                try {
                    aid = Settings.Secure.getString(app.getContentResolver(), Settings.Secure.ANDROID_ID);
                } catch (Throwable ignored) {}
                if (aid == null || aid.isEmpty()) aid = "no_android_id";
                String fp = sha256Hex(aid + "|zhiyin.device.2026").substring(0, 16);
                deviceId = fp;
                sp.edit().putString("device_fp", fp).apply();
            }
        } catch (Throwable ignored) {}
    }

    /** 给本次连接统一加上设备头；任何异常都不能影响业务请求 */
    public static void apply(HttpURLConnection conn) {
        if (conn == null) return;
        try {
            if (deviceId != null && !deviceId.isEmpty()) conn.setRequestProperty("X-Device-Id", deviceId);
            if (model != null && !model.isEmpty()) conn.setRequestProperty("X-Device-Model", model);
            if (brand != null && !brand.isEmpty()) conn.setRequestProperty("X-Device-Brand", brand);
            if (osVer != null && !osVer.isEmpty()) conn.setRequestProperty("X-Os-Ver", osVer);
            if (appVer != null && !appVer.isEmpty()) conn.setRequestProperty("X-App-Ver", appVer);
        } catch (Throwable ignored) {}
    }

    /** 供本地调试/日志使用 */
    public static String fingerprint() { return deviceId == null ? "" : deviceId; }

    /** HTTP 头合法化：只保留可打印 ASCII（32-126），长度上限 64 */
    private static String ascii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length() && sb.length() < 64; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Throwable e) {
            return "fp" + Integer.toHexString(input.hashCode());
        }
    }
}
