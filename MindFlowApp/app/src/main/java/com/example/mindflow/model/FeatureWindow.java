package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 窗口特征实体
 * 对应 features_windows.csv 表结构
 * 5分钟窗口聚合的多模态特征
 */
@Entity(tableName = "feature_windows")
public class FeatureWindow {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String windowId;
    public long windowStart;          // 窗口开始时间戳
    public long windowEnd;            // 窗口结束时间戳
    
    // 行为交互特征
    public int touchCount;            // 触摸次数
    public int scrollCount;           // 滚动次数
    public float scrollSpeed;         // 滚动速度
    public int keyStrokeCount;        // 按键次数
    public float touchFreqVar;        // 触摸频率变异
    public int appSwitchCount;        // App切换次数
    
    // 屏幕特征
    public long screenOnMs;           // 屏幕亮起时长(毫秒)
    
    // 通知统计
    public int notifReceivedCount;    // 收到通知数
    public int notifClickedCount;     // 点击通知数
    public int notifDismissedCount;   // 清除通知数
    public int notifBlockedCount;     // 拦截通知数
    
    // 生理环境(窗口均值)
    public float hrMean;              // 心率均值
    public float hrvRmssd;            // HRV RMSSD
    public int stepCount;             // 步数
    public String activityLevel;       // 活动级别
    public String locCategory;         // 位置类别
    public String networkType;         // 网络类型
    public float noiseLevel;           // 噪音级别
    
    // 会话关联
    public boolean inFocusSession;
    public String sessionType;
    public String majorAppCategory;    // 主要使用App类别
    
    public FeatureWindow() {}
}
