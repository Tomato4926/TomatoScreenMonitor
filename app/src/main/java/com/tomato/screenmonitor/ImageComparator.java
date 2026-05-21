package com.tomato.screenmonitor;

import android.graphics.Bitmap;

public class ImageComparator {

    /**
     * 比较两个位图的相似度，返回 0.0 ~ 1.0
     * @param bitmap1 截图区域（可能为 HARDWARE 格式）
     * @param bitmap2 预设图片（通常为 ARGB_8888）
     * @param colorTolerance 颜色容差 0~255
     */
    public static double compareBitmaps(Bitmap bitmap1, Bitmap bitmap2, int colorTolerance) {
        if (bitmap1 == null || bitmap2 == null) return 0.0;

        // 转换为可读的 ARGB_8888 格式
        Bitmap bmp1 = convertToArgb8888(bitmap1);
        Bitmap bmp2 = convertToArgb8888(bitmap2);

        if (bmp1.getWidth() != bmp2.getWidth() || bmp1.getHeight() != bmp2.getHeight()) {
            recycleIfCopy(bmp1, bitmap1);
            recycleIfCopy(bmp2, bitmap2);
            return 0.0;
        }

        int width = bmp1.getWidth();
        int height = bmp1.getHeight();
        long matchingPixels = 0;
        long totalPixels = (long) width * height;

        // 按行读取像素，避免逐像素 JNI 调用
        int[] row1 = new int[width];
        int[] row2 = new int[width];

        for (int y = 0; y < height; y++) {
            bmp1.getPixels(row1, 0, width, 0, y, width, 1);
            bmp2.getPixels(row2, 0, width, 0, y, width, 1);

            for (int x = 0; x < width; x++) {
                if (colorsMatch(row1[x], row2[x], colorTolerance)) {
                    matchingPixels++;
                }
            }
        }

        double similarity = (double) matchingPixels / totalPixels;

        // 回收可能创建的临时位图
        recycleIfCopy(bmp1, bitmap1);
        recycleIfCopy(bmp2, bitmap2);

        return similarity;
    }

    private static boolean colorsMatch(int color1, int color2, int tolerance) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        return Math.abs(r1 - r2) <= tolerance &&
                Math.abs(g1 - g2) <= tolerance &&
                Math.abs(b1 - b2) <= tolerance;
    }

    private static Bitmap convertToArgb8888(Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        return bitmap;
    }

    private static void recycleIfCopy(Bitmap bmp, Bitmap original) {
        if (bmp != original && !bmp.isRecycled()) {
            bmp.recycle();
        }
    }
}