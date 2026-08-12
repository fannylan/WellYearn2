package com.wellyearn.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Admin;
import com.wellyearn.app.database.entity.OperationLog;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OperationLogActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private long userId;
    private OperationLogAdapter adapter;
    private TextView textEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operation_log);
        database = AppDatabase.getInstance(this);
        userId = MaintenanceIntents.getUserId(getIntent());
        textEmpty = findViewById(R.id.textEmpty);
        RecyclerView recyclerView = findViewById(R.id.recyclerOperationLogs);
        adapter = new OperationLogAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonRefresh).setOnClickListener(v -> loadLogs());
        loadLogs();
    }

    private void loadLogs() {
        ioExecutor.execute(() -> {
            Admin user = database.adminDao().getAdminById(userId);
            if (!MaintenancePermissions.canViewOperationLogs(user)) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "当前账号无权查看操作日志", Toast.LENGTH_LONG).show();
                    finish();
                });
                return;
            }
            List<OperationLog> logs = database.operationLogDao().getAllLogs();
            runOnUiThread(() -> {
                adapter.submit(logs);
                textEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
                ((TextView) findViewById(R.id.textLogCount)).setText(
                        "共 " + logs.size() + " 条操作记录");
            });
        });
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
