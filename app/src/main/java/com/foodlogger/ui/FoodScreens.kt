@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.foodlogger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foodlogger.AppModel
import com.foodlogger.BuildConfig
import com.foodlogger.domain.*
import com.foodlogger.providers.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.*
import java.io.File

@Composable fun AddFood(vm: AppModel,d: AppData,meal: String,select: (Food)->Unit,create: (Food)->Unit) {
 var query by rememberSaveable {mutableStateOf("")};var remote by remember {mutableStateOf(emptyList<Food>())};var messages by remember {mutableStateOf(emptyList<String>())};var searching by remember {mutableStateOf(false)};var barcodeEntry by remember {mutableStateOf(false)};var showScan by remember {mutableStateOf(false)}
 var offline by remember {mutableStateOf(emptyList<Food>())}
 var offlineError by remember {mutableStateOf<String?>(null)}
 LaunchedEffect(query) {
  offline=emptyList();offlineError=null
  try { offline=vm.offlineCatalog.search(query) }
  catch(e: CancellationException) {throw e}
  catch(e: Exception) {offlineError="Offline catalog unavailable. Try again or use your saved foods."}
 }
 val scope=rememberCoroutineScope();val context=LocalContext.current
 var job by remember {mutableStateOf<Job?>(null)}
 fun runSearch(code: String?=null) {job?.cancel();job=scope.launch {searching=true;messages=emptyList();try {val r=vm.search.search(code?:query,code!=null);remote=r.foods;messages=r.messages;if(code!=null){if(r.foods.isNotEmpty()) select(r.foods.first()) else messages=messages+"Barcode not found. Scan the label or create this food manually."}}finally{searching=false}}}
 val barcode=rememberLauncherForActivityResult(ScanContract()) {r->r.contents?.let {query=it;runSearch(it)}}
 val scannerOptions=ScanOptions().setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES).setPrompt("Scan a UPC or EAN barcode").setBeepEnabled(false).setOrientationLocked(false)
 fun recognize(uri: Uri) {
  searching=true
  val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  try { recognizer.process(InputImage.fromFilePath(context,uri)).addOnSuccessListener {create(LabelParser.parse(it.text));searching=false;recognizer.close()}.addOnFailureListener {messages=listOf("Label could not be read. Try another photo or use manual entry.");searching=false;recognizer.close()} }
  catch(e: Exception){searching=false;recognizer.close();messages=listOf("Unable to open the label image.")}
 }
 var photoUri by rememberSaveable {mutableStateOf<String?>(null)}
 val photo=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {ok->if(ok)photoUri?.let {recognize(Uri.parse(it))}}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {it?.let(::recognize)}
 fun takePhoto() {val file=File(context.cacheDir,"scans/label.jpg");file.parentFile?.mkdirs();val uri=FileProvider.getUriForFile(context,BuildConfig.APPLICATION_ID+".files",file);photoUri=uri.toString();runCatching {photo.launch(uri)}.onFailure {messages=listOf("No camera app available. Choose a label photo instead.")}}
 val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted)takePhoto()else messages=listOf("Camera permission was denied. Choose a photo or enter values manually.")}
 val local=d.foods.filter {it.kind!="ALCOHOL" && (query.isBlank() || CatalogQuery.matchesPersonal("${it.name} ${it.brand} ${it.barcode}",query))}.sortedWith(compareByDescending<Food>{it.lastUsedAt>0}.thenByDescending {it.lastUsedAt}.thenByDescending {it.favorite}.thenBy {it.name})
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Heading("Add to $meal")
  Field("Search foods or brands",query,{job?.cancel();query=it;remote=emptyList();messages=emptyList()})
  Action("Search databases",query.trim().length>=2 && !searching) {runSearch()}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedButton({barcode.launch(scannerOptions)},Modifier.weight(1f)) {Text("Scan barcode")};OutlinedButton({showScan=true},Modifier.weight(1f)) {Text("Scan label")}}
  Row {TextButton({barcodeEntry=true}) {Text("Enter barcode")};TextButton({create(Food())}) {Text("Create food manually")}}
  if(searching)LinearProgressIndicator(Modifier.fillMaxWidth())
  messages.forEach {Hint(it)}
  offlineError?.let {Hint(it)}
  if(BuildConfig.USDA_API_KEY.isBlank())Hint("Offline USDA catalog ready · Online USDA key not configured")
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
   if(local.isNotEmpty())item {Hint("YOUR FOODS · RECENT & FAVORITES")}
   items(local,key={"local-"+it.id}) {FoodResult(it,d) {select(it)}}
   val offlineMatches=offline.filter {f->local.none {it.id==f.id}}
   if(offlineMatches.isNotEmpty()) item {Hint("OFFLINE USDA FOODS · PER 100 G")}
   items(offlineMatches,key={"offline-"+it.id}) {FoodResult(it,d) {select(d.foods.find {f->f.id==it.id}?:it)}}
   if(remote.isNotEmpty()) item {Hint("DATABASE RESULTS · REVIEW VALUES")}
   items(remote.distinctBy {it.id}.filter {f->local.none {it.id==f.id} && offlineMatches.none {it.id==f.id}},key={"remote-"+it.id}) {FoodResult(it,d) {select(d.foods.find {f->f.id==it.id}?:it)}}
   if(!searching && local.isEmpty() && offline.isEmpty() && remote.isEmpty()) item {Hint("Type a food to search the built-in USDA catalog. Search databases adds online results.")}
   item {Hint("Open Food Facts contributors · ODbL. USDA FoodData Central. Values come from the selected provider, never a blend of records.")}
  }
 }
 if(showScan)AlertDialog(onDismissRequest={showScan=false},title={Text("Scan nutrition label")},text={Text("Use a clear photo of the serving size and nutrition panel. You will review and correct all extracted values before saving.")},confirmButton={TextButton({showScan=false;if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)takePhoto()else cameraPermission.launch(Manifest.permission.CAMERA)}) {Text("Take photo")}},dismissButton={TextButton({showScan=false;gallery.launch("image/*")}) {Text("Choose photo")}})
 if(barcodeEntry) {var code by remember {mutableStateOf("")};AlertDialog(onDismissRequest={barcodeEntry=false},title={Text("Barcode lookup")},text={Field("UPC / EAN",code,{code=it.filter(Char::isDigit)},true)},confirmButton={TextButton({query=code;barcodeEntry=false;runSearch(code)},enabled=code.length in 8..14) {Text("Look up")}},dismissButton={TextButton({barcodeEntry=false}) {Text("Cancel")}})}
 DisposableEffect(Unit) {onDispose {job?.cancel()}}
}
@Composable fun FoodResult(f: Food,d: AppData,click: ()->Unit) {Surface(onClick=click,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=androidx.compose.ui.graphics.Color.White) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text((if(f.favorite) "★ " else "")+f.name,style=MaterialTheme.typography.titleMedium);Hint(listOf(f.brand,f.serving,f.source).filter {it.isNotBlank()}.joinToString(" · "));NutritionText(runCatching {Engine.foodNutrition(f,d.foods,d.components)}.getOrDefault(f.nutrition))}}}
@Composable fun Library(d: AppData,select: (Food)->Unit,edit: (Food)->Unit,favorite: (Food)->Unit,delete: (Food)->Unit,duplicate: (Food)->Unit) {
 var query by rememberSaveable {mutableStateOf("")};var kind by rememberSaveable {mutableStateOf("All")};var remove by remember {mutableStateOf<Food?>(null)}
 Page {
  Heading("Your food library");Hint("Saved foods, recipes and meals stay on this device and work offline.")
  Field("Filter your library",query,{query=it});Choice(listOf("All","Foods","Recipes","Saved meals","Alcohol"),kind) {kind=it}
  Action("+ Create ${when(kind){"Recipes"->"recipe";"Saved meals"->"saved meal";else->"food"}}") {edit(Food(kind=when(kind){"Recipes"->"RECIPE";"Saved meals"->"SAVED_MEAL";else->"FOOD"}))}
  d.foods.filter {it.name.contains(query,true) && when(kind){"Foods"->it.kind=="FOOD";"Recipes"->it.kind=="RECIPE";"Saved meals"->it.kind=="SAVED_MEAL";"Alcohol"->it.kind=="ALCOHOL";else->true}}.forEach {f->Panel {
   Heading(f.name);Hint("${f.kind.replace('_',' ').lowercase()} · ${f.source}");NutritionText(runCatching {Engine.foodNutrition(f,d.foods,d.components)}.getOrDefault(f.nutrition))
   Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton({edit(f)}) {Text("Edit")};TextButton({favorite(f)}) {Text(if(f.favorite) "★ Favorite" else "☆ Favorite")};TextButton({duplicate(f)}) {Text("Duplicate")};TextButton({remove=f}) {Text("Delete",color=Red)}}
  }}
  if(d.foods.isEmpty())Hint("Foods are saved here automatically when you log them.")
 }
 remove?.let {f->AlertDialog(onDismissRequest={remove=null},title={Text("Delete ${f.name}?")},text={Text("Historical food logs retain their saved nutrition values.")},confirmButton={TextButton({delete(f);remove=null}) {Text("Delete")}},dismissButton={TextButton({remove=null}) {Text("Cancel")}})}
}
@Composable fun FoodEditor(original: Food,d: AppData,dismiss: ()->Unit,save: (Food,List<Component>)->Unit) {
 var name by rememberSaveable {mutableStateOf(original.name)};var brand by rememberSaveable {mutableStateOf(original.brand)};var kind by rememberSaveable {mutableStateOf(original.kind)};var serving by rememberSaveable {mutableStateOf(original.serving)};var grams by rememberSaveable {mutableStateOf(original.grams?.toString()?:"")};var yield by rememberSaveable {mutableStateOf(original.yieldServings.toString())}
 var calories by rememberSaveable {mutableStateOf(original.nutrition.calories.toString())};var protein by rememberSaveable {mutableStateOf(original.nutrition.protein.toString())};var carbs by rememberSaveable {mutableStateOf(original.nutrition.carbs.toString())};var fat by rememberSaveable {mutableStateOf(original.nutrition.fat.toString())};var fiber by rememberSaveable {mutableStateOf(original.nutrition.fiber.toString())};var sugar by rememberSaveable {mutableStateOf(original.nutrition.sugar.toString())};var sodium by rememberSaveable {mutableStateOf(original.nutrition.sodium.toString())};var notes by rememberSaveable {mutableStateOf(original.notes)}
 var parts by remember {mutableStateOf(d.components.filter {it.parentId==original.id})};var ingredient by remember {mutableStateOf(false)};var ingredientQuery by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
 val compound=kind in listOf("RECIPE","SAVED_MEAL")
 androidx.compose.ui.window.Dialog(onDismissRequest=dismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
  Surface(Modifier.fillMaxSize().systemBarsPadding(),color=Mist) {Column {
   Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {TextButton(dismiss) {Text("Cancel")};Text("Food editor",Modifier.padding(12.dp),style=MaterialTheme.typography.titleMedium)}
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    if(original.source=="Nutrition Label Scan")Hint("Review required: OCR values may be missing or incorrect. Verify the panel, serving size and every nutrient.")
    Choice(if(original.kind=="ALCOHOL")listOf("ALCOHOL")else listOf("FOOD","RECIPE","SAVED_MEAL"),kind) {kind=it}
    Field("Name",name,{name=it});Field("Brand (optional)",brand,{brand=it});Field("Base serving description",serving,{serving=it});Field("Grams per base serving (optional)",grams,{grams=it},true)
    if(compound) {
     if(kind=="RECIPE")Field("Number of servings produced",yield,{yield=it},true)
     Hint("Ingredient quantities are multiples of each ingredient's base serving.")
     parts.forEach {p->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {Column(Modifier.weight(1f)) {Text(d.foods.find {it.id==p.foodId}?.name?:"Missing ingredient");var value by remember(p.id) {mutableStateOf(p.quantity.toString())};Field("Servings",value,{value=it;val q=it.toDoubleOrNull();parts=parts.map {c->if(c.id==p.id)c.copy(quantity=q?:0.0)else c}},true)};TextButton({parts=parts.filter {it.id!=p.id}}) {Text("Remove")}}}
     OutlinedButton({ingredient=true}) {Text("+ Add ingredient")}
     val f=original.copy(kind=kind,yieldServings=if(kind=="SAVED_MEAL")1.0 else yield.toDoubleOrNull()?:1.0)
     runCatching {Engine.foodNutrition(f,d.foods.filter {it.id!=f.id}+f,d.components.filter {it.parentId!=f.id}+parts)}.getOrNull()?.let {NutritionText(it)}
    } else {
     Field("Calories per serving",calories,{calories=it},true);Field("Protein (g)",protein,{protein=it},true);Field("Carbohydrates (g)",carbs,{carbs=it},true);Field("Fat (g)",fat,{fat=it},true);Field("Fiber (g)",fiber,{fiber=it},true);Field("Sugar (g)",sugar,{sugar=it},true);Field("Sodium (mg)",sodium,{sodium=it},true)
    }
    Field("Notes",notes,{notes=it});Hint("Source: ${original.source}");error?.let {Text(it,color=Red)}
    Action("Save food") {
     runCatching {
      require(name.isNotBlank() && serving.isNotBlank()) {"Enter a name and serving."}
      val g=if(grams.isBlank())null else grams.toDouble().also {require(it.isFinite() && it>0)}
      val n=if(compound)Nutrition()else Nutrition(calories.toDouble(),protein.toDouble(),carbs.toDouble(),fat.toDouble(),fiber.toDouble(),sugar.toDouble(),sodium.toDouble(),original.nutrition.ethanol)
      require(n.valid()) {"Nutrition values must be nonnegative numbers."}
      val y=if(kind=="RECIPE")yield.toDouble()else 1.0;require(y.isFinite() && y>0)
      if(compound)require(parts.isNotEmpty() && parts.all {it.quantity.isFinite() && it.quantity>0}) {"Add ingredients with positive quantities."}
      save(original.copy(name=name.trim(),brand=brand.trim(),kind=kind,serving=serving,grams=g,nutrition=n,yieldServings=y,notes=notes),if(compound)parts else emptyList())
     }.onFailure {error=it.message?:"Check the numeric values."}
    }
   }
  }}
 }
 if(ingredient)AlertDialog(onDismissRequest={ingredient=false},title={Text("Choose ingredient")},text={Column {Field("Search library",ingredientQuery,{ingredientQuery=it});LazyColumn(Modifier.heightIn(max=340.dp)) {items(d.foods.filter {it.id!=original.id && it.kind in listOf("FOOD","RECIPE") && it.name.contains(ingredientQuery,true)}) {f->TextButton({parts=parts+Component(parentId=original.id,foodId=f.id,quantity=1.0);ingredient=false}) {Text(f.name)}}}}},confirmButton={TextButton({ingredient=false}) {Text("Done")}})
}
