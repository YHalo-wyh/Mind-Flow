package com.example.mindflow.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * 设备管理员接收器
 * 
 * 功能：
 * 1. 防止应用被卸载
 * 2. 配合锁机功能实现系统级权限
 */
public class MindFlowDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "DeviceAdminReceiver";
    
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "✅ 设备管理员已启用");
        Toast.makeText(context, "专注模式保护已启用", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "❌ 设备管理员已禁用");
        Toast.makeText(context, "专注模式保护已禁用", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // 当用户尝试禁用设备管理员时显示警告
        return "禁用后将无法保护专注锁定功能，确定要禁用吗？";
    }
}
