package com.wellyearn.app;

import static org.junit.Assert.assertEquals;

import com.wellyearn.app.report.GastrointestinalReportService;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class GastrointestinalReportServiceTest {

    @Test
    public void fileNameUsesBarcodePatientDateAndReportType() {
        long date = new GregorianCalendar(2026, Calendar.AUGUST, 7).getTimeInMillis();

        assertEquals(
                "BC001张三20260807胃肠道检测.pdf",
                GastrointestinalReportService.buildFileName("BC001", "张三", date));
    }

    @Test
    public void fileNameReplacesCharactersThatAreInvalidOnCommonFileSystems() {
        long date = new GregorianCalendar(2026, Calendar.AUGUST, 7).getTimeInMillis();

        assertEquals(
                "BC_001李_四20260807胃肠道检测.pdf",
                GastrointestinalReportService.buildFileName("BC/001", "李:四", date));
    }
}
