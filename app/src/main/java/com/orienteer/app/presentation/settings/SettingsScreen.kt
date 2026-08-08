package com.orienteer.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.R
import com.orienteer.app.data.local.AppThemeMode
import com.orienteer.app.data.local.UserPreferencesStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val r = state.routeRatios
    val m = state.mapDisplay

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            ThemeModeChips(
                selected = themeMode,
                onSelect = viewModel::setThemeMode
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_section_route_generation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_air_tolerance_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_air_tolerance_value, r.airDistanceToleranceM),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = r.airDistanceToleranceM.toFloat(),
                onValueChange = { viewModel.setAirDistanceToleranceM(it) },
                valueRange = UserPreferencesStore.MIN_AIR_TOLERANCE_M.toFloat()..UserPreferencesStore.MAX_AIR_TOLERANCE_M.toFloat(),
                steps = 22,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.settings_air_tolerance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_path_ratio_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_route_ratio_value, r.maxPathToAirRatio),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = r.maxPathToAirRatio.toFloat(),
                onValueChange = { viewModel.setMaxPathToAirRatio(it) },
                valueRange = UserPreferencesStore.MIN_RATIO.toFloat()..UserPreferencesStore.MAX_RATIO.toFloat(),
                steps = 13,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.settings_path_ratio_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_section_map),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_air_legs),
                subtitle = stringResource(R.string.settings_show_air_legs_sub),
                checked = m.showAirLegLines,
                onCheckedChange = { viewModel.setShowAirLegLines(it) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_air_labels),
                subtitle = stringResource(R.string.settings_show_air_labels_sub),
                checked = m.showAirLegDistanceLabels,
                onCheckedChange = { viewModel.setShowAirLegDistanceLabels(it) },
                enabled = m.showAirLegLines
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_walking_path),
                subtitle = stringResource(R.string.settings_show_walking_path_sub),
                checked = m.showWalkingPathPolyline,
                onCheckedChange = { viewModel.setShowWalkingPathPolyline(it) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dark_basemap),
                subtitle = stringResource(R.string.settings_dark_basemap_sub),
                checked = m.useDarkBasemap,
                onCheckedChange = { viewModel.setUseDarkBasemap(it) }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeModeChips(
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = {
                    Text(
                        when (mode) {
                            AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
