package com.zhiyin.logic;

import android.content.Context;

public class AppHolder {
    private static volatile Context sApp;

    public static void init(Context ctx) {
        sApp = ctx.getApplicationContext();
    }

    public static Context app() {
        return sApp;
    }
}
