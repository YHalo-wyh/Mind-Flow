package com.example.mindflow.service;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.database.dao.AppEventDao;
import com.example.mindflow.model.AppEvent;

import java.util.concurrent.Executors;

/**
 * App使用轨迹采集服务
 * 使用UsageStatsManager API采集应用使用数据
 */
public class UsageStatsCollector {

    private final Context context;
    private final AppEventDao appEventDao;
    private String currentSessionId = null;

    public UsageStatsCollector(Context context) {
        this.context = context;
        MindFlowDatabase db = MindFlowDatabase.getInstance(context);
        this.appEventDao = db.appEventDao();
    }

    /**
     * 设置当前专注会话ID
     */
    public void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    /**
     * 采集指定时间范围内的App使用事件
     * 
     * @param startTime 开始时间戳(毫秒)
     * @param endTime   结束时间戳(毫秒)
     */
    public void collectUsageEvents(long startTime, long endTime) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager == null) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();

            String lastPackage = null;
            long lastMoveToForegroundTime = 0;

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);

                int eventType = event.getEventType();
                String packageName = event.getPackageName();
                long timestamp = event.getTimeStamp();

                if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    // App进入前台
                    if (lastPackage != null && lastMoveToForegroundTime > 0) {
                        // 保存上一个App的使用记录
                        saveAppEvent(lastPackage, lastMoveToForegroundTime, timestamp);
                    }
                    lastPackage = packageName;
                    lastMoveToForegroundTime = timestamp;
                } else if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    // App进入后台
                    if (packageName.equals(lastPackage) && lastMoveToForegroundTime > 0) {
                        saveAppEvent(packageName, lastMoveToForegroundTime, timestamp);
                        lastPackage = null;
                        lastMoveToForegroundTime = 0;
                    }
                } else if (eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    // 屏幕关闭
                    saveScreenEvent("screen_off", timestamp);
                } else if (eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                    // 屏幕开启
                    saveScreenEvent("screen_on", timestamp);
                }
            }
        });
    }

    private void saveAppEvent(String packageName, long startTime, long endTime) {
        AppEvent appEvent = new AppEvent();
        appEvent.eventType = "foreground_app";
        appEvent.packageName = packageName;
        appEvent.appCategory = categorizeApp(packageName);
        appEvent.startTs = startTime;
        appEvent.endTs = endTime;
        appEvent.durationMs = endTime - startTime;
        appEvent.inFocusSession = currentSessionId != null;
        appEvent.sessionId = currentSessionId;

        appEventDao.insert(appEvent);
    }

    private void saveScreenEvent(String eventType, long timestamp) {
        AppEvent appEvent = new AppEvent();
        appEvent.eventType = eventType;
        appEvent.startTs = timestamp;
        appEvent.endTs = timestamp;
        appEvent.durationMs = 0;
        appEvent.inFocusSession = currentSessionId != null;
        appEvent.sessionId = currentSessionId;

        appEventDao.insert(appEvent);
    }

    /**
     * 根据包名分类App
     * 实际使用中可以扩展为从数据库或配置文件读取分类规则
     */
    private String categorizeApp(String packageName) {
        if (packageName == null)
            return "system";

        // 学习类
        if (packageName.contains("edu") || packageName.contains("learn") ||
                packageName.contains("study") || packageName.contains("course")) {
            return "learning";
        }

        // 工作类
        if (packageName.contains("office") || packageName.contains("mail") ||
                packageName.contains("work") || packageName.contains("docs") ||
                packageName.contains("slack") || packageName.contains("teams")) {
            return "work";
        }

        // 社交类
        if (packageName.contains("social") || packageName.contains("chat") ||
                packageName.contains("messenger") || packageName.contains("whatsapp") ||
                packageName.contains("wechat") || packageName.contains("qq") ||
                packageName.contains("weibo") || packageName.contains("twitter") ||
                packageName.contains("instagram") || packageName.contains("facebook")) {
            return "social";
        }

        // 娱乐类
        if (packageName.contains("video") || packageName.contains("music") ||
                packageName.contains("game") || packageName.contains("tiktok") ||
                packageName.contains("douyin") || packageName.contains("bilibili") ||
                packageName.contains("youtube") || packageName.contains("netflix")) {
            return "entertainment";
        }

        // 工具类
        if (packageName.contains("calculator") || packageName.contains("clock") ||
                packageName.contains("calendar") || packageName.contains("file") ||
                packageName.contains("camera") || packageName.contains("browser")) {
            return "tools";
        }

        // 系统类
        if (packageName.contains("android") || packageName.contains("system") ||
                packageName.contains("launcher") || packageName.contains("settings")) {
            return "system";
        }

        return "other";
    }

    /**
     * 采集最近5分钟的使用数据
     */
    public void collectRecentUsage() {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (5 * 60 * 1000); // 5分钟前
        collectUsageEvents(startTime, endTime);
    }
}
