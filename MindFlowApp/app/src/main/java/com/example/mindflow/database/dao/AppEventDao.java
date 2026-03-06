package com.example.mindflow.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindflow.model.AppEvent;

import java.util.List;

@Dao
public interface AppEventDao {

    @Insert
    long insert(AppEvent event);

    @Insert
    void insertAll(List<AppEvent> events);

    @Query("SELECT * FROM app_events ORDER BY startTs DESC LIMIT :limit")
    LiveData<List<AppEvent>> getRecentEvents(int limit);

    @Query("SELECT * FROM app_events WHERE startTs >= :startTime AND startTs <= :endTime")
    List<AppEvent> getEventsInRange(long startTime, long endTime);

    @Query("SELECT DISTINCT appCategory FROM app_events WHERE startTs >= :startTime")
    List<String> getAppCategoriesSince(long startTime);

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM app_events WHERE appCategory = :category AND startTs >= :startTime")
    long getTotalDurationByCategory(String category, long startTime);

    @Query("SELECT COUNT(*) FROM app_events WHERE eventType = 'foreground_app' AND startTs >= :startTime")
    int getAppSwitchCountSince(long startTime);

    // 获取所有原始 App 事件，按时间倒序排列
    @androidx.room.Query("SELECT * FROM app_events ORDER BY startTs DESC")
    java.util.List<com.example.mindflow.model.AppEvent> getAllEvents();
}
