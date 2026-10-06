-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-allowaccessmodification

# ============ 灵心助手（手机操控）============
# 无障碍服务 / 任务面板由系统与清单引用，R8 需保留；Agent 全包保留，避免反射与回调被裁剪
-keep class com.zhiyin.agent.** { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# ============ Shizuku 进阶通道 ============

# ============ 病娇模式：设备管理器 ============
# 由 AndroidManifest 的 <receiver> 引用。显式保留类名与成员，
# 否则混淆后系统按清单里的类名找不到实现，设备管理器授权弹窗就起不来。
-keep class com.zhiyin.yandere.YandereDeviceAdminReceiver { *; }
-keep class com.zhiyin.yandere.YandereManager { *; }
