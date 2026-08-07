package com.wellyearn.app;

final class PhysicalExamSelectionRouter {

    static final String CO_ONLY_START_COMMAND = "7E 30 00 00 00 30 7E";
    static final String DEFAULT_START_COMMAND = "7E 10 00 00 00 10 7E";

    private PhysicalExamSelectionRouter() {
    }

    static boolean isCoOnly(boolean ch4, boolean h2, boolean co, boolean no) {
        return co && !ch4 && !h2 && !no;
    }

    static String startCommand(boolean coOnly) {
        return coOnly ? CO_ONLY_START_COMMAND : DEFAULT_START_COMMAND;
    }
}
