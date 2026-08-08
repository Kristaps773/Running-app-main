package com.orienteer.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.data.local.AppThemeMode
import com.orienteer.app.data.local.UserPreferencesStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserPreferencesEntryPoint {
    fun userPreferencesStore(): UserPreferencesStore
}

@Composable
fun OrienteerThemeRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = EntryPointAccessors.fromApplication(
        context.applicationContext,
        UserPreferencesEntryPoint::class.java
    ).userPreferencesStore()
    val storedMode by store.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)

    OrienteerTheme(themeMode = storedMode.toUiThemeMode(), content = content)
}
