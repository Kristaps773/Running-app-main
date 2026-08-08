package com.orienteer.app.ui.theme

import com.orienteer.app.data.local.AppThemeMode

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

fun ThemeMode.isDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun AppThemeMode.toUiThemeMode(): ThemeMode = when (this) {
    AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
    AppThemeMode.LIGHT -> ThemeMode.LIGHT
    AppThemeMode.DARK -> ThemeMode.DARK
}
