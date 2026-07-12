package com.wellyearn.app.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.wellyearn.app.database.dao.AdminDao;
import com.wellyearn.app.database.dao.PatientDao;
import com.wellyearn.app.database.dao.TestReportDao;
import com.wellyearn.app.database.entity.Admin;
import com.wellyearn.app.database.entity.Patient;
import com.wellyearn.app.database.entity.TestReport;

@Database(
        entities = {Patient.class, TestReport.class, Admin.class},
        version = 2,          // 保持版本2
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract PatientDao patientDao();
    public abstract TestReportDao testReportDao();
    public abstract AdminDao adminDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "wellyearn_database")
                            .fallbackToDestructiveMigration()   // 允许破坏性迁移，解决版本升级问题
                            .build();
                }
            }
        }
        return instance;
    }
}