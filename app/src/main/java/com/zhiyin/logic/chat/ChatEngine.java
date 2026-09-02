package com.zhiyin.logic.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import com.zhiyin.logic.data.MsgRepo;
import com.zhiyin.logic.data.SessionStore;
import com.zhiyin.logic.net.ApiGateway;
import com.zhiyin.logic.util.StickerManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatEngine {
    public static final String MARK_THINK = "|||THINK|||";
    public static final String MARK_SRC = "|||SRC|||";

    public static class Persona {
        public final String name;
        public final String desc;
        public final String pat;
        public Persona(String name, String desc, String pat) {
            this.name = name;
            this.desc = desc != null ? desc : "";
            this.pat = pat != null ? pat : "";
        }
    }

    public interface Listener {
        void onMessagesChanged();
        void onTypingChanged(boolean typing);
        void onNotice(String msg);
        void onSendingChanged(boolean sending);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener sActive;
    private static final Queue<String[]> sQueue = new ConcurrentLinkedQueue<>();
    private static volatile boolean sSending = false;
    public static final java.util.concurrent.atomic.AtomicBoolean sIsRequestPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile String lastSearchSourcesJson = "";
    private static long lastSearchKeyPromptTime = 0;
    private static final long SEARCH_KEY_PROMPT_THROTTLE = 30000L;

    public static void setActiveListener(Listener l) { sActive = l; }

    public static void notice(String msg) { notifyNotice(msg); }
    public static boolean isSending() { return sSending; }

    private static void notifyChanged() {
        final Listener l = sActive;
        if (l != null) MAIN.post(l::onMessagesChanged);
    }

    private static void notifyTyping(boolean typing) {
        sIsRequestPending.set(typing);
        final Listener l = sActive;
        if (l != null) MAIN.post(() -> l.onTypingChanged(typing));
    }

    private static void notifyNotice(String msg) {
        final Listener l = sActive;
        if (l != null) MAIN.post(() -> l.onNotice(msg));
    }

    private static void notifySending(boolean sending) {
        final Listener l = sActive;
        if (l != null) MAIN.post(() -> l.onSendingChanged(sending));
    }

    private static String[] resolveModel(Context ctx) {
        String keyId = "", model = "";
        try {
            JSONObject keyObj = new JSONObject(prefs(ctx).getString("active_text_model", ""));
            keyId = keyObj.optString("id", "");
            model = keyObj.optString("model", "");
        } catch (Exception ignored) {}
        if (model.isEmpty()) model = "gpt-4o-mini";
        return new String[]{keyId, model};
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE);
    }

    private static boolean officialQuota(Context ctx) {
        return prefs(ctx).getBoolean("use_official_quota", true);
    }

    private static String token(Context ctx) {
        return new SessionStore(ctx).getToken();
    }

    private static String uploadUrl(Context ctx, String personaName) {
        return "/api/upload?persona=" + personaName + (officialQuota(ctx) ? "&use_official=1" : "");
    }

    public static void sendText(Context ctx, Persona p, String text) {
        if (text == null || text.isEmpty()) return;
        MsgRepo.add(ctx, "persona_" + p.name, "user", text);
        notifyChanged();
        if (sSending) {
            sQueue.offer(new String[]{p.name, p.desc, p.pat, text});
            return;
        }
        executeSend(ctx, p, text);
    }

    private static void executeSend(Context ctx, Persona p, String text) {
        sSending = true;
        notifySending(true);
        notifyTyping(true);

        final String sid = "persona_" + p.name;
        final String fToken = token(ctx);
        final String[] km = resolveModel(ctx);

        new Thread(() -> {
            try {
                JSONArray messages = new JSONArray();
                String sysContent = buildSystemPrompt(ctx, p);
                if (!sysContent.isEmpty()) {
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", sysContent);
                    messages.put(sys);
                }

                String searchContext = searchForContext(ctx, text, fToken);
                if (!searchContext.isEmpty()) {
                    JSONObject searchMsg = new JSONObject();
                    searchMsg.put("role", "system");
                    searchMsg.put("content", searchContext);
                    messages.put(searchMsg);
                }

                List<String[]> history = MsgRepo.getAll(ctx, sid);
                for (String[] msg : history) {
                    String clean = cleanHistoryContent(ctx, msg[1]);
                    if (clean == null) continue;
                    String role = msg[0].equals("ai") ? "assistant" : msg[0];
                    JSONObject m = new JSONObject();
                    m.put("role", role);
                    m.put("content", clean);
                    messages.put(m);
                }

                JSONObject body = new JSONObject();
                if (!km[0].isEmpty()) body.put("api_key_id", Integer.parseInt(km[0]));
                body.put("model", km[1]);
                body.put("messages", messages);
                body.put("thinking", prefs(ctx).getBoolean("thinking_mode_enabled", false));
                body.put("sticker_enabled", prefs(ctx).getBoolean("sticker_enabled", true));
                if (officialQuota(ctx)) body.put("use_official", 1);
                body.put("persona_name", p.name);
                body.put("self_upload", 1);
                body.put("raw_user_content", text);

                String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/chat", body.toString(), fToken);
                JSONObject json = new JSONObject(resp);

                if (json.has("error")) {
                    String err = json.optString("error");
                    lastSearchSourcesJson = "";
                    MsgRepo.add(ctx, sid, "ai", "[Error] " + err);
                    notifyChanged();
                    finishSend(ctx);
                    return;
                }

                String content = "";
                if (json.has("choices"))
                    content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
                String reasoningContent = json.optString("reasoning_content", "");

                boolean voiceMode = prefs(ctx).getBoolean("voice_reply_enabled", false);
                boolean thinkingMode = prefs(ctx).getBoolean("thinking_mode_enabled", false);

                String displayReply = stripAndHandlePat(ctx, sid, p.name, content);
                boolean hasDisplayText = displayReply != null && !displayReply.isEmpty();

                if (voiceMode && hasDisplayText && !displayReply.startsWith("[Error]")) {
                    lastSearchSourcesJson = "";
                    notifyChanged();
                    ttsReply(ctx, p, displayReply);
                    finishSend(ctx);
                } else if (hasDisplayText) {
                    boolean hasThinking = thinkingMode && reasoningContent != null && !reasoningContent.isEmpty();
                    String fSearchJson = lastSearchSourcesJson;
                    if (hasThinking || !fSearchJson.isEmpty()) {
                        String thinkMsg = "";
                        if (hasThinking) thinkMsg = MARK_THINK + reasoningContent;
                        if (!fSearchJson.isEmpty()) thinkMsg = thinkMsg + MARK_SRC + fSearchJson;
                        lastSearchSourcesJson = "";
                        MsgRepo.add(ctx, sid, "ai", thinkMsg);
                        notifyChanged();
                    }
                    String stickerText = displayReply;
                    boolean sendSticker = prefs(ctx).getBoolean("sticker_enabled", true) && Math.random() < 0.15;
                    String stickerFileName = sendSticker ? StickerManager.getStickerFileNameForText(displayReply) : null;
                    if (stickerFileName != null) {
                        StickerManager.StickerItem si = StickerManager.findStickerItem(stickerFileName);
                        if (si != null) stickerText = "[STICKER:" + si.fileName + "]" + stickerText;
                    }
                    List<String> segs = splitText(stickerText);
                    int baseDelay = hasThinking ? 1000 : 0;
                    for (int i = 0; i < segs.size(); i++) {
                        final String seg = segs.get(i);
                        final long delay = baseDelay + i * 600L;
                        MAIN.postDelayed(() -> {
                            if (!seg.isEmpty()) {
                                MsgRepo.add(ctx, sid, "ai", seg);
                                notifyChanged();
                            }
                        }, delay);
                    }
                    MAIN.postDelayed(ChatEngine::finishSend, baseDelay + segs.size() * 600L + 80);
                } else {
                    finishSend(ctx);
                }
            } catch (final Exception e) {
                MsgRepo.add(ctx, sid, "ai", "Error " + e.getMessage());
                notifyChanged();
                finishSend(ctx);
            }
        }).start();
    }

    private static void finishSend(Context ctx) {
        finishSend();
    }

    private static void finishSend() {
        sSending = false;
        notifyTyping(false);
        notifySending(false);
        String[] next = sQueue.poll();
        if (next != null) {
            MAIN.postDelayed(() -> {
                android.content.Context appCtx = com.zhiyin.logic.AppHolder.app();
                if (appCtx != null) {
                    executeSend(appCtx, new Persona(next[0], next[1], next[2]), next[3]);
                } else {
                    finishSend();
                }
            }, 120);
        }
    }

    private static String buildSystemPrompt(Context ctx, Persona p) {
        StringBuilder sys = new StringBuilder();
        if (p.desc != null && !p.desc.isEmpty()) sys.append(p.desc).append("\n\n");
        if (p.pat != null && !p.pat.isEmpty()) {
            sys.append("\n\n你的拍一拍互动文案: ").append(p.pat);
            sys.append("\n（用户可以双击你的头像来触发拍一拍互动，你也可以在回复中使用[pat]指令主动拍一拍用户，例如：[pat]害羞地戳了戳你的脸）");
        }
        try {
            String memUrl = ApiGateway.getMemoryServiceUrl();
            String memUid = ApiGateway.getUserId(ctx);
            if (memUrl != null && !memUrl.isEmpty() && memUid != null && !memUid.isEmpty()) {
                String memResp = ApiGateway.memoryRequestSync(
                        memUrl + "/api/memory/" + URLEncoder.encode(p.name, "UTF-8"), "GET", null, memUid);
                JSONObject memJson = new JSONObject(memResp);
                String memText = memJson.optString("memory", "").trim();
                if (!memText.isEmpty()) {
                    sys.append("\n\n以下是关于用户的历史记忆，仅作背景参考，禁止把它当作当前消息去回复，禁止复述记忆。用户当前发来的消息在对话最底部，只回答那一条\n").append(memText);
                }
                try {
                    String profResp = ApiGateway.memoryRequestSync(memUrl + "/api/profile", "GET", null, memUid);
                    JSONObject profJson = new JSONObject(profResp);
                    JSONObject profile = profJson.optJSONObject("profile");
                    if (profile != null) {
                        String up = profile.optString("userProfile", "").trim();
                        if (!up.isEmpty()) {
                            sys.append("\n\n以下是用户填写的个人资料，仅供你了解用户（称呼、喜好、人设等），聊天时自然运用；绝对不要复述或逐条回答这些内容，也不要主动提起\n").append(up);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return sys.toString().trim();
    }

    public static void regenerate(Context ctx, Persona p, int position) {
        if (sSending) {
            notifyNotice("正在生成");
            return;
        }
        sSending = true;
        notifySending(true);
        notifyTyping(true);
        final String sid = "persona_" + p.name;
        final String fToken = token(ctx);
        final String[] km = resolveModel(ctx);
        new Thread(() -> {
            try {
                List<String[]> all = MsgRepo.getAll(ctx, sid);
                JSONArray messages = new JSONArray();
                if (p.desc != null && !p.desc.isEmpty()) {
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", p.desc);
                    messages.put(sys);
                }
                for (int i = 0; i < position && i < all.size(); i++) {
                    String[] m = all.get(i);
                    String clean = cleanHistoryContent(ctx, m[1]);
                    if (clean == null) continue;
                    JSONObject jo = new JSONObject();
                    jo.put("role", m[0].equals("ai") ? "assistant" : m[0]);
                    jo.put("content", clean);
                    messages.put(jo);
                }
                JSONObject body = new JSONObject();
                if (!km[0].isEmpty()) body.put("api_key_id", Integer.parseInt(km[0]));
                if (!km[1].isEmpty()) body.put("model", km[1]);
                body.put("messages", messages);
                if (officialQuota(ctx)) body.put("use_official", 1);
                body.put("persona_name", p.name);
                body.put("self_upload", 1);

                String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/chat", body.toString(), fToken);
                JSONObject json = new JSONObject(resp);
                if (json.has("error")) {
                    notifyNotice("重新生成失败: " + json.optString("error"));
                    finishSend();
                    return;
                }
                String content = "";
                if (json.has("choices"))
                    content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
                if (content.isEmpty()) {
                    notifyNotice("重新生成失败：返回为空");
                    finishSend();
                    return;
                }
                MsgRepo.replaceAt(ctx, sid, position, "ai", content);
                notifyChanged();
                notifyNotice("已重新生成");
                finishSend();
            } catch (Exception e) {
                notifyNotice("重新生成失败: " + e.getMessage());
                finishSend();
            }
        }).start();
    }

    public static void patFriend(Context ctx, Persona p) {
        String mask = p.pat != null && !p.pat.isEmpty() ? p.pat : "害羞地低下了头";
        String notice = "你拍了拍 " + p.name;
        String reply = p.name + mask;
        String sid = "persona_" + p.name;
        MsgRepo.add(ctx, sid, "ai", "[pat]" + notice);
        MsgRepo.add(ctx, sid, "ai", "[pat]" + reply);
        notifyChanged();
        notifyNotice("你拍了拍 " + p.name);
    }

    private static String stripAndHandlePat(Context ctx, String sid, String personaName, String reply) {
        if (reply == null || !reply.contains("[pat]")) return reply;
        StringBuilder clean = new StringBuilder();
        String[] parts = reply.split("\\[pat\\]");
        for (int i = 0; i < parts.length; i++) {
            if (i == 0) {
                clean.append(parts[i]);
                continue;
            }
            String patBlock = parts[i];
            int nlIdx = patBlock.indexOf("\n");
            String patLine = nlIdx > 0 ? patBlock.substring(0, nlIdx).trim()
                    : (nlIdx < 0 ? patBlock.trim() : "");
            String rest = nlIdx >= 0 ? patBlock.substring(nlIdx + 1).trim() : "";
            if (!patLine.isEmpty()) {
                String userPat = ctx.getSharedPreferences("zhiyin_pat", 0).getString("user_pat_message", "");
                String finalNotice = personaName + patLine;
                if (!userPat.isEmpty()) finalNotice += "\n" + userPat;
                MsgRepo.add(ctx, sid, "ai", "[pat]" + finalNotice);
            }
            if (!rest.isEmpty()) clean.append("\n").append(rest);
        }
        return clean.toString().trim();
    }

    public static void sendVoice(Context ctx, Persona p, String voiceFilePath, long durationSec) {
        boolean voiceMode = prefs(ctx).getBoolean("voice_reply_enabled", false);
        notifyTyping(true);
        String sid = "persona_" + p.name;
        MsgRepo.add(ctx, sid, "user", "[voice]" + voiceFilePath + "|" + durationSec);
        notifyChanged();
        String fToken = token(ctx);
        new Thread(() -> {
            try {
                String resp = ApiGateway.uploadSync(ApiGateway.ZHIYIN_BASE + uploadUrl(ctx, p.name), voiceFilePath, fToken);
                JSONObject json = new JSONObject(resp);
                String reply = json.optString("reply", "");
                if (!reply.isEmpty() && !reply.startsWith("[错误]")) {
                    String stickerText = reply;
                    boolean sendSticker = prefs(ctx).getBoolean("sticker_enabled", true) && Math.random() < 0.15;
                    String stickerFileName = sendSticker ? StickerManager.getStickerFileNameForText(reply) : null;
                    if (stickerFileName != null) {
                        StickerManager.StickerItem si = StickerManager.findStickerItem(stickerFileName);
                        if (si != null) stickerText = "[STICKER:" + si.fileName + "]" + stickerText;
                    }
                    List<String> segs = splitText(stickerText);
                    for (int i = 0; i < segs.size(); i++) {
                        final String seg = segs.get(i);
                        MAIN.postDelayed(() -> {
                            if (!seg.isEmpty()) {
                                MsgRepo.add(ctx, sid, "ai", seg);
                                notifyChanged();
                            }
                        }, i * 600L);
                    }
                    if (voiceMode) ttsReply(ctx, p, reply);
                } else {
                    notifyNotice("语音已发送");
                }
                notifyTyping(false);
            } catch (Exception e) {
                notifyNotice("语音上传失败: " + e.getMessage());
                notifyTyping(false);
            }
        }).start();
    }

    public static void sendImage(Context ctx, Persona p, String localPath) {
        String sid = "persona_" + p.name;
        MsgRepo.add(ctx, sid, "user", "[image]" + localPath);
        notifyChanged();
        String fToken = token(ctx);
        new Thread(() -> {
            try {
                ApiGateway.uploadSync(ApiGateway.ZHIYIN_BASE + uploadUrl(ctx, p.name), localPath, fToken);
                notifyNotice("图片已发送");
                recognizeImage(ctx, p, localPath, fToken);
            } catch (Exception e) {
                notifyNotice("图片上传失败: " + e.getMessage());
            }
        }).start();
    }

    private static void recognizeImage(Context ctx, Persona p, String localPath, String token) {
        new Thread(() -> {
            try {
                File imgFile = new File(localPath);
                byte[] bytes = new byte[(int) imgFile.length()];
                FileInputStream fis = new FileInputStream(imgFile);
                fis.read(bytes);
                fis.close();
                String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject body = new JSONObject();
                body.put("image_base64", b64);
                if (officialQuota(ctx)) body.put("use_official", 1);
                if (p.name != null && !p.name.isEmpty()) body.put("persona_name", p.name);
                if (p.desc != null && !p.desc.isEmpty()) body.put("persona_desc", p.desc);
                String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/image/recognize", body.toString(), token);
                JSONObject json = new JSONObject(resp);
                if (json.has("error")) {
                    notifyNotice("识图失败: " + json.optString("error"));
                    return;
                }
                String text = json.optString("text", "");
                if (!text.isEmpty()) {
                    String sid = "persona_" + p.name;
                    List<String> segs = splitText(text);
                    if (segs.size() <= 1) {
                        MsgRepo.add(ctx, sid, "ai", text);
                        notifyChanged();
                    } else {
                        for (int i = 0; i < segs.size(); i++) {
                            final String seg = segs.get(i);
                            MAIN.postDelayed(() -> {
                                if (!seg.isEmpty()) {
                                    MsgRepo.add(ctx, sid, "ai", seg);
                                    notifyChanged();
                                }
                            }, i * 600L);
                        }
                    }
                }
            } catch (Exception e) {
                notifyNotice("识图失败: " + e.getMessage());
            }
        }).start();
    }

    public static void sendFile(Context ctx, Persona p, String fileName, String localPath) {
        String sid = "persona_" + p.name;
        String msgKey = "[file]" + fileName;
        MsgRepo.add(ctx, sid, "user", msgKey);
        notifyChanged();
        String fToken = token(ctx);
        new Thread(() -> {
            try {
                String resp = ApiGateway.uploadSync(ApiGateway.ZHIYIN_BASE + uploadUrl(ctx, p.name), localPath, fToken);
                JSONObject json = new JSONObject(resp);
                JSONArray files = json.optJSONArray("files");
                if (files != null && files.length() > 0) {
                    String fileUrl = files.getJSONObject(0).optString("url", "");
                    if (!fileUrl.isEmpty()) {
                        MsgRepo.updateLast(ctx, sid, msgKey, msgKey + "||" + fileUrl);
                        notifyChanged();
                    }
                }
                notifyNotice("文件已发送");
            } catch (Exception e) {
                notifyNotice("文件上传失败: " + e.getMessage());
            }
        }).start();
    }

    public static String fmtMoney(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    public static void sendTransfer(Context ctx, Persona p, double amount, String note) {
        String sid = "persona_" + p.name;
        String fToken = token(ctx);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("persona", p.name);
                body.put("amount", amount);
                body.put("sessionId", sid);
                String noteSafe = note != null ? note.trim() : "";
                if (!noteSafe.isEmpty()) body.put("note", noteSafe);
                String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/wallet/transfer", body.toString(), fToken);
                JSONObject json = new JSONObject(resp);
                if (json.has("error")) {
                    notifyNotice(json.optString("error"));
                    return;
                }
                String marker = "(转账 " + fmtMoney(amount) + "元" + (noteSafe.isEmpty() ? "" : "|" + noteSafe) + ")";
                String receipt = "(收款 " + fmtMoney(amount) + "元)";
                MAIN.post(() -> {
                    MsgRepo.add(ctx, sid, "user", marker);
                    MsgRepo.add(ctx, sid, "ai", receipt);
                    notifyChanged();
                    if (sSending) {
                        sQueue.offer(new String[]{p.name, p.desc, p.pat, marker});
                    } else {
                        executeSend(ctx, p, marker);
                    }
                });
            } catch (Exception e) {
                notifyNotice("转账失败: " + e.getMessage());
            }
        }).start();
    }

    public static void sendRedpacket(Context ctx, Persona p, double total, String note) {
        String sid = "persona_" + p.name;
        String fToken = token(ctx);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sessionId", sid);
                body.put("isGroup", 0);
                JSONArray ps = new JSONArray();
                ps.put(p.name);
                body.put("personas", ps);
                body.put("total", total);
                body.put("count", 1);
                String noteSafe = note != null ? note.trim() : "";
                if (!noteSafe.isEmpty()) body.put("note", noteSafe);
                String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/wallet/redpacket", body.toString(), fToken);
                JSONObject json = new JSONObject(resp);
                if (json.has("error")) {
                    notifyNotice(json.optString("error"));
                    return;
                }
                final long packetId = json.optLong("packetId", 0);
                String marker = "(红包 " + fmtMoney(total) + "元" + (noteSafe.isEmpty() ? "" : "|" + noteSafe)
                        + (packetId > 0 ? "|#" + packetId : "") + ")";
                MAIN.post(() -> {
                    MsgRepo.add(ctx, sid, "user", marker);
                    notifyChanged();
                    if (sSending) {
                        sQueue.offer(new String[]{p.name, p.desc, p.pat, marker});
                    } else {
                        executeSend(ctx, p, marker);
                    }
                });
            } catch (Exception e) {
                notifyNotice("发红包失败: " + e.getMessage());
            }
        }).start();
    }

    public static class GroupInfo {
        public final String name;
        public final String[] members;
        public GroupInfo(String name, String[] members) {
            this.name = name;
            this.members = members != null ? members : new String[0];
        }
        public String sessionId() { return "group_" + name; }
    }

    public static void groupSend(Context ctx, GroupInfo g, String rawText) {
        String sid = g.sessionId();
        boolean officialQuota = officialQuota(ctx);
        String[] km = resolveModel(ctx);
        String token = token(ctx);
        if (!officialQuota && km[0].isEmpty()) {
            notifyNotice("请先在设置中添加并选择API Key");
            return;
        }

        HashSet<String> mentionedNames = new HashSet<>();
        Matcher matcher = Pattern.compile("@([^\\s]+)").matcher(rawText);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            for (String mn : g.members) {
                if (mn.contains(name) || name.contains(mn)) {
                    mentionedNames.add(mn);
                    break;
                }
            }
        }
        boolean mentionAll = rawText.contains("@all") || rawText.contains("@所有人");
        if (mentionAll) mentionedNames.addAll(java.util.Arrays.asList(g.members));

        MsgRepo.add(ctx, sid, "user", rawText);
        notifyChanged();
        notifyTyping(true);
        notifySending(true);

        new Thread(() -> {
            try {
                JSONArray messages = new JSONArray();
                StringBuilder systemPrompt = new StringBuilder();
                systemPrompt.append("这是一个群聊，群名: ").append(g.name).append("\n");
                systemPrompt.append("群成员: ");
                for (int i = 0; i < g.members.length; i++) {
                    if (i > 0) systemPrompt.append(", ");
                    systemPrompt.append(g.members[i]);
                }
                systemPrompt.append("\n\n");

                Map<String, String> personas = fetchContactsSync(token);
                for (String memberName : g.members) {
                    String persona = personas.get(memberName);
                    if (persona != null && !persona.isEmpty()) {
                        systemPrompt.append(memberName).append(" 的人设: ").append(persona).append("\n");
                    }
                }

                if (mentionAll) {
                    systemPrompt.append("\n用户使用了@所有人，请所有成员都回复这条消息。");
                } else if (!mentionedNames.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String n : mentionedNames) {
                        if (sb.length() > 0) sb.append("、");
                        sb.append(n);
                    }
                    systemPrompt.append("\n用户@了 ").append(sb).append("，请只让被@的成员回复，其他成员保持沉默。");
                }
                systemPrompt.append("\n请根据每个人的人设来回复群聊消息。回复时请在消息前标明是谁在说话，格式为: [名字] 消息内容");

                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt.toString());
                messages.put(sys);

                List<String[]> history = MsgRepo.getAll(ctx, sid);
                for (String[] msg : history) {
                    if (msg[1] != null && !msg[1].isEmpty() && !msg[1].startsWith("[错误]")) {
                        String role = msg[0].equals("ai") ? "assistant" : msg[0];
                        JSONObject m = new JSONObject();
                        m.put("role", role);
                        m.put("content", msg[1]);
                        messages.put(m);
                    }
                }

                JSONObject body = new JSONObject();
                if (!km[0].isEmpty()) body.put("api_key_id", Integer.parseInt(km[0]));
                body.put("model", km[1]);
                body.put("messages", messages);
                body.put("skip_shorten", true);
                if (officialQuota) body.put("use_official", 1);

                String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/chat", body.toString(), token);
                JSONObject json = new JSONObject(resp);
                if (json.has("error")) {
                    MsgRepo.add(ctx, sid, "ai", "[错误] " + json.optString("error"));
                    notifyChanged();
                    notifyTyping(false);
                    notifySending(false);
                    return;
                }
                String reply = "";
                if (json.has("choices"))
                    reply = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");

                Pattern speakerPattern = Pattern.compile("\\[([^\\]]+)\\]([^\\[]+)");
                Matcher spMatcher = speakerPattern.matcher(reply);
                boolean foundSpeaker = false;
                boolean firstSpeaker = true;
                while (spMatcher.find()) {
                    foundSpeaker = true;
                    if (firstSpeaker) {
                        firstSpeaker = false;
                        String leading = reply.substring(0, spMatcher.start()).trim();
                        if (!leading.isEmpty()) MsgRepo.add(ctx, sid, "ai", leading);
                    }
                    String speaker = spMatcher.group(1).trim();
                    String msg = spMatcher.group(2).trim();
                    if (!msg.isEmpty()) MsgRepo.add(ctx, sid, "ai", "[" + speaker + "] " + msg);
                }
                if (!foundSpeaker && !reply.isEmpty()) MsgRepo.add(ctx, sid, "ai", reply);
                notifyChanged();
                notifyTyping(false);
                notifySending(false);
            } catch (Exception e) {
                MsgRepo.add(ctx, sid, "ai", "[错误] " + e.getMessage());
                notifyChanged();
                notifyTyping(false);
                notifySending(false);
            }
        }).start();
    }

    public static Map<String, String> fetchContactsSync(String token) {
        Map<String, String> out = new HashMap<>();
        try {
            String resp = ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/user/contacts", "GET", null, token);
            JSONArray arr = new JSONObject(resp).optJSONArray("contacts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    out.put(o.optString("name", ""), o.optString("persona", ""));
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String searchForContext(Context ctx, String text, String token) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("zhiyin_search", 0);
            boolean searchEnabled = sp.getBoolean("search_enabled", false);
            boolean hasKey = sp.getBoolean("has_tavily_key", false);
            int depth = sp.getInt("search_frequency", 60);
            if (!searchEnabled) {
                lastSearchSourcesJson = "";
                return "";
            }
            if (!hasKey) {
                lastSearchSourcesJson = "";
                long now = System.currentTimeMillis();
                if (now - lastSearchKeyPromptTime > SEARCH_KEY_PROMPT_THROTTLE) {
                    lastSearchKeyPromptTime = now;
                    notifyNotice("联网搜索已开启，但尚未配置Tavily API Key");
                }
                return "";
            }
            long now = System.currentTimeMillis();
            long lastSearch = sp.getLong("last_search_time", 0);
            if (now - lastSearch < depth * 1000L) {
                lastSearchSourcesJson = "";
                long waitSec = (depth * 1000L - (now - lastSearch)) / 1000L;
                notifyNotice("搜索频率受限，请 " + waitSec + " 秒后再试");
                return "";
            }

            JSONObject body = new JSONObject();
            body.put("query", text);
            body.put("depth", sp.getString("search_depth", "basic"));
            body.put("max_results", sp.getInt("max_results", 5));
            body.put("include_summary", sp.getBoolean("include_summary", true));

            notifyNotice("正在联网搜索...");
            String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/search/tavily", body.toString(), token);
            JSONObject json = new JSONObject(resp);
            String errMsg = json.optString("error", "");
            if (!errMsg.isEmpty()) {
                notifyNotice("联网搜索失败: " + errMsg);
                lastSearchSourcesJson = "";
                return "";
            }
            JSONArray results = json.optJSONArray("results");
            String answer = json.optString("answer", "");
            sp.edit().putLong("last_search_time", now).apply();
            if (results == null || results.length() == 0) {
                lastSearchSourcesJson = "";
                return "";
            }
            try {
                JSONArray sources = new JSONArray();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    JSONObject src = new JSONObject();
                    src.put("title", r.optString("title", ""));
                    src.put("url", r.optString("url", ""));
                    src.put("content", r.optString("content", ""));
                    sources.put(src);
                }
                lastSearchSourcesJson = sources.toString();
            } catch (Exception e) {
                lastSearchSourcesJson = "";
            }
            StringBuilder c = new StringBuilder();
            c.append("从搜索结果中获得了以下参考信息（请据此回答用户的问题，但不要提及你进行了搜索）：\n\n");
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                c.append("- ").append(r.optString("title", "")).append("\n");
                c.append("  ").append(r.optString("content", "")).append("\n");
                c.append("  来源: ").append(r.optString("url", "")).append("\n\n");
            }
            if (!answer.isEmpty()) c.append("AI摘要: ").append(answer).append("\n\n");
            return c.toString();
        } catch (Exception e) {
            lastSearchSourcesJson = "";
            return "";
        }
    }

    public static void ttsReply(Context ctx, Persona p, String text) {
        new Thread(() -> {
            try {
                String t = token(ctx);
                if (t == null || t.isEmpty()) {
                    MsgRepo.add(ctx, "persona_" + p.name, "ai", text);
                    notifyChanged();
                    return;
                }
                String cleanText = cleanNarration(text);
                List<String> segments = splitText(cleanText);
                List<String> voiceFiles = new ArrayList<>();
                String[] lastError = {""};
                for (int i = 0; i < segments.size(); i++) {
                    String seg = segments.get(i);
                    if (seg.isEmpty()) continue;
                    try {
                        JSONObject body = new JSONObject();
                        body.put("text", seg);
                        body.put("voice", prefs(ctx).getString("tts_voice_global", ""));
                        if (officialQuota(ctx)) body.put("use_official", 1);
                        String resp = ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/tts", body.toString(), t);
                        JSONObject json = new JSONObject(resp);
                        String audioData = json.optString("audio_data", "");
                        String error = json.optString("error", "");
                        if (!error.isEmpty()) {
                            lastError[0] = error;
                            continue;
                        }
                        if (!audioData.isEmpty()) {
                            byte[] audioBytes = Base64.decode(audioData, Base64.DEFAULT);
                            File audioFile = new File(ctx.getCacheDir(), "voice_" + System.currentTimeMillis() + "_" + i + ".mp3");
                            FileOutputStream fos = new FileOutputStream(audioFile);
                            fos.write(audioBytes);
                            fos.close();
                            int durationSec = Math.max(1, seg.length() / 4);
                            String voiceMsg = "[voice]" + audioFile.getAbsolutePath() + "|" + durationSec;
                            MsgRepo.add(ctx, "persona_" + p.name, "ai", voiceMsg);
                            voiceFiles.add(audioFile.getAbsolutePath());
                            notifyChanged();
                        }
                    } catch (Exception segEx) {
                        lastError[0] = segEx.getMessage();
                    }
                }
                if (voiceFiles.isEmpty()) {
                    MsgRepo.add(ctx, "persona_" + p.name, "ai", text);
                    notifyChanged();
                    notifyNotice("TTS失败: " + lastError[0]);
                }
            } catch (Exception e) {
                MsgRepo.add(ctx, "persona_" + p.name, "ai", text);
                notifyChanged();
                notifyNotice("TTS失败，已显示文字: " + e.getMessage());
            }
        }).start();
    }

    private static String cleanNarration(String text) {
        text = text.replaceAll("\\*{1,2}[^*]+\\*{1,2}", "");
        text = text.replaceAll("\\([^)]+\\)", "");
        text = text.replaceAll("（[^）]+）", "");
        text = text.replaceAll("【[^】]+】", "");
        text = text.replaceAll("〖[^〗]+〗", "");
        text = text.replaceAll("(?m)^旁白[：:].*$", "");
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? "好的" : text;
    }

    public static String cleanHistoryContent(Context ctx, String raw) {
        if (raw == null) return null;
        String c = raw.trim();
        if (c.isEmpty() || "[空消息]".equals(c)) return null;
        if (c.startsWith("[错误]") || c.startsWith("[pat]") || c.startsWith("[image]")
                || c.startsWith("[voice]") || c.startsWith("[file]")) return null;
        if (c.contains(MARK_THINK) || c.contains(MARK_SRC) || c.contains("|||voice_reply|||") || c.contains("|||auto_sticker|||")) return null;
        if (c.startsWith("(转账") || c.startsWith("(红包") || c.startsWith("(收款")) {
            c = c.replaceAll("\\|#\\d+", "");
        }
        if (c.contains("[STICKER:")) {
            Matcher m = Pattern.compile("\\[STICKER:([^\\]]*)\\]").matcher(c);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String fn = m.group(1);
                StickerManager.StickerItem si = StickerManager.findStickerItem(fn);
                String desc = (si != null && si.description != null && !si.description.isEmpty()) ? si.description : "表情";
                m.appendReplacement(sb, Matcher.quoteReplacement("[表情:" + desc + "]"));
            }
            m.appendTail(sb);
            c = sb.toString().trim();
        }
        if (c.isEmpty()) return null;
        return c;
    }

    public static List<String> splitText(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        text = text.trim();
        if (text.isEmpty()) return out;
        List<String> units = new ArrayList<>();
        if (text.indexOf('\n') >= 0) {
            for (String ln : text.split("\\n")) {
                String s = stripTrailingCommas(ln.trim());
                if (s.isEmpty()) continue;
                if (s.length() > 40) {
                    units.addAll(splitByDelims(s));
                } else {
                    units.add(s);
                }
            }
        } else {
            units.addAll(splitByDelims(text));
        }
        if (units.isEmpty()) {
            out.add(stripTrailingCommas(text));
            return out;
        }
        if (units.size() <= 6) return units;
        return groupPieces(units, 6);
    }

    private static List<String> splitByDelims(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String delims = "\u3002\uff01\uff1f!?\u2026\uff0c,\uff1b;\u3001";
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            cur.append(ch);
            if (delims.indexOf(ch) >= 0) {
                String s = stripTrailingCommas(cur.toString().trim());
                cur.setLength(0);
                if (!s.isEmpty()) pieces.add(s);
            }
        }
        String tail = stripTrailingCommas(cur.toString().trim());
        if (!tail.isEmpty()) pieces.add(tail);
        if (pieces.isEmpty()) pieces.add(stripTrailingCommas(text));
        return pieces;
    }

    private static String stripTrailingCommas(String s) {
        int end = s.length();
        while (end > 0) {
            char last = s.charAt(end - 1);
            if (last == '\uFF0C' || last == ',') end--;
            else break;
        }
        return end == s.length() ? s : s.substring(0, end);
    }

    private static List<String> groupPieces(List<String> pieces, int maxCount) {
        List<String> out = new ArrayList<>();
        if (pieces.isEmpty()) return out;
        int totalLen = 0;
        for (String p : pieces) totalLen += p.length();
        int target = Math.max(20, (int) Math.ceil((double) totalLen / maxCount));
        StringBuilder acc = new StringBuilder();
        for (String p : pieces) {
            if (acc.length() > 0 && acc.length() + p.length() > target) {
                out.add(acc.toString());
                acc.setLength(0);
            }
            if (acc.length() > 0) acc.append('\n');
            acc.append(p);
        }
        if (acc.length() > 0) out.add(acc.toString());
        return out;
    }

    public static String previewText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.startsWith("[voice]")) return "[语音消息]";
        if (raw.startsWith("|||voice_reply|||")) return "[语音消息]";
        if (raw.startsWith("[STICKER:")) return "[表情消息]";
        if (raw.startsWith("[CUSTOM_STICKER:")) return "[表情消息]";
        if (raw.startsWith("|||auto_sticker|||")) return "[表情消息]";
        if (raw.startsWith("[image]")) return "[图片]";
        if (raw.startsWith("(转账") || raw.startsWith("(红包") || raw.startsWith("(收款")) {
            int bar = raw.indexOf('|');
            return bar > 0 ? raw.substring(0, bar) + ")" : raw;
        }
        if (raw.startsWith("[file]")) {
            String fileInfo = raw.substring(6).trim();
            int sep = fileInfo.indexOf("||");
            if (sep > 0) fileInfo = fileInfo.substring(0, sep);
            return "[文件] " + fileInfo;
        }
        if (raw.startsWith("[pat]")) return raw.substring(5).split("\n")[0];
        if (raw.contains("|||STICKER|||")) {
            raw = raw.replaceAll("\\|\\|\\|STICKER\\|\\|\\|[^\\n]*", "").trim();
        }
        int stickerPos = raw.indexOf("[STICKER:");
        if (stickerPos > 0) {
            raw = raw.substring(0, stickerPos) + raw.substring(stickerPos).replaceAll("\\[STICKER:[^\\]]*\\]", "");
        }
        if (raw.contains(MARK_THINK)) raw = raw.split("\\|\\|\\|THINK\\|\\|\\|")[0];
        if (raw.contains(MARK_SRC)) raw = raw.split("\\|\\|\\|SRC\\|\\|\\|")[0];
        return raw.trim();
    }

    public static void loadServerHistory(Context ctx, Persona p, Runnable onDone) {
        String sid = "persona_" + p.name;
        if (!MsgRepo.getAll(ctx, sid).isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        new Thread(() -> {
            try {
                String t = token(ctx);
                String resp = ApiGateway.getSync(ApiGateway.ZHIYIN_BASE
                        + "/api/wechat/persona/" + URLEncoder.encode(p.name, "UTF-8") + "/history", t);
                JSONObject json = new JSONObject(resp);
                JSONArray history = json.optJSONArray("history");
                if (history != null && history.length() > 0) {
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject msg = history.getJSONObject(i);
                        String role = msg.optString("role", "user");
                        String content = msg.optString("content", "");
                        String appRole = "assistant".equals(role) ? "ai" : "user";
                        String trimmed = content.trim();
                        if (trimmed.isEmpty()) continue;
                        String key = appRole + "|" + trimmed;
                        if (seen.contains(key)) continue;
                        seen.add(key);
                        MsgRepo.add(ctx, sid, appRole, content);
                    }
                    notifyChanged();
                }
            } catch (Exception ignored) {}
            if (onDone != null) onDone.run();
        }).start();
    }

    public static void syncCustomStickers(Context ctx) {
        new Thread(() -> {
            try {
                String t = token(ctx);
                String resp = ApiGateway.getSync(ApiGateway.ZHIYIN_BASE + "/api/stickers", t);
                JSONObject json = new JSONObject(resp);
                JSONArray arr = json.optJSONArray("stickers");
                if (arr == null) return;
                boolean changed = false;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.optString("name", "");
                    if (name.isEmpty()) continue;
                    File local = StickerManager.getCustomStickerFile(ctx, name);
                    if (local.exists()) continue;
                    byte[] data = ApiGateway.getRaw("/api/stickers/file/" + URLEncoder.encode(name, "UTF-8"), ctx);
                    if (data != null && data.length > 0) {
                        FileOutputStream fos = new FileOutputStream(local);
                        fos.write(data);
                        fos.close();
                        changed = true;
                    }
                }
                if (changed) notifyChanged();
            } catch (Exception e) {
                Log.d("SyncStickers", "sync fail: " + e.getMessage());
            }
        }).start();
    }

    public static void backupCustomSticker(Context ctx, String name) {
        new Thread(() -> {
            try {
                String t = token(ctx);
                File f = StickerManager.getCustomStickerFile(ctx, name);
                byte[] bytes = new byte[(int) f.length()];
                FileInputStream fis = new FileInputStream(f);
                fis.read(bytes);
                fis.close();
                String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("image_base64", b64);
                ApiGateway.postSync(ApiGateway.ZHIYIN_BASE + "/api/stickers/register", body.toString(), t);
            } catch (Exception e) {
                Log.d("AddSticker", "register fail: " + e.getMessage());
            }
        }).start();
    }
}
