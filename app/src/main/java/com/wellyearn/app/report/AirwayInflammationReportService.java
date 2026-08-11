package com.wellyearn.app.report;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
import java.util.Locale;

/** Saves the three airway inflammation report sections and generates its PDF. */
public final class AirwayInflammationReportService {

    public static final String REPORT_FOLDER_NAME = "呼吸道炎症检测报告";
    private static final String REPORT_TYPE_NAME = "呼吸道炎症检测";
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float PAGE_MARGIN = 42f;

    private AirwayInflammationReportService() {}

    public static SaveResult save(
            Context context,
            AppDatabase database,
            long reportId,
            String barcode,
            int patientAge,
            float noConcentration,
            AirwayInflammationDiagnosisRules.RiskLevel riskLevel,
            int dataPointsCount,
            String interpretation) throws IOException, JSONException {
        TestReport report = database.testReportDao().getReportById(reportId);
        if (report == null) {
            throw new IOException("未找到检测报告记录: " + reportId);
        }
        Patient patient = database.patientDao().getPatientById(report.getPatientId());
        if (patient == null) {
            throw new IOException("未找到患者记录: " + report.getPatientId());
        }

        boolean valid = riskLevel != null;
        String riskLabel = valid
                ? AirwayInflammationDiagnosisRules.riskLabel(riskLevel)
                : "数据无效";
        String diagnosis = valid
                ? AirwayInflammationDiagnosisRules.diagnosis(riskLevel)
                : "NO浓度数据无效，无法进行FeNO气道炎症临床判断。";
        String applicationDepartment = safe(report.getRemarks());
        String patientInfo = buildPatientInfoJson(
                barcode, patient, report, applicationDepartment).toString();
        String detectionDataChart = buildDetectionDataChartJson(
                patientAge,
                noConcentration,
                dataPointsCount).toString();
        String diagnosisResult = buildDiagnosisResultJson(
                valid,
                patientAge,
                noConcentration,
                riskLevel,
                riskLabel,
                diagnosis,
                interpretation,
                report.getTestDate()).toString();
        String fileName = buildFileName(barcode, patient.getName(), report.getTestDate());

        Uri pdfUri = null;
        try {
            pdfUri = createPdf(
                    context,
                    fileName,
                    barcode,
                    patient,
                    report,
                    applicationDepartment,
                    patientAge,
                    noConcentration,
                    riskLevel,
                    riskLabel,
                    diagnosis);

            report.setPatientInfo(patientInfo);
            report.setDetectionDataChart(detectionDataChart);
            report.setDiagnosisResult(diagnosisResult);
            report.setPdfFileName(fileName);
            report.setPdfUri(pdfUri.toString());

            report.setTestData(detectionDataChart);
            report.setTestResult(diagnosisResult);
            report.setRemarks(interpretation);
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
            TestReport report,
            String applicationDepartment) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "患者信息");
        json.put("barcode", safe(barcode));
        json.put("patientId", patient.getId());
        json.put("name", safe(patient.getName()));
        json.put("gender", safe(patient.getGender()));
        json.put("age", patient.getAge());
        json.put("phone", safe(patient.getPhone()));
        json.put("patientType", safe(patient.getPatientType()));
        json.put("applicationDoctor", safe(report.getDoctorName()));
        json.put("applicationDepartment", applicationDepartment);
        json.put("reportNumber", safe(report.getReportNumber()));
        json.put("testDate", report.getTestDate());
        return json;
    }

    private static JSONObject buildDetectionDataChartJson(
            int patientAge,
            float noConcentration,
            int dataPointsCount) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "检测数据图表");
        json.put("age", patientAge);
        json.put("ageGroup", AirwayInflammationDiagnosisRules.isAdult(patientAge) ? "成人" : "儿童");
        json.put("noConcentration", noConcentration);
        json.put("noUnit", "ppb");
        json.put("dataPointsCount", dataPointsCount);

        JSONObject chart = new JSONObject();
        chart.put("chartType", "bar");
        chart.put("title", "NO浓度");
        JSONArray categories = new JSONArray();
        categories.put("NO浓度");
        chart.put("categories", categories);
        JSONArray values = new JSONArray();
        values.put(noConcentration);
        chart.put("values", values);
        chart.put("unit", "ppb");
        json.put("chart", chart);
        return json;
    }

    private static JSONObject buildDiagnosisResultJson(
            boolean valid,
            int patientAge,
            float noConcentration,
            AirwayInflammationDiagnosisRules.RiskLevel riskLevel,
            String riskLabel,
            String diagnosis,
            String interpretation,
            long testDate) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "诊断结果");
        json.put("valid", valid);
        json.put("age", patientAge);
        json.put("ageGroup", AirwayInflammationDiagnosisRules.isAdult(patientAge) ? "成人" : "儿童");
        json.put("noConcentration", noConcentration);
        json.put("unit", "ppb");
        json.put("riskLevel", riskLevel == null ? JSONObject.NULL : riskLevel.name());
        json.put("riskLabel", riskLabel);
        json.put("clinicalStandard", AirwayInflammationDiagnosisRules.standardForAge(patientAge));
        json.put("diagnosis", diagnosis);
        json.put("interpretation", safe(interpretation));
        json.put("testDate", testDate);
        json.put("generatedAt", System.currentTimeMillis());
        return json;
    }

    private static Uri createPdf(
            Context context,
            String fileName,
            String barcode,
            Patient patient,
            TestReport report,
            String applicationDepartment,
            int patientAge,
            float noConcentration,
            AirwayInflammationDiagnosisRules.RiskLevel riskLevel,
            String riskLabel,
            String diagnosis) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/" + REPORT_FOLDER_NAME);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("无法创建呼吸道炎症诊断报告PDF");
        }

        boolean success = false;
        PdfDocument document = new PdfDocument();
        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("无法写入呼吸道炎症诊断报告PDF");
            }
            drawReportPage(
                    document,
                    barcode,
                    patient,
                    report,
                    applicationDepartment,
                    patientAge,
                    noConcentration,
                    riskLevel,
                    riskLabel,
                    diagnosis);
            document.writeTo(output);
            success = true;
        } finally {
            document.close();
            if (!success) {
                resolver.delete(uri, null, null);
            }
        }

        ContentValues completed = new ContentValues();
        completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
        try {
            int updatedRows = resolver.update(uri, completed, null, null);
            if (updatedRows <= 0) {
                throw new IOException("无法完成呼吸道炎症诊断报告PDF写入");
            }
        } catch (IOException | RuntimeException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
        return uri;
    }

    private static void drawReportPage(
            PdfDocument document,
            String barcode,
            Patient patient,
            TestReport report,
            String applicationDepartment,
            int patientAge,
            float noConcentration,
            AirwayInflammationDiagnosisRules.RiskLevel riskLevel,
            String riskLabel,
            String diagnosis) {
        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create());
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();

        drawReportHeader(canvas, paint, report);
        float y = drawSectionTitle(canvas, paint, 100f, "第一部分  患者信息");
        y = drawPatientGrid(
                canvas, paint, y, barcode, patient, report, applicationDepartment);

        y += 14f;
        y = drawSectionTitle(canvas, paint, y, "第二部分  检测数据图表");
        y = drawDataGrid(canvas, paint, y + 8f, patientAge, noConcentration);
        RectF chartBounds = new RectF(PAGE_MARGIN, y + 12f, PAGE_WIDTH - PAGE_MARGIN, y + 162f);
        drawBarChart(canvas, paint, chartBounds, patientAge, noConcentration, riskLevel);

        y = chartBounds.bottom + 14f;
        y = drawSectionTitle(canvas, paint, y, "第三部分  诊断结果");
        drawDiagnosis(canvas, paint, y + 10f, patientAge, noConcentration, riskLevel, riskLabel, diagnosis);

        drawFooter(canvas, paint);
        document.finishPage(page);
    }

    private static void drawReportHeader(Canvas canvas, Paint paint, TestReport report) {
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(22f);
        paint.setColor(Color.rgb(0, 105, 120));
        drawCenteredText(canvas, paint, "呼吸道炎症检测诊断报告", PAGE_WIDTH / 2f, 50f);

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

    private static float drawSectionTitle(Canvas canvas, Paint paint, float y, String title) {
        RectF rect = new RectF(PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, y + 28f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(224, 247, 250));
        canvas.drawRect(rect, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(13f);
        paint.setColor(Color.rgb(0, 105, 120));
        canvas.drawText(title, PAGE_MARGIN + 10f, y + 19f, paint);
        return rect.bottom;
    }

    private static float drawPatientGrid(
            Canvas canvas,
            Paint paint,
            float y,
            String barcode,
            Patient patient,
            TestReport report,
            String applicationDepartment) {
        String[][] rows = {
                {"条形码", safe(barcode), "患者姓名", safe(patient.getName())},
                {"性别", safe(patient.getGender()), "年龄", patient.getAge() + "岁"},
                {"患者类型", safe(patient.getPatientType()), "联系电话", safe(patient.getPhone())},
                {"申请医生", safe(report.getDoctorName()), "申请科室", applicationDepartment},
                {"报告编号", safe(report.getReportNumber()), "检测项目", REPORT_TYPE_NAME}
        };
        return drawKeyValueGrid(canvas, paint, y, rows, 25f);
    }

    private static float drawDataGrid(
            Canvas canvas,
            Paint paint,
            float y,
            int patientAge,
            float noConcentration) {
        String[][] rows = {
                {"NO浓度", format(noConcentration, 2) + " ppb",
                        "检测依据", "下位机上传值"},
                {"年龄分组", AirwayInflammationDiagnosisRules.isAdult(patientAge) ? "成人" : "儿童",
                        "临床阈值", AirwayInflammationDiagnosisRules.isAdult(patientAge)
                                ? "25 / 50 ppb" : "20 / 35 ppb"}
        };
        return drawKeyValueGrid(canvas, paint, y, rows, 24f);
    }

    private static float drawKeyValueGrid(
            Canvas canvas,
            Paint paint,
            float y,
            String[][] rows,
            float rowHeight) {
        float width = PAGE_WIDTH - PAGE_MARGIN * 2;
        float half = width / 2f;
        float labelWidth = 86f;
        paint.setStrokeWidth(0.7f);
        paint.setTextSize(9f);
        for (int row = 0; row < rows.length; row++) {
            float top = y + row * rowHeight;
            for (int side = 0; side < 2; side++) {
                float left = PAGE_MARGIN + side * half;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(247, 250, 250));
                canvas.drawRect(left, top, left + labelWidth, top + rowHeight, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(181, 207, 210));
                canvas.drawRect(left, top, left + half, top + rowHeight, paint);
                canvas.drawLine(left + labelWidth, top, left + labelWidth, top + rowHeight, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setColor(Color.rgb(48, 79, 83));
                drawFittedText(
                        canvas, paint, rows[row][side * 2], left + 6f, top + 16f, labelWidth - 12f);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setColor(Color.rgb(34, 48, 50));
                drawFittedText(
                        canvas,
                        paint,
                        rows[row][side * 2 + 1],
                        left + labelWidth + 6f,
                        top + 16f,
                        half - labelWidth - 12f);
            }
        }
        return y + rows.length * rowHeight;
    }

    private static void drawBarChart(
            Canvas canvas,
            Paint paint,
            RectF bounds,
            int patientAge,
            float noConcentration,
            AirwayInflammationDiagnosisRules.RiskLevel riskLevel) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(bounds, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(Color.rgb(181, 207, 210));
        canvas.drawRect(bounds, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(10.5f);
        paint.setColor(Color.rgb(40, 72, 76));
        canvas.drawText("NO浓度（ppb）", bounds.left + 12f, bounds.top + 18f, paint);

        RectF plot = new RectF(bounds.left + 42f, bounds.top + 31f, bounds.right - 24f, bounds.bottom - 25f);
        float lowThreshold = AirwayInflammationDiagnosisRules.isAdult(patientAge) ? 25f : 20f;
        float highThreshold = AirwayInflammationDiagnosisRules.isAdult(patientAge) ? 50f : 35f;
        float maxValue = Math.max(highThreshold * 1.25f, noConcentration * 1.2f);
        float axisMax = Math.max(10f, (float) Math.ceil(maxValue / 10f) * 10f);

        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(7.5f);
        for (int line = 0; line <= 4; line++) {
            float value = axisMax * line / 4f;
            float lineY = plot.bottom - plot.height() * line / 4f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f);
            paint.setColor(Color.rgb(220, 232, 234));
            canvas.drawLine(plot.left, lineY, plot.right, lineY, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(Color.rgb(87, 106, 109));
            canvas.drawText(format(value, 0), plot.left - 5f, lineY + 3f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);

        drawThresholdLine(canvas, paint, plot, lowThreshold, axisMax,
                "低/中 " + format(lowThreshold, 0));
        drawThresholdLine(canvas, paint, plot, highThreshold, axisMax,
                "中/高 " + format(highThreshold, 0));

        float centerX = plot.centerX();
        float barWidth = 76f;
        float barHeight = Math.max(0f, noConcentration) / axisMax * plot.height();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(riskColor(riskLevel));
        canvas.drawRect(centerX - barWidth / 2f, plot.bottom - barHeight,
                centerX + barWidth / 2f, plot.bottom, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(9f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.rgb(39, 57, 59));
        canvas.drawText(format(noConcentration, 2), centerX,
                Math.max(plot.top + 10f, plot.bottom - barHeight - 5f), paint);
        canvas.drawText("NO浓度", centerX, plot.bottom + 14f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawThresholdLine(
            Canvas canvas,
            Paint paint,
            RectF plot,
            float threshold,
            float axisMax,
            String label) {
        float y = plot.bottom - threshold / axisMax * plot.height();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setPathEffect(new DashPathEffect(new float[]{5f, 4f}, 0f));
        paint.setColor(Color.rgb(255, 152, 0));
        canvas.drawLine(plot.left, y, plot.right, y, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(7.5f);
        paint.setColor(Color.rgb(191, 87, 0));
        canvas.drawText(label, plot.right - 64f, y - 3f, paint);
    }

    private static void drawDiagnosis(
            Canvas canvas,
            Paint paint,
            float y,
            int patientAge,
            float noConcentration,
            AirwayInflammationDiagnosisRules.RiskLevel riskLevel,
            String riskLabel,
            String diagnosis) {
        int foreground = riskColor(riskLevel);
        int background;
        if (riskLevel == AirwayInflammationDiagnosisRules.RiskLevel.LOW) {
            background = Color.rgb(232, 245, 233);
        } else if (riskLevel == AirwayInflammationDiagnosisRules.RiskLevel.MEDIUM) {
            background = Color.rgb(255, 248, 225);
        } else if (riskLevel == AirwayInflammationDiagnosisRules.RiskLevel.HIGH) {
            background = Color.rgb(255, 235, 238);
        } else {
            background = Color.rgb(238, 238, 238);
        }

        RectF box = new RectF(PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, y + 42f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        canvas.drawRoundRect(box, 8f, 8f, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(15f);
        paint.setColor(foreground);
        String result = String.format(Locale.CHINA, "FeNO %.2f ppb - %s", noConcentration, riskLabel);
        drawCenteredText(canvas, paint, result, PAGE_WIDTH / 2f, y + 27f);

        float textY = y + 62f;
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(9.5f);
        paint.setColor(Color.rgb(55, 70, 72));
        canvas.drawText("临床标准：" + AirwayInflammationDiagnosisRules.standardForAge(patientAge),
                PAGE_MARGIN, textY, paint);
        textY += 19f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        canvas.drawText("临床判断：", PAGE_MARGIN, textY, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textY = drawWrappedText(
                canvas,
                paint,
                diagnosis,
                PAGE_MARGIN + 48f,
                textY,
                PAGE_WIDTH - PAGE_MARGIN * 2 - 48f,
                16f);
        paint.setTextSize(8.5f);
        paint.setColor(Color.rgb(90, 103, 105));
        canvas.drawText("说明：FeNO结果需结合临床症状、病史及其他检查综合判断。",
                PAGE_MARGIN, textY + 6f, paint);
    }

    private static int riskColor(AirwayInflammationDiagnosisRules.RiskLevel riskLevel) {
        if (riskLevel == null) return Color.rgb(97, 97, 97);
        switch (riskLevel) {
            case LOW:
                return Color.rgb(46, 125, 50);
            case MEDIUM:
                return Color.rgb(245, 124, 0);
            case HIGH:
                return Color.rgb(198, 40, 40);
            default:
                return Color.rgb(97, 97, 97);
        }
    }

    private static float drawWrappedText(
            Canvas canvas,
            Paint paint,
            String text,
            float x,
            float y,
            float maxWidth,
            float lineHeight) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            String candidate = line.toString() + character;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), x, y, paint);
                y += lineHeight;
                line.setLength(0);
            }
            line.append(character);
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private static void drawFooter(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.6f);
        paint.setColor(Color.rgb(198, 216, 219));
        canvas.drawLine(PAGE_MARGIN, PAGE_HEIGHT - 34f, PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 34f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(8f);
        paint.setColor(Color.rgb(91, 106, 108));
        canvas.drawText("WellYearn 呼吸道炎症检测报告", PAGE_MARGIN, PAGE_HEIGHT - 20f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("第 1 / 1 页", PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 20f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawCenteredText(
            Canvas canvas,
            Paint paint,
            String text,
            float centerX,
            float baselineY) {
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, centerX, baselineY, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawFittedText(
            Canvas canvas,
            Paint paint,
            String text,
            float x,
            float baselineY,
            float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, baselineY, paint);
            return;
        }
        String ellipsis = "...";
        StringBuilder fitted = new StringBuilder(text);
        while (fitted.length() > 0
                && paint.measureText(fitted.toString() + ellipsis) > maxWidth) {
            fitted.deleteCharAt(fitted.length() - 1);
        }
        canvas.drawText(fitted + ellipsis, x, baselineY, paint);
    }

    private static Paint basePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(Color.BLACK);
        return paint;
    }

    private static String format(float value, int decimals) {
        return String.format(Locale.CHINA, "%." + decimals + "f", value);
    }

    private static String sanitizeFileNamePart(String value) {
        String result = safe(value)
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .trim();
        return result.isEmpty() ? "未知" : result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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
