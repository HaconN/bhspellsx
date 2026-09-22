package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.client.XianSheHuanYingTailParticleEmitter;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.Optional;
import java.util.UUID;

/**
 * User-side half of Xian She Huan Ying, and the owner of the whole lifecycle. Every server tick
 * (while not already dismissing) it resolves the caster and the locked target by UUID (never a
 * held Entity reference) via ServerLevel#getEntity — same pattern as
 * AmethystDecreeTargetCrystalEntity.
 * <p>
 * This is the single source of truth for every end condition (the caster losing
 * {@link XianSheHuanYingConstants#OWNER_TAG}, either side dying, either side becoming
 * unresolvable) — the paired target-side entity no longer checks any of these itself; it only
 * reacts to {@link #beginDismissing}. Two ways an end condition resolves:
 * <ul>
 *   <li><b>Hard discard, no animation</b> — only when the caster or the target can't be resolved
 *       in this level AT ALL (logged out, changed dimension, chunk unloaded). There's no position
 *       left to hold a closing animation at, so the safer choice (per spec) is to end instantly
 *       rather than risk a lingering entity anchored to nothing.</li>
 *   <li><b>Graceful dismiss</b> — every other end condition (tag removed, either side actually
 *       dead but still resolvable/has a last position, target unreachable-but-alive is NOT a
 *       thing here so not listed) enters the "dismissing" state via {@link #beginDismissing} for
 *       {@link XianSheHuanYingConstants#DISMISS_TICKS}, then discards. {@link #tickDismissing}
 *       deliberately re-checks nothing — it is a pure, unconditional countdown to discard, so
 *       there is no path that can dismiss forever.</li>
 * </ul>
 * There is no fixed lifetime otherwise. Slowness I is applied with a short duration and refreshed
 * on an interval while running, and is never removed on discard/dismiss start — the target is
 * freed by expiry alone, per spec.
 * <p>
 * Extends plain Entity, not AoeEntity like the crystal entities: AoeEntity.tick() discards
 * itself after its 600-tick default duration, which is wrong for an unbounded lifetime. The
 * entity follows the caster every tick so it stays in a ticking chunk for as long as the caster
 * is, and is never saved (a restart simply ends the lock).
 * <p>
 * If the lock ends for a reason other than Apoli removing the tag (target died, ...), the tag is
 * stripped here so Apoli's sync power sees "toggle on, tag missing" and switches the toggle off.
 */
public class XianSheHuanYingUserEntity extends Entity {
    /** Caster's UUID, synced to clients so the renderer can find the caster and follow the
     *  caster's own interpolated position/yaw (render-only; the server never reads it back). */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(XianSheHuanYingUserEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** True once an end condition has fired and this is just counting down to discard — see the
     *  class javadoc. Render-only downstream (drives the reverse-appear animation); the tag/mana
     *  side of ending is already final by the time this flips. */
    private static final EntityDataAccessor<Boolean> DATA_DISMISSING =
            SynchedEntityData.defineId(XianSheHuanYingUserEntity.class, EntityDataSerializers.BOOLEAN);
    /** This entity's own tickCount value at the moment dismissing began — the renderer computes
     *  "ticks into the close animation" as entity.tickCount - this, exactly mirroring how the
     *  appear animation already reads entity.tickCount directly. */
    private static final EntityDataAccessor<Integer> DATA_DISMISS_START_TICK =
            SynchedEntityData.defineId(XianSheHuanYingUserEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private UUID targetId;
    private UUID targetEntityId;

    public XianSheHuanYingUserEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public XianSheHuanYingUserEntity(Level level, LivingEntity owner, LivingEntity target) {
        this(BHXEntityRegistry.XIAN_SHE_HUAN_YING_USER.get(), level);
        this.ownerId = owner.getUUID();
        this.entityData.set(DATA_OWNER_UUID, Optional.of(owner.getUUID()));
        this.targetId = target.getUUID();
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    /** Client-side accessor for the synced caster UUID (null until synced). */
    public UUID getSyncedOwnerId() {
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    public boolean isDismissing() {
        return this.entityData.get(DATA_DISMISSING);
    }

    public int getDismissStartTick() {
        return this.entityData.get(DATA_DISMISS_START_TICK);
    }

    /** Called once by the spell after it spawns the target-side entity. */
    public void setTargetEntityId(UUID id) {
        this.targetEntityId = id;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickClientTailParticles();
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }
        if (this.isDismissing()) {
            tickDismissing(serverLevel);
            return;
        }
        if (this.ownerId == null || this.targetId == null) {
            this.discard();
            return;
        }
        Entity owner = serverLevel.getEntity(this.ownerId);
        if (!(owner instanceof LivingEntity ownerLiving)) {
            // Owner not resolvable in this level at all (logged out, changed dimension, chunk
            // unloaded) — no reliable position to hold a dismiss animation at, so (per spec) this
            // is the one case that still ends instantly rather than dismissing.
            notifyOwner(serverLevel, "ui.bhspellsx.xian_she_huan_ying_lost");
            this.discard();
            return;
        }
        if (!ownerLiving.isAlive()) {
            // Owner resolved but dead: the corpse is still a valid position for a few ticks, so
            // this dismisses gracefully instead of vanishing mid-death.
            notifyOwner(serverLevel, "ui.bhspellsx.xian_she_huan_ying_lost");
            beginDismissing(serverLevel);
            return;
        }
        if (!ownerLiving.getTags().contains(XianSheHuanYingConstants.OWNER_TAG)) {
            beginDismissing(serverLevel);
            return;
        }
        Entity resolved = serverLevel.getEntity(this.targetId);
        if (!(resolved instanceof LivingEntity target)) {
            // Target not resolvable at all — same reasoning as the owner-missing case above.
            notifyOwner(serverLevel, "ui.bhspellsx.xian_she_huan_ying_lost");
            ownerLiving.removeTag(XianSheHuanYingConstants.OWNER_TAG);
            this.discard();
            return;
        }
        if (!target.isAlive()) {
            notifyOwner(serverLevel, "ui.bhspellsx.xian_she_huan_ying_target_dead");
            ownerLiving.removeTag(XianSheHuanYingConstants.OWNER_TAG);
            beginDismissing(serverLevel);
            return;
        }
        this.setPos(ownerLiving.getX(), ownerLiving.getY(), ownerLiving.getZ());
        // Render-only fallback: used by the renderer only if the caster can't be found client-side.
        // Nothing server-side reads it.
        this.setYRot(ownerLiving.getYRot());

        if (this.tickCount % XianSheHuanYingConstants.SLOW_REFRESH_INTERVAL_TICKS == 0) {
            // Plain addEffect, never a forced replace, and never removed on discard: a stronger
            // or longer Slowness from someone else is left alone, ours simply expires.
            // showParticles=false (round 7: the billboard/aura are the visual now, the swirling
            // effect particles were redundant clutter); showIcon stays true.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    XianSheHuanYingConstants.SLOW_DURATION_TICKS, XianSheHuanYingConstants.SLOW_AMPLIFIER,
                    false, false, true));
        }
        if (this.tickCount % XianSheHuanYingConstants.SOUL_INTERVAL_TICKS == 0) {
            // Faint soul smoke around the caster; the billboard is the main visual.
            serverLevel.sendParticles(ParticleTypes.SOUL, ownerLiving.getX(), ownerLiving.getY() + 1.0,
                    ownerLiving.getZ(), XianSheHuanYingConstants.SOUL_PARTICLE_COUNT, 0.5, 0.7, 0.5, 0.01);
        }
    }

    /** Enters the dismissing state and tells the target-side entity to do the same, at the same
     *  moment — see the class javadoc. Slowness is not touched here: it simply stops being
     *  refreshed (this method always returns out of the normal per-tick body above) and expires
     *  on its own within SLOW_DURATION_TICKS, per spec. */
    private void beginDismissing(ServerLevel serverLevel) {
        this.entityData.set(DATA_DISMISSING, true);
        this.entityData.set(DATA_DISMISS_START_TICK, this.tickCount);
        if (this.targetEntityId != null) {
            Entity marker = serverLevel.getEntity(this.targetEntityId);
            if (marker instanceof XianSheHuanYingTargetEntity targetEntity) {
                targetEntity.beginDismissing();
            }
        }
    }

    /** Pure countdown to discard — deliberately re-checks NO end condition (tag, owner/target
     *  alive-ness, ...), so it can never re-enter the branches above or send another message.
     *  Cosmetically keeps following the owner if it's still resolvable, purely so the closing
     *  snake/mist don't freeze mid-air if the owner is still walking around; failing to resolve
     *  the owner here does not extend or cancel the countdown. */
    private void tickDismissing(ServerLevel serverLevel) {
        if (this.ownerId != null) {
            Entity owner = serverLevel.getEntity(this.ownerId);
            if (owner instanceof LivingEntity ownerLiving) {
                this.setPos(ownerLiving.getX(), ownerLiving.getY(), ownerLiving.getZ());
                this.setYRot(ownerLiving.getYRot());
            }
        }
        if (this.tickCount - this.getDismissStartTick() >= XianSheHuanYingConstants.DISMISS_TICKS) {
            this.discard();
        }
    }

    /** Action-bar message to the caster only. Fires from the discard paths above, each of which
     *  runs once (the entity is gone right after), so it can never repeat per tick. Resolved
     *  through the player list so a caster who changed dimension still gets it, and a caster who
     *  logged out is simply skipped. The tag-removed path (Apoli's toggle-off / mana-out) sends
     *  nothing here — Apoli sends those two itself. */
    private void notifyOwner(ServerLevel serverLevel, String translationKey) {
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.ownerId);
        if (player != null) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    /** Client-only: spawns thin vanilla-particle "tail smoke" along the fading tail, timed purely
     *  to entity.tickCount — never from the renderer (which runs per FRAME, so the spawn rate
     *  would wobble with framerate). Only once the snake has fully appeared (tickCount reaches
     *  APPEAR_TICKS, same threshold the renderer's own appear ease uses to reach factor 1.0), and
     *  never while dismissing. All Minecraft/Camera-touching work lives in
     *  {@link XianSheHuanYingTailParticleEmitter}, a client-only class — this method (and this
     *  whole entity class) references no {@code net.minecraft.client.*} type, same split as
     *  XianSheHuanYingTargetEntity's own eye-sound cue. */
    private void tickClientTailParticles() {
        if (this.isRemoved() || this.isDismissing()) {
            return;
        }
        if (this.tickCount < XianSheHuanYingConstants.APPEAR_TICKS) {
            return;
        }
        RandomSource random = this.level().getRandom();
        if (random.nextFloat() >= XianSheHuanYingConstants.TAIL_PARTICLES_PER_TICK) {
            return;
        }
        UUID ownerId = this.getSyncedOwnerId();
        if (ownerId == null) {
            return;
        }
        Player owner = this.level().getPlayerByUUID(ownerId);
        if (owner == null) {
            return;
        }

        // Same shared formula the renderer uses for the snake's own center (see
        // XianSheHuanYingSnakePose) — plain per-tick position/yaw here, no partial-tick
        // interpolation needed for a particle spawn point, and the snake has already fully
        // appeared (checked above) so bob is the only additive term, rise is always 0.
        double yawRad = Math.toRadians(owner.getYRot());
        double bob = Math.sin(this.tickCount * (Mth.TWO_PI / XianSheHuanYingConstants.BOB_PERIOD_TICKS))
                * XianSheHuanYingConstants.BOB_AMPLITUDE;
        double[] centerOffset = XianSheHuanYingSnakePose.centerOffset(yawRad, bob, 0.0);
        double centerX = owner.getX() + centerOffset[0];
        double centerY = owner.getY() + centerOffset[1];
        double centerZ = owner.getZ() + centerOffset[2];

        // Pick a random point along the tail-fade pixel path, convert to the snake quad's own
        // local (x, y) exactly as renderQuad's centered, unflipped UV mapping does (see
        // XianSheHuanYingConstants.TAIL_PATH_PIXEL_X/Y's own javadoc), then jitter it.
        int lastPoint = XianSheHuanYingConstants.TAIL_PATH_PIXEL_X.length - 1;
        int segment = random.nextInt(lastPoint);
        float t = random.nextFloat();
        float pixelX = Mth.lerp(t, XianSheHuanYingConstants.TAIL_PATH_PIXEL_X[segment],
                XianSheHuanYingConstants.TAIL_PATH_PIXEL_X[segment + 1]);
        float pixelY = Mth.lerp(t, XianSheHuanYingConstants.TAIL_PATH_PIXEL_Y[segment],
                XianSheHuanYingConstants.TAIL_PATH_PIXEL_Y[segment + 1]);
        float localX = (pixelX / 256.0f - 0.5f) * XianSheHuanYingConstants.SNAKE_WIDTH
                + (random.nextFloat() - 0.5f) * 2.0f * XianSheHuanYingConstants.TAIL_PARTICLE_JITTER;
        float localY = (0.5f - pixelY / 256.0f) * XianSheHuanYingConstants.SNAKE_HEIGHT
                + (random.nextFloat() - 0.5f) * 2.0f * XianSheHuanYingConstants.TAIL_PARTICLE_JITTER;

        XianSheHuanYingTailParticleEmitter.spawnAtLocalOffset(this.level(), centerX, centerY, centerZ, localX, localY);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Pair the target-side entity's death to ours regardless of how we were removed.
        if (!this.level().isClientSide() && this.targetEntityId != null
                && this.level() instanceof ServerLevel serverLevel) {
            Entity marker = serverLevel.getEntity(this.targetEntityId);
            if (marker != null) {
                marker.discard();
            }
            this.targetEntityId = null;
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER_UUID, Optional.empty());
        this.entityData.define(DATA_DISMISSING, false);
        this.entityData.define(DATA_DISMISS_START_TICK, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
