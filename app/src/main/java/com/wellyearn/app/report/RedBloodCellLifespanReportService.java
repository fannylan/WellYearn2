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

import com.wellyearn.app.RedBloodCellLifespanCalculator;
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

/** Saves the three red blood cell lifespan report sections and generates its PDF. */
public final class RedBloodCellLifespanReportService {

    public static final String REPORT_FOLDER_NAME = "红细胞寿命检测报告";
    private static final String REPORT_TYPE_NAME = "红细胞寿命检测";
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float PAGE_MARGIN = 42f;

    private RedBloodCellLifespanReportService() {}

    public static SaveResult save(
            Context context,
            AppDatabase database,
            long reportId,
            String barcode,
            float originalCo,
            float co2,
            float correctionFactor,
            float correctedCo,
            float totalHemoglobin,
            float lifespanDays,
            String interpretation) throws IOException, JSONException {
        TestReport report = database.testReportDao().getReportById(reportId);
        if (report == null) {
            throw new IOException("未找到检测报告记录: " + reportId);
        }
        Patient patient = database.patientDao().getPatientById(report.getPatientId());
        if (patient == null) {
            throw new IOException("未找到患者记录: " + report.getPatientId());
        }

        boolean validLifespan = RedBloodCellLifespanCalculator.hasValidLifespan(
                totalHemoglobin, correctedCo);
        String diagnosis = validLifespan
                ? RedBloodCellLifespanCalculator.diagnosis(lifespanDays)
                : "检测数据无效，无法计算红细胞寿命";
        String applicationDepartment = safe(report.getRemarks());
        String patientInfo = buildPatientInfoJson(
                barcode, patient, report, applicationDepartment).toString();
        String detectionDataChart = buildDetectionDataChartJson(
                originalCo, co2, correctionFactor, correctedCo, totalHemoglobin).toString();
        String diagnosisResult = buildDiagnosisResultJson(
                validLifespan, lifespanDays, diagnosis, interpretation, report.getTestDate()).toString();
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
                    originalCo,
                    co2,
                    correctionFactor,
                    correctedCo,
                    totalHemoglobin,
                    lifespanDays,
                    validLifespan,
                    diagnosis);

            report.setPatientInfo(patientInfo);
            report.setDetectionDataChart(detectionDataChart);
            report.setDiagnosisResult(diagnosisResult);
            report.setPdfFileName(fileName);
            report.setPdfUri(pdfUri.toString());

            // Preserve compatibility with existing report query code.
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
            float originalCo,
            float co2,
            float correctionFactor,
            float correctedCo,
            float totalHemoglobin) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "检测数据图表");
        json.put("originalCo", originalCo);
        json.put("co2", co2);
        json.put("correctionFactor", correctionFactor);
        json.put("correctedCo", correctedCo);
        json.put("totalHemoglobin", totalHemoglobin);
        json.put("correctionFormula", "correctionFactor=co2/500");
        json.put("correctedCoFormula", "correctedCo=originalCo/correctionFactor");

        JSONObject chart = new JSONObject();
        chart.put("chartType", "bar");
        chart.put("title", "修正后CO浓度和全身血红蛋白总量");
        JSONArray categories = new JSONArray();
        categories.put("修正后CO浓度");
        categories.put("全身血红蛋白总量");
        chart.put("categories", categories);
        JSONArray values = new JSONArray();
        values.put(correctedCo);
        values.put(totalHemoglobin);
        chart.put("values", values);
        json.put("chart", chart);
        return json;
    }

    private static JSONObject buildDiagnosisResultJson(
            boolean valid,
            float lifespanDays,
            String diagnosis,
            String interpretation,
            long testDate) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "诊断结果");
        json.put("valid", valid);
        if (valid) {
            json.put("rbcsDays", lifespanDays);
        } else {
            json.put("rbcsDays", JSONObject.NULL);
        }
        json.put("unit", "天");
        json.put("formula", "RBCS=totalHemoglobin*1.38/correctedCo");
        json.put("normalRange", "70-140天");
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
            float originalCo,
            float co2,
            float correctionFactor,
            float correctedCo,
            float totalHemoglobin,
            float lifespanDays,
            boolean validLifespan,
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
            throw new IOException("无法创建红细胞寿命诊断报告PDF");
        }

        boolean success = false;
        PdfDocument document = new PdfDocument();
        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("无法写入红细胞寿命诊断报告PDF");
            }
            drawReportPage(
                    document,
                    barcode,
                    patient,
                    report,
                    applicationDepartment,
                    originalCo,
                    co2,
                    correctionFactor,
                    correctedCo,
                    totalHemoglobin,
                    lifespanDays,
                    validLifespan,
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
                throw new IOException("无法完成红细胞寿命诊断报告PDF写入");
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
            float originalCo,
            float co2,
            float correctionFactor,
            float correctedCo,
            float totalHemoglobin,
            float lifespanDays,
            boolean validLifespan,
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
        y = drawDataGrid(
                canvas,
                paint,
                y + 8f,
                originalCo,
                co2,
                correctionFactor,
                correctedCo,
                totalHemoglobin);
        RectF chartBounds = new RectF(PAGE_MARGIN, y + 12f, PAGE_WIDTH - PAGE_MARGIN, y + 182f);
        drawBarChart(canvas, paint, chartBounds, correctedCo, totalHemoglobin);

        y = chartBounds.bottom + 14f;
        y = drawSectionTitle(canvas, paint, y, "第三部分  诊断结果");
        drawDiagnosis(
                canvas,
                paint,
                y + 10f,
                lifespanDays,
                validLifespan,
                diagnosis);

        drawFooter(canvas, paint);
        document.finishPage(page);
    }

    private static void drawReportHeader(Canvas canvas, Paint paint, TestReport report) {
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(22f);
        paint.setColor(Color.rgb(120, 31, 77));
        drawCenteredText(canvas, paint, "红细胞寿命检测诊断报告", PAGE_WIDTH / 2f, 50f);

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
        paint.setColor(Color.rgb(250, 232, 241));
        canvas.drawRect(rect, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(13f);
        paint.setColor(Color.rgb(120, 31, 77));
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
            float originalCo,
            float co2,
            float correctionFactor,
            float correctedCo,
            float totalHemoglobin) {
        String[][] rows = {
                {"CO原始浓度", format(originalCo, 2) + " ppm",
                        "CO2浓度", format(co2, 0) + " ppm"},
                {"修正系数", format(correctionFactor, 2),
                        "CO修正后浓度", format(correctedCo, 2) + " ppm"},
                {"全身血红蛋白总量", format(totalHemoglobin, 2),
                        "修正公式", "CO ÷ (CO2/500)"}
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
                paint.setColor(Color.rgb(249, 247, 249));
                canvas.drawRect(left, top, left + labelWidth, top + rowHeight, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(205, 190, 199));
                canvas.drawRect(left, top, left + half, top + rowHeight, paint);
                canvas.drawLine(left + labelWidth, top, left + labelWidth, top + rowHeight, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setColor(Color.rgb(83, 60, 71));
                drawFittedText(
                        canvas, paint, rows[row][side * 2], left + 6f, top + 16f, labelWidth - 12f);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setColor(Color.rgb(38, 35, 37));
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
            float correctedCo,
            float totalHemoglobin) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(bounds, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(Color.rgb(205, 190, 199));
        canvas.drawRect(bounds, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(10.5f);
        paint.setColor(Color.rgb(76, 54, 65));
        canvas.drawText("修正后CO浓度与全身血红蛋白总量", bounds.left + 12f, bounds.top + 18f, paint);

        RectF plot = new RectF(bounds.left + 40f, bounds.top + 32f, bounds.right - 18f, bounds.bottom - 28f);
        float maxValue = Math.max(10f, Math.max(correctedCo, totalHemoglobin));
        float axisMax = (float) Math.ceil(maxValue * 1.15f / 10f) * 10f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(7.5f);
        for (int line = 0; line <= 4; line++) {
            float value = axisMax * line / 4f;
            float lineY = plot.bottom - plot.height() * line / 4f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f);
            paint.setColor(Color.rgb(229, 221, 225));
            canvas.drawLine(plot.left, lineY, plot.right, lineY, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(Color.rgb(105, 91, 98));
            canvas.drawText(format(value, 0), plot.left - 5f, lineY + 3f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);

        drawBar(
                canvas,
                paint,
                plot,
                plot.left + plot.width() * 0.30f,
                correctedCo,
                axisMax,
                Color.rgb(33, 150, 243),
                "修正后CO浓度",
                format(correctedCo, 2));
        drawBar(
                canvas,
                paint,
                plot,
                plot.left + plot.width() * 0.70f,
                totalHemoglobin,
                axisMax,
                Color.rgb(233, 30, 99),
                "全身血红蛋白总量",
                format(totalHemoglobin, 2));
    }

    private static void drawBar(
            Canvas canvas,
            Paint paint,
            RectF plot,
            float centerX,
            float value,
            float axisMax,
            int color,
            String label,
            String valueLabel) {
        float barWidth = 64f;
        float height = Math.max(0f, value) / axisMax * plot.height();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(centerX - barWidth / 2f, plot.bottom - height,
                centerX + barWidth / 2f, plot.bottom, paint);

        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(8f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.rgb(55, 47, 51));
        canvas.drawText(valueLabel, centerX, Math.max(plot.top + 10f, plot.bottom - height - 5f), paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(8f);
        canvas.drawText(label, centerX, plot.bottom + 14f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawDiagnosis(
            Canvas canvas,
            Paint paint,
            float y,
            float lifespanDays,
            boolean valid,
            String diagnosis) {
        int background;
        int foreground;
        if (!valid) {
            background = Color.rgb(255, 243, 224);
            foreground = Color.rgb(230, 81, 0);
        } else if (lifespanDays < RedBloodCellLifespanCalculator.MIN_NORMAL_DAYS) {
            background = Color.rgb(255, 235, 238);
            foreground = Color.rgb(198, 40, 40);
        } else if (lifespanDays > RedBloodCellLifespanCalculator.MAX_NORMAL_DAYS) {
            background = Color.rgb(255, 248, 225);
            foreground = Color.rgb(245, 124, 0);
        } else {
            background = Color.rgb(232, 245, 233);
            foreground = Color.rgb(46, 125, 50);
        }

        RectF box = new RectF(PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, y + 52f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        canvas.drawRoundRect(box, 8f, 8f, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(15f);
        paint.setColor(foreground);
        String result = valid
                ? String.format(Locale.CHINA, "RBCS %.2f 天 - %s", lifespanDays, diagnosis)
                : diagnosis;
        drawCenteredFittedText(canvas, paint, result, PAGE_WIDTH / 2f, y + 32f,
                PAGE_WIDTH - PAGE_MARGIN * 2 - 24f);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(9.5f);
        paint.setColor(Color.rgb(73, 64, 68));
        canvas.drawText("计算公式：RBCS = 全身血红蛋白总量 × 1.38 ÷ CO修正后浓度", PAGE_MARGIN, y + 70f, paint);
        canvas.drawText("参考范围：70-140 天（含边界）；本报告结果仅供临床参考。", PAGE_MARGIN, y + 87f, paint);
    }

    private static void drawFooter(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.6f);
        paint.setColor(Color.rgb(214, 204, 209));
        canvas.drawLine(PAGE_MARGIN, PAGE_HEIGHT - 34f, PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 34f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(8f);
        paint.setColor(Color.rgb(105, 96, 100));
        canvas.drawText("WellYearn 红细胞寿命检测报告", PAGE_MARGIN, PAGE_HEIGHT - 20f, paint);
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

    private static void drawCenteredFittedText(
            Canvas canvas,
            Paint paint,
            String text,
            float centerX,
            float baselineY,
            float maxWidth) {
        paint.setTextAlign(Paint.Align.CENTER);
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, centerX, baselineY, paint);
        } else {
            float originalSize = paint.getTextSize();
            float fittedSize = Math.max(10f, originalSize * maxWidth / paint.measureText(text));
            paint.setTextSize(fittedSize);
            canvas.drawText(text, centerX, baselineY, paint);
            paint.setTextSize(originalSize);
        }
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
