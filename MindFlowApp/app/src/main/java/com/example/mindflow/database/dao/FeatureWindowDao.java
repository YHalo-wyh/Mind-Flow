package com.example.mindflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindflow.model.FeatureWindow;

import java.util.List;

@Dao
public interface FeatureWindowDao {
    
    @Insert
    long insert(FeatureWindow feature);
    
    @Query("SELECT * FROM feature_windows ORDER BY windowStart DESC LIMIT 1")
    FeatureWindow getLatestWindow();
    
    @Query("SELECT * FROM feature_windows WHERE windowStart >= :startTime ORDER BY windowStart DESC")
    List<FeatureWindow> getWindowsSince(long startTime);
    
    @Query("SELECT AVG(touchCount) FROM feature_windows WHERE windowStart >= :startTime")
    float getAverageTouchCountSince(long startTime);
    
    @Query("SELECT AVG(appSwitchCount) FROM feature_windows WHERE windowStart >= :startTime")
    float getAverageAppSwitchSince(long startTime);
    
    @Query("SELECT SUM(screenOnMs) FROM feature_windows WHERE windowStart >= :startTime")
    long getTotalScreenOnTimeSince(long startTime);

    // 获取所有特征窗口，按时间倒序排列
    @androidx.room.Query("SELECT * FROM feature_windows ORDER BY windowStart DESC")
    java.util.List<com.example.mindflow.model.FeatureWindow> getAllWindows();
}
