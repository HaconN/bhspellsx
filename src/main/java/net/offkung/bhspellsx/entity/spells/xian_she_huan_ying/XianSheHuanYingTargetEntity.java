package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.client.sound.XianSheHuanYingEyeSound;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Target-side half of Xian She Huan Ying: no gameplay logic, it only follows the locked target
 * (the hook for the billboard image). Its lifetime belongs entirely to
 * XianSheHuanYingUserEntity — that class is the single place every end condition is checked; this
 * one only reacts to {@link #beginDismissing} (called by the controller) or to the controller
 * itself having already vanished (the {@code controller == null} check below, a backstop for the
 * controller's own hard-discard cases, which cascade here via its {@code remove()} anyway). It no
 * longer discards itself for target-dead/unresolved — see the controller's class javadoc for why
 * that single-source-of-truth split matters (two independent checks could disagree by a tick).
 * Never saved.
 */
public class XianSheHuanYingTargetEntity extends Entity {
    /** The locked target's entity id, synced to clients so the renderer can look it up with
     *  level().getEntity(id) and follow its own interpolated position (render-only; the server
     *  never reads it back). An id, not a UUID, because the target can be a mob. */
    private static final EntityDataAccessor<OptionalInt> DATA_TARGET_ENTITY_ID =
            SynchedEntityData.defineId(XianSheHuanYingTargetEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    /** Mirrors the controller's own dismissing flag — set only via {@link #beginDismissing}. */
    private static final EntityDataAccessor<Boolean> DATA_DISMISSING =
            SynchedEntityData.defineId(XianSheHuanYingTargetEntity.class, EntityDataSerializers.BOOLEAN);
    /** This entity's own tickCount value when beginDismissing() was called. */
    private static final EntityDataAccessor<Integer> DATA_DISMISS_START_TICK =
            SynchedEntityData.defineId(XianSheHuanYingTargetEntity.class, EntityDataSerializers.INT);

    private UUID targetId;
    private UUID controllerId;
    /** Client-side only, not synced: each client has its own ticking copy of this entity, so a
     *  plain per-instance flag is enough to play the eye-stare cue at most once per lock, on that
     *  client alone. See {@link #tickClientEyeSound}. */
    private boolean eyeSoundPlayed;

    public XianSheHuanYingTargetEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Client-side accessor for the synced target entity id (empty until synced). */
    public OptionalInt getSyncedTargetEntityId() {
        return this.entityData.get(DATA_TARGET_ENTITY_ID);
    }

    public boolean isDismissing() {
        return this.entityData.get(DATA_DISMISSING);
    }

    public int getDismissStartTick() {
        return this.entityData.get(DATA_DISMISS_START_TICK);
    }

    /** Called by the controller (XianSheHuanYingUserEntity) only — see the class javadoc. A
     *  no-op if already dismissing, so a stray second call can't reset the countdown. */
    public void beginDismissing() {
        if (this.isDismissing()) {
            return;
        }
        this.entityData.set(DATA_DISMISSING, true);
        this.entityData.set(DATA_DISMISS_START_TICK, this.tickCount);
    }

    public XianSheHuanYingTargetEntity(Level level, LivingEntity target, UUID controllerId) {
        this(BHXEntityRegistry.XIAN_SHE_HUAN_YING_TARGET.get(), level);
        this.targetId = target.getUUID();
        this.entityData.set(DATA_TARGET_ENTITY_ID, OptionalInt.of(target.getId()));
        this.controllerId = controllerId;
        this.setPos(target.getX(), target.getY(), target.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickClientEyeSound();
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || this.targetId == null || this.controllerId == null) {
            this.discard();
            return;
        }
        if (this.isDismissing()) {
            // Pure countdown — no end-condition checks here, see the class javadoc. Best-effort
            // cosmetic follow only; a target that dies/vanishes mid-dismiss just freezes the eye
            // pair in place rather than cutting the animation short or discarding early.
            Entity resolved = serverLevel.getEntity(this.targetId);
            if (resolved instanceof LivingEntity target) {
                this.setPos(target.getX(), target.getY(), target.getZ());
            }
            if (this.tickCount - this.getDismissStartTick() >= XianSheHuanYingConstants.DISMISS_TICKS) {
                this.discard();
            }
            return;
        }
        Entity controller = serverLevel.getEntity(this.controllerId);
        if (controller == null) {
            // Backstop only: the controller's own hard-discard cases already cascade to us via
            // its remove(). Every graceful end condition instead arrives via beginDismissing().
            this.discard();
            return;
        }
        Entity resolved = serverLevel.getEntity(this.targetId);
        if (resolved instanceof LivingEntity target) {
            this.setPos(target.getX(), target.getY(), target.getZ());
        }
    }

    /** Plays the "being watched" cue once, at the exact tick the FULL open-ease begins — i.e.
     *  after the round-15 squint-and-hold phase finishes, not at EYE_OPEN_DELAY_TICKS anymore
     *  ("เสียงลืมตาต้องย้ายไปดังตอนเริ่มเบิก (หลังหรี่จบ)"). Reads
     *  {@code EYE_OPEN_EASE_START_TICK}, the same derived constant the renderer's
     *  {@code openFactorFor} uses for that phase transition, so the two can't drift apart. All of
     *  the actual client-type work (finding the local player, matching it against the target,
     *  resolving/playing the sound) lives in {@link XianSheHuanYingEyeSound}, a client-only class
     *  — this method and this whole class never reference {@code net.minecraft.client.*}
     *  directly; see that class's javadoc for why that separation matters even inside an
     *  {@code isClientSide()} branch. Does not touch lifecycle, tag, or Slowness in any way. */
    private void tickClientEyeSound() {
        if (this.eyeSoundPlayed || this.isRemoved() || this.isDismissing()) {
            return;
        }
        if (this.tickCount != XianSheHuanYingConstants.EYE_OPEN_EASE_START_TICK) {
            return;
        }
        OptionalInt syncedId = this.getSyncedTargetEntityId();
        if (syncedId.isEmpty()) {
            return;
        }
        this.eyeSoundPlayed = true;
        XianSheHuanYingEyeSound.playIfLocalPlayerIsTarget(syncedId.getAsInt());
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
        this.entityData.define(DATA_TARGET_ENTITY_ID, OptionalInt.empty());
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
