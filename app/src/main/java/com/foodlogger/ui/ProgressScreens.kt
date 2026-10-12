package com.foodlogger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.foodlogger.domain.*
import com.foodlogger.domain.Target
import com.foodlogger.data.validateTarget
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

fun displayWeight(lb: Double,unit: String)=if(unit=="kg")lb/2.2046226218 else lb
fun toPounds(value: Double,unit: String)=if(unit=="kg")value*2.2046226218 else value
@Composable fun Home(d: AppData,navigate: (String)->Unit,weight: ()->Unit,targets: ()->Unit) {
 val t=Engine.targetOn(d.targets,today());val last=d.weights.maxWithOrNull(compareBy<Weight>{it.date}.thenBy {it.createdAt});val rate=Engine.weeklyTrend(d.weights,today());val unit=d.settings.weightUnit
 val next=LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(d.settings.checkDay)))
 val status=Engine.statuses(d,today(),emptySet());val usable=status.count {it.complete}
 Page {
  Hint("LOG  →  MEASURE  →  ADJUST")
  Panel {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Heading("Your progress");TextButton(weight) {Text("+ Weight")}}
   Box(Modifier.clickable {navigate("Weight history")}) {WeightChart(d,30)}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
    Column {Hint("CURRENT");Text(last?.let {"${displayWeight(it.pounds,unit).f(1)} $unit"}?:"—",style=MaterialTheme.typography.headlineMedium)}
    Column {Hint("GOAL");Text("${displayWeight(t.goalPounds,unit).f(1)} $unit",style=MaterialTheme.typography.headlineMedium)}
   }
   Hint(rate?.let {"${displayWeight(it,unit).f(2)} $unit / week · smoothed trend"}?:"Add regular weigh-ins to see your weekly trend.")
  }
  if(t.id=="unconfigured")Action("Set your starting targets",onClick=targets) else Hint("${t.calories.f()} kcal · ${t.protein.f()} g protein / day")
  Action("LOG FOOD") {navigate("Log")}
  OutlinedButton({navigate("Macros & Goal")},Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text("MACROS & GOAL")}
  OutlinedButton({navigate("Food Library")},Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text("FOOD LIBRARY")}
  OutlinedButton({navigate("Check-In")},Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text("CHECK-IN")}
  Panel {Heading(if(next==LocalDate.now()) "Check-In Ready" else "Next check-in: ${next.dayOfWeek.name.lowercase().replaceFirstChar {it.uppercase()}}");Hint("$usable / 7 days fully logged · ${status.count {it.calories==1}} within calorie target");Hint("Mark fully logged days complete in your daily log.")}
 }
}
@Composable fun WeightChart(d: AppData,days: Long?=null) {
 val all=Engine.trend(d.weights);val points=if(days==null)all else all.filter {it.date>=LocalDate.now().minusDays(days).toString()}
 if(points.size<2) {Box(Modifier.fillMaxWidth().height(180.dp).padding(20.dp)) {Hint("Your trend starts with a few weigh-ins. Each reading stays visible alongside a smoothed line.")};return}
 val min=points.minOf {minOf(it.actual,it.smooth)}-1;val max=points.maxOf {maxOf(it.actual,it.smooth)}+1;val start=LocalDate.parse(points.first().date);val span=ChronoUnit.DAYS.between(start,LocalDate.parse(points.last().date)).coerceAtLeast(1)
 Canvas(Modifier.fillMaxWidth().height(185.dp).semantics {contentDescription="Weight chart, ${points.size} observations, actual points and smoothed trend"}) {
  fun xy(date: String,v: Double)=Offset(12f+(ChronoUnit.DAYS.between(start,LocalDate.parse(date)).toFloat()/span)*(size.width-24f),12f+((max-v)/(max-min)).toFloat()*(size.height-24f))
  repeat(4) {i->val y=12f+(size.height-24f)*i/3;drawLine(Color(0xFFE4ECEF),Offset(0f,y),Offset(size.width,y),1f)}
  val path=Path();points.forEachIndexed {i,p->val o=xy(p.date,p.smooth);if(i==0)path.moveTo(o.x,o.y)else path.lineTo(o.x,o.y)};drawPath(path,Teal,style=Stroke(width=5f))
  points.forEach {p->drawCircle(Color(0xFF7EB3BF),4f,xy(p.date,p.actual))}
  d.checkins.filter {it.date>=points.first().date && it.date<=points.last().date}.forEach {c->val x=xy(c.date,min).x;drawLine(Green.copy(alpha=.5f),Offset(x,12f),Offset(x,size.height-12f),2f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5f,5f)))}
 }
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Hint(points.first().date);Hint(points.last().date)}
 Hint("Dots: weigh-ins · Line: EWMA (α = 0.30) · Dashed: check-ins")
}
@Composable fun WeightHistory(d: AppData,add: ()->Unit,delete: (Weight)->Unit) {
 var remove by remember {mutableStateOf<Weight?>(null)}
 Page {Heading("Weight over time");Panel {WeightChart(d)};Action("+ Add weigh-in",onClick=add)
  d.weights.sortedWith(compareByDescending<Weight>{it.date}.thenByDescending {it.createdAt}).forEach {w->Panel {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Column {Text("${displayWeight(w.pounds,d.settings.weightUnit).f(1)} ${d.settings.weightUnit}",style=MaterialTheme.typography.titleMedium);Hint(w.date)};if(!w.id.startsWith("checkin-"))TextButton({remove=w}) {Text("Delete")}}}}
  Heading("Target changes");d.targets.sortedByDescending {it.effectiveDate}.forEach {Hint("${it.effectiveDate} · ${it.calories.f()} kcal · goal ${displayWeight(it.goalPounds,d.settings.weightUnit).f(1)} ${d.settings.weightUnit}")}
 }
 remove?.let {w->AlertDialog(onDismissRequest={remove=null},title={Text("Delete weigh-in?")},confirmButton={TextButton({delete(w);remove=null}) {Text("Delete")}},dismissButton={TextButton({remove=null}) {Text("Cancel")}})}
}
@Composable fun WeightDialog(d: AppData,dismiss: ()->Unit,save: (Weight)->Unit) {
 var value by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(today())};var error by remember {mutableStateOf<String?>(null)}
 AlertDialog(onDismissRequest=dismiss,title={Text("Record weight")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {Field("Weight (${d.settings.weightUnit})",value,{value=it},true);Field("Date YYYY-MM-DD",date,{date=it});error?.let {Text(it,color=Red)}}},confirmButton={TextButton({runCatching {require(LocalDate.parse(date)<=LocalDate.now());val pounds=toPounds(value.toDouble(),d.settings.weightUnit);require(pounds in 20.0..1500.0);save(Weight(date=date,pounds=pounds))}.onFailure {error="Enter a valid weight and a date no later than today."}}) {Text("Save")}},dismissButton={TextButton(dismiss) {Text("Cancel")}})
}
@Composable fun Targets(d: AppData,edit: ()->Unit) {
 val t=Engine.targetOn(d.targets,today())
 Page {Heading("Macros & Goal");Panel {Text("${t.calories.f()} kcal",style=MaterialTheme.typography.headlineLarge);Text("Protein ${t.protein.f()} g · Carbs ${t.carbs.f()} g · Fat ${t.fat.f()} g");Hint("Goal ${displayWeight(t.goalPounds,d.settings.weightUnit).f(1)} ${d.settings.weightUnit} · ${displayWeight(t.weeklyRate,d.settings.weightUnit).f(2)} ${d.settings.weightUnit}/week");Hint("Alcohol allowance: ${t.alcoholAllowance.f(1)} standard drinks per day")};Action("Edit targets",onClick=edit);Heading("Target history");d.targets.sortedWith(compareByDescending<Target>{it.effectiveDate}.thenByDescending {it.createdAt}).forEach {Panel {Text("Effective ${it.effectiveDate}",style=MaterialTheme.typography.titleMedium);Hint("${it.calories.f()} kcal · P ${it.protein.f()} / C ${it.carbs.f()} / F ${it.fat.f()} g")}} }
}
@Composable fun TargetEditor(original: Target,settings: Settings,dismiss: ()->Unit,save: (Target)->Unit) {
 var date by rememberSaveable {mutableStateOf(today())};var calories by rememberSaveable {mutableStateOf(original.calories.toString())};var protein by rememberSaveable {mutableStateOf(original.protein.toString())};var carbs by rememberSaveable {mutableStateOf(original.carbs.toString())};var fat by rememberSaveable {mutableStateOf(original.fat.toString())};var goal by rememberSaveable {mutableStateOf(displayWeight(original.goalPounds,settings.weightUnit).f(2))};var rate by rememberSaveable {mutableStateOf(displayWeight(original.weeklyRate,settings.weightUnit).f(2))};var alcohol by rememberSaveable {mutableStateOf(original.alcoholAllowance.toString())};var error by remember {mutableStateOf<String?>(null)}
 AlertDialog(onDismissRequest=dismiss,title={Text("Set targets")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Hint("Changes take effect on the date below. Negative weekly change means weight loss.");Field("Effective YYYY-MM-DD",date,{date=it});Field("Calories",calories,{calories=it},true);Field("Protein (g)",protein,{protein=it},true);Field("Carbohydrates (g)",carbs,{carbs=it},true);Field("Fat (g)",fat,{fat=it},true);Field("Goal weight (${settings.weightUnit})",goal,{goal=it},true);Field("Weekly change (${settings.weightUnit})",rate,{rate=it});Field("Alcohol allowance (standard drinks/day)",alcohol,{alcohol=it},true);error?.let {Text(it,color=Red)}
 }},confirmButton={TextButton({runCatching {LocalDate.parse(date);val t=Target(effectiveDate=date,calories=calories.toDouble(),protein=protein.toDouble(),carbs=carbs.toDouble(),fat=fat.toDouble(),goalPounds=toPounds(goal.toDouble(),settings.weightUnit),weeklyRate=toPounds(rate.toDouble(),settings.weightUnit),alcoholAllowance=alcohol.toDouble());validateTarget(t);save(t)}.onFailure {error=it.message?:"Check all fields."}}) {Text("Confirm targets")}},dismissButton={TextButton(dismiss) {Text("Cancel")}})
}
