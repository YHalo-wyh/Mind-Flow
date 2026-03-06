package com.example.mindflow.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class PredictionResponse {
    @SerializedName("pred_state") public String pred_state;
    @SerializedName("pred_state_id") public String pred_state_id;
    @SerializedName("interruptibility_score") public double interruptibility_score;

    // ✨ 核心修复：将 String 改为 ExplanationData 对象
    public ExplanationData explanation;

    @SerializedName("state_probs") public Map<String, Double> state_probs;
    @SerializedName("state_probs_id") public Map<String, Double> state_probs_id;
    @SerializedName("suggested_action") public SuggestedAction suggested_action;

    // 建立内部类来对接 JSON 里的对象结构
    public static class ExplanationData {
        public String summary; // 这才是我们要显示的“人话”建议
        public Map<String, Object> latest_features;
    }

    public static class SuggestedAction {
        public List<String> allow_notification_categories;
        public String intervention;
        public String reason;
    }
}
