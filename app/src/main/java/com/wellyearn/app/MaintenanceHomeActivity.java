package com.wellyearn.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Admin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaintenanceHomeActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private long userId;
    private Admin currentUser;
    private Button buttonUsers;
    private Button buttonLogs;
    private Button buttonOperations;
    private Button buttonHospital;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance_home);
        database = AppDatabase.getInstance(this);
        userId = MaintenanceIntents.getUserId(getIntent());

        buttonUsers = findViewById(R.id.buttonUsers);
        buttonLogs = findViewById(R.id.buttonLogs);
        buttonOperations = findViewById(R.id.buttonOperations);
        buttonHospital = findViewById(R.id.buttonHospital);

        findViewById(R.id.buttonLogout).setOnClickListener(v -> logout());
        buttonUsers.setOnClickListener(v -> openProtected(UserManagementActivity.class));
        buttonLogs.setOnClickListener(v -> openProtected(OperationLogActivity.class));
        buttonOperations.setOnClickListener(v -> openProtected(
                MaintenanceOperationActivity.class));
        buttonHospital.setOnClickListener(v -> showHospitalDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentUser();
    }

    private void loadCurrentUser() {
        ioExecutor.execute(() -> {
            Admin user = database.adminDao().getAdminById(userId);
            runOnUiThread(() -> {
                if (user == null) {
                    Toast.makeText(this, "登录已失效，请重新登录", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                currentUser = user;
                renderUser();
            });
        });
    }

    private void renderUser() {
        ((TextView) findViewById(R.id.textCurrentUser)).setText(
                currentUser.getName() + "（" + currentUser.getUsername() + "） · "
                        + currentUser.getRole());
        ((TextView) findViewById(R.id.textPermissions)).setText(
                "当前权限：" + MaintenancePermissions.describe(currentUser));
        ((TextView) findViewById(R.id.textHospital)).setText(
                "设备所在医院：" + MaintenanceSettings.getHospitalName(this));

        buttonUsers.setEnabled(MaintenancePermissions.canManageNormalUsers(currentUser)
                || MaintenancePermissions.canManageAdministrators(currentUser));
        buttonLogs.setEnabled(MaintenancePermissions.canViewOperationLogs(currentUser));
        buttonOperations.setEnabled(MaintenancePermissions.canUseMaintenance(currentUser));
        buttonHospital.setVisibility(MaintenancePermissions.canSetHospitalName(currentUser)
                ? View.VISIBLE : View.GONE);
    }

    private void openProtected(Class<?> target) {
        if (currentUser == null) return;
        Intent intent = new Intent(this, target);
        intent.putExtra(MaintenanceIntents.EXTRA_USER_ID, currentUser.getId());
        startActivity(intent);
    }

    private void showHospitalDialog() {
        if (!MaintenancePermissions.canSetHospitalName(currentUser)) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("请输入设备所在医院名称");
        String current = MaintenanceSettings.getHospitalName(this);
        if (!"未设置".equals(current)) input.setText(current);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设置设备所在医院")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError("医院名称不能为空");
                        return;
                    }
                    MaintenanceSettings.setHospitalName(this, name);
                    ((TextView) findViewById(R.id.textHospital)).setText(
                            "设备所在医院：" + name);
                    ioExecutor.execute(() -> MaintenanceAudit.write(database,
                            currentUser.getUsername(), "设置医院名称", true, name));
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void logout() {
        if (currentUser != null) {
            ioExecutor.execute(() -> MaintenanceAudit.write(database,
                    currentUser.getUsername(), "退出运维", true, "主动退出"));
        }
        startActivity(new Intent(this, MaintenanceLoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
