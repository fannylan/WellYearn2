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
        if (existing != null) return existing;

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(PASSWORD);
        admin.setRole("超级管理员");
        admin.setName("默认超级用户");
        admin.setPhone("");
        admin.setEmail("");
        admin.setCreatedTime(System.currentTimeMillis());
        adminDao.insert(admin);
        return adminDao.getAdminByUsername(USERNAME);
    }

    static boolean isAdministrator(Admin admin) {
        return admin != null
                && admin.getRole() != null
                && admin.getRole().contains("管理员");
    }
}
