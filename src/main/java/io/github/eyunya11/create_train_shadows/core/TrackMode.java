package io.github.eyunya11.create_train_shadows.core;

/** How the shadow is put on Create's track. */
public enum TrackMode {
    /** Not at all: the shadow lies on the plate of the track block, or on what is under a curve. */
    OFF,
    /** A translucent plate at the height of the rails, on the track blocks and along the curves. */
    PLATE,
    /** The faces of the track's own model (rails, sleepers, girders) drawn again, black and translucent. */
    MODEL
}
