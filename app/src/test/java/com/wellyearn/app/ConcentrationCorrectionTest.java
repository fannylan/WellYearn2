package com.wellyearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ConcentrationCorrectionTest {

    @Test
    public void factorIsCo2ConcentrationDividedBy500() {
        assertEquals(1f, ConcentrationCorrection.correctionFactor(500f), 0.0001f);
        assertEquals(0.5f, ConcentrationCorrection.correctionFactor(250f), 0.0001f);
    }

    @Test
    public void correctedValueIsOriginalValueDividedByFactor() {
        assertEquals(20f, ConcentrationCorrection.correctedValue(10f, 250f), 0.0001f);
        assertEquals(10f, ConcentrationCorrection.correctedValue(10f, 500f), 0.0001f);
    }

    @Test
    public void zeroCo2DoesNotProduceAnInfiniteValue() {
        assertFalse(ConcentrationCorrection.hasValidCorrectionFactor(0f));
        assertEquals(0f, ConcentrationCorrection.correctedValue(10f, 0f), 0.0001f);
    }
}
