package com.orienteer.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orienteer.app.ui.theme.BrandForest
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerSpacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Alpine Trail route-generation loader — compass rose + bearing sweep + pulsing control.
 * Replaces the generic Material circular progress for course plotting.
 */
@Composable
fun RoutePlottingLoader(
    modifier: Modifier = Modifier,
    label: String = "Plotting course…",
    ringSize: Dp = 72.dp
) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val accent = BrandForest
    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val controlFill = MaterialTheme.colorScheme.surfaceContainerLowest
    val controlStroke = BrandForest

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                shape = OrienteerCardShape
            )
            .border(
                width = 1.dp,
                color = BrandForest.copy(alpha = 0.12f),
                shape = OrienteerCardShape
            )
            .padding(
                horizontal = OrienteerSpacing.lg,
                vertical = OrienteerSpacing.md
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.sm)
    ) {
        OrienteerCompassRing(
            modifier = Modifier.size(ringSize),
            trackColor = track,
            accentColor = accent,
            tickColor = tick,
            controlFill = controlFill,
            controlStroke = controlStroke
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing
        )
    }
}

/** Compact spin for CTAs (white on forest button). */
@Composable
fun CompactRoutePlottingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 22.dp
) {
    OrienteerCompassRing(
        modifier = modifier.size(size),
        trackColor = color.copy(alpha = 0.28f),
        accentColor = color,
        tickColor = color.copy(alpha = 0.45f),
        controlFill = color.copy(alpha = 0.9f),
        controlStroke = color,
        showCardinalTicks = false
    )
}

@Composable
private fun OrienteerCompassRing(
    modifier: Modifier,
    trackColor: Color,
    accentColor: Color,
    tickColor: Color,
    controlFill: Color,
    controlStroke: Color,
    showCardinalTicks: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "routePlot")
    val sweepRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val tickRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ticks"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val minSide = size.minDimension
        val stroke = (minSide * 0.085f).coerceIn(2.5f, 7f)
        val pad = stroke * 0.6f
        val radius = minSide / 2f - pad - stroke / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Soft track ring
        drawCircle(
            color = trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )

        // Compass ticks (slow reverse spin)
        if (showCardinalTicks) {
            rotate(tickRotation, pivot = center) {
                for (i in 0 until 12) {
                    val angleDeg = i * 30f - 90f
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val outer = radius - stroke * 0.15f
                    val tickLen = if (i % 3 == 0) stroke * 1.8f else stroke * 0.9f
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x + cosA * (outer - tickLen), center.y + sinA * (outer - tickLen)),
                        end = Offset(center.x + cosA * outer, center.y + sinA * outer),
                        strokeWidth = if (i % 3 == 0) stroke * 0.55f else stroke * 0.35f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Bearing sweep arc
        val arcSize = Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        drawArc(
            color = accentColor,
            startAngle = sweepRotation,
            sweepAngle = 78f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Leading tip glow
        drawArc(
            color = accentColor.copy(alpha = 0.35f),
            startAngle = sweepRotation - 28f,
            sweepAngle = 28f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke * 0.55f, cap = StrokeCap.Round)
        )

        // Pulsing control (checkpoint disc)
        val controlR = (minSide * 0.13f * pulse).coerceAtLeast(2.5f)
        drawCircle(color = controlFill, radius = controlR, center = center)
        drawCircle(
            color = controlStroke,
            radius = controlR,
            center = center,
            style = Stroke(width = (stroke * 0.45f).coerceAtLeast(1.5f))
        )
        // Inner control number-dot
        drawCircle(
            color = accentColor,
            radius = controlR * 0.38f,
            center = center
        )
    }
}
