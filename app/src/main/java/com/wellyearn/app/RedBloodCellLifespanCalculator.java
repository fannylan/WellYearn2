package com.wellyearn.app;

/** Calculation and interpretation rules for red blood cell lifespan testing. */
public final class RedBloodCellLifespanCalculator {

    public static final float MIN_NORMAL_DAYS = 70f;
    public static final float MAX_NORMAL_DAYS = 140f;
    private static final float CO2_REFERENCE_CONCENTRATION = 500f;
    private static final float HEMOGLOBIN_CONVERSION_FACTOR = 1.38f;

    private RedBloodCellLifespanCalculator() {}

    public static float correctionFactor(float co2Concentration) {
        return co2Concentration / CO2_REFERENCE_CONCENTRATION;
    }

    public static boolean hasValidCorrectionFactor(float co2Concentration) {
        return correctionFactor(co2Concentration) > 0f;
    }

    public static float correctedCo(float originalCo, float co2Concentration) {
        float factor = correctionFactor(co2Concentration);
        return factor > 0f ? originalCo / factor : 0f;
    }

    public static float lifespanDays(float totalHemoglobin, float correctedCo) {
        return totalHemoglobin > 0f && correctedCo > 0f
                ? totalHemoglobin * HEMOGLOBIN_CONVERSION_FACTOR / correctedCo
                : 0f;
    }

    public static boolean hasValidLifespan(float totalHemoglobin, float correctedCo) {
        return totalHemoglobin > 0f && correctedCo > 0f;
    }

    public static String diagnosis(float lifespanDays) {
        if (lifespanDays < MIN_NORMAL_DAYS) {
            return "红细胞寿命缩短，提示溶血风险";
        }
        if (lifespanDays > MAX_NORMAL_DAYS) {
            return "红细胞寿命偏长，提示造血偏缓";
        }
        return "红细胞寿命正常";
    }
}
