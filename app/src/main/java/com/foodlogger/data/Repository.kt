package com.foodlogger.data

import androidx.room.withTransaction
import com.foodlogger.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.LocalDate

class Repository(val db: FoodDatabase) {
 val dao=db.store()
 val data: Flow<AppData> = combine(
  combine(dao.foods(),dao.components(),dao.entries(),dao.days()) { f,c,e,d -> AppData(foods=f,components=c,entries=e,days=d) },
  combine(dao.weights(),dao.targets(),dao.checkins(),dao.settings()) { w,t,c,s -> AppData(weights=w,targets=t,checkins=c,settings=s?:Settings()) }
 ) { a,b -> a.copy(weights=b.weights,targets=b.targets,checkins=b.checkins,settings=b.settings) }
 suspend fun log(food: Food,quantity: Double,date: String,meal: String,data: AppData) = db.withTransaction {
  require(quantity.isFinite() && quantity>0)
  suspend fun insert(f: Food,q: Double,seen: Set<String>) {
   require(f.id !in seen) { "Meal cycle" }
   if(f.kind=="SAVED_MEAL") {
    data.components.filter { it.parentId==f.id }.forEach { p -> insert(data.foods.first { it.id==p.foodId },q*p.quantity,seen+f.id) }
   } else {
    val n=Engine.foodNutrition(f,data.foods+f,data.components)
    dao.entry(LogEntry(date=date,meal=meal,foodId=f.id,name=f.name,serving=f.serving,grams=f.grams,quantity=q,nutrition=n,source=f.source,externalId=f.externalId))
   }
   dao.food(f.copy(lastQuantity=q,lastUsedAt=now(),useCount=f.useCount+1))
  }
  insert(food,quantity,emptySet())
 }
 suspend fun saveFood(food: Food,parts: List<Component>,data: AppData) = db.withTransaction {
  require(food.name.isNotBlank() && food.nutrition.valid() && food.yieldServings>0)
  val all=data.foods.filter { it.id!=food.id }+food
  val allParts=data.components.filter { it.parentId!=food.id }+parts
  if(food.kind in listOf("RECIPE","SAVED_MEAL")) { require(parts.isNotEmpty()); Engine.foodNutrition(food,all,allParts) }
  dao.food(food.copy(updatedAt=now())); dao.deleteParts(food.id); parts.forEach { dao.component(it) }
 }
 suspend fun deleteFood(food: Food,data: AppData) = db.withTransaction {
  require(data.components.none { it.foodId==food.id }) { "Remove this ingredient from recipes or saved meals first." }
  dao.deleteParts(food.id);dao.deleteFood(food.id)
 }
 fun backup(d: AppData)=Backup(foods=d.foods,components=d.components,entries=d.entries,days=d.days,weights=d.weights,targets=d.targets,checkins=d.checkins,settings=d.settings)
 fun validate(b: Backup) {
  require(b.app=="Food.Logger" && b.schemaVersion==1) { "Unsupported backup format" }
  fun date(s: String) { LocalDate.parse(s) }
  listOf(b.foods.map { it.id },b.components.map { it.id },b.entries.map { it.id },b.weights.map { it.id },b.targets.map { it.id },b.checkins.map { it.id },b.days.map { it.date }).forEach { ids -> require(ids.all { it.isNotBlank() } && ids.size==ids.toSet().size) { "Duplicate or empty identifiers" } }
  b.foods.forEach { require(it.name.isNotBlank() && it.nutrition.valid() && it.lastQuantity.isFinite() && it.lastQuantity>0 && it.yieldServings.isFinite() && it.yieldServings>0 && (it.grams==null || it.grams.isFinite() && it.grams>0)) }
  b.components.forEach { p-> require(p.quantity.isFinite() && p.quantity>0 && b.foods.any { it.id==p.parentId } && b.foods.any { it.id==p.foodId }) }
  b.foods.filter { it.kind in listOf("RECIPE","SAVED_MEAL") }.forEach { Engine.foodNutrition(it,b.foods,b.components) }
  b.entries.forEach { date(it.date);require(it.quantity.isFinite() && it.quantity>0 && it.nutrition.valid() && it.meal in meals) }
  b.weights.forEach { date(it.date);require(it.pounds.isFinite() && it.pounds in 20.0..1500.0) }
  b.targets.forEach { date(it.effectiveDate);validateTarget(it) }
  b.days.forEach { date(it.date) }
  b.checkins.forEach { date(it.date);require(it.weight.isFinite() && it.weight in 20.0..1500.0 && it.hunger in 1..5 && it.energy in 1..5 && it.training in 1..5);json.decodeFromString<Analysis>(it.analysisJson);json.decodeFromString<Analysis>(it.originalAnalysisJson);validateTarget(json.decodeFromString(it.chosenTargetJson)) }
  require(b.settings.checkDay in 1..7 && b.settings.calorieTolerance in 0.0..1.0 && b.settings.proteinTolerance in 0.0..1.0 && b.settings.alcoholOrangeExtra>=0 && b.settings.alcoholRedExtra>b.settings.alcoholOrangeExtra)
 }
 suspend fun restore(b: Backup) { validate(b); db.withTransaction {
  clear();b.foods.forEach { dao.food(it) };b.components.forEach { dao.component(it) };b.entries.forEach { dao.entry(it) };b.days.forEach { dao.day(it) };b.weights.forEach { dao.weight(it) };b.targets.forEach { dao.target(it) };b.checkins.forEach { dao.checkin(it) };dao.settings(b.settings)
 } }
 suspend fun clear() { dao.clearParts();dao.clearEntries();dao.clearDays();dao.clearWeights();dao.clearTargets();dao.clearCheckins();dao.clearFoods();dao.clearSettings() }
 fun summary(d: AppData): String = buildJsonObject {
  put("schemaVersion",1);put("app","Food.Logger");put("exportedAt",now())
  put("days",buildJsonArray {
   (d.entries.map { it.date }+d.days.map { it.date }+d.weights.map { it.date }).distinct().sorted().forEach { date -> add(buildJsonObject {
    put("date",date);put("complete",d.days.any { it.date==date && it.complete })
    put("nutrition",json.encodeToJsonElement(d.entries.filter { it.date==date }.fold(Nutrition()) { n,e->n+e.nutrition*e.quantity }))
    put("meals",buildJsonObject { meals.forEach { m->put(m,json.encodeToJsonElement(d.entries.filter { it.date==date && it.meal==m }.fold(Nutrition()) { n,e->n+e.nutrition*e.quantity })) } })
    val target=Engine.targetOn(d.targets,date);put("targets",json.encodeToJsonElement(target));put("goalWeightLb",target.goalPounds)
    Engine.trend(d.weights).lastOrNull { it.date<=date }?.let { put("weightLb",it.actual);put("smoothedWeightLb",it.smooth) }
    put("adherence",json.encodeToJsonElement(Engine.statuses(d,LocalDate.parse(date).plusDays(1).toString(),emptySet()).last()))
   }) }
  });put("checkIns",json.encodeToJsonElement(d.checkins))
 }.toString()
 suspend fun copyDay(from: String,to: String,d: AppData,meal: String?=null) = db.withTransaction {
  d.entries.filter { it.date==from && (meal==null || it.meal==meal) }.forEach { dao.entry(it.copy(id=uid(),date=to,createdAt=now(),demo=false)) }
 }
 suspend fun finishCheckIn(check: CheckIn,chosen: Target,apply: Boolean) = db.withTransaction {
  dao.checkin(check);dao.weight(Weight(id="checkin-${check.id}",date=check.date,pounds=check.weight,demo=check.demo))
  if(apply) { validateTarget(chosen);dao.target(chosen) }
 }
}
fun validateTarget(t: Target) { require(listOf(t.calories,t.protein,t.carbs,t.fat,t.goalPounds,t.weeklyRate,t.alcoholAllowance).all { it.isFinite() });require(t.calories in 500.0..10000.0 && t.protein>=0 && t.carbs>=0 && t.fat>=0 && t.goalPounds in 20.0..1500.0 && t.weeklyRate in -5.0..5.0 && t.alcoholAllowance in 0.0..30.0) { "Check target values (calories 500–10000, weight rate −5 to +5 lb/week)." } }
