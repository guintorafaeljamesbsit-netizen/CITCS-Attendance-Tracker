
package com.example.citcsattendancetracker

data class SubjectInfo(
    val code: String,
    val name: String,
    val professor: String,
    val days: List<String>,
    val startTime: String,
    val endTime: String
)

object SubjectData {

    val subjects = listOf(

        SubjectInfo(
            code = "SLEEP101",
            name = "Advanced Sleeping Techniques",
            professor = "Prof. Antok Malala",
            days = listOf("MON", "THU"),
            startTime = "10:00",
            endTime = "12:30"
        ),

        SubjectInfo(
            code = "EAT202",
            name = "Principles of Eating and Snacking",
            professor = "Dr. Gutom Palagi",
            days = listOf("TUE", "FRI"),
            startTime = "07:30",
            endTime = "08:30"
        ),

        SubjectInfo(
            code = "CRAM303",
            name = "Mastery of Last-Minute Requirements",
            professor = "Prof. Cramming Master",
            days = listOf("MON", "THU"),
            startTime = "18:00",
            endTime = "19:00"
        ),

        SubjectInfo(
            code = "CHISMIS1",
            name = "Applied Chismis and Information Sharing",
            professor = "Ma'am Marites Everywhere",
            days = listOf("TUE", "FRI"),
            startTime = "13:00",
            endTime = "14:30"
        ),

        SubjectInfo(
            code = "LAZY404",
            name = "Introduction to Productive Laziness",
            professor = "Sir Tamad Supremo",
            days = listOf("TUE", "FRI"),
            startTime = "10:30",
            endTime = "11:30"
        ),

        SubjectInfo(
            code = "AWOL505",
            name = "Attendance Avoidance and Survival",
            professor = "Prof. Late Na Naman",
            days = listOf("TUE", "FRI"),
            startTime = "14:30",
            endTime = "16:00"
        )
    )
}
