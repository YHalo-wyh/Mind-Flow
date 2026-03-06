package com.example.mindflow.ui.session;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Handler;
import android.os.Looper;

import com.example.mindflow.R;
import com.example.mindflow.network.GlmApiService;
import com.example.mindflow.service.AppMonitorService;
import com.example.mindflow.service.FocusService;
import com.example.mindflow.utils.ScreenCaptureDataHolder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class SessionFragment extends Fragment {
    
    private static final String PREF_NAME = "MindFlowPrefs";
    private static final String KEY_CONFIRMED_GOAL = "confirmed_goal";
    private static final String KEY_CUSTOM_DURATION_MIN = "custom_duration_min";
    
    private TextView tvTimer, tvSessionStatus, tvFocusStatus, tvDistractionCount, tvCurrentApp, tvAiRawOutput, tvServiceStatus, tvTestResult, tvGoalStatus, tvWhitelistCount;
    private TextInputEditText etGoal;
    private MaterialButton btnStart, btnTestApi, btnConfirmGoal, btnWhitelist, btnAddCurrentApp;
    private String confirmedGoal = "";
    private Chip chip15, chip25, chip45, chip60, chipCustom;
    
    private long selectedDurationMs = 25 * 60 * 1000L;
    private long remainingMs = selectedDurationMs;
    private int customDurationMin = 0;

    private int lastSelectedMinutes = 25;
    private boolean lastSelectedIsCustom = false;
    
    private FocusService focusService;
    private boolean isBound = false;
    
    private ActivityResultLauncher<Intent> screenCaptureLauncher;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        screenCaptureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    ScreenCaptureDataHolder.setPermissionData(result.getResultCode(), result.getData());
                    startFocusWithScreenCapture();
                } else {
                    Toast.makeText(getContext(), "需要屏幕录制权限才能进行 AI 分析", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvTimer = view.findViewById(R.id.tvTimer);
        tvSessionStatus = view.findViewById(R.id.tvSessionStatus);
        btnStart = view.findViewById(R.id.btnStartPause);
        tvFocusStatus = view.findViewById(R.id.tvFocusStatus);
        tvDistractionCount = view.findViewById(R.id.tvDistractionCount);
        tvCurrentApp = view.findViewById(R.id.tvCurrentApp);
        tvAiRawOutput = view.findViewById(R.id.tvAiRawOutput);
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus);
        etGoal = view.findViewById(R.id.etGoal);
        btnConfirmGoal = view.findViewById(R.id.btnConfirmGoal);
        tvGoalStatus = view.findViewById(R.id.tvGoalStatus);
        
        // 深度专注模式和AI检测默认开启，无需开关
        
        // 白名单设置
        tvWhitelistCount = view.findViewById(R.id.tvWhitelistCount);
        btnWhitelist = view.findViewById(R.id.btnWhitelist);
        btnAddCurrentApp = view.findViewById(R.id.btnAddCurrentApp);
        
        if (btnWhitelist != null) {
            btnWhitelist.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), com.example.mindflow.ui.lock.WhitelistActivity.class);
                startActivity(intent);
            });
        }
        
        if (btnAddCurrentApp != null) {
            btnAddCurrentApp.setOnClickListener(v -> addCurrentAppToWhitelist());
        }
        
        updateWhitelistCount();
        
        // 确认任务按钮
        if (btnConfirmGoal != null) {
            btnConfirmGoal.setOnClickListener(v -> confirmGoal());
        }
        
        chip15 = view.findViewById(R.id.chip15min);
        chip25 = view.findViewById(R.id.chip25min);
        chip45 = view.findViewById(R.id.chip45min);
        chip60 = view.findViewById(R.id.chip60min);
        chipCustom = view.findViewById(R.id.chipCustom);
        
        restoreCustomDuration();
        setupDurationChips();
        btnStart.setOnClickListener(v -> toggleFocus());
        
        // 测试 API 按钮
        btnTestApi = view.findViewById(R.id.btnTestApi);
        tvTestResult = view.findViewById(R.id.tvTestResult);
        if (btnTestApi != null) {
            btnTestApi.setOnClickListener(v -> testApiConnection());
        }
        
        bindFocusService();
        registerReceivers();
        
        // 恢复保存的任务目标
        restoreGoal();
        
        updateUI();
    }
    
    /**
     * 从 SharedPreferences 恢复任务目标
     */
    private void restoreGoal() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedGoal = prefs.getString(KEY_CONFIRMED_GOAL, "");
        
        if (!savedGoal.isEmpty()) {
            confirmedGoal = savedGoal;
            
            // 恢复输入框显示
            if (etGoal != null) {
                etGoal.setText(savedGoal);
            }
            
            // 恢复状态显示
            if (tvGoalStatus != null) {
                tvGoalStatus.setText("当前任务：" + savedGoal);
                tvGoalStatus.setTextColor(getResources().getColor(R.color.success, null));
            }
            
            // 同步到 AI 服务
            GlmApiService.setFocusGoal(savedGoal);
            
            android.util.Log.d("SessionFragment", "恢复任务目标: " + savedGoal);
        }
    }
    
    /**
     * 清除保存的任务目标（用户主动停止专注时调用）
     */
    private void clearSavedGoal() {
        confirmedGoal = "";
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CONFIRMED_GOAL).apply();
        
        if (etGoal != null) {
            etGoal.setText("");
        }
        if (tvGoalStatus != null) {
            tvGoalStatus.setText("请设置任务目标");
            tvGoalStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
        }
        
        android.util.Log.d("SessionFragment", "已清除任务目标");
    }
    
    /**
     * 测试 API 连接
     */
    private void testApiConnection() {
        if (tvTestResult == null || btnTestApi == null) return;
        
        tvTestResult.setText("正在测试 API 连接...\n请稍候...");
        btnTestApi.setEnabled(false);
        
        String testPrompt = "用户专注目标：写代码\n当前应用：com.example.test\n屏幕内容：Android Studio 代码编辑器\n\n请判断用户是否在专注做目标任务。回复格式：[行为描述] | [是否专注:是/否]";
        
        GlmApiService.analyzeText(testPrompt, new GlmApiService.AiCallback() {
            @Override
            public void onSuccess(String result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvTestResult != null && btnTestApi != null) {
                        tvTestResult.setText("API 测试成功");
                        btnTestApi.setEnabled(true);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvTestResult != null && btnTestApi != null) {
                        tvTestResult.setText("API 测试失败");
                        btnTestApi.setEnabled(true);
                    }
                });
            }
        });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unregisterReceivers();
        if (isBound) {
            requireContext().unbindService(serviceConnection);
            isBound = false;
        }
    }
    
    private void setupDurationChips() {
        if (chip15 != null) chip15.setOnClickListener(v -> selectDuration(15));
        if (chip25 != null) chip25.setOnClickListener(v -> selectDuration(25));
        if (chip45 != null) chip45.setOnClickListener(v -> selectDuration(45));
        if (chip60 != null) chip60.setOnClickListener(v -> selectDuration(60));
        if (chipCustom != null) chipCustom.setOnClickListener(v -> showCustomDurationDialog());
    }
    
    
    
    private void selectDuration(int minutes) {
        selectedDurationMs = minutes * 60 * 1000L;
        remainingMs = selectedDurationMs;
        updateTimerDisplay();

        lastSelectedMinutes = minutes;
        lastSelectedIsCustom = false;

        if (chip15 != null) chip15.setChecked(minutes == 15);
        if (chip25 != null) chip25.setChecked(minutes == 25);
        if (chip45 != null) chip45.setChecked(minutes == 45);
        if (chip60 != null) chip60.setChecked(minutes == 60);
        if (chipCustom != null) chipCustom.setChecked(false);
    }

    private void restoreCustomDuration() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        customDurationMin = prefs.getInt(KEY_CUSTOM_DURATION_MIN, 0);
        if (chipCustom != null) {
            if (customDurationMin > 0) {
                chipCustom.setText("自定义(" + customDurationMin + "分钟)");
            } else {
                chipCustom.setText("自定义");
            }
        }
    }

    private void showCustomDurationDialog() {
        if (getContext() == null) return;

        final int prevMinutes = lastSelectedMinutes;
        final boolean prevIsCustom = lastSelectedIsCustom;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom_duration, null);
        TextInputEditText input = dialogView.findViewById(R.id.etCustomDuration);
        if (input != null) {
            input.setTextColor(getResources().getColor(R.color.text_primary, null));
            input.setHintTextColor(getResources().getColor(R.color.text_hint, null));
        }
        if (input != null && customDurationMin > 0) {
            input.setText(String.valueOf(customDurationMin));
            if (input.getText() != null) {
                input.setSelection(input.getText().length());
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle("自定义专注时长")
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", (d, w) -> {
                if (prevIsCustom) {
                    applyCustomDuration(prevMinutes);
                } else {
                    selectDuration(prevMinutes);
                }
            })
            .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String text = (input != null && input.getText() != null) ? input.getText().toString().trim() : "";
                int minutes;
                try {
                    minutes = Integer.parseInt(text);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "请输入有效的分钟数", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (minutes <= 0 || minutes > 300) {
                    Toast.makeText(getContext(), "分钟数范围：1-300", Toast.LENGTH_SHORT).show();
                    return;
                }

                customDurationMin = minutes;
                android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit().putInt(KEY_CUSTOM_DURATION_MIN, minutes).apply();
                applyCustomDuration(minutes);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void applyCustomDuration(int minutes) {
        selectedDurationMs = minutes * 60 * 1000L;
        remainingMs = selectedDurationMs;
        updateTimerDisplay();

        lastSelectedMinutes = minutes;
        lastSelectedIsCustom = true;

        if (chip15 != null) chip15.setChecked(false);
        if (chip25 != null) chip25.setChecked(false);
        if (chip45 != null) chip45.setChecked(false);
        if (chip60 != null) chip60.setChecked(false);
        if (chipCustom != null) {
            chipCustom.setChecked(true);
            chipCustom.setText("自定义(" + minutes + "分钟)");
        }
    }
    
    /**
     * 确认任务目标
     */
    private void confirmGoal() {
        String goal = etGoal != null && etGoal.getText() != null ? 
            etGoal.getText().toString().trim() : "";
        
        if (goal.isEmpty()) {
            Toast.makeText(getContext(), "请输入任务目标", Toast.LENGTH_SHORT).show();
            return;
        }
        
        confirmedGoal = goal;
        
        // 保存到 SharedPreferences
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CONFIRMED_GOAL, goal).apply();
        
        // 立即更新到 FocusService（如果已绑定）
        if (focusService != null) {
            focusService.setFocusGoal(confirmedGoal);
        }
        
        // 更新 AI 服务的目标
        GlmApiService.setFocusGoal(confirmedGoal);
        
        // 更新状态显示
        if (tvGoalStatus != null) {
            tvGoalStatus.setText("任务已设置：" + confirmedGoal);
            tvGoalStatus.setTextColor(getResources().getColor(R.color.success, null));
        }
        
        Toast.makeText(getContext(), "任务目标已更新：" + confirmedGoal, Toast.LENGTH_SHORT).show();
    }
    
    private void toggleFocus() {
        if (focusService == null) {
            Toast.makeText(getContext(), "服务未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        
        FocusService.FocusState state = focusService.getCurrentState();
        
        if (state == FocusService.FocusState.IDLE) {
            // 使用已确认的任务目标
            if (confirmedGoal.isEmpty()) {
                Toast.makeText(getContext(), "请先设置任务目标", Toast.LENGTH_SHORT).show();
                return;
            }
            focusService.setFocusGoal(confirmedGoal);
            requestScreenCapturePermission();
        } else if (state == FocusService.FocusState.FOCUSING) {
            focusService.stopFocusSession();
            // 用户主动停止专注时清除保存的任务
            clearSavedGoal();
            updateUI();
        } else if (state == FocusService.FocusState.PAUSED) {
            focusService.resumeFocusSession();
            updateUI();
        }
    }
    
    private void requestScreenCapturePermission() {
        MediaProjectionManager mpm = (MediaProjectionManager) 
            requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent());
    }
    
    private void startFocusWithScreenCapture() {
        if (focusService == null) return;
        
        Intent initIntent = new Intent(getContext(), FocusService.class);
        initIntent.setAction("INIT_SCREEN_CAPTURE");
        requireContext().startService(initIntent);
        
        btnStart.postDelayed(() -> {
            Intent startIntent = new Intent(getContext(), FocusService.class);
            startIntent.setAction("START_FOCUS");
            startIntent.putExtra("duration_ms", selectedDurationMs);
            requireContext().startService(startIntent);
            updateUI();
        }, 500);
    }
    
    private void bindFocusService() {
        Intent intent = new Intent(getContext(), FocusService.class);
        requireContext().startService(intent);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FocusService.FocusBinder binder = (FocusService.FocusBinder) service;
            focusService = binder.getService();
            isBound = true;
            updateUI();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            focusService = null;
            isBound = false;
        }
    };
    
    private void registerReceivers() {
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(requireContext());
        lbm.registerReceiver(timerReceiver, new IntentFilter(FocusService.ACTION_TIMER_TICK));
        lbm.registerReceiver(stateReceiver, new IntentFilter(FocusService.ACTION_FOCUS_STATE_CHANGED));
        lbm.registerReceiver(aiReceiver, new IntentFilter(FocusService.ACTION_AI_RESULT));
        lbm.registerReceiver(warningReceiver, new IntentFilter(FocusService.ACTION_WARNING));
        lbm.registerReceiver(serviceStatusReceiver, new IntentFilter(AppMonitorService.ACTION_SERVICE_STATUS));
    }
    
    private void unregisterReceivers() {
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(requireContext());
        lbm.unregisterReceiver(timerReceiver);
        lbm.unregisterReceiver(stateReceiver);
        lbm.unregisterReceiver(aiReceiver);
        lbm.unregisterReceiver(warningReceiver);
        lbm.unregisterReceiver(serviceStatusReceiver);
    }
    
    private final BroadcastReceiver serviceStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(AppMonitorService.EXTRA_STATUS);
            String method = intent.getStringExtra(AppMonitorService.EXTRA_METHOD);
            int readCount = intent.getIntExtra("read_count", 0);
            
            if (tvServiceStatus != null) {
                tvServiceStatus.setText(method + ": " + status + " (读取" + readCount + "次)");
            }
        }
    };
    
    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (focusService == null || focusService.getCurrentState() != FocusService.FocusState.FOCUSING) {
                return;
            }
            remainingMs = intent.getLongExtra("remaining_ms", 0);
            updateTimerDisplay();

            int warnCount = intent.getIntExtra("warn_count", -1);
            if (warnCount >= 0 && tvDistractionCount != null) {
                tvDistractionCount.setText("分心：" + warnCount + "/3");
                if (warnCount >= 2) {
                    tvDistractionCount.setTextColor(getResources().getColor(R.color.error, null));
                } else {
                    tvDistractionCount.setTextColor(getResources().getColor(R.color.warning, null));
                }
            }
        }
    };
    
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUI();
        }
    };
    
    private final BroadcastReceiver aiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (focusService == null || focusService.getCurrentState() != FocusService.FocusState.FOCUSING) {
                return;
            }
            String vision = intent.getStringExtra("vision");
            String activity = intent.getStringExtra("activity");
            boolean isFocused = intent.getBooleanExtra("is_focused", true);
            String currentApp = intent.getStringExtra("current_app");
            
            // 显示当前应用
            if (tvCurrentApp != null) {
                tvCurrentApp.setText("当前应用：" + (currentApp != null ? currentApp : "检测中..."));
            }
            
            // 解析并显示 AI 输出（提取关键字段）
            if (tvAiRawOutput != null && vision != null) {
                String displayText = formatAiOutput(vision, activity, isFocused);
                tvAiRawOutput.setText(displayText);
            }
            
            // 更新状态
            if (tvFocusStatus != null) {
                if (isFocused) {
                    tvFocusStatus.setText("状态：专注中");
                    tvFocusStatus.setTextColor(getResources().getColor(R.color.success, null));
                } else {
                    tvFocusStatus.setText("状态：分心");
                    tvFocusStatus.setTextColor(getResources().getColor(R.color.warning, null));
                }
            }
        }
    };
    
    /**
     * 格式化AI输出，提取并显示关键信息
     */
    private String formatAiOutput(String vision, String activity, boolean isFocused) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI 输出：\n");
        
        // 尝试解析JSON格式
        String behavior = extractJsonField(vision, "behavior");
        String reason = extractJsonField(vision, "reason");
        String conclusion = extractJsonField(vision, "conclusion");
        
        if (!behavior.isEmpty() || !reason.isEmpty()) {
            // JSON格式
            sb.append("行为：").append(behavior.isEmpty() ? activity : behavior).append("\n");
            
            // 【重要】以reason为准判断是否符合目标（防止AI自相矛盾）
            boolean reasonSaysNo = reason.contains("不符合") || reason.contains("分心") || reason.contains("偏离");
            boolean actuallyFocused = isFocused && !reasonSaysNo;
            
            sb.append("结论：").append(actuallyFocused ? "符合目标" : "不符合目标").append("\n");
            if (!reason.isEmpty()) {
                sb.append("理由：").append(reason);
            }
        } else {
            // 旧格式或原始输出
            sb.append(vision);
        }
        
        return sb.toString();
    }
    
    /**
     * 从JSON字符串中提取字段值
     */
    private String extractJsonField(String json, String fieldName) {
        if (json == null || json.isEmpty()) return "";
        try {
            String pattern1 = "\"" + fieldName + "\":\"";
            String pattern2 = "\"" + fieldName + "\": \"";
            int start = json.indexOf(pattern1);
            if (start == -1) start = json.indexOf(pattern2);
            if (start == -1) return "";
            
            start = json.indexOf("\"", start + fieldName.length() + 2) + 1;
            int end = json.indexOf("\"", start);
            if (end > start) {
                return json.substring(start, end);
            }
        } catch (Exception e) {
            // 解析失败
        }
        return "";
    }
    
    private final BroadcastReceiver warningReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (focusService == null || focusService.getCurrentState() != FocusService.FocusState.FOCUSING) {
                return;
            }
            int count = intent.getIntExtra("count", 0);
            if (tvSessionStatus != null) {
                tvSessionStatus.setText("分心警告");
            }
            if (tvDistractionCount != null) {
                tvDistractionCount.setText("分心：" + count + "/3");
                if (count >= 2) {
                    tvDistractionCount.setTextColor(getResources().getColor(R.color.error, null));
                }
            }
        }
    };
    
    private void updateUI() {
        if (focusService == null) {
            if (tvSessionStatus != null) tvSessionStatus.setText("设置任务后开启 AI 监控专注");
            if (btnStart != null) {
                btnStart.setText("开始专注");
                btnStart.setIconResource(android.R.drawable.ic_media_play);
            }
            if (tvFocusStatus != null) tvFocusStatus.setText("状态：待开始");
            if (tvDistractionCount != null) {
                tvDistractionCount.setText("分心：0/3");
                tvDistractionCount.setTextColor(getResources().getColor(R.color.warning, null));
            }
            remainingMs = selectedDurationMs;
            updateTimerDisplay();
            return;
        }

        FocusService.FocusState state = focusService.getCurrentState();

        if (tvDistractionCount != null) {
            int warnCount = focusService.getWarningCount();
            tvDistractionCount.setText("分心：" + warnCount + "/3");
            if (warnCount >= 2) {
                tvDistractionCount.setTextColor(getResources().getColor(R.color.error, null));
            } else {
                tvDistractionCount.setTextColor(getResources().getColor(R.color.warning, null));
            }
        }

        switch (state) {
            case IDLE:
                if (tvSessionStatus != null) tvSessionStatus.setText("设置任务后开启 AI 监控专注");
                if (btnStart != null) {
                    btnStart.setText("开始专注");
                    btnStart.setIconResource(android.R.drawable.ic_media_play);
                }
                if (tvFocusStatus != null) tvFocusStatus.setText("状态：待开始");
                if (tvDistractionCount != null) {
                    tvDistractionCount.setText("分心：0/3");
                    tvDistractionCount.setTextColor(getResources().getColor(R.color.warning, null));
                }
                if (tvServiceStatus != null) {
                    boolean accessibilityRunning = AppMonitorService.isRunning();
                    tvServiceStatus.setText(accessibilityRunning ?
                        "AccessibilityService: 已连接" :
                        "AccessibilityService: 未开启 (请在设置中开启)");
                }
                remainingMs = selectedDurationMs;
                break;
            case FOCUSING:
                if (tvSessionStatus != null) tvSessionStatus.setText("专注中");
                if (btnStart != null) {
                    btnStart.setText("结束专注");
                    btnStart.setIconResource(android.R.drawable.ic_media_pause);
                }
                break;
            case PAUSED:
                if (tvSessionStatus != null) tvSessionStatus.setText("专注已暂停");
                if (btnStart != null) {
                    btnStart.setText("继续专注");
                    btnStart.setIconResource(android.R.drawable.ic_media_play);
                }
                break;
            case RESTING:
                if (tvSessionStatus != null) tvSessionStatus.setText("休息中");
                break;
        }

        updateTimerDisplay();
    }
    
    private void updateTimerDisplay() {
        if (tvTimer == null) return;
        int minutes = (int) (remainingMs / 60000);
        int seconds = (int) ((remainingMs % 60000) / 1000);
        tvTimer.setText(String.format(Locale.CHINA, "%02d:%02d", minutes, seconds));
    }
    
    private void updateWhitelistCount() {
        if (tvWhitelistCount == null || getContext() == null) return;
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
        java.util.Set<String> raw = prefs.getStringSet("whitelist", new java.util.HashSet<>());

        java.util.Set<String> cleaned = new java.util.HashSet<>();
        for (String pkg : raw) {
            if (pkg == null) continue;
            String p = pkg.trim();
            if (p.isEmpty()) continue;
            if (p.equals(getContext().getPackageName())) continue;
            // 过滤进程名形式：com.xxx:process
            if (p.contains(":")) continue;
            try {
                android.content.Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(p);
                if (launch != null) {
                    cleaned.add(p);
                }
            } catch (Exception ignored) {
            }
        }

        tvWhitelistCount.setText("已选择 " + cleaned.size() + " 个应用");
    }
    
    private void addCurrentAppToWhitelist() {
        if (getContext() == null) return;
        
        // 获取当前前台应用包名
        com.example.mindflow.service.AppMonitorService service = com.example.mindflow.service.AppMonitorService.getInstance();
        if (service == null) {
            Toast.makeText(getContext(), "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String currentPkg = service.getCurrentPackageName();
        if (currentPkg == null || currentPkg.isEmpty() || currentPkg.equals(getContext().getPackageName())) {
            Toast.makeText(getContext(), "无法获取当前应用，请切换到其他应用后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        // 过滤进程名形式：com.xxx:process
        if (currentPkg.contains(":")) {
            Toast.makeText(getContext(), "当前获取到的是进程标识，无法加入白名单", Toast.LENGTH_SHORT).show();
            return;
        }

        // 只允许可启动应用
        try {
            android.content.Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(currentPkg);
            if (launch == null) {
                Toast.makeText(getContext(), "该条目不是可启动应用，无法加入白名单", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法校验应用，加入失败", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 获取应用名称
        String appName = currentPkg;
        try {
            appName = getContext().getPackageManager().getApplicationLabel(
                getContext().getPackageManager().getApplicationInfo(currentPkg, 0)).toString();
        } catch (Exception e) {
            // 使用包名
        }
        
        // 添加到白名单
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
        java.util.Set<String> whitelist = new java.util.HashSet<>(prefs.getStringSet("whitelist", new java.util.HashSet<>()));
        
        if (whitelist.contains(currentPkg)) {
            Toast.makeText(getContext(), appName + " 已在白名单中", Toast.LENGTH_SHORT).show();
        } else {
            whitelist.add(currentPkg);
            prefs.edit().putStringSet("whitelist", whitelist).apply();
            Toast.makeText(getContext(), "已添加 " + appName + " 到白名单", Toast.LENGTH_SHORT).show();
            updateWhitelistCount();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateWhitelistCount();

        if (focusService != null && tvDistractionCount != null) {
            int warnCount = focusService.getWarningCount();
            tvDistractionCount.setText("分心：" + warnCount + "/3");
            if (warnCount >= 2) {
                tvDistractionCount.setTextColor(getResources().getColor(R.color.error, null));
            } else {
                tvDistractionCount.setTextColor(getResources().getColor(R.color.warning, null));
            }
        }
    }
}
