package com.wellyearn.app.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.wellyearn.app.database.entity.Patient;
import java.util.List;

@Dao
public interface PatientDao {
    @Insert
    long insert(Patient patient);

    @Update
    void update(Patient patient);

    @Delete
    void delete(Patient patient);

    @Query("SELECT * FROM patients ORDER BY created_time DESC")
    List<Patient> getAllPatients();

    @Query("SELECT * FROM patients WHERE id = :patientId")
    Patient getPatientById(long patientId);

    @Query("SELECT * FROM patients WHERE name LIKE '%' || :keyword || '%' OR phone LIKE '%' || :keyword || '%'")
    List<Patient> searchPatients(String keyword);

    @Query("DELETE FROM patients")
    void deleteAll();
}