package com.wellyearn.app;

/** Age-specific FeNO clinical interpretation rules for airway inflammation. */
public final class AirwayInflammationDiagnosisRules {

    private static final int ADULT_MIN_AGE = 12;
    private static final float ADULT_LOW_UPPER = 25f;
    private static final float ADULT_HIGH_LOWER = 50f;
    private static final float CHILD_LOW_UPPER = 20f;
    private static final float CHILD_HIGH_LOWER = 35f;
    private static final float CO2_REFERENCE_CONCENTRATION = 500f;

    private AirwayInflammationDiagnosisRules() {}

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public static boolean isAdult(int age) {
        return age >= ADULT_MIN_AGE;
    }

    public static float correctionFactor(float co2Concentration) {
        return co2Concentration / CO2_REFERENCE_CONCENTRATION;
    }

    public static boolean hasValidCorrectionFactor(float co2Concentration) {
        return correctionFactor(co2Concentration) > 0f;
    }

    public static float correctedNo(float originalNo, float co2Concentration) {
        float factor = correctionFactor(co2Concentration);
        return factor > 0f ? originalNo / factor : 0f;
    }

    public static RiskLevel riskLevel(int age, float correctedNo) {
        if (isAdult(age)) {
            if (correctedNo < ADULT_LOW_UPPER) return RiskLevel.LOW;
            if (correctedNo <= ADULT_HIGH_LOWER) return RiskLevel.MEDIUM;
            return RiskLevel.HIGH;
        }
        if (correctedNo < CHILD_LOW_UPPER) return RiskLevel.LOW;
        if (correctedNo <= CHILD_HIGH_LOWER) return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    public static String riskLabel(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return "低风险";
            case MEDIUM:
                return "中等风险";
            case HIGH:
                return "高风险";
            default:
                throw new IllegalArgumentException("未知风险等级: " + riskLevel);
        }
    }

    public static String diagnosis(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return "嗜酸性气道炎症的可能性低，患者使用ICS治疗获益的可能性较小。"
                        + "诊断时应考虑其他诊断，并结合是否存在慢性阻塞性肺疾病（COPD）、"
                        + "胃食管反流（GERD）等情况综合判断。";
            case MEDIUM:
                return "提示可能存在气道炎症，但需结合患者临床症状、病史等其他信息进一步诊断，"
                        + "进行综合判断。";
            case HIGH:
                return "明确提示存在嗜酸性气道炎症，患者对ICS治疗有效的可能性很大。";
            default:
                throw new IllegalArgumentException("未知风险等级: " + riskLevel);
        }
    }

    public static String standardForAge(int age) {
        return isAdult(age)
                ? "成人：<25 ppb低风险，25-50 ppb中等风险，>50 ppb高风险"
                : "儿童：<20 ppb低风险，20-35 ppb中等风险，>35 ppb高风险";
    }
}
