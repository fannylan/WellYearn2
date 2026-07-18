package com.wellyearn.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.wellyearn.app.database.entity.TestReport;
import com.wellyearn.app.usb.UsbSerialHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Test2Activity extends AppCompatActivity {

    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress, textReceivedData, textResultInterpretation;
    private Button buttonBack, buttonReportManage;
    private BarChart barChart;
    private TableLayout tableSingleChannel;

    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientNameStr, specimenNo, patientGender;

    private float lastCO = 0, lastCO2 = 0, lastH2 = 0;
    private boolean dataReceived = false;
    private boolean detectionCompleted = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test2);

        patientId = getIntent().getLongExtra("patientId", -1);
        reportId = getIntent().getLongExtra("reportId", -1);
        patientNameStr = getIntent().getStringExtra("patientName");
        specimenNo = getIntent().getStringExtra("specimenNo");
        patientGender = getIntent().getStringExtra("patientGender");

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

        // 消息体：状态(1) + CO(2) + CO2(2) + H2(2)（H2可选，根据长度判断）
        int offset = 5; // 状态在索引4，浓度从索引5开始
        if (frameLen < offset + 4) return; // 至少CO+CO2

        int co = (frame[offset+1] & 0xFF) << 8 | (frame[offset] & 0xFF);
        offset += 2;
        int co2 = (frame[offset+1] & 0xFF) << 8 | (frame[offset] & 0xFF);
        offset += 2;
        int h2;
        if (frameLen >= offset + 2) {
            h2 = (frame[offset+1] & 0xFF) << 8 | (frame[offset] & 0xFF);
        } else {
            h2 = 0;
        }

        lastCO = co;
        lastCO2 = co2;
        lastH2 = h2;
        dataReceived = true;

        runOnUiThread(() -> {
            updateSingleChannelTable(co, co2, h2);
            updateBarChart(co, co2, h2);
            textDetectionProgress.setText("检测完成 100%");
            onDetectionComplete();
        });
    }

    private void updateSingleChannelTable(float co, float co2, float h2) {
        if (tableSingleChannel.getChildCount() < 2) return;
        TableRow dataRow = (TableRow) tableSingleChannel.getChildAt(1);
        if (dataRow == null) return;
        ((TextView) dataRow.findViewById(R.id.tvCO)).setText(String.format(Locale.CHINA, "%.2f", co));
        ((TextView) dataRow.findViewById(R.id.tvCO2)).setText(String.format(Locale.CHINA, "%.0f", co2));
        ((TextView) dataRow.findViewById(R.id.tvH2)).setText(String.format(Locale.CHINA, "%.1f", h2));
        ((TextView) dataRow.findViewById(R.id.tvCorrected)).setText("1");
        ((TextView) dataRow.findViewById(R.id.tvStatus)).setText("正常");
        ((TextView) dataRow.findViewById(R.id.tvStatus)).setTextColor(Color.parseColor("#4CAF50"));
    }

    private void updateBarChart(float co, float co2, float h2) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, co));
        entries.add(new BarEntry(1, co2));
        entries.add(new BarEntry(2, h2));
        BarDataSet dataSet = new BarDataSet(entries, "浓度值");
        dataSet.setColors(new int[]{Color.RED, Color.GREEN, Color.BLUE});
        dataSet.setValueTextSize(12f);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.invalidate();

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(3);
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(new String[]{"CO", "CO2", "H2"}));
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        Description desc = new Description();
        desc.setText("气体浓度");
        barChart.setDescription(desc);
    }

    private void initTable() {
        tableSingleChannel.removeAllViews();
        android.view.View table = getLayoutInflater().inflate(R.layout.table_single_channel, null);
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
        sb.append("结果解读：\n");
        sb.append(String.format("CO浓度: %.2f ppm\n", lastCO));
        sb.append(String.format("CO2浓度: %.0f ppm\n", lastCO2));
        sb.append(String.format("H2浓度: %.1f ppm\n", lastH2));

        if (lastCO > 0 && patientGender != null && !patientGender.isEmpty()) {
            int hb;
            if ("男".equals(patientGender) || "男性".equals(patientGender)) hb = 140;
            else if ("女".equals(patientGender) || "女性".equals(patientGender)) hb = 130;
            else hb = 140;
            float life = (float) (hb * 1.38 / lastCO);
            sb.append(String.format("红细胞寿命: %.2f 天\n", life));
        } else {
            sb.append("红细胞寿命: 数据不足（无性别或CO浓度为零）\n");
        }
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        new Thread(() -> {
            TestReport report = db.testReportDao().getReportById(reportId);
            if (report != null) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("co", lastCO);
                    obj.put("co2", lastCO2);
                    obj.put("h2", lastH2);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                report.setTestResult(obj.toString());
                report.setRemarks(interpretation);
                db.testReportDao().update(report);
                runOnUiThread(() -> Toast.makeText(Test2Activity.this, "数据已保存", Toast.LENGTH_SHORT).show());
            }
        }).start();
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