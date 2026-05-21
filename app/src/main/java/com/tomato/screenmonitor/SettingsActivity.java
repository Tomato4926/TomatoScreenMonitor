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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {

    private EditText etThreshold, etTolerance, etInterval;
    private Switch swNotification;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    swNotification.setChecked(true);
                    prefs.edit().putBoolean("showNotification", true).apply();
                    Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show();
                } else {
                    swNotification.setChecked(false);
                    prefs.edit().putBoolean("showNotification", false).apply();
                    Toast.makeText(this, "需要通知权限才能显示通知栏提示", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);
        etThreshold = findViewById(R.id.et_threshold);
        etTolerance = findViewById(R.id.et_tolerance);
        etInterval = findViewById(R.id.et_interval);
        swNotification = findViewById(R.id.sw_notification);
        Button btnSave = findViewById(R.id.btn_save);

        loadSettings();

        swNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                        swNotification.setOnCheckedChangeListener(null);
                        swNotification.setChecked(false);
                        swNotification.setOnCheckedChangeListener(this::onSwitchChanged);
                        return;
                    }
                }
                prefs.edit().putBoolean("showNotification", true).apply();
            } else {
                prefs.edit().putBoolean("showNotification", false).apply();
            }
        });

        btnSave.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void onSwitchChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
        swNotification.setChecked(isChecked);
    }

    private void loadSettings() {
        float threshold = prefs.getFloat("threshold", 0.90f);
        int tolerance = prefs.getInt("tolerance", 30);
        int interval = prefs.getInt("interval", 5);
        boolean showNotif = prefs.getBoolean("showNotification", false);

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
}