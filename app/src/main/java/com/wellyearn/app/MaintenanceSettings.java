package com.wellyearn.app;

import android.content.Context;
import android.content.SharedPreferences;

final class MaintenanceSettings {

    private static final String PREFERENCES = "maintenance_settings";
    private static final String KEY_HOSPITAL_NAME = "hospital_name";

    private MaintenanceSettings() {
    }

    static String getHospitalName(Context context) {
        return preferences(context).getString(KEY_HOSPITAL_NAME, "未设置");
    }

    static void setHospitalName(Context context, String hospitalName) {
        preferences(context).edit()
                .putString(KEY_HOSPITAL_NAME, hospitalName == null ? "" : hospitalName.trim())
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
