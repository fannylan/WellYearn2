package com.wellyearn.app;

import android.content.Intent;
import android.graphics.Color;
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
import com.wellyearn.app.report.AirwayInflammationReportService;
import com.wellyearn.app.report.GastrointestinalReportService;
import com.wellyearn.app.report.PhysicalExamReportService;
import com.wellyearn.app.report.RedBloodCellLifespanReportService;
import com.wellyearn.app.usb.UsbSerialHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Combined result screen for the ordered physical-exam detection flow. */
public class PhysicalExamResultActivity extends AppCompatActivity {

    private static final String TAG = "PhysicalExamResult";
    private static final int GASTROINTESTINAL_CHANNEL_COUNT = 8;
    private static final int RESPIRATORY_REQUIRED_POINTS = 10;
    private static final float GROUP_SPACE = 0.2f;
    private static final float BAR_SPACE = 0.05f;
    private static final float BAR_WIDTH = 0.35f;

    private TextView textCurrentTime;
    private TextView textSpecimenNo;
    private TextView textPatientName;
    private TextView textDetectionProgress;
    private TextView textReceivedData;
    private TextView textResultInterpretation;
    private Button buttonReportManage;
    private BarChart barChart;
    private TableLayout tableChannels;
    private TableLayout tableCoContainer;
    private TableLayout tableNoContainer;
    private TableRow coDataRow;
    private TableRow noDataRow;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ChannelData[] gastrointestinalData =
            new ChannelData[GASTROINTESTINAL_CHANNEL_COUNT];

    private UsbSerialHelper usbHelper;
    private AppDatabase db;
    private long gastrointestinalReportId;
    private long redBloodCellReportId;
    private long respiratoryReportId;
    private long physicalExamReportId;
    private String patientName;
    private String specimenNo;
    private int patientAge;
    private float totalHemoglobin;

    private boolean selectedH2;
    private boolean selectedCH4;
    private boolean selectedCO;
    private boolean selectedNO;
    private boolean gastrointestinalCompleted;
    private boolean redBloodCellCompleted;
    private boolean respiratoryCompleted;
    private boolean physicalExamReportSaving;
    private int gastrointestinalChannelCount;
    private int respiratoryPointCount;
    private PhysicalExamSelectionRouter.Detection currentDetection;

    private float lastCO;
    private float lastCO2ForRbc;
    private float lastCoCorrectionFactor;
    private float lastCorrectedCO;
    private float lastLifespanDays;
    private float lastNO;
    private float lastCO2ForNo;
    private float lastNoCorrectionFactor;
    private float lastCorrectedNO;
    private AirwayInflammationDiagnosisRules.RiskLevel lastNoRiskLevel;

    private String gastrointestinalDiagnosis = "等待胃肠道检测数据...";
    private String redBloodCellDiagnosis = "等待红细胞寿命检测数据...";
    private String respiratoryDiagnosis = "等待呼吸道疾病检测数据...";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physical_exam_result);

        Intent intent = getIntent();
        patientName = intent.getStringExtra("patientName");
        specimenNo = intent.getStringExtra("specimenNo");
        patientAge = intent.getIntExtra("patientAge", 0);
        totalHemoglobin = intent.getFloatExtra("hemoglobin", 0f);
        selectedCH4 = intent.getBooleanExtra("chkCH4", false);
        selectedH2 = intent.getBooleanExtra("chkH2", false);
        selectedCO = intent.getBooleanExtra("chkCO", false);
        selectedNO = intent.getBooleanExtra("chkNO", false);
        gastrointestinalReportId = intent.getLongExtra(
                PhysicalExamFlowCoordinator.EXTRA_GASTROINTESTINAL_REPORT_ID, -1L);
        redBloodCellReportId = intent.getLongExtra(
                PhysicalExamFlowCoordinator.EXTRA_RED_BLOOD_CELL_REPORT_ID, -1L);
        respiratoryReportId = intent.getLongExtra(
                PhysicalExamFlowCoordinator.EXTRA_RESPIRATORY_REPORT_ID, -1L);
        physicalExamReportId = intent.getLongExtra(
                PhysicalExamFlowCoordinator.EXTRA_PHYSICAL_EXAM_REPORT_ID, -1L);
        currentDetection = PhysicalExamSelectionRouter.firstSelected(
                selectedCH4, selectedH2, selectedCO, selectedNO);

        initViews();
        initTables();
        initChart();
        db = AppDatabase.getInstance(this);
        initUsbSerial();

        textPatientName.setText("患者姓名：" + safe(patientName));
        textSpecimenNo.setText("标本编号：" + safe(specimenNo));
        updateCurrentTime();
        updateDiagnosisText();
        updateProgressForCurrentDetection(0);
    }

    private void initViews() {
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textSpecimenNo = findViewById(R.id.textSpecimenNo);
        textPatientName = findViewById(R.id.textPatientName);
        textDetectionProgress = findViewById(R.id.textDetectionProgress);
        textReceivedData = findViewById(R.id.textReceivedData);
        textResultInterpretation = findViewById(R.id.textResultInterpretation);
        buttonReportManage = findViewById(R.id.buttonReportManage);
        barChart = findViewById(R.id.barChart);
        tableChannels = findViewById(R.id.tableChannels);
        tableCoContainer = findViewById(R.id.tableCoContainer);
        tableNoContainer = findViewById(R.id.tableNoContainer);

        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        buttonReportManage.setOnClickListener(view ->
                startActivity(new Intent(this, ReportSearchActivity.class)));
    }

    private void initTables() {
        tableChannels.removeAllViews();
        tableChannels.addView(getLayoutInflater().inflate(R.layout.table_header, tableChannels, false));
        boolean gastrointestinalSelected = selectedH2 || selectedCH4;
        for (int channel = 1; channel <= GASTROINTESTINAL_CHANNEL_COUNT; channel++) {
            TableRow row = (TableRow) getLayoutInflater().inflate(
                    R.layout.table_row_channel, tableChannels, false);
            ((TextView) row.findViewById(R.id.tvChannelNo)).setText(String.valueOf(channel));
            if (!selectedH2) {
                setText(row, R.id.tvH2, "--");
            }
            if (!selectedCH4) {
                setText(row, R.id.tvCH4, "--");
            }
            if (!gastrointestinalSelected) {
                setText(row, R.id.tvH2, "--");
                setText(row, R.id.tvCH4, "--");
                setText(row, R.id.tvH2S, "--");
                setText(row, R.id.tvCO2, "--");
                setText(row, R.id.tvCH4PlusH2, "--");
                setText(row, R.id.tvCorrectionFactor, "--");
                setStatus(row.findViewById(R.id.tvStatus), "未勾选", "#9E9E9E");
            }
            tableChannels.addView(row);
        }

        tableCoContainer.removeAllViews();
        android.view.View coTable = getLayoutInflater().inflate(
                R.layout.table_single_channel, tableCoContainer, false);
        tableCoContainer.addView(coTable);
        coDataRow = coTable.findViewById(R.id.dataRow);
        if (!selectedCO) {
            setText(coDataRow, R.id.tvCO, "--");
            setText(coDataRow, R.id.tvCO2, "--");
            setText(coDataRow, R.id.tvCorrected, "--");
            setStatus(coDataRow.findViewById(R.id.tvStatus), "未勾选", "#9E9E9E");
        }

        tableNoContainer.removeAllViews();
        android.view.View noTable = getLayoutInflater().inflate(
                R.layout.table_single_channel_test3, tableNoContainer, false);
        tableNoContainer.addView(noTable);
        noDataRow = noTable.findViewById(R.id.dataRow);
        if (!selectedNO) {
            setText(noDataRow, R.id.tvNO, "--");
            setText(noDataRow, R.id.tvCO2, "--");
            setText(noDataRow, R.id.tvCorrected, "--");
            setStatus(noDataRow.findViewById(R.id.tvStatus), "未勾选", "#9E9E9E");
        }
    }

    private void initChart() {
        barChart.setDrawGridBackground(false);
        barChart.setScaleEnabled(false);
        barChart.setNoDataText("等待检测数据");
        barChart.getAxisRight().setEnabled(false);
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
    }

    private void initUsbSerial() {
        usbHelper = new UsbSerialHelper(this);
        usbHelper.setByteDataListener(data -> {
            String hex = bytesToHex(data);
            runOnUiThread(() -> textReceivedData.setText("接收数据：" + hex));
            parseIncomingData(data);
        });
        usbHelper.scanAndConnect();
    }

    private void parseIncomingData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        String text = new String(data, StandardCharsets.UTF_8).trim();
        if (selectedNO && text.startsWith("{")) {
            parseRespiratoryJson(text);
            return;
        }

        ProtocolFrame frame = ProtocolFrame.from(data);
        if (frame == null) {
            return;
        }
        if (frame.messageId == 0x2000) {
            parseGastrointestinalFrame(frame.payload);
        } else if (frame.messageId == 0x3000) {
            parseRedBloodCellFrame(frame.payload);
        } else if (frame.messageId == 0x4000) {
            parseRespiratoryFrame(frame.payload);
        }
    }

    private void parseGastrointestinalFrame(byte[] frame) {
        if ((!selectedH2 && !selectedCH4) || gastrointestinalCompleted || frame.length < 15) {
            return;
        }
        int channelIndex = frame[5] & 0xFF;
        if (channelIndex < 0 || channelIndex >= GASTROINTESTINAL_CHANNEL_COUNT) {
            return;
        }
        int h2 = littleEndianUnsignedShort(frame, 6);
        int ch4 = littleEndianUnsignedShort(frame, 8);
        int h2s = littleEndianUnsignedShort(frame, 10);
        int co2 = littleEndianUnsignedShort(frame, 12);
        ChannelData data = new ChannelData(channelIndex + 1, h2, ch4, h2s, co2);
        boolean newChannel = gastrointestinalData[channelIndex] == null;
        gastrointestinalData[channelIndex] = data;
        if (newChannel) {
            gastrointestinalChannelCount++;
        }

        runOnUiThread(() -> {
            updateGastrointestinalRow(channelIndex, data);
            updateGastrointestinalChart();
            int progress = gastrointestinalChannelCount * 100 / GASTROINTESTINAL_CHANNEL_COUNT;
            updateProgressForCurrentDetection(progress);
            if (gastrointestinalChannelCount == GASTROINTESTINAL_CHANNEL_COUNT) {
                completeGastrointestinalDetection();
            }
        });
    }

    private void parseRedBloodCellFrame(byte[] frame) {
        if (!selectedCO || redBloodCellCompleted || frame.length < 10) {
            return;
        }
        lastCO = littleEndianUnsignedShort(frame, 5);
        lastCO2ForRbc = littleEndianUnsignedShort(frame, 7);
        lastCoCorrectionFactor = RedBloodCellLifespanCalculator.correctionFactor(lastCO2ForRbc);
        lastCorrectedCO = RedBloodCellLifespanCalculator.correctedCo(lastCO, lastCO2ForRbc);
        lastLifespanDays = RedBloodCellLifespanCalculator.lifespanDays(
                totalHemoglobin, lastCorrectedCO);

        runOnUiThread(() -> {
            updateCoTable();
            updateRedBloodCellChart();
            updateProgressForCurrentDetection(100);
            completeRedBloodCellDetection();
        });
    }

    private void parseRespiratoryFrame(byte[] frame) {
        if (!selectedNO || respiratoryCompleted || frame.length < 10) {
            return;
        }
        recordRespiratoryMeasurement(
                littleEndianUnsignedShort(frame, 5),
                littleEndianUnsignedShort(frame, 7));
    }

    private void parseRespiratoryJson(String value) {
        if (!selectedNO || respiratoryCompleted) {
            return;
        }
        try {
            JSONObject json = new JSONObject(value);
            recordRespiratoryMeasurement(
                    (float) json.getDouble("no"),
                    (float) json.getDouble("co2"));
        } catch (JSONException error) {
            Log.w(TAG, "无法解析呼吸道检测JSON数据", error);
        }
    }

    private void recordRespiratoryMeasurement(float no, float co2) {
        lastNO = no;
        lastCO2ForNo = co2;
        lastNoCorrectionFactor = AirwayInflammationDiagnosisRules.correctionFactor(co2);
        lastCorrectedNO = AirwayInflammationDiagnosisRules.correctedNo(no, co2);
        lastNoRiskLevel = AirwayInflammationDiagnosisRules.hasValidCorrectionFactor(co2)
                ? AirwayInflammationDiagnosisRules.riskLevel(patientAge, lastCorrectedNO)
                : null;
        int receivedPoints = ++respiratoryPointCount;

        runOnUiThread(() -> {
            updateNoTable();
            updateRespiratoryChart();
            updateProgressForCurrentDetection(
                    Math.min(receivedPoints * 100 / RESPIRATORY_REQUIRED_POINTS, 100));
            if (receivedPoints >= RESPIRATORY_REQUIRED_POINTS) {
                completeRespiratoryDetection();
            }
        });
    }

    private void updateGastrointestinalRow(int channelIndex, ChannelData data) {
        TableRow row = (TableRow) tableChannels.getChildAt(channelIndex + 1);
        if (row == null) {
            return;
        }
        setText(row, R.id.tvH2, selectedH2
                ? String.format(Locale.CHINA, "%.1f", (float) data.h2) : "--");
        setText(row, R.id.tvCH4, selectedCH4
                ? String.format(Locale.CHINA, "%.2f", (float) data.ch4) : "--");
        setText(row, R.id.tvH2S, String.format(Locale.CHINA, "%.2f", (float) data.h2s));
        setText(row, R.id.tvCO2, String.format(Locale.CHINA, "%.0f", (float) data.co2));
        float selectedSum = (selectedH2 ? data.h2 : 0f) + (selectedCH4 ? data.ch4 : 0f);
        setText(row, R.id.tvCH4PlusH2, String.format(Locale.CHINA, "%.1f", selectedSum));
        setText(row, R.id.tvCorrectionFactor,
                String.format(Locale.CHINA, "%.2f", data.correctionFactor()));
        if (!data.hasValidCorrectionFactor()) {
            setStatus(row.findViewById(R.id.tvStatus), "系数无效", "#F44336");
        } else if (isGastrointestinalChannelPositive(channelIndex)) {
            setStatus(row.findViewById(R.id.tvStatus), "阳性", "#F44336");
        } else {
            setStatus(row.findViewById(R.id.tvStatus), "正常", "#4CAF50");
        }
    }

    private void updateCoTable() {
        setText(coDataRow, R.id.tvCO, String.format(Locale.CHINA, "%.2f", lastCO));
        setText(coDataRow, R.id.tvCO2, String.format(Locale.CHINA, "%.0f", lastCO2ForRbc));
        setText(coDataRow, R.id.tvCorrected,
                String.format(Locale.CHINA, "%.2f", lastCoCorrectionFactor));
        TextView status = coDataRow.findViewById(R.id.tvStatus);
        if (!RedBloodCellLifespanCalculator.hasValidCorrectionFactor(lastCO2ForRbc)) {
            setStatus(status, "系数无效", "#F44336");
        } else if (!RedBloodCellLifespanCalculator.hasValidLifespan(
                totalHemoglobin, lastCorrectedCO)) {
            setStatus(status, "数据无效", "#F44336");
        } else if (lastLifespanDays < RedBloodCellLifespanCalculator.MIN_NORMAL_DAYS) {
            setStatus(status, "寿命缩短", "#F44336");
        } else if (lastLifespanDays > RedBloodCellLifespanCalculator.MAX_NORMAL_DAYS) {
            setStatus(status, "寿命偏长", "#FF9800");
        } else {
            setStatus(status, "正常", "#4CAF50");
        }
    }

    private void updateNoTable() {
        setText(noDataRow, R.id.tvNO, String.format(Locale.CHINA, "%.2f", lastNO));
        setText(noDataRow, R.id.tvCO2, String.format(Locale.CHINA, "%.0f", lastCO2ForNo));
        setText(noDataRow, R.id.tvCorrected,
                String.format(Locale.CHINA, "%.2f", lastNoCorrectionFactor));
        TextView status = noDataRow.findViewById(R.id.tvStatus);
        if (lastNoRiskLevel == null) {
            setStatus(status, "系数无效", "#F44336");
        } else {
            setStatus(status, AirwayInflammationDiagnosisRules.riskLabel(lastNoRiskLevel),
                    riskColor(lastNoRiskLevel));
        }
    }

    private void updateGastrointestinalChart() {
        List<BarEntry> h2Entries = new ArrayList<>();
        List<BarEntry> ch4Entries = new ArrayList<>();
        for (int index = 0; index < GASTROINTESTINAL_CHANNEL_COUNT; index++) {
            ChannelData data = gastrointestinalData[index];
            h2Entries.add(new BarEntry(index, data == null ? 0f : data.correctedH2()));
            ch4Entries.add(new BarEntry(index, data == null ? 0f : data.correctedCH4()));
        }

        List<com.github.mikephil.charting.interfaces.datasets.IBarDataSet> sets = new ArrayList<>();
        if (selectedH2) {
            BarDataSet h2Set = new BarDataSet(h2Entries, "修正后H2 (ppm)");
            h2Set.setColor(Color.parseColor("#2196F3"));
            sets.add(h2Set);
        }
        if (selectedCH4) {
            BarDataSet ch4Set = new BarDataSet(ch4Entries, "修正后CH4 (ppm)");
            ch4Set.setColor(Color.parseColor("#FF9800"));
            sets.add(ch4Set);
        }
        BarData data = new BarData(sets);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"通道1", "通道2", "通道3", "通道4",
                        "通道5", "通道6", "通道7", "通道8"}));
        xAxis.setLabelCount(GASTROINTESTINAL_CHANNEL_COUNT);
        if (sets.size() == 2) {
            data.setBarWidth(BAR_WIDTH);
            xAxis.setCenterAxisLabels(true);
            xAxis.setAxisMinimum(0f);
            xAxis.setAxisMaximum(data.getGroupWidth(GROUP_SPACE, BAR_SPACE)
                    * GASTROINTESTINAL_CHANNEL_COUNT);
            barChart.setData(data);
            barChart.groupBars(0f, GROUP_SPACE, BAR_SPACE);
        } else {
            data.setBarWidth(0.6f);
            xAxis.setCenterAxisLabels(false);
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(GASTROINTESTINAL_CHANNEL_COUNT - 0.5f);
            barChart.setData(data);
        }
        setChartDescription("胃肠道修正后浓度");
        refreshChart();
    }

    private void updateRedBloodCellChart() {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, lastCorrectedCO));
        entries.add(new BarEntry(1, totalHemoglobin));
        BarDataSet set = new BarDataSet(entries, "红细胞寿命检测数据");
        set.setColors(Color.parseColor("#2196F3"), Color.parseColor("#E91E63"));
        set.setValueTextSize(12f);
        barChart.setData(new BarData(set));
        XAxis xAxis = barChart.getXAxis();
        xAxis.setCenterAxisLabels(false);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(1.5f);
        xAxis.setLabelCount(2);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"修正后CO浓度", "全身血红蛋白总量"}));
        setChartDescription("修正后CO浓度 / 全身血红蛋白总量");
        refreshChart();
    }

    private void updateRespiratoryChart() {
        BarDataSet set = new BarDataSet(
                java.util.Collections.singletonList(new BarEntry(0, lastCorrectedNO)),
                "修正后NO浓度");
        set.setColor(Color.parseColor("#00ACC1"));
        set.setValueTextSize(12f);
        barChart.setData(new BarData(set));
        XAxis xAxis = barChart.getXAxis();
        xAxis.setCenterAxisLabels(false);
        xAxis.setAxisMinimum(-0.75f);
        xAxis.setAxisMaximum(0.75f);
        xAxis.setLabelCount(1);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"修正后NO浓度"}));
        setChartDescription("修正后NO浓度 (ppb)");
        refreshChart();
    }

    private void completeGastrointestinalDetection() {
        if (gastrointestinalCompleted) {
            return;
        }
        gastrointestinalCompleted = true;
        for (int index = 0; index < GASTROINTESTINAL_CHANNEL_COUNT; index++) {
            ChannelData data = gastrointestinalData[index];
            if (data != null) {
                updateGastrointestinalRow(index, data);
            }
        }
        String selectedGases = selectedH2 && selectedCH4
                ? "H2、CH4"
                : (selectedH2 ? "H2" : "CH4");
        gastrointestinalDiagnosis = "检测气体：" + selectedGases + "；"
                + (isGastrointestinalPositive()
                        ? "小肠细菌过度生长（SIBO）阳性。"
                        : "小肠细菌过度生长（SIBO）阴性。");
        updateDiagnosisText();
        saveGastrointestinalReport();
        startNextDetection(PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL);
    }

    private void completeRedBloodCellDetection() {
        if (redBloodCellCompleted) {
            return;
        }
        redBloodCellCompleted = true;
        if (!RedBloodCellLifespanCalculator.hasValidCorrectionFactor(lastCO2ForRbc)
                || !RedBloodCellLifespanCalculator.hasValidLifespan(
                totalHemoglobin, lastCorrectedCO)) {
            redBloodCellDiagnosis = "检测数据无效，无法计算红细胞寿命（RBCS）。";
        } else {
            redBloodCellDiagnosis = String.format(
                    Locale.CHINA,
                    "红细胞寿命（RBCS）= %.2f 天；%s。",
                    lastLifespanDays,
                    RedBloodCellLifespanCalculator.diagnosis(lastLifespanDays));
        }
        updateDiagnosisText();
        saveRedBloodCellReport();
        startNextDetection(PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL);
    }

    private void completeRespiratoryDetection() {
        if (respiratoryCompleted) {
            return;
        }
        respiratoryCompleted = true;
        respiratoryDiagnosis = lastNoRiskLevel == null
                ? "修正系数无效，无法进行FeNO气道炎症临床判断。"
                : AirwayInflammationDiagnosisRules.riskLabel(lastNoRiskLevel) + "；"
                        + AirwayInflammationDiagnosisRules.diagnosis(lastNoRiskLevel);
        updateDiagnosisText();
        saveRespiratoryReport();
        startNextDetection(PhysicalExamSelectionRouter.Detection.RESPIRATORY);
    }

    private void startNextDetection(PhysicalExamSelectionRouter.Detection completed) {
        PhysicalExamSelectionRouter.Detection next = PhysicalExamSelectionRouter.nextSelected(
                completed, selectedCH4, selectedH2, selectedCO, selectedNO);
        if (next == null) {
            currentDetection = null;
            textDetectionProgress.setText("全部检测完成");
            textDetectionProgress.setBackgroundColor(Color.parseColor("#4CAF50"));
            savePhysicalExamReport();
            return;
        }

        currentDetection = next;
        updateProgressForCurrentDetection(0);
        String command = PhysicalExamSelectionRouter.commandFor(next);
        new Thread(() -> {
            if (usbHelper != null && usbHelper.isConnected()) {
                usbHelper.sendBytes(hexStringToByteArray(command));
                runOnUiThread(() -> Toast.makeText(
                        this, "已发送下一项检测指令：" + command, Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(
                        this, "USB未连接，无法启动下一项检测", Toast.LENGTH_LONG).show());
            }
        }, "physical-exam-next-command").start();
    }

    private void updateProgressForCurrentDetection(int progress) {
        if (currentDetection == null) {
            return;
        }
        String name;
        if (currentDetection == PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL) {
            name = "胃肠道检测";
        } else if (currentDetection == PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL) {
            name = "红细胞寿命检测";
        } else {
            name = "呼吸道检测";
        }
        textDetectionProgress.setBackgroundColor(Color.parseColor("#9E9E9E"));
        textDetectionProgress.setText(name + " " + progress + "%");
    }

    private void updateDiagnosisText() {
        StringBuilder result = new StringBuilder("诊断结果：\n");
        if (selectedH2 || selectedCH4) {
            result.append("【胃肠道疾病检测】\n")
                    .append(gastrointestinalDiagnosis).append("\n\n");
        }
        if (selectedCO) {
            result.append("【红细胞寿命检测】\n")
                    .append(redBloodCellDiagnosis).append("\n\n");
        }
        if (selectedNO) {
            result.append("【呼吸道疾病检测】\n")
                    .append(respiratoryDiagnosis);
        }
        textResultInterpretation.setText(result.toString().trim());
    }

    private boolean isGastrointestinalPositive() {
        for (int index = 0; index < GASTROINTESTINAL_CHANNEL_COUNT; index++) {
            if (isGastrointestinalChannelPositive(index)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGastrointestinalChannelPositive(int channelIndex) {
        ChannelData data = gastrointestinalData[channelIndex];
        if (data == null || !data.hasValidCorrectionFactor()) {
            return false;
        }
        float h2 = selectedH2 ? data.correctedH2() : 0f;
        float ch4 = selectedCH4 ? data.correctedCH4() : 0f;
        float baselineH2 = 0f;
        float baselineCH4 = 0f;
        if (channelIndex >= 3) {
            ChannelData baseline = gastrointestinalData[0];
            if (baseline == null || !baseline.hasValidCorrectionFactor()) {
                return false;
            }
            baselineH2 = selectedH2 ? baseline.correctedH2() : 0f;
            baselineCH4 = selectedCH4 ? baseline.correctedCH4() : 0f;
        }
        return SiboDiagnosisRules.isChannelPositive(
                channelIndex, h2, ch4, baselineH2, baselineCH4);
    }

    private void savePhysicalExamReport() {
        if (physicalExamReportId < 0 || physicalExamReportSaving) {
            return;
        }
        physicalExamReportSaving = true;

        PhysicalExamReportService.GastrointestinalSection gastrointestinalSection =
                (selectedH2 || selectedCH4)
                        ? new PhysicalExamReportService.GastrointestinalSection(
                                selectedH2,
                                selectedCH4,
                                buildGastrointestinalMeasurements(),
                                gastrointestinalDiagnosis)
                        : null;
        PhysicalExamReportService.RedBloodCellSection redBloodCellSection = selectedCO
                ? new PhysicalExamReportService.RedBloodCellSection(
                        lastCO,
                        lastCO2ForRbc,
                        lastCoCorrectionFactor,
                        lastCorrectedCO,
                        totalHemoglobin,
                        lastLifespanDays,
                        redBloodCellDiagnosis)
                : null;
        PhysicalExamReportService.RespiratorySection respiratorySection = selectedNO
                ? new PhysicalExamReportService.RespiratorySection(
                        patientAge,
                        lastNO,
                        lastCO2ForNo,
                        lastNoCorrectionFactor,
                        lastCorrectedNO,
                        lastNoRiskLevel,
                        respiratoryPointCount,
                        respiratoryDiagnosis)
                : null;

        new Thread(() -> {
            try {
                PhysicalExamReportService.SaveResult result = PhysicalExamReportService.save(
                        getApplicationContext(),
                        db,
                        physicalExamReportId,
                        specimenNo,
                        gastrointestinalSection,
                        redBloodCellSection,
                        respiratorySection);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        buttonReportManage.setEnabled(true);
                        Toast.makeText(
                                this,
                                "体检诊断报告已生成：" + result.getFileName(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                physicalExamReportSaving = false;
                reportSaveFailed("体检诊断", error);
            }
        }, "physical-exam-save-combined").start();
    }

    private void saveGastrointestinalReport() {
        if (gastrointestinalReportId < 0) {
            return;
        }
        List<GastrointestinalReportService.ChannelMeasurement> measurements =
                buildGastrointestinalMeasurements();
        boolean positive = isGastrointestinalPositive();
        new Thread(() -> {
            try {
                GastrointestinalReportService.save(
                        getApplicationContext(), db, gastrointestinalReportId, specimenNo,
                        measurements, positive, gastrointestinalDiagnosis);
                onReportSaved();
            } catch (Exception error) {
                reportSaveFailed("胃肠道", error);
            }
        }, "physical-exam-save-gi").start();
    }

    private List<GastrointestinalReportService.ChannelMeasurement>
            buildGastrointestinalMeasurements() {
        List<GastrointestinalReportService.ChannelMeasurement> measurements = new ArrayList<>();
        for (ChannelData data : gastrointestinalData) {
            if (data == null) {
                continue;
            }
            measurements.add(new GastrointestinalReportService.ChannelMeasurement(
                    data.channel,
                    selectedH2 ? data.h2 : 0,
                    selectedCH4 ? data.ch4 : 0,
                    data.h2s,
                    data.co2,
                    data.correctionFactor(),
                    selectedH2 ? data.correctedH2() : 0f,
                    selectedCH4 ? data.correctedCH4() : 0f,
                    data.hasValidCorrectionFactor()));
        }
        return measurements;
    }

    private void saveRedBloodCellReport() {
        if (redBloodCellReportId < 0) {
            return;
        }
        new Thread(() -> {
            try {
                RedBloodCellLifespanReportService.save(
                        getApplicationContext(), db, redBloodCellReportId, specimenNo,
                        lastCO, lastCO2ForRbc, lastCoCorrectionFactor, lastCorrectedCO,
                        totalHemoglobin, lastLifespanDays, redBloodCellDiagnosis);
                onReportSaved();
            } catch (Exception error) {
                reportSaveFailed("红细胞寿命", error);
            }
        }, "physical-exam-save-rbc").start();
    }

    private void saveRespiratoryReport() {
        if (respiratoryReportId < 0) {
            return;
        }
        new Thread(() -> {
            try {
                AirwayInflammationReportService.save(
                        getApplicationContext(), db, respiratoryReportId, specimenNo,
                        patientAge, lastNO, lastCO2ForNo, lastNoCorrectionFactor,
                        lastCorrectedNO, lastNoRiskLevel, respiratoryPointCount,
                        respiratoryDiagnosis);
                onReportSaved();
            } catch (Exception error) {
                reportSaveFailed("呼吸道", error);
            }
        }, "physical-exam-save-no").start();
    }

    private void onReportSaved() {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                buttonReportManage.setEnabled(true);
            }
        });
    }

    private void reportSaveFailed(String testName, Exception error) {
        Log.e(TAG, testName + "检测报告保存失败", error);
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this,
                        testName + "检测报告保存失败：" + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateCurrentTime() {
        textCurrentTime.setText("时间：" + new SimpleDateFormat(
                "HH:mm:ss", Locale.CHINA).format(new Date()));
        mainHandler.postDelayed(this::updateCurrentTime, 1000L);
    }

    private void setChartDescription(String value) {
        Description description = new Description();
        description.setText(value);
        barChart.setDescription(description);
    }

    private void refreshChart() {
        barChart.notifyDataSetChanged();
        barChart.invalidate();
    }

    private static void setText(TableRow row, int viewId, String value) {
        ((TextView) row.findViewById(viewId)).setText(value);
    }

    private static void setStatus(TextView view, String value, String color) {
        view.setText(value);
        view.setTextColor(Color.parseColor(color));
    }

    private static int littleEndianUnsignedShort(byte[] data, int offset) {
        return (data[offset + 1] & 0xFF) << 8 | (data[offset] & 0xFF);
    }

    private static String riskColor(AirwayInflammationDiagnosisRules.RiskLevel riskLevel) {
        if (riskLevel == AirwayInflammationDiagnosisRules.RiskLevel.LOW) {
            return "#4CAF50";
        }
        if (riskLevel == AirwayInflammationDiagnosisRules.RiskLevel.MEDIUM) {
            return "#FF9800";
        }
        return "#F44336";
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) {
            value.append(String.format(Locale.US, "%02X ", item));
        }
        return value.toString().trim();
    }

    private static byte[] hexStringToByteArray(String value) {
        String[] hex = value.split(" ");
        byte[] bytes = new byte[hex.length];
        for (int index = 0; index < hex.length; index++) {
            bytes[index] = (byte) Integer.parseInt(hex[index], 16);
        }
        return bytes;
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (usbHelper != null) {
            usbHelper.disconnect();
        }
        super.onDestroy();
    }

    private static final class ProtocolFrame {
        final int messageId;
        final byte[] payload;

        ProtocolFrame(int messageId, byte[] payload) {
            this.messageId = messageId;
            this.payload = payload;
        }

        static ProtocolFrame from(byte[] raw) {
            int start = -1;
            int end = -1;
            for (int index = 0; index < raw.length; index++) {
                if (raw[index] == 0x7E) {
                    if (start == -1) {
                        start = index;
                    } else {
                        end = index;
                        break;
                    }
                }
            }
            if (start == -1 || end == -1 || end - start < 6) {
                return null;
            }
            int frameLength = end - start - 1;
            byte[] frame = new byte[frameLength];
            System.arraycopy(raw, start + 1, frame, 0, frameLength);
            int bodyLength = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
            if (frameLength < 4 + bodyLength + 1) {
                return null;
            }
            int messageId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
            return new ProtocolFrame(messageId, frame);
        }
    }

    private static final class ChannelData {
        final int channel;
        final int h2;
        final int ch4;
        final int h2s;
        final int co2;

        ChannelData(int channel, int h2, int ch4, int h2s, int co2) {
            this.channel = channel;
            this.h2 = h2;
            this.ch4 = ch4;
            this.h2s = h2s;
            this.co2 = co2;
        }

        float correctionFactor() {
            return ConcentrationCorrection.correctionFactor(co2);
        }

        boolean hasValidCorrectionFactor() {
            return ConcentrationCorrection.hasValidCorrectionFactor(co2);
        }

        float correctedH2() {
            return ConcentrationCorrection.correctedValue(h2, co2);
        }

        float correctedCH4() {
            return ConcentrationCorrection.correctedValue(ch4, co2);
        }
    }
}
