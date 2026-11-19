package com.orienteer.app.presentation.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orienteer.app.R
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerCardShape

@Composable
fun basemapStyleLabel(style: BasemapStyle): String = when (style) {
    BasemapStyle.STANDARD -> stringResource(R.string.map_style_standard)
    BasemapStyle.DARK -> stringResource(R.string.map_style_dark)
    BasemapStyle.PSEUDO_O -> stringResource(R.string.map_style_pseudo_o)
    BasemapStyle.OPEN_TOPO -> stringResource(R.string.map_style_open_topo)
    BasemapStyle.CYCLOSM -> stringResource(R.string.map_style_cyclosm)
}

/** Layers icon that opens a basemap style dropdown. */
@Composable
fun BasemapStyleMenuButton(
    selected: BasemapStyle,
    onSelect: (BasemapStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val styleTitle = stringResource(R.string.settings_map_style_title)

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = OrienteerIcons.Layers,
                contentDescription = styleTitle,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        BasemapStyleDropdownMenu(
            expanded = expanded,
            selected = selected,
            onDismiss = { expanded = false },
            onSelect = { style ->
                expanded = false
                onSelect(style)
            }
        )
    }
}

@Composable
fun BasemapStyleDropdownMenu(
    expanded: Boolean,
    selected: BasemapStyle,
    onDismiss: () -> Unit,
    onSelect: (BasemapStyle) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 148.dp),
        shape = OrienteerCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        BasemapStyle.entries.forEach { style ->
            val isSelected = style == selected
            DropdownMenuItem(
                text = {
                    Text(
                        text = basemapStyleLabel(style),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                },
                onClick = { onSelect(style) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                trailingIcon = {
                    // Fixed slot so selection does not resize the menu.
                    Box(
                        modifier = Modifier.size(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    }
}
