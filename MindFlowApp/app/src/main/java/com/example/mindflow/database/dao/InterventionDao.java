package com.example.mindflow.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindflow.model.Intervention;

import java.util.List;

@Dao
public interface InterventionDao {
    
    @Insert
    long insert(Intervention intervention);
    
    @Query("SELECT * FROM interventions ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<Intervention>> getRecentInterventions(int limit);
    
    @Query("SELECT * FROM interventions WHERE sessionId = :sessionId")
    List<Intervention> getInterventionsBySession(String sessionId);
    
    @Query("SELECT COUNT(*) FROM interventions WHERE timestamp >= :startTime")
    int getInterventionCountSince(long startTime);

    @Query("SELECT COUNT(*) FROM interventions WHERE timestamp >= :startTime AND type = 'distraction'")
    int getDistractionCountSince(long startTime);

    @Query("SELECT COUNT(*) FROM interventions WHERE timestamp >= :startTime AND (type = 'warning' OR type = 'lock')")
    int getReminderCountSince(long startTime);
    
    @Query("SELECT type, COUNT(*) as count FROM interventions WHERE timestamp >= :startTime GROUP BY type")
    List<InterventionTypeCount> getInterventionTypeDistribution(long startTime);
    
    class InterventionTypeCount {
        public String type;
        public int count;
    }
}
