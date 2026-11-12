package com.example.citcsattendancetracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        return try {

            val preferences =
                applicationContext.getSharedPreferences(
                    "citcs_attendance_preferences",
                    Context.MODE_PRIVATE
                )

            val studentNumber =
                preferences.getString(
                    "student_number",
                    null
                ) ?: return Result.success()

            val database =
                AttendanceDatabase.getDatabase(applicationContext)

            val dao =
                database.attendanceDao()

            val today =
                Calendar.getInstance()

            /*
             * Check today and the previous 7 days.
             * This allows the app to create missing Absent records
             * when the student opens/logs in again after several days.
             */
            for (daysAgo in 0..7) {

                val checkDate =
                    today.clone() as Calendar

                checkDate.add(
                    Calendar.DAY_OF_YEAR,
                    -daysAgo
                )

                val day =
                    when (
                        checkDate.get(Calendar.DAY_OF_WEEK)
                    ) {
                        Calendar.MONDAY -> "MON"
                        Calendar.TUESDAY -> "TUE"
                        Calendar.WEDNESDAY -> "WED"
                        Calendar.THURSDAY -> "THU"
                        Calendar.FRIDAY -> "FRI"
                        Calendar.SATURDAY -> "SAT"
                        Calendar.SUNDAY -> "SUN"
                        else -> ""
                    }

                val dateKey =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).format(checkDate.time)

                val dateText =
                    SimpleDateFormat(
                        "MMM dd, yyyy",
                        Locale.getDefault()
                    ).format(checkDate.time)

                for (subject in SubjectData.subjects) {

                    if (day !in subject.days) {
                        continue
                    }

                    val parts =
                        subject.startTime.split(":")

                    val startMinutes =
                        parts[0].toInt() * 60 +
                                parts[1].toInt()

                    val cutoffMinutes =
                        startMinutes + 30

                    /*
                     * For previous days:
                     * the class has already ended, so if there is
                     * no attendance record, create Absent.
                     *
                     * For today:
                     * only create Absent after the 30-minute cutoff.
                     */
                    val shouldMarkAbsent =
                        if (daysAgo > 0) {

                            true

                        } else {

                            val currentMinutes =
                                today.get(
                                    Calendar.HOUR_OF_DAY
                                ) * 60 +
                                        today.get(
                                            Calendar.MINUTE
                                        )

                            currentMinutes > cutoffMinutes
                        }

                    if (!shouldMarkAbsent) {
                        continue
                    }

                    val sessionId =
                        "${subject.code}-$dateKey"

                    /*
                     * Because AttendanceEntity has a unique index
                     * on studentNumber + sessionId, an existing
                     * Present/Late/Absent record will not be duplicated.
                     */
                    dao.insertAttendance(
                        AttendanceEntity(
                            studentNumber = studentNumber,
                            dateKey = dateKey,
                            date = dateText,
                            time = subject.startTime,
                            status = "Absent",
                            subjectCode = subject.code,
                            subjectName = subject.name,
                            sessionId = sessionId,
                            proofUri = ""
                        )
                    )
                }
            }

            Result.success()

        } catch (e: Exception) {

            Result.retry()
        }
    }
}