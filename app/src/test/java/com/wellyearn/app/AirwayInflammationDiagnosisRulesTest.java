package com.wellyearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AirwayInflammationDiagnosisRulesTest {

    @Test
    public void ageTwelveAndAboveIsAdult() {
        assertFalse(AirwayInflammationDiagnosisRules.isAdult(11));
        assertTrue(AirwayInflammationDiagnosisRules.isAdult(12));
        assertTrue(AirwayInflammationDiagnosisRules.isAdult(40));
    }

    @Test
    public void correctionUsesCo2DividedBy500() {
        assertEquals(0.5f, AirwayInflammationDiagnosisRules.correctionFactor(250f), 0.0001f);
        assertEquals(20f, AirwayInflammationDiagnosisRules.correctedNo(10f, 250f), 0.0001f);
        assertEquals(0f, AirwayInflammationDiagnosisRules.correctedNo(10f, 0f), 0.0001f);
    }

    @Test
    public void adultThresholdsAreInclusiveInMediumRange() {
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.LOW,
                AirwayInflammationDiagnosisRules.riskLevel(12, 24.99f));
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.MEDIUM,
                AirwayInflammationDiagnosisRules.riskLevel(12, 25f));
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.MEDIUM,
                AirwayInflammationDiagnosisRules.riskLevel(30, 50f));
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.HIGH,
                AirwayInflammationDiagnosisRules.riskLevel(30, 50.01f));
    }

    @Test
    public void childThresholdsAreInclusiveInMediumRange() {
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.LOW,
                AirwayInflammationDiagnosisRules.riskLevel(11, 19.99f));
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.MEDIUM,
                AirwayInflammationDiagnosisRules.riskLevel(11, 20f));
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.MEDIUM,
                AirwayInflammationDiagnosisRules.riskLevel(8, 35f));
        assertEquals(AirwayInflammationDiagnosisRules.RiskLevel.HIGH,
                AirwayInflammationDiagnosisRules.riskLevel(8, 35.01f));
    }
}
