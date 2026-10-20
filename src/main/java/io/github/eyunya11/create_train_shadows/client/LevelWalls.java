package io.github.eyunya11.create_train_shadows.client;

import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.SideShadowProjector;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the walls next to a cell: a side counts as a wall when the block there has a full collision shape,
 * like the blocks a shadow lands on on the ground. The same cells are asked for again and again (many
 * frames per tick, neighbouring carriages), so the answers are remembered until the game ticks.
 */
final class LevelWalls implements SideShadowProjector.Walls {
    private static final byte UNKNOWN = -1;

    private final Long2ByteOpenHashMap full = new Long2ByteOpenHashMap();
    private final Long2ByteOpenHashMap light = new Long2ByteOpenHashMap();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private Level level;
    private long tick = Long.MIN_VALUE;

    LevelWalls() {
        full.defaultReturnValue(UNKNOWN);
        light.defaultReturnValue(UNKNOWN);
    }

    LevelWalls use(Level level) {
        long now = level.getGameTime();
        if (level != this.level || now != tick) {
            full.clear();
            light.clear();
            this.level = level;
            this.tick = now;
        }
        return this;
    }

    @Override
    public int walls(int cellX, int y, int cellZ, double[] out) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() || isFull(cellX, y, cellZ)) {
            return 0;
        }
        int sides = 0;
        if (isFull(cellX + 1, y, cellZ)) {
            sides |= SideShadowProjector.PLUS_X;
        }
        if (isFull(cellX - 1, y, cellZ)) {
            sides |= SideShadowProjector.MINUS_X;
        }
        if (isFull(cellX, y, cellZ + 1)) {
            sides |= SideShadowProjector.PLUS_Z;
        }
        if (isFull(cellX, y, cellZ - 1)) {
            sides |= SideShadowProjector.MINUS_Z;
        }
        if (sides == 0) {
            return 0;
        }

        int lightLevel = lightAt(cellX, y, cellZ);
        if (lightLevel < ShadowOpacity.MIN_LIGHT) {
            return 0;
        }
        out[0] = LightTexture.getBrightness(level.dimensionType(), lightLevel);
        return sides;
    }

    private boolean isFull(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte known = full.get(key);
        if (known == UNKNOWN) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            boolean isWall = state.getRenderShape() != RenderShape.INVISIBLE
                && state.isCollisionShapeFullBlock(level, pos);
            known = (byte) (isWall ? 1 : 0);
            full.put(key, known);
        }
        return known == 1;
    }

    private int lightAt(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte known = light.get(key);
        if (known == UNKNOWN) {
            pos.set(x, y, z);
            known = (byte) level.getMaxLocalRawBrightness(pos);
            light.put(key, known);
        }
        return known;
    }
}
