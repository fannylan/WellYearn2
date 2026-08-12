package com.wellyearn.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Admin;
import com.wellyearn.app.database.entity.OperationLog;
import com.wellyearn.app.database.entity.TestReport;
import com.wellyearn.app.database.model.ReportSearchResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportSearchActivity extends AppCompatActivity {

    private static final int REQUEST_USB_DIRECTORY = 2101;
    private static final String GUEST_OPERATOR = "普通用户";

    private EditText editPatientName;
    private EditText editStartDate;
    private EditText editEndDate;
    private Spinner spinnerReportType;
    private Button buttonSearch;
    private Button buttonPrint;
    private Button buttonExportUsb;
    private Button buttonDelete;
    private TextView textResultCount;
    private TextView textSelectedCount;
    private TextView textEmpty;

    private AppDatabase database;
    private ReportSearchAdapter adapter;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    private long startDateMillis;
    private long endDateMillis;
    private List<ReportSearchResult> pendingUsbReports = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_search);

        database = AppDatabase.getInstance(this);
        initViews();
        initReportList();
        bindActions();
        initializeData();
    }

    private void initViews() {
        editPatientName = findViewById(R.id.editPatientName);
        editStartDate = findViewById(R.id.editStartDate);
        editEndDate = findViewById(R.id.editEndDate);
        spinnerReportType = findViewById(R.id.spinnerReportType);
        buttonSearch = findViewById(R.id.buttonSearch);
        buttonPrint = findViewById(R.id.buttonPrint);
        buttonExportUsb = findViewById(R.id.buttonExportUsb);
        buttonDelete = findViewById(R.id.buttonDelete);
        textResultCount = findViewById(R.id.textResultCount);
        textSelectedCount = findViewById(R.id.textSelectedCount);
        textEmpty = findViewById(R.id.textEmpty);
    }

    private void initReportList() {
        RecyclerView recyclerReports = findViewById(R.id.recyclerReports);
        adapter = new ReportSearchAdapter(new ReportSearchAdapter.Listener() {
            @Override
            public void onSelectionChanged(int selectedCount) {
                updateSelectionActions(selectedCount);
            }

            @Override
            public void onPdfClick(ReportSearchResult report) {
                openPdf(report);
            }
        });
        recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        recyclerReports.setAdapter(adapter);
    }

    private void bindActions() {
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        editStartDate.setOnClickListener(v -> showDatePicker(true));
        editEndDate.setOnClickListener(v -> showDatePicker(false));
        buttonSearch.setOnClickListener(v -> loadReports());
        findViewById(R.id.buttonReset).setOnClickListener(v -> resetFilters());
        buttonPrint.setOnClickListener(v -> printSelectedReports());
        buttonExportUsb.setOnClickListener(v -> chooseUsbDirectory());
        buttonDelete.setOnClickListener(v -> requestUserLoginForDeletion());
    }

    private void initializeData() {
        ioExecutor.execute(() -> {
            DefaultAdminProvisioner.ensureDefaultSuperAdmin(database.adminDao());
            List<ReportSearchResult> reports = database.testReportDao().searchReports(
                    "", 0, 0, "", "");
            runOnUiThread(() -> showReports(reports));
        });
    }

    private void showDatePicker(boolean startDate) {
        Calendar calendar = Calendar.getInstance();
        long existing = startDate ? startDateMillis : endDateMillis;
        if (existing > 0) calendar.setTimeInMillis(existing);

        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth,
                            startDate ? 0 : 23,
                            startDate ? 0 : 59,
                            startDate ? 0 : 59);
                    selected.set(Calendar.MILLISECOND, startDate ? 0 : 999);
                    if (startDate) {
                        startDateMillis = selected.getTimeInMillis();
                        editStartDate.setText(dateFormat.format(selected.getTime()));
                    } else {
                        endDateMillis = selected.getTimeInMillis();
                        editEndDate.setText(dateFormat.format(selected.getTime()));
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void resetFilters() {
        editPatientName.setText("");
        editStartDate.setText("");
        editEndDate.setText("");
        startDateMillis = 0;
        endDateMillis = 0;
        spinnerReportType.setSelection(0);
        loadReports();
    }

    private void loadReports() {
        if (startDateMillis > 0 && endDateMillis > 0 && startDateMillis > endDateMillis) {
            Toast.makeText(this, "开始日期不能晚于结束日期", Toast.LENGTH_SHORT).show();
            return;
        }

        String patientName = editPatientName.getText().toString().trim();
        String displayType = String.valueOf(spinnerReportType.getSelectedItem());
        ReportTypeMapper.QueryTypes queryTypes = ReportTypeMapper.queryTypes(displayType);
        long queryStartDate = startDateMillis;
        long queryEndDate = endDateMillis;

        buttonSearch.setEnabled(false);
        ioExecutor.execute(() -> {
            List<ReportSearchResult> reports = database.testReportDao().searchReports(
                    patientName,
                    queryStartDate,
                    queryEndDate,
                    queryTypes.primary,
                    queryTypes.alias);
            runOnUiThread(() -> {
                buttonSearch.setEnabled(true);
                showReports(reports);
            });
        });
    }

    private void showReports(List<ReportSearchResult> reports) {
        if (isFinishing() || isDestroyed()) return;
        adapter.setReports(reports);
        int count = reports == null ? 0 : reports.size();
        textResultCount.setText(String.format(Locale.CHINA, "共 %d 条报告", count));
        textEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void updateSelectionActions(int selectedCount) {
        textSelectedCount.setText(String.format(Locale.CHINA, "已选 %d 条", selectedCount));
        boolean enabled = selectedCount > 0;
        buttonPrint.setEnabled(enabled);
        buttonExportUsb.setEnabled(enabled);
        buttonDelete.setEnabled(enabled);
    }

    private List<ReportSearchResult> selectedReportsWithPdf() {
        List<ReportSearchResult> result = new ArrayList<>();
        for (ReportSearchResult report : adapter.getSelectedReports()) {
            if (!TextUtils.isEmpty(report.pdfUri)) result.add(report);
        }
        if (result.isEmpty()) {
            Toast.makeText(this, "所选报告没有可用的PDF", Toast.LENGTH_SHORT).show();
        }
        return result;
    }

    private void openPdf(ReportSearchResult report) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(report.pdfUri), "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            logOperationAsync(GUEST_OPERATOR, "查看PDF", report, true, "已打开PDF查看器");
        } catch (RuntimeException error) {
            logOperationAsync(GUEST_OPERATOR, "查看PDF", report, false, error.getMessage());
            Toast.makeText(this, "无法打开PDF：" + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void printSelectedReports() {
        List<ReportSearchResult> reports = selectedReportsWithPdf();
        if (reports.isEmpty()) return;

        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        int submitted = 0;
        for (ReportSearchResult report : reports) {
            try {
                String fileName = reportFileName(report);
                PrintAttributes attributes = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build();
                printManager.print(
                        fileName,
                        new PdfPrintDocumentAdapter(
                                getContentResolver(), Uri.parse(report.pdfUri), fileName),
                        attributes);
                submitted++;
                logOperationAsync(GUEST_OPERATOR, "打印PDF", report, true, "已提交打印任务");
            } catch (RuntimeException error) {
                logOperationAsync(GUEST_OPERATOR, "打印PDF", report, false, error.getMessage());
            }
        }
        Toast.makeText(
                this,
                String.format(Locale.CHINA, "已提交 %d 个打印任务", submitted),
                Toast.LENGTH_SHORT).show();
    }

    private void chooseUsbDirectory() {
        List<ReportSearchResult> reports = selectedReportsWithPdf();
        if (reports.isEmpty()) return;
        pendingUsbReports = new ArrayList<>(reports);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            Toast.makeText(this, "请选择U盘中的目标文件夹", Toast.LENGTH_LONG).show();
            startActivityForResult(intent, REQUEST_USB_DIRECTORY);
        } catch (ActivityNotFoundException error) {
            pendingUsbReports = Collections.emptyList();
            Toast.makeText(this, "系统不支持选择U盘目录", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_USB_DIRECTORY) return;

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            pendingUsbReports = Collections.emptyList();
            return;
        }

        Uri treeUri = data.getData();
        List<ReportSearchResult> reports = new ArrayList<>(pendingUsbReports);
        pendingUsbReports = Collections.emptyList();
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (SecurityException ignored) {
            // The current one-time grant remains sufficient for this export.
        }
        ioExecutor.execute(() -> exportReportsToDirectory(treeUri, reports));
    }

    private void exportReportsToDirectory(Uri treeUri, List<ReportSearchResult> reports) {
        ContentResolver resolver = getContentResolver();
        int successCount = 0;
        String treeDocumentId;
        try {
            treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (RuntimeException error) {
            runOnUiThread(() -> Toast.makeText(
                    this, "无法访问所选U盘目录", Toast.LENGTH_LONG).show());
            return;
        }
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);

        for (ReportSearchResult report : reports) {
            Uri outputUri = null;
            try {
                outputUri = DocumentsContract.createDocument(
                        resolver, parent, "application/pdf", reportFileName(report));
                if (outputUri == null) throw new IOException("无法在U盘创建PDF文件");
                copyUri(resolver, Uri.parse(report.pdfUri), outputUri);
                successCount++;
                writeOperationLog(GUEST_OPERATOR, "发送PDF到U盘", report, true,
                        "已复制到所选U盘目录");
            } catch (Exception error) {
                if (outputUri != null) {
                    try {
                        DocumentsContract.deleteDocument(resolver, outputUri);
                    } catch (Exception ignored) {
                    }
                }
                writeOperationLog(GUEST_OPERATOR, "发送PDF到U盘", report, false,
                        safeMessage(error));
            }
        }

        int finalSuccessCount = successCount;
        runOnUiThread(() -> Toast.makeText(
                this,
                String.format(Locale.CHINA, "已发送 %d/%d 份PDF到U盘",
                        finalSuccessCount, reports.size()),
                Toast.LENGTH_LONG).show());
    }

    private static void copyUri(ContentResolver resolver, Uri source, Uri destination)
            throws IOException {
        try (InputStream input = resolver.openInputStream(source);
             OutputStream output = resolver.openOutputStream(destination, "w")) {
            if (input == null) throw new IOException("无法读取源PDF");
            if (output == null) throw new IOException("无法写入U盘PDF");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

    private void requestUserLoginForDeletion() {
        List<ReportSearchResult> reports = selectedReportsWithPdf();
        if (reports.isEmpty()) return;

        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);

        TextView hint = new TextView(this);
        hint.setText("删除PDF需要拥有“删除报告PDF”权限的用户登录。"
                + "超级用户、管理员或已授权普通用户均可操作。");
        hint.setTypeface(null, Typeface.BOLD);
        content.addView(hint);

        EditText username = new EditText(this);
        username.setHint("用户账号");
        username.setSingleLine(true);
        content.addView(username);

        EditText password = new EditText(this);
        password.setHint("用户密码");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(password);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除授权登录")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("登录", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String usernameValue = username.getText().toString().trim();
                    String passwordValue = password.getText().toString();
                    if (usernameValue.isEmpty() || passwordValue.isEmpty()) {
                        password.setError("请输入账号和密码");
                        return;
                    }
                    authenticateAndConfirmDeletion(
                            usernameValue, passwordValue, reports, dialog, password);
                }));
        dialog.show();
    }

    private void authenticateAndConfirmDeletion(
            String username,
            String password,
            List<ReportSearchResult> reports,
            AlertDialog loginDialog,
            EditText passwordInput) {
        ioExecutor.execute(() -> {
            DefaultAdminProvisioner.ensureDefaultSuperAdmin(database.adminDao());
            Admin admin = database.adminDao().login(username, password);
            boolean authorized = MaintenancePermissions.canDeleteReportPdf(admin);
            writeOperationLog(username, "报告PDF删除授权", null, authorized,
                    authorized ? "报告PDF删除授权成功" : "账号、密码或删除权限无效");
            if (authorized) {
                database.adminDao().updateLastLoginTime(username, System.currentTimeMillis());
            }
            runOnUiThread(() -> {
                if (!authorized) {
                    passwordInput.setError("账号、密码错误或无删除权限");
                    return;
                }
                loginDialog.dismiss();
                showDeleteConfirmation(username, reports);
            });
        });
    }

    private void showDeleteConfirmation(String adminUsername, List<ReportSearchResult> reports) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除PDF")
                .setMessage(String.format(
                        Locale.CHINA,
                        "将删除已选的 %d 份PDF文件，报告记录会保留但PDF链接将清空。是否继续？",
                        reports.size()))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) ->
                        ioExecutor.execute(() -> deletePdfFiles(adminUsername, reports)))
                .show();
    }

    private void deletePdfFiles(String adminUsername, List<ReportSearchResult> reports) {
        int successCount = 0;
        for (ReportSearchResult report : reports) {
            try {
                int deleted = getContentResolver().delete(Uri.parse(report.pdfUri), null, null);
                if (deleted <= 0) throw new IOException("PDF不存在或无法删除");

                TestReport entity = database.testReportDao().getReportById(report.reportId);
                if (entity != null) {
                    entity.setPdfUri("");
                    entity.setPdfFileName("");
                    database.testReportDao().update(entity);
                }
                successCount++;
                writeOperationLog(adminUsername, "删除PDF", report, true,
                        "已删除PDF并清空报告链接");
            } catch (Exception error) {
                writeOperationLog(adminUsername, "删除PDF", report, false,
                        safeMessage(error));
            }
        }

        int finalSuccessCount = successCount;
        runOnUiThread(() -> {
            Toast.makeText(
                    this,
                    String.format(Locale.CHINA, "已删除 %d/%d 份PDF",
                            finalSuccessCount, reports.size()),
                    Toast.LENGTH_LONG).show();
            loadReports();
        });
    }

    private void logOperationAsync(
            String operator,
            String action,
            ReportSearchResult report,
            boolean success,
            String detail) {
        ioExecutor.execute(() -> writeOperationLog(
                operator, action, report, success, detail));
    }

    private void writeOperationLog(
            String operator,
            String action,
            ReportSearchResult report,
            boolean success,
            String detail) {
        OperationLog log = new OperationLog();
        log.operatorUsername = operator;
        log.action = action;
        log.reportId = report == null ? -1 : report.reportId;
        log.reportFileName = report == null ? "" : reportFileName(report);
        log.detail = detail == null ? "" : detail;
        log.success = success;
        log.operationTime = System.currentTimeMillis();
        database.operationLogDao().insert(log);
    }

    private static String reportFileName(ReportSearchResult report) {
        if (!TextUtils.isEmpty(report.pdfFileName)) return report.pdfFileName;
        return "report_" + report.reportId + ".pdf";
    }

    private static String safeMessage(Throwable error) {
        if (error == null || TextUtils.isEmpty(error.getMessage())) return "未知错误";
        return error.getMessage();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
