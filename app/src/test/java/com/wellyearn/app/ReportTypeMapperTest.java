package com.wellyearn.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReportTypeMapperTest {

    @Test
    public void legacyStoredTypesUseRequestedDisplayNames() {
        assertEquals("胃肠道检测", ReportTypeMapper.displayName("胃肠道疾病检测"));
        assertEquals("呼吸道炎症检测", ReportTypeMapper.displayName("呼吸道疾病检测"));
        assertEquals("体检报告", ReportTypeMapper.displayName("体检模式"));
    }

    @Test
    public void queryIncludesCanonicalAndLegacyType() {
        ReportTypeMapper.QueryTypes query =
                ReportTypeMapper.queryTypes(ReportTypeMapper.PHYSICAL_EXAM);

        assertEquals("体检报告", query.primary);
        assertEquals("体检模式", query.alias);
    }

    @Test
    public void allTypesUsesEmptyDatabaseFilter() {
        ReportTypeMapper.QueryTypes query = ReportTypeMapper.queryTypes(ReportTypeMapper.ALL);

        assertEquals("", query.primary);
        assertEquals("", query.alias);
    }
}
