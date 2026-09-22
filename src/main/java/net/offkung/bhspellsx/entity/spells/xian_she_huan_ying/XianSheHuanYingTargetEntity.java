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
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Target-side half of Xian She Huan Ying: no gameplay logic, it only follows the locked target
 * (the hook for round 2's billboard image). Its lifetime belongs to XianSheHuanYingUserEntity,
 * which discards it in remove(); the controller check below is a backstop so this can never
 * outlive it. Never saved.
 */
public class XianSheHuanYingTargetEntity extends Entity {
    /** The locked target's entity id, synced to clients so the renderer can look it up with
     *  level().getEntity(id) and follow its own interpolated position (render-only; the server
     *  never reads it back). An id, not a UUID, because the target can be a mob. */
    private static final EntityDataAccessor<OptionalInt> DATA_TARGET_ENTITY_ID =
            SynchedEntityData.defineId(XianSheHuanYingTargetEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);

    private UUID targetId;
    private UUID controllerId;

    public XianSheHuanYingTargetEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Client-side accessor for the synced target entity id (empty until synced). */
    public OptionalInt getSyncedTargetEntityId() {
        return this.entityData.get(DATA_TARGET_ENTITY_ID);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
