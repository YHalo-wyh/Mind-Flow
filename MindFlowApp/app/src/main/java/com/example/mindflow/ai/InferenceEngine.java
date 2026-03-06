package com.example.mindflow.ai;

import android.content.Context;
import android.util.Log;

import com.example.mindflow.model.WindowData;

import java.util.List;

/**
 * On-Device Inference Engine for MindFlow (Pure Java Edition).
 * Replaces the Python backend network calls and PyTorch library.
 * Now delegates to SimpleInferenceEngine.
 */
public class InferenceEngine {
    private static final String TAG = "InferenceEngine";

    private static InferenceEngine instance;
    private SimpleInferenceEngine mEngine;

    // Singleton
    public static synchronized InferenceEngine getInstance(Context context) {
        if (instance == null) {
            instance = new InferenceEngine(context.getApplicationContext());
        }
        return instance;
    }

    private InferenceEngine(Context context) {
        // Initialize Pure Java Engine
        mEngine = new SimpleInferenceEngine(context);
        Log.i(TAG, "InferenceEngine (Pure Java) initialized.");
    }

    public PredictionResult predict(List<WindowData> recentWindows) {
        try {
            // Delegate to Simple engine
            SimpleInferenceEngine.PredictionResult raw = mEngine.predict(recentWindows);

            // Map to local result class to keep API compatibility
            return new PredictionResult(raw.state, raw.interruptibility, null);

        } catch (Exception e) {
            Log.e(TAG, "Inference Failed", e);
            return PredictionResult.dummy();
        }
    }

    public static class PredictionResult {
        public String state;
        public float interruptibility;
        public float[] probabilities; // derived if needed, now null

        public PredictionResult(String s, float i, float[] p) {
            state = s;
            interruptibility = i;
            probabilities = p;
        }

        public static PredictionResult dummy() {
            return new PredictionResult("深度专注", 0.5f, null);
        }
    }
}
