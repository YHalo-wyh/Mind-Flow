package com.example.mindflow.service;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.database.dao.SensorDataDao;
import com.example.mindflow.model.SensorData;

import java.util.concurrent.Executors;

/**
 * 传感器数据采集服务
 * 采集步数、心率(如可用)等传感器数据
 */
public class SensorCollector implements SensorEventListener {

    private final Context context;
    private final SensorManager sensorManager;
    private final SensorDataDao sensorDataDao;

    private Sensor stepSensor;
    private Sensor heartRateSensor;

    private int currentSteps = 0;
    private float currentHeartRate = 0;
    private String currentWindowId;

    public SensorCollector(Context context) {
        this.context = context;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        MindFlowDatabase db = MindFlowDatabase.getInstance(context);
        this.sensorDataDao = db.sensorDataDao();

        // 初始化传感器
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        }
    }

    /**
     * 开始采集传感器数据
     */
    public void startCollecting() {
        if (sensorManager == null)
            return;

        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * 停止采集传感器数据
     */
    public void stopCollecting() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    /**
     * 设置当前窗口ID
     */
    public void setCurrentWindowId(String windowId) {
        this.currentWindowId = windowId;
    }

    /**
     * 保存当前传感器数据到数据库
     */
    public void saveSensorData(String locCategory, String networkType, float noiseLevel) {
        SensorData data = new SensorData();
        data.windowId = currentWindowId;
        data.timestamp = System.currentTimeMillis();
        data.hr = currentHeartRate > 0 ? currentHeartRate : generateSimulatedHeartRate();
        data.hrv = generateSimulatedHRV();
        data.steps = currentSteps;
        data.activityLevel = determineActivityLevel(currentSteps);
        data.locCategory = locCategory;
        data.networkType = networkType;
        data.noiseLevel = noiseLevel;

        Executors.newSingleThreadExecutor().execute(() -> {
            sensorDataDao.insert(data);
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            currentSteps = (int) event.values[0];
        } else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 精度变化处理
    }

    /**
     * 生成模拟心率数据（用于无传感器设备）
     */
    private float generateSimulatedHeartRate() {
        // 正常心率范围: 60-100 BPM
        return 60 + (float) (Math.random() * 40);
    }

    /**
     * 生成模拟HRV数据
     */
    private float generateSimulatedHRV() {
        // 正常HRV RMSSD范围: 20-100 ms
        return 20 + (float) (Math.random() * 80);
    }

    /**
     * 根据步数判断活动级别
     */
    private String determineActivityLevel(int steps) {
        if (steps < 100) {
            return "sedentary";
        } else if (steps < 500) {
            return "light";
        } else {
            return "moderate";
        }
    }

    public int getCurrentSteps() {
        return currentSteps;
    }

    public float getCurrentHeartRate() {
        return currentHeartRate > 0 ? currentHeartRate : generateSimulatedHeartRate();
    }
}
