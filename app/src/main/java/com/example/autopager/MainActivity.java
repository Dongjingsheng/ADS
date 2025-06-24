package com.example.autopager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnAccessibility, btnOverlay, btnShowPanel;
    private Spinner spinnerDirection;
    private EditText etStayTime, etMaxPage, etSlideTime;
    private Switch switchRandom;

    private String[] directions = {"下滑", "上滑", "左滑", "右滑"};
    private String[] directionsValue = {"down", "up", "left", "right"};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.tv_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        findViewById(R.id.tv_more).setOnClickListener(v -> {
            startActivity(new Intent(this, MoreActivity.class));
        });


        btnAccessibility = findViewById(R.id.btn_accessibility);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnShowPanel = findViewById(R.id.btn_show_panel);
        spinnerDirection = findViewById(R.id.spinner_direction);
        etStayTime = findViewById(R.id.et_stay_time);
        etMaxPage = findViewById(R.id.et_max_page);
        etSlideTime = findViewById(R.id.et_slide_time);
        switchRandom = findViewById(R.id.switch_random);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, directions);
        spinnerDirection.setAdapter(adapter);

        btnAccessibility.setOnClickListener(v -> PermissionUtils.requestAccessibilityPermission(this));
        btnOverlay.setOnClickListener(v -> PermissionUtils.requestOverlayPermission(this));

        btnShowPanel.setOnClickListener(v -> {
            saveSettings();
            if (!PermissionUtils.isServiceEnabled(this, AutoPageService.class)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            startService(new Intent(this, FloatWindowService.class));
        });

        loadSettings();
    }

    private void saveSettings() {
        getSharedPreferences("autopager", MODE_PRIVATE).edit()
                .putString("direction", directionsValue[spinnerDirection.getSelectedItemPosition()])
                .putInt("stayTime", parseInt(etStayTime.getText().toString(), 5))
                .putInt("maxPage", parseInt(etMaxPage.getText().toString(), 20))
                .putInt("slideTime", parseInt(etSlideTime.getText().toString(), 300))
                .putBoolean("random", switchRandom.isChecked())
                .apply();
    }

    private void loadSettings() {
        SharedPreferences sp = getSharedPreferences("autopager", MODE_PRIVATE);
        String dir = sp.getString("direction", "down");
        int idx = 0;
        for (int i = 0; i < directionsValue.length; i++) {
            if (directionsValue[i].equals(dir)) {
                idx = i;
                break;
            }
        }
        spinnerDirection.setSelection(idx);
        etStayTime.setText(String.valueOf(sp.getInt("stayTime", 5)));
        etMaxPage.setText(String.valueOf(sp.getInt("maxPage", 20)));
        etSlideTime.setText(String.valueOf(sp.getInt("slideTime", 300)));
        switchRandom.setChecked(sp.getBoolean("random", false));
    }

    private int parseInt(String s, int def) {
        if (TextUtils.isEmpty(s)) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnAccessibility.setText(PermissionUtils.isServiceEnabled(this, AutoPageService.class) ? "已开启" : "未开启");
        btnOverlay.setText(Settings.canDrawOverlays(this) ? "已开启" : "未开启");
    }
}