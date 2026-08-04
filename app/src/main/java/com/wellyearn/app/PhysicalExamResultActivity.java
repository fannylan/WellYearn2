package com.wellyearn.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class PhysicalExamResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physical_exam_result);

        // 可获取传递的patientId等，后续实现数据接收和展示
        long patientId = getIntent().getLongExtra("patientId", -1);
        long reportId = getIntent().getLongExtra("reportId", -1);
        String substrate = getIntent().getStringExtra("substrate");
        // 后续可以在这里初始化USB接收体检结果
    }
}