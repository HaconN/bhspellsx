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
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingTargetEntity;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingUserEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Billboard rendering for Xian She Huan Ying (round 12: the caster's own VFX — snake, mist, tail
 * particles — is gone entirely; both the caster and the locked target now show the SAME eye-pair
 * billboard above their own head, via the one shared {@link #renderEyePairAndAura}). All tunable
 * numbers live in {@link XianSheHuanYingConstants}.
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
 * {@code BHRenderType} in this project does. Never additive or translucent.
 * <p>
 * Positioning: every quad here is placed using the CLIENT-interpolated position of a real,
 * already-ticking object (the caster/target player or mob, or — as a last resort — this
 * renderer's own entity), never the entity's own 20 Hz tracked position directly. The caster and
 * target are found from ids the entities sync via {@code SynchedEntityData}
 * ({@code XianSheHuanYingUserEntity}'s owner UUID, {@code XianSheHuanYingTargetEntity}'s target
 * entity id) — if either isn't found on this client (not yet synced, chunk unloaded, ...),
 * rendering falls back to this renderer's own entity position so the image never simply
 * disappears.
 */
public class XianSheHuanYingBillboardRenderer<T extends Entity> extends EntityRenderer<T> {
    private static final ResourceLocation EYE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "bhspellsx", "textures/entity/xian_she_huan_ying/snake_eye.png");

    /** Plain white 1x1 texture Forge ships — same trick CrystalHydroDomeRenderer uses so a solid
     *  color quad (see {@link #renderAuraLines}) can go through the same cutout RenderType as
     *  every textured billboard here, tinted purely via vertex color. */
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "forge", "textures/white.png");

    public XianSheHuanYingBillboardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static XianSheHuanYingBillboardRenderer<XianSheHuanYingUserEntity> forUser(EntityRendererProvider.Context context) {
        return new XianSheHuanYingBillboardRenderer<>(context);
    }

    public static XianSheHuanYingBillboardRenderer<XianSheHuanYingTargetEntity> forTarget(EntityRendererProvider.Context context) {
        return new XianSheHuanYingBillboardRenderer<>(context);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        if (entity instanceof XianSheHuanYingUserEntity user) {
            // The caster's own eyes float above the CASTER's own head — the "subject" the eye
            // pair anchors to is the owner (found the same way the old snake placement used to
            // resolve it), not some separate target.
            UUID ownerId = user.getSyncedOwnerId();
            Entity owner = ownerId != null ? user.level().getPlayerByUUID(ownerId) : null;
            boolean dismissing = user.isDismissing();
            int dismissStartTick = user.getDismissStartTick();
            renderEyePairAndAura(poseStack, buffer, packedLight, user, partialTick, age,
                    owner, dismissing, dismissStartTick, false);
            // Smoke is a real particle now (round 15), spawned client-side from
            // XianSheHuanYingUserEntity's own tick — nothing to draw here.
        } else if (entity instanceof XianSheHuanYingTargetEntity marker) {
            OptionalInt syncedId = marker.getSyncedTargetEntityId();
            Entity target = syncedId.isPresent() ? marker.level().getEntity(syncedId.getAsInt()) : null;
            renderEyePairAndAura(poseStack, buffer, packedLight, marker, partialTick, age,
                    target, marker.isDismissing(), marker.getDismissStartTick(), true);
        }
    }

    /** Eye pair above {@code subject}'s head — shared by BOTH the caster's own eyes (round 12,
     *  {@code subject} = the caster) and the locked target's eyes ({@code subject} = the target),
     *  since spec calls for identical texture/size/open-close timing on both
     *  ("จังหวะลืมตาของผู้ใช้เท่ากับของเป้า"). {@code anchor} is this renderer's own entity (the
     *  one {@code render()} was called for) — used only for the interpolated-position fallback
     *  and as the RNG seed source for the aura lines. {@code withAura} draws the purple aura
     *  lines too (target side only, per spec — the caster never gets them). */
    private void renderEyePairAndAura(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                      Entity anchor, float partialTick, float age, Entity subject,
                                      boolean dismissing, int dismissStartTick, boolean withAura) {
        double[] anchorPos = interpolated(anchor, partialTick);
        double ex = anchorPos[0];
        double ey = anchorPos[1];
        double ez = anchorPos[2];

        double dx = 0.0;
        double dz = 0.0;
        double dy = XianSheHuanYingConstants.EYE_CENTER_HEIGHT;
        double subjectX = ex;
        double subjectY = ey;
        double subjectZ = ez;
        double subjectHeight = 0.0;

        if (subject != null) {
            double[] subjectPos = interpolated(subject, partialTick);
            subjectX = subjectPos[0];
            subjectY = subjectPos[1];
            subjectZ = subjectPos[2];
            subjectHeight = subject.getBoundingBox().getYsize();
            dx = subjectX - ex;
            dz = subjectZ - ez;
            dy = (subjectY - ey) + subjectHeight + XianSheHuanYingConstants.EYE_HEIGHT_MARGIN;
        }

        double bob = Math.sin(age * (Mth.TWO_PI / XianSheHuanYingConstants.BOB_PERIOD_TICKS))
                * XianSheHuanYingConstants.BOB_AMPLITUDE;

        float openFactor = openFactorFor(dismissing, dismissStartTick, age);
        renderEyePair(poseStack, buffer, packedLight, dx, dy + bob, dz, openFactor);

        if (withAura && subject != null) {
            // Uses the same open/close factor as the eyes: shrinks toward the subject during
            // dismissing exactly like the eye pair does, per spec.
            renderAuraLines(poseStack, buffer, anchor, ex, ey, ez, subjectX, subjectY, subjectZ,
                    subjectHeight, age, openFactor);
        }
    }

    /** Two copies of the same eye texture, side by side in the VIEWER's screen space (not world
     *  space — see the localOffsetX parameter on {@link #renderQuad}), so the gap between them
     *  reads as constant regardless of view angle. The right-hand copy (the source image is
     *  already a right eye) is drawn as-is; the left is U-mirrored. Only height eases in with
     *  {@code openFactor} — width stays fixed, per spec ("บีบความสูง ... ความกว้างคงที่") — and
     *  it eases from the vertical center, i.e. the eye "opens"/"closes" like an eyelid rather than
     *  growing from the bottom. {@code openFactor} is computed by the caller as either the normal
     *  opening ease (spawning) or {@link #closeFactor} (dismissing, driven by the synced dismiss
     *  state). */
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

    /** Purple lines falling from just above the subject's head down to its feet, ringing the body
     *  at a fixed radius so they never bunch at the chest. Pure time-based rendering — no
     *  particles, no per-line state anywhere: each line's fall position is a function of
     *  {@code age} (client tickCount+partialTick) and a fixed per-line phase (seeded from
     *  {@code anchor}'s own entity id + line index, same determinism technique this file has used
     *  since the free-floating mist), so it's stable across frames and identical on every client
     *  without syncing anything. Each line is a vertical quad rotated ONLY around world Y to face
     *  the camera horizontally (never tilts with camera pitch, per spec) — unlike the full 3-axis
     *  billboard the eyes use, so it doesn't share {@link #renderQuad}. Drawn over a plain white
     *  texture, tinted purple via vertex color, forced full-bright regardless of
     *  {@code RENDER_FULL_BRIGHT} (the aura is meant to read clearly even at night), through the
     *  same cutout-no-cull RenderType as everything else here — never additive.
     *  {@code visibleFactor} shrinks each line's length toward 0 (anchored at its bottom, so it
     *  looks like it recedes into the ground) during dismissing, exactly mirroring the eye pair. */
    private void renderAuraLines(PoseStack poseStack, MultiBufferSource buffer, Entity anchor,
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
            RandomSource rnd = RandomSource.create(anchor.getId() * 104_729L + i * 65_537L);
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

    /** Ease-out-back (Penner's standard "back" easing): overshoots past 1.0 partway through, then
     *  settles exactly at 1.0 — used for the eye pair opening. With overshoot &lt;= 0 this
     *  degenerates algebraically to {@code 1-(1-t)^3} — a strong ease-out cubic with no bounce —
     *  same family, just c1=0, exactly as spec'd. */
    private static float easeOutBackFactor(float age, float delayTicks, float durationTicks, float overshoot) {
        float t = Mth.clamp((age - delayTicks) / durationTicks, 0.0f, 1.0f);
        float c1 = overshoot;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }

    /** Ease-IN (quadratic) collapse from 1 to 0 over durationTicks, starting at dismissStartTick —
     *  both read from the same entity.tickCount space the appear animation uses, so no separate
     *  clock is needed. Purely client-side/render-only: it reads the synced dismissStartTick but
     *  never touches server lifecycle itself. */
    private static float closeFactor(int dismissStartTick, float age, float durationTicks) {
        float t = Mth.clamp((age - dismissStartTick) / durationTicks, 0.0f, 1.0f);
        return 1.0f - t * t;
    }

    /** Picks the opening curve (spawning) or {@link #closeFactor} (dismissing) for the eye pair.
     *  Dismiss/close is untouched. Opening (round 15) is now 3 phases, all in the same
     *  entity.tickCount space XianSheHuanYingTargetEntity's sound-timing check reads:
     *  <ol>
     *  <li>0 until EYE_OPEN_DELAY_TICKS ("ก่อนลืมตาพรึบ"),</li>
     *  <li>a linear rise 0 -> EYE_SQUINT_FRACTION over EYE_SQUINT_RISE_TICKS ("ตาโผล่เป็นเส้นหรี่"),</li>
     *  <li>a hold at EYE_SQUINT_FRACTION for EYE_SQUINT_TICKS ("ค้างไว้"),</li>
     *  <li>then {@link #easeOutBackFactor}, reparametrized from its native [0,1] output range to
     *  [EYE_SQUINT_FRACTION, 1.0] (raw==0 lands exactly on the squint height it's continuing from,
     *  raw==1 lands exactly on fully open) — this is "เบิกด้วย easeOutBack เดิม จาก SQUINT -> 1.0".
     *  </ol> */
    private static float openFactorFor(boolean dismissing, int dismissStartTick, float age) {
        if (dismissing) {
            return closeFactor(dismissStartTick, age, XianSheHuanYingConstants.EYE_CLOSE_TICKS);
        }
        float squintStart = XianSheHuanYingConstants.EYE_OPEN_DELAY_TICKS;
        float squintRiseEnd = squintStart + XianSheHuanYingConstants.EYE_SQUINT_RISE_TICKS;
        float squint = XianSheHuanYingConstants.EYE_SQUINT_FRACTION;
        if (age <= squintStart) {
            return 0.0f;
        }
        if (age < squintRiseEnd) {
            return Mth.lerp((age - squintStart) / XianSheHuanYingConstants.EYE_SQUINT_RISE_TICKS, 0.0f, squint);
        }
        if (age < XianSheHuanYingConstants.EYE_OPEN_EASE_START_TICK) {
            return squint;
        }
        float raw = easeOutBackFactor(age, XianSheHuanYingConstants.EYE_OPEN_EASE_START_TICK,
                XianSheHuanYingConstants.EYE_OPEN_TICKS, XianSheHuanYingConstants.EYE_OPEN_OVERSHOOT);
        return squint + (1.0f - squint) * raw;
    }

    /** Interpolated (x, y, z) of any entity with partialTick — the exact same
     *  {@code Mth.lerp(partialTick, eOld, e.get())} formula the eye code already used for both
     *  the anchor and the subject, now shared so the smoke center uses it too instead of a new
     *  copy of the formula. */
    private static double[] interpolated(Entity e, float partialTick) {
        return new double[]{
                Mth.lerp(partialTick, e.xOld, e.getX()),
                Mth.lerp(partialTick, e.yOld, e.getY()),
                Mth.lerp(partialTick, e.zOld, e.getZ())
        };
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

    /** The entity's own box is tiny and sits on the caster's/target's feet while the eye image
     *  floats elsewhere, so frustum culling would drop it wrongly. Same fix CrystalHydroDomeRenderer
     *  uses. */
    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return EYE_TEXTURE;
    }

    // ---- Custom RenderType: opaque cutout, no cull, blur switchable via TEXTURE_SMOOTH ----------

    /** Memoized per texture so repeated per-frame calls (eyes + aura, every entity, every frame)
     *  don't rebuild a CompositeState each time — same reasoning vanilla's own entityCutoutNoCull
     *  uses Util.memoize for. Rebuilt (new map) only on class (re)load, i.e. a resource-pack/game
     *  reload — fine, since TEXTURE_SMOOTH is a compile-time constant here. */
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
