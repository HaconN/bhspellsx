package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.UUID;

/**
 * Target-side half of Xian She Huan Ying: no gameplay logic, it only follows the locked target
 * (the hook for round 2's billboard image). Its lifetime belongs to XianSheHuanYingUserEntity,
 * which discards it in remove(); the controller check below is a backstop so this can never
 * outlive it. Never saved.
 */
public class XianSheHuanYingTargetEntity extends Entity {
    private UUID targetId;
    private UUID controllerId;

    public XianSheHuanYingTargetEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public XianSheHuanYingTargetEntity(Level level, LivingEntity target, UUID controllerId) {
        this(BHXEntityRegistry.XIAN_SHE_HUAN_YING_TARGET.get(), level);
        this.targetId = target.getUUID();
        this.controllerId = controllerId;
        this.setPos(target.getX(), target.getY(), target.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || this.targetId == null || this.controllerId == null) {
            this.discard();
            return;
        }
        Entity controller = serverLevel.getEntity(this.controllerId);
        Entity resolved = serverLevel.getEntity(this.targetId);
        if (controller == null || !(resolved instanceof LivingEntity target) || !target.isAlive()) {
            this.discard();
            return;
        }
        this.setPos(target.getX(), target.getY(), target.getZ());

        if (this.tickCount % XianSheHuanYingConstants.VFX_INTERVAL_TICKS == 0) {
            // Temporary round-1 VFX: marker above the target's head.
            serverLevel.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() + 0.6,
                    target.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
        }
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
