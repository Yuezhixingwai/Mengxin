package com.zhiyin.logic.data;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
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

    public static boolean addRemoteIfAbsent(Context ctx, String sid, String role, String content, long time) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            if (time > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null && o.optLong("time", 0) == time) return false;
                }
            }
            String norm = normalizeMirrorText(content);
            if (!norm.isEmpty()) {
                List<String> roles = new ArrayList<>();
                List<String> norms = new ArrayList<>();
                collectNorms(arr, roles, norms);
                for (int i = 0; i < roles.size(); i++) {
                    if (role.equals(roles.get(i)) && norm.equals(norms.get(i))) return false;
                }
                if ("ai".equals(role) && isMirrorOfKnown(roles, norms, -1, norm)) return false;
            }
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            o.put("time", time > 0 ? time : System.currentTimeMillis());
            o.put("read", sid.equals(activeSessionId));
            arr.put(o);
            healLocalDupes(arr);
            sp.edit().putString(sid, arr.toString()).apply();
            return true;
        } catch (Exception e) {
            android.util.Log.w("MsgRepo", "addRemoteIfAbsent failed: " + e.getMessage());
            return false;
        }
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
                SharedPreferences sp = fCtx.getSharedPreferences("zhiyin_msgs", 0);
                JSONArray localArr = new JSONArray();
                try {
                    localArr = new JSONArray(sp.getString(sessionId, "[]"));
                } catch (org.json.JSONException e) {
                    android.util.Log.w("MsgRepo", "parse local failed: " + e.getMessage());
                }
                try {
                    if (healLocalDupes(localArr)) {
                        sp.edit().putString(sessionId, localArr.toString()).apply();
                        changed = true;
                    }
                } catch (Exception e) {
                    android.util.Log.w("MsgRepo", "heal local failed: " + e.getMessage());
                }
                try {
                    com.zhiyin.logic.net.ApiGateway.ensureMemoryServiceUrl(fCtx);
                    String memUrl = com.zhiyin.logic.net.ApiGateway.getMemoryServiceUrl();
                    if (memUrl != null && !memUrl.isEmpty()) {
                        String userId = com.zhiyin.logic.net.ApiGateway.getUserId(fCtx);
                        if (userId != null && !userId.isEmpty()) {
                            String resp = com.zhiyin.logic.net.ApiGateway.memoryRequestSync(
                                memUrl + "/api/chat/" + java.net.URLEncoder.encode(sessionId, "UTF-8") + "?limit=100",
                                "GET", null, userId);
                            org.json.JSONObject json = new org.json.JSONObject(resp);
                            org.json.JSONArray remoteMsgs = json.optJSONArray("messages");
                            if (remoteMsgs != null && remoteMsgs.length() > 0) {
                                java.util.Set<Long> existingTimes = new java.util.HashSet<>();
                                for (int i = 0; i < localArr.length(); i++) {
                                    existingTimes.add(localArr.getJSONObject(i).optLong("time", 0));
                                }
                                List<String> knownRoles = new ArrayList<>();
                                List<String> knownNorms = new ArrayList<>();
                                collectNorms(localArr, knownRoles, knownNorms);
                                for (int i = 0; i < remoteMsgs.length(); i++) {
                                    org.json.JSONObject rm = remoteMsgs.getJSONObject(i);
                                    long t = rm.optLong("time", 0);
                                    String rRole = rm.optString("role", "ai");
                                    if ("assistant".equals(rRole)) rRole = "ai";
                                    String rContent = rm.optString("content", "");
                                    if (rContent == null || rContent.trim().isEmpty()) continue;
                                    if (t > 0 && existingTimes.contains(t)) {
                                        continue;
                                    }
                                    String rNorm = normalizeMirrorText(rContent);
                                    boolean dup = false;
                                    if (!rNorm.isEmpty()) {
                                        for (int k = 0; k < knownNorms.size() && !dup; k++) {
                                            if (rRole.equals(knownRoles.get(k)) && rNorm.equals(knownNorms.get(k))) {
                                                dup = true;
                                            }
                                        }
                                        if (!dup && "ai".equals(rRole) && isMirrorOfKnown(knownRoles, knownNorms, -1, rNorm)) {
                                            dup = true;
                                        }
                                    }
                                    if (dup) continue;
                                    JSONObject o = new JSONObject();
                                    o.put("role", rRole);
                                    o.put("content", rContent);
                                    o.put("time", t);
                                    o.put("read", sessionId.equals(activeSessionId));
                                    localArr.put(o);
                                    knownRoles.add(rRole);
                                    knownNorms.add(rNorm);
                                    if (t > 0) existingTimes.add(t);
                                    changed = true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("MsgRepo", "sync from 9005 failed: " + e.getMessage());
                }
                try {
                    if (healLocalDupes(localArr)) {
                        changed = true;
                    }
                } catch (Exception e) {
                    android.util.Log.w("MsgRepo", "heal after merge failed: " + e.getMessage());
                }
                if (changed) {
                    try {
                        JSONArray sorted = new JSONArray();
                        java.util.List<JSONObject> list = new java.util.ArrayList<>();
                        for (int i = 0; i < localArr.length(); i++) list.add(localArr.getJSONObject(i));
                        java.util.Collections.sort(list, (a, b) -> Long.compare(a.optLong("time", 0), b.optLong("time", 0)));
                        for (JSONObject o : list) sorted.put(o);
                        sp.edit().putString(sessionId, sorted.toString()).apply();
                    } catch (Exception e) {
                        android.util.Log.w("MsgRepo", "sort local failed: " + e.getMessage());
                    }
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

    private static void collectNorms(JSONArray localArr, List<String> roles, List<String> norms) throws JSONException {
        for (int i = 0; i < localArr.length(); i++) {
            JSONObject o = localArr.optJSONObject(i);
            roles.add(o == null ? "" : o.optString("role", ""));
            norms.add(o == null ? "" : normalizeMirrorText(o.optString("content", "")));
        }
    }

    private static boolean isMirrorOfKnown(List<String> roles, List<String> norms, int skipIdx, String fullNorm) {
        if (fullNorm.length() < 2) return false;
        for (int start = 0; start < norms.size(); start++) {
            if (start == skipIdx) continue;
            if (!"ai".equals(roles.get(start)) || norms.get(start).isEmpty()) continue;
            StringBuilder acc = new StringBuilder();
            int pieces = 0;
            for (int j = start; j < norms.size(); j++) {
                if (j == skipIdx) continue;
                if (!"ai".equals(roles.get(j)) || norms.get(j).isEmpty()) continue;
                acc.append(norms.get(j));
                pieces++;
                if (acc.length() >= fullNorm.length()) {
                    if (pieces >= 2 && acc.length() == fullNorm.length() && acc.toString().equals(fullNorm)) {
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private static boolean healLocalDupes(JSONArray localArr) throws JSONException {
        boolean removed = false;
        for (int pass = 0; pass < 4; pass++) {
            if (!healOnce(localArr)) break;
            removed = true;
        }
        return removed;
    }

    private static boolean healOnce(JSONArray localArr) throws JSONException {
        boolean changed = false;
        for (int i = 0; i < localArr.length(); i++) {
            JSONObject o = localArr.optJSONObject(i);
            if (o != null && "assistant".equals(o.optString("role", ""))) {
                o.put("role", "ai");
                changed = true;
            }
        }
        List<String> roles = new ArrayList<>();
        List<String> norms = new ArrayList<>();
        collectNorms(localArr, roles, norms);
        for (int i = 0; i < localArr.length(); i++) {
            JSONObject full = localArr.optJSONObject(i);
            if (full == null || !"ai".equals(full.optString("role", ""))) continue;
            String fullNorm = norms.get(i);
            if (fullNorm.isEmpty()) continue;
            if (isMirrorOfKnown(roles, norms, i, fullNorm)) {
                localArr.remove(i);
                roles.remove(i);
                norms.remove(i);
                changed = true;
                i--;
            }
        }
        for (int i = 0; i < localArr.length(); i++) {
            JSONObject a = localArr.optJSONObject(i);
            if (a == null) continue;
            String aNorm = normalizeMirrorText(a.optString("content", ""));
            if (aNorm.isEmpty()) continue;
            String aRole = a.optString("role", "");
            for (int j = localArr.length() - 1; j > i; j--) {
                JSONObject b = localArr.optJSONObject(j);
                if (b == null || !aRole.equals(b.optString("role", ""))) continue;
                if (aNorm.equals(normalizeMirrorText(b.optString("content", "")))) {
                    localArr.remove(j);
                    changed = true;
                }
            }
        }
        boolean hasSticker = false;
        for (int i = 0; i < localArr.length(); i++) {
            JSONObject o = localArr.optJSONObject(i);
            if (o == null) continue;
            String c = o.optString("content", "");
            if (c != null && (c.contains("[STICKER:") || c.contains("[CUSTOM_STICKER:"))) {
                hasSticker = true;
                break;
            }
        }
        if (hasSticker) {
            for (int i = localArr.length() - 1; i >= 0; i--) {
                JSONObject o = localArr.optJSONObject(i);
                if (o == null) continue;
                String c = o.optString("content", "").trim();
                if (c.matches("^\\[表情(:[^\\]]*)?\\]$")) {
                    localArr.remove(i);
                    changed = true;
                }
            }
        }
        return changed;
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

    public static void replaceWithSegments(Context ctx, final String sid, int index, final String role, List<String> segments) {
        try {
            if (segments == null || segments.isEmpty()) return;
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            JSONArray arr = new JSONArray(sp.getString(sid, "[]"));
            if (index < 0 || index >= arr.length()) return;
            final long oldTime = arr.optJSONObject(index).optLong("time", 0);
            final long baseTime = System.currentTimeMillis();
            JSONArray newArr = new JSONArray();
            final List<String> segContents = new ArrayList<>();
            final List<Long> segTimes = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                if (i == index) {
                    for (int s = 0; s < segments.size(); s++) {
                        long t = baseTime + s;
                        JSONObject o = new JSONObject();
                        o.put("role", role);
                        o.put("content", segments.get(s));
                        o.put("time", t);
                        o.put("read", sid.equals(activeSessionId));
                        newArr.put(o);
                        segContents.add(segments.get(s));
                        segTimes.add(t);
                    }
                } else {
                    newArr.put(arr.get(i));
                }
            }
            sp.edit().putString(sid, newArr.toString()).apply();
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
                    for (int s = 0; s < segContents.size(); s++) {
                        try {
                            org.json.JSONObject body = new org.json.JSONObject();
                            body.put("role", role);
                            body.put("content", segContents.get(s));
                            body.put("time", segTimes.get(s));
                            body.put("platform", "android_app");
                            ApiGateway.memoryRequestSync(memUrl + "/api/chat/" + java.net.URLEncoder.encode(sid, "UTF-8"), "POST", body.toString(), userId);
                        } catch (Exception ignored) {}
                    }
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

    public static void markAllSessionsRead(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_msgs", 0);
            java.util.Map<String, ?> all = sp.getAll();
            for (String key : all.keySet()) {
                if (key.startsWith("persona_") || key.startsWith("group_")) {
                    markAllRead(ctx, key);
                }
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
