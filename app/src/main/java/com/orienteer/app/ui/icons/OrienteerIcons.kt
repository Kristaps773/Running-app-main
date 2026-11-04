package com.orienteer.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small set of Material-style icons used by the app.
 * Replaces material-icons-extended so release DEX stays small under R8.
 */
object OrienteerIcons {
    val ChevronRight: ImageVector by lazy {
        materialIcon("ChevronRight") {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineToRelative(6f, -6f)
            close()
        }
    }

    val DirectionsRun: ImageVector by lazy {
        materialIcon("DirectionsRun") {
            moveTo(13.49f, 5.48f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            close()
            moveTo(9.89f, 19.38f)
            lineToRelative(1f, -4.4f)
            lineToRelative(2.1f, 2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-7.5f)
            lineToRelative(-2.1f, -2f)
            lineToRelative(0.6f, -3f)
            curveToRelative(1.3f, 1.5f, 3.3f, 2.5f, 5.5f, 2.5f)
            verticalLineToRelative(-2f)
            curveToRelative(-1.9f, 0f, -3.5f, -1f, -4.3f, -2.4f)
            lineToRelative(-1f, -1.6f)
            curveToRelative(-0.4f, -0.6f, -1f, -1f, -1.7f, -1f)
            curveToRelative(-0.3f, 0f, -0.5f, 0.1f, -0.8f, 0.1f)
            lineTo(6.89f, 8.28f)
            verticalLineToRelative(4.7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3.4f)
            lineToRelative(1.8f, -0.7f)
            lineToRelative(-1.6f, 8.1f)
            lineToRelative(-4.9f, -1f)
            lineToRelative(-0.4f, 2f)
            close()
        }
    }

    val Flag: ImageVector by lazy {
        materialIcon("Flag") {
            moveTo(14.4f, 6f)
            lineTo(14f, 4f)
            horizontalLineTo(5f)
            verticalLineToRelative(17f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(5.6f)
            lineToRelative(0.4f, 2f)
            horizontalLineTo(20f)
            verticalLineTo(6f)
            close()
        }
    }

    val Map: ImageVector by lazy {
        materialIcon("Map") {
            moveTo(20.5f, 3f)
            lineToRelative(-0.16f, 0.03f)
            lineTo(15f, 5.1f)
            lineTo(9f, 3f)
            lineTo(3.36f, 4.9f)
            curveToRelative(-0.21f, 0.07f, -0.36f, 0.25f, -0.36f, 0.48f)
            verticalLineTo(20.5f)
            curveToRelative(0f, 0.28f, 0.22f, 0.5f, 0.5f, 0.5f)
            lineToRelative(0.16f, -0.03f)
            lineTo(9f, 18.9f)
            lineToRelative(6f, 2.1f)
            lineToRelative(5.64f, -1.9f)
            curveToRelative(0.21f, -0.07f, 0.36f, -0.25f, 0.36f, -0.48f)
            verticalLineTo(3.5f)
            curveToRelative(0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
            close()
            moveTo(15f, 19f)
            lineToRelative(-6f, -2.11f)
            verticalLineTo(5f)
            lineToRelative(6f, 2.11f)
            verticalLineTo(19f)
            close()
        }
    }

    val Fullscreen: ImageVector by lazy {
        materialIcon("Fullscreen") {
            moveTo(7f, 14f)
            horizontalLineTo(5f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(-2f)
            horizontalLineTo(7f)
            verticalLineToRelative(-3f)
            close()
            moveTo(5f, 10f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(3f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineToRelative(5f)
            close()
            moveTo(17f, 17f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(-5f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(3f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(2f)
            verticalLineTo(5f)
            horizontalLineToRelative(-5f)
            close()
        }
    }

    val Layers: ImageVector by lazy {
        materialIcon("Layers") {
            moveTo(11.99f, 18.54f)
            lineToRelative(-7.37f, -5.73f)
            lineTo(3f, 14.07f)
            lineToRelative(9f, 7f)
            lineToRelative(9f, -7f)
            lineToRelative(-1.63f, -1.27f)
            lineToRelative(-7.38f, 5.74f)
            close()
            moveTo(12f, 16f)
            lineToRelative(7.36f, -5.73f)
            lineTo(21f, 9f)
            lineToRelative(-9f, -7f)
            lineToRelative(-9f, 7f)
            lineToRelative(1.63f, 1.27f)
            lineTo(12f, 16f)
            close()
        }
    }

    val MyLocation: ImageVector by lazy {
        materialIcon("MyLocation") {
            moveTo(12f, 8f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
            reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
            close()
            moveTo(20.94f, 11f)
            curveTo(20.48f, 6.83f, 17.17f, 3.52f, 13f, 3.06f)
            verticalLineTo(1f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2.06f)
            curveTo(6.83f, 3.52f, 3.52f, 6.83f, 3.06f, 11f)
            horizontalLineTo(1f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2.06f)
            curveToRelative(0.46f, 4.17f, 3.77f, 7.48f, 7.94f, 7.94f)
            verticalLineTo(23f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2.06f)
            curveToRelative(4.17f, -0.46f, 7.48f, -3.77f, 7.94f, -7.94f)
            horizontalLineTo(23f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2.06f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
            reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            close()
        }
    }

    val Schedule: ImageVector by lazy {
        materialIcon("Schedule") {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
            reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
            reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
            close()
            moveTo(12.5f, 7f)
            horizontalLineTo(11f)
            verticalLineToRelative(6f)
            lineToRelative(5.25f, 3.15f)
            lineToRelative(0.75f, -1.23f)
            lineToRelative(-4.5f, -2.67f)
            close()
        }
    }

    val Stop: ImageVector by lazy {
        materialIcon("Stop") {
            moveTo(6f, 6f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(12f)
            horizontalLineTo(6f)
            close()
        }
    }

    val Pause: ImageVector by lazy {
        materialIcon("Pause") {
            moveTo(6f, 19f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineToRelative(14f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            close()
        }
    }

    val PlayArrow: ImageVector by lazy {
        materialIcon("PlayArrow") {
            moveTo(8f, 5f)
            verticalLineToRelative(14f)
            lineToRelative(11f, -7f)
            close()
        }
    }

    val OpenInNew: ImageVector by lazy {
        materialIcon("OpenInNew") {
            moveTo(19f, 19f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineToRelative(7f)
            verticalLineTo(3f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(7f)
            close()
            moveTo(14f, 3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3.59f)
            lineToRelative(-9.83f, 9.83f)
            lineToRelative(1.41f, 1.41f)
            lineTo(19f, 6.41f)
            verticalLineTo(10f)
            horizontalLineToRelative(2f)
            verticalLineTo(3f)
            horizontalLineToRelative(-7f)
            close()
        }
    }

    val MoreVert: ImageVector by lazy {
        materialIcon("MoreVert") {
            moveTo(12f, 8f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            close()
            moveTo(12f, 10f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 16f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
        }
    }

    val ExpandMore: ImageVector by lazy {
        materialIcon("ExpandMore") {
            moveTo(16.59f, 8.59f)
            lineTo(12f, 13.17f)
            lineTo(7.41f, 8.59f)
            lineTo(6f, 10f)
            lineToRelative(6f, 6f)
            lineToRelative(6f, -6f)
            close()
        }
    }

    val Star: ImageVector by lazy {
        materialIcon("Star") {
            moveTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineToRelative(-1.64f, -7.03f)
            lineTo(22f, 9.24f)
            lineToRelative(-7.19f, -0.61f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineToRelative(5.46f, 4.73f)
            lineTo(5.82f, 21f)
            close()
        }
    }
}

private fun materialIcon(
    name: String,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            pathBuilder()
        }
    }.build()
}
