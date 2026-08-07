package com.wellyearn.app.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.wellyearn.app.database.entity.OperationLog;

import java.util.List;

@Dao
public interface OperationLogDao {
    @Insert
    long insert(OperationLog operationLog);

    @Query("SELECT * FROM operation_logs ORDER BY operation_time DESC")
    List<OperationLog> getAllLogs();
}
