package com.wellyearn.app.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;
import com.wellyearn.app.database.dao.AdminDao;
import com.wellyearn.app.database.dao.PatientDao;
import com.wellyearn.app.database.dao.TestReportDao;
import com.wellyearn.app.database.entity.Admin;
import com.wellyearn.app.database.entity.Patient;
import com.wellyearn.app.database.entity.TestReport;

@Database(
        entities = {Patient.class, TestReport.class, Admin.class},
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract PatientDao patientDao();
    public abstract TestReportDao testReportDao();
    public abstract AdminDao adminDao();

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE test_reports ADD COLUMN patient_info TEXT");
            database.execSQL("ALTER TABLE test_reports ADD COLUMN detection_data_chart TEXT");
            database.execSQL("ALTER TABLE test_reports ADD COLUMN diagnosis_result TEXT");
            database.execSQL("ALTER TABLE test_reports ADD COLUMN pdf_file_name TEXT");
            database.execSQL("ALTER TABLE test_reports ADD COLUMN pdf_uri TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "wellyearn_database")
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
