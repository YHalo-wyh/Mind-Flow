package com.example.mindflow.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mindflow.ui.lock.LockScreenActivity;

/**
 * 开机自启动接收器
 * 
 * 功能：
 * 1. 检测设备重启
 * 2. 恢复锁机状态（如果锁机未结束）
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.i(TAG, "📱 设备启动完成，检查锁机状态");
            
            // 检查LockScreenActivity的持久化状态
            SharedPreferences prefs = context.getSharedPreferences("lock_screen_state", Context.MODE_PRIVATE);
            boolean isActive = prefs.getBoolean("is_active", false);
            long endTime = prefs.getLong("end_time", 0);
            
            if (isActive && endTime > System.currentTimeMillis()) {
                // 锁机未结束，恢复锁机
                long remainingMs = endTime - System.currentTimeMillis();
                Log.i(TAG, "🔒 恢复锁机，剩余: " + (remainingMs / 1000) + "秒");
                
                // 启动LockScreenActivity
                Intent lockIntent = new Intent(context, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                lockIntent.putExtra("duration", remainingMs);
                lockIntent.putExtra("reason", "设备重启后恢复专注锁定");
                context.startActivity(lockIntent);
            } else {
                // 清除过期的锁机状态
                prefs.edit().clear().apply();
                Log.i(TAG, "✅ 无需恢复锁机");
            }
        }
    }
}
