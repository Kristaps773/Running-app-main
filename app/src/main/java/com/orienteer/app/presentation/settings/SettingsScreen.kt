package com.orienteer.app.presentation.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.orienteer.app.ui.icons.OrienteerIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orienteer.app.BuildConfig
import com.orienteer.app.R
import com.orienteer.app.data.local.AppThemeMode
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.RouteGenerationPreset
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.ui.theme.OrienteerSpacing
import io.sentry.Sentry
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val r = state.routeRatios
    val m = state.mapDisplay
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showOmapLegend by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    LaunchedEffect(Unit) {
        viewModel.backupMessages.collectLatest { message ->
            val text = when (message) {
                is BackupUserMessage.SuccessRes -> context.getString(message.messageRes)
                is BackupUserMessage.ErrorRes -> context.getString(message.messageRes)
                is BackupUserMessage.ErrorText -> message.text
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri = null
                        viewModel.importBackup(uri)
                    },
                    enabled = !state.backupBusy
                ) {
                    Text(stringResource(R.string.backup_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.backup_import_cancel))
                }
            }
        )
    }

    if (showOmapLegend) {
        AlertDialog(
            onDismissRequest = { showOmapLegend = false },
            title = { Text(stringResource(R.string.settings_omap_legend_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_omap_legend_white))
                    Text(stringResource(R.string.settings_omap_legend_green))
                    Text(stringResource(R.string.settings_omap_legend_yellow))
                    Text(stringResource(R.string.settings_omap_legend_brown))
                    Text(stringResource(R.string.settings_omap_legend_black))
                    Text(stringResource(R.string.settings_omap_legend_purple))
                    Text(
                        text = stringResource(R.string.settings_omap_legend_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOmapLegend = false }) {
                    Text(stringResource(R.string.settings_omap_legend_close))
                }
            }
        )
    }

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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OrienteerSpacing.md, vertical = OrienteerSpacing.xs)
        ) {
            Text(
                text = stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(OrienteerSpacing.sm))
            ThemeModeChips(
                selected = themeMode,
                onSelect = viewModel::setThemeMode
            )

            Spacer(Modifier.height(OrienteerSpacing.lg))
            HorizontalDivider()
            Spacer(Modifier.height(OrienteerSpacing.md))

            Text(
                text = stringResource(R.string.settings_section_route_generation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_route_preset_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            RouteGenerationPresetChips(
                selected = RouteGenerationPreset.matching(r),
                onSelect = viewModel::setRouteGenerationPreset
            )
            Text(
                text = when (RouteGenerationPreset.matching(r)) {
                    RouteGenerationPreset.PRECISE ->
                        stringResource(R.string.settings_route_preset_precise_sub)
                    RouteGenerationPreset.BALANCED ->
                        stringResource(R.string.settings_route_preset_balanced_sub)
                    RouteGenerationPreset.FLEXIBLE ->
                        stringResource(R.string.settings_route_preset_flexible_sub)
                    null -> stringResource(R.string.settings_route_preset_custom)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(R.string.settings_route_preset_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))
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
                color = MaterialTheme.colorScheme.onSurface
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
                title = stringResource(R.string.settings_show_you_are_here),
                subtitle = stringResource(R.string.settings_show_you_are_here_sub),
                checked = m.showYouAreHereDuringRun,
                onCheckedChange = { viewModel.setShowYouAreHereDuringRun(it) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_include_pdf_legends),
                subtitle = stringResource(R.string.settings_include_pdf_legends_sub),
                checked = m.includePdfLegends,
                onCheckedChange = { viewModel.setIncludePdfLegends(it) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_iof_overprint),
                subtitle = stringResource(R.string.settings_iof_overprint_sub),
                checked = m.useIofCourseOverprint,
                onCheckedChange = { viewModel.setUseIofCourseOverprint(it) }
            )
            Text(
                text = stringResource(R.string.settings_map_style_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_map_style_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            BasemapStyleChips(
                selected = m.basemapStyle,
                onSelect = viewModel::setBasemapStyle
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_omap_legend)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_omap_legend_sub),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { showOmapLegend = true }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_section_data),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_data_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_backup)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_export_backup_sub),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !state.backupBusy) { viewModel.exportBackup() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import_backup)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_import_backup_sub),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !state.backupBusy) {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            PrivacyPolicyRow()

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    Sentry.captureException(
                        RuntimeException("OrienteerRun debug Sentry test event")
                    )
                }) {
                    Text(stringResource(R.string.settings_sentry_test))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacyPolicyRow() {
    val context = LocalContext.current
    val url = BuildConfig.PRIVACY_POLICY_URL
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
        supportingContent = {
            Text(
                stringResource(R.string.settings_privacy_policy_sub),
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Icon(
                OrienteerIcons.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(enabled = url.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
    )
}

@Composable
private fun BasemapStyleChips(
    selected: BasemapStyle,
    onSelect: (BasemapStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                BasemapStyle.STANDARD,
                BasemapStyle.DARK,
                BasemapStyle.PSEUDO_O
            ).forEach { style ->
                BasemapStyleChip(style, selected, onSelect)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                BasemapStyle.STREET_O,
                BasemapStyle.OPEN_TOPO,
                BasemapStyle.CYCLOSM
            ).forEach { style ->
                BasemapStyleChip(style, selected, onSelect)
            }
        }
    }
}

@Composable
private fun BasemapStyleChip(
    style: BasemapStyle,
    selected: BasemapStyle,
    onSelect: (BasemapStyle) -> Unit
) {
    FilterChip(
        selected = selected == style,
        onClick = { onSelect(style) },
        label = {
            Text(
                when (style) {
                    BasemapStyle.STANDARD -> stringResource(R.string.map_style_standard)
                    BasemapStyle.DARK -> stringResource(R.string.map_style_dark)
                    BasemapStyle.PSEUDO_O -> stringResource(R.string.map_style_pseudo_o)
                    BasemapStyle.STREET_O -> stringResource(R.string.map_style_street_o)
                    BasemapStyle.OPEN_TOPO -> stringResource(R.string.map_style_open_topo)
                    BasemapStyle.CYCLOSM -> stringResource(R.string.map_style_cyclosm)
                }
            )
        }
    )
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
private fun RouteGenerationPresetChips(
    selected: RouteGenerationPreset?,
    onSelect: (RouteGenerationPreset) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RouteGenerationPreset.entries.forEach { preset ->
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                label = {
                    Text(
                        when (preset) {
                            RouteGenerationPreset.PRECISE ->
                                stringResource(R.string.settings_route_preset_precise)
                            RouteGenerationPreset.BALANCED ->
                                stringResource(R.string.settings_route_preset_balanced)
                            RouteGenerationPreset.FLEXIBLE ->
                                stringResource(R.string.settings_route_preset_flexible)
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
