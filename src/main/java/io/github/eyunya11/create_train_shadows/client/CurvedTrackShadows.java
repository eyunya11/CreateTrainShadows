package io.github.eyunya11.create_train_shadows.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.track.BezierConnection;

import io.github.eyunya11.create_train_shadows.Create_train_shadows;
import io.github.eyunya11.create_train_shadows.core.Mat3x4;
import io.github.eyunya11.create_train_shadows.core.ShadowOpacity;
import io.github.eyunya11.create_train_shadows.core.ShadowParams;
import io.github.eyunya11.create_train_shadows.core.ShadowProjector;
import io.github.eyunya11.create_train_shadows.core.ShadowRect;
import io.github.eyunya11.create_train_shadows.core.TrackRibbon;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The shadow of a carriage on the curved track under it. A curve is drawn by the track's block entity across
 * cells that hold no block, so {@link LevelGround} finds nothing to put a shadow on there. The curves under
 * the carriage are the ones its wheels are on: each wheel (a travelling point) knows its edge of the track
 * graph and how far along it is, and an edge that is a turn has the curve itself. The shadow is a strip along
 * that curve at the height of the rails, as dark as the shadow of the carriage is at each point of it.
 */
final class CurvedTrackShadows implements TrackRibbon.Surface {
    private static final double SAMPLE_SPACING = 0.5;
    private static final double HALF_WIDTH = 1.4; // the sleepers are about 2.7 blocks long
    private static final int LATERAL_STEPS = 4;
    private static final int MAX_SAMPLES = 256;

    private final TrackRibbon ribbon = new TrackRibbon();
    private final Map<BezierConnection, double[]> ranges = new IdentityHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private double[] px = new double[64], py = new double[64], pz = new double[64];
    private double[] nx = new double[64], ny = new double[64], nz = new double[64];
    private boolean broken;

    // The frame being drawn
    private Level level;
    private ShadowParams params;
    private double distanceFactor;
    private List<ShadowRect> mesh;
    private Mat3x4 localToWorld;
    private Mat3x4 worldToLocal;
    private int lowestBottom;

    /**
     * @param reach how far along the track from its wheels the carriage's shadow can reach, in blocks
     */
    void draw(Carriage carriage, double reach, List<ShadowRect> mesh, Mat3x4 localToWorld, ShadowParams params,
        double distanceFactor, Level level, ShadowProjector.Sink sink) {
        if (broken) {
            return;
        }
        try {
            ranges.clear();
            carriage.bogeys.forEach(bogey -> {
                if (bogey != null) {
                    collect(bogey.leading(), reach);
                    collect(bogey.trailing(), reach);
                }
            });
            if (ranges.isEmpty()) {
                return;
            }

            this.level = level;
            this.params = params;
            this.distanceFactor = distanceFactor;
            this.mesh = mesh;
            this.localToWorld = localToWorld;
            this.worldToLocal = localToWorld.inverse();
            this.lowestBottom = Integer.MAX_VALUE;
            for (ShadowRect rect : mesh) {
                lowestBottom = Math.min(lowestBottom, rect.bottomY());
            }

            for (Map.Entry<BezierConnection, double[]> entry : ranges.entrySet()) {
                drawCurve(entry.getKey(), entry.getValue(), sink);
            }
        } catch (RuntimeException | LinkageError e) {
            // Create's track classes are not ours; if one behaves differently than expected, lose only this
            broken = true;
            Create_train_shadows.LOGGER.error("Shadows on curved track failed and are switched off: {}", e.toString(), e);
        } finally {
            this.level = null;
            this.mesh = null;
        }
    }

    /** Remembers the stretch of a curve that a wheel is on, with the reach of the shadow before and behind it. */
    private void collect(TravellingPoint wheel, double reach) {
        TrackEdge edge = wheel.edge;
        if (edge == null || !edge.isTurn()) {
            return;
        }
        double length = edge.getLength();
        if (length <= 0) {
            return;
        }
        double t = wheel.position / length;
        double span = reach / length;
        double[] range = ranges.computeIfAbsent(edge.getTurn(), curve -> new double[] {t - span, t + span, length});
        range[0] = Math.min(range[0], t - span);
        range[1] = Math.max(range[1], t + span);
    }

    private void drawCurve(BezierConnection curve, double[] range, ShadowProjector.Sink sink) {
        double length = range[2];
        double step = SAMPLE_SPACING / length;
        // Samples sit on a fixed grid along the curve, so they do not slide as the train moves
        int first = (int) Math.ceil(Math.max(0.0, range[0]) / step);
        int last = (int) Math.floor(Math.min(1.0, range[1]) / step);
        int count = Math.min(MAX_SAMPLES, last - first + 1);
        if (count < 2) {
            return;
        }
        grow(count);
        for (int i = 0; i < count; i++) {
            double t = Math.min(1.0, (first + i) * step);
            Vec3 point = curve.getPosition(t);
            Vec3 up = curve.getNormal(t);
            px[i] = point.x;
            py[i] = point.y;
            pz[i] = point.z;
            nx[i] = up.x;
            ny[i] = up.y;
            nz[i] = up.z;
        }
        ribbon.build(px, py, pz, nx, ny, nz, count, HALF_WIDTH, LATERAL_STEPS, ShadowProjector.LIFT, this, sink);
    }

    /** The darkness of the carriage's shadow at a point of the rails: what the ground would get there. */
    @Override
    public double alphaAt(double x, double y, double z) {
        double localX = worldToLocal.x(x, y, z);
        double localZ = worldToLocal.z(x, y, z);
        for (ShadowRect rect : mesh) {
            double fade = rect.fadeAt(localX, localZ);
            if (Double.isNaN(fade)) {
                continue;
            }
            double underside = localToWorld.y(localX, rect.bottomY(), localZ);
            double height = underside - y - (rect.bottomY() - lowestBottom);

            pos.set(Mth.floor(x), Mth.floor(y) + 1, Mth.floor(z));
            int light = level.getMaxLocalRawBrightness(pos);
            if (light < ShadowOpacity.MIN_LIGHT) {
                return 0;
            }
            double brightness = LightTexture.getBrightness(level.dimensionType(), light);
            return ShadowOpacity.alpha(params, height, brightness) * distanceFactor * Math.max(0.0, fade);
        }
        return 0;
    }

    private void grow(int n) {
        if (px.length < n) {
            px = new double[n];
            py = new double[n];
            pz = new double[n];
            nx = new double[n];
            ny = new double[n];
            nz = new double[n];
        }
    }
}
