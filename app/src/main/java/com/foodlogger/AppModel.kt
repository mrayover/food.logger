package com.foodlogger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foodlogger.data.*
import com.foodlogger.domain.*
import com.foodlogger.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppModel(app: Application): AndroidViewModel(app) {
 val repository=Repository(FoodDatabase.open(app))
 val data=repository.data.stateIn(viewModelScope,SharingStarted.Eagerly,AppData())
 val loaded=MutableStateFlow(false)
 val message=MutableStateFlow<String?>(null)
 val busy=MutableStateFlow(false)
 val offlineCatalog=OfflineCatalog(app)
 override fun onCleared() { offlineCatalog.close(); super.onCleared() }
 val search=FoodSearch(listOf(OpenFoodFacts(),Usda(BuildConfig.USDA_API_KEY)))
 init { viewModelScope.launch { repository.data.first(); loaded.value=true } }
 fun work(success: ()->Unit = {},block: suspend ()->Unit) { if(busy.value)return;viewModelScope.launch { busy.value=true;try { block();success() } catch(e: Exception) { message.value=e.message ?: "The operation could not be completed." } finally { busy.value=false } } }
}
