package com.orienteer.app.domain.usecase

/**
 * Route generation failed after internal retries. [diagnostics] is safe for Sentry
 * (no precise GPS) and describes why generation gave up.
 */
class RouteGenerationFailedException(
    message: String,
    cause: Throwable? = null,
    val diagnostics: Map<String, Any?> = emptyMap()
) : IllegalStateException(message, cause)
