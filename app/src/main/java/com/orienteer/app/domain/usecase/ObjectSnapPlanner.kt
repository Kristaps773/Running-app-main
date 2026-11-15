package com.orienteer.app.domain.usecase

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import com.orienteer.app.util.CheckpointDescriptionGenerator
import com.orienteer.app.util.GeoUtils

data class SnapObjectCandidate(
    val position: GeoPoint,
    val landmarkType: String,
    val name: String? = null,
)

data class ControlSnapRequest(
    val index: Int,
    val anchor: GeoPoint,
    val candidates: List<SnapObjectCandidate> = emptyList(),
)

data class ControlSnapOutcome(
    val index: Int,
    val position: GeoPoint,
    val onObject: Boolean,
    val description: String,
    val landmarkType: String?,
)

data class ObjectSnapPlanResult(
    val outcomes: List<ControlSnapOutcome>,
    val ratioRevertCount: Int,
    val fullRevert: Boolean,
)

object ObjectSnapPlanner {
    const val SNAP_DISTANCE_M = 100.0
    const val NEAR_LABEL_DISTANCE_M = 220.0
    const val MAX_RATIO_REVERT_ATTEMPTS = 3

    suspend fun planSnaps(
        controls: List<ControlSnapRequest>,
        airDistanceM: Double,
        maxPathToAirRatio: Double,
        measurePathM: suspend (List<ControlSnapOutcome>) -> Double,
    ): ObjectSnapPlanResult {
        val anchors = controls.associate { it.index to it.anchor }
        var outcomes = controls.map(::initialOutcome)
        var pathM = measurePathM(outcomes)
        val maxPathM = airDistanceM * maxPathToAirRatio
        var ratioRevertCount = 0
        var fullRevert = false

        if (pathM > maxPathM) {
            while (ratioRevertCount < MAX_RATIO_REVERT_ATTEMPTS && pathM > maxPathM) {
                val onObject = outcomes.filter { it.onObject }
                if (onObject.isEmpty()) break
                val toRevert = onObject.maxByOrNull { outcome ->
                    GeoUtils.distanceMeters(anchors.getValue(outcome.index), outcome.position)
                } ?: break
                outcomes = outcomes.map { outcome ->
                    if (outcome.index == toRevert.index) {
                        revertToNear(outcome, anchors.getValue(outcome.index))
                    } else {
                        outcome
                    }
                }
                ratioRevertCount++
                pathM = measurePathM(outcomes)
            }
            if (pathM > maxPathM || ratioRevertCount >= MAX_RATIO_REVERT_ATTEMPTS) {
                fullRevert = true
                outcomes = outcomes.map { outcome ->
                    if (outcome.onObject) {
                        revertToNear(outcome, anchors.getValue(outcome.index))
                    } else {
                        outcome
                    }
                }
                measurePathM(outcomes)
            }
        }

        return ObjectSnapPlanResult(
            outcomes = outcomes,
            ratioRevertCount = ratioRevertCount,
            fullRevert = fullRevert,
        )
    }

    private fun initialOutcome(req: ControlSnapRequest): ControlSnapOutcome {
        val closest = req.candidates.minByOrNull { candidate ->
            GeoUtils.distanceMeters(req.anchor, candidate.position)
        }
        val distM = closest?.let { GeoUtils.distanceMeters(req.anchor, it.position) } ?: Double.MAX_VALUE

        return when {
            closest != null && distM <= SNAP_DISTANCE_M -> ControlSnapOutcome(
                index = req.index,
                position = closest.position,
                onObject = true,
                description = describeLandmark(req.index, closest.landmarkType, closest.name, onObject = true),
                landmarkType = closest.landmarkType,
            )
            closest != null && distM <= NEAR_LABEL_DISTANCE_M -> ControlSnapOutcome(
                index = req.index,
                position = req.anchor,
                onObject = false,
                description = describeLandmark(req.index, closest.landmarkType, closest.name, onObject = false),
                landmarkType = closest.landmarkType,
            )
            else -> ControlSnapOutcome(
                index = req.index,
                position = req.anchor,
                onObject = false,
                description = "Checkpoint ${req.index + 1}: Near path junction.",
                landmarkType = "generic=path_junction",
            )
        }
    }

    private fun revertToNear(outcome: ControlSnapOutcome, anchor: GeoPoint): ControlSnapOutcome {
        val landmarkType = outcome.landmarkType
        val description = when (landmarkType) {
            "generic=path_junction" -> "Checkpoint ${outcome.index + 1}: Near path junction."
            null -> "Checkpoint ${outcome.index + 1}: Near path junction."
            else -> describeLandmark(outcome.index, landmarkType, name = null, onObject = false)
        }
        return outcome.copy(
            position = anchor,
            onObject = false,
            description = description,
            landmarkType = landmarkType ?: "generic=path_junction",
        )
    }

    private fun describeLandmark(
        index: Int,
        landmarkType: String,
        name: String?,
        onObject: Boolean,
    ): String {
        val element = overpassElementForLandmark(landmarkType, name)
        return CheckpointDescriptionGenerator.generate(index, listOf(element), onObject = onObject).first
    }

    private fun overpassElementForLandmark(landmarkType: String, name: String?): OverpassElement {
        val eq = landmarkType.indexOf('=')
        val tags = if (eq > 0) {
            buildMap {
                put(landmarkType.substring(0, eq), landmarkType.substring(eq + 1))
                if (name != null) put("name", name)
            }
        } else {
            emptyMap()
        }
        return OverpassElement(
            type = "node",
            id = 0L,
            lat = null,
            lon = null,
            tags = tags,
            center = null,
        )
    }
}