package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 窗口标签实体
 * 对应 labels_windows.csv 表结构
 * 监督学习目标标签
 */
@Entity(tableName = "label_windows")
public class LabelWindow {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String windowId;
    public long timestamp;
    
    // 专注与可打断性
    public int focusLabel;             // 专注度标签(1-5)
    public int interruptibilityLabel;  // 可打断性标签(1-5)
    public float interruptibilityScore; // 可打断性分数(0-1)
    
    // 压力与情绪
    public String stressLevel;         // low, medium, high
    public int stressScoreNum;         // 压力分数(数值编码)
    public String moodLevel;           // bad, neutral, good
    public int moodScoreNum;           // 情绪分数(数值编码)
    
    // 认知状态(5类)
    // 深度专注 / 轻度专注 / 休闲刷屏 / 高压忙乱 / 放松休息
    public String cognitiveState;
    
    public LabelWindow() {}
}
