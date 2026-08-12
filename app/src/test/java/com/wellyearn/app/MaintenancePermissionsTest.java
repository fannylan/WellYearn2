package com.wellyearn.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wellyearn.app.database.entity.Admin;

import org.junit.Test;

import java.util.Arrays;

public class MaintenancePermissionsTest {

    @Test
    public void superUserHasEveryMaintenancePermission() {
        Admin user = user("huiyuen", MaintenancePermissions.ROLE_SUPER_USER, "");

        assertTrue(MaintenancePermissions.canManageAdministrators(user));
        assertTrue(MaintenancePermissions.canManageNormalUsers(user));
        assertTrue(MaintenancePermissions.canViewOperationLogs(user));
        assertTrue(MaintenancePermissions.canUseMaintenance(user));
        assertTrue(MaintenancePermissions.canSetHospitalName(user));
        assertTrue(MaintenancePermissions.canDeleteReportPdf(user));
    }

    @Test
    public void administratorKeepsFixedRoleCapabilities() {
        Admin user = user("admin", MaintenancePermissions.ROLE_ADMINISTRATOR,
                MaintenancePermissions.serialize(Arrays.asList(
                        MaintenancePermissions.VIEW_OPERATION_LOGS,
                        MaintenancePermissions.DELETE_REPORT_PDF)));

        assertFalse(MaintenancePermissions.canManageAdministrators(user));
        assertTrue(MaintenancePermissions.canManageNormalUsers(user));
        assertTrue(MaintenancePermissions.canViewOperationLogs(user));
        assertFalse(MaintenancePermissions.canUseMaintenance(user));
        assertTrue(MaintenancePermissions.canDeleteReportPdf(user));
    }

    @Test
    public void normalUserCanDeletePdfWithoutSeeingMaintenanceModules() {
        Admin user = user("normal", MaintenancePermissions.ROLE_NORMAL_USER,
                MaintenancePermissions.DELETE_REPORT_PDF);

        assertTrue(MaintenancePermissions.canDeleteReportPdf(user));
        assertFalse(MaintenancePermissions.canViewOperationLogs(user));
        assertFalse(MaintenancePermissions.canManageNormalUsers(user));
        assertFalse(MaintenancePermissions.canUseMaintenance(user));
    }

    @Test
    public void explicitEmptyPermissionSetDoesNotRevokeNormalUserRolePermission() {
        Admin user = user("normal", MaintenancePermissions.ROLE_NORMAL_USER, "");

        assertTrue(MaintenancePermissions.canDeleteReportPdf(user));
    }

    @Test
    public void legacyUsersReceiveRoleDefaultsWhenPermissionColumnIsNull() {
        Admin administrator = user(
                "legacy-admin", MaintenancePermissions.ROLE_ADMINISTRATOR, null);
        Admin normalUser = user("legacy-user", MaintenancePermissions.ROLE_NORMAL_USER, null);

        assertTrue(MaintenancePermissions.canViewOperationLogs(administrator));
        assertTrue(MaintenancePermissions.canManageNormalUsers(administrator));
        assertTrue(MaintenancePermissions.canDeleteReportPdf(normalUser));
    }

    private static Admin user(String username, String role, String permissions) {
        Admin user = new Admin();
        user.setUsername(username);
        user.setRole(role);
        user.setPermissions(permissions);
        return user;
    }
}
