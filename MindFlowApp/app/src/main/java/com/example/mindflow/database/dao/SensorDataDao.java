package com.example.mindflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindflow.model.SensorData;

import java.util.List;

@Dao
public interface SensorDataDao {

    @Insert
    long insert(SensorData data);

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT 1")
    SensorData getLatestData();

    @Query("SELECT * FROM sensor_data WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<SensorData> getDataSince(long startTime);

    @Query("SELECT AVG(hr) FROM sensor_data WHERE timestamp >= :startTime")
    float getAverageHeartRateSince(long startTime);

    @Query("SELECT AVG(hrv) FROM sensor_data WHERE timestamp >= :startTime")
    float getAverageHRVSince(long startTime);

    @Query("SELECT COALESCE(SUM(steps), 0) FROM sensor_data WHERE timestamp >= :startTime")
    int getTotalStepsSince(long startTime);
}
