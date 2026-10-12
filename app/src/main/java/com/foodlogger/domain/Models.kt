package com.foodlogger.domain

import androidx.room.*
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

fun uid() = UUID.randomUUID().toString()
fun now() = System.currentTimeMillis()
fun today() = LocalDate.now().toString()
@Serializable data class Nutrition(val calories: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0, val fiber: Double = 0.0, val sugar: Double = 0.0, val sodium: Double = 0.0, val ethanol: Double = 0.0) {
 operator fun times(x: Double) = Nutrition(calories*x,protein*x,carbs*x,fat*x,fiber*x,sugar*x,sodium*x,ethanol*x)
 operator fun plus(n: Nutrition) = Nutrition(calories+n.calories,protein+n.protein,carbs+n.carbs,fat+n.fat,fiber+n.fiber,sugar+n.sugar,sodium+n.sodium,ethanol+n.ethanol)
 fun valid() = listOf(calories,protein,carbs,fat,fiber,sugar,sodium,ethanol).all { it.isFinite() && it >= 0 }
}
@Serializable @Entity(tableName="foods") data class Food(
 @PrimaryKey val id: String = uid(), val name: String = "", val brand: String = "", val barcode: String = "",
 val source: String = "Manual", val externalId: String = "", val serving: String = "serving", val servingQuantity: Double = 1.0,
 val servingUnit: String = "serving", val grams: Double? = null, @Embedded val nutrition: Nutrition = Nutrition(),
 val kind: String = "FOOD", val yieldServings: Double = 1.0, val notes: String = "", val favorite: Boolean = false,
 val lastUsedAt: Long = 0, val useCount: Int = 0, val createdAt: Long = now(), val updatedAt: Long = now(),
 val demo: Boolean = false, @ColumnInfo(defaultValue="1.0") val lastQuantity: Double = 1.0
)
@Serializable @Entity(tableName="components",indices=[Index("parentId"),Index("foodId")]) data class Component(@PrimaryKey val id: String = uid(),val parentId: String,val foodId: String,val quantity: Double)
@Serializable @Entity(tableName="entries",indices=[Index("date"),Index("foodId")]) data class LogEntry(
 @PrimaryKey val id: String = uid(), val date: String = today(), val meal: String = "Breakfast", val foodId: String? = null,
 val name: String, val serving: String, val grams: Double? = null, val quantity: Double = 1.0,
 @Embedded val nutrition: Nutrition, val source: String = "Manual", val externalId: String = "", val alcoholCategory: String = "",
 val volumeMl: Double = 0.0, val abv: Double = 0.0, val createdAt: Long = now(), val demo: Boolean = false
)
@Serializable @Entity(tableName="days") data class DayLog(@PrimaryKey val date: String, val complete: Boolean = false, val demo: Boolean = false)
@Serializable @Entity(tableName="weights",indices=[Index("date")]) data class Weight(@PrimaryKey val id: String = uid(), val date: String = today(), val pounds: Double, val createdAt: Long = now(), val demo: Boolean = false)
@Serializable @Entity(tableName="targets",indices=[Index("effectiveDate")]) data class Target(
 @PrimaryKey val id: String = uid(), val effectiveDate: String = today(), val calories: Double = 2200.0, val protein: Double = 150.0,
 val carbs: Double = 250.0, val fat: Double = 67.0, val goalPounds: Double = 230.0, val weeklyRate: Double = -1.0,
 val alcoholAllowance: Double = 0.0, val createdAt: Long = now(), val demo: Boolean = false
)
@Serializable @Entity(tableName="settings") data class Settings(@PrimaryKey val id: Int = 1, val weightUnit: String = "lb", val foodUnit: String = "g", val checkDay: Int = 7, val calorieTolerance: Double = .05, val proteinTolerance: Double = .10, val alcoholOrangeExtra: Double = 1.0, val alcoholRedExtra: Double = 2.0)
@Serializable data class DayStatus(val date: String,val complete: Boolean,val calories: Int,val protein: Int,val alcohol: Int,val nutrition: Nutrition, val target: Target)
@Serializable data class Analysis(val days: List<DayStatus>, val trend: Double?, val usable: Int, val calorieAdherence: Double, val proteinAdherence: Double, val alcoholAdherence: Double, val oldTarget: Target, val proposed: Target, val reason: String)
@Serializable @Entity(tableName="checkins",indices=[Index("date")]) data class CheckIn(
 @PrimaryKey val id: String = uid(), val date: String = today(), val weight: Double, val hunger: Int, val energy: Int, val training: Int,
 val notes: String = "", val excludedDates: String = "", val analysisJson: String, val originalAnalysisJson: String = analysisJson,
 val decision: String, val chosenTargetJson: String, val createdAt: Long = now(), val lastEditedAt: Long? = null, val demo: Boolean = false
)
@Serializable data class Backup(val schemaVersion: Int = 1,val app: String = "Food.Logger",val exportedAt: Long = now(),val foods: List<Food>,val components: List<Component>,val entries: List<LogEntry>,val days: List<DayLog>,val weights: List<Weight>,val targets: List<Target>,val checkins: List<CheckIn>,val settings: Settings)
data class AppData(val foods: List<Food> = emptyList(),val components: List<Component> = emptyList(),val entries: List<LogEntry> = emptyList(),val days: List<DayLog> = emptyList(),val weights: List<Weight> = emptyList(),val targets: List<Target> = emptyList(),val checkins: List<CheckIn> = emptyList(),val settings: Settings = Settings())
val meals = listOf("Breakfast","Lunch","Dinner","Snacks","Alcohol")
