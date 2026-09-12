package net.offkung.bhspellsx.entity.spells.amethyst_decree;

import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.client.renderer.crystal.AmethystDecreeSounds;
import net.offkung.bhspellsx.client.renderer.crystal.CasterRingLayout;
import net.offkung.bhspellsx.client.renderer.crystal.CrystalTransform;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;

import java.util.List;
import java.util.Optional;

/**
 * Phase 2 VFX for the caster side of amethyst_decree — purely decorative, no damage/targeting of
 * its own (AoeEntity's own checkHits()/applyEffect() plumbing is unused, same as
 * EmbracingBosomAoe/AmethystDecreeAoe). Spawned once per cast from
 * AmethystDecreeSpell.onServerPreCast() (cast START, not completion), so its own age lines up
 * with the 1s cast: the crystal scatter/burst/sink schedule below is all driven by tickCount, not
 * a separate timer.
 * <p>
 * Everything about WHERE the crystals sit and WHEN each one rises is computed once by
 * CasterRingLayout, seeded from this entity's own network id (getId()) — stable across the
 * entity's lifetime, identical on server and every client, no synced data field needed and no
 * per-frame re-randomization. The renderer (AmethystDecreeCasterRingRenderer) is what actually
 * calls into CasterRingLayout and caches the result; this class only owns the lifetime.
 */
public class AmethystDecreeCasterRingEntity extends AoeEntity {
    // Must stay >= AmethystDecreeCasterRingRenderer's SINK_START + SINK_TICKS or the entity will
    // discard itself mid-sink. Tuning pass: hold ~3s after cast completion, then a slower sink.
    public static final int LIFETIME_TICKS = 110;

    // Client-side render cache only — computed once (lazily, on first access) from a seed fixed
    // at spawn (getId()), never recomputed per frame. A renderer instance is shared across every
    // entity of this type, so this cache has to live on the entity itself, not the renderer.
    private List<CrystalTransform> cachedScatter;
    private List<CrystalTransform> cachedBurstRing;
    private List<CrystalTransform> cachedBurstScatter;

    public AmethystDecreeCasterRingEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setRadius((float) AmethystDecreeConstants.RADIUS);
    }

    public AmethystDecreeCasterRingEntity(Level level) {
        this(BHXEntityRegistry.AMETHYST_DECREE_CASTER_RING.get(), level);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // Must match AmethystDecreeCasterRingRenderer's BURST_TICK/SINK_START/SINK_TICKS — kept as a
    // small duplicated constant here rather than a shared file, since the renderer intentionally
    // owns all animation timing (see its own class javadoc); this is only used to time particle
    // emission, not visual rise/sink math.
    private static final int BURST_TICK = 20;
    private static final int SINK_START = 80;
    private static final int AMBIENT_INTERVAL_TICKS = 12;

    // Sparse "occasional" chimes as the scatter crystals rise during the 1s cast — a few fixed
    // ticks spread across the pre-burst window, not a per-tick roll, per the "sparse, not a
    // constant stream" spec.
    private static final int[] RISE_CHIME_TICKS = {5, 11, 16};

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (this.tickCount >= LIFETIME_TICKS && !this.level().isClientSide()) {
            this.discard();
        }
        if (!this.level().isClientSide() && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (this.tickCount == BURST_TICK) {
                spawnBurstDebris(serverLevel);
                AmethystDecreeSounds.playEruption(serverLevel, this.getX(), this.getY(), this.getZ(), this.random, 4);
            } else if (this.tickCount > BURST_TICK && this.tickCount < SINK_START
                    && this.tickCount % AMBIENT_INTERVAL_TICKS == 0) {
                spawnAmbientShimmer(serverLevel);
            } else {
                for (int riseTick : RISE_CHIME_TICKS) {
                    if (this.tickCount == riseTick) {
                        AmethystDecreeSounds.playRiseChime(serverLevel, this.getX(), this.getY(), this.getZ(), this.random);
                        break;
                    }
                }
            }
        }
    }

    /** Burst-outward shard+mote spray along each rising ring/scatter spike, at cast completion. */
    private void spawnBurstDebris(net.minecraft.server.level.ServerLevel serverLevel) {
        for (CrystalTransform t : getOrComputeBurstRing()) {
            spawnBurstAt(serverLevel, t);
        }
        for (CrystalTransform t : getOrComputeBurstScatter()) {
            spawnBurstAt(serverLevel, t);
        }
    }

    private void spawnBurstAt(net.minecraft.server.level.ServerLevel serverLevel, CrystalTransform t) {
        double x = this.getX() + t.dx();
        double y = this.getY() + t.dy() + 0.3;
        double z = this.getZ() + t.dz();
        double dx = t.dx();
        double dz = t.dz();
        double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
        net.offkung.bhspellsx.client.renderer.crystal.CrystalDebris.spawnBurst(
                serverLevel, x, y, z, dx / len, 0.6, dz / len, 6, 3);
    }

    /** Sparse ambient shimmer over the scattered small crystals while the ring holds. */
    private void spawnAmbientShimmer(net.minecraft.server.level.ServerLevel serverLevel) {
        List<CrystalTransform> scatter = getOrComputeScatter();
        if (scatter.isEmpty()) {
            return;
        }
        // A few random points per pulse, not every crystal — "start sparse".
        for (int i = 0; i < 3; i++) {
            CrystalTransform t = scatter.get(this.random.nextInt(scatter.size()));
            double x = this.getX() + t.dx();
            double y = this.getY() + t.dy() + 0.4;
            double z = this.getZ() + t.dz();
            net.offkung.bhspellsx.client.renderer.crystal.CrystalDebris.spawnAmbient(serverLevel, x, y, z, 1, 2);
        }
    }

    /** Lazily computed once, cached for this entity's whole lifetime — see field javadoc. */
    public List<CrystalTransform> getOrComputeScatter() {
        if (this.cachedScatter == null) {
            double radius = AmethystDecreeConstants.RADIUS;
            this.cachedScatter = CasterRingLayout.generateScatter(this.getId(), radius, this.level(),
                    this.getX(), this.getY(), this.getZ());
        }
        return this.cachedScatter;
    }

    /** Lazily computed once, cached for this entity's whole lifetime — see field javadoc. */
    public List<CrystalTransform> getOrComputeBurstRing() {
        if (this.cachedBurstRing == null) {
            double radius = AmethystDecreeConstants.RADIUS;
            this.cachedBurstRing = CasterRingLayout.generateBurstRing(this.getId(), radius, this.level(),
                    this.getX(), this.getY(), this.getZ());
        }
        return this.cachedBurstRing;
    }

    /** Lazily computed once, cached for this entity's whole lifetime — see field javadoc. */
    public List<CrystalTransform> getOrComputeBurstScatter() {
        if (this.cachedBurstScatter == null) {
            double radius = AmethystDecreeConstants.RADIUS;
            this.cachedBurstScatter = CasterRingLayout.generateBurstScatter(this.getId(), radius, this.level(),
                    this.getX(), this.getY(), this.getZ());
        }
        return this.cachedBurstScatter;
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
