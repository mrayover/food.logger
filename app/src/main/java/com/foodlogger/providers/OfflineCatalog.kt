package com.foodlogger.providers

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.foodlogger.domain.Food
import com.foodlogger.domain.Nutrition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream

/** No network dependency; only a bounded result set is materialized. User Room DB is untouched. */
class OfflineCatalog(context: Context) : AutoCloseable {
 private val context = context.applicationContext
 private var database: SQLiteDatabase? = null

 private fun open(): SQLiteDatabase {
  database?.let { return it }
  val target = File(context.noBackupFilesDir, "usda-sr-v1.db")
  if (!target.exists()) {
   val staging = File(target.parentFile, target.name + ".tmp")
   try {
    GZIPInputStream(context.assets.open("usda-sr-v1.db.bin")).use { input ->
     staging.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
    }
    check(staging.renameTo(target)) { "Could not install the offline food catalog" }
   } finally { staging.delete() }
  }
  return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).also { database = it }
 }

 suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
  val match = CatalogQuery.match(query) ?: return@withContext emptyList()
  synchronized(this@OfflineCatalog) {
   val db = open()
   val tokens = CatalogQuery.tokens(query)
   val heads = tokens.flatMap { listOf(it, it + "s") }
   val exactHead = heads.joinToString(" OR ") { "lower(substr(f.name,1,instr(f.name || ',',',')-1)) = ?" }
   val starts = tokens.map { "$it%" }
   val priority = starts.joinToString(" OR ") { "f.name LIKE ? COLLATE NOCASE" }
   db.rawQuery("""SELECT f.* FROM foods f JOIN food_search s ON s.docid=f.fdc_id
    WHERE food_search MATCH ? ORDER BY CASE WHEN $exactHead THEN 0 WHEN $priority THEN 1 ELSE 2 END,
    length(f.name), f.name COLLATE NOCASE, f.fdc_id LIMIT 50""", (listOf(match) + heads + starts).toTypedArray()).use { cursor ->
    buildList { while(cursor.moveToNext()) add(food(cursor, db)) }
   }
  }
 }

 private fun food(c: Cursor, db: SQLiteDatabase): Food {
  fun text(column: String) = c.getString(c.getColumnIndexOrThrow(column))
  fun value(column: String) = c.getDouble(c.getColumnIndexOrThrow(column))
  val fdc = text("fdc_id")
  val optional = listOf("fiber", "sugar", "sodium", "ethanol")
  val missing = optional.filter { c.isNull(c.getColumnIndexOrThrow(it)) }
  val portions = db.rawQuery("""SELECT amount, modifier, portion_description, gram_weight
   FROM source_food_portion WHERE fdc_id=? ORDER BY CAST(seq_num AS INTEGER)""", arrayOf(fdc)).use { p ->
   buildList { while(p.moveToNext()) add("${p.getString(0)} ${p.getString(1)} ${p.getString(2)} = ${p.getString(3)} g".trim()) }
  }
  return Food(
   // Match the existing USDA provider identity, so personal edits win over both sources.
   id=UUID.nameUUIDFromBytes("usda-$fdc".toByteArray(Charsets.UTF_8)).toString(),
   name=text("name"), source="USDA SR Legacy (offline)", externalId=fdc,
   serving="100 g", servingQuantity=100.0, servingUnit="g", grams=100.0,
   nutrition=Nutrition(value("calories"), value("protein"), value("carbs"), value("fat"),
    value("fiber"), value("sugar"), value("sodium"), value("ethanol")),
   notes=buildString {
    append("USDA FoodData Central · SR Legacy April 2018 · CC0. FDC $fdc; NDB ${text("ndb_number")}. Values per 100 g edible portion. ")
    append("Original nutrient IDs, units, amounts and derivation metadata are retained in the bundled catalog. ")
    if(missing.isNotEmpty()) append("Not reported: ${missing.joinToString()}. These fields contribute 0 to totals; zero does not mean measured absence. ")
    if(portions.isNotEmpty()) append("USDA portions (use the stated gram weight): ${portions.joinToString("; ")}")
   }
  )
 }

 @Synchronized override fun close() { database?.close(); database = null }
}

object CatalogQuery {
 /** Prefix tokens allow egg/eggs and banana/bananas; punctuation cannot inject FTS syntax. */
 fun tokens(query: String): List<String> = Regex("[a-z0-9]+").findAll(query.take(200).lowercase(java.util.Locale.ROOT))
   .map { it.value }.take(8).toList()
 fun matchesPersonal(text: String, query: String): Boolean =
  query.isBlank() || tokens(query).let { it.isNotEmpty() && it.all { token -> text.contains(token, ignoreCase=true) } }
 fun match(query: String): String? {
  val tokens = tokens(query)
  if(tokens.isEmpty() || tokens.sumOf { it.length } < 2) return null
  // Whitespace is implicit AND in both standard and enhanced Android FTS4 parsers.
  return tokens.joinToString(" ") { "\"$it*\"" }
 }
}
