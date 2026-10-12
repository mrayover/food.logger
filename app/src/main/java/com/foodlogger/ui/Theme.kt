package com.foodlogger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.foodlogger.R
import com.foodlogger.domain.*
import java.util.Locale

val Teal=Color(0xFF104862)
val Navy=Color(0xFF042433)
val Mist=Color(0xFFF4F8F9)
val Green=Color(0xFF357659)
val Orange=Color(0xFFAA641D)
val Red=Color(0xFFB13F47)
val Lato=FontFamily(Font(R.font.lato_regular,FontWeight.Normal),Font(R.font.lato_black,FontWeight.Black),Font(R.font.lato_black,FontWeight.Bold))
@Composable fun FoodTheme(content: @Composable ()->Unit) {
 val base=Typography()
 MaterialTheme(colorScheme=lightColorScheme(primary=Teal,onPrimary=Color.White,secondary=Green,background=Mist,surface=Color.White,onSurface=Navy,onBackground=Navy,surfaceVariant=Color(0xFFE4EDF0)),
 typography=Typography(headlineLarge=base.headlineLarge.copy(fontFamily=Lato,fontWeight=FontWeight.Black),headlineMedium=base.headlineMedium.copy(fontFamily=Lato,fontWeight=FontWeight.Black),titleLarge=base.titleLarge.copy(fontFamily=Lato,fontWeight=FontWeight.Black),titleMedium=base.titleMedium.copy(fontFamily=Lato,fontWeight=FontWeight.Bold),bodyLarge=base.bodyLarge.copy(fontFamily=Lato),bodyMedium=base.bodyMedium.copy(fontFamily=Lato),bodySmall=base.bodySmall.copy(fontFamily=Lato),labelLarge=base.labelLarge.copy(fontFamily=Lato)),content=content)
}
fun Double.f(digits: Int=0)=String.format(Locale.US,"%.${digits}f",this)
fun Nutrition.line()="${calories.f()} kcal  ·  P ${protein.f(1)}  C ${carbs.f(1)}  F ${fat.f(1)} g"
@Composable fun Page(content: @Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content) }
@Composable fun Panel(content: @Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) } }
@Composable fun Heading(text: String) { Text(text,style=MaterialTheme.typography.titleLarge) }
@Composable fun Hint(text: String) { Text(text,style=MaterialTheme.typography.bodyMedium,color=Color(0xFF526B76)) }
@Composable fun Action(text: String,enabled: Boolean=true,onClick: ()->Unit) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(16.dp)) { Text(text) } }
@Composable fun Field(label: String,value: String,onChange: (String)->Unit,numeric: Boolean=false,modifier: Modifier=Modifier) { OutlinedTextField(value,onChange,modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(14.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text)) }
@Composable fun Choice(options: List<String>,selected: String,onSelect: (String)->Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { options.forEach { FilterChip(selected==it,{onSelect(it)},label={Text(it)},modifier=Modifier.heightIn(min=48.dp)) } } }
@Composable fun NutritionText(n: Nutrition) { Text(n.line(),style=MaterialTheme.typography.bodyMedium) }
