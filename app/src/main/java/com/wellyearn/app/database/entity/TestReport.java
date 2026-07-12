package com.wellyearn.app.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "test_reports",
        foreignKeys = @ForeignKey(
                entity = Patient.class,
                parentColumns = "id",
                childColumns = "patient_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("patient_id")}
)
public class TestReport {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "patient_id")
    public long patientId;

    @ColumnInfo(name = "report_number")
    public String reportNumber;

    @ColumnInfo(name = "test_type")
    public String testType;

    @ColumnInfo(name = "test_date")
    public long testDate;

    @ColumnInfo(name = "test_result")
    public String testResult;

    @ColumnInfo(name = "test_data")
    public String testData;  // 存储检测数据的JSON字符串

    @ColumnInfo(name = "remarks")
    public String remarks;

    @ColumnInfo(name = "doctor_name")
    public String doctorName;

    @ColumnInfo(name = "created_time")
    public long createdTime;

    public TestReport() {}

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPatientId() { return patientId; }
    public void setPatientId(long patientId) { this.patientId = patientId; }

    public String getReportNumber() { return reportNumber; }
    public void setReportNumber(String reportNumber) { this.reportNumber = reportNumber; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }

    public long getTestDate() { return testDate; }
    public void setTestDate(long testDate) { this.testDate = testDate; }

    public String getTestResult() { return testResult; }
    public void setTestResult(String testResult) { this.testResult = testResult; }

    public String getTestData() { return testData; }
    public void setTestData(String testData) { this.testData = testData; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
}