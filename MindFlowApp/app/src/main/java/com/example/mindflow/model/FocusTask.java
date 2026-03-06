package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.HashSet;
import java.util.Set;

/**
 * 专注任务 - 番茄todo风格
 * 每个任务有独立的白名单和设置
 */
@Entity(tableName = "focus_tasks")
public class FocusTask {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String name;                    // 任务名称
    public String description;             // 任务描述
    public int focusDurationMinutes = 25;  // 专注时长（分钟）
    public int restDurationMinutes = 5;    // 休息时长（分钟）
    public int targetPomodoros = 4;        // 目标番茄数
    public int completedPomodoros = 0;     // 已完成番茄数
    
    public boolean studyModeEnabled = true;   // 深度专注模式
    public boolean strictModeEnabled = false; // 严格模式（不能提前结束）
    public boolean aiModeEnabled = true;      // AI模式（智能检测分心）
    
    public String whitelistApps = "";      // 白名单App（逗号分隔的包名）
    
    public long totalFocusTimeMs = 0;      // 累计专注时间
    public long createdAt;                 // 创建时间
    public long lastFocusAt;               // 上次专注时间
    
    public int priority = 0;               // 优先级 0-普通 1-重要 2-紧急
    public boolean isCompleted = false;    // 是否已完成
    public boolean isArchived = false;     // 是否已归档
    
    public FocusTask() {
        this.createdAt = System.currentTimeMillis();
    }
    
    @Ignore
    public FocusTask(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
    }
    
    public Set<String> getWhitelistSet() {
        Set<String> set = new HashSet<>();
        if (whitelistApps != null && !whitelistApps.isEmpty()) {
            String[] apps = whitelistApps.split(",");
            for (String app : apps) {
                if (!app.trim().isEmpty()) {
                    set.add(app.trim());
                }
            }
        }
        return set;
    }
    
    public void setWhitelistSet(Set<String> apps) {
        StringBuilder sb = new StringBuilder();
        for (String app : apps) {
            if (sb.length() > 0) sb.append(",");
            sb.append(app);
        }
        this.whitelistApps = sb.toString();
    }
    
    public void addToWhitelist(String packageName) {
        Set<String> set = getWhitelistSet();
        set.add(packageName);
        setWhitelistSet(set);
    }
    
    public void removeFromWhitelist(String packageName) {
        Set<String> set = getWhitelistSet();
        set.remove(packageName);
        setWhitelistSet(set);
    }
}
