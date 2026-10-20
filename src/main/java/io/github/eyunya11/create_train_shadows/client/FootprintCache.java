package io.github.eyunya11.create_train_shadows.client;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;

import io.github.eyunya11.create_train_shadows.core.Footprint;
import io.github.eyunya11.create_train_shadows.core.LocalRect;
import io.github.eyunya11.create_train_shadows.core.ShadowRect;
import net.minecraft.core.BlockPos;

/** Shadow footprints of contraptions, rebuilt only when the set of blocks or the margin changes. */
final class FootprintCache {
    private record Entry(int blockCount, double margin, List<ShadowRect> rects) {
    }

    // Weak keys: a contraption that is disassembled or unloaded takes its footprint with it.
    private static final Map<Contraption, Entry> CACHE = new WeakHashMap<>();

    private FootprintCache() {
    }

    static List<ShadowRect> get(Contraption contraption, double margin) {
        // While a carriage is crossing a portal, parts of it are hidden and that changes every frame.
        if (contraption instanceof CarriageContraption carriage && !carriage.notInPortal()) {
            return Footprint.expand(build(contraption, true), margin);
        }

        int blockCount = contraption.getBlocks().size();
        Entry cached = CACHE.get(contraption);
        if (cached == null || cached.blockCount() != blockCount || cached.margin() != margin) {
            cached = new Entry(blockCount, margin, Footprint.expand(build(contraption, false), margin));
            CACHE.put(contraption, cached);
        }
        return cached.rects();
    }

    private static List<LocalRect> build(Contraption contraption, boolean skipHiddenInPortal) {
        Footprint.Builder footprint = new Footprint.Builder();
        for (BlockPos pos : contraption.getBlocks().keySet()) {
            if (skipHiddenInPortal && contraption.isHiddenInPortal(pos)) {
                continue;
            }
            footprint.add(pos.getX(), pos.getY(), pos.getZ());
        }
        return footprint.build();
    }
}
