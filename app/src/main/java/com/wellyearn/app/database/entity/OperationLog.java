package com.wellyearn.app.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "operation_logs",
        indices = {@Index("operation_time")}
)
public class OperationLog {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "operator_username")
    public String operatorUsername;

    @ColumnInfo(name = "action")
    public String action;

    @ColumnInfo(name = "report_id")
    public long reportId;

    @ColumnInfo(name = "report_file_name")
    public String reportFileName;

    @ColumnInfo(name = "detail")
    public String detail;

    @ColumnInfo(name = "success")
    public boolean success;

    @ColumnInfo(name = "operation_time")
    public long operationTime;
}
