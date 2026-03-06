package com.example.mindflow.core;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.model.FocusRecord;
import com.example.mindflow.model.FocusTask;
import com.example.mindflow.service.AppMonitorService;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 番茄专注管理器 - 核心逻辑
 * 模仿番茄todo的深度专注模式+严格模式
 */
public class TomatoFocusManager {
    private static final String TAG = "TomatoFocus";
    private static TomatoFocusManager instance;
    
    private final Context context;
    private final MindFlowDatabase db;
    private final SharedPreferences prefs;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    // 当前状态
    public enum State { IDLE, FOCUSING, RESTING, LOCKED }
    private State currentState = State.IDLE;
    
    // 当前任务
    private FocusTask currentTask;
    private FocusRecord currentRecord;
    
    // 计时
    private long focusStartTime;
    private long remainingMs;
    private ScheduledFuture<?> timerHandle;
    
    // 分心计数
    private int distractionCount = 0;
    private static final int MAX_DISTRACTIONS = 3;  // 触发锁机的阈值
    
    // 回调
    public interface FocusCallback {
        void onTick(long remainingMs, int completedPomodoros);
        void onFocusComplete();
        void onRestStart(long restDurationMs);
        void onRestComplete();
        void onDistraction(String reason);
        void onLocked(String reason);
    }
    private FocusCallback callback;
    
    // 持久化Keys
    private static final String KEY_STATE = "current_state";
    private static final String KEY_TASK_ID = "current_task_id";
    private static final String KEY_TASK_NAME = "current_task_name";
    private static final String KEY_TASK_GOAL = "current_task_goal";
    private static final String KEY_FOCUS_DURATION = "focus_duration_minutes";
    private static final String KEY_REST_DURATION = "rest_duration_minutes";
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_STUDY_MODE = "study_mode_enabled";
    private static final String KEY_STRICT_MODE = "strict_mode_enabled";
    private static final String KEY_FOCUS_START_TIME = "focus_start_time";
    private static final String KEY_REMAINING_MS = "remaining_ms";
    private static final String KEY_DISTRACTION_COUNT = "distraction_count";

    private TomatoFocusManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = MindFlowDatabase.getInstance(context);
        this.prefs = context.getSharedPreferences("TomatoFocus", Context.MODE_PRIVATE);
        
        // 启动时恢复上次的任务状态
        restoreState();
    }
    
    public static synchronized TomatoFocusManager getInstance(Context context) {
        if (instance == null) {
            instance = new TomatoFocusManager(context);
        }
        return instance;
    }
    
    public void setCallback(FocusCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 开始专注 - 番茄todo核心
     */
    public void startFocus(FocusTask task) {
        if (currentState != State.IDLE) {
            Log.w(TAG, "当前状态不允许开始: " + currentState);
            return;
        }
        
        this.currentTask = task;
        this.currentState = State.FOCUSING;
        this.focusStartTime = System.currentTimeMillis();
        this.remainingMs = task.focusDurationMinutes * 60 * 1000L;
        this.distractionCount = 0;
        
        // 创建记录
        currentRecord = new FocusRecord();
        currentRecord.taskId = task.id;
        currentRecord.taskName = task.name;
        currentRecord.plannedDurationMs = remainingMs;
        
        // 启用深度专注模式（锁机拦截）
        if (task.studyModeEnabled) {
            enableStudyMode(task.getWhitelistSet());
        }
        
        // 启动计时器
        startTimer();
        
        // 保存状态到本地
        saveState();
        
        Log.i(TAG, "🍅 开始专注: " + task.name + ", 时长: " + task.focusDurationMinutes + "分钟");
    }
    
    /**
     * 停止专注
     */
    public void stopFocus(boolean completed, String reason) {
        if (currentState != State.FOCUSING) return;
        
        stopTimer();
        disableStudyMode();
        
        if (currentRecord != null) {
            if (completed) {
                currentRecord.complete();
                currentRecord.distractionCount = distractionCount;
                
                // 更新任务统计
                if (currentTask != null) {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.focusTaskDao().recordPomodoroComplete(
                            currentTask.id, 
                            currentRecord.durationMs, 
                            System.currentTimeMillis()
                        );
                        db.focusRecordDao().insert(currentRecord);
                    });
                }
                
                Log.i(TAG, "✅ 专注完成!");
                if (callback != null) callback.onFocusComplete();
                
                // 开始休息
                startRest();
            } else {
                currentRecord.abandon(reason);
                Executors.newSingleThreadExecutor().execute(() -> {
                    db.focusRecordDao().insert(currentRecord);
                });
                
                currentState = State.IDLE;
                clearState();  // 清除保存的状态
                Log.i(TAG, "❌ 专注中断: " + reason);
            }
        }
        
        currentRecord = null;
    }
    
    /**
     * 开始休息
     */
    private void startRest() {
        if (currentTask == null) return;
        
        currentState = State.RESTING;
        remainingMs = currentTask.restDurationMinutes * 60 * 1000L;
        
        if (callback != null) callback.onRestStart(remainingMs);
        
        startTimer();
        Log.i(TAG, "☕ 开始休息: " + currentTask.restDurationMinutes + "分钟");
    }
    
    /**
     * 记录分心
     */
    public void recordDistraction(String app, String reason) {
        if (currentState != State.FOCUSING) return;
        
        distractionCount++;
        Log.w(TAG, "⚠️ 分心 #" + distractionCount + ": " + reason);
        
        if (callback != null) callback.onDistraction(reason);
        
        // 达到阈值触发锁机
        if (distractionCount >= MAX_DISTRACTIONS) {
            triggerLock("分心次数过多");
        }
    }
    
    /**
     * 触发锁机 - 番茄todo核心功能
     * 锁机界面由FocusService通过LockOverlayManager统一管理
     */
    public void triggerLock(String reason) {
        currentState = State.LOCKED;
        
        // 通知AppMonitorService激活锁机状态
        // 实际锁机Overlay由FocusService管理
        AppMonitorService service = AppMonitorService.getInstance();
        if (service != null) {
            service.activateLockScreen();
        }
        
        if (callback != null) callback.onLocked(reason);
        Log.w(TAG, "🔒 触发锁机: " + reason);
    }
    
    /**
     * 锁机结束后恢复
     */
    public void onUnlocked() {
        distractionCount = 0;
        if (currentTask != null && currentState == State.LOCKED) {
            currentState = State.FOCUSING;
            if (currentTask.studyModeEnabled) {
                enableStudyMode(currentTask.getWhitelistSet());
            }
        }
        Log.i(TAG, "🔓 锁机结束，继续专注");
    }
    
    // ==================== 内部方法 ====================
    
    private void startTimer() {
        timerHandle = executor.scheduleAtFixedRate(() -> {
            remainingMs -= 1000;
            
            if (callback != null) {
                int completedPomodoros = currentTask != null ? currentTask.completedPomodoros : 0;
                callback.onTick(remainingMs, completedPomodoros);
            }
            
            if (remainingMs <= 0) {
                if (currentState == State.FOCUSING) {
                    stopFocus(true, null);
                } else if (currentState == State.RESTING) {
                    currentState = State.IDLE;
                    if (callback != null) callback.onRestComplete();
                    Log.i(TAG, "☕ 休息结束");
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private void stopTimer() {
        if (timerHandle != null) {
            timerHandle.cancel(false);
            timerHandle = null;
        }
    }
    
    private void enableStudyMode(Set<String> whitelist) {
        AppMonitorService service = AppMonitorService.getInstance();
        if (service != null) {
            service.enableLockMode(whitelist, 60000L, "专注保护", "", distractionCount, "");
            Log.i(TAG, "📚 专注保护启用，白名单: " + whitelist.size() + " 个App");
        }
    }
    
    private void disableStudyMode() {
        AppMonitorService service = AppMonitorService.getInstance();
        if (service != null) {
            service.disableLockMode();
            Log.i(TAG, "📚 专注保护关闭");
        }
    }
    
    // ==================== 持久化方法 ====================
    
    /**
     * 保存当前状态到SharedPreferences
     */
    private void saveState() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_STATE, currentState.name());
        editor.putLong(KEY_FOCUS_START_TIME, focusStartTime);
        editor.putLong(KEY_REMAINING_MS, remainingMs);
        editor.putInt(KEY_DISTRACTION_COUNT, distractionCount);
        
        if (currentTask != null) {
            editor.putLong(KEY_TASK_ID, currentTask.id);
            editor.putString(KEY_TASK_NAME, currentTask.name);
            editor.putString(KEY_TASK_GOAL, currentTask.name);  // 使用name作为目标
            editor.putInt(KEY_FOCUS_DURATION, currentTask.focusDurationMinutes);
            editor.putInt(KEY_REST_DURATION, currentTask.restDurationMinutes);
            editor.putString(KEY_WHITELIST, currentTask.whitelistApps != null ? currentTask.whitelistApps : "");
            editor.putBoolean(KEY_STUDY_MODE, currentTask.studyModeEnabled);
            editor.putBoolean(KEY_STRICT_MODE, currentTask.strictModeEnabled);
        }
        editor.apply();
        Log.d(TAG, "保存状态: " + currentState + ", 任务: " + (currentTask != null ? currentTask.name : "null"));
    }
    
    /**
     * 从 SharedPreferences 恢复状态
     */
    private void restoreState() {
        String stateStr = prefs.getString(KEY_STATE, State.IDLE.name());
        try {
            currentState = State.valueOf(stateStr);
        } catch (Exception e) {
            currentState = State.IDLE;
        }
        
        // 只有在专注或休息状态时才恢复任务
        if (currentState == State.FOCUSING || currentState == State.RESTING || currentState == State.LOCKED) {
            long taskId = prefs.getLong(KEY_TASK_ID, -1);
            String taskName = prefs.getString(KEY_TASK_NAME, "");
            
            if (taskId != -1 && !taskName.isEmpty()) {
                currentTask = new FocusTask();
                currentTask.id = taskId;
                currentTask.name = taskName;
                currentTask.focusDurationMinutes = prefs.getInt(KEY_FOCUS_DURATION, 25);
                currentTask.restDurationMinutes = prefs.getInt(KEY_REST_DURATION, 5);
                currentTask.whitelistApps = prefs.getString(KEY_WHITELIST, "");
                currentTask.studyModeEnabled = prefs.getBoolean(KEY_STUDY_MODE, false);
                currentTask.strictModeEnabled = prefs.getBoolean(KEY_STRICT_MODE, false);
                
                focusStartTime = prefs.getLong(KEY_FOCUS_START_TIME, System.currentTimeMillis());
                remainingMs = prefs.getLong(KEY_REMAINING_MS, currentTask.focusDurationMinutes * 60 * 1000L);
                distractionCount = prefs.getInt(KEY_DISTRACTION_COUNT, 0);
                
                // 计算实际剩余时间（考虑app关闭期间过去的时间）
                long elapsed = System.currentTimeMillis() - focusStartTime;
                long originalDuration = currentTask.focusDurationMinutes * 60 * 1000L;
                remainingMs = Math.max(0, originalDuration - elapsed);
                
                Log.i(TAG, "🔄 恢复任务: " + taskName + ", 状态: " + currentState + ", 剩余: " + (remainingMs/1000) + "秒");
                
                // 如果还有剩余时间，重新启动计时器和深度专注模式
                if (remainingMs > 0 && currentState == State.FOCUSING) {
                    if (currentTask.studyModeEnabled) {
                        enableStudyMode(currentTask.getWhitelistSet());
                    }
                    startTimer();
                } else if (remainingMs <= 0) {
                    // 时间已经结束
                    currentState = State.IDLE;
                    clearState();
                }
            } else {
                currentState = State.IDLE;
            }
        }
    }
    
    /**
     * 清除保存的状态
     */
    private void clearState() {
        prefs.edit()
            .putString(KEY_STATE, State.IDLE.name())
            .remove(KEY_TASK_ID)
            .remove(KEY_TASK_NAME)
            .remove(KEY_TASK_GOAL)
            .apply();
        Log.d(TAG, "清除保存的状态");
    }
    
    /**
     * 获取当前任务目标（用于AI比对）
     */
    public String getCurrentTaskGoal() {
        if (currentTask != null) {
            return currentTask.name;
        }
        return prefs.getString(KEY_TASK_GOAL, "");
    }

    // ==================== Getters ====================
    
    public State getCurrentState() { return currentState; }
    public FocusTask getCurrentTask() { return currentTask; }
    public long getRemainingMs() { return remainingMs; }
    public int getDistractionCount() { return distractionCount; }
    public boolean isStrictMode() { return currentTask != null && currentTask.strictModeEnabled; }
}
