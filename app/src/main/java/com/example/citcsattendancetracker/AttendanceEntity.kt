package com.example.citcsattendancetracker

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(
            value = ["studentNumber", "sessionId"],
            unique = true
        )
    ]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val studentNumber: String,

    val dateKey: String,

    val date: String,

    val time: String,

    val status: String,

    @ColumnInfo(defaultValue = "'LEGACY'")
    val subjectCode: String = "LEGACY",

    @ColumnInfo(defaultValue = "'Legacy Attendance'")
    val subjectName: String = "Legacy Attendance",

    @ColumnInfo(defaultValue = "''")
    val sessionId: String = "",

    @ColumnInfo(defaultValue = "''")
    val proofUri: String = ""
)