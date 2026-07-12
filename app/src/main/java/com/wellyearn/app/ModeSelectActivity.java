package com.wellyearn.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ModeSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        Button btnTestMode = findViewById(R.id.btnTestMode);
        Button btnPhysicalMode = findViewById(R.id.btnPhysicalMode);
        Button btnBack = findViewById(R.id.btnBack);

        btnTestMode.setOnClickListener(v -> {
            // 跳转到检验模式（原有检测分析页面）
            Intent intent = new Intent(ModeSelectActivity.this, DetectAnalysisActivity.class);
            startActivity(intent);
        });

        btnPhysicalMode.setOnClickListener(v -> {
            // 跳转到体检模式（新页面）
            Intent intent = new Intent(ModeSelectActivity.this, PhysicalExamActivity.class);
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> finish());
    }
}