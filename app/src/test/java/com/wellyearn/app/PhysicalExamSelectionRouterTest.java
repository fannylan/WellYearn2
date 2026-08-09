package com.wellyearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PhysicalExamSelectionRouterTest {

    @Test
    public void firstSelectedUsesGastrointestinalCoRespiratoryPriority() {
        assertEquals(
                PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL,
                PhysicalExamSelectionRouter.firstSelected(true, false, true, true));
        assertEquals(
                PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL,
                PhysicalExamSelectionRouter.firstSelected(false, true, true, true));
        assertEquals(
                PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL,
                PhysicalExamSelectionRouter.firstSelected(false, false, true, true));
        assertEquals(
                PhysicalExamSelectionRouter.Detection.RESPIRATORY,
                PhysicalExamSelectionRouter.firstSelected(false, false, false, true));
        assertNull(PhysicalExamSelectionRouter.firstSelected(false, false, false, false));
    }

    @Test
    public void commandMatchesSelectedDetection() {
        assertEquals(
                "7E 20 00 00 00 20 7E",
                PhysicalExamSelectionRouter.commandFor(
                        PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL));
        assertEquals(
                "7E 30 00 00 00 30 7E",
                PhysicalExamSelectionRouter.commandFor(
                        PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL));
        assertEquals(
                "7E 10 00 00 00 10 7E",
                PhysicalExamSelectionRouter.commandFor(
                        PhysicalExamSelectionRouter.Detection.RESPIRATORY));
    }

    @Test
    public void nextSelectedSkipsUnselectedTestsAndKeepsOrder() {
        assertEquals(
                PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL,
                PhysicalExamSelectionRouter.nextSelected(
                        PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL,
                        true, false, true, true));
        assertEquals(
                PhysicalExamSelectionRouter.Detection.RESPIRATORY,
                PhysicalExamSelectionRouter.nextSelected(
                        PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL,
                        false, true, false, true));
        assertEquals(
                PhysicalExamSelectionRouter.Detection.RESPIRATORY,
                PhysicalExamSelectionRouter.nextSelected(
                        PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL,
                        false, false, true, true));
        assertNull(PhysicalExamSelectionRouter.nextSelected(
                PhysicalExamSelectionRouter.Detection.RESPIRATORY,
                true, true, true, true));
    }
}
