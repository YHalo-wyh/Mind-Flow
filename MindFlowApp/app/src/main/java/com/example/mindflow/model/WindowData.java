package com.example.mindflow.model;

import com.google.gson.annotations.SerializedName;

public class WindowData {
    // --- 7个必填类别特征 (Categorical) ---
    @SerializedName("session_type")
    public String session_type = "task";
    @SerializedName("major_app_category")
    public String major_app_category = "work";
    @SerializedName("activity_level")
    public String activity_level = "sedentary";
    @SerializedName("loc_category")
    public String loc_category = "home";
    @SerializedName("network_type")
    public String network_type = "wifi";
    @SerializedName("noise_level")
    public String noise_level = "low";
    @SerializedName("timezone")
    public String timezone = "Asia/Shanghai";

    // --- 20个必填数值特征 (Numeric - 必须对齐名且有初值) ---
    @SerializedName("hour_of_day")
    public int hour_of_day = 12;
    @SerializedName("day_of_week")
    public int day_of_week = 1;
    @SerializedName("in_focus_session")
    public int in_focus_session = 1;
    @SerializedName("touch_count")
    public int touch_count = 0;
    @SerializedName("scroll_count")
    public int scroll_count = 0;
    @SerializedName("scroll_speed")
    public double scroll_speed = 0.0;
    @SerializedName("key_stroke_count")
    public int key_stroke_count = 0;
    @SerializedName("touch_freq_var")
    public double touch_freq_var = 0.0;
    @SerializedName("app_switch_count")
    public int app_switch_count = 0;
    @SerializedName("screen_on_ms")
    public long screen_on_ms = 60000;
    @SerializedName("notif_received_count")
    public int notif_received_count = 0;
    @SerializedName("notif_clicked_count")
    public int notif_clicked_count = 0;
    @SerializedName("notif_dismissed_count")
    public int notif_dismissed_count = 0;
    @SerializedName("notif_blocked_in_focus_count")
    public int notif_blocked_in_focus_count = 0;
    @SerializedName("notif_work_count")
    public int notif_work_count = 0;
    @SerializedName("notif_social_count")
    public int notif_social_count = 0;
    @SerializedName("notif_learning_count")
    public int notif_learning_count = 0;
    @SerializedName("hr_mean")
    public double hr_mean = 75.0;
    @SerializedName("hrv_rmssd")
    public double hrv_rmssd = 40.0;
    @SerializedName("steps")
    public int steps = 0;

    // --- 排序辅助 ---
    @SerializedName("window_start")
    public long window_start = System.currentTimeMillis();

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("session_type", session_type);
        map.put("major_app_category", major_app_category);
        map.put("activity_level", activity_level);
        map.put("loc_category", loc_category);
        map.put("network_type", network_type);
        map.put("noise_level", noise_level);
        map.put("timezone", timezone);

        map.put("hour_of_day", hour_of_day);
        map.put("day_of_week", day_of_week);
        map.put("in_focus_session", in_focus_session);
        map.put("touch_count", touch_count);
        map.put("scroll_count", scroll_count);
        map.put("scroll_speed", scroll_speed);
        map.put("key_stroke_count", key_stroke_count);
        map.put("touch_freq_var", touch_freq_var);
        map.put("app_switch_count", app_switch_count);
        map.put("screen_on_ms", screen_on_ms);
        map.put("notif_received_count", notif_received_count);
        map.put("notif_clicked_count", notif_clicked_count);
        map.put("notif_dismissed_count", notif_dismissed_count);
        map.put("notif_blocked_in_focus_count", notif_blocked_in_focus_count);
        map.put("notif_work_count", notif_work_count);
        map.put("notif_social_count", notif_social_count);
        map.put("notif_learning_count", notif_learning_count);
        map.put("hr_mean", hr_mean);
        map.put("hrv_rmssd", hrv_rmssd);
        map.put("steps", steps);
        return map;
    }
}