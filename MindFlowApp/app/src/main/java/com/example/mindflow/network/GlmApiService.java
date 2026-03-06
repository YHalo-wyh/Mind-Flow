package com.example.mindflow.network;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.example.mindflow.BuildConfig;
import com.example.mindflow.utils.AuthUtils; // 👈 必须确保导入了我们之前写的 AuthUtils

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GlmApiService {
    private static final String TAG = "GlmApiService";

    // Read from build config to avoid hardcoding secrets in source.
    private static final String API_KEY = BuildConfig.GLM_API_KEY;

    // 智谱 GLM-4V 的请求地址
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    // 当前用户的专注目标
    private static String currentFocusGoal = "工作";
    
    // 当前前台应用名称（用于辅助AI判断）
    private static String currentAppName = "";

    // OkHttpClient 实例
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // 当前正在进行的请求（用于取消）
    private static Call currentCall = null;
    private static volatile boolean isCancelled = false;

    // 回调接口
    public interface AiCallback {
        void onSuccess(String result);
        void onFailure(String error);
    }
    
    /**
     * 取消所有正在进行的AI请求
     */
    public static void cancelAllRequests() {
        isCancelled = true;
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
            Log.i(TAG, "🛑 已取消所有AI请求");
        }
    }
    
    /**
     * 重置取消状态（开始新会话时调用）
     */
    public static void resetCancelState() {
        isCancelled = false;
        Log.d(TAG, "✅ AI请求状态已重置");
    }

    /**
     * 设置当前专注目标
     */
    public static void setFocusGoal(String goal) {
        if (goal != null && !goal.trim().isEmpty()) {
            currentFocusGoal = goal.trim();
        }
    }

    /**
     * 获取当前专注目标
     */
    public static String getFocusGoal() {
        return currentFocusGoal;
    }
    
    /**
     * 设置当前前台应用名称（用于辅助AI判断）
     */
    public static void setCurrentAppName(String appName) {
        if (appName != null) {
            currentAppName = appName.trim();
        }
    }

    /**
     * 构建带目标的提示词（结合当前应用+屏幕内容）- 强制JSON格式输出
     */
    private static String buildPrompt() {
        String appInfo = "";
        if (currentAppName != null && !currentAppName.isEmpty()) {
            appInfo = "当前App：" + currentAppName + "\n";
        }
        return "你是专注度分析助手。\n\n" +
               appInfo +
               "用户专注目标：【" + currentFocusGoal + "】\n\n" +
               "判断规则：\n" +
               "- 目标相关的任何行为 → YES\n" +
               "- 目标完全无关的行为 → NO\n\n" +
               "【重要】你必须且只能返回以下JSON格式，不要有任何其他文字：\n" +
               "{\"conclusion\":\"YES或NO\",\"reason\":\"1-2句话说明判断理由，格式：用户目标是X，当前在做Y，因此判断符合/不符合目标\",\"behavior\":\"当前行为5-10字\",\"confidence\":0-100}\n\n" +
               "示例1：{\"conclusion\":\"YES\",\"reason\":\"用户目标是刷B站，当前在B站看视频，因此判断符合目标\",\"behavior\":\"看B站视频\",\"confidence\":95}\n" +
               "示例2：{\"conclusion\":\"NO\",\"reason\":\"用户目标是写代码，当前在刷抖音，因此判断不符合目标\",\"behavior\":\"刷抖音\",\"confidence\":90}";
    }

    /**
     * 发送图片给 AI 进行分析
     * @param bitmap 屏幕截图
     * @param callback 结果回调
     */
    public static void analyzeImage(Bitmap bitmap, AiCallback callback) {
        // 检查是否已取消
        if (isCancelled) {
            Log.d(TAG, "⏹️ AI请求已取消，跳过分析");
            callback.onFailure("AI 请求已取消，请重试");
            return;
        }

        if (API_KEY == null || API_KEY.trim().isEmpty()) {
            Log.e(TAG, "GLM API key is missing. Set GLM_API_KEY in local.properties.");
            callback.onFailure("API Key 缺失");
            return;
        }

        // 1. 获取 JWT Token
        String token = AuthUtils.getToken(API_KEY);

        if (token == null) {
            Log.e(TAG, "Token 生成失败");
            callback.onFailure("Token 生成失败");
            return;
        }

        // 2. 将图片压缩并转为 Base64
        String base64Image = bitmapToBase64(bitmap);
        if (base64Image == null) {
            callback.onFailure("图片处理失败");
            return;
        }

        // 3. 构造请求 JSON
        try {
            JSONObject root = new JSONObject();
            root.put("model", "glm-4v-flash");  // GLM-4V-Flash视觉理解模型（当前可用） 

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");

            JSONArray contentList = new JSONArray();

            // 文本部分
            JSONObject textObj = new JSONObject();
            textObj.put("type", "text");
            textObj.put("text", buildPrompt());
            contentList.put(textObj);

            // 图片部分
            JSONObject imageObj = new JSONObject();
            imageObj.put("type", "image_url");
            JSONObject urlObj = new JSONObject();
            urlObj.put("url", base64Image);
            imageObj.put("image_url", urlObj);
            contentList.put(imageObj);

            userMessage.put("content", contentList);
            messages.put(userMessage);

            root.put("messages", messages);

            // 可选参数
            root.put("temperature", 0.5); // 控制随机性
            root.put("top_p", 0.9);

            String jsonBody = root.toString();

            // 4. 构建 HTTP 请求
            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + token) // 👈 使用生成的 Token
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // 5. 发送异步请求
            Log.d(TAG, "正在发送请求给 AI...");

            // 保存当前请求引用，以便取消
            currentCall = client.newCall(request);
            currentCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    currentCall = null;
                    if (isCancelled || call.isCanceled()) {
                        Log.d(TAG, "⏹️ AI请求已取消");
                        return;
                    }
                    Log.e(TAG, "网络请求失败", e);
                    callback.onFailure("网络错误: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    currentCall = null;
                    // 检查是否已取消
                    if (isCancelled || call.isCanceled()) {
                        Log.d(TAG, "⏹️ AI请求已取消，忽略响应");
                        return;
                    }
                    String responseStr = response.body().string();
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API 错误: " + response.code() + " - " + responseStr);
                        callback.onFailure("API 错误: " + response.code());
                        return;
                    }

                    // 6. 解析返回的 JSON
                    String resultText = parseResponse(responseStr);
                    callback.onSuccess(resultText);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "JSON 构造异常", e);
            callback.onFailure("数据构造错误");
        }
    }

    /**
     * 辅助方法：图片转 Base64
     * 会进行压缩以加快上传速度
     */
    private static String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // 压缩格式为 JPEG，质量 50% (既省流量又保留细节)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 辅助方法：解析智谱 AI 返回的 JSON
     */
    private static String parseResponse(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            // 智谱的标准返回结构：choices -> [0] -> message -> content
            if (json.has("choices")) {
                JSONArray choices = json.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    return message.getString("content").trim(); // 去除首尾空格
                }
            }
            // 如果解析失败，可能是报错信息，直接返回原文方便调试
            return "无法解析内容: " + jsonStr;
        } catch (Exception e) {
            Log.e(TAG, "解析响应失败", e);
            return "解析错误";
        }
    }

    /**
     * 分析屏幕文字内容判断是否分心
     */
    public static void analyzeText(String prompt, AiCallback callback) {
        // 检查是否已取消
        if (isCancelled) {
            Log.d(TAG, "⏹️ AI请求已取消，跳过分析");
            callback.onFailure("AI 请求已取消，请重试");
            return;
        }
        
        if (API_KEY == null || API_KEY.trim().isEmpty()) {
            callback.onFailure("API Key 缺失");
            return;
        }
        
        String token = AuthUtils.getToken(API_KEY);
        if (token == null) {
            callback.onFailure("Token 生成失败");
            return;
        }

        try {
            JSONObject root = new JSONObject();
            root.put("model", "glm-4-flash");

            JSONArray messages = new JSONArray();
            
            // 系统提示词 - 强调结合用户目标判断，返回JSON格式
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个专注度分析助手。你的任务是：\n" +
                "1. 根据用户设定的【专注目标】来判断当前行为是否分心\n" +
                "2. 用户会提供：专注目标、当前应用、屏幕内容\n" +
                "3. 判断当前行为是否与专注目标相关\n\n" +
                "判断标准：\n" +
                "- 如果目标是「写代码」，刷抖音=分心，看技术文档=专注\n" +
                "- 如果目标是「学英语」，看英语视频=专注，刷微博=分心\n" +
                "- 如果目标是「休息」，刷短视频=专注\n\n" +
                "【重要】你必须只返回JSON格式，不要有任何其他内容：\n" +
                "{\"conclusion\":\"YES或NO\",\"reason\":\"1-2句话说明判断理由\",\"behavior\":\"当前行为5-10字\",\"confidence\":0-100}\n" +
                "示例：{\"conclusion\":\"NO\",\"reason\":\"用户目标是写代码，但当前在刷短视频\",\"behavior\":\"刷抖音\",\"confidence\":95}");
            messages.put(systemMessage);

            // 用户消息
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.put(userMessage);

            root.put("messages", messages);
            root.put("temperature", 0.3);

            Log.d(TAG, "发送文字分析请求...");
            
            RequestBody body = RequestBody.create(root.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + token)
                    .post(body)
                    .build();

            // 保存当前请求引用，以便取消
            currentCall = client.newCall(request);
            currentCall.enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    currentCall = null;
                    if (isCancelled || call.isCanceled()) {
                        Log.d(TAG, "⏹️ AI请求已取消");
                        return;
                    }
                    Log.e(TAG, "文字分析请求失败", e);
                    callback.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    currentCall = null;
                    // 检查是否已取消
                    if (isCancelled || call.isCanceled()) {
                        Log.d(TAG, "⏹️ AI请求已取消，忽略响应");
                        return;
                    }
                    
                    String respStr = response.body().string();
                    if (response.isSuccessful()) {
                        String result = parseResponse(respStr);
                        Log.d(TAG, "文字分析结果: " + result);
                        callback.onSuccess(result);
                    } else {
                        Log.e(TAG, "API 错误: " + response.code() + " - " + respStr);
                        callback.onFailure("API 错误: " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "构造 JSON 失败", e);
            callback.onFailure("构造 JSON 失败");
        }
    }
}
