package io.github.eyunya11.create_train_shadows.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.eyunya11.create_train_shadows.core.QuadFan;
import io.github.eyunya11.create_train_shadows.core.ShadowProjector;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Writes shadow polygons as black, translucent quads. Positions arrive in world coordinates and are
 * written relative to the entity, because the pose stack of an entity renderer has the entity as origin.
 */
final class ShadowDrawer implements ShadowProjector.Sink {
    private VertexConsumer consumer;
    private PoseStack.Pose pose;
    private double originX, originY, originZ;
    private int polygonCount;

    void begin(VertexConsumer consumer, PoseStack.Pose pose, double originX, double originY, double originZ) {
        this.consumer = consumer;
        this.pose = pose;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.polygonCount = 0;
    }

    /** How many polygons were written since {@link #begin}. */
    int polygonCount() {
        return polygonCount;
    }

    @Override
    public void polygon(double[] xs, double[] zs, float[] alphas, int n, double y) {
        polygonCount++;
        // The render type draws quads
        for (int quad = 0; quad < QuadFan.quadCount(n); quad++) {
            for (int corner = 0; corner < 4; corner++) {
                int i = QuadFan.index(n, quad, corner);
                vertex(xs[i], y, zs[i], alphas[i]);
            }
        }
    }

    private void vertex(double x, double y, double z, float alpha) {
        consumer.addVertex(pose.pose(), (float) (x - originX), (float) (y - originY), (float) (z - originZ))
            .setColor(0.0F, 0.0F, 0.0F, alpha)
            .setUv(0.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
