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
import com.foodlogger.domain.*
import java.time.LocalDate

@Composable fun DailyLog(d: AppData,date: String,onDate: (String)->Unit,onAdd: (String)->Unit,onEntry: (LogEntry)->Unit,onComplete: (Boolean)->Unit,onCopy: ()->Unit) {
 var jump by remember {mutableStateOf(false)};var copy by remember {mutableStateOf(false)}
 val entries=d.entries.filter {it.date==date};val total=entries.fold(Nutrition()) {n,e->n+e.nutrition*e.quantity};val t=Engine.targetOn(d.targets,date)
 Column(Modifier.fillMaxSize()) {
  Surface(color=Teal,contentColor=Color.White,shape=RoundedCornerShape(bottomStart=24.dp,bottomEnd=24.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
    TextButton({onDate(LocalDate.parse(date).minusDays(1).toString())}) {Text("‹",color=Color.White)}
    TextButton({jump=true}) {Text(if(date==today()) "Today · $date" else date,color=Color.White)}
    TextButton({onDate(LocalDate.parse(date).plusDays(1).toString())}) {Text("›",color=Color.White)}
   }
   Text("REMAINING",style=MaterialTheme.typography.labelLarge)
   Text("${(t.calories-total.calories).f()} kcal",style=MaterialTheme.typography.headlineLarge)
   Text("P ${(t.protein-total.protein).f()} g  ·  C ${(t.carbs-total.carbs).f()} g  ·  F ${(t.fat-total.fat).f()} g")
   if(t.id=="unconfigured")Text("Set targets in Macros & Goal. These are starter values.")
  } }
  Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   meals.forEach { meal ->
    var expanded by rememberSaveable(date,meal) {mutableStateOf(false)}
    val list=entries.filter {it.meal==meal};val sub=list.fold(Nutrition()) {n,e->n+e.nutrition*e.quantity}
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
     Column(Modifier.fillMaxWidth().clickable {expanded=!expanded}.padding(16.dp)) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Heading(meal);Text(if(expanded) "−" else "+")};NutritionText(sub)}
     if(expanded) Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
      list.forEach { e->Column(Modifier.fillMaxWidth().clickable {onEntry(e)}.padding(vertical=10.dp)) {Text(e.name,style=MaterialTheme.typography.titleMedium);Hint("${e.quantity.f(2)} × ${e.serving}");NutritionText(e.nutrition*e.quantity)} }
      Action("+ Add to $meal") {onAdd(meal)};Spacer(Modifier.height(8.dp))
     }
    }
   }
   val complete=d.days.any {it.date==date && it.complete}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Column(Modifier.weight(1f)) {Text("Day fully logged",style=MaterialTheme.typography.titleMedium);Hint("Include this day in check-in analysis.")};Switch(complete,onComplete)}
   OutlinedButton({copy=true},Modifier.fillMaxWidth()) {Text("Copy previous day's entries")}
   Hint("Consumed: ${total.line()}. Alcohol: ${(total.ethanol/14).f(1)} standard drinks.")
  }
 }
 if(jump) {var v by remember {mutableStateOf(date)};AlertDialog(onDismissRequest={jump=false},title={Text("Go to date")},text={Field("YYYY-MM-DD",v,{v=it})},confirmButton={TextButton({if(runCatching {LocalDate.parse(v)}.isSuccess){onDate(v);jump=false}}) {Text("Go")}},dismissButton={TextButton({onDate(today());jump=false}) {Text("Today")}})}
 if(copy) AlertDialog(onDismissRequest={copy=false},title={Text("Copy previous day?")},text={Text("Entries will be added to $date. Existing entries stay in place; the day is not marked complete automatically.")},confirmButton={TextButton({onCopy();copy=false}) {Text("Copy")}},dismissButton={TextButton({copy=false}) {Text("Cancel")}})
}
@Composable fun QuantityDialog(food: Food,d: AppData,entry: LogEntry?,dismiss: ()->Unit,save: (Double)->Unit,delete: (()->Unit)?=null) {
 var unit by rememberSaveable {mutableStateOf(if(food.grams!=null) d.settings.foodUnit else "servings")};var amount by rememberSaveable {mutableStateOf(when(unit){"g"->(food.lastQuantity*food.grams!!).toString();"oz"->(food.lastQuantity*food.grams!!/28.349523125).toString();else->food.lastQuantity.toString()})};var confirmDelete by remember {mutableStateOf(false)}
 val q=runCatching {Engine.multiplier(amount.toDouble(),unit,food.grams)}.getOrNull()
 val n=runCatching {Engine.foodNutrition(food,d.foods,d.components)}.getOrDefault(food.nutrition)
 AlertDialog(onDismissRequest=dismiss,title={Text(food.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Hint("Base: ${food.serving}${food.grams?.let { " ($it g)" } ?: ""}")
  Choice(if(food.grams!=null)listOf("servings","g","oz") else listOf("servings"),unit) {next->val current=q?:1.0;unit=next;amount=when(next){"g"->(current*food.grams!!).toString();"oz"->(current*food.grams!!/28.349523125).toString();else->current.toString()}}
  Field("Quantity",amount,{amount=it},true);q?.let {NutritionText(n*it)}
  if(food.kind=="SAVED_MEAL")Hint("Adds each component separately so you can edit them afterward.")
  if(delete!=null) TextButton({confirmDelete=true}) {Text("Delete entry",color=Red)}
 }},confirmButton={TextButton({q?.let(save)},enabled=q!=null) {Text(if(entry==null) "Add" else "Save")}},dismissButton={TextButton(dismiss) {Text("Cancel")}})
 if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this entry?")},confirmButton={TextButton({delete?.invoke()}) {Text("Delete")}},dismissButton={TextButton({confirmDelete=false}) {Text("Cancel")}})
}
@Composable fun AlcoholEditor(d: AppData,save: (LogEntry,Food?)->Unit) {
 var category by rememberSaveable {mutableStateOf("Beer")};var name by rememberSaveable {mutableStateOf("Beer")};var volume by rememberSaveable {mutableStateOf("355")};var abv by rememberSaveable {mutableStateOf("5")};var quantity by rememberSaveable {mutableStateOf("1")};var calories by rememberSaveable {mutableStateOf("150")};var carbs by rememberSaveable {mutableStateOf("13")};var preset by rememberSaveable {mutableStateOf(false)}
 val ml=volume.toDoubleOrNull();val a=abv.toDoubleOrNull();val q=quantity.toDoubleOrNull();val k=calories.toDoubleOrNull();val c=carbs.toDoubleOrNull()
 val ethanol=if(ml!=null && a!=null)Engine.ethanol(ml,a) else 0.0
 Page {
  Heading("Log a drink");Hint("One standard drink = 14 g ethanol. Estimates are editable; mixed drinks may contain additional calories.")
  d.foods.filter {it.kind=="ALCOHOL"}.forEach {f->OutlinedButton({name=f.name;calories=f.nutrition.calories.toString();carbs=f.nutrition.carbs.toString();val values=f.notes.split("|");if(values.size==3){category=values[0];volume=values[1];abv=values[2]}}) {Text(f.name)}}
  Choice(listOf("Beer","Wine","Mixed Drink","Other"),category) {category=it;name=it;when(it){"Beer"->{volume="355";abv="5";calories="150";carbs="13"};"Wine"->{volume="148";abv="12";calories="125";carbs="4"};"Mixed Drink"->{volume="180";abv="10";calories="200";carbs="18"};else->{volume="44";abv="40";calories="98";carbs="0"}}}
  Field("Name",name,{name=it});Field("Volume per drink (ml)",volume,{volume=it},true);Field("ABV %",abv,{abv=it},true);Field("Number of drinks",quantity,{quantity=it},true);Field("Calories per drink",calories,{calories=it},true);Field("Carbs per drink (g)",carbs,{carbs=it},true)
  Hint("${(ethanol*(q?:1.0)).f(1)} g ethanol · ${(ethanol*(q?:1.0)/14).f(2)} standard drinks")
  Row {Checkbox(preset,{preset=it});Text("Save as alcohol preset",Modifier.padding(top=12.dp))}
  Action("Add to Alcohol",name.isNotBlank() && ml!=null && ml>0 && a!=null && a in 0.0..100.0 && q!=null && q>0 && k!=null && k>=0 && c!=null && c>=0) {
   val n=Nutrition(calories=k!!,carbs=c!!,ethanol=ethanol);save(LogEntry(meal="Alcohol",name=name,serving="${ml} ml at $a%",quantity=q!!,nutrition=n,alcoholCategory=category,volumeMl=ml!!,abv=a!!),if(preset)Food(name=name,kind="ALCOHOL",serving="$ml ml",nutrition=n,notes="$category|$ml|$a")else null)
  }
 }
}
