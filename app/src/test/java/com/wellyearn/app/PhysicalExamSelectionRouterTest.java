package com.wellyearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhysicalExamSelectionRouterTest {

    @Test
    public void onlyCoSelectsTest2FlowAndCommand() {
        boolean coOnly = PhysicalExamSelectionRouter.isCoOnly(false, false, true, false);

        assertTrue(coOnly);
        assertEquals(
                "7E 30 00 00 00 30 7E",
                PhysicalExamSelectionRouter.startCommand(coOnly));
    }

    @Test
    public void anyAdditionalGasKeepsDefaultPhysicalExamFlow() {
        assertFalse(PhysicalExamSelectionRouter.isCoOnly(true, false, true, false));
        assertFalse(PhysicalExamSelectionRouter.isCoOnly(false, true, true, false));
        assertFalse(PhysicalExamSelectionRouter.isCoOnly(false, false, true, true));
        assertEquals(
                "7E 10 00 00 00 10 7E",
                PhysicalExamSelectionRouter.startCommand(false));
    }

    @Test
    public void missingCoDoesNotSelectTest2Flow() {
        assertFalse(PhysicalExamSelectionRouter.isCoOnly(false, false, false, false));
        assertFalse(PhysicalExamSelectionRouter.isCoOnly(true, true, false, true));
    }
}
