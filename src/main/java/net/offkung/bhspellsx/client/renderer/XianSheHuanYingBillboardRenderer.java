package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingTargetEntity;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingUserEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Billboard rendering for Xian She Huan Ying, shared by both entities:
 * <ul>
 *   <li>the user-side entity draws the snake image behind the caster, plus a 6-cloud mist
 *       formation between the caster and the snake (see {@link #renderUserSide});</li>
 *   <li>the target-side entity draws the eye image above the target's head
 *       (see {@link #renderEyeSide}).</li>
 * </ul>
 * All tunable numbers live in {@link XianSheHuanYingConstants}.
 * <p>
 * RenderType is a hand-built equivalent of vanilla {@code entityCutoutNoCull} — opaque cutout, no
 * culling — with one difference: the {@code TextureStateShard}'s blur flag is driven by
 * {@link XianSheHuanYingConstants#TEXTURE_SMOOTH} instead of always {@code false}. Confirmed by
 * decompiling {@code RenderType}'s actual lambda body (mapped Forge jar) that vanilla's own
 * {@code entityCutoutNoCull(ResourceLocation, boolean)} always constructs
 * {@code TextureStateShard(texture, false, false)} regardless of that boolean (which controls
 * outline rendering, not blur) — there is no vanilla factory that exposes blur, hence the custom
 * one. Built from the same shards vanilla uses (shader, transparency, cull, lightmap, overlay),
 * accessed as {@code protected static} fields on {@link RenderStateShard} the same way
 * {@code BHRenderType} in this project does. Never additive or translucent either way.
 * <p>
 * Positioning: every quad here is placed using the CLIENT-interpolated position of a real,
 * already-ticking object (the caster player, the locked target, or — as a last resort — this
 * renderer's own entity), never the entity's own 20 Hz tracked position directly, so nothing
 * here re-introduces the stutter fixed in round 2. The caster/target are found from ids the
 * entities sync via {@code SynchedEntityData} ({@code XianSheHuanYingUserEntity}'s owner UUID,
 * {@code XianSheHuanYingTargetEntity}'s target entity id) — if either isn't found on this client
 * (not yet synced, chunk unloaded, ...), rendering falls back to this renderer's own entity
 * position so the image never simply disappears.
 */
public class XianSheHuanYingBillboardRenderer<T extends Entity> extends EntityRenderer<T> {
    private static final ResourceLocation SNAKE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/snake.png");
    private static final ResourceLocation EYE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/snake_eye.png");

    /** Purple tint for the weakening aura — see {@link #spawnAura}. */
    private static final Vector3f AURA_COLOR = new Vector3f(
            XianSheHuanYingConstants.AURA_COLOR_R, XianSheHuanYingConstants.AURA_COLOR_G,
            XianSheHuanYingConstants.AURA_COLOR_B);

    /** Last client tick a target-side entity (keyed by its own entity id) spawned an aura
     *  particle, so {@link #spawnAura} — called from render(), which runs far more than 20
     *  times/second — only actually spawns once per real game tick. */
    private static final ConcurrentHashMap<Integer, Integer> lastAuraTick = new ConcurrentHashMap<>();

    private static final ResourceLocation CLOUD_A = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/cloud_a.png");
    private static final ResourceLocation CLOUD_B = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/cloud_b.png");
    private static final ResourceLocation CLOUD_C = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/cloud_c.png");
    private static final ResourceLocation CLOUD_D = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/cloud_d.png");

    /** 6 clouds cycle through these 4 textures (index i % 4), each with its own aspect size. */
    private static final ResourceLocation[] CLOUD_TEXTURES = {CLOUD_A, CLOUD_B, CLOUD_C, CLOUD_D};
    private static final float[] CLOUD_WIDTHS = {
            XianSheHuanYingConstants.CLOUD_A_WIDTH, XianSheHuanYingConstants.CLOUD_B_WIDTH,
            XianSheHuanYingConstants.CLOUD_C_WIDTH, XianSheHuanYingConstants.CLOUD_D_WIDTH};
    private static final float[] CLOUD_HEIGHTS = {
            XianSheHuanYingConstants.CLOUD_A_HEIGHT, XianSheHuanYingConstants.CLOUD_B_HEIGHT,
            XianSheHuanYingConstants.CLOUD_C_HEIGHT, XianSheHuanYingConstants.CLOUD_D_HEIGHT};

    public enum Placement { BEHIND_OWNER, ABOVE_ENTITY }

    public record Style(ResourceLocation texture, float width, float height, float centerHeight,
                        Placement placement) {
    }

    public static final Style USER_STYLE = new Style(SNAKE_TEXTURE,
            XianSheHuanYingConstants.SNAKE_WIDTH, XianSheHuanYingConstants.SNAKE_HEIGHT,
            XianSheHuanYingConstants.SNAKE_CENTER_HEIGHT, Placement.BEHIND_OWNER);

    public static final Style TARGET_STYLE = new Style(EYE_TEXTURE,
            XianSheHuanYingConstants.EYE_WIDTH, XianSheHuanYingConstants.EYE_HEIGHT,
            XianSheHuanYingConstants.EYE_CENTER_HEIGHT, Placement.ABOVE_ENTITY);

    private final Style style;

    public XianSheHuanYingBillboardRenderer(EntityRendererProvider.Context context, Style style) {
        super(context);
        this.style = style;
    }

    public static XianSheHuanYingBillboardRenderer<XianSheHuanYingUserEntity> forUser(EntityRendererProvider.Context context) {
        return new XianSheHuanYingBillboardRenderer<>(context, USER_STYLE);
    }

    public static XianSheHuanYingBillboardRenderer<XianSheHuanYingTargetEntity> forTarget(EntityRendererProvider.Context context) {
        return new XianSheHuanYingBillboardRenderer<>(context, TARGET_STYLE);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        if (entity instanceof XianSheHuanYingUserEntity user) {
            renderUserSide(user, partialTick, age, poseStack, buffer, packedLight);
        } else if (entity instanceof XianSheHuanYingTargetEntity marker) {
            renderEyeSide(marker, partialTick, age, poseStack, buffer, packedLight);
        }
    }

    /** Snake image behind the caster, plus the single free-floating mist formation between the
     *  caster and the snake (see {@link #renderMist}). Both ease in from nothing on spawn — see
     *  {@link #appearFactor}. */
    private void renderUserSide(XianSheHuanYingUserEntity user, float partialTick, float age,
                                PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        double ex = Mth.lerp(partialTick, user.xOld, user.getX());
        double ey = Mth.lerp(partialTick, user.yOld, user.getY());
        double ez = Mth.lerp(partialTick, user.zOld, user.getZ());

        // Base position/yaw come from the caster as the client sees it, interpolated with
        // partialTick every frame, so everything below moves as smoothly as the player does. The
        // entity's own synced position/yaw (20 Hz tracker updates) is only the fallback for when
        // the caster isn't loaded on this client.
        double baseX = ex;
        double baseY = ey;
        double baseZ = ez;
        float yawDeg = Mth.rotLerp(partialTick, user.yRotO, user.getYRot());
        UUID_LOOKUP:
        {
            java.util.UUID ownerId = user.getSyncedOwnerId();
            if (ownerId == null) {
                break UUID_LOOKUP;
            }
            Player owner = user.level().getPlayerByUUID(ownerId);
            if (owner == null) {
                break UUID_LOOKUP;
            }
            baseX = Mth.lerp(partialTick, owner.xOld, owner.getX());
            baseY = Mth.lerp(partialTick, owner.yOld, owner.getY());
            baseZ = Mth.lerp(partialTick, owner.zOld, owner.getZ());
            yawDeg = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        }
        double ownerOffX = baseX - ex;
        double ownerOffY = baseY - ey;
        double ownerOffZ = baseZ - ez;

        // Minecraft yaw: forward = (-sin(yaw), +cos(yaw)) in (x, z). Behind = the opposite.
        double yawRad = Math.toRadians(yawDeg);
        double back = XianSheHuanYingConstants.SNAKE_BACK_DISTANCE;
        double snakeOffX = ownerOffX + Math.sin(yawRad) * back;
        double snakeOffZ = ownerOffZ - Math.cos(yawRad) * back;
        double snakeOffY = ownerOffY + XianSheHuanYingConstants.SNAKE_CENTER_HEIGHT;

        double bob = Math.sin(age * (Mth.TWO_PI / XianSheHuanYingConstants.BOB_PERIOD_TICKS))
                * XianSheHuanYingConstants.BOB_AMPLITUDE;

        float snakeAppear = appearFactor(age, 0);
        double snakeRise = XianSheHuanYingConstants.APPEAR_RISE_DISTANCE * (1.0 - snakeAppear);
        renderQuad(poseStack, buffer, packedLight, this.style.texture(),
                snakeOffX, snakeOffY + bob - snakeRise, snakeOffZ,
                this.style.width() * snakeAppear, this.style.height() * snakeAppear);

        renderMist(user, ownerOffX, ownerOffY, ownerOffZ, snakeOffX, snakeOffZ,
                age, poseStack, buffer, packedLight);
    }

    /** 6 clouds, cycling through 4 textures, one free-floating group centered between the caster
     *  and the snake image — reverted to round 3's single-group layout (round 4's snake-centered
     *  + front-of-player split visibly swung the whole formation on every turn, since both parts
     *  were tied to yaw; this group isn't). Horizontal center is the X/Z midpoint of the caster
     *  and the snake image, exactly as round 3 had it. The Y-stutter fix: each cloud's "height"
     *  (an absolute height above the caster's feet, 0.2-2.0 blocks, biased low) is now added onto
     *  {@code ownerOffY} — the caster's own REAL interpolated Y offset (the same one the snake
     *  image uses) — instead of round 3's bug of leaving it un-anchored, which left the mist's
     *  vertical position implicitly relative to this entity's own 20 Hz tracked Y. That mismatch
     *  (X/Z correctly interpolated, Y not) is exactly why the mist alone stuttered on a jump while
     *  the snake didn't; re-check this spot first if the stutter ever reappears. */
    private void renderMist(XianSheHuanYingUserEntity user, double ownerOffX, double ownerOffY, double ownerOffZ,
                            double snakeOffX, double snakeOffZ, float age,
                            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        double centerX = (ownerOffX + snakeOffX) * 0.5;
        double centerZ = (ownerOffZ + snakeOffZ) * 0.5;
        float groupAngle = age * (Mth.TWO_PI / XianSheHuanYingConstants.CLOUD_ORBIT_PERIOD_TICKS);

        for (int i = 0; i < XianSheHuanYingConstants.CLOUD_COUNT; i++) {
            // Seeded by entity id + cloud index: fixed per cloud for this entity's lifetime,
            // identical on every client (entity ids are server-assigned and synced), never
            // re-rolled per frame.
            RandomSource rnd = RandomSource.create(user.getId() * 104_729L + i * 65_537L);
            float baseAngleDeg = (360.0f / XianSheHuanYingConstants.CLOUD_COUNT) * i;
            float angleJitterDeg = (rnd.nextFloat() - 0.5f) * (360.0f / XianSheHuanYingConstants.CLOUD_COUNT) * 0.5f;
            float baseAngle = (float) Math.toRadians(baseAngleDeg + angleJitterDeg);
            float radius = Mth.lerp(rnd.nextFloat(), XianSheHuanYingConstants.CLOUD_ORBIT_RADIUS_MIN,
                    XianSheHuanYingConstants.CLOUD_ORBIT_RADIUS_MAX);
            float heightT = rnd.nextFloat();
            heightT = (float) Math.pow(heightT, XianSheHuanYingConstants.CLOUD_HEIGHT_BIAS_EXPONENT);
            float height = Mth.lerp(heightT, XianSheHuanYingConstants.CLOUD_HEIGHT_MIN,
                    XianSheHuanYingConstants.CLOUD_HEIGHT_MAX);
            float driftPeriod = Mth.lerp(rnd.nextFloat(), XianSheHuanYingConstants.CLOUD_DRIFT_PERIOD_MIN_TICKS,
                    XianSheHuanYingConstants.CLOUD_DRIFT_PERIOD_MAX_TICKS);
            float driftPhase = rnd.nextFloat() * Mth.TWO_PI;
            float sizeJitter = 1.0f + (rnd.nextFloat() - 0.5f) * 2.0f * XianSheHuanYingConstants.CLOUD_SIZE_JITTER;

            float angle = baseAngle + groupAngle;
            double driftPhaseAngle = age * (Mth.TWO_PI / driftPeriod) + driftPhase;
            double drift = XianSheHuanYingConstants.CLOUD_DRIFT_DISTANCE * Math.sin(driftPhaseAngle);

            double cx = centerX + Math.cos(angle) * radius + Math.cos(angle + Mth.HALF_PI) * drift;
            double cz = centerZ + Math.sin(angle) * radius + Math.sin(angle + Mth.HALF_PI) * drift;
            // height is an absolute height above the caster's feet — anchored to the caster's own
            // interpolated Y (ownerOffY), not this entity's tracked Y. See the method javadoc.
            double cy = ownerOffY + height + Math.sin(driftPhaseAngle) * (XianSheHuanYingConstants.CLOUD_DRIFT_DISTANCE * 0.5);

            int texIndex = i % CLOUD_TEXTURES.length;
            float appear = appearFactor(age, i * (float) XianSheHuanYingConstants.CLOUD_APPEAR_STAGGER_TICKS);
            float w = CLOUD_WIDTHS[texIndex] * XianSheHuanYingConstants.CLOUD_SCALE * sizeJitter * appear;
            float h = CLOUD_HEIGHTS[texIndex] * XianSheHuanYingConstants.CLOUD_SCALE * sizeJitter * appear;
            double rise = XianSheHuanYingConstants.APPEAR_RISE_DISTANCE * (1.0 - appear);

            renderQuad(poseStack, buffer, packedLight, CLOUD_TEXTURES[texIndex], cx, cy - rise, cz, w, h);
        }
    }

    /** Eye image above the target's head. No appear animation (round 3 only asks for it on the
     *  snake and mist), same gentle bob as before. */
    /** Eye pair above the target's head (round 6: two mirrored copies of snake_eye.png, opening
     *  on a delay after spawn — see {@link #renderEyePair}), plus the purple weakening aura
     *  (see {@link #spawnAura}), both anchored the same way the old single eye quad was: the
     *  target's own interpolated position if found client-side, else this entity's tracked one. */
    private void renderEyeSide(XianSheHuanYingTargetEntity marker, float partialTick, float age,
                               PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        double ex = Mth.lerp(partialTick, marker.xOld, marker.getX());
        double ey = Mth.lerp(partialTick, marker.yOld, marker.getY());
        double ez = Mth.lerp(partialTick, marker.zOld, marker.getZ());

        double dx = 0.0;
        double dz = 0.0;
        double dy = XianSheHuanYingConstants.EYE_CENTER_HEIGHT;
        double targetX = ex;
        double targetY = ey;
        double targetZ = ez;
        double targetHeight = 0.0;

        OptionalInt syncedId = marker.getSyncedTargetEntityId();
        Entity target = syncedId.isPresent() ? marker.level().getEntity(syncedId.getAsInt()) : null;
        if (target != null) {
            targetX = Mth.lerp(partialTick, target.xOld, target.getX());
            targetY = Mth.lerp(partialTick, target.yOld, target.getY());
            targetZ = Mth.lerp(partialTick, target.zOld, target.getZ());
            targetHeight = target.getBoundingBox().getYsize();
            dx = targetX - ex;
            dz = targetZ - ez;
            dy = (targetY - ey) + targetHeight + XianSheHuanYingConstants.EYE_HEIGHT_MARGIN;
        }

        double bob = Math.sin(age * (Mth.TWO_PI / XianSheHuanYingConstants.BOB_PERIOD_TICKS))
                * XianSheHuanYingConstants.BOB_AMPLITUDE;

        float openFactor = appearFactor(age, XianSheHuanYingConstants.EYE_OPEN_DELAY_TICKS,
                XianSheHuanYingConstants.EYE_OPEN_TICKS);
        renderEyePair(poseStack, buffer, packedLight, dx, dy + bob, dz, openFactor);

        if (target != null) {
            spawnAura(marker, targetX, targetY, targetZ, targetHeight);
        }
    }

    /** Two copies of the same eye texture, side by side in the VIEWER's screen space (not world
     *  space — see the localOffsetX parameter on {@link #renderQuad}), so the gap between them
     *  reads as constant regardless of view angle. The right-hand copy (the source image is
     *  already a right eye) is drawn as-is; the left is U-mirrored. Only height eases in with
     *  {@code openFactor} — width stays fixed, per spec ("บีบความสูง ... ความกว้างคงที่") — and
     *  it eases from the vertical center, i.e. the eye "opens" like an eyelid rather than
     *  growing up from the bottom. See the class javadoc for why closing on dismiss isn't wired
     *  up yet. */
    private void renderEyePair(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                               double dx, double dy, double dz, float openFactor) {
        if (openFactor <= 0.0f) {
            return;
        }
        float size = XianSheHuanYingConstants.EYE_PAIR_SIZE;
        float height = size * openFactor;
        float halfGap = XianSheHuanYingConstants.EYE_PAIR_GAP * 0.5f;
        renderQuad(poseStack, buffer, packedLight, EYE_TEXTURE, dx, dy, dz, size, height, halfGap, false);
        renderQuad(poseStack, buffer, packedLight, EYE_TEXTURE, dx, dy, dz, size, height, -halfGap, true);
    }

    /** Purple particles drifting down from the target's shoulders toward the ground, spawned
     *  entirely client-side (plain {@code Level#addParticle}, no packet) using vanilla's
     *  {@code DustParticleOptions} — see the class javadoc for why that type was chosen. Gated to
     *  once per real client tick (not per frame) via {@link #lastAuraTick}, since {@code render()}
     *  runs far more often than the game ticks. */
    private void spawnAura(XianSheHuanYingTargetEntity marker, double targetX, double targetY, double targetZ,
                           double targetHeight) {
        int tick = marker.tickCount;
        if (tick % XianSheHuanYingConstants.AURA_INTERVAL_TICKS != 0) {
            return;
        }
        Integer last = lastAuraTick.get(marker.getId());
        if (last != null && last == tick) {
            return;
        }
        lastAuraTick.put(marker.getId(), tick);

        RandomSource rnd = marker.level().random;
        float angle = rnd.nextFloat() * Mth.TWO_PI;
        float radius = Mth.lerp(rnd.nextFloat(), XianSheHuanYingConstants.AURA_RADIUS_MIN,
                XianSheHuanYingConstants.AURA_RADIUS_MAX);
        double px = targetX + Math.cos(angle) * radius;
        double pz = targetZ + Math.sin(angle) * radius;
        double py = targetY + targetHeight * XianSheHuanYingConstants.AURA_SPAWN_HEIGHT_FRACTION;

        DustParticleOptions options = new DustParticleOptions(AURA_COLOR, XianSheHuanYingConstants.AURA_SCALE);
        marker.level().addParticle(options, px, py, pz, 0.0, -XianSheHuanYingConstants.AURA_FALL_SPEED, 0.0);
    }

    /** Ease-out (quadratic) from 0 to 1 over APPEAR_TICKS, starting delayTicks after spawn.
     *  Purely a function of client tickCount+partialTick — never touches server lifecycle. */
    private static float appearFactor(float age, float delayTicks) {
        return appearFactor(age, delayTicks, XianSheHuanYingConstants.APPEAR_TICKS);
    }

    /** Same ease-out, with an explicit duration (used by the eye pair, whose open time differs
     *  from the snake/mist's APPEAR_TICKS). */
    private static float appearFactor(float age, float delayTicks, float durationTicks) {
        float t = Mth.clamp((age - delayTicks) / durationTicks, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    /** Draws one full-axis billboard quad, offset (dx, dy, dz) from the entity's own render
     *  origin (as MC's EntityRenderDispatcher already translated the PoseStack there). A
     *  collapsed (0-size) quad — mid-appear-animation — is skipped rather than drawn as a
     *  degenerate zero-area quad. */
    private void renderQuad(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                            ResourceLocation texture, double dx, double dy, double dz,
                            float width, float height) {
        renderQuad(poseStack, buffer, packedLight, texture, dx, dy, dz, width, height, 0.0f, false);
    }

    /** Same as the 9-arg overload, plus a localOffsetX (applied AFTER the camera-orientation pose,
     *  i.e. in screen space — used to lay the two eyes side by side at a view-independent gap) and
     *  a U-mirror flag (used for the left eye, since the source art is a single right eye). */
    private void renderQuad(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                            ResourceLocation texture, double dx, double dy, double dz,
                            float width, float height, float localOffsetX, boolean mirrorU) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        // Full-axis billboard: adopt the camera's orientation, so the quad's local +X is screen
        // right, +Y is screen up and +Z points at the viewer (no mirroring, no roll).
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        if (localOffsetX != 0.0f) {
            // In screen space now (after cameraOrientation), so this offset stays a constant
            // on-screen distance regardless of view angle/distance — unlike a world-space offset.
            poseStack.translate(localOffsetX, 0.0, 0.0);
        }

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(billboardRenderType(texture));
        int light = XianSheHuanYingConstants.RENDER_FULL_BRIGHT ? LightTexture.FULL_BRIGHT : packedLight;
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        float uLeft = mirrorU ? 1.0f : 0.0f;
        float uRight = mirrorU ? 0.0f : 1.0f;
        vertex(consumer, matrix, normal, -hw, -hh, uLeft, 1.0f, light);
        vertex(consumer, matrix, normal, hw, -hh, uRight, 1.0f, light);
        vertex(consumer, matrix, normal, hw, hh, uRight, 0.0f, light);
        vertex(consumer, matrix, normal, -hw, hh, uLeft, 0.0f, light);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float u, float v, int light) {
        consumer.vertex(matrix, x, y, 0.0f)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .endVertex();
    }

    /** The entity's own box is tiny and sits on the caster's/target's feet while the images float
     *  elsewhere (in the user entity's case, well outside its own box), so frustum culling would
     *  drop them wrongly. Same fix CrystalHydroDomeRenderer uses. */
    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.style.texture();
    }

    // ---- Custom RenderType: opaque cutout, no cull, blur switchable via TEXTURE_SMOOTH ----------

    /** Memoized per texture so repeated per-frame calls (snake + 6 clouds + eye, every entity,
     *  every frame) don't rebuild a CompositeState each time — same reasoning vanilla's own
     *  entityCutoutNoCull uses Util.memoize for. Rebuilt (new map) only on class (re)load, i.e. a
     *  resource-pack/game reload — fine, since TEXTURE_SMOOTH is a compile-time constant here. */
    private static final ConcurrentHashMap<ResourceLocation, RenderType> RENDER_TYPE_CACHE = new ConcurrentHashMap<>();

    private static RenderType billboardRenderType(ResourceLocation texture) {
        return RENDER_TYPE_CACHE.computeIfAbsent(texture, BillboardRenderType::create);
    }

    /** Subclassed (not instantiated) purely to reach RenderStateShard's protected static shard
     *  constants (NO_TRANSPARENCY, NO_CULL, LIGHTMAP, OVERLAY, the cutout-no-cull shader) — same
     *  access pattern this project's BHRenderType already uses. */
    private static final class BillboardRenderType extends RenderType {
        private BillboardRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                                    int bufferSize, boolean crumbling, boolean sort,
                                    Runnable setup, Runnable teardown) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, teardown);
        }

        static RenderType create(ResourceLocation texture) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            texture, XianSheHuanYingConstants.TEXTURE_SMOOTH, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true);
            return RenderType.create("bhspellsx_xian_she_huan_ying_billboard",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, state);
        }
    }
}
