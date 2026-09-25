package net.offkung.bhspellsx.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Round 15.5: Xian She Huan Ying's caster-side smoke, rewritten off a standard sprite-atlas
 * {@code TextureSheetParticle} after 3 confirmed bugs in that version (see the round's own report):
 * rise/drift speeds were never converted from blocks/second to blocks/tick (20x too fast — the
 * "shoots into the sky" symptom), the particle rendered at vanilla's default alpha=1.0 for its
 * first visible frame(s) before {@code tick()} ever ran (the "flash" symptom), and vanilla's own
 * {@code particle.fsh} discards any pixel whose {@code texture.a * vertexColor.a} is below 0.1 —
 * since this particle's own alpha never exceeds {@link XianSheHuanYingConstants#SMOKE_ALPHA}
 * (0.30) and spends most of its life well under that, most of the soft-edged sprite fell under
 * the cutoff and only the sprite's most-opaque pixels ever survived (the "hard clumpy edges"
 * symptom).
 * <p>
 * Fixes, in order:
 * <ol>
 * <li>Speeds are computed in blocks/second from the SMOKE_* constants (untouched, as instructed)
 * and divided by 20 here, once, before being stored in xd/yd/zd.</li>
 * <li>alpha is set to 0 in the constructor (matching what the age=0 point of the sin curve would
 * give anyway), not left at {@code Particle}'s own default of 1.0.</li>
 * <li>{@code ParticleEngine.render()} always binds {@code GameRenderer.getParticleShader()}
 * (decompile-confirmed: a single hardcoded {@code RenderSystem.setShader} call before any
 * {@code ParticleRenderType.begin()} runs, not selectable per render type) — so the &lt;0.1 cutoff
 * in {@code particle.fsh} can't be avoided by simply changing {@code ParticleRenderType} while
 * still going through the normal sprite/atlas pipeline. Checked every other core shader with a
 * vertex-color input for one that discards only at exactly 0 ({@code position_tex_color.fsh},
 * {@code rendertype_entity_translucent(_emissive).fsh}, decompiled from the vanilla client jar):
 * none does — {@code position_tex.fsh} discards only at {@code == 0.0} but has no vertex-color
 * input at all (texture-only), which can't carry our per-particle fade. Settled on
 * {@code GameRenderer.getRendertypeEntityTranslucentEmissiveShader()} instead (same shader/vertex
 * format this project's own {@code EmbracingBosomRingRenderer}/{@code CrystalHydroDomeRenderer}
 * already use, decompile-verified there too) — its discard is on the TEXTURE's own alpha only
 * (still &lt;0.1, decompile-confirmed), never on our per-particle fade value, so the visible shape
 * no longer pops based on how faded a puff currently is. Binding a different shader/vertex format
 * ({@code DefaultVertexFormat.NEW_ENTITY}) than the sprite pipeline expects means this particle can
 * no longer go through {@code SingleQuadParticle}/{@code TextureSheetParticle}'s built-in
 * {@code render()} (it always writes {@code DefaultVertexFormat.PARTICLE}-shaped vertices) — this
 * class extends {@link Particle} directly and writes its own quad. This shader applies vanilla's
 * fake directional entity lighting ({@code minecraft_mix_light}, decompiled from
 * {@code light.glsl}: {@code lightAccum = min(1, (max(0,dot(L0,N)) + max(0,dot(L1,N))) * 0.6 + 0.4)})
 * rather than being purely emissive — same trade-off {@code EmbracingBosomRingRenderer}'s own
 * javadoc accepts for the identical reason. Round 15.7 fixed a real bug in that trade-off: Normal
 * was first set to "point from the quad toward the camera," which put it in WORLD space while
 * {@code Light0_Direction}/{@code Light1_Direction} are in VIEW space (decompiled
 * {@code LevelRenderer}/{@code Lighting}/{@code GlStateManager.setupLevelDiffuseLighting}: the
 * fixed world vectors (0.2,1,-0.7) and (-0.2,1,0.7), normalized, get pre-transformed once per
 * frame by the SAME matrix {@code ParticleEngine.render()} later pushes onto
 * {@code RenderSystem.getModelViewMatrix()} — a pure camera-rotation matrix, no translation, for
 * particle draws) — mixing a world-space Normal against view-space light directions made the dot
 * product (and so brightness) swing with camera pitch, worse near vertical, exactly the reported
 * symptom. Fixed by transforming world "up" (0,1,0) through
 * {@code RenderSystem.getModelViewMatrix()} ({@code Matrix4f.transformDirection}, ignores
 * translation) before use — since that puts Normal through the exact same rotation the engine
 * already applied to Light0/Light1, their relative angle (and so brightness) stays constant
 * regardless of camera orientation. Verified against the decompiled formula: with both light
 * directions normalized, {@code dot(L_i, up) = 1/sqrt(0.2^2+1^2+0.7^2) ≈ 0.80845} for both,
 * giving {@code lightAccum = min(1, (0.80845+0.80845)*0.6+0.4) = min(1, 1.3702) = 1.0} — clamped
 * to the shader's own maximum, not just "brighter."</li>
 * <li>The 3 separate {@code xshy_smoke_0/1/2.png} sprite-atlas textures (which needed
 * {@code particles/xshy_smoke.json} + a registered {@code SpriteSet} to be stitched into the
 * vanilla particle atlas, and rendered at whatever filter the atlas itself uses — nearest, hence
 * the pixelated edges) are gone, replaced by one hand-combined {@code xshy_smoke_atlas.png}
 * (512x128, {@link #FRAME_COUNT} frames side by side, round 15.6) that this class binds and
 * filters (linear) itself in
 * {@link #getRenderType()}'s {@code begin()} — {@code AbstractTexture.setFilter} is called on
 * that one {@link ResourceLocation} only, so no other texture in the game is affected. Since this
 * particle no longer uses the sprite-atlas pipeline at all, it's registered via Forge's
 * {@code registerSpecial} (no {@code SpriteSet}/atlas involvement whatsoever) rather than
 * {@code registerSpriteSet} — see {@code BHSpellsXClient}.</li>
 * </ol>
 * Spin is still just {@link #roll}/{@link #oRoll} updated in {@link #tick()} — same as before,
 * just now read directly in this class's own {@link #render} instead of relying on
 * {@code SingleQuadParticle}'s built-in handling of those two fields.
 */
public class XshySmokeParticle extends Particle {
    private static final float LIFE_MIN_FRACTION = 0.8f;
    private static final float LIFE_MAX_FRACTION = 1.2f;
    private static final float SIZE_MIN_FRACTION = 0.7f;
    private static final float SIZE_MAX_FRACTION = 1.3f;
    private static final float RISE_MIN_FRACTION = 0.6f;
    private static final float RISE_MAX_FRACTION = 1.4f;
    private static final float SPIN_MIN_FRACTION = 0.5f;
    private static final float SPIN_MAX_FRACTION = 1.5f;
    /** ticks per second — every blocks/second SMOKE_* speed constant is divided by this once. */
    private static final float TICKS_PER_SECOND = 20.0f;

    /** FRAME_COUNT frames side by side in one hand-combined image — see this class's javadoc,
     *  point 4. */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/particle/xshy_smoke_atlas.png");
    private static final int FRAME_COUNT = 4;

    /** No atlas, no depth write, linear-filtered, bound directly — see this class's javadoc,
     *  points 3 and 4. Vertex format/shader must match: {@code NEW_ENTITY} +
     *  {@code getRendertypeEntityTranslucentEmissiveShader()}, so {@link #render} writes vertices
     *  itself instead of relying on {@code SingleQuadParticle}. */
    private static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            textureManager.getTexture(TEXTURE).setFilter(true, false);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentEmissiveShader);
            RenderSystem.setShaderTexture(0, TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
        }

        @Override
        public String toString() {
            return "xshy_smoke";
        }
    };

    private final float baseHalfSize;
    private final float spinPerTick;
    private final float frameU0;
    private final float frameU1;

    protected XshySmokeParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.friction = 1.0f;
        this.alpha = 0.0f; // matches sin(0) at age=0 — see this class's javadoc, point 2
        this.setColor(
                XianSheHuanYingConstants.SMOKE_COLOR_R / 255.0f,
                XianSheHuanYingConstants.SMOKE_COLOR_G / 255.0f,
                XianSheHuanYingConstants.SMOKE_COLOR_B / 255.0f);

        float lifeSeconds = XianSheHuanYingConstants.SMOKE_LIFE_SECONDS
                * Mth.lerp(this.random.nextFloat(), LIFE_MIN_FRACTION, LIFE_MAX_FRACTION);
        this.lifetime = Math.round(lifeSeconds * TICKS_PER_SECOND);

        // Full on-screen width (per spec) halved to a half-extent, since this class's own render()
        // builds each corner at +/-1 * half before adding the center.
        this.baseHalfSize = XianSheHuanYingConstants.SMOKE_SIZE * 0.5f
                * Mth.lerp(this.random.nextFloat(), SIZE_MIN_FRACTION, SIZE_MAX_FRACTION);

        // blocks/second -> blocks/tick — see this class's javadoc, point 1.
        float riseSpeed = XianSheHuanYingConstants.SMOKE_RISE
                * Mth.lerp(this.random.nextFloat(), RISE_MIN_FRACTION, RISE_MAX_FRACTION);
        this.yd = riseSpeed / TICKS_PER_SECOND;

        float driftDir = this.random.nextFloat() * Mth.TWO_PI;
        float driftSpeed = XianSheHuanYingConstants.SMOKE_DRIFT * this.random.nextFloat();
        this.xd = Mth.cos(driftDir) * driftSpeed / TICKS_PER_SECOND;
        this.zd = Mth.sin(driftDir) * driftSpeed / TICKS_PER_SECOND;

        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        float spinDir = this.random.nextBoolean() ? 1.0f : -1.0f;
        // radians/second -> radians/tick — same conversion rise/drift already got, missed here
        // last round (see this class's javadoc, point 1).
        this.spinPerTick = XianSheHuanYingConstants.SMOKE_SPIN * spinDir
                * Mth.lerp(this.random.nextFloat(), SPIN_MIN_FRACTION, SPIN_MAX_FRACTION)
                / TICKS_PER_SECOND;

        int frame = this.random.nextInt(FRAME_COUNT);
        this.frameU0 = frame / (float) FRAME_COUNT;
        this.frameU1 = (frame + 1) / (float) FRAME_COUNT;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spinPerTick;
    }

    /** Builds and writes the quad itself — see this class's javadoc for why
     *  {@code SingleQuadParticle}'s built-in render() can't be reused here. Size and alpha are
     *  computed straight from {@code (age + partialTick) / lifetime} every frame (never lerped
     *  between two stored "old"/"new" values), so there's no separate initial-state field that
     *  could be wrong on the very first frame. */
    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        float ageFraction = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0f, 1.0f);
        float alpha = XianSheHuanYingConstants.SMOKE_ALPHA * Mth.sin(Mth.PI * ageFraction);
        if (alpha <= 0.0f) {
            return;
        }
        float half = this.baseHalfSize * (1.0f + XianSheHuanYingConstants.SMOKE_GROW * ageFraction);

        Vec3 camPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - camPos.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - camPos.y());
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - camPos.z());

        Quaternionf quaternion = new Quaternionf(camera.rotation());
        float roll = Mth.lerp(partialTick, this.oRoll, this.roll);
        if (roll != 0.0f) {
            quaternion.rotateZ(roll);
        }

        // World "up" transformed into the SAME space Light0_Direction/Light1_Direction are in —
        // see this class's javadoc, point 3, for the decompiled proof this is view space (the
        // current RenderSystem.getModelViewMatrix(), a pure camera-rotation matrix with no
        // translation during particle rendering) and why a plain camera-facing Normal made
        // brightness swing with camera pitch instead.
        Vector3f normal = RenderSystem.getModelViewMatrix().transformDirection(new Vector3f(0.0f, 1.0f, 0.0f));
        if (normal.lengthSquared() > 1.0e-6f) {
            normal.normalize();
        }

        Vector3f[] corners = {
                new Vector3f(-1.0f, -1.0f, 0.0f),
                new Vector3f(-1.0f, 1.0f, 0.0f),
                new Vector3f(1.0f, 1.0f, 0.0f),
                new Vector3f(1.0f, -1.0f, 0.0f),
        };
        float[] u = {this.frameU0, this.frameU0, this.frameU1, this.frameU1};
        float[] v = {1.0f, 0.0f, 0.0f, 1.0f};

        int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        int r = Math.round(this.rCol * 255.0f);
        int g = Math.round(this.gCol * 255.0f);
        int b = Math.round(this.bCol * 255.0f);
        int light = LightTexture.FULL_BRIGHT;

        for (int i = 0; i < 4; i++) {
            Vector3f corner = corners[i];
            corner.rotate(quaternion);
            corner.mul(half);
            corner.add(x, y, z);
            consumer.vertex(corner.x(), corner.y(), corner.z())
                    .color(r, g, b, a)
                    .uv(u[i], v[i])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normal.x(), normal.y(), normal.z())
                    .endVertex();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }

    /** No {@link net.minecraft.client.particle.SpriteSet} — this particle no longer goes through
     *  the atlas/sprite pipeline at all, so it's wired up via Forge's {@code registerSpecial}
     *  (see {@code BHSpellsXClient}), not {@code registerSpriteSet}. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z, double xd, double yd, double zd) {
            return new XshySmokeParticle(level, x, y, z);
        }
    }
}
