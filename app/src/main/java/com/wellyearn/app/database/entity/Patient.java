package com.wellyearn.app.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "patients")
public class Patient {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "gender")
    public String gender;

    @ColumnInfo(name = "age")
    public int age;

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "id_card")
    public String idCard;

    // 新增：患者类型（门诊、急诊、住院、体检）
    @ColumnInfo(name = "patient_type")
    public String patientType;

    @ColumnInfo(name = "created_time")
    public long createdTime;

    @ColumnInfo(name = "updated_time")
    public long updatedTime;

    // 构造函数
    public Patient() {}

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }

    public String getPatientType() { return patientType; }
    public void setPatientType(String patientType) { this.patientType = patientType; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    public long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(long updatedTime) { this.updatedTime = updatedTime; }
}