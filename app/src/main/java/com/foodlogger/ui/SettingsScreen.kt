package com.foodlogger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foodlogger.*
import com.foodlogger.domain.*
import com.foodlogger.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@Composable fun SettingsScreen(vm: AppModel,d: AppData) {
 val context=LocalContext.current
 var weightUnit by rememberSaveable {mutableStateOf(d.settings.weightUnit)};var foodUnit by rememberSaveable {mutableStateOf(d.settings.foodUnit)};var checkDay by rememberSaveable {mutableStateOf(d.settings.checkDay.toString())};var calorieTol by rememberSaveable {mutableStateOf((d.settings.calorieTolerance*100).f(1))};var proteinTol by rememberSaveable {mutableStateOf((d.settings.proteinTolerance*100).f(1))};var orange by rememberSaveable {mutableStateOf(d.settings.alcoholOrangeExtra.toString())};var red by rememberSaveable {mutableStateOf(d.settings.alcoholRedExtra.toString())}
 var importBackup by remember {mutableStateOf<Backup?>(null)};var exportSummary by rememberSaveable {mutableStateOf(false)};var confirm by remember {mutableStateOf<String?>(null)}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->uri?.let {vm.work {val text=if(exportSummary)vm.repository.summary(d)else json.encodeToString(vm.repository.backup(d));withContext(Dispatchers.IO){context.contentResolver.openOutputStream(it)?.use {stream->stream.write(text.toByteArray())}?:error("Cannot open destination")};vm.message.value="Export saved."}}}
 val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->uri?.let {vm.work {val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(it)?.use {stream->
   val limit=20*1024*1024
   val output=java.io.ByteArrayOutputStream()
   val buffer=ByteArray(8192)
   var total=0
   while(true) {
    val count=stream.read(buffer,0,minOf(buffer.size,limit-total+1))
    if(count<0) break
    total+=count
    require(total<=limit){"Backup is too large (20 MB limit)."}
    output.write(buffer,0,count)
   }
   output.toString("UTF-8")
  }?:error("Cannot read backup")};val backup=json.decodeFromString<Backup>(text);vm.repository.validate(backup);importBackup=backup}}}
 Page {
  Heading("Preferences");Text("Weight units");Choice(listOf("lb","kg"),weightUnit) {weightUnit=it};Text("Food weight units");Choice(listOf("g","oz"),foodUnit) {foodUnit=it}
  Field("Check-in weekday (1 Monday … 7 Sunday)",checkDay,{checkDay=it},true);Field("Calorie tolerance ± %",calorieTol,{calorieTol=it},true);Field("Protein below-target tolerance %",proteinTol,{proteinTol=it},true)
  Field("Alcohol orange above allowance (drinks)",orange,{orange=it},true);Field("Alcohol red above allowance (drinks)",red,{red=it},true)
  Hint("Between allowance and orange threshold is also shown as modestly above. Red begins at the red threshold. Alcohol allowance and weekly weight goal are in Macros & Goal.")
  Action("Save preferences") {vm.work {
   val s=Settings(weightUnit=weightUnit,foodUnit=foodUnit,checkDay=checkDay.toInt(),calorieTolerance=calorieTol.toDouble()/100,proteinTolerance=proteinTol.toDouble()/100,alcoholOrangeExtra=orange.toDouble(),alcoholRedExtra=red.toDouble())
   require(s.checkDay in 1..7 && s.calorieTolerance in 0.0..1.0 && s.proteinTolerance in 0.0..1.0 && s.alcoholOrangeExtra>=0 && s.alcoholRedExtra>s.alcoholOrangeExtra) {"Check the weekday, tolerances and alcohol thresholds."}
   vm.repository.dao.settings(s);vm.message.value="Preferences saved."
  }}
  Heading("Your data");Hint("Backups contain your full local records. Summary exports are for a future dashboard and cannot restore the app.")
  Action("Export complete backup") {exportSummary=false;export.launch("Food.Logger-backup-${today()}.json")}
  OutlinedButton({exportSummary=true;export.launch("Food.Logger-summary-${today()}.json")},Modifier.fillMaxWidth()) {Text("Export dashboard summary")}
  OutlinedButton({importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))},Modifier.fillMaxWidth()) {Text("Import complete backup")}
  Hint("Food search: Open Food Facts contributors (ODbL), USDA FoodData Central. No Food.Logger account or cloud storage. Search terms and scanned barcodes are sent only to food providers when you search.")
  if(BuildConfig.DEBUG) {
   Heading("Developer tools · Debug only");Hint("Demo generation requires an empty database. Clear demo removes generated records only; Reset removes everything.")
   OutlinedButton({confirm="demo"},Modifier.fillMaxWidth()) {Text("Populate demo data")};OutlinedButton({confirm="clear demo"},Modifier.fillMaxWidth()) {Text("Clear demo data")};TextButton({confirm="reset"},Modifier.fillMaxWidth()) {Text("Reset local database",color=Red)}
  }
 }
 importBackup?.let {b->AlertDialog(onDismissRequest={importBackup=null},title={Text("Restore this backup?")},text={Text("This replaces all current local data with ${b.foods.size} foods, ${b.entries.size} log entries and ${b.checkins.size} check-ins. Export a backup first if you want to keep current records.")},confirmButton={TextButton({vm.work({importBackup=null}) {vm.repository.restore(b);vm.message.value="Backup restored."}}) {Text("Replace and restore")}},dismissButton={TextButton({importBackup=null}) {Text("Cancel")}})}
 confirm?.let {action->AlertDialog(onDismissRequest={confirm=null},title={Text("Confirm $action")},text={Text(if(action=="reset")"All local records will be deleted. This cannot be undone without a backup." else "Continue with $action?")},confirmButton={TextButton({vm.work({confirm=null}) {when(action){"demo"->Demo.populate(vm.repository,d);"clear demo"->Demo.clear(vm.repository,d);else->vm.repository.db.withTransactionCompat {vm.repository.clear()}}}}) {Text("Continue")}},dismissButton={TextButton({confirm=null}) {Text("Cancel")}})}
}
