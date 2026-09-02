package com.zhiyin.logic.data;
import android.os.Handler;
import android.os.Looper;
import com.zhiyin.logic.net.ApiGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class FriendManager {
    private static final Map<String, Integer> sNameToId = new HashMap<>();
    private static volatile List<Friend> sFriendsCache = new ArrayList<>();
    public static class Friend {
        public int id;
        public String name, persona, avatar;
        public boolean mute;
        public Friend(int id, String n, String p, String a) { this.id=id; name=n; persona=p; avatar=a!=null?a:""; mute=false; }
        public Friend(int id, String n, String p, String a, boolean m) { this.id=id; name=n; persona=p; avatar=a!=null?a:""; mute=m; }
    }

    public interface Callback { void onResult(List<Friend> friends); void onError(String err); }

    public static void getAll(String token, Callback cb) {
        ApiGateway.get("/api/user/contacts", token, new ApiGateway.Callback() {
            public void onSuccess(String resp) {
                try {
                    JSONArray arr = new JSONObject(resp).optJSONArray("contacts");
                    List<Friend> list = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new Friend(o.optInt("id"), o.optString("name",""), o.optString("persona",""), o.optString("avatar",""), o.optBoolean("mute", false)));
                        }
                    }
                    sNameToId.clear();
                    for (Friend f : list) {
                        if (f.name != null && !f.name.isEmpty()) sNameToId.put(f.name, f.id);
                    }
                    sFriendsCache = list;
                    new Handler(Looper.getMainLooper()).post(() -> cb.onResult(list));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> cb.onError(e.getMessage()));
                }
            }
            public void onError(String err) {
                new Handler(Looper.getMainLooper()).post(() -> cb.onError(err));
            }
        });
    }

    public static void add(String token, String name, String persona, String avatar, ApiGateway.Callback cb) {
        JSONObject body = new JSONObject();
        try { body.put("name", name); body.put("persona", persona != null ? persona : ""); body.put("avatar", avatar != null ? avatar : ""); body.put("mute", false); } catch (Exception e) {}
        ApiGateway.post("/api/user/contacts", body.toString(), token, cb);
    }

    public static void update(String token, int id, String name, String persona, String avatar, ApiGateway.Callback cb) {
        JSONObject body = new JSONObject();
        try {
            if (name != null) body.put("name", name);
            if (persona != null) body.put("persona", persona);
            if (avatar != null) body.put("avatar", avatar);
        } catch (Exception e) {}
        ApiGateway.put("/api/user/contacts/" + id, body.toString(), token, cb);
    }

    public static void updateMute(String token, int id, boolean mute, ApiGateway.Callback cb) {
        JSONObject body = new JSONObject();
        try { body.put("mute", mute); } catch (Exception e) {}
        ApiGateway.put("/api/user/contacts/" + id, body.toString(), token, cb);
    }

    public static void remove(String token, int id, ApiGateway.Callback cb) {
        ApiGateway.delete("/api/user/contacts/" + id, token, cb);
    }
    public static int findIdByName(String name) {
        if (name == null) return -1;
        Integer id = sNameToId.get(name);
        return id == null ? -1 : id;
    }

    public static List<Friend> getCachedFriends() {
        return sFriendsCache;
    }
}
