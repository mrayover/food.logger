package com.foodlogger

import com.foodlogger.domain.*
import com.foodlogger.providers.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServingTest {
 private fun product(serving: String="2 tbsp (32 g)", quantity: Any=32): JSONObject = JSONObject()
  .put("code","012345678905").put("product_name","Peanut Butter").put("serving_size",serving).put("serving_quantity",quantity)
  .put("nutriments",JSONObject("""{"energy-kcal_100g":600,"proteins_100g":25,"carbohydrates_100g":20,"fat_100g":50,"fiber_100g":6,"sugars_100g":10,"sodium_100g":0.4}"""))
 @Test fun manufacturerServingScalesEveryNutrient() {
  val f=OpenFoodFacts().normalize(product())!!
  assertEquals("2 tbsp (32 g)",f.serving);assertEquals(32.0,f.grams!!,0.0)
  assertEquals(Nutrition(600.0,25.0,20.0,50.0,6.0,10.0,400.0)*.32,f.nutrition)
  assertEquals("serving",f.servingUnit);assertEquals(1.0,f.lastQuantity,0.0)
  assertTrue(f.notes.contains("2 tbsp (32 g)"));assertEquals("Open Food Facts",f.source)
 }
 @Test fun fractionalMetricServingAndDecimalComma() {
  for(label in listOf("1/2 g","½ g","0,5 grams")) {
   val f=OpenFoodFacts().normalize(product(label,.5))!!
   assertEquals(.5,f.grams!!,0.0);assertEquals(3.0,f.nutrition.calories,1e-8)
  }
 }
 @Test fun unreliableServingsKeepReference() {
  for(label in listOf("","one spoon","-32 g","0 g","32 g / 64 g","32 g (30 ml)","NaN g","2 × 32 g")) {
   val f=OpenFoodFacts().normalize(product(label))!!
   assertEquals(600.0,f.nutrition.calories,0.0);assertEquals(100.0,f.servingQuantity,0.0)
  }
  assertEquals("100 g",OpenFoodFacts().normalize(product("32 g",64))!!.serving)
  assertEquals("100 g",OpenFoodFacts().normalize(product("32 g","bad"))!!.serving)
 }
 @Test fun missingDeclaredQuantityStillUsesExplicitMass() {
  val p=product();p.remove("serving_quantity")
  assertEquals(32.0,OpenFoodFacts().normalize(p)!!.grams!!,0.0)
 }
 @Test fun declaredQuantityRequiresAnExplicitUnit() {
  val p=product("2 tbsp",32)
  assertEquals(100.0,OpenFoodFacts().normalize(p)!!.grams!!,0.0)
  p.put("serving_quantity_unit","g")
  assertEquals(32.0,OpenFoodFacts().normalize(p)!!.grams!!,0.0)
  p.put("serving_size","").put("serving_quantity_unit","ml")
  val f=OpenFoodFacts().normalize(p)!!
  assertNull(f.grams);assertEquals("32.0 ml",f.serving)
 }
 @Test fun liquidsNeverAcquireInventedMass() {
  val f=OpenFoodFacts().normalize(product("1 cup (240 ml)",240))!!
  assertNull(f.grams);assertEquals(1440.0,f.nutrition.calories,1e-8)
  assertEquals(listOf("servings"),FoodQuantity.units(f))
  assertEquals("100 ml",OpenFoodFacts().normalize(product("240 ml",100))!!.serving)
  assertNull(OpenFoodFacts().normalize(product("240ml",100))!!.grams)
 }
 @Test fun incompleteAndInvalidRequiredNutritionIsRejected() {
  for(v in listOf<Any>("garbage",-1,JSONObject.NULL,"NaN","Infinity")) {
   val p=product();p.getJSONObject("nutriments").put("proteins_100g",v)
   assertNull(OpenFoodFacts().normalize(p))
  }
  val p=product();p.remove("code");assertNull(OpenFoodFacts().normalize(p))
 }
 @Test fun unitsPreserveConsumedAmountAndFractionalMacros() {
  val f=OpenFoodFacts().normalize(product())!!
  var q=1.5
  for(unit in listOf("g","oz","servings","oz","g")) {
   val amount=FoodQuantity.amount(q,unit,f.grams)
   q=Engine.multiplier(amount,unit,f.grams)
   assertEquals(1.5,q,1e-10)
  }
  assertEquals(48.0,FoodQuantity.amount(q,"g",f.grams),1e-8)
  assertEquals(288.0,(f.nutrition*q).calories,1e-8)
 }
 @Test fun invalidInputDoesNotConvertToOneServing() {
  assertTrue(runCatching {Engine.multiplier(0.0,"g",32.0)}.isFailure)
  assertTrue(runCatching {Engine.multiplier(Double.NaN,"oz",32.0)}.isFailure)
 }
 @Test fun firstServingAndRememberedUnitAndLegacyFallback() {
  val f=OpenFoodFacts().normalize(product())!!
  assertEquals("servings",FoodQuantity.initialUnit(f,"g",null))
  assertEquals("oz",FoodQuantity.initialUnit(f,"g","oz"))
  assertEquals("g",FoodQuantity.initialUnit(Food(grams=100.0,servingUnit="g"),"g",null))
  assertEquals("servings",FoodQuantity.initialUnit(Food(),"g","oz"))
 }
 @Test fun savedDefinitionWinsAndBarcodeAliasesDeduplicate() {
  val fresh=OpenFoodFacts().normalize(product())!!
  val old=fresh.copy(id="legacy-personal-id",barcode="0012345678905",serving="100 g",grams=100.0,nutrition=Nutrition(600.0),lastQuantity=2.5,source="My label")
  assertEquals(old,FoodIdentity.saved(fresh,listOf(old)))
  assertEquals(old,FoodIdentity.savedBarcode(fresh.barcode,listOf(old)))
  assertEquals(1,FoodIdentity.unique(listOf(old,fresh)).size)
  assertNull(FoodIdentity.savedBarcode("",listOf(old)))
  assertNull(FoodIdentity.barcode("123abc456789"))
  assertNull(FoodIdentity.barcode("123456789"))
 }
 @Test fun repeatedLookupAndProviderDeduplication(): Unit = runBlocking {
  val fresh=OpenFoodFacts().normalize(product())!!
  val provider=object: FoodProvider {
   override val name="fixture"
   override suspend fun search(query: String)=listOf(fresh)
   override suspend fun barcode(code: String)=fresh
  }
  val search=FoodSearch(listOf(provider,provider))
  repeat(3) {assertEquals(listOf(fresh),search.search(fresh.barcode,true).foods)}
 }
 @Test fun onlineUsdaBrandedMassUsesServingButLegacyStays100g() {
  val p=JSONObject("""{"fdcId":123,"description":"Test","dataType":"Branded","servingSize":32,"servingSizeUnit":"g","householdServingFullText":"2 tbsp","foodNutrients":[{"nutrientId":1008,"value":600},{"nutrientId":1003,"value":25},{"nutrientId":1005,"value":20},{"nutrientId":1004,"value":50}]}""")
  assertEquals(192.0,Usda("").normalize(p)!!.nutrition.calories,1e-8)
  p.put("servingSizeUnit","ml");assertEquals(100.0,Usda("").normalize(p)!!.grams!!,0.0)
  p.put("dataType","SR Legacy").put("servingSizeUnit","g")
  assertEquals(600.0,Usda("").normalize(p)!!.nutrition.calories,0.0)
 }
}
