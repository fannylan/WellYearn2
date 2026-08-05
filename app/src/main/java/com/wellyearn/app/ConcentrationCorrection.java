package com.wellyearn.app;

final class ConcentrationCorrection {

    private static final float CO2_REFERENCE_CONCENTRATION = 500f;

    private ConcentrationCorrection() {
    }

    static float correctionFactor(float co2Concentration) {
        return co2Concentration / CO2_REFERENCE_CONCENTRATION;
    }

    static boolean hasValidCorrectionFactor(float co2Concentration) {
        return correctionFactor(co2Concentration) > 0f;
    }

    static float correctedValue(float originalValue, float co2Concentration) {
        float correctionFactor = correctionFactor(co2Concentration);
        return correctionFactor > 0f ? originalValue / correctionFactor : 0f;
    }
}
