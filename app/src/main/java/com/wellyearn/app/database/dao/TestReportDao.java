package com.wellyearn.app.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.wellyearn.app.database.entity.TestReport;
import com.wellyearn.app.database.model.ReportSearchResult;
import java.util.List;

@Dao
public interface TestReportDao {
    @Insert
    long insert(TestReport report);

    @Update
    void update(TestReport report);

    @Delete
    void delete(TestReport report);

    @Query("SELECT * FROM test_reports ORDER BY test_date DESC")
    List<TestReport> getAllReports();

    @Query("SELECT * FROM test_reports WHERE patient_id = :patientId ORDER BY test_date DESC")
    List<TestReport> getReportsByPatientId(long patientId);

    @Query("SELECT * FROM test_reports WHERE report_number = :reportNumber")
    TestReport getReportByNumber(String reportNumber);

    @Query("SELECT * FROM test_reports WHERE test_type = :testType ORDER BY test_date DESC")
    List<TestReport> getReportsByType(String testType);

    @Query("DELETE FROM test_reports WHERE patient_id = :patientId")
    void deleteReportsByPatientId(long patientId);
    @Query("SELECT * FROM test_reports WHERE id = :reportId")
    TestReport getReportById(long reportId);

    @Query("SELECT r.id AS reportId, p.name AS patientName, "
            + "r.test_date AS reportDate, r.test_type AS reportType, "
            + "r.pdf_file_name AS pdfFileName, r.pdf_uri AS pdfUri "
            + "FROM test_reports r "
            + "INNER JOIN patients p ON p.id = r.patient_id "
            + "WHERE (:patientName = '' OR p.name LIKE '%' || :patientName || '%') "
            + "AND (:startDate = 0 OR r.test_date >= :startDate) "
            + "AND (:endDate = 0 OR r.test_date <= :endDate) "
            + "AND (:reportType = '' OR r.test_type = :reportType "
            + "OR r.test_type = :reportTypeAlias) "
            + "ORDER BY r.test_date DESC")
    List<ReportSearchResult> searchReports(
            String patientName,
            long startDate,
            long endDate,
            String reportType,
            String reportTypeAlias);
}
