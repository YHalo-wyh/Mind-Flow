package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 专注记录 - 每次番茄钟完成后保存
 */
@Entity(tableName = "focus_records")
public class FocusRecord {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long taskId;                    // 关联的任务ID
    public String taskName;                // 任务名称（冗余存储）
    
    public long startTime;                 // 开始时间
    public long endTime;                   // 结束时间
    public long durationMs;                // 实际专注时长
    public long plannedDurationMs;         // 计划时长
    
    public boolean isCompleted;            // 是否完成（未中途放弃）
    public String abandonReason;           // 放弃原因（如果未完成）
    
    public int distractionCount;           // 分心次数
    public String distractionApps;         // 分心时打开的App列表
    
    public boolean aiDetected;             // 是否通过AI检测
    public String aiAnalysis;              // AI分析结果
    
    // 日期字段用于统计
    public int year;
    public int month;
    public int dayOfMonth;
    public int dayOfWeek;
    public int hourOfDay;
    
    public FocusRecord() {
        this.startTime = System.currentTimeMillis();
    }
    
    public void complete() {
        this.endTime = System.currentTimeMillis();
        this.durationMs = this.endTime - this.startTime;
        this.isCompleted = true;
        fillDateFields();
    }
    
    public void abandon(String reason) {
        this.endTime = System.currentTimeMillis();
        this.durationMs = this.endTime - this.startTime;
        this.isCompleted = false;
        this.abandonReason = reason;
        fillDateFields();
    }
    
    private void fillDateFields() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(this.startTime);
        this.year = cal.get(java.util.Calendar.YEAR);
        this.month = cal.get(java.util.Calendar.MONTH) + 1;
        this.dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH);
        this.dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        this.hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY);
    }
}
