package com.example.autopager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public class PermissionUtils {

    public static void requestAccessibilityPermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        activity.startActivity(intent);
    }

    public static void requestOverlayPermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        activity.startActivity(intent);
    }

    public static boolean isServiceEnabled(Context context, Class<?> clazz) {
        String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        String pkgName = context.getPackageName();
        String className = pkgName + "/" + clazz.getName();
        return enabledServices.contains(className);
    }
}