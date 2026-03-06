package com.example.mindflow.ui.report;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.mindflow.R;
import com.example.mindflow.databinding.FragmentReportBinding;
import com.example.mindflow.network.GlmApiService;
import com.example.mindflow.service.DistractionManager;

/**
 * 数据报告Fragment
 * 展示专注统计、分心记录和 AI 总结
 */
public class ReportFragment extends Fragment {

    private FragmentReportBinding binding;
    private ReportViewModel viewModel;
    private DistractionManager distractionManager;

    private String currentPeriodLabel = "今日";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable aiSummaryTimeoutRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ReportViewModel.class);
        distractionManager = new DistractionManager(requireContext());

        setupUI();
        observeData();
        loadAiLog();

        // 默认加载今日数据
        viewModel.loadData(ReportViewModel.Period.TODAY);
    }

    private void setupUI() {
        // 周期选择
        binding.chipToday.setOnClickListener(v -> {
            currentPeriodLabel = "今日";
            viewModel.loadData(ReportViewModel.Period.TODAY);
        });
        binding.chipWeek.setOnClickListener(v -> {
            currentPeriodLabel = "本周";
            viewModel.loadData(ReportViewModel.Period.WEEK);
        });
        binding.chipMonth.setOnClickListener(v -> {
            currentPeriodLabel = "本月";
            viewModel.loadData(ReportViewModel.Period.MONTH);
        });
        
        // AI 总结按钮
        binding.btnGenerateSummary.setOnClickListener(v -> generateAiSummary());
        
        // AI日志清空按钮
        binding.btnClearAiLog.setOnClickListener(v -> {
            distractionManager.clearAiRecognitionLog();
            loadAiLog();
            android.widget.Toast.makeText(requireContext(), "日志已清空", android.widget.Toast.LENGTH_SHORT).show();
        });
        
        // 解决嵌套ScrollView滚动冲突：当触摸AI日志区域时，禁止父ScrollView拦截
        binding.scrollAiLog.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }
    
    /**
     * 加载AI识别日志（最新50条，新记录在下面）
     */
    private void loadAiLog() {
        if (binding == null) return;
        String logContent = distractionManager.getAiRecognitionLog();
        int logCount = distractionManager.getAiRecognitionLogCount();
        
        binding.tvAiLogCount.setText(logCount + "条");
        binding.tvAiLog.setText(logCount > 0 ? logContent : "暂无AI识别记录");
    }

    private void observeData() {
        viewModel.getTotalMinutes().observe(getViewLifecycleOwner(), minutes -> {
            binding.tvTotalMinutes.setText(String.valueOf(minutes != null ? minutes : 0));
            updateFocusRate();
        });

        viewModel.getTotalInterventions().observe(getViewLifecycleOwner(), count -> {
            binding.tvTotalInterventions.setText(String.valueOf(count != null ? count : 0));
            updateFocusRate();
        });

        viewModel.getPositiveFeedbackRate().observe(getViewLifecycleOwner(), rate -> {
            // 显示专注率
            if (rate != null && rate > 0) {
                binding.tvPositiveFeedback.setText(String.format("%.0f%%", rate * 100));
            }
        });

        // 分心记录
        viewModel.getDistractionHistory().observe(getViewLifecycleOwner(), history -> {
            if (history != null && !history.isEmpty()) {
                binding.tvDistractionList.setText(history);
            } else {
                binding.tvDistractionList.setText("暂无分心记录，继续保持！");
            }
        });
    }

    private void updateFocusRate() {
        Integer minutes = viewModel.getTotalMinutes().getValue();
        Integer interventions = viewModel.getTotalInterventions().getValue();
        
        if (minutes != null && minutes > 0) {
            // 简单计算：每次分心扣5%，最低0%
            int distractions = interventions != null ? interventions : 0;
            float rate = Math.max(0, 100 - distractions * 5);
            binding.tvPositiveFeedback.setText(String.format("%.0f%%", rate));
        } else {
            binding.tvPositiveFeedback.setText("--");
        }
    }

    private void generateAiSummary() {
        Integer minutes = viewModel.getTotalMinutes().getValue();
        Integer interventions = viewModel.getTotalInterventions().getValue();
        String history = viewModel.getDistractionHistory().getValue();
        
        int focusMinutes = minutes != null ? minutes : 0;
        int distractionCount = interventions != null ? interventions : 0;
        
        if (focusMinutes == 0 && distractionCount == 0) {
            binding.tvAiSummary.setText("暂无数据，开始专注后再来生成报告吧！");
            return;
        }
        
        binding.btnGenerateSummary.setEnabled(false);

        binding.tvAiSummary.setText("AI 正在生成报告...");

        // 避免 stopFocusSession() 的全局取消状态影响报告生成
        GlmApiService.resetCancelState();

        if (aiSummaryTimeoutRunnable != null) {
            mainHandler.removeCallbacks(aiSummaryTimeoutRunnable);
        }
        aiSummaryTimeoutRunnable = () -> {
            if (binding != null && !binding.btnGenerateSummary.isEnabled()) {
                binding.tvAiSummary.setText("生成超时，请检查网络后重试");
                binding.btnGenerateSummary.setEnabled(true);
            }
        };
        mainHandler.postDelayed(aiSummaryTimeoutRunnable, 35000);

        SharedPreferences prefs = requireContext().getSharedPreferences("MindFlowPrefs", Context.MODE_PRIVATE);
        String goal = prefs.getString("confirmed_goal", "");
        if (goal == null || goal.trim().isEmpty()) {
            goal = "（未设置）";
        }

        String aiLog = distractionManager != null ? distractionManager.getAiRecognitionLog() : "";
        if (aiLog == null) aiLog = "";
        aiLog = aiLog.trim();

        // 控制 prompt 长度，避免过长导致请求失败/耗时过久
        final int maxAiLogChars = 600;
        if (aiLog.length() > maxAiLogChars) {
            aiLog = aiLog.substring(aiLog.length() - maxAiLogChars);
        }

        String focusRateText;
        if (focusMinutes > 0) {
            float rate = Math.max(0f, 1f - (distractionCount * 0.05f));
            int percent = Math.max(0, Math.min(100, Math.round(rate * 100f)));
            focusRateText = percent + "%";
        } else {
            focusRateText = "--";
        }

        // 构建 AI 提示
        String historyText = (history != null && !history.trim().isEmpty()) ? history.trim() : "无";
        String aiLogText = !aiLog.isEmpty() ? aiLog : "无";

        String prompt = "你是一个专注助手，负责根据分心记录给出简短复盘与建议。\n\n" +
            "用户目标：" + goal + "\n" +
            "周期：" + currentPeriodLabel + "\n" +
            "专注时长：" + focusMinutes + " 分钟\n" +
            "分心次数：" + distractionCount + " 次\n" +
            "估算专注率：" + focusRateText + "\n\n" +
            "分心记录：" + historyText + "\n" +
            "AI识别日志：" + aiLogText + "\n\n" +
            "请输出一段中文总结（150字以内），包含：\n" +
            "1. 简单评价专注情况\n" +
            "2. 主要分心点（1-2个）\n" +
            "3. 2条可执行建议\n" +
            "不要使用markdown，不要使用表情符号。";
        
        GlmApiService.analyzeText(prompt, new GlmApiService.AiCallback() {
            @Override
            public void onSuccess(String result) {
                mainHandler.removeCallbacks(aiSummaryTimeoutRunnable);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding != null) {
                        binding.tvAiSummary.setText(result);
                        binding.btnGenerateSummary.setEnabled(true);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                mainHandler.removeCallbacks(aiSummaryTimeoutRunnable);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding != null) {
                        binding.tvAiSummary.setText("生成失败：" + error + "\n请检查网络后重试");
                        binding.btnGenerateSummary.setEnabled(true);
                    }
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到页面时刷新AI日志
        loadAiLog();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (aiSummaryTimeoutRunnable != null) {
            mainHandler.removeCallbacks(aiSummaryTimeoutRunnable);
            aiSummaryTimeoutRunnable = null;
        }
        binding = null;
    }
}
