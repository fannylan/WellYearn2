package com.wellyearn.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.database.entity.Admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManagementActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private long currentUserId;
    private Admin currentUser;
    private UserManagementAdapter adapter;
    private TextView textEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);
        database = AppDatabase.getInstance(this);
        currentUserId = MaintenanceIntents.getUserId(getIntent());
        textEmpty = findViewById(R.id.textEmpty);

        RecyclerView recyclerView = findViewById(R.id.recyclerUsers);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserManagementAdapter(new UserManagementAdapter.Listener() {
            @Override
            public void onEdit(Admin user) {
                showUserDialog(user);
            }

            @Override
            public void onDelete(Admin user) {
                confirmDelete(user);
            }
        });
        recyclerView.setAdapter(adapter);
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonAddUser).setOnClickListener(v -> showUserDialog(null));
        loadUsers();
    }

    private void loadUsers() {
        ioExecutor.execute(() -> {
            Admin operator = database.adminDao().getAdminById(currentUserId);
            boolean authorized = MaintenancePermissions.canManageNormalUsers(operator)
                    || MaintenancePermissions.canManageAdministrators(operator);
            if (!authorized) {
                runOnUiThread(() -> denyAccess("当前账号无用户管理权限"));
                return;
            }
            List<Admin> allUsers = database.adminDao().getAllAdmins();
            List<Admin> visibleUsers = new ArrayList<>();
            for (Admin user : allUsers) {
                if (MaintenancePermissions.isSuperUser(user)) continue;
                if (MaintenancePermissions.isSuperUser(operator)
                        || MaintenancePermissions.isNormalUser(user)) {
                    visibleUsers.add(user);
                }
            }
            runOnUiThread(() -> {
                currentUser = operator;
                ((TextView) findViewById(R.id.textScope)).setText(
                        MaintenancePermissions.isSuperUser(operator)
                                ? "可管理管理员和普通用户，并配置其权限"
                                : "可新增、编辑和删除普通用户");
                adapter.submit(visibleUsers);
                textEmpty.setVisibility(visibleUsers.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showUserDialog(Admin existing) {
        if (currentUser == null) return;
        boolean isSuper = MaintenancePermissions.isSuperUser(currentUser);
        boolean editing = existing != null;
        if (!isSuper && editing && !MaintenancePermissions.isNormalUser(existing)) {
            denyAccess("管理员只能设置普通用户");
            return;
        }

        int padding = Math.round(22 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, 0, padding, 0);

        EditText name = field("姓名", InputType.TYPE_CLASS_TEXT);
        EditText username = field("账号", InputType.TYPE_CLASS_TEXT);
        EditText password = field(editing ? "新密码（不修改请留空）" : "密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText phone = field("手机号码", InputType.TYPE_CLASS_PHONE);
        content.addView(name);
        content.addView(username);
        content.addView(password);
        content.addView(phone);

        Spinner role = new Spinner(this);
        List<String> roles = isSuper
                ? Arrays.asList(MaintenancePermissions.ROLE_ADMINISTRATOR,
                        MaintenancePermissions.ROLE_NORMAL_USER)
                : Arrays.asList(MaintenancePermissions.ROLE_NORMAL_USER);
        role.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, roles));
        content.addView(role);

        TextView permissionTitle = new TextView(this);
        permissionTitle.setText("角色权限（系统固定）");
        permissionTitle.setTextSize(16);
        permissionTitle.setPadding(0, padding / 2, 0, 0);
        content.addView(permissionTitle);

        CheckBox viewLogs = checkbox("查看操作日志");
        CheckBox manageNormals = checkbox("新增、编辑和删除普通用户");
        CheckBox deletePdf = checkbox("删除报告检索中的报告");
        content.addView(viewLogs);
        content.addView(manageNormals);
        content.addView(deletePdf);

        Runnable updatePermissionVisibility = () -> {
            boolean administrator = MaintenancePermissions.ROLE_ADMINISTRATOR.equals(
                    role.getSelectedItem().toString());
            viewLogs.setVisibility(administrator ? View.VISIBLE : View.GONE);
            manageNormals.setVisibility(administrator ? View.VISIBLE : View.GONE);
            viewLogs.setChecked(administrator);
            manageNormals.setChecked(administrator);
            deletePdf.setChecked(true);
            viewLogs.setEnabled(false);
            manageNormals.setEnabled(false);
            deletePdf.setEnabled(false);
        };
        role.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                updatePermissionVisibility.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (editing) {
            name.setText(existing.getName());
            username.setText(existing.getUsername());
            phone.setText(existing.getPhone());
            int roleIndex = roles.indexOf(existing.getRole());
            role.setSelection(Math.max(0, roleIndex));
        }
        updatePermissionVisibility.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "编辑用户" : "新增用户")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveUser(dialog, existing, name, username, password,
                        phone, role)));
        dialog.show();
    }

    private void saveUser(
            AlertDialog dialog,
            Admin existing,
            EditText name,
            EditText username,
            EditText password,
            EditText phone,
            Spinner role) {
        String nameValue = name.getText().toString().trim();
        String usernameValue = username.getText().toString().trim();
        String passwordValue = password.getText().toString();
        String phoneValue = phone.getText().toString().trim();
        if (nameValue.isEmpty()) { name.setError("请输入姓名"); return; }
        if (usernameValue.isEmpty()) { username.setError("请输入账号"); return; }
        if (existing == null && passwordValue.isEmpty()) {
            password.setError("请输入密码"); return;
        }
        if (phoneValue.isEmpty()) { phone.setError("请输入手机号码"); return; }

        String roleValue = role.getSelectedItem().toString();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        ioExecutor.execute(() -> {
            Admin freshOperator = database.adminDao().getAdminById(currentUserId);
            boolean canSetRole = MaintenancePermissions.isSuperUser(freshOperator)
                    || MaintenancePermissions.ROLE_NORMAL_USER.equals(roleValue);
            if (!canSetRole || !MaintenancePermissions.canManageNormalUsers(freshOperator)) {
                runOnUiThread(() -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    denyAccess("当前账号无权设置该用户");
                });
                return;
            }
            long existingId = existing == null ? -1L : existing.getId();
            if (database.adminDao().countOtherUsersWithUsername(
                    usernameValue, existingId) > 0) {
                runOnUiThread(() -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    username.setError("该账号已存在");
                });
                return;
            }

            Admin user = existing == null ? new Admin() :
                    database.adminDao().getAdminById(existing.getId());
            if (user == null) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "用户已不存在", Toast.LENGTH_LONG).show();
                    loadUsers();
                });
                return;
            }
            user.setName(nameValue);
            user.setUsername(usernameValue);
            if (!passwordValue.isEmpty()) user.setPassword(passwordValue);
            user.setPhone(phoneValue);
            user.setRole(roleValue);
            user.setEmail("");
            user.setPermissions(MaintenancePermissions.defaultPermissionsCsv(roleValue));
            if (existing == null) {
                user.setCreatedTime(System.currentTimeMillis());
                database.adminDao().insert(user);
            } else {
                database.adminDao().update(user);
            }
            MaintenanceAudit.write(database, freshOperator.getUsername(),
                    existing == null ? "新增用户" : "编辑用户", true,
                    roleValue + "：" + usernameValue);
            runOnUiThread(() -> {
                dialog.dismiss();
                Toast.makeText(this, existing == null ? "用户已新增" : "用户已更新",
                        Toast.LENGTH_SHORT).show();
                loadUsers();
            });
        });
    }

    private void confirmDelete(Admin user) {
        new AlertDialog.Builder(this)
                .setTitle("删除用户")
                .setMessage("确认删除“" + user.getName() + "（" + user.getUsername() + "）”？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteUser(user))
                .show();
    }

    private void deleteUser(Admin user) {
        ioExecutor.execute(() -> {
            Admin operator = database.adminDao().getAdminById(currentUserId);
            Admin target = database.adminDao().getAdminById(user.getId());
            boolean authorized = target != null && !MaintenancePermissions.isSuperUser(target)
                    && (MaintenancePermissions.isSuperUser(operator)
                    || (MaintenancePermissions.canManageNormalUsers(operator)
                    && MaintenancePermissions.isNormalUser(target)));
            if (!authorized) {
                runOnUiThread(() -> denyAccess("当前账号无权删除该用户"));
                return;
            }
            database.adminDao().delete(target);
            MaintenanceAudit.write(database, operator.getUsername(), "删除用户", true,
                    target.getRole() + "：" + target.getUsername());
            runOnUiThread(() -> {
                Toast.makeText(this, "用户已删除", Toast.LENGTH_SHORT).show();
                loadUsers();
            });
        });
    }

    private EditText field(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(inputType);
        return field;
    }

    private CheckBox checkbox(String text) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        return checkBox;
    }

    private void denyAccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
