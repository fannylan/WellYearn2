package com.wellyearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class RedBloodCellLifespanCalculatorTest {

    @Test
    public void correctionFactorIsCo2DividedBy500() {
        assertEquals(0.5f, RedBloodCellLifespanCalculator.correctionFactor(250f), 0.0001f);
        assertEquals(1f, RedBloodCellLifespanCalculator.correctionFactor(500f), 0.0001f);
    }

    @Test
    public void correctedCoIsOriginalCoDividedByCorrectionFactor() {
        assertEquals(20f, RedBloodCellLifespanCalculator.correctedCo(10f, 250f), 0.0001f);
        assertEquals(10f, RedBloodCellLifespanCalculator.correctedCo(10f, 500f), 0.0001f);
        assertEquals(0f, RedBloodCellLifespanCalculator.correctedCo(10f, 0f), 0.0001f);
        assertFalse(RedBloodCellLifespanCalculator.hasValidCorrectionFactor(0f));
    }

    @Test
    public void lifespanUsesTotalHemoglobinAndCorrectedCo() {
        assertEquals(69f, RedBloodCellLifespanCalculator.lifespanDays(100f, 2f), 0.0001f);
        assertEquals(138f, RedBloodCellLifespanCalculator.lifespanDays(100f, 1f), 0.0001f);
    }

    @Test
    public void diagnosisUsesInclusiveNormalRange() {
        assertEquals("红细胞寿命缩短，提示溶血风险", RedBloodCellLifespanCalculator.diagnosis(69.99f));
        assertEquals("红细胞寿命正常", RedBloodCellLifespanCalculator.diagnosis(70f));
        assertEquals("红细胞寿命正常", RedBloodCellLifespanCalculator.diagnosis(140f));
        assertEquals("红细胞寿命偏长，提示造血偏缓", RedBloodCellLifespanCalculator.diagnosis(140.01f));
    }
}
