
package com.example.citcsattendancetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // Get all attendance records of a student
    @Query("""
        SELECT * FROM attendance_records
        WHERE studentNumber = :studentNumber
        ORDER BY dateKey DESC, time DESC
    """)
    fun getRecordsForStudent(
        studentNumber: String
    ): Flow<List<AttendanceEntity>>

    // Get attendance records for a specific subject
    @Query("""
        SELECT * FROM attendance_records
        WHERE studentNumber = :studentNumber
        AND subjectCode = :subjectCode
        ORDER BY dateKey DESC, time DESC
    """)
    fun getRecordsForSubject(
        studentNumber: String,
        subjectCode: String
    ): Flow<List<AttendanceEntity>>

    // Save attendance
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttendance(
        attendance: AttendanceEntity
    ): Long
}
