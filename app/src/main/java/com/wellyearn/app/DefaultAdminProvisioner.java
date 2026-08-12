package com.wellyearn.app;

import com.wellyearn.app.database.dao.AdminDao;
import com.wellyearn.app.database.entity.Admin;

final class DefaultAdminProvisioner {

    static final String USERNAME = "huiyuen";
    static final String PASSWORD = "123456";

    private DefaultAdminProvisioner() {
    }

    static synchronized Admin ensureDefaultSuperAdmin(AdminDao adminDao) {
        Admin existing = adminDao.getAdminByUsername(USERNAME);
        if (existing != null) {
            existing.setPassword(PASSWORD);
            existing.setRole(MaintenancePermissions.ROLE_SUPER_USER);
            if (existing.getName() == null || existing.getName().trim().isEmpty()) {
                existing.setName("默认超级用户");
            }
            existing.setPermissions(MaintenancePermissions.allPermissions());
            adminDao.update(existing);
            return existing;
        }

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(PASSWORD);
        admin.setRole(MaintenancePermissions.ROLE_SUPER_USER);
        admin.setName("默认超级用户");
        admin.setPhone("");
        admin.setEmail("");
        admin.setPermissions(MaintenancePermissions.allPermissions());
        admin.setCreatedTime(System.currentTimeMillis());
        adminDao.insert(admin);
        return adminDao.getAdminByUsername(USERNAME);
    }

    static boolean isAdministrator(Admin admin) {
        return MaintenancePermissions.isSuperUser(admin)
                || MaintenancePermissions.isAdministrator(admin);
    }
}
