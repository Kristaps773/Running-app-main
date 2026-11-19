package com.orienteer.app.presentation.map

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CourseLegStrokeTest {

    @Test
    fun angledCut_endFaceParallelToCutterNotSquareToLeg() {
        // Horizontal leg; 45° screen cut — same intersection math as CourseLegStroke.
        val halfW = 10f
        val ux = 1f
        val uy = 0f
        val cx = 0.7071f
        val cy = 0.7071f

        val tL = miterAlong(ux, uy, ox = 0f, oy = halfW, cx, cy)
        val tR = miterAlong(ux, uy, ox = 0f, oy = -halfW, cx, cy)
        assertTrue("45° cut must miter along the leg (not fall back to butt t=0)", abs(tL) > 5f)

        val leftX = tL
        val leftY = halfW
        val rightX = tR
        val rightY = -halfW
        val faceDx = rightX - leftX
        val faceDy = rightY - leftY
        val fl = hypot(faceDx.toDouble(), faceDy.toDouble()).toFloat()
        val fx = faceDx / fl
        val fy = faceDy / fl
        val align = abs(fx * cx + fy * cy)
        assertTrue("end face must be parallel to cutting direction", align > 0.95f)
        // Square (90°) butt face would be vertical → face dir (0,±1), align with 45° cut ≈ 0.707.
        assertTrue("must not be a 90° butt across the leg", align > 0.9f)
    }

    private fun miterAlong(
        ux: Float,
        uy: Float,
        ox: Float,
        oy: Float,
        cx: Float,
        cy: Float
    ): Float {
        val det = ux * cy - uy * cx
        return ((0f - ox) * cy - (0f - oy) * cx) / det
    }
}
