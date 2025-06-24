package com.example.autopager;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AutoPageService extends AccessibilityService {
    private static final String TAG = "AutoPageService";

    private Handler handler = new Handler(Looper.getMainLooper());
    private int pageCount = 0;
    private int maxPages = 20;
    private int stayTime = 5000; // ms
    private int slideTime = 500; // ms
    private String direction = "down";
    private boolean random = false;

    private boolean running = false;

    private Runnable pageTask = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                Log.d(TAG, "任务已停止");
                return;
            }
            if (pageCount < maxPages) {
                Log.d(TAG, "执行第 " + (pageCount + 1) + " 次翻页");
                performSlide();
                pageCount++;
                sendPageCountBroadcast(pageCount, maxPages);

                handler.postDelayed(this, stayTime + slideTime);
            } else {
                Log.d(TAG, "已达到最大翻页次数，停止任务");
                running = false;
                Toast.makeText(getApplicationContext(), "自动翻页完成", Toast.LENGTH_SHORT).show();
                sendPageCountBroadcast(pageCount, maxPages);
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");
    }

    public void startAutoPage(int maxPages, int stayTime, int slideTime, String direction, boolean random) {
        if (running) {
            Log.d(TAG, "任务已经在运行中，忽略开始命令");
            return;
        }
        this.maxPages = maxPages;
        this.stayTime = stayTime;
        this.slideTime = slideTime;
        this.direction = direction;
        this.random = random;
        this.pageCount = 0;
        running = true;
        Log.d(TAG, "开始自动翻页任务: 最大页数=" + maxPages +
                ", 停留时间=" + stayTime + "ms, 滑动时间=" + slideTime +
                "ms, 方向=" + direction + ", 随机=" + random);
        sendPageCountBroadcast(pageCount, maxPages);
        handler.post(pageTask);
        Toast.makeText(this, "开始自动翻页", Toast.LENGTH_SHORT).show();
    }

    public void stopAutoPage() {
        if (!running) {
            Log.d(TAG, "任务未运行，忽略停止命令");
            return;
        }
        Log.d(TAG, "停止自动翻页任务，已翻页 " + pageCount + " 次");
        running = false;
        handler.removeCallbacks(pageTask);
        pageCount = 0;
        sendPageCountBroadcast(pageCount, maxPages);
        Toast.makeText(this, "停止自动翻页", Toast.LENGTH_SHORT).show();
    }

    private void sendPageCountBroadcast(int cur, int total) {
        Intent intent = new Intent("com.example.autopager.UPDATE_COUNT");
        intent.putExtra("cur", cur);
        intent.putExtra("total", total);
        sendBroadcast(intent);
    }

    private void performSlide() {
        try {
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;

            int startX, startY, endX, endY;

            switch (direction) {
                case "down":
                    startX = width / 2;
                    startY = (int)(height * 0.25);
                    endX = width / 2;
                    endY = (int)(height * 0.75);
                    Log.d(TAG, "执行下滑手势");
                    break;
                case "up":
                    startX = width / 2;
                    startY = (int)(height * 0.75);
                    endX = width / 2;
                    endY = (int)(height * 0.25);
                    Log.d(TAG, "执行上滑手势");
                    break;
                case "left":
                    startX = (int)(width * 0.85);
                    startY = height / 2;
                    endX = (int)(width * 0.15);
                    endY = height / 2;
                    Log.d(TAG, "执行左滑手势");
                    break;
                case "right":
                    startX = (int)(width * 0.15);
                    startY = height / 2;
                    endX = (int)(width * 0.85);
                    endY = height / 2;
                    Log.d(TAG, "执行右滑手势");
                    break;
                default:
                    startX = width / 2;
                    startY = (int)(height * 0.25);
                    endX = width / 2;
                    endY = (int)(height * 0.75);
                    Log.d(TAG, "使用默认下滑手势（未知方向: " + direction + "）");
                    break;
            }

            if (random) {
                int randomRange = 40;
                startX += (int)(Math.random() * randomRange - randomRange/2);
                startY += (int)(Math.random() * randomRange - randomRange/2);
                endX += (int)(Math.random() * randomRange - randomRange/2);
                endY += (int)(Math.random() * randomRange - randomRange/2);
                Log.d(TAG, "添加随机扰动");
            }

            Path path = new Path();
            path.moveTo(startX, startY);

            if (random) {
                float controlX = (startX + endX) / 2 + (float)(Math.random() * 30 - 15);
                float controlY = (startY + endY) / 2 + (float)(Math.random() * 30 - 15);
                path.quadTo(controlX, controlY, endX, endY);
                Log.d(TAG, "使用曲线轨迹");
            } else {
                path.lineTo(endX, endY);
                Log.d(TAG, "使用直线轨迹");
            }

            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, slideTime);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            boolean result = dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "手势执行完成");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.e(TAG, "手势执行被取消");
                }
            }, null);

            if (!result) {
                Log.e(TAG, "手势执行失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "执行滑动时发生错误", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务被中断");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "start".equals(intent.getAction())) {
            try {
                SharedPreferences sp = getSharedPreferences("autopager", MODE_PRIVATE);
                startAutoPage(
                        sp.getInt("maxPage", 20),
                        sp.getInt("stayTime", 5) * 1000,
                        sp.getInt("slideTime", 300),
                        sp.getString("direction", "down"),
                        sp.getBoolean("random", false)
                );
            } catch (Exception e) {
                Log.e(TAG, "执行开始命令时出错", e);
            }
        } else if (intent != null && "stop".equals(intent.getAction())) {
            try {
                stopAutoPage();
            } catch (Exception e) {
                Log.e(TAG, "执行停止命令时出错", e);
            }
        }
        return START_STICKY;
    }
}