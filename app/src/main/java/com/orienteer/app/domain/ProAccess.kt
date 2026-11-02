package com.orienteer.app.domain

/**
 * Planned one-time Pro unlock (Play Billing later).
 *
 * **Temporary:** [forceProForTesting] is true so internal/closed testers get full access
 * without a paywall. Set to false and wire [hasPro] to billing before production monetization.
 */
object ProAccess {
    private const val forceProForTesting = true

    fun hasPro(): Boolean = forceProForTesting

    /** Route preview PDF (printable map). GPX export stays free. */
    fun canExportRoutePdf(): Boolean = hasPro()

    /** Setup slider upper bound (km); free tier may cap lower when billing ships. */
    fun maxTargetDistanceKm(): Double = if (hasPro()) 50.0 else 15.0
}
