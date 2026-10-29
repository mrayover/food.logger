package com.foodlogger.providers

import com.foodlogger.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface FoodProvider { val name: String; suspend fun search(query: String): List<Food>; suspend fun barcode(code: String): Food? }
class Network {
 private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
 suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
  client.newCall(Request.Builder().url(url).header("User-Agent","Food.Logger/1.0 (Android; nutrition tracker)").build()).execute().use { response ->
   check(response.isSuccessful) { if(response.code==429) "Search limit reached. Try again shortly." else "Provider unavailable (${response.code})" }
   JSONObject(response.body!!.string())
  }
 }
}
fun enc(s: String)=URLEncoder.encode(s,"UTF-8")
class OpenFoodFacts(private val network: Network = Network()): FoodProvider {
 override val name="Open Food Facts"
 private val fields="code,product_name,brands,serving_size,serving_quantity,serving_quantity_unit,nutriments"
 override suspend fun search(query: String): List<Food> {
  val r=network.get("https://world.openfoodfacts.org/cgi/search.pl?search_terms=${enc(query)}&search_simple=1&action=process&json=1&page_size=20&fields=$fields")
  val a=r.optJSONArray("products") ?: return emptyList()
  return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::normalize) }
 }
 override suspend fun barcode(code: String): Food? = network.get("https://world.openfoodfacts.org/api/v2/product/${enc(code)}?fields=$fields").optJSONObject("product")?.let(::normalize)
 fun normalize(p: JSONObject): Food? {
  val n=p.optJSONObject("nutriments") ?: return null
  val title=p.optString("product_name").trim();if(title.isBlank() || title=="null")return null
  val keys=listOf("energy-kcal_100g","proteins_100g","carbohydrates_100g","fat_100g")
  if(!keys.all { n.optDouble(it,Double.NaN).let { v -> v.isFinite() && v>=0 } }) return null
  fun value(k: String)=n.optDouble(k,0.0).takeIf { it.isFinite() && it>=0 } ?: 0.0
  val nutrition=Nutrition(value("energy-kcal_100g"),value("proteins_100g"),value("carbohydrates_100g"),value("fat_100g"),value("fiber_100g"),value("sugars_100g"),value("sodium_100g")*1000,value("alcohol_100g"))
  if(!nutrition.valid())return null
  // OFF per-100g fields may refer to 100 ml for liquids. Never assume density.
  val serving=p.optString("serving_size").takeUnless { it=="null" } ?: ""
  val declared=if(p.has("serving_quantity") && !p.isNull("serving_quantity")) p.optDouble("serving_quantity",Double.NaN) else null
  val size=ServingSize.parse(serving,declared,p.optString("serving_quantity_unit").takeUnless {it=="null"} ?: "")
  val ml=size?.unit=="ml" || p.optString("serving_quantity_unit").equals("ml",true) || Regex("(?<![A-Za-z])(?:ml|millilit(?:er|re)s?)\\b",RegexOption.IGNORE_CASE).containsMatchIn(serving)
  val grams=if(ml) null else 100.0
  val code=p.optString("code").trim();if(code.isBlank() || code=="null")return null
  val missing=listOf("fiber_100g","sugars_100g","sodium_100g","alcohol_100g").filter {n.optDouble(it,Double.NaN).let {v->!v.isFinite() || v<0}}
  val disclosure=if(missing.isEmpty())"" else "\nMissing/invalid secondary nutrients shown as zero: ${missing.joinToString()}."
  val scaled=nutrition*(size?.quantity?.div(100.0) ?: 1.0)
  if(!scaled.valid())return null
  return Food(id=java.util.UUID.nameUUIDFromBytes("off-$code".toByteArray()).toString(),name=title,brand=p.optString("brands"),barcode=code,source=name,externalId=code,serving=if(size!=null)serving.ifBlank { "${size.quantity} ${size.unit}" } else if(ml) "100 ml" else "100 g",servingQuantity=if(size!=null)1.0 else 100.0,servingUnit=if(size!=null)"serving" else if(ml)"ml" else "g",grams=if(size!=null)size.quantity.takeIf {size.unit=="g"} else grams,nutrition=scaled,notes="Open Food Facts (ODbL). Review product values before logging.\nSource serving: $serving; serving_quantity: ${p.opt("serving_quantity")}; unit: ${p.optString("serving_quantity_unit")}\nNutrition reference: per 100 ${if(ml)"ml" else "g"}.$disclosure" )
 }
}
class Usda(private val key: String,private val network: Network=Network()): FoodProvider {
 override val name="USDA FoodData Central"
 override suspend fun search(query: String): List<Food> {
  if(key.isBlank()) return emptyList()
  val a=network.get("https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${enc(key)}&query=${enc(query)}&pageSize=20").optJSONArray("foods") ?: return emptyList()
  return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::normalize) }
 }
 override suspend fun barcode(code: String)=search(code).firstOrNull { it.barcode.trimStart('0')==code.trimStart('0') }
 fun normalize(p: JSONObject): Food? {
  val a=p.optJSONArray("foodNutrients") ?: return null
  fun nutrient(id: Int): Double? = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optInt("nutrientId")==id }?.optDouble("value")?.takeIf { it.isFinite() && it>=0 }
  val kcal=nutrient(1008) ?: nutrient(2048) ?: nutrient(2047) ?: return null
  val n=Nutrition(kcal,nutrient(1003)?:return null,nutrient(1005)?:return null,nutrient(1004)?:return null,nutrient(1079)?:0.0,nutrient(2000)?:0.0,nutrient(1093)?:0.0)
  val id=p.optString("fdcId");val title=p.optString("description").trim()
  if(id.isBlank() || id=="null" || title.isBlank() || title=="null" || !n.valid())return null
  val quantity=p.optDouble("servingSize",Double.NaN)
  val unit=p.optString("servingSizeUnit").lowercase()
  // USDA search nutrients use a mass reference. Volume alone cannot scale them.
  val size=quantity.takeIf { p.optString("dataType")=="Branded" && unit in listOf("g","grm") && it.isFinite() && it>0 && it<=10000 }
  val label=p.optString("householdServingFullText").takeUnless {it=="null"} ?: ""
  val parsed=ServingSize.parse(label)
  val reliable=size?.takeIf { !ServingSize.hasMeasure(label) || (parsed!=null && parsed.unit=="g" && kotlin.math.abs(parsed.quantity-it)<=maxOf(.01,it*.02)) }
  val scaled=n*(reliable?.div(100.0)?:1.0)
  if(!scaled.valid())return null
  return Food(id=java.util.UUID.nameUUIDFromBytes("usda-$id".toByteArray()).toString(),name=title,brand=p.optString("brandOwner"),barcode=p.optString("gtinUpc"),source=name,externalId=id,serving=if(reliable!=null) "${label.ifBlank { "Serving" }} ($reliable g)" else "100 g",servingQuantity=if(reliable!=null)1.0 else 100.0,servingUnit=if(reliable!=null)"serving" else "g",grams=reliable?:100.0,nutrition=scaled,notes="USDA FoodData Central. Source serving: $label; $quantity $unit. Nutrition reference: per 100 g.")
 }
}
data class SearchResult(val foods: List<Food>,val messages: List<String>)
class FoodSearch(private val providers: List<FoodProvider>) {
 suspend fun search(query: String,barcode: Boolean=false): SearchResult=supervisorScope {
  val results=providers.map { p->async { try { (if(barcode) listOfNotNull(p.barcode(query)) else p.search(query)) to null } catch(e: CancellationException){throw e} catch(e: Exception) { emptyList<Food>() to "${p.name}: ${e.message ?: "Could not connect"}" } } }.awaitAll()
  SearchResult(FoodIdentity.unique(results.flatMap { it.first }),results.mapNotNull { it.second })
 }
}
object LabelParser {
 fun parse(text: String): Food {
  fun read(label: String): Double=Regex("(?im)^\\s*$label\\s*[:]?\\s*(\\d+(?:[.,]\\d+)?)").find(text)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: 0.0
  val grams=Regex("(?i)serving size[^\\n]*?(\\d+(?:\\.\\d+)?)\\s*g\\b").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
  return Food(source="Nutrition Label Scan",serving=grams?.let { "$it g" }?:"serving",grams=grams,nutrition=Nutrition(read("calories"),read("protein"),read("total carbohydrate(?:s)?"),read("total fat"),read("dietary fiber"),read("total sugars"),read("sodium")),notes="Review all values and serving size. OCR may miss or misread fields.\n$text")
 }
}
