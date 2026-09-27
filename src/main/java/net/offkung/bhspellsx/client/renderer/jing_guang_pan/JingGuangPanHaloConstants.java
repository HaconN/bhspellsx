package net.offkung.bhspellsx.client.renderer.jing_guang_pan;

/** Visual-only tuning; distances in blocks and durations in ticks. */
public final class JingGuangPanHaloConstants {
    public static final double DIAMETER=1.65, CENTER_Y=1.75, BACK_GAP=.30, BODY_HALF_DEPTH=.125;
    public static final double RAY_RADIUS=1.23, RIM_TUBE_RADIUS=.018;
    public static final float RING_ALPHA=.95f, INNER_ALPHA=.88f, INNER_ADDITIVE=.28f;
    public static final float RAYS_ALPHA=.60f, GLOW_ALPHA=.24f, BACK_INNER_MIN=.10f;
    public static final double BACK_FADE_START_DOT=0, BACK_FADE_FULL_DOT=.85;
    public static final int OPEN_TICKS=8, CLOSE_TICKS=8, PULSE_TICKS=80;
    public static final double OPEN_SCALE=.85, ROTATION_DEGREES_PER_SECOND=3, PULSE_AMOUNT=.04;
    public static final int SPARK_COUNT=12, RING_SEGMENTS=160, TUBE_SEGMENTS=8;
    public static final double SPARK_SIZE=.045, SPARK_RADIUS_MIN=.87, SPARK_RADIUS_MAX=1.08;
    public static final double SPARK_DEPTH=.035, SPARK_SCALE_MIN=.7, SPARK_SCALE_MAX=1.4;
    public static final float SPARK_ALPHA=.9f;
    public static final long SPARK_SEED=427;
    // Client-local game particles. ID-only replacement supports SimpleParticleType options.
    public static final String AURA_PARTICLE_ID="minecraft:end_rod";
    public static final int AURA_INTERVAL_TICKS=4, AURA_PARTICLES_PER_BURST=1;
    public static final double AURA_RADIUS_MIN=.35, AURA_RADIUS_MAX=.70;
    public static final double AURA_START_Y_MIN=.10, AURA_START_Y_MAX=1.80;
    public static final double AURA_OUTWARD_SPEED=.005, AURA_UPWARD_SPEED=.012;
    public static final double RENDER_DISTANCE=64;
    public static final int SPAWN_GRACE_TICKS=40;
    public static final int GOLD=0xD8AE48, DEEP_GOLD=0x98702B, CREAM=0xFFF3CF, WHITE=0xFFFBEF;
    // Procedural texture generation settings; generator reads these values too.
    public static final double RING_CORE_WIDTH=.032, RING_SOFT_WIDTH=.105;
    public static final int RAY_COUNT=144;
    public static double smooth(double x) { x=Math.max(0,Math.min(1,x)); return x*x*(3-2*x); }
    private JingGuangPanHaloConstants() {}
}
