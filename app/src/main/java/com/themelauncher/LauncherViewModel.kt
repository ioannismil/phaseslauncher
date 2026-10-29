package com.themelauncher

import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.themelauncher.data.apps.AppInfo
import com.themelauncher.data.apps.AppsRepository
import com.themelauncher.data.apps.filterApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class DrawerState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
)

class LauncherViewModel(private val appsRepository: AppsRepository) : ViewModel() {

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val drawerState: StateFlow<DrawerState> =
        combine(appsRepository.apps, _query) { apps, query ->
            DrawerState(isLoading = apps == null, apps = filterApps(apps.orEmpty(), query))
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerState())

    fun openDrawer() {
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
        _query.value = ""
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    /** The Home button was pressed while we were already in front. */
    fun onHomePressed() = closeDrawer()

    fun launch(app: AppInfo, sourceBounds: Rect?) {
        appsRepository.launch(app, sourceBounds)
    }

    fun openAppInfo(app: AppInfo, sourceBounds: Rect?) = appsRepository.openAppInfo(app, sourceBounds)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LauncherViewModel(this[APPLICATION_KEY]!!.appContainer.appsRepository)
            }
        }
    }
}
