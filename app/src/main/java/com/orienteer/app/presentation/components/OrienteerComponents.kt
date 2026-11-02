package com.orienteer.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import com.orienteer.app.ui.icons.OrienteerIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orienteer.app.R
import com.orienteer.app.ui.theme.BrandForest
import com.orienteer.app.ui.theme.OrienteerBottomSheetShape
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerChipShape
import com.orienteer.app.ui.theme.OrienteerRadius
import com.orienteer.app.ui.theme.OrienteerSpacing
import kotlin.math.abs
import kotlinx.coroutines.launch

enum class BottomNavTab { Routes, Progress, Profile }

@Composable
fun OrienteerTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onSettings: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = OrienteerSpacing.minTouch)
                .padding(horizontal = OrienteerSpacing.xs, vertical = OrienteerSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                onSettings != null -> {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                trailingIcon != null && onTrailingClick != null -> {
                    IconButton(onClick = onTrailingClick) {
                        Icon(
                            trailingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                else -> Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
fun PrimaryGreenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false
) {
    // DESIGN.md: solid forest CTA (#1B5E20), white label, 56dp — both themes (dark primary is mint accent).
    val shape = OrienteerCardShape
    val cta = BrandForest
    val onCta = Color.White
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(OrienteerSpacing.buttonHeight),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = cta,
            contentColor = onCta,
            disabledContainerColor = cta.copy(alpha = 0.4f),
            disabledContentColor = onCta.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (loading) {
            CompactRoutePlottingSpinner(
                color = onCta,
                size = 22.dp
            )
            Spacer(Modifier.width(12.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** When true, no own surface — for rows inside [SettingsGroupedList]. */
    flat: Boolean = false
) {
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = OrienteerSpacing.minTouch)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = OrienteerSpacing.md,
                    vertical = if (subtitle != null) OrienteerSpacing.md else OrienteerSpacing.xs
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                OrienteerIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
    if (flat) {
        Box(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = OrienteerCardShape
        ) {
            content()
        }
    }
}

@Composable
fun SettingsGroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = OrienteerCardShape
    ) {
        Column(content = content)
    }
}

@Composable
fun DistanceStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    /** Shown inline after [value] in smaller type, e.g. "(1.1× air)". */
    valueSuffix: String? = null
) {
    val bg = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val valueColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val suffixStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        color = valueColor.copy(alpha = 0.82f)
    )
    Column(
        modifier = modifier
            .clip(OrienteerCardShape)
            .background(bg)
            .padding(OrienteerSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(OrienteerSpacing.xxs))
        Text(
            text = buildAnnotatedString {
                append(value)
                if (valueSuffix != null) {
                    withStyle(
                        SpanStyle(
                            fontSize = suffixStyle.fontSize,
                            fontWeight = suffixStyle.fontWeight,
                            color = suffixStyle.color
                        )
                    ) {
                        append(" $valueSuffix")
                    }
                }
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Map bottom panel. Drag the grey handle — finger tracks 1:1 (no per-frame coroutines).
 * Release settles with a spring-damper (m·x'' = -k·x - b·x') using release velocity.
 * Overscroll uses iOS rubber-band: (1 - 1/(x·c/d + 1)) · d.
 */
@Composable
fun RouteBottomSheet(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    /**
     * Visible sheet height in px above the nav bar. Prefer driving overlays with
     * [Modifier.graphicsLayer] translation (not layout padding) to avoid jank.
     */
    onVisibleHeightPxChanged: (Float) -> Unit = {},
    collapsible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val sheetColor = MaterialTheme.colorScheme.surfaceContainer
    val scope = rememberCoroutineScope()
    var fullHeightPx by remember { mutableIntStateOf(0) }
    var handleHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    // Unbounded finger position; rubber-banded for display while dragging.
    var rawDragPx by remember { mutableFloatStateOf(0f) }
    var dragVisualPx by remember { mutableFloatStateOf(0f) }
    var releaseVelocityPx by remember { mutableFloatStateOf(0f) }
    val offsetAnim = remember { Animatable(0f) }

    val peekHeightPx = (handleHeightPx + with(density) { OrienteerSpacing.sm.roundToPx() * 2 })
        .coerceAtLeast(with(density) { 36.dp.roundToPx() })
    val maxOffsetPx = (fullHeightPx - peekHeightPx).coerceAtLeast(0).toFloat()

    // Near-critical damping — closer to UIKit sheet settle than a bouncy spring.
    val sheetSpring = remember {
        spring<Float>(
            dampingRatio = 0.92f,
            stiffness = 900f,
            visibilityThreshold = 0.5f
        )
    }

    LaunchedEffect(expanded, maxOffsetPx) {
        if (dragging || maxOffsetPx <= 0f) return@LaunchedEffect
        val target = if (expanded) 0f else maxOffsetPx
        val releaseVx = releaseVelocityPx
        releaseVelocityPx = 0f
        val velocity = if (releaseVx != 0f) releaseVx else offsetAnim.velocity
        if (abs(offsetAnim.value - target) < 0.5f && abs(velocity) < 1f) {
            offsetAnim.snapTo(target)
            return@LaunchedEffect
        }
        offsetAnim.animateTo(
            targetValue = target,
            animationSpec = sheetSpring,
            initialVelocity = velocity
        )
    }

    val offsetPx = if (dragging) dragVisualPx else offsetAnim.value
    val visibleHeightPx = if (fullHeightPx > 0) {
        (fullHeightPx - offsetPx).coerceIn(0f, fullHeightPx.toFloat())
    } else {
        0f
    }
    SideEffect {
        onVisibleHeightPxChanged(visibleHeightPx)
    }

    val heightModifier = if (fullHeightPx > 0 && visibleHeightPx > 0f) {
        Modifier.height(with(density) { visibleHeightPx.toDp() })
    } else {
        Modifier
    }

    val handleCd = stringResource(R.string.cd_slide_bottom_sheet)
    val canDrag = collapsible && maxOffsetPx > 0f
    // Update float state only — never scope.launch per drag frame (that was the lag).
    val dragState = rememberDraggableState { delta ->
        if (!canDrag) return@rememberDraggableState
        rawDragPx += delta
        dragVisualPx = sheetRubberBandOffset(rawDragPx, maxOffsetPx)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .then(heightModifier)
            .clip(OrienteerBottomSheetShape)
            .background(sheetColor)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .onSizeChanged { size ->
                    if (size.height > 0 && size.height != fullHeightPx) {
                        fullHeightPx = size.height
                    }
                }
                .padding(horizontal = OrienteerSpacing.md, vertical = OrienteerSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .onSizeChanged { size ->
                        if (size.height > 0) handleHeightPx = size.height
                    }
                    .semantics { contentDescription = handleCd }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = canDrag,
                        onDragStarted = {
                            dragging = true
                            scope.launch { offsetAnim.stop() }
                            // Continue from live position — never jump to settled anchors.
                            val live = if (offsetAnim.isRunning) offsetAnim.value else offsetAnim.value
                            rawDragPx = live
                            dragVisualPx = live
                        },
                        onDragStopped = { velocity ->
                            if (maxOffsetPx <= 0f) {
                                dragging = false
                                return@draggable
                            }
                            val current = dragVisualPx
                            val collapse = when {
                                velocity > SheetFlingVelocityPx -> true
                                velocity < -SheetFlingVelocityPx -> false
                                else -> current >= maxOffsetPx * 0.35f
                            }
                            val target = if (collapse) maxOffsetPx else 0f
                            val newExpanded = !collapse
                            scope.launch {
                                offsetAnim.snapTo(current)
                                dragging = false
                                if (newExpanded == expanded) {
                                    offsetAnim.animateTo(
                                        targetValue = target,
                                        animationSpec = sheetSpring,
                                        initialVelocity = velocity
                                    )
                                } else {
                                    releaseVelocityPx = velocity
                                    onExpandedChange(newExpanded)
                                }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                )
            }
            Spacer(Modifier.height(OrienteerSpacing.md))
            content()
        }
    }
}

/** iOS UIScrollView rubberBand: (1 - 1/(x·c/d + 1)) · d  (c ≈ 0.55). */
private fun sheetRubberBand(overscroll: Float, dimension: Float, constant: Float = 0.55f): Float {
    if (overscroll <= 0f || dimension <= 0f) return 0f
    return (1f - 1f / (overscroll * constant / dimension + 1f)) * dimension
}

private fun sheetRubberBandOffset(raw: Float, maxOffset: Float): Float {
    val dim = maxOffset.coerceAtLeast(1f)
    return when {
        raw < 0f -> -sheetRubberBand(-raw, dim)
        raw > maxOffset -> maxOffset + sheetRubberBand(raw - maxOffset, dim)
        else -> raw
    }
}

private const val SheetFlingVelocityPx = 900f

@Composable
fun StitchBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // Flat alpine canvas — DESIGN.md Level 0 backdrop (no wash gradient).
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = content
    )
}

@Composable
fun DistanceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = OrienteerChipShape
    Surface(
        onClick = onClick,
        modifier = modifier.height(OrienteerSpacing.minTouch - 2.dp),
        shape = shape,
        color = if (selected) {
            BrandForest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) BrandForest
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OrienteerSpacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                color = if (selected) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun GhostTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun BentoMetricGrid(
    distance: String,
    duration: String,
    pace: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OrienteerCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(OrienteerSpacing.md + OrienteerSpacing.xxs)
        ) {
            Column {
                Text(
                    "DISTANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    distance,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs)
        ) {
            BentoTile("Duration", duration, Modifier.weight(1f))
            BentoTile("Pace", pace, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BentoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(OrienteerCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(OrienteerSpacing.md)
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CheckpointTargetCard(
    checkpointLabel: String,
    description: String,
    progress: Float,
    distanceAway: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OrienteerCardShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(OrienteerSpacing.md + OrienteerSpacing.xxs)
    ) {
        Icon(
            OrienteerIcons.Flag,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
        )
        Column {
            Text(
                checkpointLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                description,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (distanceAway != null) {
                Spacer(Modifier.height(OrienteerSpacing.xxs))
                Text(
                    distanceAway,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(OrienteerSpacing.sm))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun OrienteerBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.Routes,
            onClick = { onTabSelected(BottomNavTab.Routes) },
            icon = { Icon(OrienteerIcons.Map, contentDescription = null) },
            label = { Text("Routes") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.Progress,
            onClick = { onTabSelected(BottomNavTab.Progress) },
            icon = { Icon(OrienteerIcons.DirectionsRun, contentDescription = null) },
            label = { Text("Progress") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.Profile,
            onClick = { onTabSelected(BottomNavTab.Profile) },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("Profile") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        )
    }
}
