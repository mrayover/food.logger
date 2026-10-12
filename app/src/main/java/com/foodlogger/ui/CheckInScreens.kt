package com.foodlogger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodlogger.AppModel
import com.foodlogger.domain.*
import kotlinx.serialization.encodeToString
import java.time.LocalDate

data class CheckDraft(val date: String,val weight: Double,val hunger: Int,val energy: Int,val training: Int,val notes: String,val excluded: Set<String>,val analysis: Analysis,val correction: CheckIn?=null)
suspend fun saveDecision(vm: AppModel,d: CheckDraft,decision: String,chosen: Target) {
 val old=d.correction
 if(old!=null) {
  val check=old.copy(weight=d.weight,hunger=d.hunger,energy=d.energy,training=d.training,notes=d.notes,excludedDates=d.excluded.joinToString(","),analysisJson=json.encodeToString(d.analysis),lastEditedAt=now())
  vm.repository.finishCheckIn(check,json.decodeFromString(old.chosenTargetJson),false)
 } else {
  val check=CheckIn(date=d.date,weight=d.weight,hunger=d.hunger,energy=d.energy,training=d.training,notes=d.notes,excludedDates=d.excluded.joinToString(","),analysisJson=json.encodeToString(d.analysis),decision=decision,chosenTargetJson=json.encodeToString(chosen))
  vm.repository.finishCheckIn(check,chosen,decision!="Kept")
 }
}
@Composable fun StatusStrip(days: List<DayStatus>,alcohol: Boolean=false,onToggle: ((String)->Unit)?=null) {
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
  days.forEach {day->val state=if(alcohol)day.alcohol else day.calories;val color=when(state){1->Green;0->Red;2->Orange;else->Color(0xFFE3E9EC)}
   Column(Modifier.weight(1f)) {
    Surface(onClick={onToggle?.invoke(day.date)},enabled=onToggle!=null,shape=RoundedCornerShape(9.dp),color=color,modifier=Modifier.fillMaxWidth().height(48.dp)) {Box(contentAlignment=androidx.compose.ui.Alignment.Center) {Text(when(state){1->"✓";0->"×";2->"!";else->"—"},color=if(state==-1)Navy else Color.White)}}
    Text(day.date.takeLast(2),Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall)
   }
  }
 }
}
@Composable fun Rating(label: String,value: Int,change: (Int)->Unit) {Text(label,style=MaterialTheme.typography.titleMedium);Choice((1..5).map {it.toString()},value.toString()) {change(it.toInt())}}
@Composable fun CheckForm(d: AppData,correction: CheckIn?,calculate: (CheckDraft)->Unit,clearCorrection: ()->Unit) {
 val date=correction?.date?:today()
 var weight by rememberSaveable(correction?.id) {mutableStateOf((correction?.weight?:d.weights.maxByOrNull {it.date}?.pounds)?.let {displayWeight(it,d.settings.weightUnit).f(1)}?:"")}
 var hunger by rememberSaveable(correction?.id) {mutableIntStateOf(correction?.hunger?:3)};var energy by rememberSaveable(correction?.id) {mutableIntStateOf(correction?.energy?:3)};var training by rememberSaveable(correction?.id) {mutableIntStateOf(correction?.training?:3)};var notes by rememberSaveable(correction?.id) {mutableStateOf(correction?.notes?:"")}
 var excludedText by rememberSaveable(correction?.id) {mutableStateOf(correction?.excludedDates?:"")};val excluded=excludedText.split(',').filter {it.isNotBlank()}.toSet()
 val days=Engine.statuses(d,date,excluded);var error by remember {mutableStateOf<String?>(null)}
 Page {
  Heading(if(correction!=null) "Correct check-in · $date" else "Your weekly check-in")
  if(correction!=null) {Hint("Correcting the inputs preserves the original date, recommendation and recorded target decision. No new targets will be applied.");TextButton(clearCorrection) {Text("Cancel correction")}}
  Field("Current weight (${d.settings.weightUnit})",weight,{weight=it},true)
  Panel {Heading("Calorie & macro adherence");Hint("Prior seven days. Tap a day to exclude/restore it. Only days marked fully logged in the Log can be classified.");StatusStrip(days) {date->excludedText=(if(date in excluded)excluded-date else excluded+date).joinToString(",")};Hint("✓ Within calories · × Outside · — Unknown");Text("Protein: ${days.count {it.protein==1}} / ${days.count {it.complete}} complete days meet target")}
  Panel {Heading("Alcohol intake");StatusStrip(days,true) {date->excludedText=(if(date in excluded)excluded-date else excluded+date).joinToString(",")};Hint("✓ Within allowance (including zero) · ! Above · × Substantially above · — Unknown")}
  Rating("Hunger · 1 low, 5 high",hunger) {hunger=it};Rating("Energy · 1 low, 5 high",energy) {energy=it};Rating("Training performance · 1 poor, 5 strong",training) {training=it}
  OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("Notes")},minLines=3)
  error?.let {Text(it,color=Red)}
  Action("CALCULATE") {runCatching {
   val w=toPounds(weight.toDouble(),d.settings.weightUnit);require(w in 20.0..1500.0) {"Enter a valid current weight."}
   var analysis=Engine.analyze(d,date,w,hunger,energy,training,excluded)
   if(correction!=null) {
    val prior=json.decodeFromString<Analysis>(correction.originalAnalysisJson)
    val historical=d.copy(targets=d.targets.filter {it.effectiveDate<date}+prior.oldTarget.copy(effectiveDate=date),checkins=d.checkins.filter {it.id!=correction.id})
    analysis=Engine.analyze(historical,date,w,hunger,energy,training,excluded)
   }
   calculate(CheckDraft(date,w,hunger,energy,training,notes,excluded,analysis,correction))
  }.onFailure {error=it.message?:"Check your weight."}}
 }
}
@Composable fun AnalysisPanel(a: Analysis) {Panel {
 Heading("Week in review");Text("${a.usable} / 7 complete logging days");Text("Calories: ${(a.calorieAdherence*100).f()}% within target");Text("Protein: ${(a.proteinAdherence*100).f()}% meeting target");Text("Alcohol: ${(a.alcoholAdherence*100).f()}% within allowance");Hint("Smoothed rate: ${a.trend?.f(2)?:"insufficient data"} lb/week · goal ${a.oldTarget.weeklyRate.f(2)} lb/week")
}}
@Composable fun RecommendationScreen(d: CheckDraft,decide: (String)->Unit) {
 Page {AnalysisPanel(d.analysis);Panel {Heading("Suggested calories");Text("${d.analysis.oldTarget.calories.f()} → ${d.analysis.proposed.calories.f()} kcal",style=MaterialTheme.typography.headlineMedium);Text("Why?",style=MaterialTheme.typography.titleMedium);Text(d.analysis.reason);Hint("Hunger ${d.hunger}/5 · Energy ${d.energy}/5 · Training ${d.training}/5")}
  if(d.correction!=null)Action("Save correction · preserve decision") {decide("Correction")} else {
   Action("ACCEPT RECOMMENDATION") {decide("Accepted")};OutlinedButton({decide("Kept")},Modifier.fillMaxWidth()) {Text("KEEP CURRENT TARGETS")};TextButton({decide("Manual")},Modifier.fillMaxWidth()) {Text("ADJUST MANUALLY")}
  }
 }
}
@Composable fun History(d: AppData,open: (CheckIn)->Unit) {Page {
 Heading("Prior check-ins");if(d.checkins.isEmpty())Hint("Completed check-ins appear here after you choose a target decision.")
 d.checkins.sortedByDescending {it.date}.forEach {c->val a=json.decodeFromString<Analysis>(c.analysisJson);val t=json.decodeFromString<Target>(c.chosenTargetJson);Card(onClick={open(c)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Heading(c.date);Text("${displayWeight(c.weight,d.settings.weightUnit).f(1)} ${d.settings.weightUnit} · ${c.decision}");Hint("${a.usable}/7 days · ${(a.calorieAdherence*100).f()}% calorie adherence");Hint("Trend ${a.trend?.f(2)?:"—"} lb/week · ${a.oldTarget.calories.f()} → ${t.calories.f()} kcal");if(c.lastEditedAt!=null)Hint("Corrected")}}}
}}
@Composable fun CheckDetail(c: CheckIn,edit: ()->Unit) {
 val a=json.decodeFromString<Analysis>(c.analysisJson);val original=json.decodeFromString<Analysis>(c.originalAnalysisJson);val chosen=json.decodeFromString<Target>(c.chosenTargetJson)
 Page {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Heading(c.date);TextButton(edit) {Text("Edit")}};Text("Weight ${c.weight.f(1)} lb");AnalysisPanel(a)
  Panel {Heading("Saved daily classifications");StatusStrip(a.days);StatusStrip(a.days,true);a.days.forEach {Hint("${it.date}: ${if(it.complete) "complete" else "unknown"} · ${it.nutrition.line()}")}}
  Panel {Heading("Recommendation");Text("Originally suggested ${original.proposed.calories.f()} kcal");Text(original.reason);if(c.lastEditedAt!=null){Text("Corrected analysis: ${a.proposed.calories.f()} kcal");Text(a.reason)};Heading("Your recorded decision");Text("${c.decision} · ${chosen.calories.f()} kcal");Hint("P ${chosen.protein.f()} / C ${chosen.carbs.f()} / F ${chosen.fat.f()} g · effective ${chosen.effectiveDate}")}
  Text("Hunger ${c.hunger}/5 · Energy ${c.energy}/5 · Training ${c.training}/5");Text(c.notes.ifBlank {"No notes"});Hint("Created ${java.time.Instant.ofEpochMilli(c.createdAt)}");c.lastEditedAt?.let {Hint("Last corrected ${java.time.Instant.ofEpochMilli(it)}")}
 }
}
