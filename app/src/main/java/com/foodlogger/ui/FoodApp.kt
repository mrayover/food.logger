@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.foodlogger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import androidx.compose.ui.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlogger.*
import com.foodlogger.R
import com.foodlogger.domain.*
import java.time.LocalDate

@Composable fun FoodApp(vm: AppModel) {
 val d by vm.data.collectAsStateWithLifecycle();val loaded by vm.loaded.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val message by vm.message.collectAsStateWithLifecycle()
 var screen by rememberSaveable { mutableStateOf("Home") };var date by rememberSaveable { mutableStateOf(today()) };var meal by rememberSaveable { mutableStateOf("Breakfast") }
 var picked by remember { mutableStateOf<Food?>(null) };var editFood by remember { mutableStateOf<Food?>(null) };var editEntry by remember { mutableStateOf<LogEntry?>(null) }
 var weightDialog by remember { mutableStateOf(false) };var targetDialog by remember { mutableStateOf(false) };var historyId by rememberSaveable { mutableStateOf<String?>(null) }
 var draft by rememberSaveable(stateSaver=Saver<CheckDraft?,String>(save={json.encodeToString(it)},restore={json.decodeFromString<CheckDraft?>(it)})) { mutableStateOf<CheckDraft?>(null) }
 var correctionId by rememberSaveable { mutableStateOf<String?>(null) }
 val correction=d.checkins.find {it.id==correctionId}
 fun back() { screen=when(screen) { "Add food"->"Log";"Recommendation"->"Check-In";"Check-in detail"->"Prior check-ins";"Prior check-ins"->"Check-In";else->"Home" } }
 BackHandler(screen!="Home") { back() }
 Scaffold(topBar={ TopAppBar(title={if(screen=="Home") Image(painterResource(R.drawable.wordmark),"Food.Logger",Modifier.width(218.dp).height(52.dp)) else Text(screen,style=MaterialTheme.typography.titleLarge)},navigationIcon={if(screen!="Home") TextButton({back()}) {Text("‹ Back")}},actions={if(screen=="Home") TextButton({screen="Settings"}) {Text("⚙",style=MaterialTheme.typography.headlineMedium)};if(screen=="Check-In") TextButton({screen="Prior check-ins"}) {Text("History")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Mist)) }) { padding ->
  Box(Modifier.fillMaxSize().padding(padding)) {
   if(!loaded) CircularProgressIndicator(Modifier.align(Alignment.Center)) else when(screen) {
    "Home"->Home(d,{screen=it},{weightDialog=true},{targetDialog=true})
    "Weight history"->WeightHistory(d,{weightDialog=true},{w->vm.work {vm.repository.dao.deleteWeight(w.id)}})
    "Log"->DailyLog(d,date,{date=it},{m->meal=m;screen="Add food"},{editEntry=it},{complete->vm.work {vm.repository.dao.day(DayLog(date,complete))}},{vm.work {vm.repository.copyDay(LocalDate.parse(date).minusDays(1).toString(),date,d)} })
    "Add food"->if(meal=="Alcohol") AlcoholEditor(d,{entry,preset->vm.work({screen="Log"}) { vm.repository.dao.entry(entry.copy(date=date));preset?.let {vm.repository.dao.food(it)} }}) else AddFood(vm,d,meal,{picked=it},{editFood=it})
    "Food Library"->Library(d,{picked=it;meal="Breakfast"},{editFood=it},{food->vm.work {vm.repository.dao.food(food.copy(favorite=!food.favorite))}},{food->vm.work {vm.repository.deleteFood(food,d)}},{food->vm.work {val copy=food.copy(id=uid(),name=food.name+" (copy)",source="Local copy",externalId="",createdAt=now(),updatedAt=now());vm.repository.saveFood(copy,d.components.filter { it.parentId==food.id }.map {it.copy(id=uid(),parentId=copy.id)},d)}})
    "Macros & Goal"->Targets(d) {targetDialog=true}
    "Check-In"->CheckForm(d,correction,{result->draft=result;screen="Recommendation"},{correctionId=null})
    "Recommendation"->draft?.let { dr->RecommendationScreen(dr,{decision->
     if(decision=="Manual") targetDialog=true else {
      val chosen=if(decision=="Accepted") dr.analysis.proposed else dr.analysis.oldTarget
      vm.work({screen="Prior check-ins";draft=null;correctionId=null}) {saveDecision(vm,dr,decision,chosen)}
     }
    }) }
    "Prior check-ins"->History(d) { historyId=it.id;screen="Check-in detail" }
    "Check-in detail"->d.checkins.find {it.id==historyId}?.let {CheckDetail(it,{correctionId=it.id;screen="Check-In"})}
    "Settings"->SettingsScreen(vm,d)
   }
   if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
  }
 }
 picked?.let { f->QuantityDialog(f,d,null,{picked=null},save={q->vm.work({picked=null;if(screen!="Food Library")screen="Log"}) {vm.repository.log(f,q,date,meal,d)}}) }
 editEntry?.let { e->QuantityDialog(Food(id="entry-${e.id}",name=e.name,serving=e.serving,grams=e.grams,nutrition=e.nutrition,lastQuantity=e.quantity),d,e,{editEntry=null},{q->vm.work({editEntry=null}) {vm.repository.dao.entry(e.copy(quantity=q))}},{vm.work({editEntry=null}) {vm.repository.dao.deleteEntry(e.id)}}) }
 editFood?.let {f->FoodEditor(f,d,{editFood=null}) {food,parts->vm.work({editFood=null;if(screen=="Add food")picked=food}) {vm.repository.saveFood(food,parts,d)}} }
 if(weightDialog) WeightDialog(d,{weightDialog=false}) {w->vm.work({weightDialog=false}) {vm.repository.dao.weight(w)}}
 if(targetDialog) TargetEditor(if(screen=="Recommendation") draft!!.analysis.proposed else Engine.targetOn(d.targets,today()),d.settings,{targetDialog=false}) { t->vm.work({targetDialog=false;if(screen=="Recommendation"){screen="Prior check-ins";draft=null;correctionId=null}}) {if(screen=="Recommendation")saveDecision(vm,draft!!,"Manual",t) else vm.repository.dao.target(t)} }
 message?.let {AlertDialog(onDismissRequest={vm.message.value=null},title={Text("Food.Logger")},text={Text(it)},confirmButton={TextButton({vm.message.value=null}) {Text("OK")}})}
}
