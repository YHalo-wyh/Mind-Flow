package com.example.mindflow.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 传感器数据实体
 * 对应 sensors.csv 表结构（窗口级）
 */
@Entity(tableName = "sensor_data")
public class SensorData {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String windowId;
    public long timestamp;            // 采集时间戳
    
    // 生理指标
    public float hr;                  // 心率
    public float hrv;                 // 心率变异性(RMSSD)
    public int steps;                 // 步数
    public String activityLevel;      // sedentary, light, moderate
    
    // 环境指标
    public String locCategory;        // home, school, company, commute, other
    public String networkType;        // wifi, mobile, none
    public float noiseLevel;          // 噪音级别(dB)
    
    public SensorData() {}
}
