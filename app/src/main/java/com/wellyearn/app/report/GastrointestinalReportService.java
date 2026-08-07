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

/**
 * Persists the three gastrointestinal report sections and creates the matching PDF.
 */
public final class GastrointestinalReportService {

    public static final String REPORT_FOLDER_NAME = "胃肠道检测报告";
    private static final String REPORT_TYPE_NAME = "胃肠道检测";
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float PAGE_MARGIN = 42f;

    private GastrointestinalReportService() {}

    public static SaveResult save(
            Context context,
            AppDatabase database,
            long reportId,
            String barcode,
            List<ChannelMeasurement> measurements,
            boolean positive,
            String interpretation) throws IOException, JSONException {
        TestReport report = database.testReportDao().getReportById(reportId);
        if (report == null) {
            throw new IOException("未找到检测报告记录: " + reportId);
        }
        Patient patient = database.patientDao().getPatientById(report.getPatientId());
        if (patient == null) {
            throw new IOException("未找到患者记录: " + report.getPatientId());
        }

        String applicationDepartment = safe(report.getRemarks());
        String patientInfo = buildPatientInfoJson(
                barcode, patient, report, applicationDepartment).toString();
        String detectionDataChart = buildDetectionDataChartJson(measurements).toString();
        String diagnosisResult = buildDiagnosisResultJson(
                positive, interpretation, report.getTestDate()).toString();
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
                    measurements,
                    positive,
                    interpretation);

            report.setPatientInfo(patientInfo);
            report.setDetectionDataChart(detectionDataChart);
            report.setDiagnosisResult(diagnosisResult);
            report.setPdfFileName(fileName);
            report.setPdfUri(pdfUri.toString());

            // Keep the original fields populated for existing report-query code.
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
            List<ChannelMeasurement> measurements) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "检测数据图表");
        json.put("chartType", "groupedBar");
        json.put("title", "8通道修正后气体浓度");
        json.put("unit", "ppm");

        JSONArray xAxis = new JSONArray();
        JSONArray h2Series = new JSONArray();
        JSONArray ch4Series = new JSONArray();
        JSONArray channelRows = new JSONArray();
        for (ChannelMeasurement measurement : measurements) {
            xAxis.put("通道" + measurement.channel);
            h2Series.put(measurement.correctedH2);
            ch4Series.put(measurement.correctedCh4);

            JSONObject row = new JSONObject();
            row.put("channel", measurement.channel);
            row.put("h2", measurement.h2);
            row.put("ch4", measurement.ch4);
            row.put("h2s", measurement.h2s);
            row.put("co2", measurement.co2);
            row.put("correctionFactor", measurement.correctionFactor);
            row.put("correctedH2", measurement.correctedH2);
            row.put("correctedCh4", measurement.correctedCh4);
            row.put("validCorrectionFactor", measurement.validCorrectionFactor);
            channelRows.put(row);
        }
        json.put("xAxis", xAxis);

        JSONArray series = new JSONArray();
        JSONObject h2 = new JSONObject();
        h2.put("name", "修正后H2");
        h2.put("values", h2Series);
        series.put(h2);
        JSONObject ch4 = new JSONObject();
        ch4.put("name", "修正后CH4");
        ch4.put("values", ch4Series);
        series.put(ch4);
        json.put("series", series);
        json.put("channels", channelRows);
        return json;
    }

    private static JSONObject buildDiagnosisResultJson(
            boolean positive,
            String interpretation,
            long testDate) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("section", "诊断结果");
        json.put("positive", positive);
        json.put("conclusion", positive
                ? "小肠细菌过度生长（SIBO）阳性"
                : "小肠细菌过度生长（SIBO）阴性");
        json.put("interpretation", safe(interpretation));
        json.put("generatedAt", System.currentTimeMillis());
        json.put("testDate", testDate);
        return json;
    }

    private static Uri createPdf(
            Context context,
            String fileName,
            String barcode,
            Patient patient,
            TestReport report,
            String applicationDepartment,
            List<ChannelMeasurement> measurements,
            boolean positive,
            String interpretation) throws IOException {
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
            throw new IOException("无法创建诊断报告PDF");
        }

        boolean success = false;
        PdfDocument document = new PdfDocument();
        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("无法写入诊断报告PDF");
            }
            drawPatientAndDataPage(
                    document,
                    barcode,
                    patient,
                    report,
                    applicationDepartment,
                    measurements);
            drawDiagnosisPage(document, positive, interpretation);
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
                throw new IOException("无法完成诊断报告PDF写入");
            }
        } catch (IOException | RuntimeException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
        return uri;
    }

    private static void drawPatientAndDataPage(
            PdfDocument document,
            String barcode,
            Patient patient,
            TestReport report,
            String applicationDepartment,
            List<ChannelMeasurement> measurements) {
        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create());
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();

        drawReportHeader(canvas, paint, report);
        float y = 100f;
        y = drawSectionTitle(canvas, paint, y, "第一部分  患者信息");
        y = drawPatientGrid(
                canvas,
                paint,
                y,
                barcode,
                patient,
                report,
                applicationDepartment);

        y += 14f;
        y = drawSectionTitle(canvas, paint, y, "第二部分  检测数据图表");
        RectF chartBounds = new RectF(PAGE_MARGIN, y + 8f, PAGE_WIDTH - PAGE_MARGIN, y + 226f);
        drawGroupedBarChart(canvas, paint, chartBounds, measurements);
        y = chartBounds.bottom + 14f;
        drawMeasurementTable(canvas, paint, y, measurements);
        drawFooter(canvas, paint, 1);
        document.finishPage(page);
    }

    private static void drawDiagnosisPage(
            PdfDocument document,
            boolean positive,
            String interpretation) {
        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create());
        Canvas canvas = page.getCanvas();
        Paint paint = basePaint();

        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(22f);
        paint.setColor(Color.rgb(24, 75, 122));
        drawCenteredText(canvas, paint, "胃肠道检测诊断报告", PAGE_WIDTH / 2f, 57f);
        float y = drawSectionTitle(canvas, paint, 92f, "第三部分  诊断结果");

        RectF conclusionBox = new RectF(PAGE_MARGIN, y + 12f, PAGE_WIDTH - PAGE_MARGIN, y + 70f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(positive ? Color.rgb(255, 235, 238) : Color.rgb(232, 245, 233));
        canvas.drawRoundRect(conclusionBox, 8f, 8f, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(18f);
        paint.setColor(positive ? Color.rgb(198, 40, 40) : Color.rgb(46, 125, 50));
        drawCenteredText(
                canvas,
                paint,
                positive
                        ? "小肠细菌过度生长（SIBO）阳性"
                        : "小肠细菌过度生长（SIBO）阴性",
                PAGE_WIDTH / 2f,
                conclusionBox.centerY() + 6f);

        y = conclusionBox.bottom + 32f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(13f);
        paint.setColor(Color.rgb(32, 45, 58));
        canvas.drawText("结果解读", PAGE_MARGIN, y, paint);
        y += 24f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(11f);
        paint.setColor(Color.rgb(49, 61, 73));
        y = drawWrappedText(
                canvas,
                paint,
                safe(interpretation),
                PAGE_MARGIN,
                y,
                PAGE_WIDTH - PAGE_MARGIN * 2,
                18f);

        y += 26f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(12f);
        paint.setColor(Color.rgb(32, 45, 58));
        canvas.drawText("说明", PAGE_MARGIN, y, paint);
        y += 22f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(10.5f);
        paint.setColor(Color.rgb(82, 94, 106));
        drawWrappedText(
                canvas,
                paint,
                "本报告依据本次8通道呼气检测数据及修正后氢气、甲烷浓度生成，结果仅供临床参考，请结合症状、病史及其他检查综合判断。",
                PAGE_MARGIN,
                y,
                PAGE_WIDTH - PAGE_MARGIN * 2,
                17f);

        drawFooter(canvas, paint, 2);
        document.finishPage(page);
    }

    private static void drawReportHeader(
            Canvas canvas,
            Paint paint,
            TestReport report) {
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(22f);
        paint.setColor(Color.rgb(24, 75, 122));
        drawCenteredText(canvas, paint, "胃肠道检测诊断报告", PAGE_WIDTH / 2f, 50f);

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
        paint.setColor(Color.rgb(232, 242, 252));
        canvas.drawRect(rect, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(13f);
        paint.setColor(Color.rgb(24, 75, 122));
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
        float rowHeight = 25f;
        float width = PAGE_WIDTH - PAGE_MARGIN * 2;
        float half = width / 2f;
        float labelWidth = 62f;
        paint.setStrokeWidth(0.7f);
        paint.setTextSize(9.5f);
        for (int row = 0; row < rows.length; row++) {
            float top = y + row * rowHeight;
            for (int side = 0; side < 2; side++) {
                float left = PAGE_MARGIN + side * half;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(247, 249, 251));
                canvas.drawRect(left, top, left + labelWidth, top + rowHeight, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(190, 201, 211));
                canvas.drawRect(left, top, left + half, top + rowHeight, paint);
                canvas.drawLine(left + labelWidth, top, left + labelWidth, top + rowHeight, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setColor(Color.rgb(66, 78, 90));
                canvas.drawText(rows[row][side * 2], left + 7f, top + 17f, paint);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setColor(Color.rgb(34, 43, 52));
                drawFittedText(
                        canvas,
                        paint,
                        rows[row][side * 2 + 1],
                        left + labelWidth + 7f,
                        top + 17f,
                        half - labelWidth - 14f);
            }
        }
        return y + rows.length * rowHeight;
    }

    private static void drawGroupedBarChart(
            Canvas canvas,
            Paint paint,
            RectF bounds,
            List<ChannelMeasurement> measurements) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(bounds, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(Color.rgb(190, 201, 211));
        canvas.drawRect(bounds, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(10.5f);
        paint.setColor(Color.rgb(49, 61, 73));
        canvas.drawText("8通道修正后气体浓度（ppm）", bounds.left + 12f, bounds.top + 18f, paint);

        drawLegendItem(canvas, paint, bounds.right - 160f, bounds.top + 14f,
                Color.rgb(33, 150, 243), "修正后H2");
        drawLegendItem(canvas, paint, bounds.right - 82f, bounds.top + 14f,
                Color.rgb(255, 152, 0), "修正后CH4");

        RectF plot = new RectF(bounds.left + 38f, bounds.top + 35f, bounds.right - 12f, bounds.bottom - 27f);
        float maxValue = 10f;
        for (ChannelMeasurement measurement : measurements) {
            if (measurement.validCorrectionFactor) {
                maxValue = Math.max(maxValue, Math.max(measurement.correctedH2, measurement.correctedCh4));
            }
        }
        float axisMax = (float) Math.ceil(maxValue / 10f) * 10f;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(7.5f);
        for (int line = 0; line <= 5; line++) {
            float value = axisMax * line / 5f;
            float lineY = plot.bottom - plot.height() * line / 5f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f);
            paint.setColor(Color.rgb(222, 228, 234));
            canvas.drawLine(plot.left, lineY, plot.right, lineY, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(Color.rgb(92, 104, 116));
            canvas.drawText(String.format(Locale.CHINA, "%.0f", value), plot.left - 5f, lineY + 3f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);

        float groupWidth = plot.width() / 8f;
        float barWidth = groupWidth * 0.26f;
        for (int index = 0; index < 8; index++) {
            ChannelMeasurement measurement = findChannel(measurements, index + 1);
            float h2 = measurement != null && measurement.validCorrectionFactor
                    ? measurement.correctedH2 : 0f;
            float ch4 = measurement != null && measurement.validCorrectionFactor
                    ? measurement.correctedCh4 : 0f;
            float centerX = plot.left + groupWidth * (index + 0.5f);
            drawBar(canvas, paint, centerX - barWidth, plot.bottom, barWidth, h2, axisMax,
                    plot.height(), Color.rgb(33, 150, 243));
            drawBar(canvas, paint, centerX, plot.bottom, barWidth, ch4, axisMax,
                    plot.height(), Color.rgb(255, 152, 0));
            paint.setColor(Color.rgb(66, 78, 90));
            paint.setTextSize(7.5f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("通道" + (index + 1), centerX, plot.bottom + 13f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawLegendItem(
            Canvas canvas,
            Paint paint,
            float x,
            float y,
            int color,
            String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(x, y - 7f, x + 8f, y + 1f, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(7.5f);
        paint.setColor(Color.rgb(66, 78, 90));
        canvas.drawText(label, x + 11f, y, paint);
    }

    private static void drawBar(
            Canvas canvas,
            Paint paint,
            float left,
            float bottom,
            float width,
            float value,
            float axisMax,
            float plotHeight,
            int color) {
        float height = Math.max(0f, value) / axisMax * plotHeight;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(left, bottom - height, left + width, bottom, paint);
    }

    private static void drawMeasurementTable(
            Canvas canvas,
            Paint paint,
            float y,
            List<ChannelMeasurement> measurements) {
        String[] headers = {"通道", "H2", "CH4", "H2S", "CO2", "修正系数", "修正H2", "修正CH4"};
        float[] widths = {34f, 48f, 48f, 48f, 55f, 67f, 73f, 76f};
        float totalWidth = 0f;
        for (float width : widths) totalWidth += width;
        float scale = (PAGE_WIDTH - PAGE_MARGIN * 2) / totalWidth;
        float rowHeight = 18f;

        float left = PAGE_MARGIN;
        paint.setTextSize(7.3f);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        for (int column = 0; column < headers.length; column++) {
            float width = widths[column] * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(226, 235, 244));
            canvas.drawRect(left, y, left + width, y + rowHeight, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f);
            paint.setColor(Color.rgb(176, 189, 201));
            canvas.drawRect(left, y, left + width, y + rowHeight, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(46, 60, 73));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(headers[column], left + width / 2f, y + 12f, paint);
            left += width;
        }

        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        for (int row = 0; row < 8; row++) {
            ChannelMeasurement value = findChannel(measurements, row + 1);
            String[] cells = value == null
                    ? new String[]{String.valueOf(row + 1), "--", "--", "--", "--", "--", "--", "--"}
                    : new String[]{
                            String.valueOf(value.channel),
                            format(value.h2, 1),
                            format(value.ch4, 2),
                            format(value.h2s, 2),
                            format(value.co2, 0),
                            value.validCorrectionFactor ? format(value.correctionFactor, 2) : "无效",
                            value.validCorrectionFactor ? format(value.correctedH2, 1) : "--",
                            value.validCorrectionFactor ? format(value.correctedCh4, 2) : "--"
                    };
            left = PAGE_MARGIN;
            float top = y + rowHeight * (row + 1);
            for (int column = 0; column < cells.length; column++) {
                float width = widths[column] * scale;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(row % 2 == 0 ? Color.WHITE : Color.rgb(248, 250, 252));
                canvas.drawRect(left, top, left + width, top + rowHeight, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(196, 205, 214));
                canvas.drawRect(left, top, left + width, top + rowHeight, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(49, 61, 73));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(cells[column], left + width / 2f, top + 12f, paint);
                left += width;
            }
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
        String[] paragraphs = text.replace("\r", "").split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                y += lineHeight;
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int index = 0; index < paragraph.length(); index++) {
                char character = paragraph.charAt(index);
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
        }
        return y;
    }

    private static void drawFooter(Canvas canvas, Paint paint, int pageNumber) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.6f);
        paint.setColor(Color.rgb(205, 214, 222));
        canvas.drawLine(PAGE_MARGIN, PAGE_HEIGHT - 34f, PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 34f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(8f);
        paint.setColor(Color.rgb(105, 116, 126));
        canvas.drawText("WellYearn 胃肠道检测报告", PAGE_MARGIN, PAGE_HEIGHT - 20f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("第 " + pageNumber + " / 2 页", PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 20f, paint);
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

    private static ChannelMeasurement findChannel(
            List<ChannelMeasurement> measurements,
            int channel) {
        for (ChannelMeasurement measurement : measurements) {
            if (measurement.channel == channel) return measurement;
        }
        return null;
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

    public static final class ChannelMeasurement {
        public final int channel;
        public final float h2;
        public final float ch4;
        public final float h2s;
        public final float co2;
        public final float correctionFactor;
        public final float correctedH2;
        public final float correctedCh4;
        public final boolean validCorrectionFactor;

        public ChannelMeasurement(
                int channel,
                float h2,
                float ch4,
                float h2s,
                float co2,
                float correctionFactor,
                float correctedH2,
                float correctedCh4,
                boolean validCorrectionFactor) {
            this.channel = channel;
            this.h2 = h2;
            this.ch4 = ch4;
            this.h2s = h2s;
            this.co2 = co2;
            this.correctionFactor = correctionFactor;
            this.correctedH2 = correctedH2;
            this.correctedCh4 = correctedCh4;
            this.validCorrectionFactor = validCorrectionFactor;
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
