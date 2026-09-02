package com.zhiyin.logic.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StickerManager {
    private static List<StickerItem> stickers;
    private static Random random = new Random();

    public static class StickerItem {
        public String description;
        public String fileName;
        public String emotion;
    }

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
        java.util.List<StickerItem> matches = new java.util.ArrayList<>();
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
        try {
            InputStream is = context.getAssets().open("stickers/custom_stickers.json");
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
            is.close();
            String json = baos.toString("UTF-8");
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                StickerItem item = new StickerItem();
                item.description = obj.optString("description", "");
                item.fileName = obj.optString("fileName", "");
                if (!item.fileName.isEmpty()) stickers.add(item);
            }
        } catch (Exception e) {
            stickers.clear();
        }
    }

    public static Bitmap getRandomSticker(Context context) {
        if (stickers == null || stickers.isEmpty()) return null;
        StickerItem item = stickers.get(random.nextInt(stickers.size()));
        try {
            InputStream is = context.getAssets().open("stickers/" + item.fileName);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap getStickerByName(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open("stickers/" + fileName);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<StickerItem> getAllStickers() {
        return stickers != null ? stickers : new ArrayList<>();
    }

    public static StickerItem getRandomStickerItem() {
        if (stickers == null || stickers.isEmpty()) return null;
        return stickers.get(random.nextInt(stickers.size()));
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

    public static java.util.List<String> listCustomStickers(Context c) {
        java.util.List<String> list = new ArrayList<>();
        File[] files = customDir(c).listFiles((dir, name) -> name.startsWith("cs_") && name.endsWith(".png"));
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) list.add(f.getName());
        }
        return list;
    }
}
