package com.foodlogger

import androidx.room.Room
import com.foodlogger.data.*
import com.foodlogger.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ReleaseBackupCompatibilityTest {
 @Test fun completeJsonBackupMovesBetweenIndependentStoresWithoutChangingSnapshots(): Unit = runBlocking {
  val context=RuntimeEnvironment.getApplication()
  val sourceDb=Room.inMemoryDatabaseBuilder(context,FoodDatabase::class.java).allowMainThreadQueries().build()
  val releaseDb=Room.inMemoryDatabaseBuilder(context,FoodDatabase::class.java).allowMainThreadQueries().build()
  try {
   val source=Repository(sourceDb);val destination=Repository(releaseDb)
   val food=Food(name="Saved food",serving="2 tbsp (32 g)",grams=32.0,nutrition=Nutrition(192.0,8.0,6.4,16.0),source="Open Food Facts",externalId="fixture")
   source.log(food,1.5,"2026-10-28","Lunch",AppData())
   source.dao.weight(Weight(date="2026-10-28",pounds=180.0))
   source.dao.day(DayLog("2026-10-28",true))
   source.dao.settings(Settings(foodUnit="oz"))
   val before=source.data.first()
   val exported=json.encodeToString(source.backup(before))
   val decoded=json.decodeFromString<Backup>(exported)
   assertTrue(destination.data.first().entries.isEmpty())
   destination.restore(decoded)
   assertEquals(before,destination.data.first())
   assertEquals(288.0,(destination.data.first().entries.single().nutrition*1.5).calories,0.0)
   assertEquals(before,source.data.first())
  } finally {sourceDb.close();releaseDb.close()}
 }
}
