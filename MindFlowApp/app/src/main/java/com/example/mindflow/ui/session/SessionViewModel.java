package com.example.mindflow.ui.session;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.database.dao.FocusSessionDao;
import com.example.mindflow.model.FocusSession;

import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Session ViewModel
 * 管理专注会话的状态和业务逻辑
 */
public class SessionViewModel extends AndroidViewModel {

    private final FocusSessionDao sessionDao;

    private FocusSession currentSession;

    private final MutableLiveData<Integer> distractionCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> interventionCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> focusScore = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isSessionActive = new MutableLiveData<>(false);

    public SessionViewModel(@NonNull Application application) {
        super(application);
        MindFlowDatabase db = MindFlowDatabase.getInstance(application);
        sessionDao = db.focusSessionDao();
    }

    /**
     * 开始新的专注会话
     */
    public void startSession(int plannedMinutes, String goalText) {
        currentSession = new FocusSession();
        currentSession.sessionId = UUID.randomUUID().toString();
        currentSession.sessionType = "focus";
        currentSession.goalText = goalText;
        currentSession.plannedMin = plannedMinutes;
        currentSession.startTs = System.currentTimeMillis();
        currentSession.isActive = true;
        currentSession.distractionCount = 0;
        currentSession.interventionCount = 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            long id = sessionDao.insert(currentSession);
            currentSession.id = id;
        });

        distractionCount.setValue(0);
        interventionCount.setValue(0);
        focusScore.setValue(null);
        isSessionActive.setValue(true);
    }

    /**
     * 结束当前专注会话
     */
    public void endSession(int actualMinutes) {
        if (currentSession != null) {
            currentSession.endTs = System.currentTimeMillis();
            currentSession.actualMin = actualMinutes;
            currentSession.isActive = false;

            // 计算简单的专注评分 (1-5)
            int score = calculateFocusScore(actualMinutes, currentSession.plannedMin,
                    distractionCount.getValue());
            currentSession.selfFocusScore = score;
            focusScore.setValue(score);

            Executors.newSingleThreadExecutor().execute(() -> {
                sessionDao.update(currentSession);
            });

            isSessionActive.setValue(false);
            currentSession = null;
        }
    }

    /**
     * 记录一次分心事件
     */
    public void recordDistraction() {
        Integer count = distractionCount.getValue();
        distractionCount.setValue(count != null ? count + 1 : 1);

        if (currentSession != null) {
            currentSession.distractionCount++;
        }
    }

    /**
     * 记录一次干预事件
     */
    public void recordIntervention() {
        Integer count = interventionCount.getValue();
        interventionCount.setValue(count != null ? count + 1 : 1);

        if (currentSession != null) {
            currentSession.interventionCount++;
        }
    }

    private int calculateFocusScore(int actualMin, int plannedMin, Integer distractions) {
        // 简单评分算法
        float completionRate = (float) actualMin / plannedMin;
        int distractionPenalty = distractions != null ? distractions : 0;

        float score = 5 * completionRate - distractionPenalty * 0.5f;
        score = Math.max(1, Math.min(5, score));

        return Math.round(score);
    }

    // Getters
    public LiveData<Integer> getDistractionCount() {
        return distractionCount;
    }

    public LiveData<Integer> getInterventionCount() {
        return interventionCount;
    }

    public LiveData<Integer> getFocusScore() {
        return focusScore;
    }

    public LiveData<Boolean> getIsSessionActive() {
        return isSessionActive;
    }
}
