package com.zhiyin.logic.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.zhiyin.logic.chat.ChatEngine;
import com.zhiyin.logic.net.ApiGateway;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroupManager {

    public static void saveGroupMembers(Context ctx, String name, List<String> members) {
        try {
            JSONArray arr = new JSONArray();
            for (String m : members) arr.put(m);
            prefs(ctx).edit().putString("gm_" + name, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<String> loadGroupMembers(Context ctx, String name) {
        try {
            String json = prefs(ctx).getString("gm_" + name, null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                List<String> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
                if (!out.isEmpty()) return out;
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(java.util.Arrays.asList(extractMembersFromHistory(ctx, name)));
    }

    private static String[] extractMembersFromHistory(Context ctx, String groupName) {
        HashSet<String> names = new HashSet<>();
        List<String[]> msgs = MsgRepo.getAll(ctx, "group_" + groupName);
        Pattern p = Pattern.compile("\\[([^\\]]+)\\]");
        for (String[] m : msgs) {
            Matcher mat = p.matcher(m[1]);
            while (mat.find()) {
                String name = mat.group(1).trim();
                if (!name.equals("系统") && !name.isEmpty()) names.add(name);
            }
        }
        return names.toArray(new String[0]);
    }

    public static List<String[]> getGroupChats(Context ctx) {
        List<String[]> groups = new ArrayList<>();
        try {
            Map<String, ?> all = ctx.getSharedPreferences("zhiyin_msgs", 0).getAll();
            for (String key : all.keySet()) {
                if (key.startsWith("group_")) {
                    groups.add(new String[]{key, key.substring(6)});
                }
            }
        } catch (Exception ignored) {}
        return groups;
    }

    public interface CreateCallback {
        void onCreated(boolean serverOk, String error);
    }

    public static void createGroup(Context ctx, String groupName, List<Integer> memberIds, List<String> memberNames, CreateCallback cb) {
        String token = new SessionStore(ctx).getToken();
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("name", groupName);
            JSONArray members = new JSONArray();
            for (int id : memberIds) members.put(id);
            body.put("members", members);
            ApiGateway.post("/api/groups", body.toString(), token, new ApiGateway.Callback() {
                @Override
                public void onSuccess(String resp) {
                    saveGroupMembers(ctx, groupName, memberNames);
                    cb.onCreated(true, null);
                }

                @Override
                public void onError(String err) {
                    saveGroupMembers(ctx, groupName, memberNames);
                    cb.onCreated(false, err);
                }
            });
        } catch (Exception e) {
            cb.onCreated(false, e.getMessage());
        }
    }

    public static void deleteGroup(Context ctx, String groupName) {
        MsgRepo.delete(ctx, "group_" + groupName);
        prefs(ctx).edit().remove("gm_" + groupName).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE);
    }

    public static void sendGroupRedpacket(Context ctx, ChatEngine.GroupInfo g, double total, int count) {
        String token = new SessionStore(ctx).getToken();
        String sid = g.sessionId();
        new Thread(() -> {
            try {
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("sessionId", sid);
                body.put("isGroup", 1);
                JSONArray ps = new JSONArray();
                for (String m : g.members) ps.put(m);
                body.put("personas", ps);
                body.put("total", total);
                body.put("count", count);
                String resp = com.zhiyin.logic.net.ApiGateway.postSync(
                        com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE + "/api/wallet/redpacket", body.toString(), token);
                org.json.JSONObject json = new org.json.JSONObject(resp);
                if (json.has("error")) {
                    ChatEngine.notice(json.optString("error"));
                    return;
                }
                final String marker = "(红包 " + ChatEngine.fmtMoney(total) + "元 " + count + "个)";
                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> ChatEngine.groupSend(ctx, g, marker));
            } catch (Exception e) {
                ChatEngine.notice("发红包失败: " + e.getMessage());
            }
        }).start();
    }
}
