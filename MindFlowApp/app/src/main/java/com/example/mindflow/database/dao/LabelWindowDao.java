package com.example.mindflow.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindflow.model.LabelWindow;

import java.util.List;

@Dao
public interface LabelWindowDao {
    
    @Insert
    long insert(LabelWindow label);
    
    @Query("SELECT * FROM label_windows ORDER BY timestamp DESC LIMIT 1")
    LiveData<LabelWindow> getLatestLabel();
    
    @Query("SELECT * FROM label_windows ORDER BY timestamp DESC LIMIT 1")
    LabelWindow getLatestLabelSync();
    
    @Query("SELECT * FROM label_windows WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<LabelWindow> getLabelsSince(long startTime);
    
    @Query("SELECT cognitiveState, COUNT(*) as count FROM label_windows WHERE timestamp >= :startTime GROUP BY cognitiveState")
    List<CognitiveStateCount> getCognitiveStateDistribution(long startTime);
    
    @Query("SELECT AVG(focusLabel) FROM label_windows WHERE timestamp >= :startTime")
    float getAverageFocusLabelSince(long startTime);
    
    // 内部类用于聚合查询
    class CognitiveStateCount {
        public String cognitiveState;
        public int count;
    }
}
