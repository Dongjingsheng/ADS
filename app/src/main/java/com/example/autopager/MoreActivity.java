package com.example.autopager;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MoreActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);

        LinearLayout llFeedback = findViewById(R.id.ll_feedback);
        llFeedback.setOnClickListener(v -> {
            Toast.makeText(this, "如有问题请联系123@163.com", Toast.LENGTH_SHORT).show();
        });

        TextView tvVersion = findViewById(R.id.tv_version);
        tvVersion.setText("当前版本：V1.0.0");

        LinearLayout llProtocol = findViewById(R.id.ll_protocol);
        llProtocol.setOnClickListener(v -> {
            Toast.makeText(this, "支持我们", Toast.LENGTH_SHORT).show();
        });

        Button btnExit = findViewById(R.id.btn_exit);
        btnExit.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });
    }
}