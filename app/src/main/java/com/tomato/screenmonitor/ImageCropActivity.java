package com.tomato.screenmonitor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class ImageCropActivity extends AppCompatActivity {

    private ImageView imageView;
    private Button btnConfirm;
    private float startX, startY, endX, endY;
    private Bitmap selectedImageBitmap;
    private Matrix imageMatrix;
    private float[] matrixValues = new float[9];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_crop);

        imageView = findViewById(R.id.img_preview);
        btnConfirm = findViewById(R.id.btn_confirm_crop);

        Uri imageUri = getIntent().getData();
        if (imageUri != null) {
            try {
                InputStream imageStream = getContentResolver().openInputStream(imageUri);
                selectedImageBitmap = BitmapFactory.decodeStream(imageStream);
                imageView.setImageBitmap(selectedImageBitmap);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
            }
        }

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                imageMatrix = new Matrix(imageView.getImageMatrix());
                imageMatrix.getValues(matrixValues);

                float viewX = event.getX();
                float viewY = event.getY();

                float[] points = new float[]{viewX, viewY};
                Matrix inverseMatrix = new Matrix();
                imageMatrix.invert(inverseMatrix);
                inverseMatrix.mapPoints(points);

                float imgX = points[0];
                float imgY = points[1];

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = imgX;
                        startY = imgY;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        endX = imgX;
                        endY = imgY;
                        imageView.setImageBitmap(drawRectangleOnBitmap(selectedImageBitmap, startX, startY, endX, endY));
                        break;
                    case MotionEvent.ACTION_UP:
                        endX = imgX;
                        endY = imgY;
                        imageView.setImageBitmap(drawRectangleOnBitmap(selectedImageBitmap, startX, startY, endX, endY));
                        break;
                }
                return true;
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedImageBitmap == null) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
                return;
            }

            float left = Math.min(startX, endX);
            float top = Math.min(startY, endY);
            float right = Math.max(startX, endX);
            float bottom = Math.max(startY, endY);

            int imgW = selectedImageBitmap.getWidth();
            int imgH = selectedImageBitmap.getHeight();
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (right > imgW) right = imgW;
            if (bottom > imgH) bottom = imgH;

            float leftRatio = left / imgW;
            float topRatio = top / imgH;
            float rightRatio = right / imgW;
            float bottomRatio = bottom / imgH;

            SharedPreferences prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putFloat("leftRatio", leftRatio)
                    .putFloat("topRatio", topRatio)
                    .putFloat("rightRatio", rightRatio)
                    .putFloat("bottomRatio", bottomRatio)
                    .apply();

            int cropWidth = (int)(right - left);
            int cropHeight = (int)(bottom - top);
            if (cropWidth <= 0 || cropHeight <= 0) {
                Toast.makeText(this, "框选区域无效", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap cropBitmap = Bitmap.createBitmap(selectedImageBitmap, (int)left, (int)top, cropWidth, cropHeight);
            savePresetImage(cropBitmap);

            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "预设已保存，监测中...", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "请开启无障碍服务以开始监测", Toast.LENGTH_LONG).show();
                // 设置自动返回标记
                prefs.edit().putBoolean("autoReturnToMain", true).apply();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            finish();
        });
    }

    private Bitmap drawRectangleOnBitmap(Bitmap src, float x1, float y1, float x2, float y2) {
        Bitmap mutableBitmap = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        canvas.drawRect(new RectF(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)), paint);
        return mutableBitmap;
    }

    private void savePresetImage(Bitmap bitmap) {
        File dir = new File(getExternalFilesDir(null), "preset");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "preset_crop.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Toast.makeText(this, "预设图片已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
