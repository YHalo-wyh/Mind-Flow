package com.example.mindflow.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * 环境上下文采集服务
 * 采集位置类别、网络类型、噪音级别等环境数据
 */
public class EnvironmentCollector {

    private final Context context;

    public EnvironmentCollector(Context context) {
        this.context = context;
    }

    /**
     * 获取当前网络类型
     */
    public String getNetworkType() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return "none";
        }

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return "none";
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) {
            return "none";
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "wifi";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "mobile";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ethernet";
        }

        return "other";
    }

    /**
     * 获取位置类别
     * 简化实现：基于时间推断位置类别
     * 实际使用中可以结合GPS或基站定位
     */
    public String getLocationCategory() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);

        boolean isWeekend = (dayOfWeek == java.util.Calendar.SATURDAY ||
                dayOfWeek == java.util.Calendar.SUNDAY);

        // 简单的基于时间的位置推断
        if (hour >= 0 && hour < 7) {
            return "home"; // 凌晨在家
        } else if (hour >= 7 && hour < 9) {
            return isWeekend ? "home" : "commute"; // 早高峰
        } else if (hour >= 9 && hour < 18) {
            return isWeekend ? "other" : "company"; // 工作时间
        } else if (hour >= 18 && hour < 20) {
            return isWeekend ? "other" : "commute"; // 晚高峰
        } else {
            return "home"; // 晚上在家
        }
    }

    /**
     * 获取噪音级别（模拟实现）
     * 实际使用中可以通过麦克风采样获取
     */
    public float getNoiseLevel() {
        // 模拟噪音级别: 30-80 dB
        String location = getLocationCategory();

        switch (location) {
            case "home":
                return 30 + (float) (Math.random() * 20); // 30-50 dB
            case "company":
                return 40 + (float) (Math.random() * 20); // 40-60 dB
            case "commute":
                return 50 + (float) (Math.random() * 25); // 50-75 dB
            case "school":
                return 35 + (float) (Math.random() * 25); // 35-60 dB
            default:
                return 40 + (float) (Math.random() * 30); // 40-70 dB
        }
    }

    /**
     * 获取当前时段标签
     */
    public String getTimeOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);

        if (hour >= 5 && hour < 12) {
            return "morning";
        } else if (hour >= 12 && hour < 14) {
            return "noon";
        } else if (hour >= 14 && hour < 18) {
            return "afternoon";
        } else if (hour >= 18 && hour < 22) {
            return "evening";
        } else {
            return "night";
        }
    }
}
