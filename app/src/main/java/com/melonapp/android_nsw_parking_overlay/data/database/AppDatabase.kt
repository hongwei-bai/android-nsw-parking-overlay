package com.melonapp.android_nsw_parking_overlay.data.database

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.melonapp.android_nsw_parking_overlay.util.NswCalendarUtils
import java.time.Instant
import java.time.ZoneId

@Database(
    entities = [CarParkHistoryRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun carParkHistoryDao(): CarParkHistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE car_park_history ADD COLUMN isPublicHolidayNsw INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE car_park_history ADD COLUMN isFirstWeekOfMonth INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE car_park_history ADD COLUMN isLastWeekOfMonth INTEGER NOT NULL DEFAULT 0"
                )

                db.query("SELECT id, queriedAtEpochMillis FROM car_park_history").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val timeIndex = cursor.getColumnIndexOrThrow("queriedAtEpochMillis")

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val queriedAt = cursor.getLong(timeIndex)
                        val date = Instant.ofEpochMilli(queriedAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()

                        val isHoliday = if (NswCalendarUtils.isNswPublicHoliday(date)) 1 else 0
                        val isFirstWeek = if (NswCalendarUtils.isFirstWeekOfMonth(date)) 1 else 0
                        val isLastWeek = if (NswCalendarUtils.isLastWeekOfMonth(date)) 1 else 0

                        db.execSQL(
                            """
                            UPDATE car_park_history
                            SET isPublicHolidayNsw = ?, isFirstWeekOfMonth = ?, isLastWeekOfMonth = ?
                            WHERE id = ?
                            """.trimIndent(),
                            arrayOf(isHoliday, isFirstWeek, isLastWeek, id)
                        )
                    }
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parking_history.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
