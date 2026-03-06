package com.example.mindflow.ui.warning;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.example.mindflow.R;

/**
 * 分心警告弹窗 Activity
 * 以对话框形式显示，提醒用户回到工作状态
 */
public class WarningDialogActivity extends Activity {
    
    private static final long AUTO_DISMISS_MS = 5000; // 5秒后自动关闭
    private Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置为对话框样式
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_warning_dialog);
        
        // 设置窗口属性
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, 
                           WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        
        // 点击外部不关闭
        setFinishOnTouchOutside(false);
        
        // 获取传入的数据
        String message = getIntent().getStringExtra("message");
        int count = getIntent().getIntExtra("count", 0);
        int maxCount = getIntent().getIntExtra("max_count", 3);
        
        // 设置UI
        TextView tvMessage = findViewById(R.id.tvWarningMessage);
        TextView tvCount = findViewById(R.id.tvWarningCount);
        Button btnOk = findViewById(R.id.btnWarningOk);
        
        if (message != null) {
            tvMessage.setText(message);
        }
        tvCount.setText("分心次数: " + count + "/" + maxCount);
        
        btnOk.setOnClickListener(v -> finish());
        
        // 自动关闭
        handler.postDelayed(this::finish, AUTO_DISMISS_MS);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
