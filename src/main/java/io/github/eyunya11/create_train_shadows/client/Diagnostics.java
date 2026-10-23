package io.github.eyunya11.create_train_shadows.client;

import io.github.eyunya11.create_train_shadows.Create_train_shadows;

/**
 * Counts what happened to every train shadow request and logs a summary every two seconds, when the game is
 * started with {@code -Dcreate_train_shadows.debug=true}. Costs nothing otherwise.
 */
final class Diagnostics {
    enum Outcome {
        DRAWN, NOT_READY, SHADOWS_OFF_IN_VIDEO_SETTINGS, DISABLED_IN_MOD_CONFIG, SHADER_PACK_IN_USE, INVISIBLE,
        OTHER_LEVEL, NO_CONTRAPTION, TOO_FAR, NO_FOOTPRINT
    }

    private static final boolean ENABLED = Boolean.getBoolean("create_train_shadows.debug");
    private static final long INTERVAL_NANOS = 2_000_000_000L;

    private static final int[] COUNTS = new int[Outcome.values().length];
    private static long lastLog = System.nanoTime();
    private static int polygons;
    private static double lastDistance = Double.NaN;

    private Diagnostics() {
    }

    static void record(Outcome outcome) {
        if (!ENABLED) {
            return;
        }
        COUNTS[outcome.ordinal()]++;
        long now = System.nanoTime();
        if (now - lastLog >= INTERVAL_NANOS) {
            log();
            lastLog = now;
        }
    }

    static void drawn(int polygonCount, double cameraDistance) {
        if (!ENABLED) {
            return;
        }
        polygons += polygonCount;
        lastDistance = cameraDistance;
        record(Outcome.DRAWN);
    }

    static void distance(double cameraDistance) {
        if (ENABLED) {
            lastDistance = cameraDistance;
        }
    }

    private static void log() {
        StringBuilder summary = new StringBuilder();
        for (Outcome outcome : Outcome.values()) {
            int count = COUNTS[outcome.ordinal()];
            if (count > 0) {
                summary.append(' ').append(outcome).append('=').append(count);
            }
            COUNTS[outcome.ordinal()] = 0;
        }
        if (summary.isEmpty()) {
            summary.append(" no train was rendered");
        }
        Create_train_shadows.LOGGER.info("Train shadows, last 2 s:{} (polygons drawn: {}, last camera distance: {})",
            summary, polygons, Double.isNaN(lastDistance) ? "-" : String.format("%.1f", lastDistance));
        polygons = 0;
    }
}
