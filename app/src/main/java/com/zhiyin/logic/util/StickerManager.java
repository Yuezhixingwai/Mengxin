package com.zhiyin.logic.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StickerManager {
    private static List<StickerItem> stickers;
    private static int defaultPackId = 1;
    private static final Random random = new Random();

    public static class StickerItem {
        public String description;
        public String fileName;
        public int packId = 1;
    }

    public static int getDefaultPackId() { return defaultPackId; }

    public static String getStickerFileNameForText(String text) {
        if (stickers == null || stickers.isEmpty() || text == null || text.isEmpty()) return null;
        String lower = text.toLowerCase();
        int bestScore = 0;
        int bestIdx = -1;
        for (int i = 0; i < EMOTION_MAP.length; i++) {
            int score = 0;
            String[] keywords = EMOTION_MAP[i][0].split(",");
            for (String kw : keywords) {
                if (lower.contains(kw.trim())) score++;
            }
            if (score > bestScore) { bestScore = score; bestIdx = i; }
        }
        if (bestIdx < 0) {
            return stickers.isEmpty() ? null : stickers.get(random.nextInt(stickers.size())).fileName;
        }
        String[] candidateNames = EMOTION_MAP[bestIdx][1].split(",");
        List<StickerItem> matches = new ArrayList<>();
        for (StickerItem s : stickers) {
            for (String name : candidateNames) {
                if (s.description != null && s.description.contains(name.trim())) {
                    matches.add(s);
                    break;
                }
            }
        }
        if (matches.isEmpty()) {
            return stickers.get(random.nextInt(stickers.size())).fileName;
        }
        return matches.get(random.nextInt(matches.size())).fileName;
    }

    private static final String[][] EMOTION_MAP = {
        {"开心,哈哈,嘻嘻,呵呵,高兴,快乐,好呀,太好,棒,棒棒,不错,喜欢,爱,好开心,笑,欢乐,雀跃,耶,嘿嘿,嘿嘿嘿", "开心,嘻嘻,眯眼笑,双眼冒爱心,惊喜,开心,开心~"},
        {"难过,伤心,哭,呜呜,委屈,泪,哭泣,悲伤,心疼,心痛,失落", "哭,大哭,嘤嘤嘤,自闭,垂头丧气"},
        {"害羞,不好意思,羞,脸红,难为情,腼腆,忸怩,羞涩,不好意思啦", "害羞,躲在被子后,偷瞄,看地板,赔笑"},
        {"生气,气,哼,讨厌,烦,怒,恼火,暴躁,嫌弃,咬你,咬", "嫌弃,咬你,咬你~,咬_2,咬_3,噗"},
        {"撒娇,萌,撒娇,黏,抱,贴,蹭,要抱抱,抱抱,撒娇", "抱着,贴,喵,咪,嘤嘤嘤"},
        {"疲惫,累,困,晚安,睡觉,休息,困了,累了,精疲力尽", "晚安,生无可恋,垂头丧气,自闭"},
        {"惊讶,惊,啊,哇,天哪,居然,竟然,真的吗,不是吧,吃惊", "惊,瞪大眼睛,惊喜,噗"},
        {"馋,饿,吃,好吃,美味,想吃,嚼,零食,美食,吃货", "馋,嚼嚼嚼,嚼,咬你~"},
        {"思考,想,嗯,考虑,琢磨,沉思,思索,想想,觉得,可能", "思考,偷瞄,看地板"},
        {"镇定,嗯,好,行,可以,明白,知道,OK,没事,算了", "赔笑,眯眼笑,点头"},
        {"喜欢,爱,想你,想,喜欢,好喜欢,超喜欢,爱,好爱,最喜欢", "双眼冒爱心,开心,害羞,开心~,嘿嘿"},
    };

    public static void init(Context context) {
        if (stickers != null) return;
        stickers = new ArrayList<>();
        loadMeta(context);
    }

    private static File metaFile(Context c) {
        return new File(c.getFilesDir(), "stickers/meta.json");
    }

    private static void loadMeta(Context c) {
        try {
            File f = metaFile(c);
            if (!f.exists()) return;
            String json = new String(readBytes(f), "UTF-8");
            parseMeta(json);
        } catch (Exception ignored) {
        }
    }

    private static void parseMeta(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            defaultPackId = obj.optInt("packId", 1);
            JSONArray arr = obj.optJSONArray("items");
            if (arr == null) return;
            List<StickerItem> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                StickerItem item = new StickerItem();
                item.description = it.optString("description", "");
                item.fileName = it.optString("fileName", "");
                item.packId = it.optInt("packId", defaultPackId);
                if (!item.fileName.isEmpty()) list.add(item);
            }
            if (!list.isEmpty()) stickers = list;
        } catch (Exception ignored) {
        }
    }

    private static void saveMeta(Context c, String json) {
        try {
            File f = metaFile(c);
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {
        }
    }

    private static byte[] readBytes(File f) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] buf = new byte[4096];
        int len;
        while ((len = fis.read(buf)) != -1) baos.write(buf, 0, len);
        fis.close();
        return baos.toByteArray();
    }

    public static void syncDefaultPackMeta(final Context c) {
        new Thread(() -> {
            try {
                String packsJson = com.zhiyin.logic.net.ApiGateway.requestSync(
                    com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE + "/api/sticker-packs", "GET", null,
                    com.zhiyin.data.AppSession.INSTANCE.token());
                JSONObject pj = new JSONObject(packsJson);
                JSONArray packs = pj.optJSONArray("packs");
                if (packs == null) return;
                int pid = -1;
                for (int i = 0; i < packs.length(); i++) {
                    JSONObject p = packs.getJSONObject(i);
                    if (p.optBoolean("is_default")) { pid = p.optInt("id"); break; }
                }
                if (pid <= 0) return;
                String detailJson = com.zhiyin.logic.net.ApiGateway.requestSync(
                    com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE + "/api/sticker-packs/" + pid, "GET", null,
                    com.zhiyin.data.AppSession.INSTANCE.token());
                JSONObject d = new JSONObject(detailJson);
                JSONArray items = d.optJSONArray("items");
                JSONObject meta = new JSONObject();
                meta.put("packId", pid);
                JSONArray arr = new JSONArray();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        JSONObject o = new JSONObject();
                        o.put("fileName", it.optString("name", ""));
                        o.put("description", it.optString("description", ""));
                        o.put("packId", pid);
                        arr.put(o);
                    }
                }
                meta.put("items", arr);
                defaultPackId = pid;
                parseMeta(meta.toString());
                saveMeta(c, meta.toString());
            } catch (Exception ignored) {
            }
        }).start();
    }

    private static File packsDir(Context c) {
        File d = new File(c.getFilesDir(), "stickers/packs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File stickerFileFor(Context c, String name) {
        if (name == null) return null;
        File root = packsDir(c);
        File[] packDirs = root.listFiles();
        if (packDirs == null) return null;
        for (File pd : packDirs) {
            if (!pd.isDirectory()) continue;
            File f = new File(pd, name);
            if (f.exists()) return f;
        }
        return null;
    }

    public static Bitmap getStickerByName(Context c, String name) {
        File f = stickerFileFor(c, name);
        if (f == null) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    public static void ensureSticker(final Context c, final String name) {
        if (name == null || stickerFileFor(c, name) != null) return;
        new Thread(() -> {
            try {
                String url = com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE
                    + "/api/sticker-packs/file/" + defaultPackId + "/" + java.net.URLEncoder.encode(name, "UTF-8");
                byte[] data = com.zhiyin.logic.net.ApiGateway.getRaw(url, c);
                if (data == null || data.length == 0) return;
                File dir = new File(packsDir(c), String.valueOf(defaultPackId));
                if (!dir.exists()) dir.mkdirs();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(dir, name));
                fos.write(data);
                fos.close();
            } catch (Exception ignored) {
            }
        }).start();
    }

    public static void cachePackImages(final Context c, final int packId, final List<String> names) {
        new Thread(() -> {
            File dir = new File(packsDir(c), String.valueOf(packId));
            if (!dir.exists()) dir.mkdirs();
            for (final String name : names) {
                try {
                    if (new File(dir, name).exists()) continue;
                    String url = com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE
                        + "/api/sticker-packs/file/" + packId + "/" + java.net.URLEncoder.encode(name, "UTF-8");
                    byte[] data = com.zhiyin.logic.net.ApiGateway.getRaw(url, c);
                    if (data == null) continue;
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(dir, name));
                    fos.write(data);
                    fos.close();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public static Bitmap loadStickerBitmap(Context c, int packId, String name) {
        if (name == null) return null;
        File f = stickerFileFor(c, name);
        if (f != null) {
            try { return BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Exception ignored) {}
        }
        try {
            String url = com.zhiyin.logic.net.ApiGateway.ZHIYIN_BASE
                + "/api/sticker-packs/file/" + packId + "/" + java.net.URLEncoder.encode(name, "UTF-8");
            byte[] data = com.zhiyin.logic.net.ApiGateway.getRaw(url, c);
            if (data == null || data.length == 0) return null;
            File dir = new File(packsDir(c), String.valueOf(packId));
            if (!dir.exists()) dir.mkdirs();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(dir, name));
            fos.write(data);
            fos.close();
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void mergePackMeta(Context c, int packId, String itemsJson) {
        try {
            JSONArray incoming = new JSONArray(itemsJson);
            List<StickerItem> merged = new ArrayList<>(stickers != null ? stickers : new ArrayList<>());
            java.util.Set<String> names = new java.util.HashSet<>();
            for (StickerItem s : merged) names.add(s.fileName);
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject it = incoming.getJSONObject(i);
                String fn = it.optString("fileName", "");
                if (fn.isEmpty() || names.contains(fn)) continue;
                StickerItem item = new StickerItem();
                item.fileName = fn;
                item.description = it.optString("description", "");
                item.packId = packId;
                merged.add(item);
                names.add(fn);
            }
            stickers = merged;
            JSONObject meta = new JSONObject();
            meta.put("packId", defaultPackId);
            JSONArray arr = new JSONArray();
            for (StickerItem s : merged) {
                JSONObject o = new JSONObject();
                o.put("fileName", s.fileName);
                o.put("description", s.description == null ? "" : s.description);
                o.put("packId", s.packId);
                arr.put(o);
            }
            meta.put("items", arr);
            saveMeta(c, meta.toString());
        } catch (Exception ignored) {
        }
    }

    public static void removePack(Context c, int packId, List<String> names) {
        try {
            File dir = new File(packsDir(c), String.valueOf(packId));
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) f.delete();
                dir.delete();
            }
            if (stickers != null && names != null) {
                java.util.Set<String> rm = new java.util.HashSet<>(names);
                List<StickerItem> kept = new ArrayList<>();
                for (StickerItem s : stickers) if (!rm.contains(s.fileName)) kept.add(s);
                stickers = kept;
                JSONObject meta = new JSONObject();
                meta.put("packId", defaultPackId);
                JSONArray arr = new JSONArray();
                for (StickerItem s : kept) {
                    JSONObject o = new JSONObject();
                    o.put("fileName", s.fileName);
                    o.put("description", s.description == null ? "" : s.description);
                    o.put("packId", s.packId);
                    arr.put(o);
                }
                meta.put("items", arr);
                saveMeta(c, meta.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public static List<StickerItem> getAllStickers() {
        return stickers != null ? stickers : new ArrayList<>();
    }

    public static StickerItem findStickerItem(String fileName) {
        if (stickers == null || fileName == null) return null;
        for (StickerItem item : stickers) {
            if (fileName.equals(item.fileName)) return item;
        }
        return null;
    }

    public static String findStickerFileNameInText(String text) {
        if (stickers == null || text == null) return null;
        String trimmed = text.trim();
        for (StickerItem item : stickers) {
            if (trimmed.equals(item.fileName)) return item.fileName;
        }
        for (StickerItem item : stickers) {
            if (trimmed.contains(item.fileName)) return item.fileName;
        }
        return null;
    }

    private static File customDir(Context c) {
        File d = new File(c.getFilesDir(), "custom_stickers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String addCustomSticker(Context c, Bitmap bmp) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] bytes = baos.toByteArray();
            String name = "cs_" + md5(bytes) + ".png";
            File f = new File(customDir(c), name);
            if (!f.exists()) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                fos.write(bytes);
                fos.close();
            }
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private static String md5(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    public static Bitmap getCustomStickerByName(Context c, String name) {
        try {
            File f = new File(customDir(c), name);
            if (!f.exists()) return null;
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    public static File getCustomStickerFile(Context c, String name) {
        return new File(customDir(c), name);
    }

    public static List<String> listCustomStickers(Context c) {
        List<String> list = new ArrayList<>();
        File[] files = customDir(c).listFiles((dir, name) -> name.startsWith("cs_") && name.endsWith(".png"));
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) list.add(f.getName());
        }
        return list;
    }
}
