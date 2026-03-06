package com.example.mindflow.ui.lock;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public String label;        // 应用名称
    public String packageName;  // 包名 (唯一标识)
    public Drawable icon;       // 应用图标

    public AppInfo(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }
}