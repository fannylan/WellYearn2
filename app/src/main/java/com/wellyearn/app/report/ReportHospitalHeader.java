package com.wellyearn.app.report;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Draws the configured hospital name in the upper-left corner of report pages. */
final class ReportHospitalHeader {

    private static final float BASELINE_Y = 23f;
    private static final float TEXT_SIZE = 10.5f;
    private static final String UNSET_NAME = "未设置";

    private ReportHospitalHeader() {
    }

    static void draw(Canvas canvas, String hospitalName, float left, float right) {
        String name = normalizedName(hospitalName);
        if (name.isEmpty()) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(TEXT_SIZE);
        paint.setColor(Color.rgb(55, 65, 81));
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fitText(paint, name, Math.max(1f, right - left)),
                left, BASELINE_Y, paint);
    }

    static String normalizedName(String hospitalName) {
        if (hospitalName == null) return "";
        String name = hospitalName.trim();
        return name.isEmpty() || UNSET_NAME.equals(name) ? "" : name;
    }

    private static String fitText(Paint paint, String text, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) return text;
        String ellipsis = "...";
        StringBuilder fitted = new StringBuilder(text);
        while (fitted.length() > 0
                && paint.measureText(fitted.toString() + ellipsis) > maxWidth) {
            fitted.deleteCharAt(fitted.length() - 1);
        }
        return fitted + ellipsis;
    }
}
