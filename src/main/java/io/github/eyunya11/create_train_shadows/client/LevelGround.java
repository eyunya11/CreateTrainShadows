package io.github.eyunya11.create_train_shadows.client;

import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.ShadowProjector;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Finds what a shadow lands on, following the vanilla entity shadow (and Create's ShadowRenderHelper):
 * a visible block whose collision shape is a full block, with enough light in the cell above it.
 *
 * <p>Create's track is not a full block. Its collision shape is a 2/16 high plate over the whole cell, so by
 * the vanilla rule a shadow would be drawn on the block under the track, inside the track's own model, and
 * would show only beside it. Unless {@code -Dcreate_train_shadows.vanillaReceiver=true} is set, plates like
 * that (one box covering the whole cell) also receive the shadow, at the top of the plate.
 */
final class LevelGround implements ShadowProjector.Ground {
    private static final boolean VANILLA_RECEIVER = Boolean.getBoolean("create_train_shadows.vanillaReceiver");
    private static final double EDGE = 1.0e-3;

    private final BlockPos.MutableBlockPos receiver = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
    private Level level;

    LevelGround use(Level level) {
        this.level = level;
        return this;
    }

    @Override
    public boolean sample(int cellX, int cellZ, int startY, int depth, double[] out) {
        int lowest = level.getMinBuildHeight();
        // Same loop as vanilla: y is the row of the air above the receiving block at y - 1.
        for (int y = startY; y > startY - depth && y - 1 >= lowest; y--) {
            receiver.set(cellX, y - 1, cellZ);
            BlockState state = level.getBlockState(receiver);
            if (state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            double top = receivingTop(state);
            if (Double.isNaN(top)) {
                continue;
            }
            above.set(cellX, y, cellZ);
            int light = level.getMaxLocalRawBrightness(above);
            if (light < ShadowOpacity.MIN_LIGHT) {
                continue;
            }
            out[0] = (y - 1) + top;
            out[1] = LightTexture.getBrightness(level.dimensionType(), light);
            return true;
        }
        return false;
    }

    /** Height of the receiving surface inside its block, or NaN when this block can't receive a shadow. */
    private double receivingTop(BlockState state) {
        VoxelShape collision = state.getCollisionShape(level, receiver);
        if (collision.isEmpty()) {
            return Double.NaN;
        }
        if (state.isCollisionShapeFullBlock(level, receiver)) {
            return 1.0;
        }
        if (VANILLA_RECEIVER) {
            return Double.NaN;
        }

        AABB bounds = collision.bounds();
        boolean coversCell = bounds.minX <= EDGE && bounds.maxX >= 1.0 - EDGE
            && bounds.minZ <= EDGE && bounds.maxZ >= 1.0 - EDGE;
        if (coversCell && collision.toAabbs().size() == 1) {
            return bounds.maxY;
        }
        return Double.NaN;
    }
}
