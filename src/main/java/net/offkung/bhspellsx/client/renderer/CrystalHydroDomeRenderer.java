package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeAoe;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeConstants;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.offkung.bhspellsx.client.renderer.CrystalHydroDomeVisuals.*;

/**
 * Phase 2A: a translucent hemisphere shell for CrystalHydroDomeAoe, replacing the Phase 1
 * {@code NoopRenderer} registration. Built as a hand-rolled CPU mesh (no model file, no
 * GeckoLib) — same reasoning as {@code EmbracingBosomRingRenderer}: there's no Blockbench way
 * to author a smooth sphere segment.
 * <p>
 * === RenderType: copied, not reused, from EmbracingBosomRingRenderer.
 * <p>
 * Per MERGE.md's "READ BEFORE TOUCHING embracing_bosom's RING RENDERER" warning, that file is
 * already merged upstream and must not be modified or refactored to share code with this one.
 * The composite state below ({@code buildDomeRenderType()}) is therefore a deliberate,
 * byte-for-byte copy of {@code EmbracingBosomRingRenderer.buildRingRenderType()}'s shader,
 * blend, cull, lightmap, overlay, and depth-write-mask shards — see that class's own javadoc
 * (and MERGE.md) for the full root-cause writeup of why each shard is what it is; the summary:
 * {@code GameRenderer.getRendertypeEntityTranslucentEmissiveShader()} is the one shader that is
 * simultaneously alpha-blended (unlike {@code eyes()}'s {@code ONE,ONE} additive, which ignores
 * vertex alpha) and correctly routed by Iris/Oculus to a real translucent entity gbuffers stage
 * (unlike {@code getPositionColorTexShader()}, which Iris routes to a flat 2D/UI program) while
 * still being smooth rather than binary-alpha-discard (unlike irons_spellbooks' own
 * {@code getRendertypeEnergySwirlShader()}, which Iris routes to a cutout stage).
 * <p>
 * The one deliberate difference from the ring: the dome has no texture of its own (a flat tint,
 * not a painted haze/vortex sprite), so it binds Forge's generic {@code forge:textures/white.png}
 * instead of a per-layer sprite — the shader still multiplies sampled-texture-color by
 * vertex-color, so a white texture lets the vertex color (light cyan, see
 * {@link CrystalHydroDomeVisuals}) come through unmodified.
 * <p>
 * === Frustum culling (recon Q4).
 * <p>
 * {@code AoeEntity.getDimensions()} hardcodes bounding-box height to 1.2 blocks regardless of
 * {@code setRadius()} (confirmed by decompile — see
 * {@code docs/recon/crystal_hydro_dome_phase2_part2_render.md} Q4). At this dome's 10-block
 * radius/height, that box is far smaller than the actual hemisphere, so vanilla's default
 * frustum-culling {@code shouldRender()} check would cull the whole dome the moment its
 * (tiny, ground-level) entity box left the camera frustum, even while most of the dome's
 * visible surface is on-screen. Fixed the same way three existing irons_spellbooks renderers
 * (EldritchBlastRenderer, RayOfFrostRenderer, SunbeamRenderer) fix the identical problem for
 * their own oversized beam entities: {@link #shouldRender} unconditionally returns {@code true}.
 * <p>
 * A separate, unrelated 64-block distance cutoff ({@link CrystalHydroDomeVisuals#MAX_RENDER_DISTANCE})
 * is applied inside {@link #render} instead, as a plain performance guard — not a correctness
 * fix, since {@code shouldRender} already always renders regardless of distance.
 * <p>
 * === Mesh: built once, alpha computed per frame (recon Q2).
 * <p>
 * Per-vertex position and normal are computed once and cached in {@link #MESH_CACHE}, keyed by
 * (radius, latitude rings, segments) via {@link MeshKey} — see {@link #buildMesh}. Every frame,
 * {@link #render} computes a single once-per-frame {@link #insideFactor} (how far the camera is
 * into the hemisphere), then per vertex sums three alpha terms — {@link #fresnelEdgeTerm}
 * (view-angle dependent grazing-angle brightening, the original outside-facing look, left
 * unchanged), the inside term ({@code INSIDE_ALPHA * insideFactor}, added so the dome doesn't
 * read as nearly invisible when the camera is deep inside it and every surface point is
 * face-on), and {@link #rimFactor} (a fixed bright band near the dome's base, visible from
 * both sides) — clamps the sum to 0..1, then applies the fade-in multiplier. No heap allocation
 * happens in the per-frame path itself, only primitive float/double math over the cached arrays.
 */
public class CrystalHydroDomeRenderer extends EntityRenderer<CrystalHydroDomeAoe> {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("forge", "textures/white.png");

    // Same additive-but-alpha-respecting blend as embracing_bosom's ring (blendFunc(SRC_ALPHA, ONE)
    // instead of eyes()'s ONE,ONE) — never darkens the framebuffer, but our fresnel alpha actually
    // drives the blend instead of being silently discarded.
    private static final RenderStateShard.TransparencyStateShard ADDITIVE_ALPHA_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("bhspellsx_dome_additive_alpha", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderType RENDER_TYPE = buildDomeRenderType();

    private static RenderType buildDomeRenderType() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                // Copied from EmbracingBosomRingRenderer.buildRingRenderType() — see class javadoc.
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                .setTextureState(new RenderStateShard.TextureStateShard(WHITE_TEXTURE, false, false))
                .setTransparencyState(ADDITIVE_ALPHA_TRANSPARENCY)
                // Backfaces drawn — required for a hemisphere seen from both inside and outside.
                .setCullState(new RenderStateShard.CullStateShard(false))
                // This shader never samples the world lightmap; false for clarity of intent, same
                // as the ring. Full-bright is instead baked into the per-vertex UV2 below.
                .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                // This shader DOES texelFetch the overlay sampler — must be true, paired with a
                // real OverlayTexture.NO_OVERLAY UV1 per vertex, or it samples garbage.
                .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                // Depth test on (still occluded by solid terrain/blocks); depth write off so the
                // dome's own near/far hemisphere surfaces blend via alpha instead of z-fighting.
                .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                .createCompositeState(false);
        return RenderType.create("bhspellsx_crystal_hydro_dome", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    // Lotus-only RenderType (Phase 2B fix). Dome shell above is untouched (still additive,
    // untouched shader/blend/state).
    //
    // === Why the previous attempt rendered black.
    //
    // getRendertypeEntityTranslucentShader()'s fragment shader (rendertype_entity_translucent.fsh,
    // confirmed by extracting it from the vanilla client-extra jar this session) ends with
    // `color *= lightMapColor;` — it always multiplies the final color by the interpolated world
    // lightmap sample, unconditionally. The previous composite state copied the dome's
    // `.setLightmapState(new LightmapStateShard(false))` onto this shader without checking that
    // assumption — `false` here means the RenderType machinery never binds/updates the lightmap
    // texture unit for this draw, so the shader's mandatory lightmap multiply reads an unbound
    // sampler and comes back effectively black, and black times any vertex color is black.
    // getRendertypeEntityTranslucentEmissiveShader() (rendertype_entity_translucent_emissive.fsh,
    // same extraction) has NO such multiply at all — "emissive" here specifically means the
    // lightmap is never sampled in the first place, so `LightmapStateShard(false)` is correct for
    // it (this is exactly why the dome shell, built on the emissive shader, was never black).
    //
    // === Fix: reuse vanilla's own entityTranslucentEmissive(texture) factory directly, not a
    // hand-built copy.
    //
    // Checked (RenderType.java, official-mapped source, this session) what
    // `RenderType.entityTranslucentEmissive(ResourceLocation)` actually builds:
    // shader=RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER (same shader the dome uses),
    // transparency=TRANSLUCENT_TRANSPARENCY (real SRC_ALPHA/ONE_MINUS_SRC_ALPHA, "add" blend func
    // — exactly the standard alpha blend asked for), cull=NO_CULL, writeMask=COLOR_WRITE (color
    // yes, depth NO — depth write is already off, unlike the earlier assumption that it wasn't),
    // overlay=OVERLAY (a real OverlayStateShard(true), same as what the dome/lotus already pass
    // per-vertex as OverlayTexture.NO_OVERLAY), and no explicit depth-test override, which means
    // the CompositeStateBuilder's own default (RenderStateShard.LEQUAL_DEPTH_TEST — "on") applies,
    // same as every other RenderType in this file that doesn't touch it. Every shard the spec asks
    // for is already exactly what this factory produces — there is nothing left to override, so
    // this is the vanilla RenderType instance itself, not a copy of its state.
    private static final RenderType LOTUS_RENDER_TYPE = RenderType.entityTranslucentEmissive(WHITE_TEXTURE);

    private record MeshKey(double radius, int ringDensityPerQuarter, int segments, double lowerExtent) {
    }

    private record MeshData(float[] posX, float[] posY, float[] posZ,
                             float[] normX, float[] normY, float[] normZ,
                             int[] quadVertexIndices) {
    }

    private static final Map<MeshKey, MeshData> MESH_CACHE = new ConcurrentHashMap<>();

    private static MeshData meshFor(double radius, int ringDensityPerQuarter, int segments, double lowerExtent) {
        return MESH_CACHE.computeIfAbsent(new MeshKey(radius, ringDensityPerQuarter, segments, lowerExtent),
                key -> buildMesh(key.radius(), key.ringDensityPerQuarter(), key.segments(), key.lowerExtent()));
    }

    /**
     * VISUAL shell only — phi now runs from the top pole (0) past the equator (pi/2) down to
     * {@code phiMax = acos(-lowerExtent)} (Phase 2A.3), reaching y = -lowerExtent*radius at the
     * bottom row. Row count is derived from {@code ringDensityPerQuarter} (rings per pi/2 of arc,
     * the original Phase 2A density) scaled by {@code phiMax / (pi/2)} and rounded, so ring
     * spacing per arc length stays roughly constant regardless of how far down the shell reaches,
     * instead of hardcoding a new row count. At the shipped LOWER_EXTENT=0.7/LAT_RINGS=12, this
     * is 18 rows (phiMax ~= 134.4 degrees, vs. 90 degrees originally).
     * <p>
     * Unique vertices: (latRings + 1) * segments. Quads: latRings * segments, each referencing 4
     * shared vertex indices — emitted per frame in {@link #render}. theta in [0, 2*pi) matches
     * CrystalHydroDomeAoe's own hemisphere convention (its now-removed spawnBoundaryVfx() used
     * the identical parameterization for its particle sampling).
     * <p>
     * This never touches CrystalHydroDomeConstants/isInside — the gameplay hemisphere shape is
     * untouched; only this visual mesh's silhouette extends further down.
     */
    private static MeshData buildMesh(double radius, int ringDensityPerQuarter, int segments, double lowerExtent) {
        double phiMax = Math.acos(-lowerExtent);
        int latRings = Math.max(1, (int) Math.round(ringDensityPerQuarter * (phiMax / (Math.PI / 2.0))));

        int vertexCount = (latRings + 1) * segments;
        float[] posX = new float[vertexCount];
        float[] posY = new float[vertexCount];
        float[] posZ = new float[vertexCount];
        float[] normX = new float[vertexCount];
        float[] normY = new float[vertexCount];
        float[] normZ = new float[vertexCount];

        for (int row = 0; row <= latRings; row++) {
            double phi = (double) row / latRings * phiMax;
            double sinPhi = Math.sin(phi);
            double cosPhi = Math.cos(phi);
            for (int col = 0; col < segments; col++) {
                double theta = (double) col / segments * (2.0 * Math.PI);
                double nx = sinPhi * Math.cos(theta);
                double ny = cosPhi;
                double nz = sinPhi * Math.sin(theta);
                int i = row * segments + col;
                posX[i] = (float) (nx * radius);
                posY[i] = (float) (ny * radius);
                posZ[i] = (float) (nz * radius);
                normX[i] = (float) nx;
                normY[i] = (float) ny;
                normZ[i] = (float) nz;
            }
        }

        int quadCount = latRings * segments;
        int[] quadVertexIndices = new int[quadCount * 4];
        int q = 0;
        for (int row = 0; row < latRings; row++) {
            for (int col = 0; col < segments; col++) {
                int nextCol = (col + 1) % segments;
                int a = row * segments + col;
                int b = row * segments + nextCol;
                int c = (row + 1) * segments + nextCol;
                int d = (row + 1) * segments + col;
                quadVertexIndices[q++] = a;
                quadVertexIndices[q++] = b;
                quadVertexIndices[q++] = c;
                quadVertexIndices[q++] = d;
            }
        }
        return new MeshData(posX, posY, posZ, normX, normY, normZ, quadVertexIndices);
    }

    public CrystalHydroDomeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalHydroDomeAoe entity) {
        return WHITE_TEXTURE;
    }

    /** Always true — see class javadoc's frustum-culling section (recon Q4). The camera-distance
     *  cutoff is a separate, plain performance guard applied in {@link #render}, not here. */
    @Override
    public boolean shouldRender(CrystalHydroDomeAoe entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(CrystalHydroDomeAoe entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        Vec3 center = entity.position();
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float time = entity.tickCount + partialTicks;

        // Counter bolts reach the attacker wherever they are (no range limit), so they're drawn
        // before the dome's own distance cutoff — an attacker far from the dome still sees theirs.
        renderCounterBolts(entity, time, (float) (camPos.x - center.x), (float) (camPos.y - center.y),
                (float) (camPos.z - center.z), poseStack.last().pose(), poseStack.last().normal(), buffer);

        if (camPos.distanceToSqr(center) > CrystalHydroDomeVisuals.MAX_RENDER_DISTANCE_SQ) {
            return;
        }

        float fadeAlpha = fadeInAlpha(entity.tickCount, partialTicks);
        if (fadeAlpha <= 0.0f) {
            return;
        }

        MeshData mesh = meshFor(CrystalHydroDomeConstants.RADIUS, CrystalHydroDomeVisuals.LAT_RINGS,
                CrystalHydroDomeVisuals.SEGMENTS, CrystalHydroDomeVisuals.LOWER_EXTENT);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);

        // Phase 2D: 0 alive, otherwise which ending is playing and how long ago it started.
        int endState = entity.getEndState();
        boolean ending = endState != CrystalHydroDomeAoe.END_STATE_ALIVE;
        float endAge = ending ? Math.max(0.0f, time - entity.getEndStartTick()) : 0.0f;

        // Camera position relative to the dome center, in the same local space as the cached mesh
        // vertices — the PoseStack the dispatcher hands us is already camera-relative/entity-anchored,
        // so local-space math here (rather than transforming every vertex to world space) matches it.
        float camLocalX = (float) (camPos.x - center.x);
        float camLocalY = (float) (camPos.y - center.y);
        float camLocalZ = (float) (camPos.z - center.z);

        renderShell(mesh, entity.getId(), time, endState, endAge, fadeAlpha, camLocalX, camLocalY, camLocalZ,
                positionMatrix, normalMatrix, consumer);
        renderLotus(time, endState, endAge, fadeAlpha, positionMatrix, normalMatrix, buffer);
    }

    private static void renderShell(MeshData mesh, int seed, float time, int endState, float endAge, float fadeAlpha,
                                    float camLocalX, float camLocalY, float camLocalZ,
                                    Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer consumer) {
        if (endState == CrystalHydroDomeAoe.END_STATE_BROKEN) {
            renderShards(seed, endAge, fadeAlpha, positionMatrix, normalMatrix, consumer);
            return;
        }
        // Natural end: the shell swells out over the knockback ring (10 -> 13) while fading.
        float shellScale = 1.0f;
        if (endState == CrystalHydroDomeAoe.END_STATE_NATURAL) {
            float p = Mth.clamp(endAge / END_WAVE_TICKS, 0.0f, 1.0f);
            shellScale = 1.0f + (END_SHELL_EXPAND - 1.0f) * easeOut(p);
            fadeAlpha *= (float) Math.pow(1.0f - p, 1.3f);
            renderEndWave(endAge, positionMatrix, normalMatrix, consumer);
        }
        if (fadeAlpha <= 0.0f) {
            return;
        }

        // Once per frame, not per vertex — see insideFactor()'s own javadoc for the metric.
        float insideFactor = insideFactor(camLocalX, camLocalY, camLocalZ);

        int[] indices = mesh.quadVertexIndices();
        float[] posX = mesh.posX();
        float[] posY = mesh.posY();
        float[] posZ = mesh.posZ();
        float[] normX = mesh.normX();
        float[] normY = mesh.normY();
        float[] normZ = mesh.normZ();

        for (int vi : indices) {
            float lx = posX[vi] * shellScale;
            float ly = posY[vi] * shellScale;
            float lz = posZ[vi] * shellScale;
            float nx = normX[vi];
            float ny = normY[vi];
            float nz = normZ[vi];

            float edgeTerm = fresnelEdgeTerm(lx, ly, lz, nx, ny, nz, camLocalX, camLocalY, camLocalZ);
            float rim = rimFactor(ly);
            float alpha = edgeTerm
                    + CrystalHydroDomeVisuals.INSIDE_ALPHA * insideFactor
                    + CrystalHydroDomeVisuals.RIM_ALPHA * rim;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f) * fadeAlpha;

            consumer.vertex(positionMatrix, lx, ly, lz)
                    .color(CrystalHydroDomeVisuals.COLOR_R, CrystalHydroDomeVisuals.COLOR_G, CrystalHydroDomeVisuals.COLOR_B, alpha)
                    .uv(0.5f, 0.5f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(normalMatrix, nx, ny, nz)
                    .endVertex();
        }

        renderWaterStreaks(seed, time, shellScale, fadeAlpha, positionMatrix, normalMatrix, consumer);

        if (endState == CrystalHydroDomeAoe.END_STATE_ALIVE) {
            renderShellLightning(seed, time, fadeAlpha, camLocalX, camLocalY, camLocalZ,
                    positionMatrix, normalMatrix, consumer);
        }
    }

    // === Water pattern on the shell: soft wavy streaks that flow around the dome and fade in/out
    // in staggered cycles (each cycle re-rolls height/length/wave), plus short bright flecks.
    // Ribbons lie ON the sphere (latitude-wise width), not camera-facing, so they read as part of
    // the water surface from inside and outside. Additive, same batch as the shell.
    private static void renderWaterStreaks(int seed, float time, float scale, float alpha,
                                           Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        if (alpha <= 0.0f) {
            return;
        }
        float radius = ((float) CrystalHydroDomeConstants.RADIUS + WATER_STREAK_SURFACE_OFFSET) * scale;
        for (int i = 0; i < WATER_STREAK_COUNT; i++) {
            float local = time / WATER_STREAK_CYCLE_TICKS + hash(seed, i, 61, 0);
            int cycle = Mth.floor(local);
            float life = Mth.sin((local - cycle) * Mth.PI);
            float a = alpha * life * Mth.lerp(hash(seed, i, cycle, 62), WATER_STREAK_ALPHA_MIN, WATER_STREAK_ALPHA_MAX);
            if (a <= 0.003f) {
                continue;
            }
            float height = Mth.lerp(hash(seed, i, cycle, 63), WATER_STREAK_MIN_HEIGHT, WATER_STREAK_MAX_HEIGHT);
            float span = Mth.lerp(hash(seed, i, cycle, 64), WATER_STREAK_MIN_SPAN_DEG, WATER_STREAK_MAX_SPAN_DEG)
                    * Mth.DEG_TO_RAD;
            float speed = Mth.lerp(hash(seed, i, cycle, 65), WATER_STREAK_MIN_SPEED_DEG, WATER_STREAK_MAX_SPEED_DEG)
                    * Mth.DEG_TO_RAD;
            float width = Mth.lerp(hash(seed, i, cycle, 66), WATER_STREAK_MIN_WIDTH, WATER_STREAK_MAX_WIDTH);
            float start = hash(seed, i, cycle, 67) * Mth.TWO_PI + time * speed;
            surfaceStreak(radius, (float) Math.acos(height), start, span, width / radius,
                    WATER_STREAK_WAVE * hash(seed, i, cycle, 68), 2.0f + 3.0f * hash(seed, i, cycle, 69),
                    hash(seed, i, cycle, 70) * Mth.TWO_PI + time * 0.03f, WATER_STREAK_SEGMENTS,
                    WATER_STREAK_R, WATER_STREAK_G, WATER_STREAK_B, a, position, normal, consumer);
        }
        for (int i = 0; i < WATER_FLECK_COUNT; i++) {
            float local = time / WATER_FLECK_CYCLE_TICKS + hash(seed, i, 71, 0);
            int cycle = Mth.floor(local);
            float life = Mth.sin((local - cycle) * Mth.PI);
            float a = alpha * life * WATER_FLECK_ALPHA;
            if (a <= 0.003f) {
                continue;
            }
            float height = Mth.lerp(hash(seed, i, cycle, 72), WATER_STREAK_MIN_HEIGHT, WATER_STREAK_MAX_HEIGHT);
            float span = Mth.lerp(hash(seed, i, cycle, 73), 2.0f, 6.0f) * Mth.DEG_TO_RAD;
            float start = hash(seed, i, cycle, 74) * Mth.TWO_PI + time * WATER_STREAK_MAX_SPEED_DEG * Mth.DEG_TO_RAD;
            surfaceStreak(radius, (float) Math.acos(height), start, span, WATER_FLECK_WIDTH / radius,
                    0.0f, 0.0f, 0.0f, 4, 0.9f, 0.97f, 1.0f, a, position, normal, consumer);
        }
    }

    /** One tapered ribbon along a latitude of the sphere, from theta `start` over `span`, with a
     *  sine wobble in latitude. Two quads per segment (edge → center → edge) so the sides feather
     *  to alpha 0 instead of ending in a hard line. */
    private static void surfaceStreak(float radius, float phi0, float start, float span, float halfWidthRad,
                                      float waveAmp, float waveFreq, float wavePhase, int segments,
                                      float r, float g, float b, float alpha,
                                      Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        for (int s = 0; s < segments; s++) {
            for (int side = 0; side < 2; side++) {
                for (int corner = 0; corner < 4; corner++) {
                    float f = (s + (corner >= 2 ? 1.0f : 0.0f)) / segments;
                    // col 0 = outer edge, 1 = center, 2 = other outer edge.
                    int col = side + (corner == 1 || corner == 2 ? 1 : 0);
                    float taper = Mth.sin(f * Mth.PI);
                    float theta = start + span * f;
                    float phi = phi0 + waveAmp * Mth.sin(theta * waveFreq + wavePhase)
                            + (col - 1) * halfWidthRad * taper;
                    phi = Mth.clamp(phi, 0.03f, Mth.HALF_PI);
                    float sinPhi = Mth.sin(phi);
                    vertex(consumer, position, normal, radius * sinPhi * Mth.cos(theta), radius * Mth.cos(phi),
                            radius * sinPhi * Mth.sin(theta), r, g, b, col == 1 ? alpha * taper : 0.0f);
                }
            }
        }
    }

    private static float easeOut(float p) {
        float inv = 1.0f - p;
        return 1.0f - inv * inv * inv;
    }

    private static float smooth(float p) {
        return p * p * (3.0f - 2.0f * p);
    }

    // === Phase 2D natural end: expanding water rings (floor band + short fading wall) marking
    // the 10-13 block knockback zone.
    private static void renderEndWave(float endAge, Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        for (int ring = 0; ring < END_WAVE_RINGS; ring++) {
            float age = endAge - ring * END_WAVE_STAGGER_TICKS;
            if (age < 0.0f || age >= END_WAVE_TICKS) {
                continue;
            }
            float p = age / END_WAVE_TICKS;
            float radius = Mth.lerp(easeOut(p), END_WAVE_START_RADIUS, END_WAVE_END_RADIUS);
            float width = Mth.lerp(p, END_WAVE_WIDTH_START, END_WAVE_WIDTH_END);
            float alpha = END_WAVE_ALPHA * (float) Math.pow(1.0f - p, 1.5f) * (ring == 0 ? 1.0f : 0.6f);
            renderRing(radius, width, SIGIL_Y + 0.02f, END_WAVE_R, END_WAVE_G, END_WAVE_B, alpha,
                    position, normal, consumer);
            renderRing(radius, width * 0.3f, SIGIL_Y + 0.03f, 0.9f, 0.97f, 1.0f, alpha,
                    position, normal, consumer);
            float height = END_WAVE_WALL_HEIGHT * (1.0f - 0.5f * p);
            for (int i = 0; i < SIGIL_SEGMENTS; i++) {
                float c0 = RING_COS[i], s0 = RING_SIN[i], c1 = RING_COS[i + 1], s1 = RING_SIN[i + 1];
                vertex(consumer, position, normal, c0 * radius, 0.0f, s0 * radius,
                        END_WAVE_R, END_WAVE_G, END_WAVE_B, alpha * 0.7f);
                vertex(consumer, position, normal, c1 * radius, 0.0f, s1 * radius,
                        END_WAVE_R, END_WAVE_G, END_WAVE_B, alpha * 0.7f);
                vertex(consumer, position, normal, c1 * radius, height, s1 * radius,
                        END_WAVE_R, END_WAVE_G, END_WAVE_B, 0.0f);
                vertex(consumer, position, normal, c0 * radius, height, s0 * radius,
                        END_WAVE_R, END_WAVE_G, END_WAVE_B, 0.0f);
            }
        }
    }

    // === Phase 2D broken end: irregular crystal shards. A jittered triangle mesh over the upper
    // shell (random diagonal per cell) is grouped into Voronoi cells around random seed points on
    // the sphere, so shards differ in size and outline and their edges read as cracks, not a grid.
    // Built once; each dome rotates the whole pattern by a per-entity yaw so breaks don't repeat.
    // Motion: a short per-shard "crack" hold (gaps open in place + flash), then fly out, fall, spin,
    // shrink and fade. Triangles go out as QUADS with the last vertex repeated. No particles/packets.
    private record ShardSet(float[] tri, int[] start, int[] count, float[] cx, float[] cy, float[] cz,
                            float[] dirX, float[] dirY, float[] dirZ, float[] speed,
                            float[] axisX, float[] axisY, float[] axisZ, float[] spin, float[] shade,
                            float[] delay) { }

    private static final ShardSet SHARDS = buildShards();

    private static ShardSet buildShards() {
        int rows = SHARD_ROWS, cols = SHARD_COLS;
        float radius = (float) CrystalHydroDomeConstants.RADIUS;
        double phiMax = Math.toRadians(SHARD_MAX_POLAR_DEG);
        double phiStep = phiMax / rows, thetaStep = 2.0 * Math.PI / cols;

        float[] vx = new float[(rows + 1) * cols], vy = new float[vx.length], vz = new float[vx.length];
        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = row * cols + col;
                double phi = row * phiStep;
                double theta = col * thetaStep;
                if (row > 0) {
                    theta += (hash(i, 31, 0, 0) - 0.5f) * 2.0f * SHARD_JITTER * thetaStep;
                    if (row < rows) {
                        phi += (hash(i, 32, 0, 0) - 0.5f) * 2.0f * SHARD_JITTER * phiStep;
                    }
                }
                vx[i] = (float) (radius * Math.sin(phi) * Math.cos(theta));
                vy[i] = (float) (radius * Math.cos(phi));
                vz[i] = (float) (radius * Math.sin(phi) * Math.sin(theta));
            }
        }

        int cells = SHARD_CELLS;
        float[] seedX = new float[cells], seedY = new float[cells], seedZ = new float[cells];
        for (int s = 0; s < cells; s++) {
            // Uniform over the spherical cap: uniform y, uniform angle.
            float y = Mth.lerp(hash(s, 41, 0, 0), SHARD_SEED_MIN_Y, 1.0f);
            float ring = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
            float theta = hash(s, 42, 0, 0) * Mth.TWO_PI;
            seedX[s] = ring * Mth.cos(theta);
            seedY[s] = y;
            seedZ[s] = ring * Mth.sin(theta);
        }

        int maxTris = rows * cols * 2;
        int[] ta = new int[maxTris], tb = new int[maxTris], tc = new int[maxTris], owner = new int[maxTris];
        int triCount = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int a = row * cols + col, b = row * cols + (col + 1) % cols;
                int c = (row + 1) * cols + (col + 1) % cols, d = (row + 1) * cols + col;
                if (row == 0) {
                    // Pole row: a and b coincide, one real triangle.
                    ta[triCount] = a; tb[triCount] = c; tc[triCount] = d; triCount++;
                } else if (hash(a, 43, 0, 0) < 0.5f) {
                    ta[triCount] = a; tb[triCount] = b; tc[triCount] = c; triCount++;
                    ta[triCount] = a; tb[triCount] = c; tc[triCount] = d; triCount++;
                } else {
                    ta[triCount] = a; tb[triCount] = b; tc[triCount] = d; triCount++;
                    ta[triCount] = b; tb[triCount] = c; tc[triCount] = d; triCount++;
                }
            }
        }

        int[] count = new int[cells];
        for (int t = 0; t < triCount; t++) {
            float mx = vx[ta[t]] + vx[tb[t]] + vx[tc[t]];
            float my = vy[ta[t]] + vy[tb[t]] + vy[tc[t]];
            float mz = vz[ta[t]] + vz[tb[t]] + vz[tc[t]];
            int best = 0;
            float bestDot = -Float.MAX_VALUE;
            for (int s = 0; s < cells; s++) {
                float dot = mx * seedX[s] + my * seedY[s] + mz * seedZ[s];
                if (dot > bestDot) {
                    bestDot = dot;
                    best = s;
                }
            }
            owner[t] = best;
            count[best]++;
        }
        int[] start = new int[cells];
        for (int s = 1; s < cells; s++) {
            start[s] = start[s - 1] + count[s - 1];
        }

        float[] cx = new float[cells], cy = new float[cells], cz = new float[cells];
        for (int t = 0; t < triCount; t++) {
            int s = owner[t];
            cx[s] += (vx[ta[t]] + vx[tb[t]] + vx[tc[t]]) / 3.0f;
            cy[s] += (vy[ta[t]] + vy[tb[t]] + vy[tc[t]]) / 3.0f;
            cz[s] += (vz[ta[t]] + vz[tb[t]] + vz[tc[t]]) / 3.0f;
        }
        for (int s = 0; s < cells; s++) {
            if (count[s] > 0) {
                cx[s] /= count[s];
                cy[s] /= count[s];
                cz[s] /= count[s];
            }
        }

        // Triangle vertices stored grouped by shard, relative to the shard centroid.
        float[] tri = new float[triCount * 9];
        int[] fill = start.clone();
        for (int t = 0; t < triCount; t++) {
            int s = owner[t];
            int k = fill[s]++ * 9;
            int[] corners = {ta[t], tb[t], tc[t]};
            for (int v = 0; v < 3; v++) {
                tri[k + v * 3] = vx[corners[v]] - cx[s];
                tri[k + v * 3 + 1] = vy[corners[v]] - cy[s];
                tri[k + v * 3 + 2] = vz[corners[v]] - cz[s];
            }
        }

        float[] dirX = new float[cells], dirY = new float[cells], dirZ = new float[cells], speed = new float[cells];
        float[] axisX = new float[cells], axisY = new float[cells], axisZ = new float[cells], spin = new float[cells];
        float[] shade = new float[cells], delay = new float[cells];
        for (int s = 0; s < cells; s++) {
            float len = Math.max(Mth.sqrt(cx[s] * cx[s] + cy[s] * cy[s] + cz[s] * cz[s]), 1.0e-3f);
            // Outward with a small upward kick and some sideways scatter.
            dirX[s] = cx[s] / len + (hash(s, 17, 0, 0) - 0.5f) * 0.5f;
            dirY[s] = cy[s] / len + 0.35f;
            dirZ[s] = cz[s] / len + (hash(s, 18, 0, 0) - 0.5f) * 0.5f;
            speed[s] = Mth.lerp(hash(s, 11, 0, 0), SHARD_SPEED_MIN, SHARD_SPEED_MAX);
            float ax = hash(s, 12, 0, 0) - 0.5f, ay = hash(s, 13, 0, 0) - 0.5f, az = hash(s, 14, 0, 0) - 0.5f;
            float aLen = Math.max(Mth.sqrt(ax * ax + ay * ay + az * az), 1.0e-3f);
            axisX[s] = ax / aLen;
            axisY[s] = ay / aLen;
            axisZ[s] = az / aLen;
            spin[s] = (hash(s, 15, 0, 0) - 0.5f) * 2.0f * SHARD_SPIN_MAX;
            shade[s] = 0.65f + 0.35f * hash(s, 16, 0, 0);
            delay[s] = SHARD_HOLD_TICKS + hash(s, 19, 0, 0) * SHARD_HOLD_JITTER_TICKS;
        }
        return new ShardSet(tri, start, count, cx, cy, cz, dirX, dirY, dirZ, speed,
                axisX, axisY, axisZ, spin, shade, delay);
    }

    private static void renderShards(int seed, float age, float fadeAlpha,
                                     Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        if (age >= SHARD_TICKS) {
            return;
        }
        ShardSet set = SHARDS;
        float yaw = hash(seed, 51, 0, 0) * Mth.TWO_PI;
        float yawCos = Mth.cos(yaw), yawSin = Mth.sin(yaw);
        float p = age / SHARD_TICKS;
        float life = 1.0f - p;
        float flash = age < SHARD_HOLD_TICKS + SHARD_HOLD_JITTER_TICKS ? SHARD_FLASH_ALPHA : SHARD_FLASH_ALPHA * life * life * life;
        float baseAlpha = (SHARD_ALPHA * (float) Math.pow(life, 1.2f) + flash) * fadeAlpha;
        float[] tri = set.tri();
        for (int s = 0; s < set.cx().length; s++) {
            if (set.count()[s] == 0) {
                continue;
            }
            float hold = set.delay()[s];
            float move = Math.max(0.0f, age - hold);
            float shrink = move <= 0.0f
                    ? Mth.lerp(age / hold, 1.0f, SHARD_START_SHRINK)
                    : Mth.lerp(Mth.clamp(move / (SHARD_TICKS - hold), 0.0f, 1.0f), SHARD_START_SHRINK, SHARD_END_SHRINK);
            float travel = set.speed()[s] * move;
            float px = set.cx()[s] + set.dirX()[s] * travel;
            float py = set.cy()[s] + set.dirY()[s] * travel - 0.5f * SHARD_GRAVITY * move * move;
            float pz = set.cz()[s] + set.dirZ()[s] * travel;
            float ox = px * yawCos - pz * yawSin, oz = px * yawSin + pz * yawCos;
            float angle = set.spin()[s] * move;
            float cos = Mth.cos(angle), sin = Mth.sin(angle), k = 1.0f - cos;
            float kx = set.axisX()[s], ky = set.axisY()[s], kz = set.axisZ()[s];
            float alpha = baseAlpha * set.shade()[s];
            int end = (set.start()[s] + set.count()[s]) * 9;
            for (int t = set.start()[s] * 9; t < end; t += 9) {
                for (int corner = 0; corner < 4; corner++) {
                    int v = t + Math.min(corner, 2) * 3;
                    float vx = tri[v] * shrink, vy = tri[v + 1] * shrink, vz = tri[v + 2] * shrink;
                    // Rodrigues rotation about the shard's own axis, then the per-dome yaw.
                    float dot = kx * vx + ky * vy + kz * vz;
                    float rx = vx * cos + (ky * vz - kz * vy) * sin + kx * dot * k;
                    float ry = vy * cos + (kz * vx - kx * vz) * sin + ky * dot * k;
                    float rz = vz * cos + (kx * vy - ky * vx) * sin + kz * dot * k;
                    vertex(consumer, position, normal, ox + rx * yawCos - rz * yawSin, py + ry,
                            oz + rx * yawSin + rz * yawCos, SHARD_R, SHARD_G, SHARD_B, alpha);
                }
            }
        }
    }

    // === Phase 2C lightning. Shapes come from an integer hash of (seed, slot, cycle, index), so a
    // bolt is stable within its cycle, needs no Random state, and allocates nothing per frame.
    // Points go into one shared scratch array (render thread only), then out as camera-facing
    // ribbons on the shell's additive RENDER_TYPE (already verified under Oculus/Iris).
    private static final float[] BOLT_POINTS =
            new float[(Math.max(SHELL_BOLT_SEGMENTS, COUNTER_BOLT_MAX_SEGMENTS) + 1) * 3];

    private static float hash(int a, int b, int c, int d) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77 + c * 0xC2B2AE3D + d * 0x27D4EB2F;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) * 0x1.0p-24f;
    }

    private static void renderShellLightning(int seed, float time, float fadeAlpha,
                                             float camX, float camY, float camZ,
                                             Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        float surface = (float) CrystalHydroDomeConstants.RADIUS + SHELL_BOLT_SURFACE_OFFSET;
        for (int slot = 0; slot < SHELL_BOLT_SLOTS; slot++) {
            float local = time + (float) slot * SHELL_BOLT_PERIOD_TICKS / SHELL_BOLT_SLOTS;
            int cycle = Mth.floor(local / SHELL_BOLT_PERIOD_TICKS);
            float age = local - cycle * SHELL_BOLT_PERIOD_TICKS;
            if (age >= SHELL_BOLT_LIFE_TICKS) {
                continue;
            }
            // Whole-tick flicker plus a fade across the short life.
            float flicker = hash(seed, slot, cycle, 7 + (int) age) < 0.25f ? 0.4f : 1.0f;
            float alpha = fadeAlpha * flicker * (0.35f + 0.65f * (1.0f - age / SHELL_BOLT_LIFE_TICKS));

            float theta = hash(seed, slot, cycle, 1) * Mth.TWO_PI;
            float y0 = Mth.lerp(hash(seed, slot, cycle, 2), SHELL_BOLT_MIN_Y, surface * 0.9f);
            float phi = (float) Math.acos(y0 / surface);
            float heading = hash(seed, slot, cycle, 3) * Mth.TWO_PI;
            float length = Mth.lerp(hash(seed, slot, cycle, 4), SHELL_BOLT_MIN_LENGTH, SHELL_BOLT_MAX_LENGTH);

            fillSurfaceArc(seed, slot, cycle, 100, theta, phi, heading, length, SHELL_BOLT_SEGMENTS, surface);
            drawBolt(SHELL_BOLT_SEGMENTS + 1, SHELL_BOLT_GLOW_WIDTH, SHELL_BOLT_CORE_WIDTH,
                    SHELL_BOLT_GLOW_ALPHA * alpha, SHELL_BOLT_CORE_ALPHA * alpha,
                    camX, camY, camZ, position, normal, consumer);

            // One short fork off the middle point, angled away from the main heading.
            int mid = (SHELL_BOLT_SEGMENTS / 2) * 3;
            float mx = BOLT_POINTS[mid], my = BOLT_POINTS[mid + 1], mz = BOLT_POINTS[mid + 2];
            float forkPhi = (float) Math.acos(Mth.clamp(my / surface, -1.0f, 1.0f));
            float forkTheta = (float) Mth.atan2(mz, mx);
            float turn = (0.5f + 0.4f * hash(seed, slot, cycle, 5)) * (hash(seed, slot, cycle, 6) < 0.5f ? -1.0f : 1.0f);
            fillSurfaceArc(seed, slot, cycle, 200, forkTheta, forkPhi, heading + turn, length * 0.45f,
                    SHELL_BOLT_FORK_SEGMENTS, surface);
            drawBolt(SHELL_BOLT_FORK_SEGMENTS + 1, SHELL_BOLT_GLOW_WIDTH * 0.7f, SHELL_BOLT_CORE_WIDTH * 0.7f,
                    SHELL_BOLT_GLOW_ALPHA * alpha * 0.8f, SHELL_BOLT_CORE_ALPHA * alpha * 0.8f,
                    camX, camY, camZ, position, normal, consumer);
        }
    }

    /** Walks `length` blocks over the sphere from (theta, phi) along `heading` (0 = toward the top
     *  pole), jagging interior points sideways, and writes segments+1 xyz points to BOLT_POINTS.
     *  Phi is clamped so the arc never dips below the dome's ground line. */
    private static void fillSurfaceArc(int seed, int slot, int cycle, int salt, float theta0, float phi0,
                                       float heading, float length, int segments, float surface) {
        float cosH = Mth.cos(heading), sinH = Mth.sin(heading);
        float maxPhi = (float) Math.acos(SHELL_BOLT_MIN_Y * 0.5f / surface);
        for (int k = 0; k <= segments; k++) {
            float angle = (float) k / segments * length / surface;
            float phi = phi0 - cosH * angle;
            float along = sinH * angle;
            if (k > 0 && k < segments) {
                float jag = (hash(seed, slot, cycle, salt + k) - 0.5f) * 2.0f * SHELL_BOLT_JAG / surface;
                phi += sinH * jag;
                along += cosH * jag;
            }
            phi = Mth.clamp(phi, 0.02f, maxPhi);
            float theta = theta0 + along / Math.max(Mth.sin(phi), 0.2f);
            float sinPhi = Mth.sin(phi);
            BOLT_POINTS[k * 3] = surface * sinPhi * Mth.cos(theta);
            BOLT_POINTS[k * 3 + 1] = surface * Mth.cos(phi);
            BOLT_POINTS[k * 3 + 2] = surface * sinPhi * Mth.sin(theta);
        }
    }

    private static void renderCounterBolts(CrystalHydroDomeAoe entity, float time, float camX, float camY, float camZ,
                                           Matrix4f position, Matrix3f normal, MultiBufferSource buffer) {
        VertexConsumer consumer = null;
        float radius = (float) CrystalHydroDomeConstants.RADIUS;
        for (int slot = 0; slot < CrystalHydroDomeAoe.COUNTER_BOLT_SLOTS; slot++) {
            float age = time - entity.getCounterBoltStartTick(slot);
            if (age < 0.0f || age >= COUNTER_BOLT_LIFE_TICKS) {
                continue;
            }
            float alpha = age < COUNTER_BOLT_HOLD_TICKS ? 1.0f
                    : 1.0f - (age - COUNTER_BOLT_HOLD_TICKS) / (COUNTER_BOLT_LIFE_TICKS - COUNTER_BOLT_HOLD_TICKS);

            Vector3f target = entity.getCounterBoltOffset(slot);
            float ex = target.x(), ey = target.y(), ez = target.z();
            // Nearest shell point: attacker direction from the center, kept at or above ground.
            float dx = ex, dy = Math.max(ey, 0.0f), dz = ez;
            float dLen = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (dLen < 1.0e-4f) {
                dx = 0.0f;
                dy = 1.0f;
                dz = 0.0f;
                dLen = 1.0f;
            }
            int serial = entity.getCounterBoltSerial(slot);
            // Origin wander: tilt the shell direction by up to ORIGIN_SPREAD degrees, fixed per bolt,
            // so repeated Counters don't all leave from the same point. Target is unchanged.
            float nx = dx / dLen, ny = dy / dLen, nz = dz / dLen;
            boolean upright = Math.abs(ny) > 0.9f;
            float t1x = upright ? 0.0f : -nz, t1y = upright ? nz : 0.0f, t1z = upright ? -ny : nx;
            float t1Len = Mth.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
            t1x /= t1Len;
            t1y /= t1Len;
            t1z /= t1Len;
            float t2x = ny * t1z - nz * t1y, t2y = nz * t1x - nx * t1z, t2z = nx * t1y - ny * t1x;
            float tilt = hash(entity.getId(), serial, 91, 0) * COUNTER_BOLT_ORIGIN_SPREAD_DEG * Mth.DEG_TO_RAD;
            float around = hash(entity.getId(), serial, 92, 0) * Mth.TWO_PI;
            float tc = Mth.cos(tilt), ts = Mth.sin(tilt), ac = Mth.cos(around), as = Mth.sin(around);
            nx = nx * tc + (t1x * ac + t2x * as) * ts;
            ny = Math.max(0.0f, ny * tc + (t1y * ac + t2y * as) * ts);
            nz = nz * tc + (t1z * ac + t2z * as) * ts;
            float nLen = Math.max(Mth.sqrt(nx * nx + ny * ny + nz * nz), 1.0e-4f);
            float sx = nx / nLen * radius, sy = ny / nLen * radius, sz = nz / nLen * radius;
            float lx = ex - sx, ly = ey - sy, lz = ez - sz;
            float lineLen = Mth.sqrt(lx * lx + ly * ly + lz * lz);
            if (lineLen < 0.05f) {
                continue;
            }
            float fx = lx / lineLen, fy = ly / lineLen, fz = lz / lineLen;
            // Perpendicular jag basis: p1 = normalize(dir x ref), p2 = dir x p1.
            boolean steep = Math.abs(fy) >= 0.9f;
            float refX = steep ? 1.0f : 0.0f, refY = steep ? 0.0f : 1.0f;
            float p1x = -fz * refY, p1y = fz * refX, p1z = fx * refY - fy * refX;
            float p1Len = Mth.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
            p1x /= p1Len;
            p1y /= p1Len;
            p1z /= p1Len;
            float p2x = fy * p1z - fz * p1y, p2y = fz * p1x - fx * p1z, p2z = fx * p1y - fy * p1x;

            int segments = Mth.clamp(Mth.ceil(lineLen / COUNTER_BOLT_SEGMENT_LENGTH),
                    COUNTER_BOLT_MIN_SEGMENTS, COUNTER_BOLT_MAX_SEGMENTS);
            int reshape = (int) (age / COUNTER_BOLT_RESHAPE_TICKS);
            float envelopeMax = Math.min(COUNTER_BOLT_JAG, lineLen * 0.12f) * 2.0f;
            for (int k = 0; k <= segments; k++) {
                float t = (float) k / segments;
                float px = sx + lx * t, py = sy + ly * t, pz = sz + lz * t;
                if (k > 0 && k < segments) {
                    float envelope = Mth.sin(t * Mth.PI) * envelopeMax;
                    float o1 = (hash(entity.getId(), serial, reshape, k * 2) - 0.5f) * envelope;
                    float o2 = (hash(entity.getId(), serial, reshape, k * 2 + 1) - 0.5f) * envelope;
                    px += p1x * o1 + p2x * o2;
                    py += p1y * o1 + p2y * o2;
                    pz += p1z * o1 + p2z * o2;
                }
                BOLT_POINTS[k * 3] = px;
                BOLT_POINTS[k * 3 + 1] = py;
                BOLT_POINTS[k * 3 + 2] = pz;
            }
            if (consumer == null) {
                consumer = buffer.getBuffer(RENDER_TYPE);
            }
            drawBolt(segments + 1, COUNTER_BOLT_GLOW_WIDTH, COUNTER_BOLT_CORE_WIDTH,
                    COUNTER_BOLT_GLOW_ALPHA * alpha, COUNTER_BOLT_CORE_ALPHA * alpha,
                    camX, camY, camZ, position, normal, consumer);
        }
    }

    /** Glow ribbon first, then the core on top, both from BOLT_POINTS[0 .. pointCount). */
    private static void drawBolt(int pointCount, float glowWidth, float coreWidth, float glowAlpha, float coreAlpha,
                                 float camX, float camY, float camZ,
                                 Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        boltRibbon(pointCount, glowWidth, BOLT_GLOW_R, BOLT_GLOW_G, BOLT_GLOW_B, glowAlpha,
                camX, camY, camZ, position, normal, consumer);
        boltRibbon(pointCount, coreWidth, BOLT_CORE_R, BOLT_CORE_G, BOLT_CORE_B, coreAlpha,
                camX, camY, camZ, position, normal, consumer);
    }

    /** One camera-facing quad per segment: side = normalize(segment x toCamera) * width/2. */
    private static void boltRibbon(int pointCount, float width, float r, float g, float b, float alpha,
                                   float camX, float camY, float camZ,
                                   Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        if (alpha <= 0.0f) {
            return;
        }
        for (int i = 0; i < pointCount - 1; i++) {
            int a = i * 3, n = a + 3;
            float ax = BOLT_POINTS[a], ay = BOLT_POINTS[a + 1], az = BOLT_POINTS[a + 2];
            float bx = BOLT_POINTS[n], by = BOLT_POINTS[n + 1], bz = BOLT_POINTS[n + 2];
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float vx = camX - (ax + bx) * 0.5f, vy = camY - (ay + by) * 0.5f, vz = camZ - (az + bz) * 0.5f;
            float cx = dy * vz - dz * vy, cy = dz * vx - dx * vz, cz = dx * vy - dy * vx;
            float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1.0e-6f) {
                continue;
            }
            float k = width * 0.5f / len;
            cx *= k;
            cy *= k;
            cz *= k;
            vertex(consumer, position, normal, ax - cx, ay - cy, az - cz, r, g, b, alpha);
            vertex(consumer, position, normal, ax + cx, ay + cy, az + cz, r, g, b, alpha);
            vertex(consumer, position, normal, bx + cx, by + cy, bz + cz, r, g, b, alpha);
            vertex(consumer, position, normal, bx - cx, by - cy, bz - cz, r, g, b, alpha);
        }
    }

    /**
     * f = 1 - |dot(normal, viewDir)|, term = BASE_ALPHA + EDGE_ALPHA * f^EDGE_POWER — grazing
     * angles (viewDir nearly perpendicular to the surface normal, i.e. looking along the dome's
     * skin) get bright, face-on angles (looking straight through, whether from inside or outside)
     * stay near BASE_ALPHA. abs() on the dot product is what makes this symmetric for both cases
     * without needing to know which side of the surface the camera is on. Not clamped to 0..1
     * itself (BASE_ALPHA+EDGE_ALPHA is already well under 1) — the combined alpha (this term plus
     * the inside and rim terms) is clamped once in {@link #render}.
     */
    private static float fresnelEdgeTerm(float lx, float ly, float lz, float nx, float ny, float nz,
                                          float camLocalX, float camLocalY, float camLocalZ) {
        float dx = camLocalX - lx;
        float dy = camLocalY - ly;
        float dz = camLocalZ - lz;
        float lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1.0e-6f) {
            return CrystalHydroDomeVisuals.BASE_ALPHA;
        }
        float invLen = 1.0f / (float) Math.sqrt(lenSq);
        float vx = dx * invLen;
        float vy = dy * invLen;
        float vz = dz * invLen;
        float dot = nx * vx + ny * vy + nz * vz;
        float f = Mth.clamp(1.0f - Math.abs(dot), 0.0f, 1.0f);
        float edge = (float) Math.pow(f, CrystalHydroDomeVisuals.EDGE_POWER);
        return CrystalHydroDomeVisuals.BASE_ALPHA + CrystalHydroDomeVisuals.EDGE_ALPHA * edge;
    }

    /**
     * Once-per-frame "how deep inside the dome is the camera" factor, 0 at/outside the hemisphere
     * boundary, 1 at the center, smoothstepped in between so there's no visible pop crossing the
     * boundary (the fresnel edge term already peaks right at the boundary from both sides, and
     * this term ramps up from exactly 0 there, so the two hand off continuously).
     * <p>
     * Metric mirrors {@code CrystalHydroDomeAoe.isInside} — same ellipsoid-hemisphere shape,
     * just normalized (divided through by RADIUS/HEIGHT so it reads as "1.0 at the boundary"
     * instead of isInside's un-normalized squared-distance-vs-RADIUS_SQUARED comparison) so it
     * still degrades correctly if RADIUS and HEIGHT ever diverge (they're both 10.0 today):
     * {@code r = sqrt((dx²+dz²)/RADIUS² + (max(dy,0))²/HEIGHT²)}, then
     * {@code insideFactor = smoothstep(clamp(1 - r, 0, 1))}. Camera below the dome's floor
     * (isInside's own BELOW_CENTER_TOLERANCE cutoff) is treated as outside, same as isInside.
     */
    private static float insideFactor(float camLocalX, float camLocalY, float camLocalZ) {
        if (camLocalY < CrystalHydroDomeConstants.BELOW_CENTER_TOLERANCE) {
            return 0.0f;
        }
        double horizontalSq = (double) camLocalX * camLocalX + (double) camLocalZ * camLocalZ;
        double vertical = Math.max(camLocalY, 0.0f);
        double r = Math.sqrt(horizontalSq / (CrystalHydroDomeConstants.RADIUS * CrystalHydroDomeConstants.RADIUS)
                + (vertical * vertical) / (CrystalHydroDomeConstants.HEIGHT * CrystalHydroDomeConstants.HEIGHT));
        float t = Mth.clamp((float) (1.0 - r), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Bright band centered on the ground line (local-space vertex height 0), fading out linearly
     *  to 0 by RIM_HEIGHT blocks on EITHER side (Phase 2A.3: the shell now extends below y=0 too,
     *  so this is symmetric via abs() rather than one-sided) — a fixed property of the mesh,
     *  independent of view angle, so it reads the same from both inside and outside. */
    private static float rimFactor(float vertexHeightAboveCenter) {
        float t = Mth.clamp(Math.abs(vertexHeightAboveCenter) / CrystalHydroDomeVisuals.RIM_HEIGHT, 0.0f, 1.0f);
        return 1.0f - t;
    }

    /** Ease-out cubic, 0 at spawn to 1 at FADE_IN_TICKS — same curve shape as
     *  EmbracingBosomRingRenderer's convergence easing, applied here to a simple fade-in instead. */
    private static float fadeInAlpha(int tickCount, float partialTicks) {
        float t = tickCount + partialTicks;
        float p = Mth.clamp(t / CrystalHydroDomeVisuals.FADE_IN_TICKS, 0.0f, 1.0f);
        float inv = 1.0f - p;
        return 1.0f - inv * inv * inv;
    }

    // === Lotus. Mesh arrays are built once; the render path transforms cached vertices only.
    private record PetalMesh(float[] u, float[] h, float[] w, float[] t, float[] edge,
                             int[] indices, float pitchDeg) { }

    private static final PetalMesh OUTER = buildPetal(LOTUS_OUTER_LENGTH, LOTUS_OUTER_HEIGHT, LOTUS_PETAL_SEGMENTS);
    private static final PetalMesh INNER = buildPetal(LOTUS_INNER_LENGTH, LOTUS_INNER_HEIGHT, LOTUS_PETAL_SEGMENTS);
    private static final PetalMesh HEART = buildPetal(LOTUS_HEART_LENGTH, LOTUS_HEART_HEIGHT, LOTUS_PETAL_SEGMENTS);
    private static final PetalMesh DRIFT = buildPetal(DRIFT_LENGTH, 0.07f, DRIFT_SEGMENTS);
    // Wind: x/y/z/alpha, two quads per step, fading across the ribbon as well as at its ends.
    private static final float[] WIND = buildWind();
    private static final float[] RING_COS = new float[SIGIL_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[SIGIL_SEGMENTS + 1];
    // Ground flourishes: x/z coordinates for radial-width quads.
    private static final float[] GROUND_ARCS = buildGroundArcs();
    static {
        for (int i = 0; i <= SIGIL_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / SIGIL_SEGMENTS;
            RING_COS[i] = (float) Math.cos(angle);
            RING_SIN[i] = (float) Math.sin(angle);
        }
    }

    private static PetalMesh buildPetal(float length, float tipHeight, int segments) {
        int columns = 5;
        int count = (segments + 1) * columns;
        float[] u = new float[count], h = new float[count], w = new float[count];
        float[] t = new float[count], edge = new float[count];
        float curve = Math.min(length * LOTUS_CURVE_RATIO, LOTUS_CURVE_MAX);
        float cup = Math.min(length * LOTUS_CUP_RATIO, LOTUS_CUP_MAX);
        for (int row = 0; row <= segments; row++) {
            float f = (float) row / segments;
            float bow = (float) Math.sin(Math.PI * f);
            // Broad shoulder, narrow root and a true tapered tip, unlike the old oval paddle.
            float width = (float) Math.pow(Math.max(0.0f, bow), 0.75) * (0.7f + 0.6f * f);
            if (row == 0) width = 0.045f;
            if (row == segments) width = 0.0f;
            for (int col = 0; col < columns; col++) {
                int i = row * columns + col;
                float across = (col - 2) * 0.5f;
                u[i] = length * f;
                // Raised edges form an actual transverse cup; asymmetric sweep bends the outline.
                h[i] = curve * f * f + cup * across * across * bow;
                w[i] = across * length * LOTUS_WIDTH_RATIO * width * 0.5f
                        + length * LOTUS_SWEEP_RATIO * bow * f;
                t[i] = f;
                edge[i] = Math.abs(across);
            }
        }
        int[] indices = new int[segments * (columns - 1) * 4];
        int k = 0;
        for (int row = 0; row < segments; row++) {
            for (int col = 0; col < columns - 1; col++) {
                int a = row * columns + col, b = a + columns;
                indices[k++] = a;
                indices[k++] = a + 1;
                indices[k++] = b + 1;
                indices[k++] = b;
            }
        }
        float pitch = (float) Math.toDegrees(Math.asin(tipHeight / Math.hypot(length, curve))
                - Math.atan2(curve, length));
        return new PetalMesh(u, h, w, t, edge, indices, pitch);
    }

    private static float[] buildWind() {
        float[] mesh = new float[WIND_SEGMENTS * 8 * 4];
        int k = 0;
        for (int segment = 0; segment < WIND_SEGMENTS; segment++) {
            for (int side = 0; side < 2; side++) {
                for (int corner = 0; corner < 4; corner++) {
                    float f = (segment + (corner >= 2 ? 1.0f : 0.0f)) / WIND_SEGMENTS;
                    int col = side + (corner == 1 || corner == 2 ? 1 : 0);
                    float taper = (float) Math.sin(Math.PI * f);
                    double angle = f * WIND_TURNS * 2.0 * Math.PI;
                    float radius = WIND_RADIUS + WIND_RADIUS_SWELL * taper;
                    mesh[k++] = radius * (float) Math.cos(angle);
                    mesh[k++] = LOTUS_Y_OFFSET + 0.12f + f * WIND_HEIGHT
                            + (col - 1) * WIND_HALF_WIDTH * taper;
                    mesh[k++] = radius * (float) Math.sin(angle);
                    mesh[k++] = col == 1 ? WIND_ALPHA * taper : 0.0f;
                }
            }
        }
        return mesh;
    }

    private static float[] buildGroundArcs() {
        float[] mesh = new float[SIGIL_ARCS * SIGIL_ARC_SEGMENTS * 4 * 2];
        int k = 0;
        for (int arc = 0; arc < SIGIL_ARCS; arc++) {
            for (int segment = 0; segment < SIGIL_ARC_SEGMENTS; segment++) {
                for (int corner = 0; corner < 4; corner++) {
                    float f = (segment + (corner >= 2 ? 1.0f : 0.0f)) / SIGIL_ARC_SEGMENTS;
                    double angle = (arc + f * SIGIL_ARC_COVERAGE) * 2.0 * Math.PI / SIGIL_ARCS;
                    float side = corner == 0 || corner == 3 ? -0.5f : 0.5f;
                    float radius = SIGIL_ARC_RADIUS + SIGIL_ARC_SWELL * (float) Math.sin(Math.PI * f)
                            + side * SIGIL_LINE_WIDTH;
                    mesh[k++] = radius * (float) Math.cos(angle);
                    mesh[k++] = radius * (float) Math.sin(angle);
                }
            }
        }
        return mesh;
    }

    private static void renderLotus(float time, int endState, float endAge, float fadeAlpha,
                                     Matrix4f positionMatrix, Matrix3f normalMatrix, MultiBufferSource buffer) {
        float p = Mth.clamp(time / LOTUS_BLOOM_TICKS, 0.0f, 1.0f);
        float bloom = easeOut(p);
        float scale = Mth.lerp(bloom, LOTUS_BLOOM_START_SCALE, 1.0f);

        // Per-part modifiers; identity while alive, driven by endAge during the Phase 2D endings.
        float driftScale = scale;
        float rootScale = bloom;
        float pitchBias = LOTUS_BLOOM_OVERSHOOT_DEG * (1.0f - bloom);
        float flatten = 0.0f;
        float yShift = 0.0f;
        float petalAlpha = fadeAlpha;
        float sigilAlpha = fadeAlpha;
        float windAlpha = fadeAlpha * bloom;
        float windScale = scale;
        float driftTime = time;
        float driftAlpha = fadeAlpha * bloom;
        float burst = -1.0f;
        if (endState == CrystalHydroDomeAoe.END_STATE_NATURAL) {
            // Lotus spreads out to ~END_LOTUS_RADIUS and fades; drifting petals are flung outward.
            float lp = Mth.clamp(endAge / END_LOTUS_TICKS, 0.0f, 1.0f);
            float grow = Mth.lerp(easeOut(lp), 1.0f, END_LOTUS_RADIUS / (LOTUS_ROOT_RADIUS + LOTUS_OUTER_LENGTH));
            scale *= grow;
            rootScale *= grow;
            windScale *= grow;
            flatten = END_LOTUS_FLATTEN * easeOut(lp);
            petalAlpha *= 1.0f - smooth(lp);
            sigilAlpha *= 1.0f - Mth.clamp(endAge / END_SIGIL_FADE_TICKS, 0.0f, 1.0f);
            windAlpha *= 1.0f - Mth.clamp(endAge / END_WIND_FADE_TICKS, 0.0f, 1.0f);
            driftTime = time - endAge;
            burst = Mth.clamp(endAge / END_DRIFT_TICKS, 0.0f, 1.0f);
            driftAlpha *= 1.0f - burst * burst;
        } else if (endState == CrystalHydroDomeAoe.END_STATE_BROKEN) {
            // Lotus folds shut, shrinks and sinks into the ground; everything else just fades.
            float cp = Mth.clamp(endAge / END_CLOSE_TICKS, 0.0f, 1.0f);
            float close = smooth(cp);
            pitchBias += END_CLOSE_PITCH_DEG * close;
            rootScale *= 1.0f - 0.8f * close;
            scale *= Mth.lerp(close, 1.0f, END_CLOSE_SCALE);
            yShift = -END_SINK_DEPTH * cp * cp;
            petalAlpha *= 1.0f - Mth.clamp((cp - 0.4f) / 0.6f, 0.0f, 1.0f);
            float fade = 1.0f - Mth.clamp(endAge / END_BROKEN_FADE_TICKS, 0.0f, 1.0f);
            sigilAlpha *= fade;
            windAlpha *= fade;
            driftAlpha *= fade;
        }

        // The shell already occupies this additive batch. Draw ground lines before the petals.
        VertexConsumer additive = buffer.getBuffer(RENDER_TYPE);
        if (sigilAlpha > 0.0f) {
            renderRing(SIGIL_RADIUS, SIGIL_LINE_WIDTH, SIGIL_Y, 0.40f, 0.82f, 1.0f,
                    SIGIL_ALPHA * sigilAlpha, positionMatrix, normalMatrix, additive);
            for (int i = 0; i < GROUND_ARCS.length; i += 2) {
                vertex(additive, positionMatrix, normalMatrix, GROUND_ARCS[i], SIGIL_Y, GROUND_ARCS[i + 1],
                        0.75f, 0.86f, 1.0f, SIGIL_ALPHA * 0.6f * sigilAlpha);
            }
            for (int i = 0; i < PULSE_COUNT; i++) {
                float phase = (time / PULSE_PERIOD_TICKS + (float) i / PULSE_COUNT) % 1.0f;
                float radius = Mth.lerp(phase, SIGIL_RADIUS, PULSE_INNER_RADIUS);
                float alpha = Mth.sin(phase * Mth.PI) * PULSE_ALPHA * sigilAlpha;
                renderRing(radius, SIGIL_LINE_WIDTH * 1.5f, SIGIL_Y, 1.0f, 0.86f, 0.52f,
                        alpha, positionMatrix, normalMatrix, additive);
            }
        }
        flush(buffer, RENDER_TYPE);

        VertexConsumer petals = buffer.getBuffer(LOTUS_RENDER_TYPE);
        if (petalAlpha > 0.0f) {
            renderLayer(OUTER, LOTUS_OUTER_COUNT, time * LOTUS_OUTER_YAW_SPEED, pitchBias, flatten, rootScale,
                    scale, yShift, petalAlpha, positionMatrix, normalMatrix, petals);
            renderLayer(INNER, LOTUS_INNER_COUNT, LOTUS_INNER_YAW_OFFSET + time * LOTUS_INNER_YAW_SPEED, pitchBias,
                    flatten, rootScale, scale, yShift, petalAlpha, positionMatrix, normalMatrix, petals);
            renderLayer(HEART, LOTUS_HEART_COUNT, LOTUS_HEART_YAW_OFFSET + time * LOTUS_HEART_YAW_SPEED, pitchBias,
                    flatten, rootScale, scale, yShift, petalAlpha, positionMatrix, normalMatrix, petals);
        }
        for (int i = 0; i < DRIFT_COUNT && driftAlpha > 0.0f; i++) {
            float phase = (driftTime / DRIFT_CYCLE_TICKS + (float) i / DRIFT_COUNT) % 1.0f;
            // Stagger heights and golden-angle orbits so petals fill the hemisphere rather
            // than forming one low ring. Available horizontal radius shrinks toward the roof.
            float angle = (i * 137.50776f + driftTime * DRIFT_YAW_SPEED) * Mth.DEG_TO_RAD;
            float y = DRIFT_Y_MIN + phase * DRIFT_Y_RANGE;
            double safeRadius = CrystalHydroDomeConstants.RADIUS - DRIFT_SHELL_CLEARANCE;
            float availableRadius = (float) Math.sqrt(Math.max(0.0, safeRadius * safeRadius - y * y));
            float fraction = Mth.lerp((float) Math.sqrt((i * 0.618034f + 0.2f) % 1.0f),
                    DRIFT_MIN_RADIUS_FRACTION, DRIFT_MAX_RADIUS_FRACTION);
            float radius = availableRadius * fraction;
            float roll = driftTime * 0.025f + i;
            if (burst >= 0.0f) {
                // Natural end: fling outward from the frozen position, with a small hop and fast spin.
                radius += END_DRIFT_DISTANCE * easeOut(burst) * (0.7f + 0.3f * hash(i, 21, 0, 0));
                y += END_DRIFT_LIFT * Mth.sin(burst * Mth.PI);
                roll += burst * 6.0f;
            }
            float x = Mth.cos(angle) * radius, z = Mth.sin(angle) * radius;
            float lifeFade = Mth.clamp(Math.min(phase, 1.0f - phase) / DRIFT_FADE_FRACTION, 0.0f, 1.0f);
            float alpha = lifeFade * driftAlpha;
            renderPetal(DRIFT, angle, 0.35f + 0.4f * Mth.sin(driftTime * 0.04f + i),
                    roll, driftScale, x, y, z, alpha, positionMatrix, normalMatrix, petals);
        }
        flush(buffer, LOTUS_RENDER_TYPE);

        if (windAlpha <= 0.0f) {
            return;
        }
        // Soft ribbons use alpha blending, not a solid center or an additive white pile-up.
        VertexConsumer wind = buffer.getBuffer(LOTUS_RENDER_TYPE);
        for (int strand = 0; strand < WIND_STRANDS; strand++) {
            float angle = (time * WIND_YAW_SPEED + strand * (360.0f / WIND_STRANDS)) * Mth.DEG_TO_RAD;
            float cos = Mth.cos(angle), sin = Mth.sin(angle);
            for (int i = 0; i < WIND.length; i += 4) {
                float x = WIND[i] * windScale, z = WIND[i + 2] * windScale;
                vertex(wind, positionMatrix, normalMatrix, x * cos - z * sin,
                        WIND[i + 1] * windScale + yShift, x * sin + z * cos,
                        WIND_R, WIND_G, WIND_B, WIND[i + 3] * windAlpha);
            }
        }
        flush(buffer, LOTUS_RENDER_TYPE);
    }

    private static void flush(MultiBufferSource buffer, RenderType type) {
        if (buffer instanceof MultiBufferSource.BufferSource batches) batches.endBatch(type);
    }

    private static void renderLayer(PetalMesh mesh, int count, float yawDeg, float pitchBiasDeg, float flatten,
                                     float rootScale, float scale, float yShift, float alpha,
                                     Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        float pitch = (mesh.pitchDeg() * (1.0f - flatten) + pitchBiasDeg) * Mth.DEG_TO_RAD;
        float root = LOTUS_ROOT_RADIUS * rootScale;
        for (int i = 0; i < count; i++) {
            float angle = (yawDeg + i * (360.0f / count)) * Mth.DEG_TO_RAD;
            renderPetal(mesh, angle, pitch, 0.0f, scale, root * Mth.cos(angle),
                    LOTUS_Y_OFFSET + yShift, root * Mth.sin(angle), alpha, position, normal, consumer);
        }
    }

    private static void renderPetal(PetalMesh mesh, float yaw, float pitch, float roll, float scale,
                                     float x, float y, float z, float alpha,
                                     Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        float cy = Mth.cos(yaw), sy = Mth.sin(yaw), cp = Mth.cos(pitch), sp = Mth.sin(pitch);
        float cr = Mth.cos(roll), sr = Mth.sin(roll);
        boolean gold = mesh == HEART;
        float baseR = gold ? HEART_BASE_R : LOTUS_BASE_R;
        float baseG = gold ? HEART_BASE_G : LOTUS_BASE_G;
        float baseB = gold ? HEART_BASE_B : LOTUS_BASE_B;
        float tipR = gold ? HEART_TIP_R : LOTUS_TIP_R;
        float tipG = gold ? HEART_TIP_G : LOTUS_TIP_G;
        float tipB = gold ? HEART_TIP_B : LOTUS_TIP_B;
        for (int i : mesh.indices()) {
            float u = mesh.u()[i] * scale, h = mesh.h()[i] * scale, w = mesh.w()[i] * scale;
            float radial = u * cp - h * sp;
            float vertical = u * sp + h * cp;
            float across = w * cr - vertical * sr;
            float height = w * sr + vertical * cr;
            float t = mesh.t()[i], e = mesh.edge()[i];
            float shade = 1.0f - LOTUS_EDGE_DARKEN * e;
            float r = Mth.lerp(t, baseR, tipR) * shade;
            float g = Mth.lerp(t, baseG, tipG) * shade;
            float b = Mth.lerp(t, baseB, tipB) * shade;
            // Root and rim remain softer than the body, preserving layered translucency.
            float a = LOTUS_ALPHA * (0.55f + 0.45f * Mth.sin(t * Mth.PI)) * (1.0f - 0.2f * e) * alpha;
            vertex(consumer, position, normal, x + radial * cy - across * sy,
                    y + height, z + radial * sy + across * cy, r, g, b, a);
        }
    }

    private static void renderRing(float radius, float width, float y, float r, float g, float b, float alpha,
                                    Matrix4f position, Matrix3f normal, VertexConsumer consumer) {
        for (int i = 0; i < SIGIL_SEGMENTS; i++) {
            for (int corner = 0; corner < 4; corner++) {
                int j = i + (corner >= 2 ? 1 : 0);
                float rr = radius + (corner == 0 || corner == 3 ? -0.5f : 0.5f) * width;
                vertex(consumer, position, normal, RING_COS[j] * rr, y, RING_SIN[j] * rr,
                        r, g, b, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f position, Matrix3f normal,
                                 float x, float y, float z, float r, float g, float b, float alpha) {
        consumer.vertex(position, x, y, z).color(r, g, b, alpha).uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0f, 1.0f, 0.0f).endVertex();
    }
}
