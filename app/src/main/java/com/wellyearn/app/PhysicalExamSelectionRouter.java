package com.wellyearn.app;

final class PhysicalExamSelectionRouter {

    static final String GASTROINTESTINAL_COMMAND = "7E 20 00 00 00 20 7E";
    static final String RED_BLOOD_CELL_COMMAND = "7E 30 00 00 00 30 7E";
    static final String RESPIRATORY_COMMAND = "7E 40 00 00 00 40 7E";

    enum Detection {
        GASTROINTESTINAL,
        RED_BLOOD_CELL,
        RESPIRATORY
    }

    private PhysicalExamSelectionRouter() {
    }

    static Detection firstSelected(boolean ch4, boolean h2, boolean co, boolean no) {
        if (ch4 || h2) {
            return Detection.GASTROINTESTINAL;
        }
        if (co) {
            return Detection.RED_BLOOD_CELL;
        }
        if (no) {
            return Detection.RESPIRATORY;
        }
        return null;
    }

    static Detection nextSelected(
            Detection current,
            boolean ch4,
            boolean h2,
            boolean co,
            boolean no) {
        if (current == Detection.GASTROINTESTINAL) {
            if (co) {
                return Detection.RED_BLOOD_CELL;
            }
            return no ? Detection.RESPIRATORY : null;
        }
        if (current == Detection.RED_BLOOD_CELL) {
            return no ? Detection.RESPIRATORY : null;
        }
        return null;
    }

    static String commandFor(Detection detection) {
        if (detection == Detection.GASTROINTESTINAL) {
            return GASTROINTESTINAL_COMMAND;
        }
        if (detection == Detection.RED_BLOOD_CELL) {
            return RED_BLOOD_CELL_COMMAND;
        }
        if (detection == Detection.RESPIRATORY) {
            return RESPIRATORY_COMMAND;
        }
        throw new IllegalArgumentException("检测类型不能为空");
    }
}
