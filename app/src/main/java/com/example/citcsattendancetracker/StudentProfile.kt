
package com.example.citcsattendancetracker

data class StudentProfile(
    val fullName: String = "",
    val studentNumber: String = "",
    val course: String = "",
    val section: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
