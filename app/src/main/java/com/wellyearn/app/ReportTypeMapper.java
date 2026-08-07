package com.wellyearn.app;

final class ReportTypeMapper {

    static final String ALL = "全部";
    static final String GASTROINTESTINAL = "胃肠道检测";
    static final String RED_BLOOD_CELL = "红细胞寿命检测";
    static final String AIRWAY_INFLAMMATION = "呼吸道炎症检测";
    static final String PHYSICAL_EXAM = "体检报告";

    private ReportTypeMapper() {
    }

    static QueryTypes queryTypes(String displayType) {
        if (displayType == null || ALL.equals(displayType)) {
            return new QueryTypes("", "");
        }
        switch (displayType) {
            case GASTROINTESTINAL:
                return new QueryTypes(GASTROINTESTINAL, "胃肠道疾病检测");
            case AIRWAY_INFLAMMATION:
                return new QueryTypes(AIRWAY_INFLAMMATION, "呼吸道疾病检测");
            case PHYSICAL_EXAM:
                return new QueryTypes(PHYSICAL_EXAM, "体检模式");
            case RED_BLOOD_CELL:
            default:
                return new QueryTypes(displayType, displayType);
        }
    }

    static String displayName(String storedType) {
        if (storedType == null) return "未知类型";
        switch (storedType) {
            case "胃肠道疾病检测":
            case GASTROINTESTINAL:
                return GASTROINTESTINAL;
            case "呼吸道疾病检测":
            case AIRWAY_INFLAMMATION:
                return AIRWAY_INFLAMMATION;
            case "体检模式":
            case PHYSICAL_EXAM:
                return PHYSICAL_EXAM;
            case RED_BLOOD_CELL:
                return RED_BLOOD_CELL;
            default:
                return storedType;
        }
    }

    static final class QueryTypes {
        final String primary;
        final String alias;

        QueryTypes(String primary, String alias) {
            this.primary = primary;
            this.alias = alias;
        }
    }
}
