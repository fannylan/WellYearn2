package com.wellyearn.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
// 新增导入
import android.widget.CheckBox;

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

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PhysicalExamActivity extends AppCompatActivity {

    private EditText etScanResult, etSpecimenNo, etName, etAge, etHemoglobin, etPhone;
    private Spinner spGender;
    private TextView tvCurrentDate, tvDeviceStatus;
    private Button btnScan, btnBack, btnStartExam;
    private Spinner spSubstrate;

    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private CheckBox chkCH4, chkH2, chkCO, chkNO;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private int deviceStatus = -1;
    private boolean isDeviceReady = false;
    private boolean isExamStarted = false;

    private static final int POLL_TIMEOUT_MS = 5000;
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int SCANNER_INPUT_SETTLE_MS = 200;
    private static final String START_EXAM_TEXT = "开始体检";
    private static final String STARTING_EXAM_TEXT = "正在启动...";

    private final Runnable scannerInputCommitRunnable = this::commitScannerInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physical_exam);

        initViews();
        initDatabase();
        initUsbSerial();
        requestCameraPermission();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        tvCurrentDate.setText(sdf.format(new Date()));
    }

    private void initViews() {
        etScanResult = findViewById(R.id.etScanResult);
        etSpecimenNo = findViewById(R.id.etSpecimenNo);
        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etHemoglobin = findViewById(R.id.etHemoglobin);
        etPhone = findViewById(R.id.etPhone);
        spGender = findViewById(R.id.spGender);
        chkCH4 = findViewById(R.id.chkCH4);
        chkH2 = findViewById(R.id.chkH2);
        chkCO = findViewById(R.id.chkCO);
        chkNO = findViewById(R.id.chkNO);
        tvCurrentDate = findViewById(R.id.tvCurrentDate);
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        btnScan = findViewById(R.id.btnScan);
        btnBack = findViewById(R.id.btnBack);
        btnStartExam = findViewById(R.id.btnStartExam);
        spSubstrate = findViewById(R.id.spSubstrate);

        initScannerInput();

        btnStartExam.setEnabled(false);
        btnStartExam.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.GRAY));

        btnScan.setOnClickListener(v -> startScan());
        btnBack.setOnClickListener(v -> finish());
        btnStartExam.setOnClickListener(v -> {
            if (isExamStarted) return;
            if (!isDeviceReady) {
                Toast.makeText(this, "设备未就绪，请稍后", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!chkCH4.isChecked() && !chkH2.isChecked()
                    && !chkCO.isChecked() && !chkNO.isChecked()) {
                Toast.makeText(this, "请至少选择一项检测", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(etSpecimenNo.getText())) {
                Toast.makeText(this, "请输入标本编号", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(etName.getText())) {
                Toast.makeText(this, "请输入姓名", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(etAge.getText())) {
                Toast.makeText(this, "请输入年龄", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(etHemoglobin.getText())) {
                Toast.makeText(this, "请输入全身血红蛋白总量", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(etPhone.getText())) {
                Toast.makeText(this, "请输入联系电话", Toast.LENGTH_SHORT).show();
                return;
            }
            startPhysicalExam();
        });
    }

    private void initScannerInput() {
        etScanResult.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mainHandler.removeCallbacks(scannerInputCommitRunnable);
                if (!TextUtils.isEmpty(editable.toString().trim())) {
                    // 扫码枪通常会快速连续输入；短暂停顿后再按完整内容解析。
                    mainHandler.postDelayed(scannerInputCommitRunnable, SCANNER_INPUT_SETTLE_MS);
                }
            }
        });

        etScanResult.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                commitScannerInput();
                return true;
            }
            return false;
        });
    }

    private void commitScannerInput() {
        mainHandler.removeCallbacks(scannerInputCommitRunnable);
        String scanText = etScanResult.getText().toString().trim();
        if (!scanText.isEmpty()) {
            parseAndFillPatientInfo(scanText);
        }
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(this);
    }

    private void initUsbSerial() {
        usbHelper = new UsbSerialHelper(this);
        usbHelper.setByteDataListener(data -> parseDeviceStatus(data));
        usbHelper.scanAndConnect();

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (usbHelper.isConnected()) {
                    sendPollCommand();
                } else {
                    mainHandler.postDelayed(this, 2000);
                }
            }
        }, 1000);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan(); // 权限授予后自动启动扫码
                Toast.makeText(this, "相机权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "相机权限被拒绝，无法扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @SuppressWarnings("deprecation")
    private void startScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                Log.d("Scan", "可用相机: " + cameraId);
            }
        } catch (CameraAccessException e) {
            Log.e("Scan", "无法访问相机服务", e);
        }

        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("");
        integrator.setCameraId(-1);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        @SuppressWarnings("deprecation")
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                String scanText = result.getContents();
                etScanResult.setText(scanText);
                commitScannerInput();
            } else {
                Toast.makeText(this, "扫码取消", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void parseAndFillPatientInfo(String scanText) {
        String scanValue = scanText == null ? "" : scanText.trim();
        if (scanValue.isEmpty()) {
            return;
        }

        // 普通条码直接作为标本编号；JSON 二维码则同时填充患者信息。
        if (!scanValue.startsWith("{")) {
            etSpecimenNo.setText(scanValue);
            return;
        }

        try {
            JSONObject json = new JSONObject(scanValue);
            String specimenNo = json.optString("specimenNo", "").trim();
            if (specimenNo.isEmpty()) {
                specimenNo = json.optString("barcode", "").trim();
            }
            if (specimenNo.isEmpty()) {
                specimenNo = json.optString("specimenCode", "").trim();
            }
            etSpecimenNo.setText(specimenNo.isEmpty() ? scanValue : specimenNo);
            etName.setText(json.optString("name", ""));
            String gender = json.optString("gender", "");
            if (!gender.isEmpty()) {
                String[] genders = getResources().getStringArray(R.array.gender_array);
                for (int i = 0; i < genders.length; i++) {
                    if (genders[i].equals(gender)) {
                        spGender.setSelection(i);
                        break;
                    }
                }
            }
            etAge.setText(json.optString("age", ""));
            etHemoglobin.setText(json.optString("hemoglobin", ""));
            etPhone.setText(json.optString("phone", ""));
        } catch (JSONException e) {
            Log.w("PhysicalExam", "Failed to parse scanned patient JSON", e);
            etSpecimenNo.setText(scanValue);
            Toast.makeText(this, "已将扫码内容填入标本编号，请补充患者信息", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendPollCommand() {
        byte[] cmdId = {(byte) 0x0A, (byte) 0x0A};
        byte[] frame = buildFrame(cmdId, null);
        usbHelper.sendBytes(frame);
        mainHandler.postDelayed(() -> {
            if (deviceStatus == -1) {
                tvDeviceStatus.setText("设备状态：获取超时");
                tvDeviceStatus.setTextColor(Color.RED);
            }
        }, POLL_TIMEOUT_MS);
    }

    private void parseDeviceStatus(byte[] data) {
        byte[] raw = removeEscape(data);
        if (raw == null || raw.length < 8) return;
        if (raw[0] != 0x7E || raw[raw.length - 1] != 0x7E) return;

        int msgId = ((raw[1] & 0xFF) << 8) | (raw[2] & 0xFF);
        if (msgId != 0x1A0A) return;

        int status = raw[5] & 0xFF;
        deviceStatus = status;
        runOnUiThread(() -> {
            String statusText;
            int color;
            boolean ready = false;
            switch (status) {
                case 0: statusText = "初始化中"; color = Color.GRAY; break;
                case 4: statusText = "空闲已就位"; color = Color.GREEN; ready = true; break;
                case 2: statusText = "检测中"; color = Color.parseColor("#9C27B0"); break;
                case 3: statusText = "校准中"; color = Color.parseColor("#FFEB3B"); break;
                case 1: statusText = "故障"; color = Color.RED; break;
                default: statusText = "未知"; color = Color.GRAY;
            }
            tvDeviceStatus.setText("设备状态：" + statusText);
            tvDeviceStatus.setTextColor(color);

            if (ready) {
                isDeviceReady = true;
                if (!isExamStarted) {
                    btnStartExam.setEnabled(true);
                    btnStartExam.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                }
            } else {
                isDeviceReady = false;
                btnStartExam.setEnabled(false);
                btnStartExam.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.GRAY));
            }
        });
    }

    private void startPhysicalExam() {
        final int patientAge;
        try {
            patientAge = Integer.parseInt(etAge.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "年龄格式错误，请填写数字", Toast.LENGTH_SHORT).show();
            return;
        }

        final float totalHemoglobin;
        try {
            totalHemoglobin = Float.parseFloat(etHemoglobin.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "全身血红蛋白总量格式错误，请填写数字", Toast.LENGTH_SHORT).show();
            return;
        }

        isExamStarted = true;
        btnStartExam.setEnabled(false);
        btnStartExam.setText(STARTING_EXAM_TEXT);
        btnStartExam.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.GRAY));

        final String patientName = etName.getText().toString().trim();
        final String phone = etPhone.getText().toString().trim();
        final String specimenNo = etSpecimenNo.getText().toString().trim();
        final String gender = spGender.getSelectedItem().toString();
        final String substrate = spSubstrate.getSelectedItem().toString();
        // 获取复选框状态
        final boolean isCH4 = chkCH4.isChecked();
        final boolean isH2 = chkH2.isChecked();
        final boolean isCO = chkCO.isChecked();
        final boolean isNO = chkNO.isChecked();
        final PhysicalExamSelectionRouter.Detection firstDetection =
                PhysicalExamSelectionRouter.firstSelected(isCH4, isH2, isCO, isNO);

        new Thread(() -> {
            try {
                Patient patient = new Patient();
                patient.setName(patientName);
                patient.setGender(gender);
                patient.setAge(patientAge);
                patient.setPhone(phone);
                patient.setIdCard("");
                patient.setPatientType("体检模式");
                patient.setCreatedTime(System.currentTimeMillis());
                patient.setUpdatedTime(System.currentTimeMillis());

                final long[] insertedIds = {-1L, -1L};
                db.runInTransaction(() -> {
                    long patientId = db.patientDao().insert(patient);
                    insertedIds[0] = patientId;
                    insertedIds[1] = insertInitialReport(
                            patientId, "体检诊断报告", "PE");
                });
                final long patientId = insertedIds[0];
                final long physicalExamReportId = insertedIds[1];

                String startCommand = PhysicalExamSelectionRouter.commandFor(firstDetection);
                byte[] startCmd = hexStringToByteArray(startCommand);
                if (!usbHelper.isConnected()) {
                    throw new IllegalStateException("USB未连接，无法发送检测指令");
                }
                usbHelper.sendBytes(startCmd);
                // 释放串口后再进入检测页，由检测页负责接收本次检测数据。
                usbHelper.disconnect();

                final Class<?> targetActivity = PhysicalExamResultActivity.class;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(this, "已发送启动指令：" + startCommand,
                            Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(PhysicalExamActivity.this, targetActivity);
                    intent.putExtra("patientId", patientId);
                    intent.putExtra("reportId", physicalExamReportId);
                    intent.putExtra("patientName", patientName);
                    intent.putExtra("specimenNo", specimenNo);
                    intent.putExtra("substrate", substrate);
                    intent.putExtra("patientAge", patientAge);
                    intent.putExtra("hemoglobin", totalHemoglobin);
                    intent.putExtra(Test2Activity.EXTRA_PHYSICAL_EXAM_MODE, true);
                    intent.putExtra("chkCH4", isCH4);
                    intent.putExtra("chkH2", isH2);
                    intent.putExtra("chkCO", isCO);
                    intent.putExtra("chkNO", isNO);
                    PhysicalExamFlowCoordinator.putFlowState(intent, physicalExamReportId);

                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                Log.e("PhysicalExam", "Failed to save and start physical exam", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    isExamStarted = false;
                    btnStartExam.setText(START_EXAM_TEXT);
                    btnStartExam.setEnabled(isDeviceReady);
                    btnStartExam.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                    isDeviceReady ? Color.parseColor("#4CAF50") : Color.GRAY));
                    String message = e.getMessage();
                    Toast.makeText(this,
                            "体检启动失败" + (TextUtils.isEmpty(message) ? "" : "：" + message),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "physical-exam-save").start();
    }

    private long insertInitialReport(long patientId, String testType, String numberPrefix) {
        long now = System.currentTimeMillis();
        TestReport report = new TestReport();
        report.setPatientId(patientId);
        report.setTestType(testType);
        report.setTestData("");
        report.setDoctorName("体检科");
        report.setRemarks("");
        report.setTestDate(now);
        report.setReportNumber(numberPrefix + now);
        report.setTestResult("");
        report.setPatientInfo("");
        report.setDetectionDataChart("");
        report.setDiagnosisResult("");
        report.setPdfFileName("");
        report.setPdfUri("");
        report.setCreatedTime(now);
        return db.testReportDao().insert(report);
    }

    private byte[] buildFrame(byte[] commandId, byte[] body) {
        int bodyLen = (body == null) ? 0 : body.length;
        byte[] header = new byte[4 + bodyLen];
        System.arraycopy(commandId, 0, header, 0, 2);
        int attr = bodyLen & 0x03FF;
        header[2] = (byte) ((attr >> 8) & 0xFF);
        header[3] = (byte) (attr & 0xFF);
        if (body != null && bodyLen > 0) {
            System.arraycopy(body, 0, header, 4, bodyLen);
        }
        byte checksum = 0;
        for (byte b : header) checksum ^= b;
        byte[] dataToEscape = new byte[header.length + 1];
        System.arraycopy(header, 0, dataToEscape, 0, header.length);
        dataToEscape[header.length] = checksum;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x7E);
        for (byte b : dataToEscape) {
            if (b == 0x7E) { baos.write(0x7D); baos.write(0x02); }
            else if (b == 0x7D) { baos.write(0x7D); baos.write(0x01); }
            else { baos.write(b); }
        }
        baos.write(0x7E);
        return baos.toByteArray();
    }

    private byte[] removeEscape(byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0x7D && i + 1 < data.length) {
                if (data[i + 1] == 0x01) { baos.write(0x7D); i++; }
                else if (data[i + 1] == 0x02) { baos.write(0x7E); i++; }
                else { baos.write(data[i]); }
            } else {
                baos.write(data[i]);
            }
        }
        return baos.toByteArray();
    }

    private byte[] hexStringToByteArray(String s) {
        String[] hex = s.split(" ");
        byte[] bytes = new byte[hex.length];
        for (int i = 0; i < hex.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex[i], 16);
        }
        return bytes;
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(scannerInputCommitRunnable);
        super.onDestroy();
        if (usbHelper != null) usbHelper.disconnect();
    }
}
