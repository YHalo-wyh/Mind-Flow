package com.example.mindflow.ui.report;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.database.dao.FocusSessionDao;
import com.example.mindflow.database.dao.InterventionDao;
import com.example.mindflow.database.dao.LabelWindowDao;

import java.util.Calendar;
import java.util.concurrent.Executors;

/**
 * Report ViewModel
 * 管理报告页面的数据加载和统计
 */
public class ReportViewModel extends AndroidViewModel {

    public enum Period {
        TODAY, WEEK, MONTH
    }

    public static class CognitiveDistribution {
        public float deepFocus;
        public float lightFocus;
        public float leisure;
        public float highStress;
        public float relaxed;
    }

    private final FocusSessionDao sessionDao;
    private final InterventionDao interventionDao;
    private final LabelWindowDao labelDao;

    private final MutableLiveData<Integer> totalMinutes = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> totalInterventions = new MutableLiveData<>(0);
    private final MutableLiveData<Float> positiveFeedbackRate = new MutableLiveData<>(0f);
    private final MutableLiveData<CognitiveDistribution> cognitiveDistribution = new MutableLiveData<>();
    private final MutableLiveData<String> distractionHistory = new MutableLiveData<>("");

    public ReportViewModel(@NonNull Application application) {
        super(application);

        MindFlowDatabase db = MindFlowDatabase.getInstance(application);
        sessionDao = db.focusSessionDao();
        interventionDao = db.interventionDao();
        labelDao = db.labelWindowDao();
    }

    public void loadData(Period period) {
        long startTime = getStartTime(period);

        Executors.newSingleThreadExecutor().execute(() -> {
            // 加载专注时长（数据库 + 当前会话）
            int dbMinutes = sessionDao.getTotalFocusMinutesSince(startTime);
            
            // 从 SharedPreferences 获取当前会话的实时数据
            android.content.SharedPreferences prefs = getApplication()
                .getSharedPreferences("MindFlowPrefs", android.content.Context.MODE_PRIVATE);
            int currentSessionMinutes = prefs.getInt("current_session_minutes", 0);
            int currentDistractions = prefs.getInt("current_distractions", 0);
            String currentHistory = prefs.getString("current_distraction_history", "");
            
            totalMinutes.postValue(dbMinutes + currentSessionMinutes);

            // 加载干预统计（数据库 + 当前会话）
            int dbReminders = interventionDao.getReminderCountSince(startTime);
            totalInterventions.postValue(dbReminders);
            
            // 更新分心历史
            distractionHistory.postValue(currentHistory);

            // 计算专注率
            int totalMins = dbMinutes + currentSessionMinutes;
            int totalDist = dbReminders;
            if (totalMins > 0) {
                float rate = Math.max(0, (100f - totalDist * 5f) / 100f);
                positiveFeedbackRate.postValue(rate);
            } else {
                positiveFeedbackRate.postValue(0f);
            }

            // 加载认知状态分布
            java.util.List<LabelWindowDao.CognitiveStateCount> stateCounts = labelDao
                    .getCognitiveStateDistribution(startTime);
            CognitiveDistribution distribution = new CognitiveDistribution();

            int total = 0;
            for (LabelWindowDao.CognitiveStateCount count : stateCounts) {
                total += count.count;
            }

            if (total > 0) {
                for (LabelWindowDao.CognitiveStateCount count : stateCounts) {
                    float percent = (float) count.count / total;
                    switch (count.cognitiveState) {
                        case "深度专注":
                            distribution.deepFocus = percent;
                            break;
                        case "轻度专注":
                            distribution.lightFocus = percent;
                            break;
                        case "休闲刷屏":
                            distribution.leisure = percent;
                            break;
                        case "高压忙乱":
                            distribution.highStress = percent;
                            break;
                        case "放松休息":
                            distribution.relaxed = percent;
                            break;
                    }
                }
            }

            cognitiveDistribution.postValue(distribution);
        });
    }

    private long getStartTime(Period period) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        switch (period) {
            case WEEK:
                // 本周开始（周一 00:00）
                calendar.setFirstDayOfWeek(Calendar.MONDAY);
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                break;
            case MONTH:
                // 本月开始（1号 00:00）
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case TODAY:
            default:
                // 今天开始
                break;
        }

        return calendar.getTimeInMillis();
    }

    // Getters
    public LiveData<Integer> getTotalMinutes() {
        return totalMinutes;
    }

    public LiveData<Integer> getTotalInterventions() {
        return totalInterventions;
    }

    public LiveData<Float> getPositiveFeedbackRate() {
        return positiveFeedbackRate;
    }

    public LiveData<CognitiveDistribution> getCognitiveStateDistribution() {
        return cognitiveDistribution;
    }
    
    public LiveData<String> getDistractionHistory() {
        return distractionHistory;
    }
    
    /**
     * 设置分心历史（从 SharedPreferences 或其他来源加载）
     */
    public void setDistractionHistory(String history) {
        distractionHistory.postValue(history);
    }
}
