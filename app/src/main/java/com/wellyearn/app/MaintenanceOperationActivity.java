package com.wellyearn.app;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Admin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaintenanceOperationActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance_operation);
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        long userId = MaintenanceIntents.getUserId(getIntent());
        AppDatabase database = AppDatabase.getInstance(this);
        ioExecutor.execute(() -> {
            Admin user = database.adminDao().getAdminById(userId);
            if (!MaintenancePermissions.canUseMaintenance(user)) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "当前账号无运维操作权限", Toast.LENGTH_LONG).show();
                    finish();
                });
                return;
            }
            MaintenanceAudit.write(database, user.getUsername(), "进入运维操作", true,
                    "运维操作模块功能待定");
        });
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
