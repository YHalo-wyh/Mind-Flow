package com.example.mindflow.ui.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mindflow.MainActivity;
import com.example.mindflow.R;
import com.example.mindflow.databinding.FragmentDashboardBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private RecentActivityAdapter recentAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        setupUI();
        setupChart();
        setupRecentList();
        observeData();
    }

    private void setupUI() {
        binding.btnStartFocus.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).toggleFocus();
            }
        });

        binding.tvCognitiveState.setText("Ready");
        updateCognitiveStateColor("Ready");
    }

    private void setupChart() {
        LineChart chart = binding.chartState;
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.getLegend().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.GRAY);

        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(1f);
    }

    private void observeData() {
        viewModel.getCognitiveState().observe(getViewLifecycleOwner(), state -> {
            if (state != null) {
                binding.tvCognitiveState.setText(state);
                updateCognitiveStateColor(state);
            }
        });

        viewModel.getInterruptibility().observe(getViewLifecycleOwner(), score -> {
            float s = score != null ? score : 0f;
            String state = viewModel.getCognitiveState().getValue();
            binding.tvInterruptibility.setText(formatInterruptibilityText(state, s));
        });

        viewModel.getFocusMinutesToday().observe(getViewLifecycleOwner(), minutes -> {
            binding.tvFocusMinutes.setText(String.valueOf(minutes != null ? minutes : 0));
        });

        viewModel.getSessionCountToday().observe(getViewLifecycleOwner(), count -> {
            binding.tvSessionCount.setText(String.valueOf(count != null ? count : 0));
        });

        viewModel.getHeartRate().observe(getViewLifecycleOwner(), hr -> {
            binding.tvHeartRate.setText(String.valueOf(hr != null ? hr.intValue() : 72));
        });

        viewModel.getHistoryData().observe(getViewLifecycleOwner(), history -> {
            if (history != null && !history.isEmpty()) {
                updateChart(history);
            }
            if (recentAdapter != null) {
                recentAdapter.setItems(history);
            }
        });
    }

    private void setupRecentList() {
        recentAdapter = new RecentActivityAdapter();
        binding.rvRecentActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentActivities.setAdapter(recentAdapter);
    }

    private void updateChart(List<com.example.mindflow.model.LabelWindow> history) {
        List<Entry> entries = new ArrayList<>();
        // history is ordered DESC in DAO, reverse it for chart
        for (int i = history.size() - 1; i >= 0; i--) {
            com.example.mindflow.model.LabelWindow w = history.get(i);
            // Use interruptibilityScore (0-1) or convert label to score
            entries.add(new Entry(history.size() - 1 - i, w.interruptibilityScore));
        }

        LineDataSet dataSet = new LineDataSet(entries, "阻断指数");
        dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ContextCompat.getColor(requireContext(), R.color.primary));
        dataSet.setFillAlpha(50);

        LineData lineData = new LineData(dataSet);
        binding.chartState.setData(lineData);
        binding.chartState.invalidate();
    }

    private void updateCognitiveStateColor(String state) {
        int colorRes;
        switch (state) {
            case "深度专注":
                colorRes = R.color.state_deep_focus;
                break;
            case "轻度专注":
                colorRes = R.color.state_light_focus;
                break;
            case "休闲刷屏":
                colorRes = R.color.state_leisure;
                break;
            case "高压忙乱":
                colorRes = R.color.state_high_stress;
                break;
            case "放松休息":
                colorRes = R.color.state_relaxed;
                break;
            default:
                colorRes = R.color.primary;
        }
        // binding.tvCognitiveState.setTextColor(ContextCompat.getColor(requireContext(),
        // colorRes));
        // Actually keep text primary and change card stroke or something else?
        // For now keep text color.
        try {
            binding.tvCognitiveState.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        } catch (Exception e) {
        }
    }

    private String formatInterruptibilityText(String state, float score) {
        String suggestion;
        if (score < 0.3f) {
            suggestion = "建议保持专注";
        } else if (score > 0.7f) {
            suggestion = "适合处理打断";
        } else {
            suggestion = "注意专注波动";
        }
        String stateLabel = state != null ? state : "未知";
        return String.format("阻断指数: %.2f | %s · %s", score, suggestion, stateLabel);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
