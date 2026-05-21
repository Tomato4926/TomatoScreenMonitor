package com.tomato.screenmonitor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class LogViewActivity extends AppCompatActivity {

    private TextView tvLog;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_view);

        tvLog = findViewById(R.id.tv_log);
        Button btnClear = findViewById(R.id.btn_clear_log);

        btnClear.setOnClickListener(v -> {
            LogBuffer.clear();
            refreshLog();
        });

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshLog();
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void refreshLog() {
        List<String> logs = LogBuffer.getLogs();
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append("\n");
        }
        tvLog.setText(sb.toString());
    }
}