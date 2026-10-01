package com.zhiyin.logic.data;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class SessionStore {
    private final SharedPreferences sp;
    private static SecretKeySpec secretKey;
    private static Context appContext;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        if (secretKey != null) return;
        String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = "zhiyin_default_key";
        byte[] keyBytes = new byte[32];
        byte[] seed = (androidId + "zhiyin_app_salt_2024").getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 32; i++) keyBytes[i] = seed[i % seed.length];
        for (int i = 0; i < seed.length; i++) keyBytes[i % 32] ^= seed[i];
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    private static String encrypt(String plain) {
        if (secretKey == null || plain == null) return plain;
        try {
            Cipher c = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LEN, iv);
            c.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] encrypted = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[GCM_IV_LEN + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LEN);
            System.arraycopy(encrypted, 0, combined, GCM_IV_LEN, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) { return plain; }
    }

    private static String decrypt(String encoded) {
        if (secretKey == null || encoded == null) return encoded;
        try {
            byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
            if (combined.length < GCM_IV_LEN + 1) return encoded;
            Cipher c = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LEN, combined, 0, GCM_IV_LEN);
            c.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] plain = c.doFinal(combined, GCM_IV_LEN, combined.length - GCM_IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) { return encoded; }
    }

    public static String getTokenInternal() {
        if (appContext == null) return null;
        SharedPreferences sp = appContext.getSharedPreferences("zhiyin_session", 0);
        String stored = sp.getString("token", null);
        if (stored == null) return null;
        String decrypted = decrypt(stored);
        if (decrypted.equals(stored)) {
            String encrypted = encrypt(stored);
            if (!encrypted.equals(stored))
                sp.edit().putString("token", encrypted).apply();
        }
        if (!decrypted.startsWith("eyJ")) {
            sp.edit().remove("token").apply();
            return null;
        }
        return decrypted;
    }

    public SessionStore(Context ctx) {
        sp = ctx.getSharedPreferences("zhiyin_session", 0);
    }

    public void saveToken(String t) {
        sp.edit().putString("token", encrypt(t)).apply();
    }

    public String getToken() {
        String stored = sp.getString("token", "");
        if (stored.isEmpty()) return stored;
        String decrypted = decrypt(stored);
        if (decrypted.equals(stored)) {
            String encrypted = encrypt(stored);
            if (!encrypted.equals(stored))
                sp.edit().putString("token", encrypted).apply();
        }
        if (!decrypted.startsWith("eyJ")) {
            sp.edit().remove("token").apply();
            return "";
        }
        return decrypted;
    }

    public void saveUser(String u) { sp.edit().putString("user", u).apply(); }
    public String getUser() { return sp.getString("user", ""); }
    public void saveUserId(String id) { sp.edit().putString("userId", id != null ? id : "").apply(); }
    public String getUserId() { return sp.getString("userId", ""); }
    public boolean isLoggedIn() { return !getToken().isEmpty(); }
    public void clear() { sp.edit().clear().apply(); }
}
