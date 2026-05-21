package com.tomato.screenmonitor;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.app.NotificationCompat;
import java.io.File;

public class ScreenMonitorService extends AccessibilityService {

    private Handler handler;
    private Runnable monitorRunnable;
    private static final String CHANNEL_ID = "screen_monitor_channel";
    private static final int NOTIFICATION_ID = 1;
    private SharedPreferences prefs;
    private String lastNotificationText = "监测中..."; // 记录上一次的通知文本

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        LogBuffer.add("Service", "服务已连接");
        prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);
        createNotificationChannel();
        updateNotificationVisibility();

        if (prefs.getBoolean("autoReturnToMain", false)) {
            prefs.edit().putBoolean("autoReturnToMain", false).apply();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            LogBuffer.add("Service", "自动跳转回主页面");
        }

        handler = new Handler(Looper.getMainLooper());
        startMonitoring();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() {
        LogBuffer.add("Service", "服务被中断");
        stopMonitoring();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        removeNotification();
        LogBuffer.add("Service", "服务已销毁");
    }

    private void startMonitoring() {
        stopMonitoring();
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                // 无论是否监测，先同步通知状态
                syncNotificationState();

                if (!prefs.getBoolean("monitorEnabled", true)) {
                    // 暂停时也更新通知文字（如果需要）
                    updateNotification("监测已暂停");
                    int intervalSec = prefs.getInt("interval", 5);
                    if (intervalSec < 1) intervalSec = 1;
                    handler.postDelayed(this, intervalSec * 1000L);
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performScreenCapture();
                } else {
                    LogBuffer.add("Service", "需要 Android 11+");
                }
                int intervalSec = prefs.getInt("interval", 5);
                if (intervalSec < 1) intervalSec = 1;
                handler.postDelayed(this, intervalSec * 1000L);
            }
        };
        handler.post(monitorRunnable);
    }

    private void stopMonitoring() {
        if (handler != null && monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
        }
    }

    private void performScreenCapture() {
        int displayId = Display.DEFAULT_DISPLAY;
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                Display[] displays = dm.getDisplays();
                if (displays.length > 0) displayId = displays[0].getDisplayId();
            }
        } catch (Exception e) {
            LogBuffer.add("Screen", "获取显示ID失败: " + e.getMessage());
        }

        final int finalDisplayId = displayId;
        takeScreenshot(finalDisplayId, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshotResult) {
                LogBuffer.add("Screen", "截图成功");
                Bitmap fullScreenBitmap = Bitmap.wrapHardwareBuffer(
                        screenshotResult.getHardwareBuffer(), null);
                if (fullScreenBitmap != null) processScreenshot(fullScreenBitmap);
                screenshotResult.getHardwareBuffer().close();
            }

            @Override
            public void onFailure(int errorCode) {
                LogBuffer.add("Screen", "截图失败 错误码:" + errorCode);
            }
        });
    }

    private void processScreenshot(Bitmap fullScreenBitmap) {
        float leftRatio = prefs.getFloat("leftRatio", 0.0f);
        float topRatio = prefs.getFloat("topRatio", 0.0f);
        float rightRatio = prefs.getFloat("rightRatio", 0.0f);
        float bottomRatio = prefs.getFloat("bottomRatio", 0.0f);

        if (rightRatio <= 0 || bottomRatio <= 0) {
            updateNotification("未设置监测区域");
            return;
        }

        int cropLeft = (int) (fullScreenBitmap.getWidth() * leftRatio);
        int cropTop = (int) (fullScreenBitmap.getHeight() * topRatio);
        int cropRight = (int) (fullScreenBitmap.getWidth() * rightRatio);
        int cropBottom = (int) (fullScreenBitmap.getHeight() * bottomRatio);

        cropLeft = Math.max(0, cropLeft);
        cropTop = Math.max(0, cropTop);
        cropRight = Math.min(fullScreenBitmap.getWidth(), cropRight);
        cropBottom = Math.min(fullScreenBitmap.getHeight(), cropBottom);
        if (cropRight - cropLeft <= 0 || cropBottom - cropTop <= 0) return;

        Bitmap croppedRegion = Bitmap.createBitmap(fullScreenBitmap, cropLeft, cropTop,
                cropRight - cropLeft, cropBottom - cropTop);
        Bitmap presetBitmap = loadPresetImage();

        if (presetBitmap != null) {
            Bitmap scaledPreset = Bitmap.createScaledBitmap(presetBitmap, croppedRegion.getWidth(),
                    croppedRegion.getHeight(), true);
            float threshold = prefs.getFloat("threshold", 0.90f);
            int tolerance = prefs.getInt("tolerance", 30);
            double similarity = ImageComparator.compareBitmaps(croppedRegion, scaledPreset, tolerance);

            String logMsg = String.format("相似度:%.4f (阈值:%.2f, 容差:%d)", similarity, threshold, tolerance);
            LogBuffer.add("Compare", logMsg);

            if (similarity >= threshold) {
                LogBuffer.add("Match", "匹配成功，播放提示音");
                playNotificationSound();
                updateNotification("匹配成功！");
            } else {
                updateNotification("相似度: " + String.format("%.2f", similarity));
            }
            scaledPreset.recycle();
            presetBitmap.recycle();
        } else {
            LogBuffer.add("Compare", "预设图片不存在");
            updateNotification("预设图片缺失");
        }
        fullScreenBitmap.recycle();
    }

    private Bitmap loadPresetImage() {
        try {
            File dir = new File(getExternalFilesDir(null), "preset");
            File file = new File(dir, "preset_crop.png");
            if (file.exists()) return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Exception e) {
            LogBuffer.add("Compare", "加载预设图片失败: " + e.getMessage());
        }
        return null;
    }

    private void playNotificationSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) ringtone.play();
        } catch (Exception e) {
            LogBuffer.add("Sound", "播放失败: " + e.getMessage());
        }
    }

    // ==================== 通知管理（核心修改） ====================
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "屏幕监测", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("监测状态");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /**
     * 根据当前开关状态同步通知：开则显示（若无则重建），关则移除
     */
    private void syncNotificationState() {
        boolean show = prefs.getBoolean("showNotification", false);
        if (show && hasNotificationPermission()) {
            // 如果通知没显示，或者需要更新为默认文本，这里用最后一次设置的文本
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                // 简单方式：每次都重新设置通知，不会重复创建，只是更新内容
                showNotification(lastNotificationText);
            }
        } else {
            removeNotification();
        }
    }

    private void updateNotificationVisibility() {
        // 服务连接时调用一次
        syncNotificationState();
    }

    private void showNotification(String text) {
        if (!hasNotificationPermission()) {
            LogBuffer.add("Notif", "无通知权限，不显示通知");
            return;
        }
        lastNotificationText = text; // 记录文本
        Notification notification = buildNotification(text);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String text) {
        // 更新通知，并受开关控制
        boolean show = prefs.getBoolean("showNotification", false);
        if (show) {
            showNotification(text);
        } else {
            // 如果关闭了通知，则移除已有通知
            removeNotification();
        }
    }

    private void removeNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕监测")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
