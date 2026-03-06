package com.example.mindflow.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * 语音播报助手 - 使用 TTS 进行语音提醒
 */
public class VoiceHelper {
    private static final String TAG = "VoiceHelper";
    
    private static VoiceHelper instance;
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private Context appContext;
    
    private VoiceHelper() {}
    
    public static synchronized VoiceHelper getInstance() {
        if (instance == null) {
            instance = new VoiceHelper();
        }
        return instance;
    }
    
    public void init(Context context) {
        if (tts != null) return;
        
        appContext = context.getApplicationContext();
        tts = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 尝试使用默认语言
                    tts.setLanguage(Locale.getDefault());
                    Log.w(TAG, "中文语音包不可用，使用默认语言");
                }
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.0f);
                isInitialized = true;
                Log.d(TAG, "TTS 初始化成功");
            } else {
                Log.e(TAG, "TTS 初始化失败");
            }
        });
    }
    
    /**
     * 播报文本
     */
    public void speak(String text) {
        if (!isInitialized || tts == null || text == null || text.isEmpty()) {
            Log.w(TAG, "TTS 未初始化或文本为空");
            return;
        }
        
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_" + System.currentTimeMillis());
    }
    
    /**
     * 添加到播报队列（不打断当前播报）
     */
    public void speakAdd(String text) {
        if (!isInitialized || tts == null || text == null || text.isEmpty()) return;
        
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "utterance_" + System.currentTimeMillis());
    }
    
    /**
     * 专注开始播报
     */
    public void announceStart(String task, int minutes) {
        speak("专注模式已开启，目标：" + task + "，时长" + minutes + "分钟，AI正在监控您的屏幕");
    }
    
    /**
     * 分心警告播报
     */
    public void announceWarning(int count, int maxCount) {
        if (count == 1) {
            speak("检测到分心，请回到任务");
        } else {
            speak("警告，这是第" + count + "次分心，再分心" + (maxCount - count) + "次将锁定应用");
        }
    }
    
    /**
     * 锁定播报
     */
    public void announceLock() {
        speak("分心次数过多，应用已锁定，请冷静一分钟");
    }
    
    /**
     * 专注完成播报
     */
    public void announceComplete(int minutes) {
        speak("恭喜，您已完成" + minutes + "分钟的专注，做得很棒");
    }
    
    /**
     * AI 状态播报
     */
    public void announceAiStatus(String activity, boolean isFocused) {
        if (isFocused) {
            // 专注时不打扰
        } else {
            speak("检测到您正在" + activity + "，这不符合当前任务");
        }
    }
    
    /**
     * 停止播报
     */
    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }
    
    /**
     * 释放资源
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            isInitialized = false;
        }
    }
    
    public boolean isReady() {
        return isInitialized;
    }
}
