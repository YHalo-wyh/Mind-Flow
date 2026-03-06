package com.example.mindflow.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mindflow.R;
import com.example.mindflow.model.LabelWindow;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentActivityAdapter extends RecyclerView.Adapter<RecentActivityAdapter.ViewHolder> {

    private final List<LabelWindow> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);

    public void setItems(List<LabelWindow> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LabelWindow item = items.get(position);
        holder.tvState.setText(item.cognitiveState != null
                ? item.cognitiveState
                : holder.itemView.getContext().getString(R.string.unknown_state));
        holder.tvDetail.setText(holder.itemView.getContext().getString(
                R.string.recent_activity_detail,
                item.interruptibilityScore,
                timeFormat.format(new Date(item.timestamp))));
        holder.tvState.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                mapStateColor(item.cognitiveState)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvState;
        final TextView tvDetail;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvState = itemView.findViewById(R.id.tvState);
            tvDetail = itemView.findViewById(R.id.tvDetail);
        }
    }

    private int mapStateColor(String state) {
        if (state == null) {
            return R.color.primary;
        }
        switch (state) {
            case "深度专注":
                return R.color.state_deep_focus;
            case "轻度专注":
                return R.color.state_light_focus;
            case "休闲刷屏":
                return R.color.state_leisure;
            case "高压忙乱":
                return R.color.state_high_stress;
            case "放松休息":
                return R.color.state_relaxed;
            default:
                return R.color.primary;
        }
    }
}
