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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
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

public class Test1Activity extends AppCompatActivity {

    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress, textReceivedData, textResultInterpretation;
    private Button buttonBack, buttonReportManage;
    private LineChart lineChart;
    private TableLayout tableChannels;
    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientNameStr, specimenNo;

    private ChannelData[] channelDataArray = new ChannelData[8];
    private int receivedChannelCount = 0;
    private boolean detectionCompleted = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<Entry> h2Entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test1);

        patientId = getIntent().getLongExtra("patientId", -1);
        reportId = getIntent().getLongExtra("reportId", -1);
        patientNameStr = getIntent().getStringExtra("patientName");
        specimenNo = getIntent().getStringExtra("specimenNo");
        String substrate = getIntent().getStringExtra("substrate");

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
        lineChart = findViewById(R.id.lineChart);
        tableChannels = findViewById(R.id.tableChannels);

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
            runOnUiThread(() -> textReceivedData.setText("接收数据：" + hexStr));
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
        if (start == -1 || end == -1 || end - start < 12) return;

        int frameLen = end - start - 1;
        byte[] frame = new byte[frameLen];
        System.arraycopy(raw, start + 1, frame, 0, frameLen);

        // 消息ID（大端）
        int msgId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        // 修正：实际ID为0x2000（示例中20 00）
        if (msgId != 0x2000) return;

        int dataLen = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (frameLen < dataLen + 1) return; // 至少包含校验码

        // 状态（忽略）
        int status = frame[4] & 0xFF;
        // 通道号（0~7）
        int channel = frame[5] & 0xFF;
        if (channel < 0 || channel > 7) return;

        // 浓度数据从索引6开始，每个2字节，小端
        int h2 = (frame[7] & 0xFF) << 8 | (frame[6] & 0xFF);
        int ch4 = (frame[9] & 0xFF) << 8 | (frame[8] & 0xFF);
        int h2s = (frame[11] & 0xFF) << 8 | (frame[10] & 0xFF);
        int co2 = (frame[13] & 0xFF) << 8 | (frame[12] & 0xFF);

        ChannelData cd = new ChannelData(channel + 1, h2, ch4, h2s, co2);
        channelDataArray[channel] = cd;
        receivedChannelCount++;

        runOnUiThread(() -> {
            updateTableRow(channel + 1, cd);
            h2Entries.add(new Entry(channel + 1, h2));
            updateChart();
            int progress = receivedChannelCount * 100 / 8;
            textDetectionProgress.setText("检测中 " + progress + "%");
            if (receivedChannelCount == 8) {
                onDetectionComplete();
            }
        });
    }

    private void updateTableRow(int channel, ChannelData data) {
        TableRow row = (TableRow) tableChannels.getChildAt(channel);
        if (row != null) {
            ((TextView) row.findViewById(R.id.tvH2)).setText(String.format(Locale.CHINA, "%.1f", (float) data.h2));
            ((TextView) row.findViewById(R.id.tvCH4)).setText(String.format(Locale.CHINA, "%.2f", (float) data.ch4));
            ((TextView) row.findViewById(R.id.tvH2S)).setText(String.format(Locale.CHINA, "%.2f", (float) data.h2s));
            ((TextView) row.findViewById(R.id.tvCO2)).setText(String.format(Locale.CHINA, "%.0f", (float) data.co2));
            float ch4PlusH2 = data.ch4 + data.h2;
            ((TextView) row.findViewById(R.id.tvCH4PlusH2)).setText(String.format(Locale.CHINA, "%.1f", ch4PlusH2));
            ((TextView) row.findViewById(R.id.tvCorrected)).setText("1");
            ((TextView) row.findViewById(R.id.tvStatus)).setText("正常");
            ((TextView) row.findViewById(R.id.tvStatus)).setTextColor(Color.parseColor("#4CAF50"));
        }
    }

    private void updateChart() {
        if (h2Entries.isEmpty()) return;
        LineDataSet dataSet = new LineDataSet(h2Entries, "H2浓度 (ppm)");
        dataSet.setColor(Color.BLUE);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);
        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate();

        Description desc = new Description();
        desc.setText("通道");
        lineChart.setDescription(desc);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void initTable() {
        tableChannels.removeAllViews();
        TableRow headerRow = (TableRow) getLayoutInflater().inflate(R.layout.table_header, null);
        tableChannels.addView(headerRow);
        for (int i = 1; i <= 8; i++) {
            TableRow dataRow = (TableRow) getLayoutInflater().inflate(R.layout.table_row_channel, null);
            TextView tvChannelNo = dataRow.findViewById(R.id.tvChannelNo);
            tvChannelNo.setText(String.valueOf(i));
            tableChannels.addView(dataRow);
        }
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
        boolean hasSIBO = false;
        for (int i = 0; i < 7; i++) {
            ChannelData cd = channelDataArray[i];
            if (cd != null) {
                float h2 = cd.h2;
                float ch4 = cd.ch4;
                float sum = h2 + ch4;
                sb.append(String.format("通道%d: H2=%.1f, CH4=%.2f, H2S=%.2f, CO2=%.0f\n",
                        i + 1, h2, ch4, (float) cd.h2s, (float) cd.co2));
                if (h2 >= 20 || ch4 >= 10 || sum >= 15) hasSIBO = true;
            } else {
                sb.append(String.format("通道%d: 无数据\n", i + 1));
            }
        }
        ChannelData cd8 = channelDataArray[7];
        if (cd8 != null) {
            sb.append(String.format("通道8: H2=%.1f, CH4=%.2f, H2S=%.2f, CO2=%.0f\n",
                    (float) cd8.h2, (float) cd8.ch4, (float) cd8.h2s, (float) cd8.co2));
        } else {
            sb.append("通道8: 无数据\n");
        }
        sb.append(hasSIBO ? "小肠细菌过度生长：阳性\n" : "小肠细菌过度生长：阴性\n");
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        new Thread(() -> {
            TestReport report = db.testReportDao().getReportById(reportId);
            if (report != null) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < 8; i++) {
                    ChannelData cd = channelDataArray[i];
                    if (cd != null) {
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("channel", i + 1);
                            obj.put("h2", cd.h2);
                            obj.put("ch4", cd.ch4);
                            obj.put("h2s", cd.h2s);
                            obj.put("co2", cd.co2);
                            jsonArray.put(obj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
                report.setTestResult(jsonArray.toString());
                report.setRemarks(interpretation);
                db.testReportDao().update(report);
                runOnUiThread(() -> Toast.makeText(Test1Activity.this, "检测数据已保存", Toast.LENGTH_SHORT).show());
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

    private static class ChannelData {
        int channel, h2, ch4, h2s, co2;
        ChannelData(int channel, int h2, int ch4, int h2s, int co2) {
            this.channel = channel;
            this.h2 = h2;
            this.ch4 = ch4;
            this.h2s = h2s;
            this.co2 = co2;
        }
    }
}
