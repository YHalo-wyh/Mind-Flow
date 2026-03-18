package com.example.mindflow.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mindflow.model.FocusSession;

import java.util.List;

@Dao
public interface FocusSessionDao {

    @Insert
    long insert(FocusSession session);

    @Update
    void update(FocusSession session);

    @Delete
    void delete(FocusSession session);

    @Query("SELECT * FROM focus_sessions ORDER BY startTs DESC")
    LiveData<List<FocusSession>> getAllSessions();

    @Query("SELECT * FROM focus_sessions WHERE isActive = 1 LIMIT 1")
    LiveData<FocusSession> getActiveSession();

    @Query("SELECT * FROM focus_sessions WHERE isActive = 1 LIMIT 1")
    FocusSession getActiveSessionSync();

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    FocusSession getSessionById(long id);

    @Query("SELECT * FROM focus_sessions WHERE startTs >= :startTime AND startTs <= :endTime")
    List<FocusSession> getSessionsInRange(long startTime, long endTime);

    @Query("SELECT COALESCE(SUM(actualMin), 0) FROM focus_sessions WHERE startTs >= :startTime")
    int getTotalFocusMinutesSince(long startTime);

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startTs >= :startTime")
    int getSessionCountSince(long startTime);
    
    @Query("SELECT * FROM focus_sessions WHERE startTs >= :startTime ORDER BY startTs DESC")
    List<FocusSession> getSessionsAfterTime(long startTime);
}
