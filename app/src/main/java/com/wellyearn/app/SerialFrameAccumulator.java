package com.wellyearn.app;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects 0x7E-delimited protocol frames across arbitrary serial callback chunks. */
final class SerialFrameAccumulator {

    private static final int FRAME_BOUNDARY = 0x7E;
    private static final int MAX_FRAME_SIZE = 4096;

    private final ByteArrayOutputStream currentFrame = new ByteArrayOutputStream();
    private boolean collecting;

    synchronized List<byte[]> append(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return Collections.emptyList();
        }

        List<byte[]> completedFrames = new ArrayList<>();
        for (byte value : chunk) {
            if ((value & 0xFF) == FRAME_BOUNDARY) {
                if (!collecting) {
                    startFrame(value);
                    continue;
                }

                if (currentFrame.size() > 1) {
                    currentFrame.write(value);
                    completedFrames.add(currentFrame.toByteArray());
                }
                startFrame(value);
                continue;
            }

            if (collecting) {
                currentFrame.write(value);
                if (currentFrame.size() > MAX_FRAME_SIZE) {
                    currentFrame.reset();
                    collecting = false;
                }
            }
        }
        return completedFrames;
    }

    private void startFrame(byte boundary) {
        currentFrame.reset();
        currentFrame.write(boundary);
        collecting = true;
    }
}
