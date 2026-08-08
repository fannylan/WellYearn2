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
import com.wellyearn.app.report.AirwayInflammationReportService;
import com.wellyearn.app.usb.UsbSerialHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Test3Activity extends AppCompatActivity {

    private static final String TAG = "Test3Activity";
    private static final int REQUIRED_POINTS = 10;

    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress;
    private Button buttonBack, buttonReportManage;
    private BarChart barChart;
    private TextView textResultInterpretation;
    private TableLayout tableSingleChannel;
    private TableRow singleChannelDataRow;

    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientNameStr, specimenNo;
    private int patientAge;

    private float lastNO;
    private float lastCO2;
    private float lastCorrectionFactor;
    private float lastCorrectedNO;
    private AirwayInflammationDiagnosisRules.RiskLevel lastRiskLevel;
    private boolean detectionCompleted = false;
    private String generatedPdfUri;
    private int dataPointsCount = 0;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test3);

        // 获取传递的患者信息
        patientId = getIntent().getLongExtra("patientId", -1);
        reportId = getIntent().getLongExtra("reportId", -1);
        patientNameStr = getIntent().getStringExtra("patientName");
        specimenNo = getIntent().getStringExtra("specimenNo");
        patientAge = getIntent().getIntExtra("patientAge", 0);

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
        buttonBack = findViewById(R.id.buttonBack);
        buttonReportManage = findViewById(R.id.buttonReportManage);
        barChart = findViewById(R.id.barChart);
        textResultInterpretation = findViewById(R.id.textResultInterpretation);
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
        usbHelper.setByteDataListener(this::parseDataFrame);
        usbHelper.scanAndConnect();
    }

    private void parseDataFrame(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        String text = new String(data, StandardCharsets.UTF_8).trim();
        if (text.startsWith("{")) {
            parseJsonMeasurement(text);
            return;
        }

        int start = -1;
        int end = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0x7E) {
                if (start == -1) {
                    start = i;
                } else {
                    end = i;
                    break;
                }
            }
        }
        if (start == -1 || end == -1 || end - start < 10) {
            return;
        }

        int frameLength = end - start - 1;
        byte[] frame = new byte[frameLength];
        System.arraycopy(data, start + 1, frame, 0, frameLength);
        int messageId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        if (messageId != 0x4000) {
            return;
        }

        int dataLength = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (frameLength < dataLength + 1 || frameLength < 9) {
            return;
        }

        // 消息体：状态(1) + NO(2) + CO2(2)，浓度为小端无符号数。
        int offset = 5;
        int no = (frame[offset + 1] & 0xFF) << 8 | (frame[offset] & 0xFF);
        offset += 2;
        int co2 = (frame[offset + 1] & 0xFF) << 8 | (frame[offset] & 0xFF);
        recordMeasurement(no, co2);
    }

    private void parseJsonMeasurement(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            float no = (float) json.getDouble("no");
            float co2 = (float) json.getDouble("co2");
            recordMeasurement(no, co2);
        } catch (JSONException e) {
            Log.w(TAG, "无法解析呼吸道检测JSON数据", e);
        }
    }

    private void recordMeasurement(float no, float co2) {
        updateMeasurement(no, co2);
        int receivedPoints = ++dataPointsCount;
        runOnUiThread(() -> {
            updateSingleChannelTable();
            updateBarChart();
            int progressPercent = Math.min(receivedPoints * 100 / REQUIRED_POINTS, 100);
            textDetectionProgress.setText("检测中 " + progressPercent + "%");
            if (receivedPoints >= REQUIRED_POINTS && !detectionCompleted) {
                onDetectionComplete();
            }
        });
    }

    private void updateMeasurement(float no, float co2) {
        lastNO = no;
        lastCO2 = co2;
        lastCorrectionFactor = AirwayInflammationDiagnosisRules.correctionFactor(co2);
        lastCorrectedNO = AirwayInflammationDiagnosisRules.correctedNo(no, co2);
        if (AirwayInflammationDiagnosisRules.hasValidCorrectionFactor(co2)) {
            lastRiskLevel = AirwayInflammationDiagnosisRules.riskLevel(patientAge, lastCorrectedNO);
        } else {
            lastRiskLevel = null;
        }
    }

    private void updateSingleChannelTable() {
        if (singleChannelDataRow == null) return;
        ((TextView) singleChannelDataRow.findViewById(R.id.tvNO)).setText(
                String.format(Locale.CHINA, "%.2f", lastNO));
        ((TextView) singleChannelDataRow.findViewById(R.id.tvCO2)).setText(
                String.format(Locale.CHINA, "%.0f", lastCO2));
        ((TextView) singleChannelDataRow.findViewById(R.id.tvCorrected)).setText(
                String.format(Locale.CHINA, "%.2f", lastCorrectionFactor));
        TextView status = singleChannelDataRow.findViewById(R.id.tvStatus);
        if (lastRiskLevel == null) {
            status.setText("系数无效");
            status.setTextColor(Color.parseColor("#F44336"));
        } else {
            status.setText(AirwayInflammationDiagnosisRules.riskLabel(lastRiskLevel));
            status.setTextColor(riskColor(lastRiskLevel));
        }
    }

    private void updateBarChart() {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, lastCorrectedNO));
        BarDataSet dataSet = new BarDataSet(entries, "修正后NO浓度");
        dataSet.setColor(Color.parseColor("#00ACC1"));
        dataSet.setValueTextSize(12f);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.invalidate();

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(1);
        xAxis.setAxisMinimum(-0.75f);
        xAxis.setAxisMaximum(0.75f);
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                new String[]{"修正后NO浓度"}));
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        Description desc = new Description();
        desc.setText("修正后NO浓度（ppb）");
        barChart.setDescription(desc);
    }

    private int riskColor(AirwayInflammationDiagnosisRules.RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return Color.parseColor("#4CAF50");
            case MEDIUM:
                return Color.parseColor("#FF9800");
            case HIGH:
                return Color.parseColor("#F44336");
            default:
                return Color.GRAY;
        }
    }

    private void initTable() {
        tableSingleChannel.removeAllViews();
        android.view.View table = getLayoutInflater().inflate(R.layout.table_single_channel_test3, null);
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
        PhysicalExamFlowCoordinator.advanceAfterCompletion(
                this,
                usbHelper,
                PhysicalExamSelectionRouter.Detection.RESPIRATORY);
    }

    private String generateInterpretation() {
        StringBuilder sb = new StringBuilder();
        sb.append("检测完成，共接收").append(dataPointsCount).append("个数据点。\n");
        sb.append(String.format(Locale.CHINA, "年龄：%d岁（%s）\n",
                patientAge, AirwayInflammationDiagnosisRules.isAdult(patientAge) ? "成人" : "儿童"));
        sb.append(String.format(Locale.CHINA, "NO原始浓度：%.2f ppb\n", lastNO));
        sb.append(String.format(Locale.CHINA, "CO2浓度：%.0f ppm\n", lastCO2));
        sb.append(String.format(Locale.CHINA, "修正系数：%.2f\n", lastCorrectionFactor));
        sb.append(String.format(Locale.CHINA, "NO修正后浓度：%.2f ppb\n", lastCorrectedNO));
        if (lastRiskLevel == null) {
            sb.append("诊断结果：修正系数无效，无法进行FeNO气道炎症临床判断。\n");
        } else {
            sb.append("临床标准：")
                    .append(AirwayInflammationDiagnosisRules.standardForAge(patientAge)).append("\n");
            sb.append("风险等级：")
                    .append(AirwayInflammationDiagnosisRules.riskLabel(lastRiskLevel)).append("\n");
            sb.append("诊断结果：")
                    .append(AirwayInflammationDiagnosisRules.diagnosis(lastRiskLevel)).append("\n");
        }
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        new Thread(() -> {
            try {
                AirwayInflammationReportService.SaveResult result =
                        AirwayInflammationReportService.save(
                                getApplicationContext(),
                                db,
                                reportId,
                                specimenNo,
                                patientAge,
                                lastNO,
                                lastCO2,
                                lastCorrectionFactor,
                                lastCorrectedNO,
                                lastRiskLevel,
                                dataPointsCount,
                                interpretation);
                runOnUiThread(() -> {
                    generatedPdfUri = result.getUri();
                    buttonReportManage.setEnabled(true);
                    Toast.makeText(
                            Test3Activity.this,
                            "三部分报告数据已保存，PDF已生成：" + result.getFileName(),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                Log.e(TAG, "保存呼吸道炎症检测报告失败", error);
                runOnUiThread(() -> Toast.makeText(
                        Test3Activity.this,
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

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (usbHelper != null) usbHelper.disconnect();
    }
}
