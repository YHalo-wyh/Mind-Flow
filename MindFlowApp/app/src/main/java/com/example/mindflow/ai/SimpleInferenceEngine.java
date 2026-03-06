package com.example.mindflow.ai;

import android.content.Context;
import com.example.mindflow.model.WindowData;
import java.util.List;

/**
 * Pure Java Inference Engine.
 * Uses hardcoded weights from MindFlowModelWeights.java.
 * No PyTorch dependency.
 */
public class SimpleInferenceEngine {

    // Feature extraction mapping matches export_manual.py
    private static final String[] FEATURE_ORDER = MindFlowModelWeights.NUM_COLS;
    private final MindFlowModelWeights.Weights weights;

    public static class PredictionResult {
        public String state;
        public float interruptibility;

        public PredictionResult(String state, float interruptibility) {
            this.state = state;
            this.interruptibility = interruptibility;
        }
    }

    public SimpleInferenceEngine(Context context) {
        this.weights = MindFlowModelWeights.get(context);
    }

    public PredictionResult predict(List<WindowData> history) {
        if (history.size() < MindFlowModelWeights.LOOKBACK) {
            // Pad or handle error? For simplicity, we just take what we have and pad with
            // zeros/last
            // But ideally we need enough history.
            // Let's pad with first element if needed.
            return new PredictionResult("未知", 0.5f);
        }

        // 1. Extract Features & Standardize
        int lookback = MindFlowModelWeights.LOOKBACK;
        int numFeats = MindFlowModelWeights.NUM_COLS_COUNT;

        // Input buffer [lookback * numFeats]
        float[] input = new float[lookback * numFeats];

        // Take last 'lookback' items
        int startIdx = history.size() - lookback;

        for (int i = 0; i < lookback; i++) {
            WindowData w = history.get(startIdx + i);
            float[] feats = extractFeatures(w);

            // Standardize and Flatten
            for (int f = 0; f < numFeats; f++) {
                float val = feats[f];
                float mean = MindFlowModelWeights.MEAN[f];
                float std = MindFlowModelWeights.STD[f];
                float norm = (val - mean) / std;
                input[i * numFeats + f] = norm;
            }
        }

        // 2. Forward Pass (MLP)
        // FC1: input [N] * W [Hidden, N] + B [Hidden]
        float[] h1 = dense(input, weights.fc1W, weights.fc1B);
        relu(h1);

        // FC2: h1 [Hidden] * W [Hidden, Hidden] + B [Hidden]
        float[] h2 = dense(h1, weights.fc2W, weights.fc2B);
        relu(h2);

        // Head State: h2 * W [5, Hidden] + B [5]
        float[] logits = dense(h2, weights.headStateW, weights.headStateB);

        // Head IT: h2 * W [1, Hidden] + B [1]
        float[] itLogit = dense(h2, weights.headItW, weights.headItB);

        // 3. Post-process
        int maxIdx = argmax(logits);
        String state = MindFlowModelWeights.LABELS[maxIdx];
        float itScore = sigmoid(itLogit[0]);

        return new PredictionResult(state, itScore);
    }

    private float[] extractFeatures(WindowData w) {
        // Must match order:
        // hour_of_day, day_of_week, in_focus_session, touch_count, ...
        // We assume WindowData has these fields or we calculate them.
        // For simplicity, mapping known fields and 0 for others if missing in Java
        // model

        float[] f = new float[MindFlowModelWeights.NUM_COLS_COUNT];

        // We'll map by index manually based on the known list
        // 0: hour_of_day
        f[0] = w.hour_of_day;
        // 1: day_of_week
        f[1] = w.day_of_week;
        // 2: in_focus_session
        f[2] = w.in_focus_session; // was int in WindowData
        // 3: touch_count
        f[3] = w.touch_count;
        // 4: scroll_count
        f[4] = w.scroll_count;
        // 5: scroll_speed
        f[5] = (float) w.scroll_speed; // double -> float
        // 6: key_stroke_count
        f[6] = w.key_stroke_count;
        // 7: touch_freq_var
        f[7] = (float) w.touch_freq_var; // double -> float
        // 8: app_switch_count
        f[8] = w.app_switch_count;
        // 9: screen_on_ms
        f[9] = w.screen_on_ms;
        // 10: notif_received_count
        f[10] = w.notif_received_count;
        // 11: notif_clicked_count
        f[11] = w.notif_clicked_count;
        // 12: notif_dismissed_count
        f[12] = w.notif_dismissed_count;
        // 13: notif_blocked_in_focus_count
        f[13] = w.notif_blocked_in_focus_count;
        // 14: notif_work_count
        f[14] = w.notif_work_count;
        // 15: notif_social_count
        f[15] = w.notif_social_count;
        // 16: notif_learning_count
        f[16] = w.notif_learning_count;
        // 17: hr_mean
        f[17] = (float) w.hr_mean; // double -> float
        // 18: hrv_rmssd
        f[18] = (float) w.hrv_rmssd; // double -> float
        // 19: steps
        f[19] = w.steps;

        return f;
    }

    // --- Ops ---

    private float[] dense(float[] x, float[][] W, float[] b) {
        int outDim = W.length;
        int inDim = W[0].length;
        // Note: PyTorch/Java export provides W as [Out, In] usually
        // Let's verify: input_dim is cols, hidden is rows.
        // Yes, standard Linear layer weight is [out_features, in_features]

        float[] y = new float[outDim];
        for (int i = 0; i < outDim; i++) {
            float sum = b[i];
            for (int j = 0; j < inDim; j++) {
                sum += W[i][j] * x[j];
            }
            y[i] = sum;
        }
        return y;
    }

    private void relu(float[] x) {
        for (int i = 0; i < x.length; i++) {
            if (x[i] < 0)
                x[i] = 0;
        }
    }

    private float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    private int argmax(float[] x) {
        int idx = 0;
        float max = x[0];
        for (int i = 1; i < x.length; i++) {
            if (x[i] > max) {
                max = x[i];
                idx = i;
            }
        }
        return idx;
    }
}
