package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_events")
public class AppEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startTs;
    public String packageName;
    public String eventType;
    public String details; // 👈 对应 content_info

    // 辅助分析字段
    public String sessionId;
    public String appCategory;
    public long endTs;
    public long durationMs;
    public boolean inFocusSession;
}