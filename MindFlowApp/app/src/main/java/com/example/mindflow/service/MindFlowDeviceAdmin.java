package com.example.mindflow.service;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * 设备管理器接收器 - 用于增强锁机强制性
 * 类似番茄ToDo的"提高强制性"功能
 */
public class MindFlowDeviceAdmin extends DeviceAdminReceiver {
    private static final String TAG = "MindFlowDeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "设备管理器已启用");
        Toast.makeText(context, "MindFlow 强制锁机已启用", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "设备管理器已禁用");
        Toast.makeText(context, "MindFlow 强制锁机已禁用", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // 当用户尝试禁用设备管理器时的提示
        return "禁用后将无法使用强制锁机功能";
    }
}
