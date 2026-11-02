package com.orienteer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Alpine Trail & Field Showcase — from DESIGN.md (Downloads).
 * Forest greens, iced-sage surfaces, pine charcoal dark (no navy / mint neon).
 */

// ── Brand anchors (prose + M3 YAML) ──
/** High-emphasis CTA fill. */
val BrandForest = Color(0xFF1B5E20)
val BrandForestPressed = Color(0xFF144618)
val BrandSecondaryGreen = Color(0xFF2E7D32)
val BrandTeal = Color(0xFF00897B)
val BrandSageSoft = Color(0xFFE8F5E9)
val BrandSageMint = Color(0xFFC8E6C9)
val BrandCanvas = Color(0xFFF4F8F5)
val BrandPorcelain = Color(0xFFFFFFFF)
val BrandCoolSlate = Color(0xFFEBF2EE)
val BrandPineCharcoal = Color(0xFF0F1A13)

// ── Light (YAML tokens) ──
val LightBackground = Color(0xFFF0FDF1)
val LightOnBackground = Color(0xFF131E17)
val LightSurface = Color(0xFFF0FDF1)
val LightOnSurface = Color(0xFF131E17)
val LightOnSurfaceVariant = Color(0xFF41493E)
val LightSurfaceDim = Color(0xFFD0DDD2)
val LightSurfaceBright = Color(0xFFF0FDF1)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEAF7EB)
val LightSurfaceContainer = Color(0xFFE4F1E5)
val LightSurfaceContainerHigh = Color(0xFFDEECE0)
val LightSurfaceContainerHighest = Color(0xFFD9E6DA)
val LightSurfaceVariant = Color(0xFFD9E6DA)
val LightPrimary = Color(0xFF1B5E20)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF1B5E20)
val LightOnPrimaryContainer = Color(0xFF90D689)
val LightSecondary = Color(0xFF1B6D24)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFA0F399)
val LightOnSecondaryContainer = Color(0xFF217128)
val LightTertiary = Color(0xFF005C52)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF005C52)
val LightOnTertiaryContainer = Color(0xFF6ED6C6)
val LightOutline = Color(0xFF717A6D)
val LightOutlineVariant = Color(0xFFC0C9BB)
val LightSurfaceTint = Color(0xFF2A6B2C)
val LightInverseSurface = Color(0xFF28332B)
val LightInverseOnSurface = Color(0xFFE7F4E8)
val LightInversePrimary = Color(0xFF91D78A)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF93000A)
val LightPrimaryFixed = Color(0xFFACF4A4)
val LightPrimaryFixedDim = Color(0xFF91D78A)

val LightMapPlannedPath = Color(0xFF717A6D)
val LightMapActualPath = Color(0xFF1B5E20)
/** Straight control-to-control legs (orienteering overprint magenta). */
val LightMapAirLeg = Color(0xFFAD0078)
val LightMapAirLegHalo = Color(0xFFFFFFFF)

// ── Dark (pine charcoal + forest CTAs) ──
val OrienteerBackground = BrandPineCharcoal
val OrienteerOnBackground = Color(0xFFE7F4E8)
val OrienteerSurface = BrandPineCharcoal
val OrienteerSurfaceContainerLowest = Color(0xFF0A120D)
val OrienteerSurfaceContainerLow = Color(0xFF162019)
val OrienteerSurfaceContainer = Color(0xFF1C2820)
val OrienteerSurfaceContainerHigh = Color(0xFF243028)
val OrienteerSurfaceContainerHighest = Color(0xFF2C3A32)
val OrienteerOnSurface = Color(0xFFE7F4E8)
val OrienteerOnSurfaceVariant = Color(0xFFC0C9BB)
/**
 * M3 dark primary must be a *light* accent — titles/icons use [primary] on pine charcoal.
 * Solid forest CTAs (#1B5E20 + white) stay on [BrandForest] in PrimaryGreenButton / chips.
 */
val OrienteerPrimary = Color(0xFF91D78A)
val OrienteerOnPrimary = Color(0xFF003910)
val OrienteerPrimaryContainer = Color(0xFF144618)
val OrienteerOnPrimaryContainer = Color(0xFFACF4A4)
val OrienteerSecondary = Color(0xFF88D982)
val OrienteerOnSecondary = Color(0xFF003A08)
val OrienteerSecondaryContainer = Color(0xFF0C5216)
val OrienteerOnSecondaryContainer = Color(0xFFA3F69C)
/** Lighter teal for labels/icons on dark surfaces. */
val OrienteerTertiary = Color(0xFF70D8C8)
val OrienteerOnTertiary = Color(0xFF003731)
val OrienteerTertiaryContainer = Color(0xFF005048)
val OrienteerOnTertiaryContainer = Color(0xFF8DF5E4)
val OrienteerOutline = Color(0xFF8A9385)
val OrienteerOutlineVariant = Color(0xFF41493E)
val OrienteerError = Color(0xFFFFB4AB)
val OrienteerOnError = Color(0xFF690005)
val OrienteerErrorContainer = Color(0xFF93000A)
val OrienteerOnErrorContainer = Color(0xFFFFDAD6)
val OrienteerInversePrimary = Color(0xFF91D78A)

val MapPlannedPath = Color(0xFF8A9385)
val MapActualPath = Color(0xFF91D78A)
val MapAirLeg = Color(0xFFFF6BE9)
val MapAirLegHalo = Color(0xFF1A0A16)
