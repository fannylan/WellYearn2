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

    // UI 控件
    private TextView textCurrentTime, textSpecimenNo, textPatientName;
    private TextView textDetectionProgress;
    private Button buttonBack, buttonReportManage;
    private LineChart lineChart;
    private TextView textResultInterpretation;
    private TableLayout tableChannels;

    // 数据
    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long patientId;
    private long reportId;
    private String patientName, specimenNo;

    // 八通道数据存储
    private List<ChannelData> channelDataList = new ArrayList<>();
    private int receivedChannelCount = 0;
    private boolean detectionCompleted = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 图表数据集（示例只用一个数据集展示H2浓度，可根据需求扩展）
    private List<Entry> h2Entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test1);

        // 获取传递的患者信息
        patientId = getIntent().getLongExtra("patientId", -1);
        reportId = getIntent().getLongExtra("reportId", -1);
        patientName = getIntent().getStringExtra("patientName");
        specimenNo = getIntent().getStringExtra("specimenNo");

        initViews();
        initDatabase();
        initUsbSerial();
        initTable();

        // 显示传递的信息
        textPatientName.setText("患者姓名：" + (patientName != null ? patientName : "--"));
        textSpecimenNo.setText("标本编号：" + (specimenNo != null ? specimenNo : "--"));
        updateCurrentTime();

        // 开始检测进度模拟（实际应由数据接收驱动进度）
        startDetectionProgress();
    }

    private void initViews() {
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textSpecimenNo = findViewById(R.id.textSpecimenNo);
        textPatientName = findViewById(R.id.textPatientName);
        textDetectionProgress = findViewById(R.id.textDetectionProgress);
        buttonBack = findViewById(R.id.buttonBack);
        buttonReportManage = findViewById(R.id.buttonReportManage);
        lineChart = findViewById(R.id.lineChart);
        textResultInterpretation = findViewById(R.id.textResultInterpretation);
        tableChannels = findViewById(R.id.tableChannels);

        buttonBack.setOnClickListener(v -> finish());
        buttonReportManage.setOnClickListener(v -> {
            if (detectionCompleted) {
                // 跳转到报告管理页面（可后续实现）
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
            // 解析单片机上传的数据，假设格式为 JSON: {"channel":1, "h2":12.5, "ch4":0.3, "co":0.1, "h2s":0.0, "co2":400, "corrected":12.3}
            parseAndUpdateData(data);
        });
        usbHelper.scanAndConnect();
    }

    private void parseAndUpdateData(String data) {
        try {
            JSONObject json = new JSONObject(data);
            int channel = json.getInt("channel");
            float h2 = (float) json.getDouble("h2");
            float ch4 = (float) json.getDouble("ch4");
            float co = (float) json.getDouble("co");
            float h2s = (float) json.getDouble("h2s");
            float co2 = (float) json.getDouble("co2");
            float corrected = (float) json.getDouble("corrected");
            String status = json.optString("status", "正常");

            // 更新对应通道的数据
            if (channel >= 1 && channel <= 8) {
                ChannelData cd = new ChannelData(channel, h2, ch4, co, h2s, co2, corrected, status);
                channelDataList.add(cd);
                receivedChannelCount++;
                runOnUiThread(() -> updateTableRow(channel, cd));
                // 更新图表（以H2为例）
                h2Entries.add(new Entry(channel, h2));
                runOnUiThread(this::updateChart);

                // 更新进度条（色带）文字
                int progressPercent = (receivedChannelCount * 100) / 8;
                runOnUiThread(() -> {
                    textDetectionProgress.setText("检测中 " + progressPercent + "%");
                    if (progressPercent == 100) {
                        onDetectionComplete();
                    }
                });
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateTableRow(int channel, ChannelData data) {
        // 动态更新表格对应行的各列数据
        TableRow row = (TableRow) tableChannels.getChildAt(channel); // 第0行为表头，所以第1行对应通道1
        if (row != null) {
            ((TextView) row.findViewById(R.id.tvH2)).setText(String.format(Locale.CHINA, "%.1f", data.h2));
            ((TextView) row.findViewById(R.id.tvCH4)).setText(String.format(Locale.CHINA, "%.2f", data.ch4));
            ((TextView) row.findViewById(R.id.tvCO)).setText(String.format(Locale.CHINA, "%.2f", data.co));
            ((TextView) row.findViewById(R.id.tvH2S)).setText(String.format(Locale.CHINA, "%.2f", data.h2s));
            ((TextView) row.findViewById(R.id.tvCO2)).setText(String.format(Locale.CHINA, "%.0f", data.co2));
            // 新增：显示 CH4+H2
            ((TextView) row.findViewById(R.id.tvCH4PlusH2)).setText(String.format(Locale.CHINA, "%.1f", data.ch4PlusH2));
            ((TextView) row.findViewById(R.id.tvCorrected)).setText(String.format(Locale.CHINA, "%.1f", data.corrected));
            ((TextView) row.findViewById(R.id.tvStatus)).setText(data.status);
            if ("异常".equals(data.status)) {
                ((TextView) row.findViewById(R.id.tvStatus)).setTextColor(Color.RED);
            } else {
                ((TextView) row.findViewById(R.id.tvStatus)).setTextColor(Color.parseColor("#FF9800"));
            }
        }
    }

    private void updateChart() {
        LineDataSet dataSet = new LineDataSet(h2Entries, "H2浓度 (ppm)");
        dataSet.setColor(Color.BLUE);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);
        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate(); // 刷新

        // 配置图表样式
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

    private void onDetectionComplete() {
        if (detectionCompleted) return;
        detectionCompleted = true;
        // 更新底部文字为绿色
        textDetectionProgress.setBackgroundColor(Color.parseColor("#4CAF50"));
        textDetectionProgress.setText("检测完成");
        buttonReportManage.setEnabled(true);
        // 生成结果解读（示例）
        String interpretation = generateInterpretation();
        textResultInterpretation.setText(interpretation);
        // 保存检测数据到数据库
        saveReportData(interpretation);
        // 停止USB监听（可选）
        // usbHelper.disconnect();
    }

    private String generateInterpretation() {
        // 根据八通道数据简单判断（示例）
        StringBuilder sb = new StringBuilder();
        sb.append("结果解读：\n");
        for (ChannelData cd : channelDataList) {
            sb.append(String.format("通道%d: H2=%.1f ppm, %s\n", cd.channel, cd.h2, cd.status));
        }
        sb.append("总体评估：");
        // 简单逻辑：如有任一通道异常则提示
        boolean hasAbnormal = channelDataList.stream().anyMatch(cd -> "异常".equals(cd.status));
        if (hasAbnormal) {
            sb.append("存在异常数据，建议复查。");
        } else {
            sb.append("所有通道数据正常。");
        }
        return sb.toString();
    }

    private void saveReportData(String interpretation) {
        new Thread(() -> {
            TestReport report = db.testReportDao().getReportById(reportId);
            if (report != null) {
                // 将八通道数据保存为JSON字符串
                JSONArray jsonArray = new JSONArray();
                for (ChannelData cd : channelDataList) {
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("channel", cd.channel);
                        obj.put("h2", cd.h2);
                        obj.put("ch4", cd.ch4);
                        obj.put("co", cd.co);
                        obj.put("h2s", cd.h2s);
                        obj.put("co2", cd.co2);
                        obj.put("corrected", cd.corrected);
                        obj.put("status", cd.status);
                        jsonArray.put(obj);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                report.setTestResult(jsonArray.toString());
                report.setRemarks(interpretation);
                report.setTestDate(System.currentTimeMillis());
                db.testReportDao().update(report);
                runOnUiThread(() -> Toast.makeText(Test1Activity.this, "检测数据已保存", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void initTable() {
        // 动态添加表头（已在布局中引入 table_header.xml，但需要手动添加）
        // 由于 activity_test.xml 中的 TableLayout 没有子视图，我们需要在代码中动态添加表头和行
        tableChannels.removeAllViews();
        // 添加表头（从布局 inflate）
        TableRow headerRow = (TableRow) getLayoutInflater().inflate(R.layout.table_header, null);
        tableChannels.addView(headerRow);
        // 添加 8 行数据行
        for (int i = 1; i <= 8; i++) {
            TableRow dataRow = (TableRow) getLayoutInflater().inflate(R.layout.table_row_channel, null);
            TextView tvChannelNo = dataRow.findViewById(R.id.tvChannelNo);
            tvChannelNo.setText(String.valueOf(i));
            tableChannels.addView(dataRow);
        }
    }

    private void startDetectionProgress() {
        // 实际进度由数据接收驱动，这里只是初始化显示
        textDetectionProgress.setBackgroundColor(Color.parseColor("#9E9E9E"));
        textDetectionProgress.setText("检测中 0%");
    }

    private void updateCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        textCurrentTime.setText("时间：" + sdf.format(new Date()));
        // 每秒刷新一次
        mainHandler.postDelayed(this::updateCurrentTime, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbHelper != null) {
            usbHelper.disconnect();
        }
    }

    // 内部数据类
    private static class ChannelData {
        int channel;
        float h2, ch4, co, h2s, co2, corrected;
        float ch4PlusH2;  // 新增字段
        String status;

        ChannelData(int channel, float h2, float ch4, float co, float h2s, float co2, float corrected, String status) {
            this.channel = channel;
            this.h2 = h2;
            this.ch4 = ch4;
            this.co = co;
            this.h2s = h2s;
            this.co2 = co2;
            this.corrected = corrected;
            this.ch4PlusH2 = ch4 + h2;
            this.status = status;
        }
    }
}