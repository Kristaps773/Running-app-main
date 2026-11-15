package com.orienteer.app.presentation.map

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.ui.theme.OrienteerSpacing

/** Space reserved so attribution never sits under zoom / recenter chrome. */
val MapChromeEndReserve = 88.dp

@Composable
fun MapAttributionBar(
    basemapStyle: BasemapStyle,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 220.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = basemapAttributionLine(basemapStyle),
            modifier = Modifier.padding(
                horizontal = OrienteerSpacing.sm,
                vertical = OrienteerSpacing.xxs
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
