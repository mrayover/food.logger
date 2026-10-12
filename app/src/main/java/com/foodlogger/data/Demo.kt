package com.foodlogger.data

import androidx.room.withTransaction
import com.foodlogger.domain.*
import com.foodlogger.domain.Target
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import kotlin.math.sin

suspend fun FoodDatabase.withTransactionCompat(block: suspend ()->Unit)=withTransaction {block()}
object Demo {
 suspend fun populate(repo: Repository,current: AppData) {
  require(current.foods.isEmpty() && current.entries.isEmpty() && current.weights.isEmpty() && current.targets.isEmpty() && current.checkins.isEmpty()) {"Demo data requires an empty database. Export your data before resetting."}
  val oats=Food(name="Demo · Rolled oats",serving="40 g",grams=40.0,nutrition=Nutrition(150.0,5.0,27.0,3.0,4.0),favorite=true,demo=true)
  val yogurt=Food(name="Demo · Greek yogurt",serving="170 g container",grams=170.0,nutrition=Nutrition(100.0,17.0,6.0,0.0),demo=true)
  val bowl=Food(name="Demo · Breakfast bowl",kind="RECIPE",serving="bowl",yieldServings=1.0,demo=true)
  val meal=Food(name="Demo · Morning meal",kind="SAVED_MEAL",demo=true)
  val preset=Food(name="Demo · Beer",kind="ALCOHOL",serving="355 ml",nutrition=Nutrition(150.0,carbs=13.0,ethanol=14.0),notes="Beer|355|5",demo=true)
  val foods=listOf(oats,yogurt,bowl,meal,preset)
  val parts=listOf(Component(parentId=bowl.id,foodId=oats.id,quantity=1.0),Component(parentId=bowl.id,foodId=yogurt.id,quantity=1.0),Component(parentId=meal.id,foodId=bowl.id,quantity=1.0))
  val start=LocalDate.now().minusDays(56);val target=Target(effectiveDate=start.toString(),calories=2350.0,protein=150.0,carbs=260.0,fat=70.0,demo=true);val target2=target.copy(id=uid(),effectiveDate=start.plusDays(28).toString(),calories=2250.0)
  val weights=(0..56).map {i->Weight(date=start.plusDays(i.toLong()).toString(),pounds=260-i*.10+sin(i*1.4)*.9,demo=true)}
  val days=(0..55).map {i->DayLog(start.plusDays(i.toLong()).toString(),i%10!=0,true)}
  val entries=days.flatMapIndexed {i,day->val t=if(i<28)target else target2;val factor=if(i%6==0)1.2 else 1.0;listOf(LogEntry(date=day.date,meal="Breakfast",foodId=bowl.id,name=bowl.name,serving="bowl",nutrition=Nutrition(250.0,22.0,33.0,3.0),demo=true),LogEntry(date=day.date,meal="Dinner",name="Demo · daily food balance",serving="day balance",nutrition=Nutrition(t.calories*factor-250,140.0,220.0,65.0),demo=true)) + if(i%7==0)listOf(LogEntry(date=day.date,meal="Alcohol",name="Demo · Beer",serving="355 ml",nutrition=Nutrition(150.0,carbs=13.0,ethanol=14.0),demo=true))else emptyList()}
  var data=AppData(foods=foods,components=parts,entries=entries,days=days,weights=weights,targets=listOf(target,target2))
  val checks=mutableListOf<CheckIn>()
  (14..49 step 7).forEach {i->val date=start.plusDays(i.toLong()).toString();val w=weights[i].pounds;val a=Engine.analyze(data.copy(checkins=checks.toList()),date,w,3,3,3,emptySet());checks+=CheckIn(date=date,weight=w,hunger=3,energy=3,training=3,notes="Demonstration record",analysisJson=json.encodeToString(a),decision="Kept",chosenTargetJson=json.encodeToString(a.oldTarget),demo=true)}
  data=data.copy(checkins=checks);repo.restore(repo.backup(data))
 }
 suspend fun clear(repo: Repository,d: AppData) {
  val removed=d.foods.filter {it.demo}.map {it.id}.toSet()
  val retained=d.foods.filter {!it.demo}
  require(d.components.none {it.parentId in retained.map {f->f.id} && it.foodId in removed}) {"A personal recipe uses a demo ingredient. Remove that ingredient before clearing demo data."}
  repo.restore(repo.backup(d.copy(foods=retained,components=d.components.filter {it.parentId !in removed},entries=d.entries.filter {!it.demo},days=d.days.filter {!it.demo},weights=d.weights.filter {!it.demo},targets=d.targets.filter {!it.demo},checkins=d.checkins.filter {!it.demo})))
 }
}
