package io.github.eyunya11.create_train_shadows.client;

import dev.engine_room.flywheel.lib.util.ShadersModHelper;
import io.github.eyunya11.create_train_shadows.Create_train_shadows;

/**
 * Whether a shader pack (Iris, through Flywheel's helper) is in use. A shader pack lights and shades the world
 * itself, shadow map included, so a flat shadow of ours on top of it would be a second, wrong one; and our
 * translucent quads would also be drawn into the shadow pass of the pack, where they would cast shadows.
 *
 * <p>The helper is part of Flywheel's API, which every supported version has. It is asked in a guarded way all
 * the same: if a later Flywheel moves it, the shadows go on being drawn instead of the game crashing.
 */
final class ShaderPacks {
    private static boolean broken;

    private ShaderPacks() {
    }

    static boolean inUse() {
        if (broken) {
            return false;
        }
        try {
            return ShadersModHelper.isShaderPackInUse();
        } catch (LinkageError | RuntimeException e) {
            broken = true;
            Create_train_shadows.LOGGER.warn("Could not ask Flywheel whether a shader pack is in use; train shadows "
                + "will be drawn whatever the pack: {}", e.toString());
            return false;
        }
    }
}
