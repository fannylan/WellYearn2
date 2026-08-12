package com.wellyearn.app.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.wellyearn.app.database.entity.Admin;
import java.util.List;

@Dao
public interface AdminDao {
    @Insert
    long insert(Admin admin);

    @Update
    void update(Admin admin);

    @Delete
    void delete(Admin admin);

    @Query("SELECT * FROM admins ORDER BY created_time DESC")
    List<Admin> getAllAdmins();

    @Query("SELECT * FROM admins WHERE id = :id")
    Admin getAdminById(long id);

    @Query("SELECT * FROM admins WHERE username = :username")
    Admin getAdminByUsername(String username);

    @Query("SELECT * FROM admins WHERE username = :username AND password = :password")
    Admin login(String username, String password);

    @Query("UPDATE admins SET last_login_time = :lastLoginTime WHERE username = :username")
    void updateLastLoginTime(String username, long lastLoginTime);

    @Query("SELECT COUNT(*) FROM admins")
    int getAdminCount();

    @Query("SELECT COUNT(*) FROM admins WHERE username = :username AND id != :excludedId")
    int countOtherUsersWithUsername(String username, long excludedId);
}
