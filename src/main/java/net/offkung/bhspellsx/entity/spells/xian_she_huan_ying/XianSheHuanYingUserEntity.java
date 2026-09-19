package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.UUID;

/**
 * User-side half of Xian She Huan Ying, and the owner of the whole lifecycle. Every server tick
 * it resolves the caster and the locked target by UUID (never a held Entity reference) via
 * ServerLevel#getEntity — same pattern as AmethystDecreeTargetCrystalEntity — and it discards
 * itself, and the paired target-side entity, the moment any of these is true:
 * <ul>
 *   <li>the caster no longer has {@link XianSheHuanYingConstants#OWNER_TAG} (Apoli's toggle-off
 *       and mana-out both funnel through removing that tag);</li>
 *   <li>the caster or the target can't be resolved in this level (logged out, changed
 *       dimension, chunk unloaded) or is dead.</li>
 * </ul>
 * There is no fixed lifetime. Slowness I is applied with a short duration and refreshed on an
 * interval, and is never removed on discard, so the target is freed by expiry alone.
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
        this.targetId = target.getUUID();
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    /** Called once by the spell after it spawns the target-side entity. */
    public void setTargetEntityId(UUID id) {
        this.targetEntityId = id;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || this.ownerId == null || this.targetId == null) {
            this.discard();
            return;
        }
        Entity owner = serverLevel.getEntity(this.ownerId);
        if (!(owner instanceof LivingEntity ownerLiving) || !ownerLiving.isAlive()) {
            // Owner dead, or not in this level any more (changed dimension). Logged out: no
            // player in the list, nothing is sent.
            notifyOwner(serverLevel, "ui.bhspellsx.xian_she_huan_ying_lost");
            this.discard();
            return;
        }
        if (!ownerLiving.getTags().contains(XianSheHuanYingConstants.OWNER_TAG)) {
            this.discard();
            return;
        }
        Entity resolved = serverLevel.getEntity(this.targetId);
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            notifyOwner(serverLevel, resolved instanceof LivingEntity
                    ? "ui.bhspellsx.xian_she_huan_ying_target_dead"
                    : "ui.bhspellsx.xian_she_huan_ying_lost");
            ownerLiving.removeTag(XianSheHuanYingConstants.OWNER_TAG);
            this.discard();
            return;
        }
        this.setPos(ownerLiving.getX(), ownerLiving.getY(), ownerLiving.getZ());

        if (this.tickCount % XianSheHuanYingConstants.SLOW_REFRESH_INTERVAL_TICKS == 0) {
            // Plain addEffect, never a forced replace, and never removed on discard: a stronger
            // or longer Slowness from someone else is left alone, ours simply expires.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    XianSheHuanYingConstants.SLOW_DURATION_TICKS, XianSheHuanYingConstants.SLOW_AMPLIFIER,
                    false, true, true));
        }
        if (this.tickCount % XianSheHuanYingConstants.VFX_INTERVAL_TICKS == 0) {
            // Temporary round-1 VFX: soul particles around the caster.
            serverLevel.sendParticles(ParticleTypes.SOUL, ownerLiving.getX(), ownerLiving.getY() + 1.0,
                    ownerLiving.getZ(), 3, 0.5, 0.7, 0.5, 0.01);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
