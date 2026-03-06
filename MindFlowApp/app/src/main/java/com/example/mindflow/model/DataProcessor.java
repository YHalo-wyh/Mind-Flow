package com.example.mindflow.model;

import com.example.mindflow.model.AppEvent;
import com.example.mindflow.model.FeatureWindow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataProcessor {

    // 把一堆事件聚合成一个窗口特征
    public static FeatureWindow aggregate(List<AppEvent> events) {
        FeatureWindow window = new FeatureWindow();

        window.windowEnd = System.currentTimeMillis();
        window.windowStart = window.windowEnd - (5 * 60 * 1000); // 5分钟前

        if (events == null || events.isEmpty()) {
            return window;
        }

        long screenOnDuration = 0;
        Map<String, Long> categoryDuration = new HashMap<>();
        int switchCount = 0;

        for (AppEvent event : events) {
            // 简单统计：如果是前台事件，就算一次切换
            if ("foreground_app".equals(event.eventType)) {
                switchCount++;
                String cat = event.appCategory != null ? event.appCategory : "other";
                categoryDuration.put(cat, categoryDuration.getOrDefault(cat, 0L) + event.durationMs);
            }

            // 简单统计亮屏（假设事件里有 duration）
            if ("screen_on".equals(event.eventType)) {
                screenOnDuration += event.durationMs;
            }
        }

        window.appSwitchCount = switchCount;
        window.screenOnMs = screenOnDuration;
        window.majorAppCategory = findMajorCategory(categoryDuration);

        // 补全其他必填字段，防止模型报错
        window.touchCount = 0;
        window.scrollCount = 0;
        window.scrollSpeed = 0f;
        window.keyStrokeCount = 0;
        window.notifReceivedCount = 0; // 注意这里你的变量名可能叫 notifReceivedCount
        window.stepCount = 0;
        window.hrMean = 0;
        window.hrvRmssd = 0;
        window.noiseLevel = 0;

        return window;
    }

    private static String findMajorCategory(Map<String, Long> map) {
        String major = "other";
        long maxDuration = -1;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (entry.getValue() > maxDuration) {
                maxDuration = entry.getValue();
                major = entry.getKey();
            }
        }
        return major;
    }
}
