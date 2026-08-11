package com.wellyearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class RespiratoryProtocolParserTest {

    @Test
    public void parsesControllerSampleFrame() {
        byte[] frame = new byte[]{
                0x40, 0x00, 0x00, 0x03, 0x04, 0x23, 0x00, 0x64
        };

        RespiratoryProtocolParser.Measurement measurement =
                RespiratoryProtocolParser.parseFrame(frame);

        assertNotNull(measurement);
        assertEquals(0x04, measurement.systemStatus);
        assertEquals(35f, measurement.noConcentration, 0.001f);
    }

    @Test
    public void rejectsZeroLengthAcknowledgementAndBadChecksum() {
        assertNull(RespiratoryProtocolParser.parseFrame(
                new byte[]{0x40, 0x00, 0x00, 0x00, 0x40}));
        assertNull(RespiratoryProtocolParser.parseFrame(
                new byte[]{0x40, 0x00, 0x00, 0x03, 0x04, 0x23, 0x00, 0x00}));
    }
}
