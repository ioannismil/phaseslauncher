package com.themelauncher

import android.app.Application
import android.content.Context
import com.themelauncher.data.apps.AppsRepository
import com.themelauncher.data.apps.IconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThemeLauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container; app-wide singletons live here. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appsRepository = AppsRepository(context, appScope)
    val iconCache = IconCache(context, appsRepository)

    init {
        appScope.launch {
            appsRepository.packageEvents.collect { iconCache.invalidate(it.packageName) }
        }
        // Warm the icon cache in the background so the drawer opens fully drawn.
        appScope.launch {
            appsRepository.apps.filterNotNull().collect { iconCache.preload(it) }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ThemeLauncherApp).container
