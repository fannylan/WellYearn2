package com.wellyearn.app;

/** Parses respiratory measurement frames returned by the lower-level controller. */
final class RespiratoryProtocolParser {

    private static final int RESPIRATORY_MESSAGE_ID = 0x4000;
    private static final int MINIMUM_BODY_LENGTH = 3;

    private RespiratoryProtocolParser() {
    }

    static Measurement parseFrame(byte[] frame) {
        if (frame == null || frame.length < 8) {
            return null;
        }

        int messageId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        int bodyLength = (((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF)) & 0x03FF;
        if (messageId != RESPIRATORY_MESSAGE_ID || bodyLength < MINIMUM_BODY_LENGTH) {
            return null;
        }

        int checksumIndex = 4 + bodyLength;
        if (frame.length <= checksumIndex || !hasValidChecksum(frame, checksumIndex)) {
            return null;
        }

        int systemStatus = frame[4] & 0xFF;
        int noConcentration = (frame[5] & 0xFF) | ((frame[6] & 0xFF) << 8);
        return new Measurement(systemStatus, noConcentration);
    }

    private static boolean hasValidChecksum(byte[] frame, int checksumIndex) {
        int checksum = 0;
        for (int index = 0; index < checksumIndex; index++) {
            checksum ^= frame[index] & 0xFF;
        }
        return checksum == (frame[checksumIndex] & 0xFF);
    }

    static final class Measurement {
        final int systemStatus;
        final float noConcentration;

        Measurement(int systemStatus, float noConcentration) {
            this.systemStatus = systemStatus;
            this.noConcentration = noConcentration;
        }
    }
}
