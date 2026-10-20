package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackSurfaceTest {
    private static final double[] OUT = new double[3];

    @Test
    void levelTrackHasItsRailsAtTheRailTopAndNoSlope() {
        assertTrue(TrackSurface.plane(0, 1, 0, 0, OUT));
        assertEquals(TrackSurface.RAIL_TOP, OUT[0], 1e-12);
        assertEquals(0.0, OUT[1], 1e-12);
        assertEquals(0.0, OUT[2], 1e-12);
    }

    @Test
    void theNormalDoesNotNeedToBeUnitLength() {
        assertTrue(TrackSurface.plane(0, 3, 0, 0, OUT));
        assertEquals(TrackSurface.RAIL_TOP, OUT[0], 1e-12);
    }

    @Test
    void aSlopeClimbingTowardsMinusZHasItsPlaneTiltedThatWay() {
        // the ascending track shape's normal is (0, 1, 1) and its centre is half a block up
        assertTrue(TrackSurface.plane(0, 1, 1, 0.5, OUT));
        assertEquals(0.5 + TrackSurface.RAIL_TOP * Math.sqrt(2), OUT[0], 1e-9);
        assertEquals(0.0, OUT[1], 1e-12);
        assertEquals(-1.0, OUT[2], 1e-12);
    }

    @Test
    void aSlopeClimbingTowardsMinusXTiltsAlongX() {
        assertTrue(TrackSurface.plane(1, 1, 0, 0.5, OUT));
        assertEquals(-1.0, OUT[1], 1e-12);
        assertEquals(0.0, OUT[2], 1e-12);
    }

    @Test
    void thePlaneIsPerpendicularToTheNormalAtTheRailTopAndKeepsItsDistance() {
        // On a 45 degree slope a point on the plane is RAIL_TOP away from the line of the track, measured
        // along the normal.
        double elevation = 0.5;
        assertTrue(TrackSurface.plane(0, 1, 1, elevation, OUT));
        double nz = 1 / Math.sqrt(2), ny = 1 / Math.sqrt(2);
        // the point of the track line in the middle of the block, moved along the normal
        double pointY = elevation + ny * TrackSurface.RAIL_TOP;
        double pointZ = nz * TrackSurface.RAIL_TOP; // relative to the middle of the block
        double planeHeightThere = OUT[0] + OUT[2] * pointZ;
        assertEquals(pointY, planeHeightThere, 1e-9);
    }

    @Test
    void aNormalThatDoesNotPointUpHasNoPlane() {
        assertFalse(TrackSurface.plane(0, 0, 0, 0, OUT));
        assertFalse(TrackSurface.plane(1, 0, 0, 0, OUT));
        assertFalse(TrackSurface.plane(0, -1, 0, 0, OUT));
    }
}
