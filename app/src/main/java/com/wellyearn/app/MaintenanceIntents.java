package com.wellyearn.app;

import android.content.Intent;

final class MaintenanceIntents {

    static final String EXTRA_USER_ID = "maintenance_user_id";

    private MaintenanceIntents() {
    }

    static long getUserId(Intent intent) {
        return intent == null ? -1L : intent.getLongExtra(EXTRA_USER_ID, -1L);
    }
}
