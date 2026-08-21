package com.wellyearn.app.report;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReportHospitalHeaderTest {

    @Test
    public void normalizedName_keepsConfiguredHospitalName() {
        assertEquals("惠雨恩医院",
                ReportHospitalHeader.normalizedName("  惠雨恩医院  "));
    }

    @Test
    public void normalizedName_hidesUnsetAndBlankValues() {
        assertEquals("", ReportHospitalHeader.normalizedName(null));
        assertEquals("", ReportHospitalHeader.normalizedName("  "));
        assertEquals("", ReportHospitalHeader.normalizedName("未设置"));
    }
}
