package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 干预事件实体
 * 对应 interventions.csv 表结构
 */
@Entity(tableName = "interventions")
public class Intervention {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String sessionId;           // 关联会话ID
    public long timestamp;             // 触发时间戳
    
    // 干预类型: hint, card, lock, breath, todo
    public String type;
    
    // 触发原因: social_distraction, high_hr, high_stress_pattern, rule_based
    public String triggerReason;
    
    // 干预前状态
    public String stateBefore;
    public float interruptibilityBefore; // 干预前可打断性(0-1)
    
    // 干预效果
    public float deltaDistraction;     // 分心变化量
    public float deltaFocusTime;       // 专注时长变化
    
    // 用户反馈
    public String userFeedback;        // positive, negative, neutral, dismissed
    
    public Intervention() {}
}
