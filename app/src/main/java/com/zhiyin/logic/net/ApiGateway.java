package com.zhiyin.logic.net;

import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class ApiGateway {
    public static String ZHIYIN_BASE = "https://api.zhiyin.zhendeqiang.top";

    public static final String OFFICIAL_SITE = "https://zhiyin.zhendeqiang.top";

    public static String getBaseUrl() { return ZHIYIN_BASE; }

    public static void init(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("zhiyin_config", Context.MODE_PRIVATE);
        String saved = sp.getString("server_url", null);
        if (saved != null && !saved.isEmpty()) ZHIYIN_BASE = saved;
        String memUrl = sp.getString("memory_service_url", null);
        if (memUrl != null && !memUrl.isEmpty()) memoryServiceUrl = memUrl;
        CookieStore.init(ctx);
        com.zhiyin.logic.data.SessionStore.init(ctx);
    }

    public static void setServerUrl(Context ctx, String url) {
        ZHIYIN_BASE = url;
        ctx.getSharedPreferences("zhiyin_config", Context.MODE_PRIVATE).edit().putString("server_url", url).apply();
    }

    private static String memoryServiceUrl = "";
    public static String getMemoryServiceUrl() { return memoryServiceUrl; }
    public static void setMemoryServiceUrl(Context ctx, String url) {
        if (url != null) {
            memoryServiceUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            ctx.getSharedPreferences("zhiyin_config", Context.MODE_PRIVATE).edit().putString("memory_service_url", memoryServiceUrl).apply();
        }
    }
    public static void loadMemoryServiceUrl(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("zhiyin_config", Context.MODE_PRIVATE);
        memoryServiceUrl = sp.getString("memory_service_url", "");
    }
    private static volatile long lastMemUrlFetchTime = 0L;
    public static void ensureMemoryServiceUrl(Context ctx) {
        long now = System.currentTimeMillis();
        boolean hasValid = memoryServiceUrl != null && !memoryServiceUrl.isEmpty();
        if (hasValid && (now - lastMemUrlFetchTime) < 300000L) return;
        try {
            String resp = requestSync(ZHIYIN_BASE + "/api/config/client-config", "GET", null, null);
            org.json.JSONObject json = new org.json.JSONObject(resp);
            String memUrl = json.optString("memory_service_url", "");
            if (!memUrl.isEmpty()) {
                setMemoryServiceUrl(ctx, memUrl);
                lastMemUrlFetchTime = now;
                android.util.Log.i("ApiGateway", "memory_service_url refreshed: " + memUrl);
            } else {
                android.util.Log.w("ApiGateway", "client-config no memory_service_url");
            }
        } catch (Exception e) {
            android.util.Log.w("ApiGateway", "ensureMemoryServiceUrl failed: " + e.getMessage());
        }
    }

    public static String getUserId(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences("zhiyin_session", Context.MODE_PRIVATE).getString("userId", "");
    }
    public static void memoryGet(String path, Context ctx, Callback cb) {
        executor.execute(() -> {
            try {
                String result = memoryRequestSync(getMemoryServiceUrl() + path, "GET", null, getUserId(ctx));
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }
    public static void memoryPost(String path, String jsonBody, Context ctx, Callback cb) {
        executor.execute(() -> {
            try {
                String result = memoryRequestSync(getMemoryServiceUrl() + path, "POST", jsonBody, getUserId(ctx));
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }
    public static void memoryPut(String path, String jsonBody, Context ctx, Callback cb) {
        executor.execute(() -> {
            try {
                String result = memoryRequestSync(getMemoryServiceUrl() + path, "PUT", jsonBody, getUserId(ctx));
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }
    public static void memoryDelete(String path, Context ctx, Callback cb) {
        executor.execute(() -> {
            try {
                String result = memoryRequestSync(getMemoryServiceUrl() + path, "DELETE", null, getUserId(ctx));
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }
    public static String memoryRequestSync(String url, String method, String jsonBody, String userId) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        applySSL(conn);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        String token = getToken();
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(jsonBody != null && !jsonBody.isEmpty());
        if (jsonBody != null && !jsonBody.isEmpty()) {
            byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            OutputStream os = conn.getOutputStream(); os.write(data); os.flush(); os.close();
        }
        int code = conn.getResponseCode();
        String body = readAll(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body);
        return body;
    }
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static SSLSocketFactory sslSocketFactory;
    private static HostnameVerifier hostnameVerifier;
    static {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            sslSocketFactory = ctx.getSocketFactory();
            hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        } catch (Exception ignored) {}
    }

    public static void applySSL(HttpURLConnection conn) {
        if (conn instanceof HttpsURLConnection) {
            HttpsURLConnection hc = (HttpsURLConnection) conn;
            if (sslSocketFactory != null) hc.setSSLSocketFactory(sslSocketFactory);
            if (hostnameVerifier != null) hc.setHostnameVerifier(hostnameVerifier);
        }
    }

    private static String getToken() {
        return com.zhiyin.logic.data.SessionStore.getTokenInternal();
    }

    public interface Callback { void onSuccess(String response); void onError(String error); }

    public static void get(String path, String token, Callback cb) {
        executor.execute(() -> {
            try {
                String result = requestSync(ZHIYIN_BASE + path, "GET", null, token);
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }

    public static void post(String path, String jsonBody, String token, Callback cb) {
        executor.execute(() -> {
            try {
                String result = requestSync(ZHIYIN_BASE + path, "POST", jsonBody, token);
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }

    public static void put(String path, String jsonBody, String token, Callback cb) {
        executor.execute(() -> {
            try {
                String result = requestSync(ZHIYIN_BASE + path, "PUT", jsonBody, token);
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }

    public static void delete(String path, String token, Callback cb) {
        executor.execute(() -> {
            try {
                String result = requestSync(ZHIYIN_BASE + path, "DELETE", null, token);
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }

    public static String requestSync(String url, String method, String jsonBody, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        applySSL(conn);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
        String cookie = CookieStore.get();
        if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        String finalBody = jsonBody;
        if (jsonBody != null && !jsonBody.isEmpty()) {
            try { finalBody = PayloadCipher.wrap(jsonBody); } catch (Exception ignored) {}
        }

        conn.setDoOutput(finalBody != null && !finalBody.isEmpty());
        if (finalBody != null && !finalBody.isEmpty()) {
            byte[] data = finalBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            OutputStream os = conn.getOutputStream(); os.write(data); os.flush(); os.close();
        }

        int code = conn.getResponseCode();
        String body = readAll(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();

        try {
            if (PayloadCipher.isEncrypted(body)) body = PayloadCipher.unwrap(body);
        } catch (Exception ignored) {}

        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body.substring(0, Math.min(300, body.length())));
        return body;
    }

    public static void upload(String path, String filePath, String token, Callback cb) {
        executor.execute(() -> {
            try {
                String result = uploadSync(ZHIYIN_BASE + path, filePath, token);
                final String r = result;
                handler.post(() -> cb.onSuccess(r));
            } catch (final Exception e) { handler.post(() -> cb.onError(e.getMessage())); }
        });
    }

    public static String uploadSync(String url, String filePath, String token) throws Exception {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        File file = new File(filePath);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        applySSL(conn);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase();
        String mime;
        switch (ext) {
            case "m4a": case "mp4": mime = "audio/mp4"; break;
            case "mp3": mime = "audio/mpeg"; break;
            case "wav": mime = "audio/wav"; break;
            case "ogg": mime = "audio/ogg"; break;
            case "jpg": case "jpeg": mime = "image/jpeg"; break;
            case "png": mime = "image/png"; break;
            case "gif": mime = "image/gif"; break;
            default: mime = "application/octet-stream";
        }
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\nContent-Type: " + mime + "\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));
        FileInputStream fis = new FileInputStream(file);
        byte[] buf = new byte[4096]; int len;
        while ((len = fis.read(buf)) != -1) os.write(buf, 0, len);
        fis.close();
        os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        os.flush(); os.close();
        int code = conn.getResponseCode();
        String body = readAll(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body.substring(0, Math.min(300, body.length())));
        return body;
    }

    public static String postSync(String url, String jsonBody, String token) throws Exception {
        return requestSync(url, "POST", jsonBody, token);
    }

    public static String getSync(String url, String token) throws Exception {
        return requestSync(url, "GET", null, token);
    }

    public static String deleteSync(String url, String token) throws Exception {
        return requestSync(url, "DELETE", null, token);
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close(); return sb.toString().trim();
    }

    public static String getBaseUrl(Context ctx) { return getBaseUrl(); }

    public static String getToken(Context ctx) {
        return com.zhiyin.logic.data.SessionStore.getTokenInternal();
    }

    public static String get(String path, Context ctx) throws Exception {
        return requestSync(ZHIYIN_BASE + path, "GET", null, getToken(ctx));
    }

    public static String put(String path, String jsonBody, Context ctx) throws Exception {
        return requestSync(ZHIYIN_BASE + path, "PUT", jsonBody, getToken(ctx));
    }

    public static byte[] getRaw(String path, Context ctx) throws Exception {
        String baseUrl = getBaseUrl(ctx);
        String fullUrl = path.startsWith("http") ? path : baseUrl + path;
        java.net.URL url = new java.net.URL(fullUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        applySSL(conn);
        conn.setRequestMethod("GET");
        String token = getToken(ctx);
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        java.io.InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close();
        return baos.toByteArray();
    }

    public static byte[] getRawWithCookie(String path, Context ctx) throws Exception {
        String baseUrl = getBaseUrl(ctx);
        String fullUrl = path.startsWith("http") ? path : baseUrl + path;
        java.net.URL url = new java.net.URL(fullUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        applySSL(conn);
        conn.setRequestMethod("GET");
        String cookie = CookieStore.get();
        if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        try {
            java.util.List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
            if (setCookies != null) {
                for (String sc : setCookies) {
                    if (sc != null && sc.startsWith("connect.sid")) {
                        CookieStore.set(sc.split(";")[0]);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (code != 200) throw new Exception("HTTP " + code);
        java.io.InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close();
        return baos.toByteArray();
    }

    public static String postMultipart(String path, String fieldName, byte[] fileData, Context ctx) throws Exception {
        String baseUrl = getBaseUrl(ctx);
        String fullUrl = baseUrl + path;
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        java.net.URL url = new java.net.URL(fullUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        applySSL(conn);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        String token = getToken(ctx);
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        java.io.OutputStream os = conn.getOutputStream();
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"avatar.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
        os.write(header.getBytes());
        os.write(fileData);
        os.write(("\r\n--" + boundary + "--\r\n").getBytes());
        os.flush(); os.close();
        int code = conn.getResponseCode();
        java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close();
        String resp = baos.toString("UTF-8");
        try { if (PayloadCipher.isEncrypted(resp)) resp = PayloadCipher.unwrap(resp); } catch (Exception ignored) {}
        if (code != 200) throw new Exception(resp);
        return resp;
    }

    public static String toBeijingTime(String utcStr, String format) {
        if (utcStr == null || utcStr.isEmpty()) return "";
        try {
            java.text.SimpleDateFormat utcFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            utcFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date dt = utcFmt.parse(utcStr.contains("T") ? utcStr.substring(0, 19) : utcStr);
            java.text.SimpleDateFormat bjFmt = new java.text.SimpleDateFormat(format != null ? format : "yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            bjFmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            return bjFmt.format(dt);
        } catch (Exception e) {
            return utcStr.length() >= 16 ? utcStr.substring(0, 16) : utcStr;
        }
    }

    private static boolean ENABLE_ENCRYPTION = false;
    private static final String PAYLOAD_KEY = "zhiyin_app_encrypt_key_2024";
    private static byte[] payloadKeyBytes;
    private static byte[] getPayloadKey() throws Exception {
        if (payloadKeyBytes == null) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            payloadKeyBytes = md.digest(PAYLOAD_KEY.getBytes(StandardCharsets.UTF_8));
        }
        return payloadKeyBytes;
    }

    public static class PayloadCipher {
        public static String encrypt(String plain) throws Exception {
            byte[] key = getPayloadKey();
            byte[] iv = new byte[16];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(iv) + ":" + bytesToHex(encrypted);
        }

        public static String decrypt(String encText) throws Exception {
            byte[] key = getPayloadKey();
            String[] parts = encText.split(":");
            byte[] iv = hexToBytes(parts[0]);
            byte[] data = hexToBytes(parts[1]);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return new String(c.doFinal(data), StandardCharsets.UTF_8);
        }

        public static boolean isEncrypted(String response) {
            return ENABLE_ENCRYPTION && response != null && response.trim().startsWith("{\"data\":");
        }

        public static String unwrap(String response) throws Exception {
            org.json.JSONObject obj = new org.json.JSONObject(response);
            return decrypt(obj.getString("data"));
        }

        public static String wrap(String jsonBody) throws Exception {
            if (!ENABLE_ENCRYPTION) return jsonBody;
            return "{\"data\":\"" + encrypt(jsonBody) + "\"}";
        }

        private static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        }

        private static byte[] hexToBytes(String hex) {
            int len = hex.length();
            byte[] result = new byte[len / 2];
            for (int i = 0; i < len; i += 2)
                result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
            return result;
        }
    }

    public static class CookieStore {
        private static Context appContext;
        private static final String PREF_NAME = "zhiyin_cookies";
        public static void init(Context ctx) { appContext = ctx.getApplicationContext(); }
        public static void set(String c) {
            if (appContext != null)
                appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString("cookie", c).apply();
        }
        public static String get() {
            if (appContext == null) return null;
            return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("cookie", null);
        }
    }
}
