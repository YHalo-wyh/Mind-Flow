package com.example.mindflow.sync;

import android.content.Context;
import android.util.Log;

import com.example.mindflow.auth.AuthManager;
import com.example.mindflow.database.MindFlowDatabase;
import com.example.mindflow.database.FocusRecordDao;
import com.example.mindflow.database.dao.FocusSessionDao;
import com.example.mindflow.model.FocusRecord;
import com.example.mindflow.model.FocusSession;
import com.example.mindflow.auth.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 云端数据同步服务
 * 负责将本地专注数据同步到 Supabase
 */
public class CloudSyncService {
    private static final String TAG = "CloudSyncService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final Context context;
    private final ExecutorService executor;
    private final MindFlowDatabase database;
    private final OkHttpClient client;
    private final String supabaseUrl;
    private final String supabaseKey;
    
    private static CloudSyncService instance;
    
    public static synchronized CloudSyncService getInstance(Context context) {
        if (instance == null) {
            instance = new CloudSyncService(context.getApplicationContext());
        }
        return instance;
    }
    
    private CloudSyncService(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.database = MindFlowDatabase.getInstance(context);
        this.client = new OkHttpClient();
        this.supabaseUrl = com.example.mindflow.BuildConfig.SUPABASE_URL;
        this.supabaseKey = com.example.mindflow.BuildConfig.SUPABASE_ANON_KEY;
    }
    
    /**
     * 同步所有本地数据到云端
     */
    public void syncAll(SyncCallback callback) {
        executor.execute(() -> {
            try {
                // 检查登录状态
                if (!AuthManager.getInstance(context).isLoggedIn()) {
                    notifyError(callback, "未登录");
                    return;
                }
                
                String accessToken = AuthManager.getInstance(context).getAccessToken();
                String userId = AuthManager.getInstance(context).getUserId();
                
                if (accessToken == null || userId == null) {
                    notifyError(callback, "会话已过期");
                    return;
                }
                
                // 同步专注会话
                boolean sessionsSynced = syncSessions(accessToken, userId);
                
                // 同步专注记录
                boolean recordsSynced = syncRecords(accessToken, userId);
                
                if (sessionsSynced && recordsSynced) {
                    notifySuccess(callback, "同步成功");
                } else {
                    notifyError(callback, "部分数据同步失败");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Sync error", e);
                notifyError(callback, "同步失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 同步单个专注会话
     */
    public void syncSession(FocusSession session, SyncCallback callback) {
        executor.execute(() -> {
            try {
                if (!AuthManager.getInstance(context).isLoggedIn()) {
                    return; // 静默失败
                }
                
                String accessToken = AuthManager.getInstance(context).getAccessToken();
                String userId = AuthManager.getInstance(context).getUserId();
                
                if (accessToken == null || userId == null) {
                    return;
                }
                
                JSONObject body = buildSessionJson(session, userId);
                
                Request request = new Request.Builder()
                        .url(supabaseUrl + "/rest/v1/focus_sessions")
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Session synced: " + session.sessionId);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Sync session error", e);
            }
        });
    }
    
    /**
     * 同步单个专注记录
     */
    public void syncRecord(FocusRecord record, SyncCallback callback) {
        executor.execute(() -> {
            try {
                if (!AuthManager.getInstance(context).isLoggedIn()) {
                    return;
                }
                
                String accessToken = AuthManager.getInstance(context).getAccessToken();
                String userId = AuthManager.getInstance(context).getUserId();
                
                if (accessToken == null || userId == null) {
                    return;
                }
                
                JSONObject body = buildRecordJson(record, userId);
                
                // 使用 upsert 避免重复
                Request request = new Request.Builder()
                        .url(supabaseUrl + "/rest/v1/focus_records?on_conflict=user_id,date")
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Record synced: " + record.id);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Sync record error", e);
            }
        });
    }
    
    private boolean syncSessions(String accessToken, String userId) {
        try {
            FocusSessionDao dao = database.focusSessionDao();
            // 获取最近7天的会话
            long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
            List<FocusSession> sessions = dao.getSessionsAfterTime(sevenDaysAgo);
            
            int successCount = 0;
            for (FocusSession session : sessions) {
                try {
                    JSONObject body = buildSessionJson(session, userId);
                    
                    Request request = new Request.Builder()
                            .url(supabaseUrl + "/rest/v1/focus_sessions")
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .post(RequestBody.create(body.toString(), JSON))
                            .build();
                    
                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync session: " + session.sessionId, e);
                }
            }
            
            Log.d(TAG, "Synced " + successCount + "/" + sessions.size() + " sessions");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Sync sessions error", e);
            return false;
        }
    }
    
    private boolean syncRecords(String accessToken, String userId) {
        try {
            FocusRecordDao dao = database.focusRecordDao();
            List<FocusRecord> records = dao.getAllRecordsSync();
            
            int successCount = 0;
            for (FocusRecord record : records) {
                try {
                    JSONObject body = buildRecordJson(record, userId);
                    
                    Request request = new Request.Builder()
                            .url(supabaseUrl + "/rest/v1/focus_records?on_conflict=user_id,date")
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                            .post(RequestBody.create(body.toString(), JSON))
                            .build();
                    
                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync record: " + record.id, e);
                }
            }
            
            Log.d(TAG, "Synced " + successCount + "/" + records.size() + " records");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Sync records error", e);
            return false;
        }
    }
    
    private JSONObject buildSessionJson(FocusSession session, String userId) throws Exception {
        JSONObject json = new JSONObject();
        json.put("user_id", userId);
        json.put("session_type", session.sessionType != null ? session.sessionType : "work");
        json.put("goal_text", session.goalText != null ? session.goalText : "");
        json.put("planned_min", session.plannedMin);
        json.put("actual_min", session.actualMin);
        json.put("start_ts", session.startTs);
        json.put("end_ts", session.endTs);
        json.put("distraction_count", session.distractionCount);
        json.put("intervention_count", session.interventionCount);
        json.put("self_focus_score", session.selfFocusScore);
        json.put("self_fatigue_score", session.selfFatigueScore);
        json.put("is_active", session.isActive);
        return json;
    }
    
    private JSONObject buildRecordJson(FocusRecord record, String userId) throws Exception {
        JSONObject json = new JSONObject();
        json.put("user_id", userId);
        // 使用日期字符串作为 date 字段
        String dateStr = String.format(Locale.US, "%04d-%02d-%02d", 
                record.year, record.month, record.dayOfMonth);
        json.put("date", dateStr);
        json.put("total_focus_time", (int)(record.durationMs / 60000)); // 转换为分钟
        json.put("session_count", 1);
        json.put("avg_focus_score", record.isCompleted ? 80.0 : 50.0); // 简单计算
        return json;
    }
    
    private void notifySuccess(SyncCallback callback, String message) {
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onSuccess(message));
        }
    }
    
    private void notifyError(SyncCallback callback, String error) {
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onError(error));
        }
    }
    
    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String error);
    }
}
