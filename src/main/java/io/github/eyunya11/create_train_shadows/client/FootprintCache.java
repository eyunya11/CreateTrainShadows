package io.github.eyunya11.create_train_shadows.client;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;

import io.github.eyunya11.create_train_shadows.core.Footprint;
import io.github.eyunya11.create_train_shadows.core.LocalRect;
import io.github.eyunya11.create_train_shadows.core.ShadowParams;
import io.github.eyunya11.create_train_shadows.core.ShadowRect;
import net.minecraft.core.BlockPos;

/** Shadow footprints of contraptions, rebuilt only when the set of blocks or the shape settings change. */
final class FootprintCache {
    /**
     * @param ground the shadow on the ground: grown by the margin, with a soft edge
     * @param walls  the footprint itself, which is what walls are measured from
     * @param topY   the local height of the top of the highest block
     */
    record Footprints(List<ShadowRect> ground, List<ShadowRect> walls, double topY) {
    }

    private record Entry(int blockCount, double margin, double softness, Footprints footprints) {
    }

    // Weak keys: a contraption that is disassembled or unloaded takes its footprint with it.
    private static final Map<Contraption, Entry> CACHE = new WeakHashMap<>();

    private FootprintCache() {
    }

    static Footprints get(Contraption contraption, ShadowParams params) {
        // While a carriage is crossing a portal, parts of it are hidden and that changes every frame.
        if (contraption instanceof CarriageContraption carriage && !carriage.notInPortal()) {
            return build(contraption, true, params);
        }

        int blockCount = contraption.getBlocks().size();
        Entry cached = CACHE.get(contraption);
        if (cached == null || cached.blockCount() != blockCount || cached.margin() != params.margin()
            || cached.softness() != params.softness()) {
            cached = new Entry(blockCount, params.margin(), params.softness(), build(contraption, false, params));
            CACHE.put(contraption, cached);
        }
        return cached.footprints();
    }

    private static Footprints build(Contraption contraption, boolean skipHiddenInPortal, ShadowParams params) {
        Footprint.Builder footprint = new Footprint.Builder();
        int top = Integer.MIN_VALUE;
        for (BlockPos pos : contraption.getBlocks().keySet()) {
            if (skipHiddenInPortal && contraption.isHiddenInPortal(pos)) {
                continue;
            }
            footprint.add(pos.getX(), pos.getY(), pos.getZ());
            top = Math.max(top, pos.getY() + 1);
        }
        List<LocalRect> rects = footprint.build();
        return new Footprints(Footprint.soft(rects, params.margin(), params.softness()),
            Footprint.expand(rects, 0), top);
    }
}
