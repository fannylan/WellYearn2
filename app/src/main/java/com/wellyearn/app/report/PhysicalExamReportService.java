package com.wellyearn.app.report;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.wellyearn.app.AirwayInflammationDiagnosisRules;
import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Patient;
import com.wellyearn.app.database.entity.TestReport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Persists and renders one combined physical-exam report for the selected tests. */
public final class PhysicalExamReportService {

    public static final String REPORT_FOLDER_NAME = "体检报告";
    private static final String REPORT_TYPE_NAME = "体检诊断报告";
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float PAGE_MARGIN = 42f;

    private PhysicalExamReportService() {
    }

    public static SaveResult save(
            Context context,
            AppDatabase database,
            long reportId,
            String barcode,
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) throws IOException, JSONException {
        TestReport report = database.testReportDao().getReportById(reportId);
        if (report == null) {
            throw new IOException("未找到体检报告记录: " + reportId);
        }
        Patient patient = database.patientDao().getPatientById(report.getPatientId());
        if (patient == null) {
            throw new IOException("未找到患者记录: " + report.getPatientId());
        }
        if (gastrointestinal == null && redBloodCell == null && respiratory == null) {
            throw new IOException("体检报告没有可写入的检测项目");
        }

        JSONObject patientInfo = buildPatientInfoJson(barcode, patient, report);
        JSONObject detectionData = buildDetectionDataJson(
                gastrointestinal, redBloodCell, respiratory);
        JSONObject diagnosisResult = buildDiagnosisJson(
                gastrointestinal, redBloodCell, respiratory);
        String diagnosisText = buildCombinedDiagnosisText(
                gastrointestinal, redBloodCell, respiratory);
        String fileName = buildFileName(barcode, patient.getName(), report.getTestDate());

        Uri pdfUri = null;
        try {
            pdfUri = createPdf(
                    context,
                    fileName,
                    barcode,
                    patient,
                    report,
                    gastrointestinal,
                    redBloodCell,
                    respiratory);

            report.setPatientInfo(patientInfo.toString());
            report.setDetectionDataChart(detectionData.toString());
            report.setDiagnosisResult(diagnosisResult.toString());
            report.setPdfFileName(fileName);
            report.setPdfUri(pdfUri.toString());
            report.setTestData(detectionData.toString());
            report.setTestResult(diagnosisResult.toString());
            report.setRemarks(diagnosisText);
            database.runInTransaction(() -> database.testReportDao().update(report));
            return new SaveResult(fileName, pdfUri.toString());
        } catch (IOException | RuntimeException error) {
            if (pdfUri != null) {
                context.getContentResolver().delete(pdfUri, null, null);
            }
            throw error;
        }
    }

    public static String buildFileName(String barcode, String patientName, long reportDate) {
        String date = new SimpleDateFormat("yyyyMMdd", Locale.CHINA)
                .format(new Date(reportDate));
        return sanitizeFileNamePart(barcode)
                + sanitizeFileNamePart(patientName)
                + date
                + REPORT_TYPE_NAME
                + ".pdf";
    }

    private static JSONObject buildPatientInfoJson(
            String barcode,
            Patient patient,
            TestReport report) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "患者信息");
        json.put("barcode", safe(barcode));
        json.put("patientId", patient.getId());
        json.put("name", safe(patient.getName()));
        json.put("gender", safe(patient.getGender()));
        json.put("age", patient.getAge());
        json.put("phone", safe(patient.getPhone()));
        json.put("patientType", safe(patient.getPatientType()));
        json.put("doctor", safe(report.getDoctorName()));
        json.put("reportNumber", safe(report.getReportNumber()));
        json.put("testDate", report.getTestDate());
        return json;
    }

    private static JSONObject buildDetectionDataJson(
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("section", "检测数据图表");
        JSONArray selectedTests = new JSONArray();

        if (gastrointestinal != null) {
            selectedTests.put("胃肠道疾病检测");
            JSONObject section = new JSONObject();
            JSONArray gases = new JSONArray();
            if (gastrointestinal.selectedH2) gases.put("H2");
            if (gastrointestinal.selectedCH4) gases.put("CH4");
            section.put("selectedGases", gases);
            section.put("chartType", "groupedBar");
            JSONArray channels = new JSONArray();
            for (GastrointestinalReportService.ChannelMeasurement measurement
                    : gastrointestinal.measurements) {
                JSONObject row = new JSONObject();
                row.put("channel", measurement.channel);
                row.put("h2", gastrointestinal.selectedH2
                        ? measurement.h2 : JSONObject.NULL);
                row.put("correctedH2", gastrointestinal.selectedH2
                        ? measurement.correctedH2 : JSONObject.NULL);
                row.put("ch4", gastrointestinal.selectedCH4
                        ? measurement.ch4 : JSONObject.NULL);
                row.put("correctedCH4", gastrointestinal.selectedCH4
                        ? measurement.correctedCh4 : JSONObject.NULL);
                row.put("co2", measurement.co2);
                row.put("correctionFactor", measurement.correctionFactor);
                channels.put(row);
            }
            section.put("channels", channels);
            root.put("gastrointestinal", section);
        }

        if (redBloodCell != null) {
            selectedTests.put("红细胞寿命检测");
            JSONObject section = new JSONObject();
            section.put("originalCO", redBloodCell.originalCO);
            section.put("co2", redBloodCell.co2);
            section.put("correctionFactor", redBloodCell.correctionFactor);
            section.put("correctedCO", redBloodCell.correctedCO);
            section.put("totalHemoglobin", redBloodCell.totalHemoglobin);
            section.put("lifespanDays", redBloodCell.lifespanDays);
            section.put("chartType", "bar");
            root.put("redBloodCell", section);
        }

        if (respiratory != null) {
            selectedTests.put("呼吸道炎症检测");
            JSONObject section = new JSONObject();
            section.put("age", respiratory.patientAge);
            section.put("ageGroup", AirwayInflammationDiagnosisRules.isAdult(
                    respiratory.patientAge) ? "成人" : "儿童");
            section.put("noConcentration", respiratory.noConcentration);
            section.put("dataPointsCount", respiratory.dataPointsCount);
            section.put("chartType", "bar");
            root.put("respiratory", section);
        }
        root.put("selectedTests", selectedTests);
        return root;
    }

    private static JSONObject buildDiagnosisJson(
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("section", "诊断结果");
        JSONArray sections = new JSONArray();
        if (gastrointestinal != null) {
            sections.put(diagnosisSection("胃肠道疾病检测", gastrointestinal.diagnosis));
        }
        if (redBloodCell != null) {
            sections.put(diagnosisSection("红细胞寿命检测", redBloodCell.diagnosis));
        }
        if (respiratory != null) {
            JSONObject section = diagnosisSection("呼吸道炎症检测", respiratory.diagnosis);
            section.put("clinicalStandard",
                    AirwayInflammationDiagnosisRules.standardForAge(respiratory.patientAge));
            section.put("riskLevel", respiratory.riskLevel == null
                    ? JSONObject.NULL
                    : AirwayInflammationDiagnosisRules.riskLabel(respiratory.riskLevel));
            sections.put(section);
        }
        root.put("selectedSections", sections);
        root.put("combinedText", buildCombinedDiagnosisText(
                gastrointestinal, redBloodCell, respiratory));
        root.put("generatedAt", System.currentTimeMillis());
        return root;
    }

    private static JSONObject diagnosisSection(String type, String diagnosis)
            throws JSONException {
        JSONObject section = new JSONObject();
        section.put("testType", type);
        section.put("diagnosis", safe(diagnosis));
        return section;
    }

    private static String buildCombinedDiagnosisText(
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) {
        StringBuilder value = new StringBuilder();
        appendDiagnosis(value, "胃肠道疾病检测",
                gastrointestinal == null ? null : gastrointestinal.diagnosis);
        appendDiagnosis(value, "红细胞寿命检测",
                redBloodCell == null ? null : redBloodCell.diagnosis);
        appendDiagnosis(value, "呼吸道炎症检测",
                respiratory == null ? null : respiratory.diagnosis);
        return value.toString().trim();
    }

    private static void appendDiagnosis(StringBuilder value, String title, String diagnosis) {
        if (diagnosis == null) return;
        if (value.length() > 0) value.append("\n\n");
        value.append("【").append(title).append("】\n").append(safe(diagnosis));
    }

    private static Uri createPdf(
            Context context,
            String fileName,
            String barcode,
            Patient patient,
            TestReport report,
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/" + REPORT_FOLDER_NAME);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) {
            throw new IOException("无法创建体检诊断报告PDF");
        }

        boolean success = false;
        PdfDocument document = new PdfDocument();
        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) throw new IOException("无法写入体检诊断报告PDF");
            int pageNumber = 1;
            drawSummaryPage(
                    document,
                    pageNumber++,
                    barcode,
                    patient,
                    report,
                    gastrointestinal,
                    redBloodCell,
                    respiratory);
            if (gastrointestinal != null) {
                drawGastrointestinalPage(document, pageNumber++, gastrointestinal);
            }
            if (redBloodCell != null) {
                drawRedBloodCellPage(document, pageNumber++, redBloodCell);
            }
            if (respiratory != null) {
                drawRespiratoryPage(document, pageNumber, respiratory);
            }
            document.writeTo(output);
            success = true;
        } finally {
            document.close();
            if (!success) resolver.delete(uri, null, null);
        }

        ContentValues completed = new ContentValues();
        completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
        try {
            if (resolver.update(uri, completed, null, null) <= 0) {
                throw new IOException("无法完成体检诊断报告PDF写入");
            }
        } catch (IOException | RuntimeException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
        return uri;
    }

    private static void drawSummaryPage(
            PdfDocument document,
            int pageNumber,
            String barcode,
            Patient patient,
            TestReport report,
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) {
        PdfDocument.Page page = startPage(document, pageNumber);
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();
        drawHeader(canvas, paint, report, "体检诊断报告");

        float y = drawSectionTitle(canvas, paint, 100f, "第一部分  患者信息");
        String[][] patientRows = {
                {"条形码", safe(barcode), "患者姓名", safe(patient.getName())},
                {"性别", safe(patient.getGender()), "年龄", patient.getAge() + "岁"},
                {"患者类型", safe(patient.getPatientType()), "联系电话", safe(patient.getPhone())},
                {"申请医生", safe(report.getDoctorName()), "报告编号", safe(report.getReportNumber())}
        };
        y = drawKeyValueGrid(canvas, paint, y, patientRows, 27f);

        y += 16f;
        y = drawSectionTitle(canvas, paint, y, "第二部分  检测项目");
        String selected = selectedTestNames(gastrointestinal, redBloodCell, respiratory);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(11f);
        paint.setColor(Color.rgb(49, 61, 73));
        y = drawWrappedText(canvas, paint, selected, PAGE_MARGIN + 8f, y + 23f,
                PAGE_WIDTH - PAGE_MARGIN * 2 - 16f, 18f);

        y += 18f;
        y = drawSectionTitle(canvas, paint, y, "第三部分  诊断结果");
        y += 18f;
        y = drawDiagnosisBlock(canvas, paint, y, "胃肠道疾病检测",
                gastrointestinal == null ? null : gastrointestinal.diagnosis);
        y = drawDiagnosisBlock(canvas, paint, y, "红细胞寿命检测",
                redBloodCell == null ? null : redBloodCell.diagnosis);
        drawDiagnosisBlock(canvas, paint, y, "呼吸道炎症检测",
                respiratory == null ? null : respiratory.diagnosis);
        drawFooter(canvas, paint, pageNumber);
        document.finishPage(page);
    }

    private static void drawGastrointestinalPage(
            PdfDocument document,
            int pageNumber,
            GastrointestinalSection section) {
        PdfDocument.Page page = startPage(document, pageNumber);
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();
        drawSimpleHeader(canvas, paint, "检测数据 - 胃肠道疾病检测");
        String gases = (section.selectedH2 ? "H2" : "")
                + (section.selectedH2 && section.selectedCH4 ? "、" : "")
                + (section.selectedCH4 ? "CH4" : "");
        paint.setTextSize(10.5f);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("本次勾选气体：" + gases, PAGE_MARGIN, 88f, paint);

        String[] headers = {"通道", "H2", "修正H2", "CH4", "修正CH4", "CO2", "修正系数"};
        float[] widths = {45f, 66f, 79f, 66f, 79f, 80f, 88f};
        float y = 108f;
        drawTableRow(canvas, paint, y, 25f, headers, widths, true);
        y += 25f;
        for (GastrointestinalReportService.ChannelMeasurement measurement : section.measurements) {
            String[] row = {
                    String.valueOf(measurement.channel),
                    section.selectedH2 ? format(measurement.h2, 1) : "--",
                    section.selectedH2 ? format(measurement.correctedH2, 1) : "--",
                    section.selectedCH4 ? format(measurement.ch4, 2) : "--",
                    section.selectedCH4 ? format(measurement.correctedCh4, 2) : "--",
                    format(measurement.co2, 0),
                    format(measurement.correctionFactor, 2)
            };
            drawTableRow(canvas, paint, y, 25f, row, widths, false);
            y += 25f;
        }
        RectF chart = new RectF(PAGE_MARGIN, y + 24f, PAGE_WIDTH - PAGE_MARGIN, y + 250f);
        drawGastrointestinalChart(canvas, paint, chart, section);
        drawFooter(canvas, paint, pageNumber);
        document.finishPage(page);
    }

    private static void drawRedBloodCellPage(
            PdfDocument document,
            int pageNumber,
            RedBloodCellSection section) {
        PdfDocument.Page page = startPage(document, pageNumber);
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();
        drawSimpleHeader(canvas, paint, "检测数据 - 红细胞寿命检测");
        float y = drawSectionTitle(canvas, paint, 95f, "检测数据");
        String[][] rows = {
                {"CO原始浓度", format(section.originalCO, 2) + " ppm", "CO2浓度", format(section.co2, 0) + " ppm"},
                {"修正系数", format(section.correctionFactor, 2), "修正后CO", format(section.correctedCO, 2) + " ppm"},
                {"全身血红蛋白总量", format(section.totalHemoglobin, 2), "红细胞寿命", format(section.lifespanDays, 2) + " 天"}
        };
        y = drawKeyValueGrid(canvas, paint, y, rows, 30f);
        RectF chart = new RectF(PAGE_MARGIN, y + 28f, PAGE_WIDTH - PAGE_MARGIN, y + 270f);
        drawSimpleBarChart(
                canvas,
                paint,
                chart,
                new String[]{"修正后CO", "全身血红蛋白总量"},
                new float[]{section.correctedCO, section.totalHemoglobin},
                new int[]{Color.rgb(33, 150, 243), Color.rgb(233, 30, 99)});
        y = chart.bottom + 24f;
        drawSectionTitle(canvas, paint, y, "诊断结果");
        paint.setTextSize(11f);
        paint.setColor(Color.rgb(49, 61, 73));
        drawWrappedText(canvas, paint, safe(section.diagnosis), PAGE_MARGIN + 8f,
                y + 51f, PAGE_WIDTH - PAGE_MARGIN * 2 - 16f, 18f);
        drawFooter(canvas, paint, pageNumber);
        document.finishPage(page);
    }

    private static void drawRespiratoryPage(
            PdfDocument document,
            int pageNumber,
            RespiratorySection section) {
        PdfDocument.Page page = startPage(document, pageNumber);
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();
        drawSimpleHeader(canvas, paint, "检测数据 - 呼吸道炎症检测");
        float y = drawSectionTitle(canvas, paint, 95f, "检测数据");
        String[][] rows = {
                {"年龄分组", AirwayInflammationDiagnosisRules.isAdult(section.patientAge) ? "成人" : "儿童", "数据点", String.valueOf(section.dataPointsCount)},
                {"NO浓度", format(section.noConcentration, 2) + " ppb", "检测依据", "下位机上传值"}
        };
        y = drawKeyValueGrid(canvas, paint, y, rows, 30f);
        RectF chart = new RectF(PAGE_MARGIN, y + 28f, PAGE_WIDTH - PAGE_MARGIN, y + 260f);
        drawSimpleBarChart(
                canvas,
                paint,
                chart,
                new String[]{"NO浓度"},
                new float[]{section.noConcentration},
                new int[]{Color.rgb(0, 172, 193)});
        y = chart.bottom + 22f;
        y = drawSectionTitle(canvas, paint, y, "诊断结果");
        paint.setTextSize(10.5f);
        paint.setColor(Color.rgb(49, 61, 73));
        y = drawWrappedText(canvas, paint,
                AirwayInflammationDiagnosisRules.standardForAge(section.patientAge),
                PAGE_MARGIN + 8f, y + 24f,
                PAGE_WIDTH - PAGE_MARGIN * 2 - 16f, 17f);
        drawWrappedText(canvas, paint, safe(section.diagnosis), PAGE_MARGIN + 8f,
                y + 12f, PAGE_WIDTH - PAGE_MARGIN * 2 - 16f, 17f);
        drawFooter(canvas, paint, pageNumber);
        document.finishPage(page);
    }

    private static PdfDocument.Page startPage(PdfDocument document, int pageNumber) {
        return document.startPage(new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
    }

    private static Paint basePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private static void drawHeader(Canvas canvas, Paint paint, TestReport report, String title) {
        drawSimpleHeader(canvas, paint, title);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(9.5f);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("报告编号：" + safe(report.getReportNumber()), PAGE_MARGIN, 77f, paint);
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .format(new Date(report.getTestDate()));
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("检测日期：" + date, PAGE_WIDTH - PAGE_MARGIN, 77f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawSimpleHeader(Canvas canvas, Paint paint, String title) {
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(22f);
        paint.setColor(Color.rgb(25, 92, 122));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, PAGE_WIDTH / 2f, 52f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static float drawSectionTitle(Canvas canvas, Paint paint, float y, String title) {
        RectF rect = new RectF(PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, y + 28f);
        paint.setColor(Color.rgb(229, 242, 247));
        canvas.drawRect(rect, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(13f);
        paint.setColor(Color.rgb(25, 92, 122));
        canvas.drawText(title, PAGE_MARGIN + 10f, y + 19f, paint);
        return rect.bottom;
    }

    private static float drawKeyValueGrid(
            Canvas canvas,
            Paint paint,
            float y,
            String[][] rows,
            float rowHeight) {
        float width = PAGE_WIDTH - PAGE_MARGIN * 2;
        float half = width / 2f;
        float labelWidth = 78f;
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            float top = y + rowIndex * rowHeight;
            for (int side = 0; side < 2; side++) {
                float left = PAGE_MARGIN + side * half;
                paint.setColor(Color.rgb(247, 249, 251));
                canvas.drawRect(left, top, left + labelWidth, top + rowHeight, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(0.7f);
                paint.setColor(Color.rgb(190, 200, 210));
                canvas.drawRect(left, top, left + half, top + rowHeight, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setTextSize(9f);
                paint.setColor(Color.rgb(60, 72, 84));
                canvas.drawText(rows[rowIndex][side * 2], left + 6f, top + 18f, paint);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                canvas.drawText(rows[rowIndex][side * 2 + 1],
                        left + labelWidth + 6f, top + 18f, paint);
            }
        }
        return y + rows.length * rowHeight;
    }

    private static float drawDiagnosisBlock(
            Canvas canvas,
            Paint paint,
            float y,
            String title,
            String diagnosis) {
        if (diagnosis == null) return y;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(12f);
        paint.setColor(Color.rgb(25, 92, 122));
        canvas.drawText("【" + title + "】", PAGE_MARGIN + 8f, y, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(10.5f);
        paint.setColor(Color.rgb(49, 61, 73));
        return drawWrappedText(canvas, paint, safe(diagnosis), PAGE_MARGIN + 8f,
                y + 21f, PAGE_WIDTH - PAGE_MARGIN * 2 - 16f, 17f) + 18f;
    }

    private static void drawTableRow(
            Canvas canvas,
            Paint paint,
            float y,
            float height,
            String[] values,
            float[] widths,
            boolean header) {
        float x = PAGE_MARGIN;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(header ? 8.3f : 8f);
        paint.setTypeface(Typeface.create("sans-serif",
                header ? Typeface.BOLD : Typeface.NORMAL));
        for (int index = 0; index < values.length; index++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(header ? Color.rgb(225, 237, 244) : Color.WHITE);
            canvas.drawRect(x, y, x + widths[index], y + height, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.6f);
            paint.setColor(Color.rgb(185, 197, 207));
            canvas.drawRect(x, y, x + widths[index], y + height, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(48, 61, 73));
            canvas.drawText(values[index], x + widths[index] / 2f, y + 16.5f, paint);
            x += widths[index];
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawGastrointestinalChart(
            Canvas canvas,
            Paint paint,
            RectF bounds,
            GastrointestinalSection section) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.rgb(170, 180, 190));
        canvas.drawRect(bounds, paint);
        paint.setStyle(Paint.Style.FILL);
        float max = 1f;
        for (GastrointestinalReportService.ChannelMeasurement measurement
                : section.measurements) {
            if (section.selectedH2) max = Math.max(max, measurement.correctedH2);
            if (section.selectedCH4) max = Math.max(max, measurement.correctedCh4);
        }
        float baseline = bounds.bottom - 28f;
        float usableHeight = bounds.height() - 52f;
        float groupWidth = bounds.width() / Math.max(section.measurements.size(), 1);
        int seriesCount = (section.selectedH2 ? 1 : 0) + (section.selectedCH4 ? 1 : 0);
        float barWidth = Math.min(19f, groupWidth / (seriesCount + 1f));
        for (int index = 0; index < section.measurements.size(); index++) {
            GastrointestinalReportService.ChannelMeasurement measurement =
                    section.measurements.get(index);
            float center = bounds.left + groupWidth * (index + 0.5f);
            int seriesIndex = 0;
            if (section.selectedH2) {
                drawBar(canvas, paint, center, baseline, barWidth, usableHeight,
                        measurement.correctedH2, max, seriesIndex++, seriesCount,
                        Color.rgb(33, 150, 243));
            }
            if (section.selectedCH4) {
                drawBar(canvas, paint, center, baseline, barWidth, usableHeight,
                        measurement.correctedCh4, max, seriesIndex, seriesCount,
                        Color.rgb(255, 152, 0));
            }
            paint.setTextSize(7.5f);
            paint.setColor(Color.DKGRAY);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(measurement.channel), center, bounds.bottom - 10f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(9f);
        paint.setColor(Color.rgb(33, 150, 243));
        canvas.drawText(section.selectedH2 ? "■ 修正后H2" : "", bounds.left + 8f, bounds.top + 15f, paint);
        if (section.selectedCH4) {
            paint.setColor(Color.rgb(255, 152, 0));
            canvas.drawText("■ 修正后CH4", bounds.left + 100f, bounds.top + 15f, paint);
        }
    }

    private static void drawBar(
            Canvas canvas,
            Paint paint,
            float center,
            float baseline,
            float width,
            float usableHeight,
            float value,
            float max,
            int seriesIndex,
            int seriesCount,
            int color) {
        float totalWidth = width * seriesCount;
        float left = center - totalWidth / 2f + seriesIndex * width;
        float height = usableHeight * value / max;
        paint.setColor(color);
        canvas.drawRect(left, baseline - height, left + width - 2f, baseline, paint);
    }

    private static void drawSimpleBarChart(
            Canvas canvas,
            Paint paint,
            RectF bounds,
            String[] labels,
            float[] values,
            int[] colors) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.rgb(170, 180, 190));
        canvas.drawRect(bounds, paint);
        paint.setStyle(Paint.Style.FILL);
        float max = 1f;
        for (float value : values) max = Math.max(max, value);
        float baseline = bounds.bottom - 42f;
        float areaHeight = bounds.height() - 65f;
        float slot = bounds.width() / values.length;
        for (int index = 0; index < values.length; index++) {
            float center = bounds.left + slot * (index + 0.5f);
            float barWidth = Math.min(70f, slot * 0.4f);
            float height = areaHeight * values[index] / max;
            paint.setColor(colors[index]);
            canvas.drawRoundRect(new RectF(
                    center - barWidth / 2f,
                    baseline - height,
                    center + barWidth / 2f,
                    baseline), 4f, 4f, paint);
            paint.setColor(Color.rgb(49, 61, 73));
            paint.setTextSize(9f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(format(values[index], 2), center, baseline - height - 7f, paint);
            canvas.drawText(labels[index], center, bounds.bottom - 18f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static float drawWrappedText(
            Canvas canvas,
            Paint paint,
            String text,
            float x,
            float y,
            float maxWidth,
            float lineHeight) {
        if (text == null || text.isEmpty()) return y;
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n') {
                canvas.drawText(line.toString(), x, y, paint);
                line.setLength(0);
                y += lineHeight;
                continue;
            }
            line.append(character);
            if (paint.measureText(line.toString()) > maxWidth) {
                char overflow = line.charAt(line.length() - 1);
                line.deleteCharAt(line.length() - 1);
                canvas.drawText(line.toString(), x, y, paint);
                line.setLength(0);
                line.append(overflow);
                y += lineHeight;
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private static void drawFooter(Canvas canvas, Paint paint, int pageNumber) {
        paint.setTextSize(8.5f);
        paint.setColor(Color.GRAY);
        canvas.drawText("WellYearn 体检诊断报告", PAGE_MARGIN, PAGE_HEIGHT - 25f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("第 " + pageNumber + " 页", PAGE_WIDTH - PAGE_MARGIN,
                PAGE_HEIGHT - 25f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static String selectedTestNames(
            GastrointestinalSection gastrointestinal,
            RedBloodCellSection redBloodCell,
            RespiratorySection respiratory) {
        StringBuilder value = new StringBuilder();
        if (gastrointestinal != null) {
            value.append("胃肠道疾病检测（");
            if (gastrointestinal.selectedH2) value.append("H2");
            if (gastrointestinal.selectedH2 && gastrointestinal.selectedCH4) value.append("、");
            if (gastrointestinal.selectedCH4) value.append("CH4");
            value.append("）");
        }
        if (redBloodCell != null) appendTestName(value, "红细胞寿命检测（CO）");
        if (respiratory != null) appendTestName(value, "呼吸道炎症检测（NO）");
        return value.toString();
    }

    private static void appendTestName(StringBuilder value, String name) {
        if (value.length() > 0) value.append("；");
        value.append(name);
    }

    private static String format(float value, int decimals) {
        return String.format(Locale.CHINA, "%." + decimals + "f", value);
    }

    private static String sanitizeFileNamePart(String value) {
        String safe = safe(value).replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isEmpty() ? "未填写" : safe;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class GastrointestinalSection {
        public final boolean selectedH2;
        public final boolean selectedCH4;
        public final List<GastrointestinalReportService.ChannelMeasurement> measurements;
        public final String diagnosis;

        public GastrointestinalSection(
                boolean selectedH2,
                boolean selectedCH4,
                List<GastrointestinalReportService.ChannelMeasurement> measurements,
                String diagnosis) {
            this.selectedH2 = selectedH2;
            this.selectedCH4 = selectedCH4;
            this.measurements = measurements;
            this.diagnosis = diagnosis;
        }
    }

    public static final class RedBloodCellSection {
        public final float originalCO;
        public final float co2;
        public final float correctionFactor;
        public final float correctedCO;
        public final float totalHemoglobin;
        public final float lifespanDays;
        public final String diagnosis;

        public RedBloodCellSection(
                float originalCO,
                float co2,
                float correctionFactor,
                float correctedCO,
                float totalHemoglobin,
                float lifespanDays,
                String diagnosis) {
            this.originalCO = originalCO;
            this.co2 = co2;
            this.correctionFactor = correctionFactor;
            this.correctedCO = correctedCO;
            this.totalHemoglobin = totalHemoglobin;
            this.lifespanDays = lifespanDays;
            this.diagnosis = diagnosis;
        }
    }

    public static final class RespiratorySection {
        public final int patientAge;
        public final float noConcentration;
        public final AirwayInflammationDiagnosisRules.RiskLevel riskLevel;
        public final int dataPointsCount;
        public final String diagnosis;

        public RespiratorySection(
                int patientAge,
                float noConcentration,
                AirwayInflammationDiagnosisRules.RiskLevel riskLevel,
                int dataPointsCount,
                String diagnosis) {
            this.patientAge = patientAge;
            this.noConcentration = noConcentration;
            this.riskLevel = riskLevel;
            this.dataPointsCount = dataPointsCount;
            this.diagnosis = diagnosis;
        }
    }

    public static final class SaveResult {
        private final String fileName;
        private final String uri;

        SaveResult(String fileName, String uri) {
            this.fileName = fileName;
            this.uri = uri;
        }

        public String getFileName() {
            return fileName;
        }

        public String getUri() {
            return uri;
        }
    }
}
