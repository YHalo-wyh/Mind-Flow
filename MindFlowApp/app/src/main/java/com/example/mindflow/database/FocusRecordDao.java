package com.example.mindflow.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindflow.model.FocusRecord;

import java.util.List;

@Dao
public interface FocusRecordDao {
    
    @Insert
    long insert(FocusRecord record);
    
    @Query("SELECT * FROM focus_records ORDER BY startTime DESC")
    LiveData<List<FocusRecord>> getAllRecords();
    
    @Query("SELECT * FROM focus_records ORDER BY startTime DESC")
    List<FocusRecord> getAllRecordsSync();
    
    @Query("SELECT * FROM focus_records WHERE taskId = :taskId ORDER BY startTime DESC")
    List<FocusRecord> getRecordsByTask(long taskId);
    
    // 今日统计
    @Query("SELECT SUM(durationMs) FROM focus_records WHERE year = :year AND month = :month AND dayOfMonth = :day AND isCompleted = 1")
    Long getTodayTotalFocusTime(int year, int month, int day);
    
    @Query("SELECT COUNT(*) FROM focus_records WHERE year = :year AND month = :month AND dayOfMonth = :day AND isCompleted = 1")
    int getTodayPomodoroCount(int year, int month, int day);
    
    // 本周统计
    @Query("SELECT SUM(durationMs) FROM focus_records WHERE startTime >= :weekStartMs AND isCompleted = 1")
    Long getWeekTotalFocusTime(long weekStartMs);
    
    // 本月统计
    @Query("SELECT SUM(durationMs) FROM focus_records WHERE year = :year AND month = :month AND isCompleted = 1")
    Long getMonthTotalFocusTime(int year, int month);
    
    // 按小时分布（用于热力图）
    @Query("SELECT hourOfDay, COUNT(*) as count FROM focus_records WHERE year = :year AND month = :month AND isCompleted = 1 GROUP BY hourOfDay")
    List<HourlyCount> getHourlyDistribution(int year, int month);
    
    // 按天分布
    @Query("SELECT dayOfMonth, SUM(durationMs) as totalMs FROM focus_records WHERE year = :year AND month = :month AND isCompleted = 1 GROUP BY dayOfMonth")
    List<DailyTotal> getDailyTotals(int year, int month);
    
    class HourlyCount {
        public int hourOfDay;
        public int count;
    }
    
    class DailyTotal {
        public int dayOfMonth;
        public long totalMs;
    }
}
