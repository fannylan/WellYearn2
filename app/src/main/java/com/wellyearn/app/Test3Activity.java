package com.wellyearn.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TableLayout;
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
import com.wellyearn.app.database.entity.TestReport;
import com.wellyearn.app.usb.UsbSerialHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Test3Activity extends AppCompatActivity {

    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress;
    private Button buttonBack, buttonReportManage;
    private BarChart barChart;
    private TextView textResultInterpretation;
    private TableLayout tableSingleChannel;

    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientNameStr, specimenNo;

    // 存储最近接收的数据
    private float lastNO, lastH2, lastCO2, lastCorrected;
    private String lastStatus;
    private boolean detectionCompleted = false;
    private int dataPointsCount = 0;
    private static final int REQUIRED_POINTS = 10; // 需要10个数据点完成检测

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
            if (detectionCompleted) {
                Toast.makeText(this, "报告管理功能开发中", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "检测未完成，无法查看报告", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(this);
    }

    private void initUsbSerial() {
        usbHelper = new UsbSerialHelper(this);
        usbHelper.setOnDataReceivedListener(data -> {
            parseAndUpdateData(data);
        });
        usbHelper.scanAndConnect();
    }

    private void parseAndUpdateData(String data) {
        if (data == null || data.trim().isEmpty()) return;
        String trimmed = data.trim();
        if (!trimmed.startsWith("{")) {
            // 开发阶段可用模拟数据
            if (dataPointsCount == 0) generateMockData();
            return;
        }
        try {
            JSONObject json = new JSONObject(trimmed);
            // 注意：字段名根据实际USB协议修改
            float no = (float) json.getDouble("no");
            float h2 = (float) json.getDouble("h2");
            float co2 = (float) json.getDouble("co2");
            float corrected = (float) json.getDouble("corrected");
            String status = json.optString("status", "正常");

            // 更新界面
            runOnUiThread(() -> {
                updateSingleChannelTable(no, h2, co2, corrected, status);
                updateBarChart(no, h2, co2);
            });

            dataPointsCount++;
            int progressPercent = Math.min(dataPointsCount * 100 / REQUIRED_POINTS, 99);
            runOnUiThread(() -> textDetectionProgress.setText("检测中 " + progressPercent + "%"));

            if (dataPointsCount >= REQUIRED_POINTS && !detectionCompleted) {
                runOnUiThread(this::onDetectionComplete);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateSingleChannelTable(float no, float h2, float co2, float corrected, String status) {
        // 找到数据行中的各个TextView并更新
        android.view.View tableRoot = tableSingleChannel.getChildAt(0);
        if (tableRoot instanceof android.widget.TableLayout) {
            android.widget.TableRow dataRow = ((android.widget.TableLayout) tableRoot).findViewById(R.id.dataRow);
            if (dataRow != null) {
                ((TextView) dataRow.findViewById(R.id.tvNO)).setText(String.format(Locale.CHINA, "%.2f", no));
                ((TextView) dataRow.findViewById(R.id.tvH2)).setText(String.format(Locale.CHINA, "%.1f", h2));
                ((TextView) dataRow.findViewById(R.id.tvCO2)).setText(String.format(Locale.CHINA, "%.0f", co2));
                ((TextView) dataRow.findViewById(R.id.tvCorrected)).setText(String.format(Locale.CHINA, "%.1f", corrected));
                ((TextView) dataRow.findViewById(R.id.tvStatus)).setText(status);
                if ("异常".equals(status)) {
                    ((TextView) dataRow.findViewById(R.id.tvStatus)).setTextColor(Color.RED);
                } else {
                    ((TextView) dataRow.findViewById(R.id.tvStatus)).setTextColor(Color.parseColor("#FF9800"));
                }
            }
        }
        lastNO = no;
        lastH2 = h2;
        lastCO2 = co2;
        lastCorrected = corrected;
        lastStatus = status;
    }

    private void updateBarChart(float no, float h2, float co2) {
        // 柱状图显示三个指标：NO、H2、CO2
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, no));
        entries.add(new BarEntry(1, h2));
        entries.add(new BarEntry(2, co2));
        BarDataSet dataSet = new BarDataSet(entries, "浓度值");
        dataSet.setColors(new int[]{Color.parseColor("#9C27B0"), Color.BLUE, Color.GREEN});
        dataSet.setValueTextSize(12f);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.invalidate();

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(3);
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(new String[]{"NO", "H2", "CO2"}));
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        Description desc = new Description();
        desc.setText("气体浓度");
        barChart.setDescription(desc);
    }

    private void initTable() {
        tableSingleChannel.removeAllViews();
        android.view.View table = getLayoutInflater().inflate(R.layout.table_single_channel_test3, null);
        tableSingleChannel.addView(table);
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
        buttonReportManage.setEnabled(true);
        String interpretation = generateInterpretation();
        textResultInterpretation.setText(interpretation);
        saveReportData(interpretation);
    }

    private String generateInterpretation() {
        StringBuilder sb = new StringBuilder();
        sb.append("检测完成，共接收").append(dataPointsCount).append("个数据点。\n");
        sb.append("最新值：NO=").append(lastNO).append(", H2=").append(lastH2)
                .append(", CO2=").append(lastCO2).append(", 修正值=").append(lastCorrected).append("\n");
        if ("异常".equals(lastStatus)) {
            sb.append("状态异常，建议复查。");
        } else {
            sb.append("状态正常。");
        }
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        new Thread(() -> {
            TestReport report = db.testReportDao().getReportById(reportId);
            if (report != null) {
                JSONArray jsonArray = new JSONArray();
                JSONObject obj = new JSONObject();
                try {
                    obj.put("no", lastNO);
                    obj.put("h2", lastH2);
                    obj.put("co2", lastCO2);
                    obj.put("corrected", lastCorrected);
                    obj.put("status", lastStatus);
                    jsonArray.put(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                report.setTestResult(jsonArray.toString());
                report.setRemarks(interpretation);
                db.testReportDao().update(report);
                runOnUiThread(() -> Toast.makeText(Test3Activity.this, "报告数据已保存", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void generateMockData() {
        // 模拟10个数据点，用于调试
        for (int i = 0; i < REQUIRED_POINTS; i++) {
            float no = (float) (0.1 + Math.random() * 0.5);
            float h2 = (float) (10 + Math.random() * 20);
            float co2 = (float) (380 + Math.random() * 40);
            float corrected = no + h2;
            String status = i % 3 == 0 ? "异常" : "正常";
            final int index = i;
            runOnUiThread(() -> {
                updateSingleChannelTable(no, h2, co2, corrected, status);
                updateBarChart(no, h2, co2);
                int progress = (index + 1) * 100 / REQUIRED_POINTS;
                textDetectionProgress.setText("检测中 " + progress + "%");
                if (index + 1 == REQUIRED_POINTS) onDetectionComplete();
            });
            dataPointsCount++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        textCurrentTime.setText("时间：" + sdf.format(new Date()));
        mainHandler.postDelayed(this::updateCurrentTime, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbHelper != null) usbHelper.disconnect();
    }
}