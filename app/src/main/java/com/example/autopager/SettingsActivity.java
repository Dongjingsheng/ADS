package com.example.autopager;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SeekBar seekBarAlpha;
    private TextView tvAlphaPercent;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sp = getSharedPreferences("autopager", MODE_PRIVATE);

        int alpha = sp.getInt("float_alpha", 80);

        seekBarAlpha = findViewById(R.id.seekbar_alpha);
        tvAlphaPercent = findViewById(R.id.tv_alpha_percent);
        seekBarAlpha.setProgress(alpha);
        tvAlphaPercent.setText(alpha + "%");

        seekBarAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvAlphaPercent.setText(progress + "%");
                sp.edit().putInt("float_alpha", progress).apply();

                Intent intent = new Intent("com.example.autopager.UPDATE_ALPHA");
                intent.putExtra("alpha", progress);
                sendBroadcast(intent);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        findViewById(R.id.tv_back).setOnClickListener(v -> finish());
    }
}