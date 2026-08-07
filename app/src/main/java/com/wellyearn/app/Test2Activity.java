package com.wellyearn.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.report.RedBloodCellLifespanReportService;
import com.wellyearn.app.usb.UsbSerialHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Test2Activity extends AppCompatActivity {

    public static final String EXTRA_PHYSICAL_EXAM_MODE = "physicalExamMode";
    private static final String TAG = "Test2Activity";

    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress, textReceivedData, textResultInterpretation;
    private Button buttonBack, buttonReportManage;
    private BarChart barChart;
    private TableLayout tableSingleChannel;
    private TableRow singleChannelDataRow;

    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientNameStr, specimenNo;

    private float totalHemoglobin;
    private float lastCO = 0f;
    private float lastCO2 = 0f;
    private float lastCorrectionFactor = 0f;
    private float lastCorrectedCO = 0f;
    private float lastLifespanDays = 0f;
    private boolean physicalExamMode = false;
    private boolean detectionCompleted = false;
    private String generatedPdfUri;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test2);

        patientId = getIntent().getLongExtra("patientId", -1);
        reportId = getIntent().getLongExtra("reportId", -1);
        patientNameStr = getIntent().getStringExtra("patientName");
        specimenNo = getIntent().getStringExtra("specimenNo");
        totalHemoglobin = getIntent().getFloatExtra("hemoglobin", 0f);
        physicalExamMode = getIntent().getBooleanExtra(EXTRA_PHYSICAL_EXAM_MODE, false);

        initViews();
        initDatabase();
        initUsbSerial();
        initTable();

        textPatientName.setText("患者姓名：" + (patientNameStr != null ? patientNameStr : "--"));
        textSpecimenNo.setText("标本编号：" + (specimenNo != null ? specimenNo : "--"));
        updateCurrentTime();
        startDetectionProgress();
    }

    private void initViews() {
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textSpecimenNo = findViewById(R.id.textSpecimenNo);
        textPatientName = findViewById(R.id.textPatientName);
        textDetectionProgress = findViewById(R.id.textDetectionProgress);
        textReceivedData = findViewById(R.id.textReceivedData);
        textResultInterpretation = findViewById(R.id.textResultInterpretation);
        buttonBack = findViewById(R.id.buttonBack);
        buttonReportManage = findViewById(R.id.buttonReportManage);
        barChart = findViewById(R.id.barChart);
        tableSingleChannel = findViewById(R.id.tableSingleChannel);

        buttonBack.setOnClickListener(v -> finish());
        buttonReportManage.setOnClickListener(v -> {
            if (!detectionCompleted) {
                Toast.makeText(this, "检测未完成，无法查看报告", Toast.LENGTH_SHORT).show();
            } else if (generatedPdfUri == null || generatedPdfUri.isEmpty()) {
                Toast.makeText(this, "诊断报告正在生成，请稍候", Toast.LENGTH_SHORT).show();
            } else {
                openGeneratedReport();
            }
        });
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(this);
    }

    private void initUsbSerial() {
        usbHelper = new UsbSerialHelper(this);
        usbHelper.setByteDataListener(data -> {
            String hexStr = bytesToHex(data);
            runOnUiThread(() -> {
                if (textReceivedData != null) textReceivedData.setText("接收数据：" + hexStr);
            });
            parseDataFrame(data);
        });
        usbHelper.scanAndConnect();
    }

    private void parseDataFrame(byte[] data) {
        byte[] raw = data;
        int start = -1, end = -1;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0x7E) {
                if (start == -1) start = i;
                else { end = i; break; }
            }
        }
        if (start == -1 || end == -1 || end - start < 10) return;

        int frameLen = end - start - 1;
        byte[] frame = new byte[frameLen];
        System.arraycopy(raw, start + 1, frame, 0, frameLen);

        // 消息ID（大端）
        int msgId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        // 修正：实际ID为0x3000（示例中30 00）
        if (msgId != 0x3000) return;

        int dataLen = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (frameLen < dataLen + 1) return;

        // 消息体：状态(1) + CO(2) + CO2(2)，后续协议字节不参与本检测。
        int offset = 5; // 状态在索引4，浓度从索引5开始
        if (frameLen < offset + 4) return; // 至少CO+CO2

        int co = (frame[offset+1] & 0xFF) << 8 | (frame[offset] & 0xFF);
        offset += 2;
        int co2 = (frame[offset+1] & 0xFF) << 8 | (frame[offset] & 0xFF);

        lastCO = co;
        lastCO2 = co2;
        lastCorrectionFactor = RedBloodCellLifespanCalculator.correctionFactor(lastCO2);
        lastCorrectedCO = RedBloodCellLifespanCalculator.correctedCo(lastCO, lastCO2);
        lastLifespanDays = RedBloodCellLifespanCalculator.lifespanDays(
                totalHemoglobin, lastCorrectedCO);

        runOnUiThread(() -> {
            updateSingleChannelTable();
            updateBarChart();
            textDetectionProgress.setText("检测完成 100%");
            onDetectionComplete();
        });
    }

    private void updateSingleChannelTable() {
        if (singleChannelDataRow == null) return;
        ((TextView) singleChannelDataRow.findViewById(R.id.tvCO)).setText(
                String.format(Locale.CHINA, "%.2f", lastCO));
        ((TextView) singleChannelDataRow.findViewById(R.id.tvCO2)).setText(
                String.format(Locale.CHINA, "%.0f", lastCO2));
        ((TextView) singleChannelDataRow.findViewById(R.id.tvCorrected)).setText(
                String.format(Locale.CHINA, "%.2f", lastCorrectionFactor));
        TextView status = singleChannelDataRow.findViewById(R.id.tvStatus);
        if (!RedBloodCellLifespanCalculator.hasValidCorrectionFactor(lastCO2)) {
            status.setText("系数无效");
            status.setTextColor(Color.parseColor("#F44336"));
        } else if (!RedBloodCellLifespanCalculator.hasValidLifespan(
                totalHemoglobin, lastCorrectedCO)) {
            status.setText("数据无效");
            status.setTextColor(Color.parseColor("#F44336"));
        } else if (lastLifespanDays < RedBloodCellLifespanCalculator.MIN_NORMAL_DAYS) {
            status.setText("寿命缩短");
            status.setTextColor(Color.parseColor("#F44336"));
        } else if (lastLifespanDays > RedBloodCellLifespanCalculator.MAX_NORMAL_DAYS) {
            status.setText("寿命偏长");
            status.setTextColor(Color.parseColor("#FF9800"));
        } else {
            status.setText("正常");
            status.setTextColor(Color.parseColor("#4CAF50"));
        }
    }

    private void updateBarChart() {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, lastCorrectedCO));
        entries.add(new BarEntry(1, totalHemoglobin));
        BarDataSet dataSet = new BarDataSet(entries, "检测数据");
        dataSet.setColors(new int[]{Color.parseColor("#2196F3"), Color.parseColor("#E91E63")});
        dataSet.setValueTextSize(12f);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.invalidate();

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(2);
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                new String[]{"修正后CO浓度", "全身血红蛋白总量"}));
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(1.5f);
        xAxis.setTextSize(10f);
        barChart.setExtraBottomOffset(12f);
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        Description desc = new Description();
        desc.setText("修正后CO浓度(ppm) / 全身血红蛋白总量");
        barChart.setDescription(desc);
    }

    private void initTable() {
        tableSingleChannel.removeAllViews();
        android.view.View table = getLayoutInflater().inflate(R.layout.table_single_channel, null);
        tableSingleChannel.addView(table);
        singleChannelDataRow = table.findViewById(R.id.dataRow);
    }

    private void startDetectionProgress() {
        textDetectionProgress.setBackgroundColor(Color.parseColor("#9E9E9E"));
        textDetectionProgress.setText("检测中 0%");
    }

    private void onDetectionComplete() {
        if (detectionCompleted) return;
        detectionCompleted = true;
        textDetectionProgress.setBackgroundColor(Color.parseColor("#4CAF50"));
        textDetectionProgress.setText("检测完成");
        buttonReportManage.setEnabled(false);
        String interpretation = generateInterpretation();
        textResultInterpretation.setText(interpretation);
        saveReportData(interpretation);
    }

    private String generateInterpretation() {
        StringBuilder sb = new StringBuilder();
        sb.append("结果解读：\n");
        sb.append(String.format(Locale.CHINA, "CO原始浓度：%.2f ppm\n", lastCO));
        sb.append(String.format(Locale.CHINA, "CO2浓度：%.0f ppm\n", lastCO2));
        sb.append(String.format(Locale.CHINA, "修正系数：%.2f\n", lastCorrectionFactor));
        sb.append(String.format(Locale.CHINA, "CO修正后浓度：%.2f ppm\n", lastCorrectedCO));
        sb.append(String.format(Locale.CHINA, "全身血红蛋白总量：%.2f\n", totalHemoglobin));

        if (!RedBloodCellLifespanCalculator.hasValidCorrectionFactor(lastCO2)) {
            lastLifespanDays = 0f;
            sb.append("红细胞寿命（RBCS）：无法计算（修正系数无效）\n");
            sb.append("诊断结果：检测数据无效\n");
        } else if (!RedBloodCellLifespanCalculator.hasValidLifespan(
                totalHemoglobin, lastCorrectedCO)) {
            lastLifespanDays = 0f;
            sb.append("红细胞寿命（RBCS）：无法计算（全身血红蛋白总量或CO修正后浓度无效）\n");
            sb.append("诊断结果：检测数据不足\n");
        } else {
            lastLifespanDays = RedBloodCellLifespanCalculator.lifespanDays(
                    totalHemoglobin, lastCorrectedCO);
            String diagnosis = RedBloodCellLifespanCalculator.diagnosis(lastLifespanDays);
            sb.append(String.format(Locale.CHINA, "红细胞寿命（RBCS）：%.2f 天\n", lastLifespanDays));
            sb.append("诊断结果：").append(diagnosis).append("\n");
        }
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        new Thread(() -> {
            try {
                RedBloodCellLifespanReportService.SaveResult result =
                        physicalExamMode
                                ? RedBloodCellLifespanReportService.savePhysicalExam(
                                        getApplicationContext(),
                                        db,
                                        reportId,
                                        specimenNo,
                                        lastCO,
                                        lastCO2,
                                        lastCorrectionFactor,
                                        lastCorrectedCO,
                                        totalHemoglobin,
                                        lastLifespanDays,
                                        interpretation)
                                : RedBloodCellLifespanReportService.save(
                                        getApplicationContext(),
                                        db,
                                        reportId,
                                        specimenNo,
                                        lastCO,
                                        lastCO2,
                                        lastCorrectionFactor,
                                        lastCorrectedCO,
                                        totalHemoglobin,
                                        lastLifespanDays,
                                        interpretation);
                runOnUiThread(() -> {
                    generatedPdfUri = result.getUri();
                    buttonReportManage.setEnabled(true);
                    Toast.makeText(
                            Test2Activity.this,
                            "三部分报告数据已保存，PDF已生成：" + result.getFileName(),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                Log.e(TAG, "保存红细胞寿命检测报告失败", error);
                runOnUiThread(() -> Toast.makeText(
                        Test2Activity.this,
                        "报告保存失败：" + error.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void openGeneratedReport() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(generatedPdfUri), "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "未找到可打开PDF的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        textCurrentTime.setText("时间：" + sdf.format(new Date()));
        mainHandler.postDelayed(this::updateCurrentTime, 1000);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbHelper != null) usbHelper.disconnect();
    }
}
