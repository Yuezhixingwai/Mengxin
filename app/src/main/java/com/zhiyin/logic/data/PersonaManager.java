package com.zhiyin.logic.data;
import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PersonaManager {
    public static class Persona {
        public String name, keywords, description, pat;
        public Persona(String n, String k, String d, String p) { name=n; keywords=k; description=d; pat=p; }
    }
    private static List<Persona> cache;
    public static List<Persona> getAll(Context ctx) {
        if (cache != null) return cache;
        cache = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list("personas");
            if (files == null) return cache;
            for (String f : files) {
                if (!f.endsWith(".json")) continue;
                try {
                    InputStream is = ctx.getAssets().open("personas/"+f);
                    byte[] buf = new byte[is.available()]; is.read(buf); is.close();
                    JSONObject o = new JSONObject(new String(buf,"UTF-8"));
                    String name = o.optString("name", f.replace(".json",""));
                    String pat = o.optString("pat", "轻轻拍了拍{name}的头");
                    pat = pat.replace("{name}", name);
                    cache.add(new Persona(name, o.optString("personality_keywords",""), o.optString("description",""), pat));
                } catch(Exception e) {}
            }
        } catch(Exception e) {}
        return cache;
    }
    public static boolean isOfficial(Context ctx, String name) {
        if (name == null) return false;
        for (Persona p : getAll(ctx)) {
            if (name.equals(p.name)) return true;
        }
        return false;
    }

    public static String getPatByName(Context ctx, String name) {
        for (Persona p : getAll(ctx)) {
            if (p.name.equals(name)) return p.pat;
        }
        return "轻轻拍了拍" + name + "的头";
    }
}
