package com.wellyearn.app;

import static org.junit.Assert.assertEquals;

import com.wellyearn.app.report.PhysicalExamReportService;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class PhysicalExamReportServiceTest {

    @Test
    public void fileNameUsesBarcodePatientDateAndPhysicalExamReportType() {
        long date = new GregorianCalendar(2026, Calendar.AUGUST, 9).getTimeInMillis();

        assertEquals(
                "BC001张三20260809体检诊断报告.pdf",
                PhysicalExamReportService.buildFileName("BC001", "张三", date));
        assertEquals("体检报告", PhysicalExamReportService.REPORT_FOLDER_NAME);
    }

    @Test
    public void fileNameReplacesInvalidCharacters() {
        long date = new GregorianCalendar(2026, Calendar.AUGUST, 9).getTimeInMillis();

        assertEquals(
                "BC_001李_四20260809体检诊断报告.pdf",
                PhysicalExamReportService.buildFileName("BC/001", "李:四", date));
    }
}
