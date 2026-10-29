package com.foodlogger.providers

import kotlin.math.abs

/** Only explicit metric mass/volume is reliable; household measures imply no density. */
internal data class ServingSize(val quantity: Double, val unit: String) {
 companion object {
  private val measure=Regex("(?i)(?<![\\w.,/−-])([0-9]+(?:[.,][0-9]+)?(?:\\s+[0-9]+/[0-9]+)?|[0-9]+/[0-9]+|[½¼¾⅓⅔⅛⅜⅝⅞])\\s*(grams?|g|millilit(?:er|re)s?|ml)\\b")
  fun hasMeasure(description: String)=measure.containsMatchIn(description)
  private fun number(raw: String): Double? {
   val fractions=mapOf("½" to .5,"¼" to .25,"¾" to .75,"⅓" to 1.0/3,"⅔" to 2.0/3,"⅛" to .125,"⅜" to .375,"⅝" to .625,"⅞" to .875)
   fractions[raw]?.let { return it }
   val parts=raw.trim().split(Regex("\\s+"))
   fun part(s: String): Double? {
    val f=s.split('/')
    return if(f.size==2) f[0].toDoubleOrNull()?.let { n -> f[1].toDoubleOrNull()?.let { d -> n/d } } else s.replace(',','.').toDoubleOrNull()
   }
   return if(parts.size==2) part(parts[0])?.let { a -> part(parts[1])?.let { a+it } } else part(raw)
  }
  fun parse(description: String, declared: Double?=null, declaredUnit: String=""): ServingSize? {
   if(Regex("[0-9]\\s*[x×]\\s*[0-9]",RegexOption.IGNORE_CASE).containsMatchIn(description))return null
   val matches=measure.findAll(description).mapNotNull { m -> number(m.groupValues[1])?.let { ServingSize(it,if(m.groupValues[2].lowercase().startsWith("g")) "g" else "ml") } }.toList()
   if(matches.isEmpty()) {
    // A numeric quantity without an explicit unit is ambiguous.
    val unit=declaredUnit.lowercase()
    val malformedMetric=Regex("(?i)(?<![a-z])(?:g|grams?|ml|millilit(?:er|re)s?)\\b").containsMatchIn(description)
    return if(!malformedMetric && declared!=null && declared.isFinite() && declared>0 && declared<=10000 && unit in listOf("g","ml")) ServingSize(declared,unit) else null
   }
   if(matches.any { !it.quantity.isFinite() || it.quantity<=0 || it.quantity>10000 }) return null
   val first=matches.first()
   if(matches.any { it.unit!=first.unit || abs(it.quantity-first.quantity)>maxOf(.01,first.quantity*.02) }) return null
   if(declared!=null && (!declared.isFinite() || declared<=0 || abs(declared-first.quantity)>maxOf(.01,first.quantity*.02))) return null
   if(declaredUnit.isNotBlank() && declaredUnit.lowercase()!=first.unit) return null
   return first
  }
 }
}
