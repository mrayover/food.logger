package com.foodlogger.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.foodlogger.domain.*
import com.foodlogger.domain.Target
import kotlinx.coroutines.flow.Flow

@Dao interface Store {
 @Query("SELECT * FROM foods ORDER BY name COLLATE NOCASE") fun foods(): Flow<List<Food>>
 @Query("SELECT * FROM components") fun components(): Flow<List<Component>>
 @Query("SELECT * FROM entries ORDER BY createdAt") fun entries(): Flow<List<LogEntry>>
 @Query("SELECT * FROM days") fun days(): Flow<List<DayLog>>
 @Query("SELECT * FROM weights ORDER BY date") fun weights(): Flow<List<Weight>>
 @Query("SELECT * FROM targets ORDER BY effectiveDate,createdAt") fun targets(): Flow<List<Target>>
 @Query("SELECT * FROM checkins ORDER BY date DESC") fun checkins(): Flow<List<CheckIn>>
 @Query("SELECT * FROM settings WHERE id=1") fun settings(): Flow<Settings?>
 @Upsert suspend fun food(value: Food)
 @Upsert suspend fun component(value: Component)
 @Upsert suspend fun entry(value: LogEntry)
 @Upsert suspend fun day(value: DayLog)
 @Upsert suspend fun weight(value: Weight)
 @Upsert suspend fun target(value: Target)
 @Upsert suspend fun checkin(value: CheckIn)
 @Upsert suspend fun settings(value: Settings)
 @Query("DELETE FROM foods WHERE id=:id") suspend fun deleteFood(id: String)
 @Query("DELETE FROM components WHERE parentId=:id") suspend fun deleteParts(id: String)
 @Query("DELETE FROM entries WHERE id=:id") suspend fun deleteEntry(id: String)
 @Query("DELETE FROM weights WHERE id=:id") suspend fun deleteWeight(id: String)
 @Query("DELETE FROM foods") suspend fun clearFoods()
 @Query("DELETE FROM components") suspend fun clearParts()
 @Query("DELETE FROM entries") suspend fun clearEntries()
 @Query("DELETE FROM days") suspend fun clearDays()
 @Query("DELETE FROM weights") suspend fun clearWeights()
 @Query("DELETE FROM targets") suspend fun clearTargets()
 @Query("DELETE FROM checkins") suspend fun clearCheckins()
 @Query("DELETE FROM settings") suspend fun clearSettings()
}
@Database(entities=[Food::class,Component::class,LogEntry::class,DayLog::class,Weight::class,Target::class,CheckIn::class,Settings::class],version=2,exportSchema=true)
abstract class FoodDatabase: RoomDatabase() {
 abstract fun store(): Store
 companion object {
  val MIGRATION_1_2=object: Migration(1,2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE foods ADD COLUMN lastQuantity REAL NOT NULL DEFAULT 1.0") } }
  fun open(context: Context)=Room.databaseBuilder(context,FoodDatabase::class.java,"food-logger.db").addMigrations(MIGRATION_1_2).build()
 }
}
