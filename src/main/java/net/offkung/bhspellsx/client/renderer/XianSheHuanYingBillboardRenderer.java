package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingTargetEntity;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingUserEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

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

    /** Plain white 1x1 texture Forge ships — same trick CrystalHydroDomeRenderer uses so a solid
     *  color quad (see {@link #renderAuraLines}) can go through the same cutout RenderType as
     *  every textured billboard here, tinted purely via vertex color. */
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "forge", "textures/white.png");

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

        float snakeVisible = visibleFactor(user.isDismissing(), user.getDismissStartTick(), age, 0,
                XianSheHuanYingConstants.APPEAR_TICKS);
        double snakeRise = XianSheHuanYingConstants.APPEAR_RISE_DISTANCE * (1.0 - snakeVisible);
        renderQuad(poseStack, buffer, packedLight, this.style.texture(),
                snakeOffX, snakeOffY + bob - snakeRise, snakeOffZ,
                this.style.width() * snakeVisible, this.style.height() * snakeVisible);

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
            float appear = visibleFactor(user.isDismissing(), user.getDismissStartTick(), age,
                    i * (float) XianSheHuanYingConstants.CLOUD_APPEAR_STAGGER_TICKS, XianSheHuanYingConstants.APPEAR_TICKS);
            float w = CLOUD_WIDTHS[texIndex] * XianSheHuanYingConstants.CLOUD_SCALE * sizeJitter * appear;
            float h = CLOUD_HEIGHTS[texIndex] * XianSheHuanYingConstants.CLOUD_SCALE * sizeJitter * appear;
            double rise = XianSheHuanYingConstants.APPEAR_RISE_DISTANCE * (1.0 - appear);

            renderQuad(poseStack, buffer, packedLight, CLOUD_TEXTURES[texIndex], cx, cy - rise, cz, w, h);
        }
    }

    /** Eye pair above the target's head (round 6: two mirrored copies of snake_eye.png, opening
     *  on a delay after spawn — see {@link #renderEyePair}), plus the purple weakening aura lines
     *  (see {@link #renderAuraLines}), both anchored the same way the old single eye quad was: the
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

        boolean dismissing = marker.isDismissing();
        float openFactor = dismissing
                ? closeFactor(marker.getDismissStartTick(), age, XianSheHuanYingConstants.EYE_CLOSE_TICKS)
                : easeOutBackFactor(age, XianSheHuanYingConstants.EYE_OPEN_DELAY_TICKS,
                        XianSheHuanYingConstants.EYE_OPEN_TICKS, XianSheHuanYingConstants.EYE_OPEN_OVERSHOOT);
        renderEyePair(poseStack, buffer, packedLight, dx, dy + bob, dz, openFactor);

        if (target != null) {
            // Uses the same open/close factor as the eyes: shrinks toward the target during
            // dismissing exactly like the eye pair does, per spec.
            renderAuraLines(poseStack, buffer, marker, ex, ey, ez, targetX, targetY, targetZ,
                    targetHeight, age, openFactor);
        }
    }

    /** Two copies of the same eye texture, side by side in the VIEWER's screen space (not world
     *  space — see the localOffsetX parameter on {@link #renderQuad}), so the gap between them
     *  reads as constant regardless of view angle. The right-hand copy (the source image is
     *  already a right eye) is drawn as-is; the left is U-mirrored. Only height eases in with
     *  {@code openFactor} — width stays fixed, per spec ("บีบความสูง ... ความกว้างคงที่") — and
     *  it eases from the vertical center, i.e. the eye "opens"/"closes" like an eyelid rather than
     *  growing from the bottom. {@code openFactor} is computed by the caller as either the normal
     *  opening ease (spawning) or {@link #closeFactor} (dismissing, driven by the marker's own
     *  synced dismiss state). */
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

    /** Purple lines falling from just above the target's head down to its feet, ringing the body
     *  at a fixed radius so they never bunch at the chest. Pure time-based rendering — no
     *  particles, no per-line state anywhere: each line's fall position is a function of
     *  {@code age} (client tickCount+partialTick) and a fixed per-line phase (seeded from the
     *  marker's own entity id + line index, same determinism technique the mist clouds use), so
     *  it's stable across frames and identical on every client without syncing anything. Each
     *  line is a vertical quad rotated ONLY around world Y to face the camera horizontally (never
     *  tilts with camera pitch, per spec) — unlike the full 3-axis billboard the snake/eye/mist
     *  use, so it doesn't share {@link #renderQuad}. Drawn over a plain white texture, tinted
     *  purple via vertex color, forced full-bright regardless of {@code RENDER_FULL_BRIGHT} (the
     *  aura is meant to read clearly even at night), through the same cutout-no-cull RenderType as
     *  everything else here — never additive. {@code visibleFactor} shrinks each line's length
     *  toward 0 (anchored at its bottom, so it looks like it recedes into the ground) during
     *  dismissing, exactly mirroring the eye pair. */
    private void renderAuraLines(PoseStack poseStack, MultiBufferSource buffer, XianSheHuanYingTargetEntity marker,
                                 double ex, double ey, double ez,
                                 double targetX, double targetY, double targetZ, double targetHeight,
                                 float age, float visibleFactor) {
        if (visibleFactor <= 0.0f) {
            return;
        }
        double dropDistance = targetHeight + XianSheHuanYingConstants.AURA_LINE_START_ABOVE_HEAD;
        if (dropDistance <= 0.0) {
            return;
        }
        Vec3 camPos = this.entityRenderDispatcher.camera.getPosition();
        int r = Math.round(XianSheHuanYingConstants.AURA_COLOR_R * 255.0f);
        int g = Math.round(XianSheHuanYingConstants.AURA_COLOR_G * 255.0f);
        int b = Math.round(XianSheHuanYingConstants.AURA_COLOR_B * 255.0f);

        for (int i = 0; i < XianSheHuanYingConstants.AURA_LINE_COUNT; i++) {
            RandomSource rnd = RandomSource.create(marker.getId() * 104_729L + i * 65_537L);
            float angle = (float) Math.toRadians((360.0f / XianSheHuanYingConstants.AURA_LINE_COUNT) * i
                    + (rnd.nextFloat() - 0.5f) * (360.0f / XianSheHuanYingConstants.AURA_LINE_COUNT) * 0.6f);
            float radius = Mth.lerp(rnd.nextFloat(), XianSheHuanYingConstants.AURA_LINE_RADIUS_MIN,
                    XianSheHuanYingConstants.AURA_LINE_RADIUS_MAX);
            float length = Mth.lerp(rnd.nextFloat(), XianSheHuanYingConstants.AURA_LINE_LENGTH_MIN,
                    XianSheHuanYingConstants.AURA_LINE_LENGTH_MAX);
            float phase = rnd.nextFloat();

            double lineWorldX = targetX + Math.cos(angle) * radius;
            double lineWorldZ = targetZ + Math.sin(angle) * radius;

            // fallen: distance already fallen from the top of this line's drop range, wrapping
            // every dropDistance blocks so it loops forever with no reset/state.
            double fallen = (age * XianSheHuanYingConstants.AURA_LINE_FALL_SPEED + phase * dropDistance) % dropDistance;
            double segTopY = targetY + dropDistance - fallen;
            double segBottomY = Math.max(targetY, segTopY - length);
            if (segTopY <= segBottomY) {
                continue;
            }

            // Dismissing: shrink the segment from its current length toward 0, anchored at the
            // bottom (so it looks like it recedes into the ground rather than rescaling in place).
            double segHeight = (segTopY - segBottomY) * visibleFactor;
            if (segHeight <= 0.0) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(lineWorldX - ex, segBottomY - ey, lineWorldZ - ez);
            // Vertical-only billboard: rotate around world Y to face the camera horizontally,
            // never around X/Z, so the line always stays upright regardless of camera pitch.
            float yaw = (float) Mth.atan2(camPos.x - lineWorldX, camPos.z - lineWorldZ);
            poseStack.mulPose(Axis.YP.rotation(yaw));

            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            Matrix3f normal = pose.normal();
            VertexConsumer consumer = buffer.getBuffer(billboardRenderType(WHITE_TEXTURE));
            float hw = XianSheHuanYingConstants.AURA_LINE_WIDTH * 0.5f;
            float h = (float) segHeight;
            vertex(consumer, matrix, normal, -hw, 0.0f, 0.0f, 1.0f, LightTexture.FULL_BRIGHT, r, g, b, 255);
            vertex(consumer, matrix, normal, hw, 0.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, r, g, b, 255);
            vertex(consumer, matrix, normal, hw, h, 1.0f, 0.0f, LightTexture.FULL_BRIGHT, r, g, b, 255);
            vertex(consumer, matrix, normal, -hw, h, 0.0f, 0.0f, LightTexture.FULL_BRIGHT, r, g, b, 255);
            poseStack.popPose();
        }
    }

    /** Ease-out (quadratic) from 0 to 1 over durationTicks, starting delayTicks after spawn.
     *  Purely a function of client tickCount+partialTick — never touches server lifecycle. */
    private static float appearFactor(float age, float delayTicks, float durationTicks) {
        float t = Mth.clamp((age - delayTicks) / durationTicks, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    /** Ease-out-back (Penner's standard "back" easing): overshoots past 1.0 partway through, then
     *  settles exactly at 1.0 — used ONLY for the eye pair opening (not closing, not snake/mist,
     *  which stay on the plain appearFactor/closeFactor above). With overshoot &lt;= 0 this
     *  degenerates algebraically to {@code 1-(1-t)^3} — a strong ease-out cubic with no bounce —
     *  same family, just c1=0, exactly as spec'd. */
    private static float easeOutBackFactor(float age, float delayTicks, float durationTicks, float overshoot) {
        float t = Mth.clamp((age - delayTicks) / durationTicks, 0.0f, 1.0f);
        float c1 = overshoot;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }

    /** Reverse of appearFactor: ease-IN (quadratic) collapse from 1 to 0 over durationTicks,
     *  starting at dismissStartTick — both read from the same entity.tickCount space the appear
     *  animation uses, so no separate clock is needed. Also purely client-side/render-only: it
     *  reads the synced dismissStartTick but never touches server lifecycle itself. */
    private static float closeFactor(int dismissStartTick, float age, float durationTicks) {
        float t = Mth.clamp((age - dismissStartTick) / durationTicks, 0.0f, 1.0f);
        return 1.0f - t * t;
    }

    /** Picks appearFactor (spawning) or closeFactor (dismissing) — used by the snake and each
     *  mist cloud, both of which need the same open/close behavior, just with their own stagger
     *  delay. Dismissing always uses DISMISS_TICKS (the server-side countdown length), regardless
     *  of the delay/openDurationTicks used while spawning. */
    private static float visibleFactor(boolean dismissing, int dismissStartTick, float age,
                                       float delayTicks, float openDurationTicks) {
        return dismissing
                ? closeFactor(dismissStartTick, age, XianSheHuanYingConstants.DISMISS_TICKS)
                : appearFactor(age, delayTicks, openDurationTicks);
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
        vertex(consumer, matrix, normal, x, y, u, v, light, 255, 255, 255, 255);
    }

    /** Same as the 8-arg overload, with an explicit vertex color (used by the purple aura lines,
     *  which draw over a plain white texture and rely entirely on this for their tint). */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float u, float v, int light,
                               int r, int g, int b, int a) {
        consumer.vertex(matrix, x, y, 0.0f)
                .color(r, g, b, a)
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
