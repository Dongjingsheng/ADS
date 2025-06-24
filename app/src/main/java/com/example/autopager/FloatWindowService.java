package com.example.autopager;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

public class FloatWindowService extends Service {
    private static final String TAG = "FloatWindowService";
    private WindowManager windowManager;
    private View floatView;
    private boolean isStarted = false;
    private int currentCount = 0;
    private int totalCount = 0;

    private BroadcastReceiver countReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.autopager.UPDATE_COUNT".equals(intent.getAction())) {
                int cur = intent.getIntExtra("cur", 0);
                int total = intent.getIntExtra("total", 0);
                updateCount(cur, total);
            }
        }
    };

    private BroadcastReceiver alphaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.autopager.UPDATE_ALPHA".equals(intent.getAction())) {
                int alpha = intent.getIntExtra("alpha", 80);
                updateAlpha(alpha);
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.float_panel, null);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0; params.y = 200;

        Button btnStartStop = floatView.findViewById(R.id.btn_start_stop);
        TextView tvCount = floatView.findViewById(R.id.tv_count);
        Button btnClose = floatView.findViewById(R.id.btn_close);

        tvCount.setText(currentCount + "/" + totalCount);

        SharedPreferences sp = getSharedPreferences("autopager", MODE_PRIVATE);
        int alpha = sp.getInt("float_alpha", 80);
        updateAlpha(alpha);

        btnStartStop.setOnClickListener(v -> {
            if (!isStarted) {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                    openAccessibilitySettings();
                    return;
                }
                Intent intent = new Intent(this, AutoPageService.class);
                intent.setAction("start");
                try {
                    startService(intent);
                    isStarted = true;
                    btnStartStop.setText("停止");
                } catch (Exception e) {
                    Log.e(TAG, "启动服务失败", e);
                    Toast.makeText(this, "启动服务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Intent intent = new Intent(this, AutoPageService.class);
                intent.setAction("stop");
                try {
                    startService(intent);
                } catch (Exception e) {
                    Log.e(TAG, "停止服务失败", e);
                    Toast.makeText(this, "停止服务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                isStarted = false;
                btnStartStop.setText("开始");
            }
        });

        btnClose.setOnClickListener(v -> {
            try {
                if (windowManager != null && floatView != null) {
                    windowManager.removeView(floatView);
                }
            } catch (Exception e) {
                Log.e(TAG, "移除悬浮窗失败", e);
            }
            stopSelf();
        });

        floatView.setOnTouchListener(new View.OnTouchListener() {
            private int lastX, lastY, paramX, paramY;
            private int screenWidth = 0, screenHeight = 0;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (screenWidth == 0 || screenHeight == 0) {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        WindowMetrics metrics = wm.getCurrentWindowMetrics();
                        screenWidth = metrics.getBounds().width();
                        screenHeight = metrics.getBounds().height();
                    } else {
                        Display display = wm.getDefaultDisplay();
                        android.graphics.Point size = new android.graphics.Point();
                        display.getSize(size);
                        screenWidth = size.x;
                        screenHeight = size.y;
                    }
                }
                int viewWidth = floatView.getWidth();
                int viewHeight = floatView.getHeight();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        paramX = params.x;
                        paramY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) event.getRawX() - lastX;
                        int dy = (int) event.getRawY() - lastY;
                        int newX = paramX + dx;
                        int newY = paramY + dy;

                        newX = Math.max(0, Math.min(newX, screenWidth - viewWidth));
                        newY = Math.max(0, Math.min(newY, screenHeight - viewHeight));

                        params.x = newX;
                        params.y = newY;
                        windowManager.updateViewLayout(floatView, params);
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatView, params);
        } catch (Exception e) {
            Log.e(TAG, "添加悬浮窗失败", e);
            Toast.makeText(this, "添加悬浮窗失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.autopager.UPDATE_COUNT");
        filter.addAction("com.example.autopager.UPDATE_ALPHA");
        registerReceiver(countReceiver, new IntentFilter("com.example.autopager.UPDATE_COUNT"));
        registerReceiver(alphaReceiver, new IntentFilter("com.example.autopager.UPDATE_ALPHA"));
    }

    public void updateCount(int cur, int total) {
        this.currentCount = cur;
        this.totalCount = total;
        if (floatView != null) {
            TextView tvCount = floatView.findViewById(R.id.tv_count);
            tvCount.setText(currentCount + "/" + totalCount);
        }
    }

    public void updateAlpha(int alphaPercent) {
        if (floatView != null) {
            float alpha = Math.max(0, Math.min(alphaPercent, 100)) / 100f;
            floatView.setAlpha(alpha);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + AutoPageService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        return enabledServices != null && enabledServices.contains(serviceName);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        Toast.makeText(this, "请在无障碍服务中启用AutoPageService", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatView != null) {
            try {
                windowManager.removeView(floatView);
            } catch (Exception e) {
                Log.e(TAG, "移除悬浮窗失败", e);
            }
        }
        unregisterReceiver(countReceiver);
        unregisterReceiver(alphaReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}