package com.wellyearn.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Admin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaintenanceLoginActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private EditText editUsername;
    private EditText editPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance_login);
        database = AppDatabase.getInstance(this);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        editPassword.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonLogin).setOnClickListener(v -> login());
        ioExecutor.execute(() -> DefaultAdminProvisioner.ensureDefaultSuperAdmin(
                database.adminDao()));
    }

    private void login() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();
        if (username.isEmpty()) {
            editUsername.setError("请输入账号");
            return;
        }
        if (password.isEmpty()) {
            editPassword.setError("请输入密码");
            return;
        }

        findViewById(R.id.buttonLogin).setEnabled(false);
        ioExecutor.execute(() -> {
            DefaultAdminProvisioner.ensureDefaultSuperAdmin(database.adminDao());
            Admin user = database.adminDao().login(username, password);
            boolean success = user != null;
            MaintenanceAudit.write(database, username, "登录运维", success,
                    success ? "登录成功，角色：" + user.getRole() : "账号或密码错误");
            if (success) {
                database.adminDao().updateLastLoginTime(username, System.currentTimeMillis());
            }
            runOnUiThread(() -> {
                findViewById(R.id.buttonLogin).setEnabled(true);
                if (!success) {
                    editPassword.setError("账号或密码错误");
                    return;
                }
                Intent intent = new Intent(this, MaintenanceHomeActivity.class);
                intent.putExtra(MaintenanceIntents.EXTRA_USER_ID, user.getId());
                startActivity(intent);
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
