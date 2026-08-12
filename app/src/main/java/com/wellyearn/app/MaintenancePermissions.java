package com.wellyearn.app;

import com.wellyearn.app.database.entity.Admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MaintenancePermissions {

    static final String ROLE_SUPER_USER = "超级用户";
    static final String ROLE_ADMINISTRATOR = "管理员";
    static final String ROLE_NORMAL_USER = "普通用户";

    static final String VIEW_OPERATION_LOGS = "VIEW_OPERATION_LOGS";
    static final String MANAGE_NORMAL_USERS = "MANAGE_NORMAL_USERS";
    static final String USE_MAINTENANCE = "USE_MAINTENANCE";
    static final String DELETE_REPORT_PDF = "DELETE_REPORT_PDF";

    private static final String SEPARATOR = ",";
    private static final List<String> ALL = Arrays.asList(
            VIEW_OPERATION_LOGS,
            MANAGE_NORMAL_USERS,
            USE_MAINTENANCE,
            DELETE_REPORT_PDF);

    private MaintenancePermissions() {
    }

    static boolean isSuperUser(Admin user) {
        if (user == null) return false;
        if (DefaultAdminProvisioner.USERNAME.equals(user.getUsername())) return true;
        String role = user.getRole();
        return role != null && role.contains("超级");
    }

    static boolean isAdministrator(Admin user) {
        if (user == null || isSuperUser(user)) return false;
        return ROLE_ADMINISTRATOR.equals(user.getRole())
                || (user.getRole() != null && user.getRole().contains("管理员"));
    }

    static boolean isNormalUser(Admin user) {
        return user != null && ROLE_NORMAL_USER.equals(user.getRole());
    }

    static boolean canViewOperationLogs(Admin user) {
        return isSuperUser(user)
                || (isAdministrator(user) && hasPermission(user, VIEW_OPERATION_LOGS));
    }

    static boolean canManageNormalUsers(Admin user) {
        return isSuperUser(user)
                || (isAdministrator(user) && hasPermission(user, MANAGE_NORMAL_USERS));
    }

    static boolean canManageAdministrators(Admin user) {
        return isSuperUser(user);
    }

    static boolean canUseMaintenance(Admin user) {
        return isSuperUser(user) && hasPermission(user, USE_MAINTENANCE);
    }

    static boolean canSetHospitalName(Admin user) {
        return isSuperUser(user);
    }

    static boolean canDeleteReportPdf(Admin user) {
        return user != null && hasPermission(user, DELETE_REPORT_PDF);
    }

    static boolean hasPermission(Admin user, String permission) {
        if (user == null || permission == null) return false;
        if (isSuperUser(user)) return true;

        String stored = user.getPermissions();
        if (stored == null) {
            return defaultPermissionsForRole(user.getRole()).contains(permission);
        }
        return parse(stored).contains(permission);
    }

    static Set<String> effectivePermissions(Admin user) {
        if (user == null) return new LinkedHashSet<>();
        if (isSuperUser(user)) return new LinkedHashSet<>(ALL);
        if (user.getPermissions() == null) return defaultPermissionsForRole(user.getRole());
        return parse(user.getPermissions());
    }

    static Set<String> defaultPermissionsForRole(String role) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (ROLE_ADMINISTRATOR.equals(role)) {
            result.add(VIEW_OPERATION_LOGS);
            result.add(MANAGE_NORMAL_USERS);
            result.add(DELETE_REPORT_PDF);
        } else if (ROLE_NORMAL_USER.equals(role)) {
            result.add(DELETE_REPORT_PDF);
        } else if (role != null && role.contains("超级")) {
            result.addAll(ALL);
        }
        return result;
    }

    static String serialize(Iterable<String> permissions) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (permissions != null) {
            for (String permission : permissions) {
                if (ALL.contains(permission)) normalized.add(permission);
            }
        }
        return join(normalized);
    }

    static String defaultPermissionsCsv(String role) {
        return serialize(defaultPermissionsForRole(role));
    }

    static String allPermissions() {
        return join(ALL);
    }

    static String describe(Admin user) {
        List<String> labels = new ArrayList<>();
        if (canViewOperationLogs(user)) labels.add("查看操作日志");
        if (canManageNormalUsers(user)) labels.add("管理普通用户");
        if (canUseMaintenance(user)) labels.add("运维操作");
        if (canDeleteReportPdf(user)) labels.add("删除报告PDF");
        if (canSetHospitalName(user)) labels.add("设置医院名称");
        return labels.isEmpty() ? "无可用权限" : joinWithChineseComma(labels);
    }

    private static Set<String> parse(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) return result;
        for (String item : value.split(SEPARATOR)) {
            String normalized = item.trim();
            if (ALL.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private static String join(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(SEPARATOR);
            builder.append(value);
        }
        return builder.toString();
    }

    private static String joinWithChineseComma(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append("、");
            builder.append(value);
        }
        return builder.toString();
    }
}
