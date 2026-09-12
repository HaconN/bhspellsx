package net.offkung.bhspellsx.client.renderer.crystal;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic crystal placement for the caster-side ring VFX. Pure layout math only — no
 * entity/tick/sync code here, so tuning counts/angles/sizes never touches
 * AmethystDecreeCasterRingEntity or its renderer's animation logic.
 * <p>
 * Called ONCE per entity (cached by the caller) with a seed fixed at spawn (the entity's own id),
 * so results are stable for that entity's whole lifetime instead of re-randomizing every frame.
 */
public final class CasterRingLayout {
    // Tuning pass 3: reverted the centre-bias from pass 2 — it read worse in practice than the
    // original uniform-per-area distribution. Back to sqrt(u)*radius, count back to 36.
    // Radius 6.75 -> 10: area scales ~2.19x (10/6.75)^2. Scatter fills area, so scaled by area:
    // 36 -> 80.
    // Second density pass: the 80/12/9 numbers were tuned and approved while radius was actually
    // stuck at 6.75 (deployed config toml override, see bhspellsx\CLAUDE.md), not the intended
    // 10 — so that's the density to preserve at the now-genuine radius 10, same ~2.19x area
    // factor applied again: 80 -> 176.
    private static final int SCATTER_COUNT = 176;
    private static final float SCATTER_MIN_SCALE = 0.5f;
    private static final float SCATTER_MAX_SCALE = 0.9f;
    private static final int SCATTER_RISE_STAGGER_TICKS = 14;

    // Tuning pass 3: fully-random burst placement read as messy/unintentional and could come out
    // lopsided. Replaced with structure — a legible ring (backbone of the look) plus a smaller
    // random-scatter group filling in between, instead of one random group. Total reduced
    // 16 -> 12 (8 ring + 4 scatter); target-strike spikes (group C) are separate/additional, see
    // AmethystDecreeAoe/AmethystDecreeCasterRingEntity.
    // large_core is 14 model units tall (0.875 blocks) at scale 1.0 — 1.5-2.0 puts a burst spike
    // at roughly 1.3-1.75 blocks tall, averaging ~1.5 blocks (confirmed good size, unchanged).
    private static final float BURST_MIN_SCALE = 1.5f;
    private static final float BURST_MAX_SCALE = 2.0f;
    private static final float BURST_TILT_DEG = 25.0f;
    private static final float BURST_TILT_JITTER_DEG = 10.0f;

    // Ring spacing is circumference-driven (linear in r), not area — scaled by the ~1.48x
    // linear factor (10/6.75) instead of the area factor, to keep gap distance similar: 8 -> 12.
    // Linear (circumference) factor ~1.48x again: 12 -> 18.
    private static final int BURST_RING_COUNT = 18;
    private static final float BURST_RING_RADIUS_FRACTION = 0.75f;
    private static final float BURST_RING_ANGLE_JITTER_RAD = 0.18f;
    private static final float BURST_RING_RADIUS_JITTER_FRACTION = 0.12f;

    // Fills area between ring spikes, scaled by area factor: 4 -> 9.
    // Area factor ~2.19x again: 9 -> 20.
    private static final int BURST_SCATTER_COUNT = 20;

    private CasterRingLayout() {
    }

    /** Small crystals across the whole disc, biased dense near the centre and thinning toward the
     *  edge (see SCATTER_RADIUS_BIAS_POWER), ground-conformed via a cheap heightmap lookup (one
     *  column-height query per crystal, done once here — not per frame, not a raycast). */
    public static List<CrystalTransform> generateScatter(long seed, double radius, Level level,
                                                           double originX, double originY, double originZ) {
        RandomSource random = RandomSource.create(seed);
        List<CrystalTransform> list = new ArrayList<>(SCATTER_COUNT);
        for (int i = 0; i < SCATTER_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(random.nextDouble()) * radius;
            double x = originX + Math.cos(angle) * dist;
            double z = originZ + Math.sin(angle) * dist;
            double groundY = groundHeight(level, x, z);
            float scale = SCATTER_MIN_SCALE + random.nextFloat() * (SCATTER_MAX_SCALE - SCATTER_MIN_SCALE);
            float yaw = random.nextFloat() * 360.0f;
            int riseDelay = random.nextInt(SCATTER_RISE_STAGGER_TICKS);
            list.add(new CrystalTransform(x - originX, groundY - originY, z - originZ, yaw, 0.0f, scale, riseDelay, false));
        }
        return list;
    }

    /** Group A — the backbone: spikes at even angular intervals at a consistent radius, reading
     *  as one wave pushing outward. Slight jitter on angle/radius keeps it from looking
     *  mechanically perfect while staying legible as a ring. */
    public static List<CrystalTransform> generateBurstRing(long seed, double radius, Level level,
                                                             double originX, double originY, double originZ) {
        RandomSource random = RandomSource.create(seed ^ 0x5DEECE66DL);
        List<CrystalTransform> list = new ArrayList<>(BURST_RING_COUNT);
        for (int i = 0; i < BURST_RING_COUNT; i++) {
            double baseAngle = (Math.PI * 2.0 / BURST_RING_COUNT) * i;
            double angle = baseAngle + (random.nextDouble() - 0.5) * BURST_RING_ANGLE_JITTER_RAD;
            double dist = radius * BURST_RING_RADIUS_FRACTION
                    * (1.0 + (random.nextDouble() - 0.5) * BURST_RING_RADIUS_JITTER_FRACTION);
            list.add(burstSpike(random, level, originX, originY, originZ, angle, dist));
        }
        return list;
    }

    /** Group B — a smaller random-scatter set filling in between the ring spikes. */
    public static List<CrystalTransform> generateBurstScatter(long seed, double radius, Level level,
                                                                double originX, double originY, double originZ) {
        RandomSource random = RandomSource.create(seed ^ 0x27220A5D1L);
        List<CrystalTransform> list = new ArrayList<>(BURST_SCATTER_COUNT);
        for (int i = 0; i < BURST_SCATTER_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(random.nextDouble()) * radius;
            list.add(burstSpike(random, level, originX, originY, originZ, angle, dist));
        }
        return list;
    }

    private static CrystalTransform burstSpike(RandomSource random, Level level,
                                                double originX, double originY, double originZ,
                                                double angle, double dist) {
        double x = originX + Math.cos(angle) * dist;
        double z = originZ + Math.sin(angle) * dist;
        double groundY = groundHeight(level, x, z);
        float scale = BURST_MIN_SCALE + random.nextFloat() * (BURST_MAX_SCALE - BURST_MIN_SCALE);
        float yaw = (float) Math.toDegrees(angle) + 90.0f;
        float tilt = BURST_TILT_DEG + (random.nextFloat() - 0.5f) * BURST_TILT_JITTER_DEG;
        return new CrystalTransform(x - originX, groundY - originY, z - originZ, yaw, tilt, scale, 0, true);
    }

    private static double groundHeight(Level level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }
}
