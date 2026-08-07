package com.wellyearn.app;

import static org.junit.Assert.assertEquals;

import com.wellyearn.app.report.RedBloodCellLifespanReportService;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class RedBloodCellLifespanReportServiceTest {

    @Test
    public void fileNameUsesBarcodePatientDateAndReportType() {
        long date = new GregorianCalendar(2026, Calendar.AUGUST, 7).getTimeInMillis();

        assertEquals(
                "BC001张三20260807红细胞寿命检测.pdf",
                RedBloodCellLifespanReportService.buildFileName("BC001", "张三", date));
    }

    @Test
    public void fileNameReplacesInvalidCharacters() {
        long date = new GregorianCalendar(2026, Calendar.AUGUST, 7).getTimeInMillis();

        assertEquals(
                "BC_001李_四20260807红细胞寿命检测.pdf",
                RedBloodCellLifespanReportService.buildFileName("BC/001", "李:四", date));
    }
}
