package com.wellyearn.app;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.OperationLog;

final class MaintenanceAudit {

    private MaintenanceAudit() {
    }

    static void write(
            AppDatabase database,
            String operator,
            String action,
            boolean success,
            String detail) {
        OperationLog log = new OperationLog();
        log.operatorUsername = operator == null ? "" : operator;
        log.action = action == null ? "" : action;
        log.reportId = -1L;
        log.reportFileName = "";
        log.detail = detail == null ? "" : detail;
        log.success = success;
        log.operationTime = System.currentTimeMillis();
        database.operationLogDao().insert(log);
    }
}
