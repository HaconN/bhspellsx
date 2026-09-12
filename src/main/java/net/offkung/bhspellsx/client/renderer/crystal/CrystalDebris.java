package net.offkung.bhspellsx.client.renderer.crystal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.offkung.bhspellsx.client.particle.AmethystShardParticleOption;
import net.offkung.bhspellsx.registry.BHXParticleRegistry;
import org.joml.Vector3f;

/**
 * Spawns the shard+mote pair together — every emission point in this VFX uses both particle
 * types at the same spot/moment (shards for the angular crystal-debris read, vanilla WITCH motes
 * for the soft sparkle already used on hit targets), never one alone. Ratio is chosen per call
 * site: burst is shard-heavy (2:1), ambient shimmer is mote-heavy (1:2) and sparse.
 */
public final class CrystalDebris {
    private static final Vector3f SHARD_TINT = new Vector3f(0.68f, 0.45f, 0.95f);

    private CrystalDebris() {
    }

    /** Burst-outward: shards sprayed along an outward direction as spikes rise, plus a smaller
     *  number of motes at the same point. Ratio ~2:1 shards:motes per your guess, tune later. */
    public static void spawnBurst(ServerLevel level, double x, double y, double z,
                                   double dx, double dy, double dz, int shardCount, int moteCount) {
        AmethystShardParticleOption shard = new AmethystShardParticleOption(SHARD_TINT);
        level.sendParticles(shard, x, y, z, shardCount, dx, dy, dz, 1.0);
        level.sendParticles(ParticleTypes.WITCH, x, y, z, moteCount, 0.2, 0.1, 0.2, 0.01);
    }

    /** Ambient shimmer: sparse drifting shards+motes at a resting point, mote-heavy (~1:2) for a
     *  glittering rather than debris feel. Intentionally low counts — start sparse per spec. */
    public static void spawnAmbient(ServerLevel level, double x, double y, double z, int shardCount, int moteCount) {
        AmethystShardParticleOption shard = new AmethystShardParticleOption(SHARD_TINT);
        level.sendParticles(shard, x, y, z, shardCount, 0.05, 0.05, 0.05, 0.005);
        level.sendParticles(ParticleTypes.WITCH, x, y, z, moteCount, 0.1, 0.08, 0.1, 0.005);
    }
}
