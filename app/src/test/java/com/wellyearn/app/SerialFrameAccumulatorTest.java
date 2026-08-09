package com.wellyearn.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class SerialFrameAccumulatorTest {

    @Test
    public void reconstructsFrameSplitAcrossCallbacks() {
        SerialFrameAccumulator accumulator = new SerialFrameAccumulator();

        assertEquals(0, accumulator.append(new byte[]{0x7E, 0x40, 0x00}).size());
        List<byte[]> frames = accumulator.append(
                new byte[]{0x00, 0x05, 0x00, 0x1E, 0x00, (byte) 0xF4, 0x01, 0x53, 0x7E});

        assertEquals(1, frames.size());
        assertArrayEquals(
                new byte[]{0x7E, 0x40, 0x00, 0x00, 0x05, 0x00, 0x1E, 0x00,
                        (byte) 0xF4, 0x01, 0x53, 0x7E},
                frames.get(0));
    }

    @Test
    public void extractsMultipleFramesAndIgnoresLeadingNoise() {
        SerialFrameAccumulator accumulator = new SerialFrameAccumulator();

        List<byte[]> frames = accumulator.append(new byte[]{
                0x01, 0x02,
                0x7E, 0x40, 0x00, 0x7E,
                0x40, 0x01, 0x7E
        });

        assertEquals(2, frames.size());
        assertArrayEquals(new byte[]{0x7E, 0x40, 0x00, 0x7E}, frames.get(0));
        assertArrayEquals(new byte[]{0x7E, 0x40, 0x01, 0x7E}, frames.get(1));
    }
}
