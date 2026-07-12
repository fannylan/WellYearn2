package com.wellyearn.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Patient;
import com.wellyearn.app.database.entity.TestReport;
import com.wellyearn.app.usb.UsbSerialHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetectAnalysisActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private EditText etScanResult;
    private Button btnScan, btnBack, btnTest1, btnTest2,btnTest3;

    // 左侧控件
    private TextView tvSpecimenNo, tvPatientName, tvPatientGender, tvPatientAge, tvPatientNo, tvPhone;
    private Spinner spPatientType;

    // 右侧控件
    private TextView tvCurrentDate, tvApplyTime;
    private Spinner spTestType, spSubstrate;
    private EditText etApplyDoctor, etApplyDept, etTestDoctor, etRemarks;

    private AppDatabase db;
    private UsbSerialHelper usbHelper;
    private JSONObject currentPatientJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_analysis);

        initViews();
        requestCameraPermission();
        initDatabase();
        initUsbSerial();

        // 显示当前日期
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        tvCurrentDate.setText(sdf.format(new Date()));
    }

    private void initViews() {
        etScanResult = findViewById(R.id.etScanResult);
        btnScan = findViewById(R.id.btnScan);
        btnBack = findViewById(R.id.btnBack);
        btnTest1 = findViewById(R.id.btnTest1);
        btnTest2 = findViewById(R.id.btnTest2);
        btnTest3 = findViewById(R.id.btnTest3);


        // 左侧
        tvSpecimenNo = findViewById(R.id.tvSpecimenNo);
        tvPatientName = findViewById(R.id.tvPatientName);
        tvPatientGender = findViewById(R.id.tvPatientGender);
        tvPatientAge = findViewById(R.id.tvPatientAge);
        tvPatientNo = findViewById(R.id.tvPatientNo);
        tvPhone = findViewById(R.id.tvPhone);
        spPatientType = findViewById(R.id.spPatientType);

        // 右侧
        tvCurrentDate = findViewById(R.id.tvCurrentDate);
        tvApplyTime = findViewById(R.id.tvApplyTime);
        spTestType = findViewById(R.id.spTestType);
        spSubstrate = findViewById(R.id.spSubstrate);
        etApplyDoctor = findViewById(R.id.etApplyDoctor);
        etApplyDept = findViewById(R.id.etApplyDept);
        etTestDoctor = findViewById(R.id.etTestDoctor);
        etRemarks = findViewById(R.id.etRemarks);

        btnScan.setOnClickListener(v -> startScan());
        btnBack.setOnClickListener(v -> finish());
        btnTest1.setOnClickListener(v -> onTestClick("测试1"));
        btnTest2.setOnClickListener(v -> onTestClick("测试2"));
        btnTest3.setOnClickListener(v -> onTestClick("测试3"));
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(this);
    }

    private void initUsbSerial() {
        usbHelper = new UsbSerialHelper(this);
        usbHelper.scanAndConnect();
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    private void startScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("");  // 无提示文字
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                String scanText = result.getContents();
                etScanResult.setText(scanText);  // 覆盖原有结果
                parsePatientInfo(scanText);
            } else {
                Toast.makeText(this, "扫码取消", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，可扫码
            } else {
                Toast.makeText(this, "相机权限被拒绝，无法扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void parsePatientInfo(String jsonStr) {
        try {
            currentPatientJson = new JSONObject(jsonStr);
            tvSpecimenNo.setText(currentPatientJson.optString("specimenNo", ""));
            tvPatientName.setText(currentPatientJson.optString("name", ""));
            tvPatientGender.setText(currentPatientJson.optString("gender", ""));
            tvPatientAge.setText(currentPatientJson.optString("age", ""));
            tvPatientNo.setText(currentPatientJson.optString("patientNo", ""));
            tvPhone.setText(currentPatientJson.optString("phone", ""));
            tvApplyTime.setText(currentPatientJson.optString("applyTime", ""));
            // 患者类型通过 Spinner 手动选择，不从二维码中读取
        } catch (JSONException e) {
            e.printStackTrace();
            // 解析失败时，使用模拟患者信息
            useMockPatientInfo();
            Toast.makeText(this, "二维码格式错误，已使用模拟患者信息", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 填充模拟患者数据（用于调试或非标准二维码）
     */
    private void useMockPatientInfo() {
        // 构造一个模拟的 JSONObject（可选）
        try {
            currentPatientJson = new JSONObject();
            currentPatientJson.put("specimenNo", "MOCK001");
            currentPatientJson.put("name", "模拟患者");
            currentPatientJson.put("gender", "男");
            currentPatientJson.put("age", "45");
            currentPatientJson.put("patientNo", "P99999");
            currentPatientJson.put("phone", "13800000000");
            currentPatientJson.put("applyTime", "2025-04-20 10:30:00");
        } catch (JSONException ex) {
            currentPatientJson = null;
        }

        // 同时更新界面上的 TextView
        tvSpecimenNo.setText("MOCK001");
        tvPatientName.setText("模拟患者");
        tvPatientGender.setText("男");
        tvPatientAge.setText("45");
        tvPatientNo.setText("P99999");
        tvPhone.setText("13800000000");
        tvApplyTime.setText("2025-04-20 10:30:00");
    }



    private void onTestClick(String testCommand) {
        if (TextUtils.isEmpty(etScanResult.getText().toString())) {
            Toast.makeText(this, "请先扫描患者二维码", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确保有患者数据（如果 currentPatientJson 为空，尝试解析或模拟）
        if (currentPatientJson == null) {
            parsePatientInfo(etScanResult.getText().toString());
        }

        // 在后台线程执行数据库操作
        new Thread(() -> {
            // 保存患者信息
            Patient patient = new Patient();
            patient.setName(tvPatientName.getText().toString());
            patient.setGender(tvPatientGender.getText().toString());
            patient.setPatientType(spPatientType.getSelectedItem().toString());
            try {
                int age = Integer.parseInt(tvPatientAge.getText().toString());
                patient.setAge(age);
            } catch (NumberFormatException e) {
                patient.setAge(0);
            }
            patient.setPhone(tvPhone.getText().toString());
            patient.setIdCard("");
            patient.setCreatedTime(System.currentTimeMillis());
            patient.setUpdatedTime(System.currentTimeMillis());

            long patientId = db.patientDao().insert(patient);

            // 保存检测报告
            TestReport report = new TestReport();
            report.setPatientId(patientId);
            report.setTestType(spTestType.getSelectedItem().toString());
            report.setTestData(spSubstrate.getSelectedItem().toString());
            report.setDoctorName(etTestDoctor.getText().toString());
            report.setRemarks(etRemarks.getText().toString());
            report.setTestDate(System.currentTimeMillis());
            report.setReportNumber("RP" + System.currentTimeMillis());
            report.setTestResult("");
            report.setCreatedTime(System.currentTimeMillis());

            long reportId = db.testReportDao().insert(report);

            // 回到主线程处理 USB 通信和页面跳转
            runOnUiThread(() -> {
                // 发送指令到单片机
                if (usbHelper != null && usbHelper.isConnected()) {
                    usbHelper.sendData(testCommand);
                    Toast.makeText(DetectAnalysisActivity.this, "已向单片机发送指令：" + testCommand, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DetectAnalysisActivity.this, "USB未连接，无法发送指令", Toast.LENGTH_SHORT).show();
                }

                // 跳转到对应的测试页面
                Class<?> targetClass;
                switch (testCommand) {
                    case "测试1":
                        targetClass = Test1Activity.class;
                        break;
                    case "测试2":
                        targetClass = Test2Activity.class;
                        break;
                    case "测试3":
                        targetClass = Test3Activity.class;
                        break;
                    default:
                        targetClass = Test1Activity.class;
                }
                Intent intent = new Intent(DetectAnalysisActivity.this, targetClass);
                intent.putExtra("patientId", patientId);
                intent.putExtra("reportId", reportId);
                intent.putExtra("patientName", tvPatientName.getText().toString());
                intent.putExtra("specimenNo", tvSpecimenNo.getText().toString());
                startActivity(intent);
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbHelper != null) {
            usbHelper.disconnect();
        }
    }
}