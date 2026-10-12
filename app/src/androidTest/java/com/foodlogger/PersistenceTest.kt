package com.foodlogger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.foodlogger.data.*
import com.foodlogger.domain.*
import com.foodlogger.domain.Target
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun snapshotsSurviveEditingDeletionAndRelaunch(): Unit=runBlocking {
  context.deleteDatabase("persistence-test.db")
  var db=Room.databaseBuilder(context,FoodDatabase::class.java,"persistence-test.db").addMigrations(FoodDatabase.MIGRATION_1_2).build()
  var repo=Repository(db)
  val food=Food(name="Yogurt",grams=170.0,nutrition=Nutrition(100.0,17.0))
  repo.dao.food(food);repo.log(food,1.5,"2026-10-09","Breakfast",repo.data.first())
  repo.dao.food(food.copy(nutrition=Nutrition(300.0)));repo.deleteFood(food,repo.data.first());db.close()
  db=Room.databaseBuilder(context,FoodDatabase::class.java,"persistence-test.db").addMigrations(FoodDatabase.MIGRATION_1_2).build();repo=Repository(db)
  val restored=repo.data.first();assertTrue(restored.foods.isEmpty());assertEquals(150.0,(restored.entries.single().nutrition*restored.entries.single().quantity).calories,0.0)
  db.close();context.deleteDatabase("persistence-test.db")
 }
 @Test fun backupRoundTripSavedMealAndDecision()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,FoodDatabase::class.java).build();val repo=Repository(db)
  val food=Food(name="Oats",nutrition=Nutrition(150.0,5.0));val meal=Food(name="Breakfast",kind="SAVED_MEAL")
  repo.dao.food(food);repo.dao.food(meal);repo.dao.component(Component(parentId=meal.id,foodId=food.id,quantity=2.0));repo.dao.target(Target(effectiveDate="2026-01-01"))
  repo.log(meal,1.0,"2026-10-09","Breakfast",repo.data.first())
  val d=repo.data.first();assertEquals(food.id,d.entries.single().foodId);assertEquals(2.0,d.entries.single().quantity,0.0)
  val b=repo.backup(d);repo.restore(b);assertEquals(b.foods,repo.data.first().foods);assertEquals(b.entries,repo.data.first().entries)
  val a=Engine.analyze(d,"2026-10-10",250.0,3,3,3,emptySet())
  val draft=com.foodlogger.ui.CheckDraft("2026-10-10",250.0,3,3,3,"",emptySet(),a)
  assertEquals(0,a.usable);assertEquals(a.oldTarget.calories,a.proposed.calories,0.0)
  db.close()
 }
 @Test fun invalidRestoreIsAtomic()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,FoodDatabase::class.java).build();val repo=Repository(db);repo.dao.food(Food(name="Keep me"))
  val b=repo.backup(repo.data.first()).copy(schemaVersion=999)
  runCatching {repo.restore(b)};assertEquals("Keep me",repo.data.first().foods.single().name);db.close()
 }
}
