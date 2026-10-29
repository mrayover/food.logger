package com.foodlogger

import androidx.room.Room
import com.foodlogger.data.*
import com.foodlogger.domain.*
import com.foodlogger.providers.OpenFoodFacts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ServingPersistenceTest {
 private fun product()=OpenFoodFacts().normalize(JSONObject("""{"code":"012345678905","product_name":"Peanut Butter","serving_size":"2 tbsp (32 g)","serving_quantity":32,"nutriments":{"energy-kcal_100g":600,"proteins_100g":25,"carbohydrates_100g":20,"fat_100g":50,"fiber_100g":6,"sugars_100g":10,"sodium_100g":0.4}}"""))!!
 @Test fun manufacturerSelectQuantityLogTotalsAndSnapshots(): Unit = runBlocking {
  val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),FoodDatabase::class.java).allowMainThreadQueries().build()
  try {
   val repository=Repository(db);val f=product()
   val q=Engine.multiplier(48.0,"g",f.grams)
   repository.log(f,q,"2026-10-28","Breakfast",AppData())
   var data=repository.data.first()
   assertEquals(1.5,data.foods.single().lastQuantity,0.0)
   val entry=data.entries.single()
   assertEquals(f.nutrition*1.5,entry.nutrition*entry.quantity)
   assertEquals(288.0,(entry.nutrition*entry.quantity).calories,1e-8)
   val saved=FoodIdentity.savedBarcode(f.barcode,data.foods)!!
   repository.log(saved,2.0,"2026-10-28","Lunch",data)
   data=repository.data.first()
   assertEquals(1,data.foods.size);assertEquals(2,data.entries.size)
   assertEquals(f.nutrition*3.5,data.entries.fold(Nutrition()) {n,e->n+e.nutrition*e.quantity})
   repository.saveFood(saved.copy(serving="my scoop",grams=10.0,nutrition=Nutrition(10.0)),emptyList(),data)
   assertEquals(entry,repository.data.first().entries.first())
   val backup=repository.backup(repository.data.first());repository.validate(backup)
   assertEquals(entry,backup.entries.first())
  } finally {db.close()}
 }
 @Test fun olderSavedDefinitionIsReusedWithoutReinterpretation(): Unit = runBlocking {
  val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),FoodDatabase::class.java).allowMainThreadQueries().build()
  try {
   val repository=Repository(db)
   val legacy=product().copy(id="old-personal",serving="100 g",servingUnit="g",servingQuantity=100.0,grams=100.0,nutrition=Nutrition(600.0,25.0,20.0,50.0),lastQuantity=1.25)
   repository.dao.food(legacy)
   val saved=FoodIdentity.saved(product(),repository.data.first().foods)!!
   assertEquals(legacy,saved)
   repository.log(saved,saved.lastQuantity,"2026-10-28","Dinner",repository.data.first())
   val data=repository.data.first()
   assertEquals(1,data.foods.size)
   assertEquals(750.0,(data.entries.single().nutrition*data.entries.single().quantity).calories,1e-8)
   assertEquals("100 g",data.foods.single().serving)
  } finally {db.close()}
 }
 @Test fun savedServingSurvivesDatabaseReopenAndOfflineLookup(): Unit = runBlocking {
  val context=RuntimeEnvironment.getApplication();val name="serving-persistence-test.db"
  context.deleteDatabase(name)
  var db=Room.databaseBuilder(context,FoodDatabase::class.java,name).allowMainThreadQueries().build()
  try {
   Repository(db).log(product(),1.5,"2026-10-28","Breakfast",AppData())
   db.close()
   db=Room.databaseBuilder(context,FoodDatabase::class.java,name).allowMainThreadQueries().build()
   val data=Repository(db).data.first()
   val f=FoodIdentity.savedBarcode("0012345678905",data.foods)!!
   assertEquals(32.0,f.grams!!,0.0);assertEquals(1.5,f.lastQuantity,0.0)
   assertEquals(product().nutrition,f.nutrition)
  } finally {db.close();context.deleteDatabase(name)}
 }
}
