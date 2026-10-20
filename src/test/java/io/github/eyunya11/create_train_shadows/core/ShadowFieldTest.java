package io.github.eyunya11.create_train_shadows.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShadowFieldTest {
    private static final ShadowParams PARAMS = ShadowParams.DEFAULT; // opacity 0.6, grace 1, fade 8, margin 0.5, soft 0.5

    // A carriage 3 wide and 7 long at x in [10.5, 13.5], z in [20.5, 27.5], underside at y = 5
    private static ShadowField carriage(double distanceFactor) {
        ShadowField field = new ShadowField();
        field.set(Footprint.soft(List.of(new LocalRect(0, 0, 3, 7, 0)), PARAMS.margin(), PARAMS.softness()),
            Mat3x4.translation(10.5, 5, 20.5), PARAMS, distanceFactor);
        return field;
    }

    @Test
    void underTheBodyTheShadowIsAsDarkAsTheGroundWouldBe() {
        // the top of the rails, 0.65 blocks under the underside: inside the grace height
        assertEquals(0.6, carriage(1).alphaAt(12, 4.35, 24, 1.0), 1e-6);
    }

    @Test
    void itFadesOutInTheMarginAndNothingIsOutsideIt() {
        ShadowField field = carriage(1);
        assertEquals(0.6 * 0.5, field.alphaAt(10.25, 4.35, 24, 1.0), 1e-6); // halfway out in the soft band
        assertEquals(0.0, field.alphaAt(9.9, 4.35, 24, 1.0), 1e-12);
        assertEquals(0.0, field.alphaAt(12, 4.35, 28.2, 1.0), 1e-12);
    }

    @Test
    void lightAndDistanceScaleTheShadow() {
        assertEquals(0.6 * 0.5, carriage(1).alphaAt(12, 4.35, 24, 0.5), 1e-6);
        assertEquals(0.6 * 0.25, carriage(0.25).alphaAt(12, 4.35, 24, 1.0), 1e-6);
    }

    @Test
    void aPointFarUnderTheBodyGetsALighterShadow() {
        ShadowField field = carriage(1);
        // 5 blocks under the underside: 4 beyond the grace height, half of the fade
        assertEquals(0.6 * 0.5, field.alphaAt(12, 0, 24, 1.0), 1e-6);
        assertEquals(0.0, field.alphaAt(12, -4, 24, 1.0), 1e-12);
    }

    @Test
    void thePartsOfTheBodyThatHangHigherAreMeasuredFromTheLowestOne() {
        ShadowField field = new ShadowField();
        field.set(Footprint.soft(List.of(new LocalRect(0, 0, 1, 1, 0), new LocalRect(2, 0, 6, 1, 1)), 0, 0),
            Mat3x4.translation(10.5, 5, 20.5), PARAMS, 1);
        double underWheels = field.alphaAt(11, 4.35, 21, 1.0);
        double underFloor = field.alphaAt(14, 4.35, 21, 1.0); // its underside is a block higher
        assertEquals(underWheels, underFloor, 1e-6);
        assertTrue(underWheels > 0);
    }

    @Test
    void theBoxAroundTheFootprintRulesOutFarPoints() {
        ShadowField field = carriage(1);
        assertTrue(field.isNear(12, 4.35, 24, 0));
        assertTrue(field.isNear(9.0, 4.35, 24, 2));
        assertFalse(field.isNear(5, 4.35, 24, 2));
        assertFalse(field.isNear(12, 4.35, 40, 2));
        assertFalse(field.isNear(12, 12, 24, 2)); // well above the body
    }

    @Test
    void anEmptyFootprintHasNoShadowAndNothingIsNear() {
        ShadowField field = new ShadowField();
        field.set(List.of(), Mat3x4.translation(0, 0, 0), PARAMS, 1);
        assertEquals(0.0, field.alphaAt(0, 0, 0, 1.0), 1e-12);
        assertFalse(field.isNear(0, 0, 0, 5));
    }
}
