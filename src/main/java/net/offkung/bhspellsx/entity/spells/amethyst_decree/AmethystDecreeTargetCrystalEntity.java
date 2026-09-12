package net.offkung.bhspellsx.entity.spells.amethyst_decree;

import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.client.renderer.crystal.AmethystDecreeSounds;
import net.offkung.bhspellsx.client.renderer.crystal.CrystalTransform;
import net.offkung.bhspellsx.client.renderer.crystal.TargetCrystalLayout;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 VFX for the target side of amethyst_decree — one per affected target, spawned from
 * AmethystDecreeAoe.applyBurst() alongside the existing (unchanged) Phase 1 effects. Purely
 * decorative: no damage, no targeting of its own.
 * <p>
 * Root duration and total lifetime are read from AmethystDecreeConstants ONCE at construction and
 * baked into final fields — same "fixed for this spell" reasoning EmbracingBosomAoe already uses
 * for its own lifetime constant.
 * <p>
 * MUST follow the target: the target is only rooted for the first rootDurationTicks, then walks
 * freely for the rest of this entity's life. Every server tick this resolves the target by UUID
 * (never a held Entity reference) via ServerLevel#getEntity and calls setPos() to match — same
 * null/dead-tolerant resolution pattern AmethystDecreeAoe's own DoT loop uses. A missing or dead
 * target discards this entity immediately rather than waiting out the rest of its lifetime; no
 * crash, no orphaned reference.
 * <p>
 * Visual phase (encased legs vs. shattered leg-crystals) is derived purely from tickCount against
 * the baked rootDurationTicks/totalLifetimeTicks, the same tickCount-driven approach
 * AmethystDecreeCasterRingEntity and irons_spellbooks' own IceSpikeEntity use — no synced phase
 * field needed.
 */
public class AmethystDecreeTargetCrystalEntity extends AoeEntity {
    private UUID targetId;
    private final int rootDurationTicks;
    private final int totalLifetimeTicks;
    private boolean shatterBurstSpawned;

    // Client-side render cache only — see AmethystDecreeCasterRingEntity's field javadoc for why
    // this lives on the entity rather than the (shared) renderer.
    private List<CrystalTransform> cachedEncasement;
    private List<CrystalTransform> cachedLegCrystals;
    private List<CrystalTransform> cachedStrikeSpikes;

    public AmethystDecreeTargetCrystalEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.rootDurationTicks = AmethystDecreeConstants.ROOT_DURATION_TICKS;
        this.totalLifetimeTicks = this.rootDurationTicks
                + AmethystDecreeConstants.DOT_INTERVAL_TICKS * AmethystDecreeConstants.DOT_TICK_COUNT;
    }

    public AmethystDecreeTargetCrystalEntity(Level level, LivingEntity target) {
        this(BHXEntityRegistry.AMETHYST_DECREE_TARGET_CRYSTAL.get(), level);
        this.targetId = target.getUUID();
        this.setPos(target.getX(), target.getY(), target.getZ());
    }

    public int getRootDurationTicks() {
        return this.rootDurationTicks;
    }

    public int getTotalLifetimeTicks() {
        return this.totalLifetimeTicks;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || this.targetId == null) {
            this.discard();
            return;
        }
        Entity resolved = serverLevel.getEntity(this.targetId);
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            this.discard();
            return;
        }
        this.setPos(target.getX(), target.getY(), target.getZ());

        if (!this.shatterBurstSpawned && this.tickCount >= this.rootDurationTicks) {
            this.shatterBurstSpawned = true;
            spawnShatterBurst(serverLevel, target);
            AmethystDecreeSounds.playShatter(serverLevel, target.getX(), target.getY(), target.getZ(), this.random);
        }
        if (this.tickCount == 0) {
            spawnStrikeDebris(serverLevel);
        } else if (this.tickCount % AMBIENT_INTERVAL_TICKS == 0) {
            spawnAmbientShimmer(serverLevel);
        }
        if (this.tickCount >= this.totalLifetimeTicks) {
            this.discard();
        }
    }

    private static final int AMBIENT_INTERVAL_TICKS = 15;

    /** Burst-outward shard+mote spray along the target-strike spikes, the instant they rise. */
    private void spawnStrikeDebris(ServerLevel serverLevel) {
        for (CrystalTransform t : getOrComputeStrikeSpikes()) {
            double x = this.getX() + t.dx();
            double y = this.getY() + t.dy() + 0.3;
            double z = this.getZ() + t.dz();
            double len = Math.max(0.001, Math.sqrt(t.dx() * t.dx() + t.dz() * t.dz()));
            net.offkung.bhspellsx.client.renderer.crystal.CrystalDebris.spawnBurst(
                    serverLevel, x, y, z, t.dx() / len, 0.6, t.dz() / len, 6, 3);
        }
    }

    /** Sparse ambient shimmer over whichever visible group (encasement or leg crystals) is
     *  currently showing. */
    private void spawnAmbientShimmer(ServerLevel serverLevel) {
        List<CrystalTransform> group = this.tickCount < this.rootDurationTicks
                ? getOrComputeEncasement() : getOrComputeLegCrystals();
        if (group.isEmpty()) {
            return;
        }
        CrystalTransform t = group.get(this.random.nextInt(group.size()));
        double x = this.getX() + t.dx();
        double y = this.getY() + t.dy() + 0.3;
        double z = this.getZ() + t.dz();
        net.offkung.bhspellsx.client.renderer.crystal.CrystalDebris.spawnAmbient(serverLevel, x, y, z, 1, 2);

        // DoT tail (post-shatter, leg-crystal phase) only — "very sparse, err toward less": one
        // in three ambient pulses actually gets a faint chime instead of every one.
        if (this.tickCount >= this.rootDurationTicks && this.random.nextInt(3) == 0) {
            AmethystDecreeSounds.playDotChime(serverLevel, x, y, z, this.random);
        }
    }

    /** Same vanilla purple particle already used for hit motes (see AmethystDecreeAoe) — reused
     *  here so the encasement visibly breaks apart instead of just vanishing. */
    private static void spawnShatterBurst(ServerLevel serverLevel, LivingEntity target) {
        serverLevel.sendParticles(ParticleTypes.WITCH,
                target.getX(), target.getY() + 0.2, target.getZ(),
                20, 0.35, 0.15, 0.35, 0.02);
    }

    /** Lazily computed once, cached for this entity's whole lifetime — see field javadoc. */
    public List<CrystalTransform> getOrComputeEncasement() {
        if (this.cachedEncasement == null) {
            this.cachedEncasement = TargetCrystalLayout.generateEncasement(this.getId());
        }
        return this.cachedEncasement;
    }

    /** Lazily computed once, cached for this entity's whole lifetime — see field javadoc. */
    public List<CrystalTransform> getOrComputeLegCrystals() {
        if (this.cachedLegCrystals == null) {
            this.cachedLegCrystals = TargetCrystalLayout.generateLegCrystals(this.getId());
        }
        return this.cachedLegCrystals;
    }

    /** Lazily computed once, cached for this entity's whole lifetime — see field javadoc. */
    public List<CrystalTransform> getOrComputeStrikeSpikes() {
        if (this.cachedStrikeSpikes == null) {
            this.cachedStrikeSpikes = TargetCrystalLayout.generateStrikeSpikes(this.getId());
        }
        return this.cachedStrikeSpikes;
    }

    /** Unused — see class javadoc. Still required: abstract in AoeEntity. */
    @Override
    public void applyEffect(LivingEntity target) {
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return false;
    }

    @Override
    protected boolean canHitTargetForGroundContext(LivingEntity target) {
        return false;
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
