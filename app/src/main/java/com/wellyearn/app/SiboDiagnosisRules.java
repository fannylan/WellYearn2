package com.wellyearn.app;

final class SiboDiagnosisRules {

    private static final int FIRST_CHANNEL_INDEX = 0;
    private static final int THIRD_CHANNEL_INDEX = 2;
    private static final int LAST_CHANNEL_INDEX = 7;

    private SiboDiagnosisRules() {
    }

    static boolean isChannelPositive(
            int channelIndex,
            float h2,
            float ch4,
            float baselineH2,
            float baselineCh4) {
        if (channelIndex < FIRST_CHANNEL_INDEX || channelIndex > LAST_CHANNEL_INDEX) {
            throw new IllegalArgumentException("channelIndex must be between 0 and 7");
        }

        // 通道1是0分钟基线，正常基线必须满足 H2 < 20 且 CH4 < 5。
        if (channelIndex == FIRST_CHANNEL_INDEX) {
            return h2 >= 20f || ch4 >= 5f;
        }

        // 通道2、3使用各自浓度直接判断。
        if (channelIndex <= THIRD_CHANNEL_INDEX) {
            return h2 >= 20f || ch4 >= 10f || h2 + ch4 >= 15f;
        }

        // 通道4至8与通道1的0分钟基线比较。
        return h2 - baselineH2 >= 20f
                || ch4 - baselineCh4 >= 10f
                || (h2 + ch4) - (baselineH2 + baselineCh4) >= 15f;
    }
}
