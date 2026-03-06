package com.example.mindflow.ui.dashboard;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.database.dao.FocusSessionDao;
import com.example.mindflow.database.dao.InterventionDao;
import com.example.mindflow.database.dao.LabelWindowDao;
import com.example.mindflow.database.dao.SensorDataDao;

import java.util.Calendar;
import java.util.concurrent.Executors;

/**
 * Dashboard ViewModel
 * 管理仪表盘页面的数据和业务逻辑
 */
public class DashboardViewModel extends AndroidViewModel {

    private final FocusSessionDao sessionDao;
    private final LabelWindowDao labelDao;
    private final SensorDataDao sensorDao;
    private final InterventionDao interventionDao;

    // LiveData for UI
    private final MutableLiveData<String> cognitiveState = new MutableLiveData<>("深度专注");
    private final MutableLiveData<Float> interruptibility = new MutableLiveData<>(0.2f);
    private final MutableLiveData<Integer> focusMinutesToday = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> sessionCountToday = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> interventionCountToday = new MutableLiveData<>(0);
    private final MutableLiveData<Float> heartRate = new MutableLiveData<>(72f);
    private final MutableLiveData<Integer> steps = new MutableLiveData<>(0);
    private final MutableLiveData<String> location = new MutableLiveData<>("未知");

    public DashboardViewModel(@NonNull Application application) {
        super(application);

        MindFlowDatabase db = MindFlowDatabase.getInstance(application);
        sessionDao = db.focusSessionDao();
        labelDao = db.labelWindowDao();
        sensorDao = db.sensorDataDao();
        interventionDao = db.interventionDao();

        // 加载今日数据
        loadTodayData();
        loadHistory();
    }

    private void loadTodayData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long todayStart = getTodayStartTimestamp();

            // 加载专注统计
            int minutes = sessionDao.getTotalFocusMinutesSince(todayStart);
            int sessions = sessionDao.getSessionCountSince(todayStart);
            int interventions = interventionDao.getReminderCountSince(todayStart);

            focusMinutesToday.postValue(minutes);
            sessionCountToday.postValue(sessions);
            interventionCountToday.postValue(interventions);

            // 加载最新传感器数据
            var latestSensor = sensorDao.getLatestData();
            if (latestSensor != null) {
                heartRate.postValue(latestSensor.hr);
                steps.postValue(latestSensor.steps);
                location.postValue(latestSensor.locCategory);
            }

            // 加载最新认知状态
            var latestLabel = labelDao.getLatestLabelSync();
            if (latestLabel != null) {
                cognitiveState.postValue(latestLabel.cognitiveState);
                interruptibility.postValue(latestLabel.interruptibilityScore);
            }
        });
    }

    private long getTodayStartTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public void refreshData() {
        loadTodayData();
        loadHistory();
    }

    // Getters for LiveData
    public LiveData<String> getCognitiveState() {
        return cognitiveState;
    }

    public LiveData<Float> getInterruptibility() {
        return interruptibility;
    }

    public LiveData<Integer> getFocusMinutesToday() {
        return focusMinutesToday;
    }

    public LiveData<Integer> getSessionCountToday() {
        return sessionCountToday;
    }

    public LiveData<Integer> getInterventionCountToday() {
        return interventionCountToday;
    }

    public LiveData<Float> getHeartRate() {
        return heartRate;
    }

    public LiveData<Integer> getSteps() {
        return steps;
    }

    public LiveData<String> getLocation() {
        return location;
    }

    public LiveData<java.util.List<com.example.mindflow.model.LabelWindow>> getHistoryData() {
        return historyData;
    }

    private final MutableLiveData<java.util.List<com.example.mindflow.model.LabelWindow>> historyData = new MutableLiveData<>();

    private void loadHistory() {
        Executors.newSingleThreadExecutor().execute(() -> {
            // Last 1 hour
            long startTime = System.currentTimeMillis() - 3600 * 1000;
            java.util.List<com.example.mindflow.model.LabelWindow> list = labelDao.getLabelsSince(startTime);
            historyData.postValue(list);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
