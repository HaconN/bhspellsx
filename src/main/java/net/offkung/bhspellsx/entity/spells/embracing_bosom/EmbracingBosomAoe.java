package net.offkung.bhspellsx.entity.spells.embracing_bosom;

import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;
import net.offkung.bhspellsx.registry.BHXMobEffectRegistry;

import java.util.List;
import java.util.Optional;

/**
 * Modelled on irons_spellbooks' own HealingAoe (io.redspace.ironsspellbooks.entity.spells.HealingAoe)
 * for the canHitEntity()-based target predicate, and on HealingCircleSpell.onCast for spawning
 * (see EmbracingBosomSpell).
 * <p>
 * The heal is NOT applied here — it lives in EmbracingBosomEffect.applyEffectTick() so it keeps
 * firing for the 2s the buff lingers after a player leaves the zone. This entity's only job is
 * applying/refreshing that effect on valid targets every tick; AoeEntity's own
 * checkHits()/applyEffect()/reapplicationDelay plumbing is unused (applyEffect() is an
 * intentional no-op, same as bhspells' own DarkRainFallAoe).
 * <p>
 * All lifetime/radius constants are fixed for this spell (no spell-level scaling), so they're
 * baked into the constructor rather than set by the caller — the caller (EmbracingBosomSpell) only
 * needs to setOwner/setPos/addFreshEntity.
 * <p>
 * Placeholder VFX only (vanilla particles) — replaced with real effects in Phase 2.
 */
public class EmbracingBosomAoe extends AoeEntity {
    private static final float RADIUS = 6.0f;
    private static final int LIFETIME_TICKS = 160;
    private static final int BUFF_DURATION_TICKS = 40;
    private static final int PARTICLE_INTERVAL_TICKS = 4;
    private static final int RING_POINTS = 12;

    public EmbracingBosomAoe(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.duration = LIFETIME_TICKS;
        this.setRadius(RADIUS);
    }

    public EmbracingBosomAoe(Level level) {
        this(BHXEntityRegistry.EMBRACING_BOSOM_AOE.get(), level);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        if (this.tickCount <= this.getDelay()) {
            return;
        }
        if (this.tickCount % PARTICLE_INTERVAL_TICKS == 0) {
            spawnPlaceholderParticles();
        }
        applyBuffToNearbyTargets();
    }

    /** Unused — see class javadoc. Still required: abstract in AoeEntity. */
    @Override
    public void applyEffect(LivingEntity target) {
    }

    /**
     * The single target predicate, reused wherever this entity needs to pick targets
     * (currently just applyBuffToNearbyTargets() below).
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        return target instanceof Player player && player.isAlive() && !player.isSpectator();
    }

    @Override
    protected boolean canHitTargetForGroundContext(LivingEntity target) {
        // No ground-contact requirement in the spec — neutralize the base class's default filter
        // so canHitEntity() above is the only thing gating targets.
        return true;
    }

    private void applyBuffToNearbyTargets() {
        List<Player> targets = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox(), this::canHitEntity);
        for (Player target : targets) {
            // ambient=false, visible=false (no particle spam on refresh), showIcon=true.
            target.addEffect(new MobEffectInstance(BHXMobEffectRegistry.EMBRACING_BOSOM.get(),
                    BUFF_DURATION_TICKS, 0, false, false, true));
        }
    }

    private void spawnPlaceholderParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        float radius = this.getRadius();
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / RING_POINTS;
            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, centerY + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        serverLevel.sendParticles(ParticleTypes.COMPOSTER, centerX, centerY + 0.1, centerZ, 1, 0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public float getParticleCount() {
        return 0.0f;
    }

    @Override
    public Optional<ParticleOptions> getParticle() {
        return Optional.empty();
    }
}
