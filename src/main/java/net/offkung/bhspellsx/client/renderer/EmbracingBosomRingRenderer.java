package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.offkung.bhspellsx.entity.spells.embracing_bosom.EmbracingBosomAoe;
import org.joml.Matrix3f;
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
 * built here from scratch instead, using RenderStateShard's public shard *constructors*.
 * <p>
 * === Item 1 follow-up: invisible under Oculus/Iris shaderpacks. The RenderType above (built on
 * {@code GameRenderer.getPositionColorTexShader()}) rendered correctly without shaders but
 * vanished completely with a shaderpack loaded. Root-caused by disassembling the actual
 * {@code oculus-mc1.20.1-1.8.0.jar} Mixins shipped in this modpack — Iris does NOT generically
 * intercept arbitrary RenderTypes/shaders. It has one Mixin ({@code MixinGameRenderer}) that
 * {@code @Inject}s into ~27 *specific* {@code GameRenderer.get*Shader()} getters by exact method
 * identity, each redirected (when a shaderpack is active) to one of a closed, fixed
 * {@code ShaderKey} enum of real deferred gbuffers programs. Anything not on that list gets no
 * shaderpack routing at all.
 * <p>
 * {@code getPositionColorTexShader()} IS on that list, but its override
 * ({@code iris$overridePositionTexColorShader}) has no entity-context branch — it always routes
 * to {@code ShaderKey.TEXTURED_COLOR} ({@code gbuffers_textured}), the generic 2D/UI-quad
 * program, not the deferred terrain->entities->translucent->composite chain a world-space effect
 * needs to land in. That mismatch is almost certainly why it disappeared under Iris.
 * {@code getRendertypeEntityTranslucentEmissiveShader()} — the shader vanilla's
 * {@code RenderType.entityTranslucentEmissive()} uses — IS routed correctly: in normal (non
 * shadow-pass, non-block-entity) entity rendering it lands on {@code ShaderKey.ENTITIES_EYES_TRANS},
 * a real alpha-blended deferred entity gbuffer stage. (For reference: irons_spellbooks' own
 * ground-magic-glow RenderTypes, found in the deobfuscated jar, are built on
 * {@code getRendertypeEnergySwirlShader()}, which Iris routes to {@code ShaderKey.ENTITIES_CUTOUT}
 * — a real stage too, but binary-alpha-discard, not smooth blending, which would wreck our fades
 * and soft haze layers.)
 * <p>
 * Fixed by switching the shader to {@code getRendertypeEntityTranslucentEmissiveShader()} and the
 * vertex format back to {@code NEW_ENTITY} (that shader's GLSL declares Position/Color/UV0/UV1/
 * UV2/Normal). Its fragment shader actually samples the overlay texture
 * ({@code texelFetch(Sampler1, UV1, 0)}), unlike eyes()/energy_swirl/position_color_tex, so
 * {@code OverlayStateShard} has to go back to {@code true} with a real
 * {@code OverlayTexture.NO_OVERLAY} UV1 coordinate this time — leaving it {@code false} would
 * sample an unbound texture unit. Cull-off, our own alpha-respecting additive blend, and
 * depth-write-off are untouched: Iris only swaps the compiled GLSL program for a recognized
 * getter, not our other RenderStateShards.
 * <p>
 * Trade-off accepted: this shader applies vanilla's fake directional entity lighting
 * ({@code minecraft_mix_light}, driven by the mesh normal vs. two light directions) rather than
 * being purely emissive like eyes() was. Since the ring is flat with a fixed straight-up normal,
 * this is one uniform brightness multiplier across the whole mesh (not spatially varying), floored
 * so it stays visible in caves/at night, but dimmer than true full-bright — compensated for by
 * raising the per-layer alpha values below (see LAYERS).
 * <p>
 * === Spawn converge phase. Originally the rings just faded in at their landed radius, which read
 * as switching on rather than forming — disconnected from the Phase 2B dust burst
 * (EmbracingBosomAoe.spawnConvergeBurstTick), which visibly converges from radius 6 to 0.4 over
 * its first 8 ticks. Each layer now starts at RING_CONVERGE_START_RADIUS_MULT times its configured
 * radius and eases (cubic ease-out — fast, then decelerating to a rest, not linear) down to 1x
 * over RING_CONVERGE_TICKS, with the alpha fade-in driven by the exact same per-layer eased
 * progress value so radius and opacity always resolve together. RING_CONVERGE_TICKS (15) runs
 * longer than the dust's 8 ticks by design: the ease-out curve front-loads most of the visible
 * radius/alpha motion into roughly that same first ~8 ticks, so the two still read as one event,
 * while the ring gets a bit more time to visibly decelerate into place rather than snapping to
 * rest exactly when the dust does. Layers are staggered by radius (smaller settles first) over
 * RING_CONVERGE_STAGGER_TICKS so all five don't land on the same frame — this affects only the
 * radius/alpha convergence, not the base rotation, which always progresses continuously.
 * Rotation gets its own "spin-up" on top of the base angle: convergeSpinBonusDegrees() adds the
 * closed-form integral of speed*(RING_CONVERGE_SPIN_MULT-1)*(1-t/C)^3 (same cubic curve), so each
 * layer spins RING_CONVERGE_SPIN_MULT times its configured speed at t=0 and eases to exactly its
 * configured speed by t=RING_CONVERGE_TICKS — continuous (no pop) and shared by all layers
 * unstaggered, so the spin-up reads as one shared pulse rather than per-layer. Direction/sign and
 * each layer's relative speed ratio are untouched; only the transient bonus is added.
 * Does not touch the existing fade-out (FADE_OUT_TICKS/FADE_OUT_SHRINK) at all — that logic is
 * unchanged and still applies uniformly, unstaggered, at the end of the AoE's life.
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

    private static final int FADE_OUT_TICKS = 20;
    // Fade-out also contracts the radius slightly for a "flowing inward" feel — kept subtle.
    private static final float FADE_OUT_SHRINK = 0.18f;

    // Spawn converge phase — see class javadoc. Named/exposed per spec so these can be retuned
    // without touching the animation code below.
    private static final int RING_CONVERGE_TICKS = 15;
    private static final float RING_CONVERGE_START_RADIUS_MULT = 1.7f;
    private static final float RING_CONVERGE_SPIN_MULT = 3.0f;
    private static final float RING_CONVERGE_STAGGER_TICKS = 4.0f;

    // Alpha values bumped ~15-20% over the pre-Item-1 numbers (0.45/0.40/0.70/0.65/0.85) to
    // compensate for entityTranslucentEmissive's fake directional lighting dimming the ring
    // relative to eyes()'s pure emissive output — see class javadoc. First-pass estimate, not
    // measured in-game; retune from a screenshot if it's still off.
    private static final List<RingLayer> LAYERS = List.of(
            new RingLayer(ring("haze_soft"), 6.0f, 0.0f, 1.8f, 360f, 0.54f, AMBER_TINT_HEX, 0.02f),
            new RingLayer(ring("haze_patchy"), 5.4f, 0.0f, -2.7f, 360f, 0.48f, AMBER_TINT_HEX, 0.04f),
            new RingLayer(ring("vortex"), 6.0f, 0.0f, -7.2f, 360f, 0.82f, AMBER_TINT_HEX, 0.06f),
            new RingLayer(ring("cyclone"), 4.2f, 0.0f, 12.6f, 360f, 0.75f, AMBER_TINT_HEX, 0.08f),
            // Thin bright annulus rather than a full disc, matching the "rim" name/role. Smaller
            // bump here — it was already near the top of the range.
            new RingLayer(ring("highlight_rim"), 6.2f, 5.6f, -5.4f, 360f, 0.90f, AMBER_TINT_HEX, 0.10f));

    // Drives the per-layer converge stagger (see staggerOffsetTicks()) — derived from LAYERS
    // itself so it stays correct if the stack is retuned.
    private static final float MIN_LAYER_RADIUS = (float) LAYERS.stream().mapToDouble(RingLayer::radius).min().orElse(0.0);
    private static final float MAX_LAYER_RADIUS = (float) LAYERS.stream().mapToDouble(RingLayer::radius).max().orElse(1.0);

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
                // Item 1 fix: this exact shader is the one Iris/Oculus explicitly recognizes and
                // routes to a real alpha-blended deferred entity gbuffer stage (ShaderKey.
                // ENTITIES_EYES_TRANS) — see class javadoc.
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_ALPHA_TRANSPARENCY)
                // Explicit — this is the fix from the original invisibility bug. CullStateShard(false)
                // actively disables GL culling; omitting this call (as RenderType.eyes() does) leaves
                // the default cull-on state.
                .setCullState(new RenderStateShard.CullStateShard(false))
                // This shader never samples the world lightmap (UV2 is declared but unused in its
                // GLSL), so this is a no-op either way — false for clarity of intent.
                .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                // This shader DOES texelFetch the overlay sampler (Sampler1) — has to be true, with
                // a real OverlayTexture.NO_OVERLAY UV1 coordinate per vertex, or it samples garbage.
                .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                // Depth TEST left at the builder default (LEQUAL) — still occluded by solid terrain.
                // Depth WRITE off so layers/segments blend via alpha instead of z-fighting.
                .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                .createCompositeState(false);
        return RenderType.create("bhspellsx_ring", DefaultVertexFormat.NEW_ENTITY,
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

        float fadeOutAlpha = lifecycleFadeOutAlpha(activeTicks, partialTicks);
        if (fadeOutAlpha <= 0.0f) {
            return;
        }
        float shrink = lifecycleShrink(activeTicks, partialTicks);

        UUID uuid = entity.getUUID();
        for (int i = 0; i < LAYERS.size(); i++) {
            renderLayer(LAYERS.get(i), i, uuid, entity.tickCount, activeTicks, partialTicks, fadeOutAlpha, shrink,
                    poseStack, buffer);
        }
        // Intentionally no super.render() call — same as the NoopEntityRenderer this replaces,
        // this entity has no model/nametag/shadow to fall back on.
    }

    /** 1.0 until the last FADE_OUT_TICKS of the AoE's life, then ramps linearly to 0. Unchanged
     *  by the spawn converge work — the spawn fade-in now lives per-layer in renderLayer(). */
    private static float lifecycleFadeOutAlpha(int activeTicks, float partialTicks) {
        float t = activeTicks + partialTicks;
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

    /** Fraction of RING_CONVERGE_TICKS the given layer has advanced through its own (staggered)
     *  local converge window, clamped to [0, 1]. Only affects radius/alpha, not rotation — see
     *  class javadoc. */
    private static float layerConvergeProgress(RingLayer layer, int activeTicks, float partialTicks) {
        float globalT = activeTicks + partialTicks;
        float localT = Math.max(0.0f, globalT - staggerOffsetTicks(layer));
        return Mth.clamp(localT / RING_CONVERGE_TICKS, 0.0f, 1.0f);
    }

    /** Smaller-radius layers settle first: staggerOffsetTicks ranges from 0 (smallest radius in
     *  LAYERS) to RING_CONVERGE_STAGGER_TICKS (largest), interpolated by radius. */
    private static float staggerOffsetTicks(RingLayer layer) {
        float range = MAX_LAYER_RADIUS - MIN_LAYER_RADIUS;
        if (range < 1.0e-4f) {
            return 0.0f;
        }
        return (layer.radius() - MIN_LAYER_RADIUS) / range * RING_CONVERGE_STAGGER_TICKS;
    }

    /** Fast at first, decelerating to a rest — not linear. */
    private static float easeOutCubic(float p) {
        float inv = 1.0f - p;
        return 1.0f - inv * inv * inv;
    }

    /**
     * Extra angular travel (degrees) on top of the steady rotationSpeed*t progression: the closed-
     * form integral of speed*(RING_CONVERGE_SPIN_MULT-1)*(1-t/C)^3 from 0 to t (C =
     * RING_CONVERGE_TICKS), i.e. the same cubic ease-out curve as the radius, applied to angular
     * velocity instead of position. Continuous and exactly zero once t >= C — not staggered
     * per-layer (unlike radius/alpha), so every layer's spin-up reads as one shared pulse. Only
     * the sign/magnitude of the layer's own rotationSpeed feeds in, so direction and each layer's
     * relative speed ratio are preserved; this only adds a transient bonus on top.
     */
    private static float convergeSpinBonusDegrees(float rotationSpeed, int activeTicks, float partialTicks) {
        float t = Math.min(activeTicks + partialTicks, (float) RING_CONVERGE_TICKS);
        if (t <= 0.0f) {
            return 0.0f;
        }
        float p = t / RING_CONVERGE_TICKS;
        float oneMinusP = 1.0f - p;
        float oneMinusP4 = oneMinusP * oneMinusP * oneMinusP * oneMinusP;
        float integral = (RING_CONVERGE_TICKS / 4.0f) * (1.0f - oneMinusP4);
        return rotationSpeed * (RING_CONVERGE_SPIN_MULT - 1.0f) * integral;
    }

    private static void renderLayer(RingLayer layer, int layerIndex, UUID uuid, int tickCount, int activeTicks,
                                     float partialTicks, float fadeOutAlpha, float shrink,
                                     PoseStack poseStack, MultiBufferSource buffer) {
        float jitter = stableJitterDegrees(uuid, layerIndex, layer.startAngleJitter());
        float baseAngleDeg = jitter + layer.rotationSpeed() * (tickCount + partialTicks);
        float spinBonusDeg = convergeSpinBonusDegrees(layer.rotationSpeed(), activeTicks, partialTicks);
        float angleDeg = baseAngleDeg + spinBonusDeg;

        float convergeP = layerConvergeProgress(layer, activeTicks, partialTicks);
        float convergeEased = easeOutCubic(convergeP);
        float convergeMult = RING_CONVERGE_START_RADIUS_MULT
                + (1.0f - RING_CONVERGE_START_RADIUS_MULT) * convergeEased;

        float outer = layer.radius() * convergeMult * shrink;
        float inner = layer.innerRadius() * convergeMult * shrink;

        float r = ((layer.tintRGB() >> 16) & 0xFF) / 255.0f;
        float g = ((layer.tintRGB() >> 8) & 0xFF) / 255.0f;
        float b = (layer.tintRGB() & 0xFF) / 255.0f;
        // Alpha fade-in driven by the exact same eased progress as the radius, so they resolve
        // together, per spec.
        float a = layer.alpha() * convergeEased * fadeOutAlpha;

        poseStack.pushPose();
        poseStack.translate(0.0, layer.yOffset(), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(angleDeg));

        PoseStack.Pose pose = poseStack.last();
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(ringRenderType(layer.texture()));
        buildAnnulus(consumer, positionMatrix, normalMatrix, outer, inner, r, g, b, a);

        poseStack.popPose();
    }

    /** x/z are local ring-plane coordinates (blocks); y is always 0 — the caller's pose already
     *  carries its yOffset via translate(). Backfaces are NOT culled (CullStateShard(false) in
     *  buildRingRenderType), so the ring reads correctly from below, per spec. */
    private static void buildAnnulus(VertexConsumer consumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
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
            quadVertex(consumer, positionMatrix, normalMatrix, inner * prevCos, inner * prevSin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, normalMatrix, outer * prevCos, outer * prevSin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, normalMatrix, outer * cos, outer * sin, r, g, b, a, uvScale);
            quadVertex(consumer, positionMatrix, normalMatrix, inner * cos, inner * sin, r, g, b, a, uvScale);

            prevCos = cos;
            prevSin = sin;
        }
    }

    private static void quadVertex(VertexConsumer consumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
                                    float x, float z, float r, float g, float b, float a, float uvScale) {
        float u = x * uvScale + 0.5f;
        float v = z * uvScale + 0.5f;
        consumer.vertex(positionMatrix, x, 0.0f, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT) // declared by NEW_ENTITY but unused by this shader's GLSL
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
