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
    private Button btnScan, btnBack, btnGI, btnRBC, btnResp;

    private EditText etSpecimenNo, etPatientType, etPatientName, etPatientAge, etPhone;
    // 将 etPatientGender 改为 spPatientGender
    private Spinner spPatientGender;
    private TextView tvCurrentDate, tvApplyTime;
    private EditText etApplyDoctor, etApplyDept,etHemoglobin;
    private Spinner spSubstrate;

    private AppDatabase db;
    private UsbSerialHelper usbHelper;
    private JSONObject currentPatientJson;
    private boolean isScanned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_analysis);

        initViews();
        requestCameraPermission();
        initDatabase();
        initUsbSerial();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        tvCurrentDate.setText(sdf.format(new Date()));
        tvApplyTime.setText(sdf.format(new Date()));
    }

    private void initViews() {
        etScanResult = findViewById(R.id.etScanResult);
        btnScan = findViewById(R.id.btnScan);
        btnBack = findViewById(R.id.btnBack);
        btnGI = findViewById(R.id.btnGI);
        btnRBC = findViewById(R.id.btnRBC);
        btnResp = findViewById(R.id.btnResp);

        etSpecimenNo = findViewById(R.id.etSpecimenNo);
        etPatientType = findViewById(R.id.etPatientType);
        etPatientName = findViewById(R.id.etPatientName);
        spPatientGender = findViewById(R.id.spPatientGender);
        etPatientAge = findViewById(R.id.etPatientAge);
        etPhone = findViewById(R.id.etPhone);
        spSubstrate = findViewById(R.id.spSubstrate);

        tvCurrentDate = findViewById(R.id.tvCurrentDate);
        tvApplyTime = findViewById(R.id.tvApplyTime);
        etApplyDoctor = findViewById(R.id.etApplyDoctor);
        etApplyDept = findViewById(R.id.etApplyDept);
        etHemoglobin = findViewById(R.id.etHemoglobin);

        btnScan.setOnClickListener(v -> startScan());
        btnBack.setOnClickListener(v -> finish());

        btnGI.setOnClickListener(v -> onTestClick("胃肠道疾病检测", "7E 20 00 00 00 20 7E", Test1Activity.class));
        btnRBC.setOnClickListener(v -> onTestClick("红细胞寿命检测", "7E 30 00 00 00 30 7E", Test2Activity.class));
        btnResp.setOnClickListener(v -> onTestClick("呼吸道疾病检测", "7E 40 00 00 00 40 7E", Test3Activity.class));
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
            return;
        }
        @SuppressWarnings("deprecation")
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("");
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
                etScanResult.setText(scanText);
                isScanned = true;
                parseAndFillPatientInfo(scanText);
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
                startScan();
            } else {
                Toast.makeText(this, "相机权限被拒绝，无法扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void parseAndFillPatientInfo(String jsonStr) {
        try {
            currentPatientJson = new JSONObject(jsonStr);
            JSONObject json = new JSONObject(jsonStr);
            etSpecimenNo.setText(currentPatientJson.optString("specimenNo", ""));
            etPatientType.setText(currentPatientJson.optString("patientType", ""));
            etPatientName.setText(currentPatientJson.optString("name", ""));
            etPatientAge.setText(currentPatientJson.optString("age", ""));
            etPhone.setText(currentPatientJson.optString("phone", ""));
            etApplyDoctor.setText(currentPatientJson.optString("applyDoctor", ""));
            etApplyDept.setText(currentPatientJson.optString("applyDept", ""));
            String applyTime = currentPatientJson.optString("applyTime", "");
            String gender = json.optString("gender", "");
            if (!gender.isEmpty()) {
                String[] genders = getResources().getStringArray(R.array.gender_array);
                for (int i = 0; i < genders.length; i++) {
                    if (genders[i].equals(gender)) {
                        spPatientGender.setSelection(i);
                        break;
                    }
                }
            }
            if (!applyTime.isEmpty()) {
                tvApplyTime.setText(applyTime);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "二维码格式错误，请手动输入", Toast.LENGTH_SHORT).show();
        }
    }

    private void fillMockPatientInfo(String input) {
        etSpecimenNo.setText(input);
        etPatientType.setText("门诊");
        etPatientName.setText("模拟患者");
        // 设置性别 Spinner 选中“男”
        String[] genders = getResources().getStringArray(R.array.gender_array);
        for (int i = 0; i < genders.length; i++) {
            if ("男".equals(genders[i])) {
                spPatientGender.setSelection(i);
                break;
            }
        }
        etPatientAge.setText("30");
        etPhone.setText("13800138000");
        etApplyDoctor.setText("张医生");
        etApplyDept.setText("内科");
        etHemoglobin.setText("140"); // 默认值
        isScanned = false;
        Toast.makeText(this, "已生成模拟患者信息", Toast.LENGTH_SHORT).show();
    }

    private void onTestClick(String testName, String hexCmd, Class<?> targetClass) {
        if (!isScanned && TextUtils.isEmpty(etScanResult.getText().toString())) {
            Toast.makeText(this, "请扫码或输入条形码号", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isScanned && !TextUtils.isEmpty(etScanResult.getText().toString())) {
            fillMockPatientInfo(etScanResult.getText().toString());
        }

        if (TextUtils.isEmpty(etSpecimenNo.getText())) {
            Toast.makeText(this, "请输入条形码号", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(etPatientName.getText())) {
            Toast.makeText(this, "请输入患者姓名", Toast.LENGTH_SHORT).show();
            return;
        }

        final String name = etPatientName.getText().toString();
        final String gender = spPatientGender.getSelectedItem().toString();
        final String patientType = etPatientType.getText().toString();
        final int age;
        try {
            age = Integer.parseInt(etPatientAge.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "年龄格式错误，请填写数字", Toast.LENGTH_SHORT).show();
            return;
        }
        final String phone = etPhone.getText().toString();
        final String applyDoctor = etApplyDoctor.getText().toString();
        final String applyDept = etApplyDept.getText().toString();
        final String specimenNo = etSpecimenNo.getText().toString();
        // 获取底物
        String substrate = spSubstrate.getSelectedItem().toString();
        // 获取血红蛋白总量
        String hemoglobinStr = etHemoglobin.getText().toString();
        if (TextUtils.isEmpty(hemoglobinStr)) {
            Toast.makeText(this, "请输入血红蛋白总量", Toast.LENGTH_SHORT).show();
            return;
        }
        float hemoglobin;
        try {
            hemoglobin = Float.parseFloat(hemoglobinStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "血红蛋白总量格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            // 保存患者
            Patient patient = new Patient();
            patient.setName(name);
            patient.setGender(gender);
            patient.setPatientType(patientType);
            patient.setAge(age);
            patient.setPhone(phone);
            patient.setIdCard("");
            patient.setCreatedTime(System.currentTimeMillis());
            patient.setUpdatedTime(System.currentTimeMillis());

            long patientId = db.patientDao().insert(patient);

            // 保存报告
            TestReport report = new TestReport();
            report.setPatientId(patientId);
            report.setTestType(testName);
            report.setTestData("");
            report.setDoctorName(applyDoctor);
            report.setRemarks(applyDept);  // 申请科室存入remarks
            report.setTestDate(System.currentTimeMillis());
            report.setReportNumber("RP" + System.currentTimeMillis());
            report.setTestResult("");
            report.setPatientInfo("");
            report.setDetectionDataChart("");
            report.setDiagnosisResult("");
            report.setPdfFileName("");
            report.setPdfUri("");
            report.setCreatedTime(System.currentTimeMillis());

            long reportId = db.testReportDao().insert(report);

            // 发送USB指令
            if (usbHelper != null && usbHelper.isConnected()) {
                byte[] cmd = hexStringToByteArray(hexCmd);
                usbHelper.sendBytes(cmd);
            }

            runOnUiThread(() -> {
                if (usbHelper != null && usbHelper.isConnected()) {
                    Toast.makeText(DetectAnalysisActivity.this, "已发送指令：" + hexCmd, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DetectAnalysisActivity.this, "USB未连接，无法发送指令", Toast.LENGTH_SHORT).show();
                }

                Intent intent = new Intent(DetectAnalysisActivity.this, targetClass);
                intent.putExtra("patientId", patientId);
                intent.putExtra("reportId", reportId);
                intent.putExtra("patientName", name);
                intent.putExtra("specimenNo", specimenNo);
                intent.putExtra("patientGender", gender);
                intent.putExtra("patientAge", age);
                intent.putExtra("substrate", substrate);
                intent.putExtra("hemoglobin", hemoglobin);
                startActivity(intent);
            });
        }).start();
    }

    private byte[] hexStringToByteArray(String hex) {
        String[] parts = hex.split(" ");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbHelper != null) usbHelper.disconnect();
    }
}
