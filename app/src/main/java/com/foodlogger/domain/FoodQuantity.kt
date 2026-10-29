package com.foodlogger.domain

/** A quantity is always a multiple of the food's saved nutrition base. */
object FoodQuantity {
 fun units(food: Food) = if(food.grams?.let { it.isFinite() && it>0 }==true) listOf("servings","g","oz") else listOf("servings")
 fun amount(servings: Double, unit: String, grams: Double?): Double = when(unit) {
  "g" -> servings * requireNotNull(grams)
  "oz" -> servings * requireNotNull(grams) / 28.349523125
  else -> servings
 }
 fun initialUnit(food: Food, preferred: String, remembered: String?): String {
  val choice=remembered ?: if(food.servingUnit=="serving") "servings" else preferred
  return choice.takeIf { it in units(food) } ?: "servings"
 }
}

object FoodIdentity {
 fun barcode(code: String): String? = code.trim().takeIf { it.length in listOf(8,12,13,14) && it.all { c -> c in '0'..'9' } }?.trimStart('0')?.takeIf { it.isNotEmpty() }
 fun saved(food: Food, foods: List<Food>): Food? = foods.find { it.id==food.id }
  ?: savedBarcode(food.barcode,foods)
 fun savedBarcode(code: String, foods: List<Food>): Food? {
  val key=barcode(code) ?: return null
  return foods.filter { it.kind=="FOOD" && barcode(it.barcode)==key }.maxByOrNull { it.lastUsedAt }
 }
 fun unique(foods: List<Food>): List<Food> = foods.distinctBy { barcode(it.barcode)?.let { b -> "barcode:$b" } ?: "id:${it.id}" }
}
