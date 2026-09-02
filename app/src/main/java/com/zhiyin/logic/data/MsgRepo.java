package com.zhiyin.logic.data;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import com.zhiyin.logic.net.ApiGateway;
import java.util.ArrayList;
import java.util.List;

public class MsgRepo {
    private static String activeSessionId = null;

    public static void setActiveSession(String sid) { activeSessionId = sid; }
    public static String getActiveSession() { return activeSessionId; }

    public static void add(Context ctx, String sid, String role, String content) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            o.put("time", System.currentTimeMillis());
            o.put("read", sid.equals(activeSessionId));
            arr.put(o);
            sp.edit().putString(sid, arr.toString()).apply();
            syncToMemoryService(ctx, sid, role, content, o.optLong("time", 0));
        } catch (Exception ignored) {}
    }

    public static void addSilent(Context ctx, String sid, String role, String content, long time) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            o.put("time", time > 0 ? time : System.currentTimeMillis());
            o.put("read", sid.equals(activeSessionId));
            arr.put(o);
            sp.edit().putString(sid, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void syncToMemoryService(Context ctx, String sid, String role, String content, long time) {
        if (content != null && (content.contains("|||THINK|||") || content.contains("|||SRC|||"))) return;
        try {
            final Context fCtx = ctx;
            final String sessionId = sid;
            final String fRole = role;
            final String fContent = content;
            final long fTime = time;
            new Thread(() -> {
                try {
                    com.zhiyin.logic.net.ApiGateway.ensureMemoryServiceUrl(fCtx);
                    String memUrl = com.zhiyin.logic.net.ApiGateway.getMemoryServiceUrl();
                    if (memUrl == null || memUrl.isEmpty()) {
                        android.util.Log.w("MsgRepo", "sync upload skip: memory_service_url empty, sid=" + sessionId);
                        return;
                    }
                    String userId = com.zhiyin.logic.net.ApiGateway.getUserId(fCtx);
                    if (userId == null || userId.isEmpty()) {
                        android.util.Log.w("MsgRepo", "sync upload skip: userId empty");
                        return;
                    }
                    org.json.JSONObject body = new org.json.JSONObject();
                    body.put("role", fRole);
                    body.put("content", fContent);
                    body.put("time", fTime);
                    body.put("platform", "android_app");
                    com.zhiyin.logic.net.ApiGateway.memoryRequestSync(
                        memUrl + "/api/chat/" + java.net.URLEncoder.encode(sessionId, "UTF-8"),
                        "POST", body.toString(), userId);
                } catch (Exception e) {
                    android.util.Log.w("MsgRepo", "sync to 9005 failed: " + e.getMessage());
                }
            }).start();
        } catch (Exception ignored) {}
    }

    public interface SyncCallback { void onSynced(boolean changed); }

    public static void syncFromMemoryService(Context ctx, String sid, SyncCallback cb) {
        try {
            final Context fCtx = ctx;
            final String sessionId = sid;
            new Thread(() -> {
                boolean changed = false;
                try {
                    com.zhiyin.logic.net.ApiGateway.ensureMemoryServiceUrl(fCtx);
                    String memUrl = com.zhiyin.logic.net.ApiGateway.getMemoryServiceUrl();
                    if (memUrl == null || memUrl.isEmpty()) {
                        android.util.Log.w("MsgRepo", "sync pull skip: memory_service_url empty, sid=" + sessionId);
                        return;
                    }
                    String userId = com.zhiyin.logic.net.ApiGateway.getUserId(fCtx);
                    if (userId == null || userId.isEmpty()) {
                        android.util.Log.w("MsgRepo", "sync pull skip: userId empty");
                        return;
                    }
                    String resp = com.zhiyin.logic.net.ApiGateway.memoryRequestSync(
                        memUrl + "/api/chat/" + java.net.URLEncoder.encode(sessionId, "UTF-8") + "?limit=100",
                        "GET", null, userId);
                    org.json.JSONObject json = new org.json.JSONObject(resp);
                    org.json.JSONArray remoteMsgs = json.optJSONArray("messages");
                    if (remoteMsgs == null || remoteMsgs.length() == 0) return;
                    SharedPreferences sp = fCtx.getSharedPreferences("zhiyin_msgs", 0);
                    JSONArray localArr = new JSONArray(sp.getString(sessionId, "[]"));
                    java.util.Set<Long> existingTimes = new java.util.HashSet<>();
                    for (int i = 0; i < localArr.length(); i++) {
                        existingTimes.add(localArr.getJSONObject(i).optLong("time", 0));
                    }
                    for (int i = 0; i < remoteMsgs.length(); i++) {
                        org.json.JSONObject rm = remoteMsgs.getJSONObject(i);
                        long t = rm.optLong("time", 0);
                        if (t > 0 && existingTimes.contains(t)) {
                            continue;
                        }
                        String rRole = rm.optString("role", "ai");
                        String rContent = rm.optString("content", "");
                        if (rContent == null || rContent.trim().isEmpty()) continue;
                        if ("app_server".equals(rm.optString("platform", ""))
                                && isMirrorDuplicate(localArr, rRole, rContent, t)) {
                            continue;
                        }
                        JSONObject o = new JSONObject();
                        o.put("role", rRole);
                        o.put("content", rContent);
                        o.put("time", t);
                        o.put("read", sessionId.equals(activeSessionId));
                        localArr.put(o);
                        changed = true;
                    }
                    if (changed) {
                        JSONArray sorted = new JSONArray();
                        java.util.List<JSONObject> list = new java.util.ArrayList<>();
                        for (int i = 0; i < localArr.length(); i++) list.add(localArr.getJSONObject(i));
                        java.util.Collections.sort(list, (a, b) -> Long.compare(a.optLong("time", 0), b.optLong("time", 0)));
                        for (JSONObject o : list) sorted.put(o);
                        sp.edit().putString(sessionId, sorted.toString()).apply();
                    }
                } catch (Exception e) {
                    android.util.Log.w("MsgRepo", "sync from 9005 failed: " + e.getMessage());
                }
                final SyncCallback fCb = cb;
                if (fCb != null) {
                    final boolean fChanged = changed;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> fCb.onSynced(fChanged));
                }
            }).start();
        } catch (Exception ignored) {}
    }

    private static String normalizeMirrorText(String s) {
        if (s == null) return "";
        return s.replaceAll("\\[STICKER:[^\\]]*\\]", "")
                .replaceAll("\\[CUSTOM_STICKER:[^\\]]*\\]", "")
                .replaceAll("\\|#\\d+", "")
                .replaceAll("[\\u3002\\uff01\\uff1f!?\\u2026\\uff0c,\\uff1b;\\u3001]", "")
                .replaceAll("\\s+", "");
    }

    private static boolean isMirrorDuplicate(JSONArray localArr, String role, String content, long time) {
        try {
            final String c = content == null ? "" : content.trim();
            if (c.isEmpty()) return false;
            if ("assistant".equals(role)) role = "ai";
            if ("user".equals(role) && c.matches("^\\[表情:[^\\]]*\\]$")) return true;
            if (c.matches("^\\[(CUSTOM_)?STICKER:[^\\]]*\\]$")) return true;
            final long WINDOW = 90 * 1000L;
            final String target = normalizeMirrorText(c);
            if (target.isEmpty()) return false;
            List<String> win = new ArrayList<>();
            for (int i = 0; i < localArr.length(); i++) {
                JSONObject o = localArr.getJSONObject(i);
                if (!role.equals(o.optString("role", ""))) continue;
                long lt = o.optLong("time", 0);
                if (lt > 0 && Math.abs(lt - time) <= WINDOW) {
                    win.add(normalizeMirrorText(o.optString("content", "")));
                }
            }
            for (int i = 0; i < win.size(); i++) {
                StringBuilder acc = new StringBuilder();
                for (int j = i; j < win.size() && j < i + 8; j++) {
                    acc.append(win.get(j));
                    if (acc.toString().trim().equals(target)) return true;
                    if (acc.length() > target.length()) break;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static List<String[]> getAll(Context ctx, String sid) {
        List<String[]> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(ctx.getSharedPreferences("zhiyin_msgs", 0).getString(sid, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new String[]{o.getString("role"), o.getString("content"), String.valueOf(o.optLong("time", 0))});
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void delete(Context ctx, String sid) {
        ctx.getSharedPreferences("zhiyin_msgs", 0).edit().remove(sid).apply();
        try {
            final Context fCtx = ctx;
            final String fSid = sid;
            final String personaName = fSid != null && fSid.startsWith("persona_") ? fSid.substring(8) : null;
            new Thread(() -> {
                try {
                    ApiGateway.ensureMemoryServiceUrl(fCtx);
                    String memUrl = ApiGateway.getMemoryServiceUrl();
                    String userId = ApiGateway.getUserId(fCtx);
                    if (memUrl == null || memUrl.isEmpty() || userId == null || userId.isEmpty()) return;
                    org.json.JSONObject body = new org.json.JSONObject();
                    if (personaName != null && !personaName.isEmpty()) body.put("personaName", personaName);
                    ApiGateway.memoryRequestSync(memUrl + "/api/chat/" + java.net.URLEncoder.encode(fSid, "UTF-8") + "/archive", "POST", body.toString(), userId);
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ignored) {}
    }

    public static void deleteAt(Context ctx, String sid, int index) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            if (index < 0 || index >= arr.length()) return;
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (i != index) newArr.put(arr.get(i));
            }
            sp.edit().putString(sid, newArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void replaceAt(Context ctx, final String sid, int index, String role, String content) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            if (index < 0 || index >= arr.length()) return;
            final long oldTime = arr.optJSONObject(index).optLong("time", 0);
            final long newTime = System.currentTimeMillis();
            JSONObject newObj = new JSONObject();
            newObj.put("role", role);
            newObj.put("content", content);
            newObj.put("time", newTime);
            arr.put(index, newObj);
            sp.edit().putString(sid, arr.toString()).apply();
            final String fRole = role;
            final String fContent = content;
            new Thread(() -> {
                try {
                    ApiGateway.ensureMemoryServiceUrl(ctx);
                    String memUrl = ApiGateway.getMemoryServiceUrl();
                    String userId = ApiGateway.getUserId(ctx);
                    if (memUrl == null || memUrl.isEmpty() || userId == null || userId.isEmpty()) return;
                    if (oldTime > 0) {
                        try {
                            ApiGateway.memoryRequestSync(memUrl + "/api/chat/" + java.net.URLEncoder.encode(sid, "UTF-8") + "?time=" + oldTime, "DELETE", null, userId);
                        } catch (Exception ignored) {}
                    }
                    org.json.JSONObject body = new org.json.JSONObject();
                    body.put("role", fRole);
                    body.put("content", fContent);
                    body.put("time", newTime);
                    body.put("platform", "android_app");
                    ApiGateway.memoryRequestSync(memUrl + "/api/chat/" + java.net.URLEncoder.encode(sid, "UTF-8"), "POST", body.toString(), userId);
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ignored) {}
    }

    public static int getUnreadCount(Context ctx, String sid) {
        try {
            JSONArray arr = new JSONArray(ctx.getSharedPreferences("zhiyin_msgs", 0).getString(sid, "[]"));
            int count = 0;
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                if ("ai".equals(o.optString("role")) && !o.optBoolean("read", false)) {
                    count++;
                } else if ("user".equals(o.optString("role"))) {
                    break;
                }
            }
            return count;
        } catch (Exception ignored) {}
        return 0;
    }

    public static void markAllRead(Context ctx, String sid) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("read", false)) {
                    o.put("read", true);
                    changed = true;
                }
            }
            if (changed) {
                sp.edit().putString(sid, arr.toString()).apply();
            }
        } catch (Exception ignored) {}
    }

    public static void updateLast(Context ctx, String sid, String oldPrefix, String newContent) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optString("content", "").startsWith(oldPrefix)) {
                    o.put("content", newContent);
                    break;
                }
            }
            sp.edit().putString(sid, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static int getTotalUnreadCount(Context ctx, List<FriendManager.Friend> friends) {
        int total = 0;
        for (FriendManager.Friend f : friends) {
            total += getUnreadCount(ctx, "persona_" + f.name);
        }
        return total;
    }
}
