package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.offkung.bhspellsx.entity.spells.embracing_bosom.EmbracingBosomAoe;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 2A: the rotating ring VFX for EmbracingBosomAoe. Draws each configured RingLayer as a
 * flat annulus/disc mesh in the XZ plane, built directly (no GeckoLib, no model file — Blockbench
 * models are cube-based and can't produce a clean ring).
 * <p>
 * === Post-mortem: the first version of this file used vanilla's {@code RenderType.eyes()} and
 * was completely invisible in-game. Root-caused against the decompiled/official-mapped source
 * (not from memory) before touching anything:
 * <p>
 * 1) {@code RenderType.eyes(ResourceLocation)} (SRG {@code m_110488_}) never calls
 * {@code setCullState(...)} in its factory. {@code CompositeState.CompositeStateBuilder}'s
 * default cull field is {@code CullStateShard(true)}, and {@code CullStateShard}'s own code shows
 * that "true" means "don't touch whatever cull state is already active" — i.e. it does NOT mean
 * "disable culling", it means "leave backface culling on". So eyes() renders single-sided.
 * <p>
 * 2) {@code rendertype_eyes}'s own JSON/GLSL (assets/minecraft/shaders/core/rendertype_eyes.*)
 * DOES multiply {@code texture(Sampler0, texCoord0) * vertexColor}, so per-vertex tint AND alpha
 * are read by the shader correctly. But the RenderType's {@code TransparencyStateShard} is
 * ADDITIVE_TRANSPARENCY, whose real GL blend func (confirmed in RenderStateShard's decompiled
 * source) is {@code blendFunc(ONE, ONE)} — source factor ONE, not SRC_ALPHA. That ignores our
 * vertex alpha entirely for blending purposes: fade-in/out and per-layer alpha would have had
 * zero visible effect even once the mesh itself was visible. Fixed below by hand-building a
 * TransparencyStateShard with {@code blendFunc(SRC_ALPHA, ONE)} — still additive/never-darkens,
 * but now actually driven by the alpha we set per vertex.
 * <p>
 * Because RenderStateShard's shard-instance fields are {@code protected} (package-private in
 * effect, from our mod's package), eyes() couldn't just be patched — the whole CompositeState is
 * built here from scratch instead, using RenderStateShard's public shard *constructors* and
 * {@code GameRenderer.getPositionColorTexShader()} (position_color_tex: Position+Color+UV0 only —
 * we don't use overlay/lightmap/normal, so there's no reason to carry the heavier NEW_ENTITY
 * format eyes() used).
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

    // Additive, but driven by SRC_ALPHA (not eyes()'s ONE,ONE) so our alpha/fade actually matters.
    // Never darkens the framebuffer — same "safe" additive property, just alpha-aware.
    private static final RenderStateShard.TransparencyStateShard ADDITIVE_ALPHA_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("bhspellsx_additive_alpha", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final Map<ResourceLocation, RenderType> RENDER_TYPE_CACHE = new HashMap<>();

    private static RenderType ringRenderType(ResourceLocation texture) {
        return RENDER_TYPE_CACHE.computeIfAbsent(texture, EmbracingBosomRingRenderer::buildRingRenderType);
    }

    private static RenderType buildRingRenderType(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_ALPHA_TRANSPARENCY)
                // Explicit — this is the fix. CullStateShard(false) actively disables GL culling;
                // omitting this call (as RenderType.eyes() does) leaves the default cull-on state.
                .setCullState(new RenderStateShard.CullStateShard(false))
                // Explicit full-bright: never sample the world lightmap, regardless of builder default.
                .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                // Depth TEST left at the builder default (LEQUAL) — still occluded by solid terrain.
                // Depth WRITE off so layers/segments blend via alpha instead of z-fighting.
                .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                .createCompositeState(false);
        return RenderType.create("bhspellsx_ring", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 256, false, true, state);
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

        Matrix4f positionMatrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(ringRenderType(layer.texture()));
        buildAnnulus(consumer, positionMatrix, outer, inner, r, g, b, a);

        poseStack.popPose();
    }

    /** x/z are local ring-plane coordinates (blocks); y is always 0 — the caller's pose already
     *  carries its yOffset via translate(). Backfaces are NOT culled (CullStateShard(false) in
     *  buildRingRenderType), so the ring reads correctly from below, per spec. */
    private static void buildAnnulus(VertexConsumer consumer, Matrix4f positionMatrix,
                                      float outer, float inner, float r, float g, float b, float a) {
        // uv normalized against the outer radius so the texture centre sits at the ring centre.
        float uvScale = outer > 1.0e-4f ? 1.0f / (2.0f * outer) : 0.0f;

        float prevCos = Mth.cos(0.0f);
        float prevSin = 0.0f;
        for (int i = 1; i <= SEGMENTS; i++) {
            float angleRad = (float) (i * (2.0 * Math.PI) / SEGMENTS);
            float cos = Mth.cos(angleRad);
            float sin = Mth.sin(angleRad);

            // Quad: inner@prev, outer@prev, outer@cur, inner@cur.
            quadVertex(consumer, positionMatrix, inner * prevCos, inner * prevSin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, outer * prevCos, outer * prevSin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, outer * cos, outer * sin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, inner * cos, inner * sin, r, g, b, a, uvScale);

            prevCos = cos;
            prevSin = sin;
        }
    }

    private static void quadVertex(VertexConsumer consumer, Matrix4f positionMatrix,
                                    float x, float z, float r, float g, float b, float a, float uvScale) {
        float u = x * uvScale + 0.5f;
        float v = z * uvScale + 0.5f;
        consumer.vertex(positionMatrix, x, 0.0f, z)
                .color(r, g, b, a)
                .uv(u, v)
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
