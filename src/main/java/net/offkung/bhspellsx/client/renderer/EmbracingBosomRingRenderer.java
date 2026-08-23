package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.offkung.bhspellsx.entity.spells.embracing_bosom.EmbracingBosomAoe;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

/**
 * Phase 2A: the rotating ring VFX for EmbracingBosomAoe. Draws each configured RingLayer as a
 * flat annulus/disc mesh in the XZ plane, built directly (no GeckoLib, no model file — Blockbench
 * models are cube-based and can't produce a clean ring).
 * <p>
 * Uses vanilla's {@code RenderType.eyes(ResourceLocation)} for every layer: NEW_ENTITY vertex
 * format (position/color/uv/overlay/lightmap/normal), additive transparency, no depth-write
 * (WriteMaskStateShard(colorWrite=true, depthWrite=false)), and — critically — it never binds a
 * LightmapStateShard, so the world lightmap has no effect on it: it's exactly the same trick
 * vanilla uses for glowing enderman/spider eyes, and gives full-bright emissive rendering for
 * free without a hand-rolled RenderType.CompositeState. Confirmed against the decompiled/official
 * mapping (RenderType.m_110488_ -> eyes) rather than assumed.
 * <p>
 * Depth TEST is left at its default (enabled), so the rings are still properly occluded by solid
 * terrain above them; only depth WRITE is disabled, so overlapping layers/segments blend via
 * their additive alpha instead of fighting each other or the ground for the depth buffer.
 */
public class EmbracingBosomRingRenderer extends EntityRenderer<EmbracingBosomAoe> {
    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/pig/pig.png");

    private static final String RING_TEX_PATH = "textures/entity/ring/";

    // Reuse the exact tint from Phase 2B — do not introduce a second colour constant.
    private static final int AMBER_TINT_HEX = 0xF0B23D;

    // 48 radial segments per layer, drawn as 48 quads (2 triangles each) = 96 triangles/layer.
    // 5 layers in the starting stack -> 480 triangles/frame while a ring is up, rebuilt fresh
    // each frame with no per-segment heap allocation (see renderLayer()).
    private static final int SEGMENTS = 48;

    private static final int FADE_IN_TICKS = 10;
    private static final int FADE_OUT_TICKS = 20;
    // Fade-out also contracts the radius slightly for a "flowing inward" feel — kept subtle.
    private static final float FADE_OUT_SHRINK = 0.18f;

    private static final List<RingLayer> LAYERS = List.of(
            new RingLayer(ring("haze_soft"), 6.0f, 0.0f, 2.0f, 360f, 0.45f, AMBER_TINT_HEX, 0.02f),
            new RingLayer(ring("haze_patchy"), 5.4f, 0.0f, -3.0f, 360f, 0.40f, AMBER_TINT_HEX, 0.04f),
            new RingLayer(ring("vortex"), 6.0f, 0.0f, -8.0f, 360f, 0.70f, AMBER_TINT_HEX, 0.06f),
            new RingLayer(ring("cyclone"), 4.2f, 0.0f, 14.0f, 360f, 0.65f, AMBER_TINT_HEX, 0.08f),
            // Thin bright annulus rather than a full disc, matching the "rim" name/role.
            new RingLayer(ring("highlight_rim"), 6.2f, 5.6f, -6.0f, 360f, 0.85f, AMBER_TINT_HEX, 0.10f));

    private static ResourceLocation ring(String name) {
        return ResourceLocation.fromNamespaceAndPath("bhspellsx", RING_TEX_PATH + name + ".png");
    }

    public EmbracingBosomRingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EmbracingBosomAoe entity) {
        return DUMMY_TEXTURE;
    }

    @Override
    public void render(EmbracingBosomAoe entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        int activeTicks = Math.max(0, entity.tickCount - entity.getDelay());
        float fadeAlpha = lifecycleFadeAlpha(activeTicks, partialTicks);
        if (fadeAlpha <= 0.0f) {
            return;
        }
        float shrink = lifecycleShrink(activeTicks, partialTicks);

        UUID uuid = entity.getUUID();
        for (int i = 0; i < LAYERS.size(); i++) {
            renderLayer(LAYERS.get(i), i, uuid, entity.tickCount, partialTicks, fadeAlpha, shrink,
                    poseStack, buffer);
        }
        // Intentionally no super.render() call — same as the NoopEntityRenderer this replaces,
        // this entity has no model/nametag/shadow to fall back on.
    }

    private static float lifecycleFadeAlpha(int activeTicks, float partialTicks) {
        float t = activeTicks + partialTicks;
        if (t < FADE_IN_TICKS) {
            return Mth.clamp(t / FADE_IN_TICKS, 0.0f, 1.0f);
        }
        float fadeOutStart = EmbracingBosomAoe.LIFETIME_TICKS - FADE_OUT_TICKS;
        if (t > fadeOutStart) {
            return 1.0f - Mth.clamp((t - fadeOutStart) / FADE_OUT_TICKS, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    private static float lifecycleShrink(int activeTicks, float partialTicks) {
        float t = activeTicks + partialTicks;
        float fadeOutStart = EmbracingBosomAoe.LIFETIME_TICKS - FADE_OUT_TICKS;
        if (t > fadeOutStart) {
            float p = Mth.clamp((t - fadeOutStart) / FADE_OUT_TICKS, 0.0f, 1.0f);
            return 1.0f - FADE_OUT_SHRINK * p;
        }
        return 1.0f;
    }

    private static void renderLayer(RingLayer layer, int layerIndex, UUID uuid, int tickCount,
                                     float partialTicks, float fadeAlpha, float shrink,
                                     PoseStack poseStack, MultiBufferSource buffer) {
        float jitter = stableJitterDegrees(uuid, layerIndex, layer.startAngleJitter());
        float angleDeg = jitter + layer.rotationSpeed() * (tickCount + partialTicks);

        float outer = layer.radius() * shrink;
        float inner = layer.innerRadius() * shrink;

        float r = ((layer.tintRGB() >> 16) & 0xFF) / 255.0f;
        float g = ((layer.tintRGB() >> 8) & 0xFF) / 255.0f;
        float b = (layer.tintRGB() & 0xFF) / 255.0f;
        float a = layer.alpha() * fadeAlpha;

        poseStack.pushPose();
        poseStack.translate(0.0, layer.yOffset(), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(angleDeg));

        PoseStack.Pose pose = poseStack.last();
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(layer.texture()));

        // uv normalized against the (possibly shrunk) outer radius so the texture scales in step
        // with the mesh during fade-out rather than sliding across it.
        float uvScale = outer > 1.0e-4f ? 1.0f / (2.0f * outer) : 0.0f;

        float prevCos = Mth.cos(0.0f);
        float prevSin = 0.0f;
        for (int i = 1; i <= SEGMENTS; i++) {
            float angleRad = (float) (i * (2.0 * Math.PI) / SEGMENTS);
            float cos = Mth.cos(angleRad);
            float sin = Mth.sin(angleRad);

            // Quad: inner@prev, outer@prev, outer@cur, inner@cur.
            quadVertex(consumer, positionMatrix, normalMatrix, inner * prevCos, inner * prevSin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, normalMatrix, outer * prevCos, outer * prevSin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, normalMatrix, outer * cos, outer * sin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, normalMatrix, inner * cos, inner * sin, r, g, b, a, uvScale);

            prevCos = cos;
            prevSin = sin;
        }

        poseStack.popPose();
    }

    /** x/z are local ring-plane coordinates (blocks); y is always 0 — the layer's own pose already
     *  carries its yOffset via translate(). Backfaces are not culled (RenderType.eyes doesn't set
     *  a CullStateShard), so the ring reads correctly from below, per spec. */
    private static void quadVertex(VertexConsumer consumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
                                    float x, float z, float r, float g, float b, float a, float uvScale) {
        float u = x * uvScale + 0.5f;
        float v = z * uvScale + 0.5f;
        consumer.vertex(positionMatrix, x, 0.0f, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880) // LightTexture.FULL_BRIGHT — unused by RenderType.eyes (no lightmap
                               // bound) but the vertex format still requires a value.
                .normal(normalMatrix, 0.0f, 1.0f, 0.0f)
                .endVertex();
    }

    /**
     * Deterministic per-entity, per-layer start-angle jitter in [0, amplitude): a pure hash of
     * (uuid, layerIndex), so it's stable across every frame without needing to cache anything on
     * spawn — "random once at spawn" without the mutable state.
     */
    private static float stableJitterDegrees(UUID uuid, int layerIndex, float amplitudeDegrees) {
        if (amplitudeDegrees <= 0.0f) {
            return 0.0f;
        }
        long h = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()
                ^ (layerIndex * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        float unit = (float) ((h >>> 11) * 0x1.0p-53); // [0, 1)
        return unit * amplitudeDegrees;
    }
}
