package com.example.mindflow.ui.lock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mindflow.R;

import java.util.List;
import java.util.Set;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppInfo> appList;
    private final Set<String> selectedPackages;

    public AppAdapter(List<AppInfo> appList, Set<String> selectedPackages) {
        this.appList = appList;
        this.selectedPackages = selectedPackages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载单行布局 (请确保稍后创建 item_app.xml)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = appList.get(position);

        // 1. 填充数据
        holder.tvName.setText(app.label);
        holder.tvPackage.setText(app.packageName);
        holder.ivIcon.setImageDrawable(app.icon);

        // 2. 根据 Set 中的记录决定 CheckBox 状态
        holder.cbSelect.setChecked(selectedPackages.contains(app.packageName));

        // 3. 点击整个条目触发选择切换
        holder.itemView.setOnClickListener(v -> {
            toggleSelection(app.packageName, position);
        });

        // 4. 点击 CheckBox 本身触发选择切换
        holder.cbSelect.setOnClickListener(v -> {
            toggleSelection(app.packageName, position);
        });
    }

    private void toggleSelection(String packageName, int position) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName);
        } else {
            selectedPackages.add(packageName);
        }
        // 刷新该项 UI
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    // ViewHolder 内部类：通过 ID 找到布局中的控件
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvPackage;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvName = itemView.findViewById(R.id.tvAppName);
            tvPackage = itemView.findViewById(R.id.tvAppPackage);
            cbSelect = itemView.findViewById(R.id.cbAppSelect);
        }
    }
}