package com.example.citcsattendancetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {

        @Volatile
        private var INSTANCE: AttendanceDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(
                db: SupportSQLiteDatabase
            ) {

                db.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN subjectCode TEXT NOT NULL DEFAULT 'LEGACY'"
                )

                db.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN subjectName TEXT NOT NULL DEFAULT 'Legacy Attendance'"
                )

                db.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''"
                )

                db.execSQL(
                    """
                    UPDATE attendance_records
                    SET sessionId = 'LEGACY-' || id
                    """.trimIndent()
                )

                db.execSQL(
                    "DROP INDEX IF EXISTS index_attendance_records_studentNumber_dateKey"
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_attendance_records_studentNumber_sessionId
                    ON attendance_records(studentNumber, sessionId)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(
                db: SupportSQLiteDatabase
            ) {

                db.execSQL(
                    """
                    ALTER TABLE attendance_records
                    ADD COLUMN proofUri TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(
            context: Context
        ): AttendanceDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        AttendanceDatabase::class.java,
                        "citcs_attendance_database"
                    )
                        .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3
                        )
                        .build()

                INSTANCE = instance

                instance
            }
        }
    }
}