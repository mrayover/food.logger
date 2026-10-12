package com.foodlogger

import com.foodlogger.domain.*
import com.foodlogger.domain.Target
import com.foodlogger.providers.*
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EngineTest {
 private val end="2026-10-10"
 private val target=Target(effectiveDate="2026-01-01",calories=2200.0,protein=150.0,weeklyRate=-1.0)
 private fun data(complete: Int=7,adherent: Int=7,rate: Double=-1.0): AppData {
  val dates=(7 downTo 1).map {LocalDate.parse(end).minusDays(it.toLong()).toString()}
  return AppData(targets=listOf(target),days=dates.mapIndexed {i,s->DayLog(s,i<complete)},entries=dates.mapIndexed {i,s->LogEntry(date=s,name="food",serving="day",nutrition=Nutrition(if(i<adherent)2200.0 else 2800.0,150.0))},weights=(0..35).map {i->Weight(date=LocalDate.parse(end).minusDays((35-i).toLong()).toString(),pounds=250+i*rate/7)})
 }
 private fun analyze(d: AppData,h: Int=3,e: Int=3,t: Int=3)=Engine.analyze(d,end,d.weights.last().pounds,h,e,t,emptySet())
 @Test fun gramsAndOuncesUseRealMass() {assertEquals(1.5,Engine.multiplier(255.0,"g",170.0),1e-8);assertEquals(1.0,Engine.multiplier(1.0,"oz",28.349523125),1e-8)}
 @Test(expected=IllegalArgumentException::class) fun missingMassIsNotInvented() {Engine.multiplier(2.0,"g",null)}
 @Test(expected=IllegalArgumentException::class) fun zeroQuantityRejected() {Engine.multiplier(0.0,"servings",null)}
 @Test fun snapshotDoesNotFollowLibraryEdits() {val f=Food(name="Yogurt",nutrition=Nutrition(100.0,17.0));val e=LogEntry(name=f.name,serving=f.serving,foodId=f.id,nutrition=f.nutrition);val edited=f.copy(nutrition=Nutrition(300.0));assertEquals(100.0,e.nutrition.calories,0.0);assertEquals(300.0,edited.nutrition.calories,0.0)}
 @Test fun targetSelectionIsDateEffective() {val future=target.copy(id=uid(),effectiveDate="2026-10-11",calories=2100.0);assertEquals(2200.0,Engine.targetOn(listOf(target,future),end).calories,0.0)}
 @Test fun noRetroactiveDefaultTarget() {assertEquals("unconfigured",Engine.targetOn(listOf(target),"2025-01-01").id)}
 @Test fun incompleteDaysExcludedNotZero() {val a=analyze(data(3,3));assertEquals(3,a.usable);assertEquals(1.0,a.calorieAdherence,0.0);assertTrue(a.reason.contains("Insufficient evidence"));assertEquals(2200.0,a.proposed.calories,0.0)}
 @Test fun poorAdherenceDifferentFromMissingData() {val a=analyze(data(7,3));assertEquals(7,a.usable);assertTrue(a.reason.contains("consistency"));assertEquals(2200.0,a.proposed.calories,0.0)}
 @Test fun moderateAdherenceHolds() {val a=analyze(data(7,5));assertEquals(2200.0,a.proposed.calories,0.0)}
 @Test fun correctRateHolds() {val a=analyze(data());assertEquals(2200.0,a.proposed.calories,0.0)}
 @Test fun largeDiscrepancyCapsCut() {val a=analyze(data(rate=0.5));assertEquals(2000.0,a.proposed.calories,0.0);assertEquals(150.0,a.proposed.protein,0.0)}
 @Test fun poorRecoveryPreventsCut() {val a=analyze(data(rate=0.5),5,1,1);assertEquals(2200.0,a.proposed.calories,0.0);assertTrue(a.reason.contains("recovery"))}
 @Test fun singleModerateDiscrepancyWaits() {val a=analyze(data(rate=-0.5));assertEquals(2200.0,a.proposed.calories,0.0);assertTrue(a.reason.contains("second"))}
 @Test fun consecutiveEvidenceAdjusts() {val d=data(rate=-0.5);val a=analyze(d);val prior=CheckIn(date="2026-10-03",weight=250.0,hunger=3,energy=3,training=3,analysisJson=json.encodeToString(a),decision="Kept",chosenTargetJson=json.encodeToString(target));val next=analyze(d.copy(checkins=listOf(prior)));assertTrue(next.proposed.calories<2200)}
 @Test fun zeroAlcoholIsGreen() {assertTrue(Engine.statuses(data(),end,emptySet()).all {it.alcohol==1})}
 @Test fun excludedDayIsNeutral() {val statuses=Engine.statuses(data(),end,setOf("2026-10-09"));assertEquals(-1,statuses.last().calories);assertEquals(-1,statuses.last().alcohol)}
 @Test fun trendUsesElapsedDays() {val weights=listOf(Weight(date="2026-10-01",pounds=250.0),Weight(date="2026-10-05",pounds=249.0),Weight(date="2026-10-09",pounds=248.0));assertNotNull(Engine.weeklyTrend(weights,end));assertEquals(249.7,Engine.trend(weights)[1].smooth,1e-6)}
 @Test fun staleWeightIsInsufficient() {assertNull(Engine.weeklyTrend(data().weights,"2026-10-20"))}
 @Test fun recipeYieldDividesTotals() {val a=Food(name="Oats",nutrition=Nutrition(150.0));val b=Food(name="Yogurt",nutrition=Nutrition(100.0));val r=Food(name="Recipe",kind="RECIPE",yieldServings=2.0);assertEquals(200.0,Engine.foodNutrition(r,listOf(a,b,r),listOf(Component(parentId=r.id,foodId=a.id,quantity=2.0),Component(parentId=r.id,foodId=b.id,quantity=1.0))).calories,0.0)}
 @Test(expected=IllegalArgumentException::class) fun recipeCycleRejected() {val r=Food(name="Cycle",kind="RECIPE");Engine.foodNutrition(r,listOf(r),listOf(Component(parentId=r.id,foodId=r.id,quantity=1.0)))}
 @Test fun ethanolIsNotSugarAlcohol() {assertEquals(14.00475,Engine.ethanol(355.0,5.0),.00001)}
 @Test fun serializationRoundTrip() {val d=data();val b=Backup(foods=emptyList(),components=emptyList(),entries=d.entries,days=d.days,weights=d.weights,targets=d.targets,checkins=emptyList(),settings=Settings());assertEquals(b,json.decodeFromString<Backup>(json.encodeToString(b)))}
 @Test fun offConvertsSodiumGramsToMg() {val f=OpenFoodFacts().normalize(JSONObject("""{"code":"123","product_name":"Test","nutriments":{"energy-kcal_100g":100,"proteins_100g":5,"carbohydrates_100g":12,"fat_100g":4,"sodium_100g":0.4}}"""));assertEquals(400.0,f!!.nutrition.sodium,0.0)}
 @Test fun offRejectsMissingNutrition() {assertNull(OpenFoodFacts().normalize(JSONObject("""{"code":"123","product_name":"Test","nutriments":{"energy-kcal_100g":100}}""")))}
 @Test fun ocrRequiresReviewAndPreservesText() {val f=LabelParser.parse("Serving size 170g\nCalories 120\nTotal Fat 2g\nTotal Carbohydrate 8g\nProtein 17g\nSodium 50mg");assertEquals(170.0,f.grams!!,0.0);assertEquals(120.0,f.nutrition.calories,0.0);assertEquals(17.0,f.nutrition.protein,0.0);assertTrue(f.notes.contains("Review"))}
}
