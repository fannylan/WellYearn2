package com.wellyearn.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiboDiagnosisRulesTest {

    @Test
    public void channel1UsesInclusiveBaselineLimits() {
        assertFalse(SiboDiagnosisRules.isChannelPositive(0, 19.9f, 4.9f, 0f, 0f));
        assertTrue(SiboDiagnosisRules.isChannelPositive(0, 20f, 0f, 0f, 0f));
        assertTrue(SiboDiagnosisRules.isChannelPositive(0, 0f, 5f, 0f, 0f));
    }

    @Test
    public void channels2And3UseDirectConcentrationLimits() {
        assertFalse(SiboDiagnosisRules.isChannelPositive(1, 7f, 7f, 0f, 0f));
        assertTrue(SiboDiagnosisRules.isChannelPositive(1, 20f, 0f, 0f, 0f));
        assertTrue(SiboDiagnosisRules.isChannelPositive(2, 0f, 10f, 0f, 0f));
        assertTrue(SiboDiagnosisRules.isChannelPositive(2, 8f, 7f, 0f, 0f));
    }

    @Test
    public void channels4To8UseChangesFromChannel1Baseline() {
        float baselineH2 = 5f;
        float baselineCh4 = 2f;

        assertFalse(SiboDiagnosisRules.isChannelPositive(
                3, 19f, 2f, baselineH2, baselineCh4));
        assertTrue(SiboDiagnosisRules.isChannelPositive(
                3, 25f, 2f, baselineH2, baselineCh4));
        assertTrue(SiboDiagnosisRules.isChannelPositive(
                6, 5f, 12f, baselineH2, baselineCh4));
        assertTrue(SiboDiagnosisRules.isChannelPositive(
                7, 13f, 9f, baselineH2, baselineCh4));
    }
}
