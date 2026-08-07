package com.wellyearn.app.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "test_reports",
        foreignKeys = @ForeignKey(entity = Patient.class,
                parentColumns = "id",
                childColumns = "patient_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("patient_id")})
public class TestReport {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "patient_id")
    public long patientId;

    @ColumnInfo(name = "report_number")
    public String reportNumber;

    @ColumnInfo(name = "test_type")
    public String testType;          // 存储检测名称（胃肠道/红细胞/呼吸道）

    @ColumnInfo(name = "test_date")
    public long testDate;

    @ColumnInfo(name = "test_result")
    public String testResult;        // 存储结果数据（JSON等）

    @ColumnInfo(name = "test_data")
    public String testData;          // 额外数据，可留空

    @ColumnInfo(name = "patient_info")
    public String patientInfo;       // 报告第一部分：患者信息 JSON

    @ColumnInfo(name = "detection_data_chart")
    public String detectionDataChart; // 报告第二部分：检测数据和图表 JSON

    @ColumnInfo(name = "diagnosis_result")
    public String diagnosisResult;   // 报告第三部分：诊断结果 JSON

    @ColumnInfo(name = "pdf_file_name")
    public String pdfFileName;

    @ColumnInfo(name = "pdf_uri")
    public String pdfUri;

    @ColumnInfo(name = "remarks")
    public String remarks;           // 存储申请科室

    @ColumnInfo(name = "doctor_name")
    public String doctorName;        // 存储申请医生

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

    public String getPatientInfo() { return patientInfo; }
    public void setPatientInfo(String patientInfo) { this.patientInfo = patientInfo; }

    public String getDetectionDataChart() { return detectionDataChart; }
    public void setDetectionDataChart(String detectionDataChart) { this.detectionDataChart = detectionDataChart; }

    public String getDiagnosisResult() { return diagnosisResult; }
    public void setDiagnosisResult(String diagnosisResult) { this.diagnosisResult = diagnosisResult; }

    public String getPdfFileName() { return pdfFileName; }
    public void setPdfFileName(String pdfFileName) { this.pdfFileName = pdfFileName; }

    public String getPdfUri() { return pdfUri; }
    public void setPdfUri(String pdfUri) { this.pdfUri = pdfUri; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
}
