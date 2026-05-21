package com.tomato.screenmonitor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private SharedPreferences prefs;
    private Button btnMonitorToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);

        Button btnSelectImage = findViewById(R.id.btn_select_image);
        Button btnViewLog = findViewById(R.id.btn_view_log);
        btnMonitorToggle = findViewById(R.id.btn_monitor_toggle);
        Button btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        Button btnSettings = findViewById(R.id.btn_settings);

        updateMonitorToggleButton();

        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        btnViewLog.setOnClickListener(v -> {
            startActivity(new Intent(this, LogViewActivity.class));
        });

        btnMonitorToggle.setOnClickListener(v -> {
            boolean current = prefs.getBoolean("monitorEnabled", true);
            prefs.edit().putBoolean("monitorEnabled", !current).apply();
            updateMonitorToggleButton();
            Toast.makeText(this, "监测已" + (!current ? "开启" : "暂停"), Toast.LENGTH_SHORT).show();
            LogBuffer.add("Main", "监测开关: " + (!current ? "开启" : "暂停"));
        });

        btnOpenAccessibility.setOnClickListener(v -> {
            // 设置标记，以便服务连接后自动返回主界面
            prefs.edit().putBoolean("autoReturnToMain", true).apply();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private void updateMonitorToggleButton() {
        boolean enabled = prefs.getBoolean("monitorEnabled", true);
        btnMonitorToggle.setText("监测开关：" + (enabled ? "开启" : "关闭"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Intent cropIntent = new Intent(this, ImageCropActivity.class);
            cropIntent.setData(data.getData());
            startActivity(cropIntent);
        }
    }
}
