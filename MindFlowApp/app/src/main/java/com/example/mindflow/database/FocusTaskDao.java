package com.example.mindflow.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mindflow.model.FocusTask;

import java.util.List;

@Dao
public interface FocusTaskDao {
    
    @Insert
    long insert(FocusTask task);
    
    @Update
    void update(FocusTask task);
    
    @Delete
    void delete(FocusTask task);
    
    @Query("SELECT * FROM focus_tasks WHERE isArchived = 0 ORDER BY priority DESC, createdAt DESC")
    LiveData<List<FocusTask>> getAllActiveTasks();
    
    @Query("SELECT * FROM focus_tasks WHERE isArchived = 0 ORDER BY priority DESC, createdAt DESC")
    List<FocusTask> getAllActiveTasksSync();
    
    @Query("SELECT * FROM focus_tasks WHERE id = :id")
    FocusTask getTaskById(long id);
    
    @Query("SELECT * FROM focus_tasks WHERE isCompleted = 0 AND isArchived = 0 ORDER BY priority DESC LIMIT 1")
    FocusTask getNextTask();
    
    @Query("UPDATE focus_tasks SET completedPomodoros = completedPomodoros + 1, totalFocusTimeMs = totalFocusTimeMs + :focusTimeMs, lastFocusAt = :timestamp WHERE id = :taskId")
    void recordPomodoroComplete(long taskId, long focusTimeMs, long timestamp);
    
    @Query("UPDATE focus_tasks SET isCompleted = 1 WHERE id = :taskId")
    void markTaskComplete(long taskId);
    
    @Query("UPDATE focus_tasks SET isArchived = 1 WHERE id = :taskId")
    void archiveTask(long taskId);
}
