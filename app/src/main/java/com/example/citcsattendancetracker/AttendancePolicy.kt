package com.example.citcsattendancetracker

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AttendancePolicy {

    private val formatter =
        DateTimeFormatter.ofPattern("HH:mm")

    fun getStatus(
        checkInTime: LocalTime,
        subject: SubjectInfo
    ): String {

        val start =
            LocalTime.parse(
                subject.startTime,
                formatter
            )

        val presentCutoff =
            start.plusMinutes(15)

        val lateCutoff =
            start.plusMinutes(30)

        return when {

            checkInTime.isBefore(start) ->
                "Not Yet Open"

            !checkInTime.isAfter(presentCutoff) ->
                "Present"

            !checkInTime.isAfter(lateCutoff) ->
                "Late"

            else ->
                "Absent"
        }
    }

    fun getAbsentStatus(
        currentTime: LocalTime,
        subject: SubjectInfo
    ): Boolean {

        val start =
            LocalTime.parse(
                subject.startTime,
                formatter
            )

        val absentStart =
            start.plusMinutes(30)

        return currentTime.isAfter(absentStart)
    }

    fun requiresProof(
        currentTime: LocalTime,
        subject: SubjectInfo
    ): Boolean {

        val start =
            LocalTime.parse(
                subject.startTime,
                formatter
            )

        val absentStart =
            start.plusMinutes(30)

        return !currentTime.isBefore(start) &&
                !currentTime.isAfter(absentStart)
    }

    fun isAttendanceOpen(
        currentTime: LocalTime,
        subject: SubjectInfo
    ): Boolean {

        val start =
            LocalTime.parse(
                subject.startTime,
                formatter
            )

        return !currentTime.isBefore(start)
    }
}