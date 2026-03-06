package com.example.mindflow.service;

import android.app.Activity; // 👈 记得加这个引用
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.mindflow.R;
import com.example.mindflow.utils.ScreenCaptureDataHolder;
import com.example.mindflow.utils.ScreenCaptureManager;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. 启动前台服务 (必须)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        // 2. 从静态 Holder 获取权限数据
        int resultCode = ScreenCaptureDataHolder.getResultCode();
        Intent resultData = ScreenCaptureDataHolder.getResultData();

        Log.d("MindFlow", "Service 获取到的 resultCode: " + resultCode + " (RESULT_OK是-1)");

        // 👇👇👇【核心修复在这里】👇👇👇
        // 之前的写法是 (resultCode != -1)，这是错的！
        // 正确的写法是：只要 resultData 不为空，且 resultCode 是 RESULT_OK (-1)，就开始
        if (resultData != null && resultCode == Activity.RESULT_OK) {

            Log.d("MindFlow", "Service 权限校验通过，正在启动 Manager...");

            try {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection mediaProjection = mpm.getMediaProjection(resultCode, resultData);

                if (mediaProjection != null) {
                    ScreenCaptureManager.getInstance().start(this, mediaProjection);
                } else {
                    Log.e("MindFlow", "MediaProjection 获取失败 (null)");
                }
            } catch (Exception e) {
                Log.e("MindFlow", "Service 内部崩溃", e);
            }
        } else {
            // 打印出具体是哪个数据不对，方便调试
            Log.e("MindFlow", "Service 启动失败：数据无效。Data: " + resultData + ", Code: " + resultCode);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("MindFlow", "Service 销毁");
        ScreenCaptureManager.getInstance().stop();
        ScreenCaptureDataHolder.clear();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "专注模式录屏服务",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MindFlow 专注中")
                .setContentText("正在运行 AI 屏幕分析...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        return builder.build();
    }
}