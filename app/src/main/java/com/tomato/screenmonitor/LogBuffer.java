package com.tomato.screenmonitor;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class LogBuffer {
    private static final int MAX_LINES = 500;
    private static final List<String> logs = new ArrayList<>();
    private static final String SYSTEM_TAG = "ScreenMonitor";

    public static void add(String tag, String msg) {
        // 输出到系统 Logcat
        Log.d(SYSTEM_TAG, tag + ": " + msg);

        // 保存到内存供应用内查看
        synchronized (logs) {
            logs.add(System.currentTimeMillis() + " " + tag + ": " + msg);
            if (logs.size() > MAX_LINES) {
                logs.remove(0);
            }
        }
    }

    public static List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public static void clear() {
        synchronized (logs) {
            logs.clear();
        }
    }
}