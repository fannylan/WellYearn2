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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.report.GastrointestinalReportService;
import com.wellyearn.app.usb.UsbSerialHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Test1Activity extends AppCompatActivity {

    private static final String TAG = "Test1Activity";
    private static final int CHANNEL_COUNT = 8;
    private static final float GROUP_SPACE = 0.2f;
    private static final float BAR_SPACE = 0.05f;
    private static final float BAR_WIDTH = 0.35f;

    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress, textReceivedData, textResultInterpretation;
    private Button buttonBack, buttonReportManage;
    private BarChart barChart;
    private TableLayout tableChannels;
    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientNameStr, specimenNo;

    private ChannelData[] channelDataArray = new ChannelData[CHANNEL_COUNT];
    private int receivedChannelCount = 0;
    private boolean detectionCompleted = false;
    private String generatedPdfUri;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        initTable();
        initBarChart();
        initUsbSerial();

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
        tableChannels = findViewById(R.id.tableChannels);

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
        boolean isNewChannel = channelDataArray[channel] == null;
        channelDataArray[channel] = cd;
        if (isNewChannel) {
            receivedChannelCount++;
        }

        runOnUiThread(() -> {
            updateTableRow(channel + 1, cd);
            updateChart();
            updateTableStatuses();
            int progress = receivedChannelCount * 100 / CHANNEL_COUNT;
            textDetectionProgress.setText("检测中 " + progress + "%");
            if (receivedChannelCount == CHANNEL_COUNT) {
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
            ((TextView) row.findViewById(R.id.tvCorrectionFactor)).setText(
                    String.format(Locale.CHINA, "%.2f", data.getCorrectionFactor()));
        }
    }

    private void initBarChart() {
        Description description = new Description();
        description.setText("修正后浓度（ppm）");
        barChart.setDescription(description);
        barChart.setDrawGridBackground(false);
        barChart.setScaleEnabled(false);
        barChart.setNoDataText("等待8通道数据");

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(true);
        xAxis.setLabelCount(CHANNEL_COUNT);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"通道1", "通道2", "通道3", "通道4", "通道5", "通道6", "通道7", "通道8"}));

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(true);
    }

    private void updateChart() {
        List<BarEntry> correctedH2Entries = new ArrayList<>();
        List<BarEntry> correctedCh4Entries = new ArrayList<>();
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            ChannelData data = channelDataArray[i];
            float correctedH2 = data != null ? data.getCorrectedH2() : 0f;
            float correctedCh4 = data != null ? data.getCorrectedCh4() : 0f;
            correctedH2Entries.add(new BarEntry(i, correctedH2));
            correctedCh4Entries.add(new BarEntry(i, correctedCh4));
        }

        BarDataSet h2DataSet = new BarDataSet(correctedH2Entries, "修正后H2 (ppm)");
        h2DataSet.setColor(Color.parseColor("#2196F3"));
        h2DataSet.setValueTextSize(9f);
        BarDataSet ch4DataSet = new BarDataSet(correctedCh4Entries, "修正后CH4 (ppm)");
        ch4DataSet.setColor(Color.parseColor("#FF9800"));
        ch4DataSet.setValueTextSize(9f);

        BarData barData = new BarData(h2DataSet, ch4DataSet);
        barData.setBarWidth(BAR_WIDTH);
        barChart.setData(barData);
        barChart.getXAxis().setAxisMinimum(0f);
        barChart.getXAxis().setAxisMaximum(
                barData.getGroupWidth(GROUP_SPACE, BAR_SPACE) * CHANNEL_COUNT);
        barChart.groupBars(0f, GROUP_SPACE, BAR_SPACE);
        barChart.notifyDataSetChanged();
        barChart.invalidate();
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
        buttonReportManage.setEnabled(false);
        String interpretation = generateInterpretation();
        textResultInterpretation.setText(interpretation);
        saveReportData(interpretation);
    }

    private void updateTableStatuses() {
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            ChannelData data = channelDataArray[i];
            if (data == null) continue;

            TableRow row = (TableRow) tableChannels.getChildAt(i + 1);
            if (row == null) continue;

            TextView statusView = row.findViewById(R.id.tvStatus);
            if (!data.hasValidCorrectionFactor()) {
                setStatus(statusView, "系数无效", "#F44336");
            } else if (i >= 3 && (channelDataArray[0] == null
                    || !channelDataArray[0].hasValidCorrectionFactor())) {
                setStatus(statusView, "等待基线", "#FF9800");
            } else if (isChannelPositive(i)) {
                setStatus(statusView, "阳性", "#F44336");
            } else {
                setStatus(statusView, "正常", "#4CAF50");
            }
        }
    }

    private void setStatus(TextView statusView, String status, String color) {
        statusView.setText(status);
        statusView.setTextColor(Color.parseColor(color));
    }

    private boolean isSiboPositive() {
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            if (isChannelPositive(i)) return true;
        }
        return false;
    }

    private boolean isChannelPositive(int channelIndex) {
        ChannelData data = channelDataArray[channelIndex];
        if (data == null || !data.hasValidCorrectionFactor()) return false;

        float h2 = data.getCorrectedH2();
        float ch4 = data.getCorrectedCh4();
        float baselineH2 = 0f;
        float baselineCh4 = 0f;
        if (channelIndex >= 3) {
            ChannelData baseline = channelDataArray[0];
            if (baseline == null || !baseline.hasValidCorrectionFactor()) return false;
            baselineH2 = baseline.getCorrectedH2();
            baselineCh4 = baseline.getCorrectedCh4();
        }
        return SiboDiagnosisRules.isChannelPositive(
                channelIndex, h2, ch4, baselineH2, baselineCh4);
    }

    private String generateInterpretation() {
        StringBuilder sb = new StringBuilder();
        sb.append("结果解读（修正后浓度）：\n");
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            ChannelData cd = channelDataArray[i];
            if (cd != null) {
                if (cd.hasValidCorrectionFactor()) {
                    sb.append(String.format(Locale.CHINA,
                            "通道%d: H2=%.1f ppm, CH4=%.2f ppm, 修正系数=%.2f\n",
                            i + 1, cd.getCorrectedH2(), cd.getCorrectedCh4(),
                            cd.getCorrectionFactor()));
                } else {
                    sb.append(String.format(Locale.CHINA,
                            "通道%d: CO2=%.0f ppm，修正系数无效\n", i + 1, (float) cd.co2));
                }
            } else {
                sb.append(String.format(Locale.CHINA, "通道%d: 无数据\n", i + 1));
            }
        }
        sb.append(isSiboPositive()
                ? "诊断结果：小肠细菌过度生长（SIBO）阳性\n"
                : "诊断结果：小肠细菌过度生长（SIBO）阴性\n");
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        List<GastrointestinalReportService.ChannelMeasurement> measurements = new ArrayList<>();
        for (ChannelData channelData : channelDataArray) {
            if (channelData == null) continue;
            measurements.add(new GastrointestinalReportService.ChannelMeasurement(
                    channelData.channel,
                    channelData.h2,
                    channelData.ch4,
                    channelData.h2s,
                    channelData.co2,
                    channelData.getCorrectionFactor(),
                    channelData.getCorrectedH2(),
                    channelData.getCorrectedCh4(),
                    channelData.hasValidCorrectionFactor()));
        }
        boolean positive = isSiboPositive();
        new Thread(() -> {
            try {
                GastrointestinalReportService.SaveResult result =
                        GastrointestinalReportService.save(
                                getApplicationContext(),
                                db,
                                reportId,
                                specimenNo,
                                measurements,
                                positive,
                                interpretation);
                runOnUiThread(() -> {
                    generatedPdfUri = result.getUri();
                    buttonReportManage.setEnabled(true);
                    Toast.makeText(
                            Test1Activity.this,
                            "三部分报告数据已保存，PDF已生成：" + result.getFileName(),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                Log.e(TAG, "保存胃肠道检测报告失败", error);
                runOnUiThread(() -> Toast.makeText(
                        Test1Activity.this,
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

    private static class ChannelData {
        int channel, h2, ch4, h2s, co2;
        ChannelData(int channel, int h2, int ch4, int h2s, int co2) {
            this.channel = channel;
            this.h2 = h2;
            this.ch4 = ch4;
            this.h2s = h2s;
            this.co2 = co2;
        }

        float getCorrectionFactor() {
            return ConcentrationCorrection.correctionFactor(co2);
        }

        boolean hasValidCorrectionFactor() {
            return ConcentrationCorrection.hasValidCorrectionFactor(co2);
        }

        float getCorrectedH2() {
            return applyCorrection(h2);
        }

        float getCorrectedCh4() {
            return applyCorrection(ch4);
        }

        private float applyCorrection(float originalValue) {
            return ConcentrationCorrection.correctedValue(originalValue, co2);
        }
    }
}
