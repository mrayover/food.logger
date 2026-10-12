package com.foodlogger.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object Rules {
 const val EWMA_ALPHA = .30
 const val MIN_COMPLETE_DAYS = 5
 const val MIN_ADHERENCE = .85
 const val RATE_DEADBAND = .25
 const val MIN_CALORIES = 1200.0
 const val MAX_CALORIES = 6000.0
}
data class TrendPoint(val date: String, val actual: Double, val smooth: Double)
object Engine {
 fun targetOn(targets: List<Target>, date: String): Target = targets.filter { it.effectiveDate <= date }.maxWithOrNull(compareBy<Target> { it.effectiveDate }.thenBy { it.createdAt }) ?: Target(id="unconfigured",effectiveDate="0001-01-01")
 fun trend(weights: List<Weight>): List<TrendPoint> {
  var smooth: Double? = null
  return weights.groupBy { it.date }.toSortedMap().map { (date,values) ->
   val actual = values.maxBy { it.createdAt }.pounds
   smooth = smooth?.let { it + Rules.EWMA_ALPHA*(actual-it) } ?: actual
   TrendPoint(date,actual,smooth!!)
  }
 }
 fun weeklyTrend(weights: List<Weight>, end: String): Double? {
  val points=trend(weights.filter { it.date <= end })
  val last=points.lastOrNull() ?: return null
  if(ChronoUnit.DAYS.between(LocalDate.parse(last.date),LocalDate.parse(end))>3) return null
  val first=points.lastOrNull { it.date<=LocalDate.parse(last.date).minusDays(7).toString() } ?: return null
  val span=ChronoUnit.DAYS.between(LocalDate.parse(first.date),LocalDate.parse(last.date))
  if(span !in 7..14 || points.count { it.date >= first.date }<3) return null
  return (last.smooth-first.smooth)*7.0/span
 }
 fun statuses(data: AppData,end: String,excluded: Set<String>): List<DayStatus> = (7 downTo 1).map { offset ->
  val date=LocalDate.parse(end).minusDays(offset.toLong()).toString()
  val n=data.entries.filter { it.date==date }.fold(Nutrition()) { a,e->a+e.nutrition*e.quantity }
  val t=targetOn(data.targets,date)
  val complete=data.days.any { it.date==date && it.complete } && date !in excluded && t.id!="unconfigured"
  val drinks=n.ethanol/14.0
  val extra=(drinks-t.alcoholAllowance).coerceAtLeast(0.0)
  DayStatus(date,complete,if(!complete) -1 else if(abs(n.calories-t.calories)<=t.calories*data.settings.calorieTolerance) 1 else 0,
   if(!complete) -1 else if(n.protein>=t.protein*(1-data.settings.proteinTolerance)) 1 else 0,
   if(!complete) -1 else if(extra<=.001) 1 else if(extra<data.settings.alcoholRedExtra) 2 else 0,n,t)
 }
 fun analyze(data: AppData,date: String,weight: Double,hunger: Int,energy: Int,training: Int,excluded: Set<String>): Analysis {
  val days=statuses(data,date,excluded); val usable=days.count { it.complete }
  fun ratio(f:(DayStatus)->Boolean)=if(usable==0) 0.0 else days.count(f).toDouble()/usable
  val ca=ratio { it.calories==1 }; val pa=ratio { it.protein==1 }; val aa=ratio { it.alcohol==1 }
  val rate=weeklyTrend(data.weights.filter { it.date!=date }+Weight(date=date,pounds=weight),date)
  val current=targetOn(data.targets,date)
  val error=rate?.minus(current.weeklyRate)
  val previous=data.checkins.filter { it.date<=LocalDate.parse(date).minusDays(6).toString() && it.date>=LocalDate.parse(date).minusDays(15).toString() }.maxByOrNull { it.date }
  val prev=previous?.let { runCatching { json.decodeFromString<Analysis>(it.analysisJson) }.getOrNull() }
  val consistent=prev?.let { it.usable>=5 && it.calorieAdherence>=.85 && it.trend!=null && error!=null && (it.trend-it.oldTarget.weeklyRate)*error>0 } ?: false
  var delta=0.0
  val reason=when {
   current.id=="unconfigured" -> "Set your starting targets before making an adjustment."
   usable<Rules.MIN_COMPLETE_DAYS -> "Insufficient evidence: $usable of 7 days are complete. Mark fully logged days complete; unknown days are excluded, never counted as zero."
   ca<.70 -> "Logging is sufficiently complete, but calorie adherence is ${(ca*100).toInt()}%. Keep targets and work on consistency before adjusting."
   ca<Rules.MIN_ADHERENCE -> "Calorie adherence is ${(ca*100).toInt()}%. Keep targets while building a more consistent week."
   pa<.70 -> "Protein adherence is low. Keep calories and improve protein consistency before adjusting."
   rate==null -> "More weight observations are needed: at least three readings spanning 7–14 days, including a recent reading. Keep targets."
   abs(error!!)<=Rules.RATE_DEADBAND -> "Your smoothed weight change is within 0.25 lb/week of your goal. Keep targets."
   error>0 && hunger>=4 && (energy<=2 || training<=2) -> "Hunger and recovery suggest holding calories rather than cutting. Maintain and reassess next week."
   aa<.70 -> "Alcohol exceeded the allowance on several complete days. Address that pattern before changing calories."
   !consistent && abs(error)<=.75 -> "This week's trend differs from the goal, but a second consistent check-in is needed before changing calories."
   else -> {
    val size=when { abs(error)<=.5->100.0; abs(error)<=.75->150.0; else->200.0 }
    delta=if(error>0) -size else size
    "Reliable logging and ${if(consistent) "two consistent check-ins" else "a large trend discrepancy"} support a ${size.toInt()} kcal/day ${if(delta<0) "decrease" else "increase"}. This is a suggestion; you choose the next target."
   }
  }
  val calories=if(delta==0.0) current.calories else (current.calories+delta).coerceIn(Rules.MIN_CALORIES,Rules.MAX_CALORIES)
  val proposed=current.copy(id=uid(),effectiveDate=date,calories=calories,carbs=(current.carbs+(calories-current.calories)/4).coerceAtLeast(0.0),createdAt=now())
  return Analysis(days,rate,usable,ca,pa,aa,current,proposed,reason)
 }
 fun foodNutrition(food: Food,foods: List<Food>,parts: List<Component>,seen: Set<String> = emptySet()): Nutrition {
  require(food.id !in seen) { "Recipe cycle" }
  if(food.kind !in listOf("RECIPE","SAVED_MEAL")) return food.nutrition
  return parts.filter { it.parentId==food.id }.fold(Nutrition()) { n,p ->
   val child=foods.find { it.id==p.foodId } ?: error("Missing ingredient")
   n+foodNutrition(child,foods,parts,seen+food.id)*p.quantity
  }*(1.0/food.yieldServings)
 }
 fun multiplier(amount: Double,unit: String,grams: Double?): Double {
  require(amount.isFinite() && amount>0) { "Enter a positive quantity" }
  return when(unit) { "g" -> amount/requireNotNull(grams); "oz" -> amount*28.349523125/requireNotNull(grams); else -> amount }.also { require(it.isFinite() && it>0) }
 }
 fun ethanol(volumeMl: Double,abv: Double)=volumeMl*abv/100.0*.789
}
val json=kotlinx.serialization.json.Json { prettyPrint=true; encodeDefaults=true; ignoreUnknownKeys=true; allowSpecialFloatingPointValues=false }
