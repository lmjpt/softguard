package kr.woorijip.softguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EventLogEntity::class], version = 1, exportSchema = false)
abstract class SoftGuardDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile private var instance: SoftGuardDatabase? = null

        fun get(context: Context): SoftGuardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SoftGuardDatabase::class.java, "softguard.db")
                // 스키마가 바뀌면 여기에 .addMigrations(...) 를 추가한다. fallbackToDestructiveMigration 은 쓰지 않는다.
                .build()
                .also { instance = it }
        }
    }
}
