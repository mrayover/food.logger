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
 private val fields="code,product_name,brands,serving_size,serving_quantity,nutriments"
 override suspend fun search(query: String): List<Food> {
  val r=network.get("https://world.openfoodfacts.org/cgi/search.pl?search_terms=${enc(query)}&search_simple=1&action=process&json=1&page_size=20&fields=$fields")
  val a=r.optJSONArray("products") ?: return emptyList()
  return (0 until a.length()).mapNotNull { normalize(a.getJSONObject(it)) }
 }
 override suspend fun barcode(code: String): Food? = network.get("https://world.openfoodfacts.org/api/v2/product/${enc(code)}?fields=$fields").optJSONObject("product")?.let(::normalize)
 fun normalize(p: JSONObject): Food? {
  val n=p.optJSONObject("nutriments") ?: return null
  val title=p.optString("product_name").trim();if(title.isBlank())return null
  val keys=listOf("energy-kcal_100g","proteins_100g","carbohydrates_100g","fat_100g")
  if(!keys.all { n.has(it) }) return null
  fun value(k: String)=n.optDouble(k,0.0).coerceAtLeast(0.0)
  val nutrition=Nutrition(value("energy-kcal_100g"),value("proteins_100g"),value("carbohydrates_100g"),value("fat_100g"),value("fiber_100g"),value("sugars_100g"),value("sodium_100g")*1000)
  if(!nutrition.valid())return null
  // OFF per-100g fields may refer to 100 ml for liquids. Never assume density.
  val serving=p.optString("serving_size")
  val ml=Regex("\\bml\\b",RegexOption.IGNORE_CASE).containsMatchIn(serving)
  val grams=if(ml) null else 100.0
  val code=p.optString("code")
  return Food(id=java.util.UUID.nameUUIDFromBytes("off-$code".toByteArray()).toString(),name=title,brand=p.optString("brands"),barcode=code,source=name,externalId=code,serving=if(ml) "100 ml" else "100 g",servingQuantity=100.0,servingUnit=if(ml) "ml" else "g",grams=grams,nutrition=nutrition,notes="Open Food Facts (ODbL). Review product values before logging.")
 }
}
class Usda(private val key: String,private val network: Network=Network()): FoodProvider {
 override val name="USDA FoodData Central"
 override suspend fun search(query: String): List<Food> {
  if(key.isBlank()) return emptyList()
  val a=network.get("https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${enc(key)}&query=${enc(query)}&pageSize=20").optJSONArray("foods") ?: return emptyList()
  return (0 until a.length()).mapNotNull { normalize(a.getJSONObject(it)) }
 }
 override suspend fun barcode(code: String)=search(code).firstOrNull { it.barcode.trimStart('0')==code.trimStart('0') }
 fun normalize(p: JSONObject): Food? {
  val a=p.optJSONArray("foodNutrients") ?: return null
  fun nutrient(id: Int): Double? = (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optInt("nutrientId")==id }?.optDouble("value")?.takeIf { it.isFinite() && it>=0 }
  val kcal=nutrient(1008) ?: nutrient(2048) ?: nutrient(2047) ?: return null
  val n=Nutrition(kcal,nutrient(1003)?:return null,nutrient(1005)?:return null,nutrient(1004)?:return null,nutrient(1079)?:0.0,nutrient(2000)?:0.0,nutrient(1093)?:0.0)
  val id=p.optString("fdcId");return Food(id=java.util.UUID.nameUUIDFromBytes("usda-$id".toByteArray()).toString(),name=p.optString("description"),brand=p.optString("brandOwner"),barcode=p.optString("gtinUpc"),source=name,externalId=id,serving="100 g",servingQuantity=100.0,servingUnit="g",grams=100.0,nutrition=n)
 }
}
data class SearchResult(val foods: List<Food>,val messages: List<String>)
class FoodSearch(private val providers: List<FoodProvider>) {
 suspend fun search(query: String,barcode: Boolean=false): SearchResult=supervisorScope {
  val results=providers.map { p->async { try { (if(barcode) listOfNotNull(p.barcode(query)) else p.search(query)) to null } catch(e: CancellationException){throw e} catch(e: Exception) { emptyList<Food>() to "${p.name}: ${e.message ?: "Could not connect"}" } } }.awaitAll()
  SearchResult(results.flatMap { it.first },results.mapNotNull { it.second })
 }
}
object LabelParser {
 fun parse(text: String): Food {
  fun read(label: String): Double=Regex("(?im)^\\s*$label\\s*[:]?\\s*(\\d+(?:[.,]\\d+)?)").find(text)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: 0.0
  val grams=Regex("(?i)serving size[^\\n]*?(\\d+(?:\\.\\d+)?)\\s*g\\b").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
  return Food(source="Nutrition Label Scan",serving=grams?.let { "$it g" }?:"serving",grams=grams,nutrition=Nutrition(read("calories"),read("protein"),read("total carbohydrate(?:s)?"),read("total fat"),read("dietary fiber"),read("total sugars"),read("sodium")),notes="Review all values and serving size. OCR may miss or misread fields.\n$text")
 }
}
