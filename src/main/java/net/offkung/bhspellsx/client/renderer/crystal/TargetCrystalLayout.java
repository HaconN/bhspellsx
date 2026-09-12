package net.offkung.bhspellsx.client.renderer.crystal;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic crystal placement for the target-side VFX (foot encasement, then post-shatter leg
 * crystals). Pure layout math only — see CasterRingLayout's javadoc for why this is a separate
 * class from the entity/renderer.
 * <p>
 * No ground-conform here: these are positioned relative to the target's own feet (small radius
 * around the entity's own origin), not scattered across open terrain, so there's no meaningful
 * separate "ground height" to look up.
 */
public final class TargetCrystalLayout {
    private static final int ENCASEMENT_COUNT = 6;
    private static final float ENCASEMENT_MIN_SCALE = 0.7f;
    private static final float ENCASEMENT_MAX_SCALE = 1.1f;
    private static final double ENCASEMENT_MIN_DIST = 0.35;
    private static final double ENCASEMENT_MAX_DIST = 0.5;
    private static final float ENCASEMENT_TILT_MIN_DEG = 10.0f;
    private static final float ENCASEMENT_TILT_MAX_DEG = 25.0f;

    // Tuning pass 2: "evenly scattered small shards" read as debris stuck on, not a leg still
    // being consumed by crystal. Redesigned as one larger cluster gripping ONE side of one calf
    // (the side is picked deterministically from the seed, so it varies per target but is stable
    // for that target's whole lifetime) plus a few loose shards around it — fewer pieces, a more
    // readable silhouette.
    private static final int CLUSTER_SIZE = 3;
    private static final float CLUSTER_MIN_SCALE = 0.6f;
    private static final float CLUSTER_MAX_SCALE = 0.9f;
    private static final double CLUSTER_HEIGHT = 0.35; // calf height
    private static final double CLUSTER_HEIGHT_JITTER = 0.08;
    private static final double CLUSTER_DIST = 0.22;
    private static final double CLUSTER_DIST_JITTER = 0.06;
    private static final float CLUSTER_ANGLE_SPREAD_DEG = 35.0f; // how tightly the cluster huddles onto one side
    private static final float CLUSTER_TILT_MIN_DEG = 15.0f;
    private static final float CLUSTER_TILT_MAX_DEG = 30.0f;

    private static final int SHARD_COUNT = 3;
    private static final float SHARD_MIN_SCALE = 0.35f;
    private static final float SHARD_MAX_SCALE = 0.55f;
    private static final double SHARD_MIN_DIST = 0.12;
    private static final double SHARD_MAX_DIST = 0.3;
    private static final double SHARD_MIN_HEIGHT = 0.0;
    private static final double SHARD_MAX_HEIGHT = 0.45;

    // Tuning pass 3, group C ("target strikes"): 1-2 large spikes at the moment the burst lands,
    // same size/appearance as the caster ring's own burst spikes. Lives on this entity (spawned
    // exactly when the burst lands, at the target's own position) instead of needing any
    // cross-entity handoff from the caster ring. Independent of the encasement/leg-crystal
    // phases — see AmethystDecreeTargetCrystalRenderer for its own separate rise/hold/sink
    // schedule, which is not tied to root duration.
    private static final float STRIKE_MIN_SCALE = 1.5f;
    private static final float STRIKE_MAX_SCALE = 2.0f;
    private static final double STRIKE_DIST = 0.25;
    private static final double STRIKE_DIST_JITTER = 0.1;
    private static final float STRIKE_TILT_DEG = 25.0f;
    private static final float STRIKE_TILT_JITTER_DEG = 10.0f;

    private TargetCrystalLayout() {
    }

    /** Large formation encasing the target's feet, spikes tilted outward like the burst ring. */
    public static List<CrystalTransform> generateEncasement(long seed) {
        RandomSource random = RandomSource.create(seed);
        List<CrystalTransform> list = new ArrayList<>(ENCASEMENT_COUNT);
        for (int i = 0; i < ENCASEMENT_COUNT; i++) {
            double angle = (Math.PI * 2.0 / ENCASEMENT_COUNT) * i + random.nextDouble() * 0.4;
            double dist = ENCASEMENT_MIN_DIST + random.nextDouble() * (ENCASEMENT_MAX_DIST - ENCASEMENT_MIN_DIST);
            double x = Math.cos(angle) * dist;
            double z = Math.sin(angle) * dist;
            float scale = ENCASEMENT_MIN_SCALE + random.nextFloat() * (ENCASEMENT_MAX_SCALE - ENCASEMENT_MIN_SCALE);
            float yaw = (float) Math.toDegrees(angle) + 90.0f;
            float tilt = ENCASEMENT_TILT_MIN_DEG + random.nextFloat() * (ENCASEMENT_TILT_MAX_DEG - ENCASEMENT_TILT_MIN_DEG);
            list.add(new CrystalTransform(x, 0.0, z, yaw, tilt, scale, 0, true));
        }
        return list;
    }

    /** One cluster of larger crystals gripping one side of one calf, plus a few small loose
     *  shards around it — see field javadoc above for why this replaced an even scatter. */
    public static List<CrystalTransform> generateLegCrystals(long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x2545F4914F6CDD1DL);
        List<CrystalTransform> list = new ArrayList<>(CLUSTER_SIZE + SHARD_COUNT);

        // Which side of the calf the cluster grips — deterministic per target, varies per seed.
        double baseAngle = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < CLUSTER_SIZE; i++) {
            double angle = baseAngle + Math.toRadians((random.nextDouble() - 0.5) * CLUSTER_ANGLE_SPREAD_DEG);
            double dist = CLUSTER_DIST + (random.nextDouble() - 0.5) * CLUSTER_DIST_JITTER;
            double height = CLUSTER_HEIGHT + (random.nextDouble() - 0.5) * CLUSTER_HEIGHT_JITTER;
            double x = Math.cos(angle) * dist;
            double z = Math.sin(angle) * dist;
            float scale = CLUSTER_MIN_SCALE + random.nextFloat() * (CLUSTER_MAX_SCALE - CLUSTER_MIN_SCALE);
            float yaw = (float) Math.toDegrees(angle) + 90.0f;
            float tilt = CLUSTER_TILT_MIN_DEG + random.nextFloat() * (CLUSTER_TILT_MAX_DEG - CLUSTER_TILT_MIN_DEG);
            list.add(new CrystalTransform(x, height, z, yaw, tilt, scale, 0, true));
        }
        for (int i = 0; i < SHARD_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = SHARD_MIN_DIST + random.nextDouble() * (SHARD_MAX_DIST - SHARD_MIN_DIST);
            double height = SHARD_MIN_HEIGHT + random.nextDouble() * (SHARD_MAX_HEIGHT - SHARD_MIN_HEIGHT);
            double x = Math.cos(angle) * dist;
            double z = Math.sin(angle) * dist;
            float scale = SHARD_MIN_SCALE + random.nextFloat() * (SHARD_MAX_SCALE - SHARD_MIN_SCALE);
            float yaw = random.nextFloat() * 360.0f;
            list.add(new CrystalTransform(x, height, z, yaw, 0.0f, scale, 0, false));
        }
        return list;
    }

    /** Group C — 1-2 large outward-tilted spikes at the target's own position, matching group
     *  A/B's size/appearance on the caster ring. Count and placement deterministic per seed. */
    public static List<CrystalTransform> generateStrikeSpikes(long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x9E3779B97F4A7C15L);
        int count = 1 + random.nextInt(2); // 1 or 2
        List<CrystalTransform> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = STRIKE_DIST + (random.nextDouble() - 0.5) * STRIKE_DIST_JITTER;
            double x = Math.cos(angle) * dist;
            double z = Math.sin(angle) * dist;
            float scale = STRIKE_MIN_SCALE + random.nextFloat() * (STRIKE_MAX_SCALE - STRIKE_MIN_SCALE);
            float yaw = (float) Math.toDegrees(angle) + 90.0f;
            float tilt = STRIKE_TILT_DEG + (random.nextFloat() - 0.5f) * STRIKE_TILT_JITTER_DEG;
            list.add(new CrystalTransform(x, 0.0, z, yaw, tilt, scale, 0, true));
        }
        return list;
    }
}
