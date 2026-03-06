package com.example.mindflow.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {
    private static final String TAG = "ScreenCaptureManager";
    private static ScreenCaptureManager instance;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;
    private Handler backgroundHandler;

    // AI 分析相关
    private long lastAnalyzeTime = 0;
    private static final long ANALYZE_INTERVAL = 3000; // 3秒分析一次，更实时的反馈
    private OnScreenContentListener aiListener;

    // 定义回调接口
    public interface OnScreenContentListener {
        void onScreenCaptured(Bitmap bitmap);
    }

    private ScreenCaptureManager() {
        HandlerThread thread = new HandlerThread("ScreenCaptureThread");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());
    }

    public static synchronized ScreenCaptureManager getInstance() {
        if (instance == null) {
            instance = new ScreenCaptureManager();
        }
        return instance;
    }

    // 设置 AI 监听器
    public void setAiListener(OnScreenContentListener listener) {
        this.aiListener = listener;
    }

    public void start(Context context, MediaProjection projection) {
        if (projection == null || context == null) return;
        this.mediaProjection = projection;

        // 1. 获取屏幕参数
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        this.screenDensity = metrics.densityDpi;

        // 2. 注册停止回调
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stop();
            }
        }, null);

        // 3. 初始化并开始
        setupImageReader();
        createVirtualDisplay();
        Log.d(TAG, "Screen Capture Started!");
    }

    @SuppressLint("WrongConstant")
    private void setupImageReader() {
        // 使用 RGBA_8888 格式
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    // 限流检查：如果距离上次分析不足 5 秒，直接丢弃
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastAnalyzeTime > ANALYZE_INTERVAL) {
                        lastAnalyzeTime = currentTime;
                        // 转换并发送给 AI
                        Bitmap bitmap = imageToBitmap(image);
                        if (aiListener != null && bitmap != null) {
                            Log.d(TAG, "捕捉到画面，发送给 AI 分析...");
                            // 注意：这里是在后台线程回调，AI处理如果有UI操作需要切回主线程
                            aiListener.onScreenCaptured(bitmap);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Image processing error", e);
            } finally {
                if (image != null) {
                    image.close(); // 必须关闭，否则 ImageReader 会卡死
                }
            }
        }, backgroundHandler);
    }

    private void createVirtualDisplay() {
        if (mediaProjection == null || imageReader == null) return;
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "MindFlow-Display",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
        );
    }

    public void stop() {
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        virtualDisplay = null;
        imageReader = null;
        mediaProjection = null;
        Log.d(TAG, "Screen Capture Stopped");
    }

    // 将 Image 对象转换为 Bitmap
    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            // 创建 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            // 裁剪掉因为字节对齐产生的黑边
            Bitmap finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);

            // 可选：为了性能，这里可以将图片缩小，比如缩小到 1/4
            return Bitmap.createScaledBitmap(finalBitmap, screenWidth / 4, screenHeight / 4, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}