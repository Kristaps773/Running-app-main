package com.orienteer.app.presentation.map

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orienteer.app.R
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerCardShape
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** Hide OSMDroid's stock +/- squares so Compose map chrome owns zoom UX. */
fun MapView.disableBuiltInZoomButtons() {
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
}

private val ZoomButtonSize = 36.dp
private val ZoomIconSize = 18.dp

/**
 * Compact horizontal zoom tray: [+] [−] side by side.
 */
@Composable
fun MapZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = OrienteerCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(ZoomButtonSize)
            ) {
                Icon(
                    OrienteerIcons.Add,
                    contentDescription = stringResource(R.string.map_zoom_in),
                    modifier = Modifier.size(ZoomIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(ZoomButtonSize)
            ) {
                Icon(
                    OrienteerIcons.Remove,
                    contentDescription = stringResource(R.string.map_zoom_out),
                    modifier = Modifier.size(ZoomIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
