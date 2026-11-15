package com.orienteer.app.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orienteer.app.domain.CoursePointsCatalog
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.ui.components.OrienteerTopBar
import com.orienteer.app.ui.theme.OrienteerSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoriteSets: List<CoursePointsSet>,
    onApplyFavorite: (CoursePointsSet) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRenameFavorite: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var pendingRemove by remember { mutableStateOf<CoursePointsSet?>(null) }
    var pendingRename by remember { mutableStateOf<CoursePointsSet?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OrienteerTopBar(
                title = "Favorites",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Text(
                        text = "${favoriteSets.size}/${CoursePointsCatalog.MAX_FAVORITES}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = OrienteerSpacing.sm)
                    )
                }
            )
        }
    ) { padding ->
        if (favoriteSets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "None yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = OrienteerSpacing.md),
                contentPadding = PaddingValues(
                    top = OrienteerSpacing.xs,
                    bottom = OrienteerSpacing.lg
                )
            ) {
                items(favoriteSets, key = { it.id }) { set ->
                    FavoriteRow(
                        set = set,
                        onUse = {
                            onApplyFavorite(set)
                            onNavigateBack()
                        },
                        onRename = {
                            pendingRename = set
                            renameText = set.displayName ?: CoursePointsCatalog.autoLabel(set)
                        },
                        onRemove = { pendingRemove = set }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }

    pendingRemove?.let { set ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove favorite?") },
            text = {
                Text("Remove \"${CoursePointsCatalog.autoLabel(set)}\" from favorites?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFavorite(set.id)
                        pendingRemove = null
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingRename?.let { set ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename favorite") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameFavorite(set.id, renameText)
                        pendingRename = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FavoriteRow(
    set: CoursePointsSet,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OrienteerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = CoursePointsCatalog.autoLabel(set),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(
            onClick = onUse,
            contentPadding = PaddingValues(horizontal = OrienteerSpacing.xs)
        ) {
            Text(
                "Use",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(
            onClick = onRename,
            contentPadding = PaddingValues(horizontal = OrienteerSpacing.xs)
        ) {
            Text(
                "Rename",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(
            onClick = onRemove,
            contentPadding = PaddingValues(horizontal = OrienteerSpacing.xs)
        ) {
            Text(
                "Remove",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
