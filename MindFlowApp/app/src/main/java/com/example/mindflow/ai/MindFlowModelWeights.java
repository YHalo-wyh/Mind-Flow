package com.example.mindflow.ai;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Model weights for SimpleInferenceEngine.
 *
 * To avoid Java bytecode size limits, large arrays are loaded from assets when available.
 * Asset format (big-endian float32, row-major order):
 * FC1_W, FC1_B, FC2_W, FC2_B, HEAD_STATE_W, HEAD_STATE_B, HEAD_IT_W, HEAD_IT_B.
 */
public final class MindFlowModelWeights {
    private static final String TAG = "MindFlowWeights";
    private static final String ASSET_NAME = "mindflow_weights.bin";
    private static final long DEFAULT_SEED = 1337L;

    public static final int LOOKBACK = 12;
    public static final int NUM_COLS_COUNT = 20;
    public static final int HIDDEN = 64;
    public static final int NUM_CLASSES = 5;

    // --- Preprocessing ---
    public static final String[] NUM_COLS = {
            "hour_of_day",
            "day_of_week",
            "in_focus_session",
            "touch_count",
            "scroll_count",
            "scroll_speed",
            "key_stroke_count",
            "touch_freq_var",
            "app_switch_count",
            "screen_on_ms",
            "notif_received_count",
            "notif_clicked_count",
            "notif_dismissed_count",
            "notif_blocked_in_focus_count",
            "notif_work_count",
            "notif_social_count",
            "notif_learning_count",
            "hr_mean",
            "hrv_rmssd",
            "steps"
    };

    public static final float[] MEAN = {
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f
    };

    public static final float[] STD = {
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f
    };

    // --- Labels ---
    public static final String[] LABELS = { "深度专注", "轻度专注", "休闲刷屏", "高压忙乱", "放松休息" };

    public static final class Weights {
        public final float[][] fc1W;
        public final float[] fc1B;
        public final float[][] fc2W;
        public final float[] fc2B;
        public final float[][] headStateW;
        public final float[] headStateB;
        public final float[][] headItW;
        public final float[] headItB;

        private Weights(float[][] fc1W,
                        float[] fc1B,
                        float[][] fc2W,
                        float[] fc2B,
                        float[][] headStateW,
                        float[] headStateB,
                        float[][] headItW,
                        float[] headItB) {
            this.fc1W = fc1W;
            this.fc1B = fc1B;
            this.fc2W = fc2W;
            this.fc2B = fc2B;
            this.headStateW = headStateW;
            this.headStateB = headStateB;
            this.headItW = headItW;
            this.headItB = headItB;
        }
    }

    private static volatile Weights cached;

    public static Weights get(Context context) {
        Weights local = cached;
        if (local != null) {
            return local;
        }
        synchronized (MindFlowModelWeights.class) {
            if (cached == null) {
                cached = load(context);
            }
        }
        return cached;
    }

    private static Weights load(Context context) {
        if (context != null) {
            try (InputStream raw = context.getAssets().open(ASSET_NAME);
                 DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
                return readFromStream(in);
            } catch (IOException e) {
                Log.w(TAG, "Weights asset missing or unreadable, using fallback", e);
            }
        } else {
            Log.w(TAG, "Context was null, using fallback weights");
        }
        return createFallback();
    }

    private static Weights readFromStream(DataInputStream in) throws IOException {
        float[][] fc1W = readMatrix(in, HIDDEN, LOOKBACK * NUM_COLS_COUNT);
        float[] fc1B = readVector(in, HIDDEN);
        float[][] fc2W = readMatrix(in, HIDDEN, HIDDEN);
        float[] fc2B = readVector(in, HIDDEN);
        float[][] headStateW = readMatrix(in, NUM_CLASSES, HIDDEN);
        float[] headStateB = readVector(in, NUM_CLASSES);
        float[][] headItW = readMatrix(in, 1, HIDDEN);
        float[] headItB = readVector(in, 1);
        return new Weights(fc1W, fc1B, fc2W, fc2B, headStateW, headStateB, headItW, headItB);
    }

    private static Weights createFallback() {
        Random random = new Random(DEFAULT_SEED);
        float[][] fc1W = randomMatrix(random, HIDDEN, LOOKBACK * NUM_COLS_COUNT);
        float[] fc1B = randomVector(random, HIDDEN);
        float[][] fc2W = randomMatrix(random, HIDDEN, HIDDEN);
        float[] fc2B = randomVector(random, HIDDEN);
        float[][] headStateW = randomMatrix(random, NUM_CLASSES, HIDDEN);
        float[] headStateB = randomVector(random, NUM_CLASSES);
        float[][] headItW = randomMatrix(random, 1, HIDDEN);
        float[] headItB = randomVector(random, 1);
        return new Weights(fc1W, fc1B, fc2W, fc2B, headStateW, headStateB, headItW, headItB);
    }

    private static float[][] readMatrix(DataInputStream in, int rows, int cols) throws IOException {
        float[][] out = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                out[i][j] = in.readFloat();
            }
        }
        return out;
    }

    private static float[] readVector(DataInputStream in, int size) throws IOException {
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = in.readFloat();
        }
        return out;
    }

    private static float[][] randomMatrix(Random random, int rows, int cols) {
        float[][] out = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                out[i][j] = nextWeight(random);
            }
        }
        return out;
    }

    private static float[] randomVector(Random random, int size) {
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = nextWeight(random);
        }
        return out;
    }

    private static float nextWeight(Random random) {
        return (random.nextFloat() * 0.2f) - 0.1f;
    }
}
