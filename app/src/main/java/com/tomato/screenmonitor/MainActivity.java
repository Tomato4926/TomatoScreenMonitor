package com.tomato.screenmonitor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private EditText etThreshold, etTolerance, etInterval;
    private Switch swNotification;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // 权限已授予，更新开关状态
                    swNotification.setChecked(true);
                    prefs.edit().putBoolean("showNotification", true).apply();
                    Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show();
                } else {
                    // 权限被拒绝，关闭开关并提示
                    swNotification.setChecked(false);
                    prefs.edit().putBoolean("showNotification", false).apply();
                    Toast.makeText(this, "需要通知权限才能显示通知栏提示", Toast.LENGTH_LONG).show();
                    // 可选：引导用户到应用设置页面
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);

        Button btnSelectImage = findViewById(R.id.btn_select_image);
        etThreshold = findViewById(R.id.et_threshold);
        etTolerance = findViewById(R.id.et_tolerance);
        etInterval = findViewById(R.id.et_interval);
        swNotification = findViewById(R.id.sw_notification);
        Button btnViewLog = findViewById(R.id.btn_view_log);

        loadSettings();

        btnSelectImage.setOnClickListener(v -> {
            saveSettings();
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        btnViewLog.setOnClickListener(v -> {
            startActivity(new Intent(this, LogViewActivity.class));
        });

        swNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 尝试开启通知，先检查权限
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        // 请求权限（系统对话框），结果在回调中处理
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                        // 暂时将开关还原，等权限回调再设置
                        swNotification.setOnCheckedChangeListener(null);
                        swNotification.setChecked(false);
                        swNotification.setOnCheckedChangeListener(this::onNotificationSwitchChanged);
                        return;
                    }
                }
                // 权限已授予或旧版本，直接打开
                prefs.edit().putBoolean("showNotification", true).apply();
                Toast.makeText(this, "通知已开启", Toast.LENGTH_SHORT).show();
            } else {
                prefs.edit().putBoolean("showNotification", false).apply();
            }
        });

        // 添加文本监听器，自动保存设置
        TextWatcher textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                saveSettings();
            }
        };
        etThreshold.addTextChangedListener(textWatcher);
        etTolerance.addTextChangedListener(textWatcher);
        etInterval.addTextChangedListener(textWatcher);
    }

    // 避免 lambda 引用问题，单独定义一个方法
    private void onNotificationSwitchChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
        swNotification.setChecked(isChecked);
    }

    private void loadSettings() {
        float threshold = prefs.getFloat("threshold", 0.90f);
        int tolerance = prefs.getInt("tolerance", 30);
        int interval = prefs.getInt("interval", 5);
        boolean showNotif = prefs.getBoolean("showNotification", false); // 默认关闭，等用户手动开启

        etThreshold.setText(String.valueOf(threshold));
        etTolerance.setText(String.valueOf(tolerance));
        etInterval.setText(String.valueOf(interval));
        swNotification.setChecked(showNotif);
    }

    private void saveSettings() {
        try {
            float threshold = Float.parseFloat(etThreshold.getText().toString());
            int tolerance = Integer.parseInt(etTolerance.getText().toString());
            int interval = Integer.parseInt(etInterval.getText().toString());

            prefs.edit()
                    .putFloat("threshold", threshold)
                    .putInt("tolerance", tolerance)
                    .putInt("interval", interval)
                    .apply();
        } catch (NumberFormatException ignored) {}
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