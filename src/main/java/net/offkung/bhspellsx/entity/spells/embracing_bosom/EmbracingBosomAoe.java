package net.offkung.bhspellsx.entity.spells.embracing_bosom;

import com.gametechbc.traveloptics.api.particle.AdvancedCylinderParticleManager;
import com.gametechbc.traveloptics.api.particle.ParticleDirection;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;
import net.offkung.bhspellsx.registry.BHXMobEffectRegistry;
import org.joml.Vector3f;

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
 * All lifetime/radius/heal constants are fixed for this spell (no spell-level scaling), so they're
 * baked into the constructor rather than set by the caller — the caller (EmbracingBosomSpell) only
 * needs to setOwner/setPos/addFreshEntity.
 * <p>
 * Phase 2B VFX: traveloptics' AdvancedCylinderParticleManager, called server-side from tick()
 * (it's a stateless static spawner, not an actual persistent "manager" despite the name — see
 * EternalPurificationSpell for the same house-style pattern: continuous effects just call it every
 * tick/interval, one-shot effects call it once). Coloured via vanilla's DustParticleOptions, which
 * accepts any RGB tint — no custom particle type needed for the amber palette.
 * <p>
 * The continuous column (spawnColumnTick) mixes two particle types rather than using
 * DustParticleOptions alone: decompiling DustParticleBase showed its constructor forcibly
 * multiplies whatever velocity we pass in by 0.1 (`xd/yd/zd *= 0.1f`) and applies 0.96 friction
 * per tick on top, with only an ~8-40 tick lifetime — dust particles are built to hover, not
 * travel, and no reasonable `speed` value makes them read as a rising column before they expire.
 * (Gravity is NOT the cause — dust's `gravity` field is never set, defaults to 0.) ParticleTypes.END_ROD
 * (SimpleAnimatedParticle) applies velocity undamped and lives 60-71 ticks, so it actually
 * rises visibly; AMBER_DUST is kept alongside it, sparse, for colour near the base.
 */
public class EmbracingBosomAoe extends AoeEntity {
    private static final float RADIUS = 6.0f;
    // Public (not private) solely so EmbracingBosomRingRenderer (client) can compute fade-in/out
    // timing from the same source of truth — the value/behavior here is unchanged.
    public static final int LIFETIME_TICKS = 160;
    private static final int BUFF_DURATION_TICKS = 40;

    // Single source of truth for the VFX tint — retune this one constant, nothing else.
    private static final int AMBER_TINT_HEX = 0xF0B23D;
    private static final Vector3f AMBER_TINT = new Vector3f(
            ((AMBER_TINT_HEX >> 16) & 0xFF) / 255f,
            ((AMBER_TINT_HEX >> 8) & 0xFF) / 255f,
            (AMBER_TINT_HEX & 0xFF) / 255f);
    // Scale 1.0 matches vanilla's own DustParticleOptions.REDSTONE.
    private static final DustParticleOptions AMBER_DUST = new DustParticleOptions(AMBER_TINT, 1.0f);

    // 3a) Cast-in burst (reference image 2): energy converging inward, radius shrinking from
    // the full 6.0 zone radius down to a point over CONVERGE_BURST_TICKS. 6 particles/tick for
    // 8 ticks = 48 particles total, spent in 0.4s — a brief flourish, not a sustained cost.
    private static final int CONVERGE_BURST_TICKS = 8;
    private static final int CONVERGE_PARTICLES_PER_TICK = 6;
    private static final float CONVERGE_END_RADIUS = 0.4f;
    private static final double CONVERGE_HEIGHT = 1.0;
    private static final double CONVERGE_SPEED = 0.15;

    // 3b) Continuous column (reference image 3), held for the whole 8s lifetime, mixing two
    // particle types (see class javadoc for why DustParticleOptions alone doesn't rise):
    //   - END_ROD carries the actual rising motion, undamped velocity, ~3s lifetime.
    //   - AMBER_DUST adds sparse colour near the base (short-lived/near-static, but still
    //     visible while it lasts).
    // Every 5 ticks: 2 END_ROD + 1 AMBER_DUST = 3 particles/call = 12 particles/sec total,
    // sustained for 160 ticks — same overall density as before, just reallocated between the
    // two types instead of increased.
    private static final int COLUMN_INTERVAL_TICKS = 5;
    private static final int COLUMN_END_ROD_PER_CALL = 2;
    private static final int COLUMN_DUST_PER_CALL = 1;
    private static final double COLUMN_RADIUS = 1.5;
    private static final double COLUMN_HEIGHT = 2.5;
    // END_ROD's velocity passes straight through the particle unmodified (no damping, unlike
    // dust) — 0.04 blocks/tick over its ~60-71 tick lifetime rises roughly 2.5-3 blocks, visible
    // without shooting off screen.
    private static final double COLUMN_END_ROD_SPEED = 0.04;
    // Dust's velocity gets crushed to ~10% by DustParticleBase regardless of this value (see
    // class javadoc); kept modest since it's only meant to sit and glow near the base, not rise.
    private static final double COLUMN_DUST_SPEED = 0.02;

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
        int activeTicks = this.tickCount - this.getDelay();
        if (activeTicks <= CONVERGE_BURST_TICKS) {
            spawnConvergeBurstTick(activeTicks);
        }
        if (activeTicks % COLUMN_INTERVAL_TICKS == 0) {
            spawnColumnTick();
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

    private void spawnConvergeBurstTick(int activeTicks) {
        // activeTicks runs 1..CONVERGE_BURST_TICKS inclusive here.
        float progress = (activeTicks - 1) / (float) (CONVERGE_BURST_TICKS - 1);
        float radius = RADIUS + (CONVERGE_END_RADIUS - RADIUS) * progress;
        AdvancedCylinderParticleManager.spawnParticles(this.level(), this.position(),
                CONVERGE_PARTICLES_PER_TICK, AMBER_DUST, ParticleDirection.INWARD,
                radius, CONVERGE_HEIGHT, 0.0, 0.0, 0.0, CONVERGE_SPEED, false);
    }

    private void spawnColumnTick() {
        AdvancedCylinderParticleManager.spawnParticles(this.level(), this.position(),
                COLUMN_END_ROD_PER_CALL, ParticleTypes.END_ROD, ParticleDirection.UPWARD,
                COLUMN_RADIUS, COLUMN_HEIGHT, 0.0, 0.0, 0.0, COLUMN_END_ROD_SPEED, false);
        AdvancedCylinderParticleManager.spawnParticles(this.level(), this.position(),
                COLUMN_DUST_PER_CALL, AMBER_DUST, ParticleDirection.UPWARD,
                COLUMN_RADIUS, COLUMN_HEIGHT, 0.0, 0.0, 0.0, COLUMN_DUST_SPEED, false);
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
