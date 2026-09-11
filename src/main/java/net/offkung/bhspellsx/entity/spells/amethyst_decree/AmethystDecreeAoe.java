package net.offkung.bhspellsx.entity.spells.amethyst_decree;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.config.AmethystDecreeConfig;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;
import net.offkung.bhspellsx.registry.BHXSpellRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Modelled on bhspells' own FeetStompAoe (spell spawns the entity, entity does the per-tick
 * work) but radius-based like our own EmbracingBosomAoe (io.redspace.ironsspellbooks's
 * AoeEntity, not AbstractMagicProjectile — FeetStompAoe's forward-trapezoid sweep doesn't fit
 * "radius around the caster").
 * <p>
 * Tick 1 (the first active tick after spawn) does the whole immediate burst: gather every
 * valid target in radius, damage + root + stun + debuff each, and record their UUIDs for the
 * DoT. Every DOT_INTERVAL_TICKS thereafter, up to DOT_TICK_COUNT times, each still-alive
 * recorded target takes one more fixed tick of damage — this is why the entity keeps existing
 * and ticking after the burst instead of discarding immediately, unlike a one-shot spell.
 * <p>
 * Target resolution for the DoT is by UUID via ServerLevel#getEntity each tick (not a held
 * Entity reference) specifically so a target logging out, dying, or being unloaded just makes
 * that lookup return null for the rest of the DoT — no crash, no orphaned reference, the tick
 * loop simply skips that UUID from then on. The caster (getOwner()) is resolved fresh the same
 * way on every damage call, same as bhspells' own FeetStompAoe — tolerant of a null owner.
 * <p>
 * getDamageSource() (see AmethystDecreeSpell) overrides setIFrames(0), same fix
 * StoneCrumbleSpell uses, so the burst hit and the first DoT tick landing within the same
 * invulnerability window never gets silently swallowed.
 */
public class AmethystDecreeAoe extends AoeEntity {
    // Public: BHSpellsX's common-setup check logs an ERROR against these same ids if either
    // mod effect can't be resolved, rather than duplicating the ResourceLocation elsewhere.
    public static final ResourceLocation EFN_STOP_ID = ResourceLocation.fromNamespaceAndPath("efn", "stop");
    public static final ResourceLocation CATACLYSM_STUN_ID = ResourceLocation.fromNamespaceAndPath("cataclysm", "stun");

    // Vanilla purple particle (used for witches / potion-style hits) — Phase 1 placeholder only.
    private static final int RING_VFX_POINTS = 24;

    private final List<UUID> dotTargets = new ArrayList<>();

    public AmethystDecreeAoe(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setRadius((float) AmethystDecreeConfig.RADIUS.get().doubleValue());
    }

    public AmethystDecreeAoe(Level level) {
        this(BHXEntityRegistry.AMETHYST_DECREE_AOE.get(), level);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (this.tickCount <= this.getDelay()) {
            return;
        }
        int activeTicks = this.tickCount - this.getDelay();
        if (activeTicks == 1) {
            applyBurst();
        }
        tickDot(activeTicks);

        int totalDotTicks = AmethystDecreeConfig.DOT_INTERVAL_TICKS.get() * AmethystDecreeConfig.DOT_TICK_COUNT.get();
        if (activeTicks >= totalDotTicks) {
            this.discard();
        }
    }

    /** Unused — see class javadoc. Still required: abstract in AoeEntity. */
    @Override
    public void applyEffect(LivingEntity target) {
    }

    /** No idle particle spawner of its own — see class javadoc VFX methods. Required: abstract in AoeEntity. */
    @Override
    public float getParticleCount() {
        return 0.0f;
    }

    /** No idle particle spawner of its own — see class javadoc VFX methods. Required: abstract in AoeEntity. */
    @Override
    public Optional<ParticleOptions> getParticle() {
        return Optional.empty();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }
        Entity owner = this.getOwner();
        if (target == owner) {
            return false;
        }
        return owner == null || !DamageSources.isFriendlyFireBetween(owner, living);
    }

    @Override
    protected boolean canHitTargetForGroundContext(LivingEntity target) {
        // No ground-contact requirement in the spec — canHitEntity() above is the only filter.
        return true;
    }

    private void applyBurst() {
        Entity owner = this.getOwner();
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox(), this::canHitEntity);
        for (LivingEntity target : targets) {
            DamageSources.applyDamage(target, AmethystDecreeConfig.BURST_DAMAGE.get().floatValue(), getDamageSource(owner));
            applyRootAndStun(target);
            applyDebuffs(target);
            spawnHitVfx(target);
            dotTargets.add(target.getUUID());
        }
        if (!targets.isEmpty()) {
            spawnCastRingVfx();
        }
    }

    private void tickDot(int activeTicks) {
        if (dotTargets.isEmpty()) {
            return;
        }
        int interval = AmethystDecreeConfig.DOT_INTERVAL_TICKS.get();
        if (activeTicks % interval != 0) {
            return;
        }
        int tickIndex = activeTicks / interval;
        if (tickIndex > AmethystDecreeConfig.DOT_TICK_COUNT.get()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity owner = this.getOwner();
        for (UUID targetId : dotTargets) {
            Entity resolved = serverLevel.getEntity(targetId);
            if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            DamageSources.applyDamage(target, AmethystDecreeConfig.DOT_DAMAGE_PER_TICK.get().floatValue(), getDamageSource(owner));
            spawnHitVfx(target);
        }
    }

    private void applyRootAndStun(LivingEntity target) {
        MobEffect stop = ForgeRegistries.MOB_EFFECTS.getValue(EFN_STOP_ID);
        if (stop != null) {
            target.addEffect(new MobEffectInstance(stop, AmethystDecreeConfig.ROOT_DURATION_TICKS.get(), 0, false, true, true));
        }
        MobEffect stun = ForgeRegistries.MOB_EFFECTS.getValue(CATACLYSM_STUN_ID);
        if (stun != null) {
            target.addEffect(new MobEffectInstance(stun, AmethystDecreeConfig.STUN_DURATION_TICKS.get(), 0, false, true, true));
        }
    }

    private void applyDebuffs(LivingEntity target) {
        int duration = AmethystDecreeConfig.DEBUFF_DURATION_TICKS.get();
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 1, false, true, true));
    }

    private net.minecraft.world.damagesource.DamageSource getDamageSource(Entity owner) {
        return ((AbstractSpell) BHXSpellRegistry.AMETHYST_DECREE.get()).getDamageSource(this, owner);
    }

    // --- Phase 1 placeholder VFX — vanilla particles only, swap wholesale in Phase 2. ---

    /** Vanilla purple particles at the target's feet on hit. */
    private void spawnHitVfx(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 0.1, target.getZ(), 12, 0.3, 0.05, 0.3, 0.01);
    }

    /** A simple ring of vanilla purple particles at the cast radius, once, on burst. */
    private void spawnCastRingVfx() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double radius = AmethystDecreeConfig.RADIUS.get();
        for (int i = 0; i < RING_VFX_POINTS; i++) {
            double angle = (Math.PI * 2 * i) / RING_VFX_POINTS;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.WITCH, x, this.getY() + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
