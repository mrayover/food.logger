package com.foodlogger

import androidx.room.Room
import com.foodlogger.data.FoodDatabase
import com.foodlogger.data.Repository
import com.foodlogger.domain.*
import com.foodlogger.providers.CatalogQuery
import com.foodlogger.providers.OfflineCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OfflineCatalogTest {
 @Test fun representativeSearchesUsePackagedDatabase(): Unit = runBlocking {
  OfflineCatalog(RuntimeEnvironment.getApplication()).use { catalog ->
   for (query in listOf("chicken breast", "banana", "egg", "rice", "milk", "ground beef")) {
    val found = catalog.search(query)
    assertTrue(query, found.isNotEmpty())
    assertTrue(found.size <= 50)
    if(query == "chicken breast") assertTrue(found.any { it.name.contains("Chicken,") && it.name.contains("breast") })
    if(query == "ground beef") assertTrue(found.any { it.name.startsWith("Beef, ground") })
    assertEquals(found.map { it.id }, catalog.search(query.uppercase()).map { it.id })
    found.forEach {
     assertTrue(it.nutrition.valid())
     assertEquals(100.0, it.grams!!, 0.0)
     assertEquals("g", it.servingUnit)
     assertTrue(it.externalId.toInt() > 0)
     assertTrue(it.notes.contains("NDB"))
    }
   }
   assertTrue(catalog.search("").isEmpty())
   assertTrue(catalog.search("\" * : () -").isEmpty())
   assertTrue(catalog.search("zzzznonexistentfood").isEmpty())
  }
 }

 @Test fun selectedFoodLogsQuantityAndKeepsHistoricalSnapshot(): Unit = runBlocking {
  val context = RuntimeEnvironment.getApplication()
  val db = Room.inMemoryDatabaseBuilder(context, FoodDatabase::class.java).allowMainThreadQueries().build()
  val repository = Repository(db)
  try {
   OfflineCatalog(context).use { catalog ->
    val banana = catalog.search("banana").first { it.name == "Bananas, raw" }
    assertEquals(89.0, banana.nutrition.calories, 0.00001)
    assertEquals(1.09, banana.nutrition.protein, 0.00001)
    val quantity = Engine.multiplier(150.0, "g", banana.grams)
    assertEquals(1.5, quantity, 0.0)
    assertEquals(quantity, Engine.multiplier(150.0 / 28.349523125, "oz", banana.grams), 0.00001)
    repository.log(banana, quantity, "2026-10-18", "Breakfast", AppData())
    val data = repository.data.first()
    assertEquals(1, data.foods.size)
    assertEquals(banana.id, data.foods.single().id)
    assertEquals(1.5, data.foods.single().lastQuantity, 0.0)
    val entry = data.entries.single()
    assertEquals(banana.externalId, entry.externalId)
    val total = data.entries.fold(Nutrition()) { n, e -> n + e.nutrition * e.quantity }
    assertEquals(133.5, total.calories, 0.00001)
    assertEquals(1.635, total.protein, 0.00001)
    assertEquals(banana.nutrition * quantity, total)
    repository.saveFood(data.foods.single().copy(name="My edited banana", nutrition=Nutrition(calories=1.0)), emptyList(), data)
    assertEquals(entry, repository.data.first().entries.single())
    assertEquals(banana.nutrition, catalog.search("banana").first { it.id == banana.id }.nutrition)
    repository.deleteFood(repository.data.first().foods.single(), repository.data.first())
    assertEquals(entry, repository.data.first().entries.single())
   }
   // Reopen the read-only installed catalog; personal deletion cannot remove it.
   OfflineCatalog(context).use { assertTrue(it.search("banana").any { f -> f.name == "Bananas, raw" }) }
  } finally { db.close() }
 }

 @Suppress("DEPRECATION")
 @Test fun searchWorksWithNetworkConnectionsDenied(): Unit = runBlocking {
  val context = RuntimeEnvironment.getApplication()
  val previous = System.getSecurityManager()
  System.setSecurityManager(object : SecurityManager() {
   override fun checkPermission(permission: java.security.Permission) {}
   override fun checkConnect(host: String, port: Int) { throw SecurityException("Network disabled for offline catalog test") }
  })
  try {
   OfflineCatalog(context).use { catalog ->
    for(query in listOf("chicken breast", "banana", "egg", "rice", "milk", "ground beef"))
     assertTrue(query, catalog.search(query).isNotEmpty())
   }
  } finally { System.setSecurityManager(previous) }
 }

 @Test fun unreportedSecondaryNutrientIsDisclosed(): Unit = runBlocking {
  OfflineCatalog(RuntimeEnvironment.getApplication()).use { catalog ->
   val bear = catalog.search("bear raw").first { it.name == "Game meat, bear, raw" }
   assertEquals(0.0, bear.nutrition.sodium, 0.0)
   assertTrue(bear.notes.contains("Not reported:"))
   assertTrue(bear.notes.substringAfter("Not reported:").substringBefore(".").contains("sodium"))
  }
 }

 @Test fun sourcePortionsSurvive(): Unit = runBlocking {
  OfflineCatalog(RuntimeEnvironment.getApplication()).use { catalog ->
   val banana = catalog.search("banana").first { it.name == "Bananas, raw" }
   assertTrue(banana.notes.contains("USDA portions"))
   assertTrue(banana.notes.contains("118")) // USDA medium banana weight, not inferred density.
  }
 }

 @Test fun queryCannotSupplySqlOrFtsOperators() {
  assertTrue(CatalogQuery.matchesPersonal("Chicken, broilers or fryers, breast, raw", "chicken breast"))
  assertFalse(CatalogQuery.matchesPersonal("Milk", "chicken breast"))
  assertEquals("\"chicken*\" \"breast*\"", CatalogQuery.match("CHICKEN, breast"))
  assertNull(CatalogQuery.match("* : \""))
  assertEquals("\"milk*\" \"or*\" \"egg*\"", CatalogQuery.match("milk OR egg"))
 }
}
