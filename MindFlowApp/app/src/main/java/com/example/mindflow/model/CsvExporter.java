package com.example.mindflow.model;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.example.mindflow.database.MindFlowDatabase;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class CsvExporter {
    private static final String USER_ID = "user_001";

    public static void export(Context context) {
        new Thread(() -> {
            try {
                List<AppEvent> events = MindFlowDatabase.getInstance(context).appEventDao().getAllEvents();
                if (events == null || events.isEmpty()) return;

                File file = new File(context.getExternalFilesDir(null), "app_events.csv");
                FileWriter writer = new FileWriter(file);
                PackageManager pm = context.getPackageManager();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

                // 表头
                writer.append("event_id,user_id,app_name,pkg_name,readable_time,start_ts,content_info\n");

                // 找到 exportAppEvents 方法中的循环部分，替换为：
                for (AppEvent event : events) {
                    // 改进的应用名获取逻辑
                    String appLabel = "Unknown";
                    if (event.packageName != null) {
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(event.packageName, 0);
                            appLabel = pm.getApplicationLabel(ai).toString();
                        } catch (Exception e) {
                            // 如果查不到中文名，就用包名代替，这样你就知道是谁了
                            appLabel = event.packageName;
                        }
                    }

                    // 格式化时间
                    String readableTime = sdf.format(new Date(event.startTs));

                    // 强制清理 details，防止出现 "null" 字符串
                    String safeDetails = (event.details == null || event.details.equals("null")) ? "Active" : event.details;
                    safeDetails = safeDetails.replace(",", " ").replace("\n", " ");

                    writer.append(UUID.randomUUID().toString()).append(",")
                            .append(USER_ID).append(",")
                            .append(appLabel).append(",") // 现在这里不会是 Unknown 了
                            .append(String.valueOf(event.packageName)).append(",")
                            .append(readableTime).append(",")
                            .append(String.valueOf(event.startTs)).append(",")
                            .append("\"").append(safeDetails).append("\"\n");
                }
                writer.flush();
                writer.close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}