package net.offkung.bhspellsx.client.renderer;

/**
 * Phase 2A tuning values for CrystalHydroDomeRenderer, hardcoded (no config) — same reasoning
 * as CrystalHydroDomeConstants: a ForgeConfigSpec would let a previously-generated toml
 * silently override a balance/look change on next launch (see bhspellsx/CLAUDE.md's
 * "Forge config files override code defaults" note). These are purely visual and don't affect
 * server-side dome logic at all.
 */
public final class CrystalHydroDomeVisuals {
    private CrystalHydroDomeVisuals() {
    }

    // --- Mesh resolution (VISUAL shell only — gameplay stays the CrystalHydroDomeConstants
    //     hemisphere; see CrystalHydroDomeRenderer.buildMesh) ---

    /** Reference ring density: rings per 90 degrees of arc. Phase 1 (hemisphere-only) used this
     *  directly as the total row count; Phase 2A.3 extended the visual mesh past the equator
     *  (see LOWER_EXTENT), so the actual total row count is now derived from this density and
     *  LOWER_EXTENT (buildMesh's phiMax/ringDensityPerQuarter math), to keep ring spacing per arc
     *  length roughly constant regardless of how far down the shell extends. */
    public static final int LAT_RINGS = 12;
    /** Segments around each latitude circle. */
    public static final int SEGMENTS = 36;
    /** How far below the center (y=0) the VISUAL shell extends, as a fraction of RADIUS —
     *  0 would stop exactly at the equator (Phase 2A's original hemisphere silhouette), 1 would
     *  reach a full sphere's bottom pole. Purely cosmetic: does not touch isInside/RADIUS/HEIGHT
     *  or any server-side hit-detection shape at all. */
    public static final double LOWER_EXTENT = 0.7;

    // --- Color (light cyan, unlit — see the shader's fixed FULL_BRIGHT light below) ---

    public static final float COLOR_R = 0.55f;
    public static final float COLOR_G = 0.85f;
    public static final float COLOR_B = 1.0f;

    // --- Fresnel-like per-vertex alpha (see CrystalHydroDomeRenderer.fresnelAlpha) ---

    /** Alpha at face-on angles (looking straight through the surface) — very transparent. */
    public static final float BASE_ALPHA = 0.02f;
    /** Extra alpha added at grazing angles (the "brighter edge" that reads as a dome). */
    public static final float EDGE_ALPHA = 0.35f;
    /** Falloff exponent on the grazing-angle term — higher = edge glow stays narrower/crisper. */
    public static final float EDGE_POWER = 3.0f;

    /** Extra alpha added everywhere on the shell, scaled by how deep inside the dome the camera
     *  is (see CrystalHydroDomeRenderer.insideFactor) — the outside-facing fresnel look is
     *  approved and unchanged; this only lifts the near-invisible face-on case seen from near
     *  the dome's center, where every surface point faces the camera and the edge term alone
     *  sits at BASE_ALPHA. */
    public static final float INSIDE_ALPHA = 0.10f;
    /** Extra alpha for a fixed band centered on the ground line (local-space vertex height 0,
     *  measured both up AND down since Phase 2A.3 extended the shell below y=0 — see
     *  CrystalHydroDomeRenderer.rimFactor), independent of view angle or camera position —
     *  visible from both inside and outside. */
    public static final float RIM_ALPHA = 0.25f;
    /** Height (blocks) on EITHER side of the ground line over which RIM_ALPHA fades out to 0. */
    public static final float RIM_HEIGHT = 1.5f;

    // --- Fade-in ---

    /** Ticks (from entity spawn) over which the dome eases in from fully invisible to its
     *  normal fresnel alpha. */
    public static final int FADE_IN_TICKS = 8;

    // --- Distance cull (recon Q4 covers the separate frustum-culling fix; this is a plain
    //     distance-based skip, unrelated to that) ---

    /** Beyond this distance (blocks) from the dome center, render() submits no geometry. */
    public static final double MAX_RENDER_DISTANCE = 64.0;
    public static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // --- Lotus: broad, cupped petals. Pure visual constants, no server configuration. ---
    public static final float LOTUS_Y_OFFSET = 0.06f;
    public static final float LOTUS_ROOT_RADIUS = 0.65f;
    public static final int LOTUS_PETAL_SEGMENTS = 16;
    public static final int LOTUS_OUTER_COUNT = 10;
    public static final int LOTUS_INNER_COUNT = 8;
    public static final int LOTUS_HEART_COUNT = 6;
    public static final float LOTUS_OUTER_LENGTH = 6.6f;
    public static final float LOTUS_INNER_LENGTH = 4.2f;
    public static final float LOTUS_HEART_LENGTH = 2.3f;
    public static final float LOTUS_OUTER_HEIGHT = 1.0f;
    public static final float LOTUS_INNER_HEIGHT = 1.6f;
    public static final float LOTUS_HEART_HEIGHT = 1.85f;
    public static final float LOTUS_WIDTH_RATIO = 0.65f;
    public static final float LOTUS_CURVE_RATIO = 0.09f;
    public static final float LOTUS_CURVE_MAX = 0.45f;
    public static final float LOTUS_CUP_RATIO = 0.085f;
    public static final float LOTUS_CUP_MAX = 0.38f;
    public static final float LOTUS_SWEEP_RATIO = 0.07f;
    public static final int LOTUS_BLOOM_TICKS = 12;
    public static final float LOTUS_BLOOM_START_SCALE = 0.12f;
    public static final float LOTUS_BLOOM_OVERSHOOT_DEG = 18.0f;
    public static final float LOTUS_OUTER_YAW_SPEED = 0.22f;
    public static final float LOTUS_INNER_YAW_SPEED = -0.28f;
    public static final float LOTUS_HEART_YAW_SPEED = 0.36f;
    public static final float LOTUS_INNER_YAW_OFFSET = 18.0f;
    public static final float LOTUS_HEART_YAW_OFFSET = 36.0f;
    // Outer/middle petals (and drifting petals): pink, per owner request. Heart stays gold.
    public static final float LOTUS_BASE_R = 0.86f, LOTUS_BASE_G = 0.34f, LOTUS_BASE_B = 0.60f;
    public static final float LOTUS_TIP_R = 1.0f, LOTUS_TIP_G = 0.84f, LOTUS_TIP_B = 0.92f;
    public static final float HEART_BASE_R = 1.0f, HEART_BASE_G = 0.60f, HEART_BASE_B = 0.10f;
    public static final float HEART_TIP_R = 1.0f, HEART_TIP_G = 0.90f, HEART_TIP_B = 0.45f;
    public static final float LOTUS_EDGE_DARKEN = 0.18f;
    public static final float LOTUS_ALPHA = 0.64f;

    // Two soft tapered helical ribbons; open center, no solid disk or stamens.
    public static final int WIND_SEGMENTS = 48;
    public static final int WIND_STRANDS = 2;
    public static final float WIND_RADIUS = 0.85f;
    public static final float WIND_RADIUS_SWELL = 0.3f;
    public static final float WIND_HEIGHT = 1.8f;
    public static final float WIND_TURNS = 1.15f;
    public static final float WIND_HALF_WIDTH = 0.11f;
    public static final float WIND_ALPHA = 0.28f;
    public static final float WIND_YAW_SPEED = 2.2f;
    public static final float WIND_R = 1.0f, WIND_G = 0.78f, WIND_B = 0.24f;

    // Sparse drifting petals, drawn locally instead of spawning more entities.
    public static final int DRIFT_COUNT = 24;
    public static final int DRIFT_SEGMENTS = 6;
    public static final float DRIFT_LENGTH = 0.38f;
    /** Clearance includes the entire small petal, not only its orbit anchor. */
    public static final float DRIFT_SHELL_CLEARANCE = 0.65f;
    public static final float DRIFT_MIN_RADIUS_FRACTION = 0.20f;
    public static final float DRIFT_MAX_RADIUS_FRACTION = 0.95f;
    public static final float DRIFT_Y_MIN = 0.8f;
    public static final float DRIFT_Y_RANGE = 8.0f;
    public static final float DRIFT_FADE_FRACTION = 0.12f;
    public static final float DRIFT_YAW_SPEED = -0.65f;
    public static final int DRIFT_CYCLE_TICKS = 120;

    // Fine ground tracery and two staggered inward pulses, all well inside the dome.
    public static final int SIGIL_SEGMENTS = 128;
    public static final float SIGIL_RADIUS = 8.15f;
    public static final float SIGIL_Y = 0.035f;
    public static final float SIGIL_LINE_WIDTH = 0.035f;
    public static final float SIGIL_ALPHA = 0.32f;
    public static final int SIGIL_ARCS = 6;
    public static final int SIGIL_ARC_SEGMENTS = 24;
    public static final float SIGIL_ARC_RADIUS = 7.15f;
    public static final float SIGIL_ARC_SWELL = 0.55f;
    public static final float SIGIL_ARC_COVERAGE = 0.8f;
    public static final int PULSE_COUNT = 2;
    public static final int PULSE_PERIOD_TICKS = 48;
    public static final float PULSE_INNER_RADIUS = 1.1f;
    public static final float PULSE_ALPHA = 0.28f;

    // --- Water pattern on the shell (owner request): flowing wavy streaks + bright flecks ---
    public static final int WATER_STREAK_COUNT = 24;
    public static final int WATER_STREAK_SEGMENTS = 20;
    /** Each streak fades in and out over one cycle, then re-rolls its height/length/wave. */
    public static final float WATER_STREAK_CYCLE_TICKS = 70.0f;
    /** Height on the dome as a fraction of the radius (0 = ground line, 1 = top). */
    public static final float WATER_STREAK_MIN_HEIGHT = 0.05f;
    public static final float WATER_STREAK_MAX_HEIGHT = 0.92f;
    public static final float WATER_STREAK_MIN_SPAN_DEG = 35.0f;
    public static final float WATER_STREAK_MAX_SPAN_DEG = 110.0f;
    /** Flow speed around the dome, degrees per tick (all the same direction = a slow swirl). */
    public static final float WATER_STREAK_MIN_SPEED_DEG = 0.25f;
    public static final float WATER_STREAK_MAX_SPEED_DEG = 0.8f;
    /** Full ribbon width in blocks at its widest point. */
    public static final float WATER_STREAK_MIN_WIDTH = 0.12f;
    public static final float WATER_STREAK_MAX_WIDTH = 0.45f;
    /** Max latitude wobble in radians (~0.05 = half a block at radius 10). */
    public static final float WATER_STREAK_WAVE = 0.05f;
    public static final float WATER_STREAK_ALPHA_MIN = 0.10f;
    public static final float WATER_STREAK_ALPHA_MAX = 0.26f;
    public static final float WATER_STREAK_R = 0.70f, WATER_STREAK_G = 0.92f, WATER_STREAK_B = 1.0f;
    public static final float WATER_STREAK_SURFACE_OFFSET = 0.03f;
    public static final int WATER_FLECK_COUNT = 10;
    public static final float WATER_FLECK_CYCLE_TICKS = 24.0f;
    public static final float WATER_FLECK_WIDTH = 0.08f;
    public static final float WATER_FLECK_ALPHA = 0.55f;

    // --- Phase 2C: thin yellow lightning over the shell (client-only, deterministic hash) ---
    public static final int SHELL_BOLT_SLOTS = 4;
    /** Each slot restarts at a new spot every period; visible only for the first LIFE ticks. */
    public static final int SHELL_BOLT_PERIOD_TICKS = 9;
    public static final int SHELL_BOLT_LIFE_TICKS = 5;
    public static final int SHELL_BOLT_SEGMENTS = 12;
    public static final int SHELL_BOLT_FORK_SEGMENTS = 5;
    public static final float SHELL_BOLT_MIN_LENGTH = 3.0f;
    public static final float SHELL_BOLT_MAX_LENGTH = 6.5f;
    /** Perpendicular jag amplitude in blocks. */
    public static final float SHELL_BOLT_JAG = 0.35f;
    /** Drawn just outside the shell to avoid z-fighting with it. */
    public static final float SHELL_BOLT_SURFACE_OFFSET = 0.06f;
    /** Lowest start height above the dome base, keeps bolts off the ground line. */
    public static final float SHELL_BOLT_MIN_Y = 0.8f;
    public static final float SHELL_BOLT_CORE_WIDTH = 0.05f;
    public static final float SHELL_BOLT_GLOW_WIDTH = 0.18f;
    public static final float SHELL_BOLT_CORE_ALPHA = 0.85f;
    public static final float SHELL_BOLT_GLOW_ALPHA = 0.28f;

    // --- Phase 2C: counter bolt from the nearest shell point to the countered attacker ---
    public static final int COUNTER_BOLT_LIFE_TICKS = 6;
    /** Ticks at full brightness before the linear fade over the rest of the life. */
    public static final int COUNTER_BOLT_HOLD_TICKS = 2;
    /** Blocks per segment; segment count is clamped to [MIN, MAX]. */
    public static final float COUNTER_BOLT_SEGMENT_LENGTH = 0.8f;
    public static final int COUNTER_BOLT_MIN_SEGMENTS = 6;
    public static final int COUNTER_BOLT_MAX_SEGMENTS = 64;
    public static final float COUNTER_BOLT_JAG = 0.55f;
    /** Jag pattern re-rolls every N ticks so the bolt crackles instead of sitting still. */
    public static final int COUNTER_BOLT_RESHAPE_TICKS = 2;
    public static final float COUNTER_BOLT_CORE_WIDTH = 0.09f;
    public static final float COUNTER_BOLT_GLOW_WIDTH = 0.32f;
    public static final float COUNTER_BOLT_CORE_ALPHA = 1.0f;
    public static final float COUNTER_BOLT_GLOW_ALPHA = 0.35f;

    /** Max tilt (degrees) of a counter bolt's origin away from the shell point nearest the target. */
    public static final float COUNTER_BOLT_ORIGIN_SPREAD_DEG = 20.0f;

    // --- Phase 2D natural end (time ran out): expand + wave + flung petals. All within the
    //     server's END_LINGER_TICKS (30) window. ---
    /** Shell swells from RADIUS to RADIUS*this (10 -> 13 = the knockback ring) while fading. */
    public static final float END_SHELL_EXPAND = 1.3f;
    public static final int END_WAVE_TICKS = 18;
    public static final int END_WAVE_RINGS = 3;
    public static final int END_WAVE_STAGGER_TICKS = 3;
    public static final float END_WAVE_START_RADIUS = 9.5f;
    public static final float END_WAVE_END_RADIUS = 13.5f;
    public static final float END_WAVE_WIDTH_START = 0.9f;
    public static final float END_WAVE_WIDTH_END = 0.2f;
    public static final float END_WAVE_WALL_HEIGHT = 1.1f;
    public static final float END_WAVE_ALPHA = 0.55f;
    public static final float END_WAVE_R = 0.40f, END_WAVE_G = 0.78f, END_WAVE_B = 1.0f;
    public static final int END_LOTUS_TICKS = 26;
    /** Outer petal tips reach about this far from the center at full spread. */
    public static final float END_LOTUS_RADIUS = 10.0f;
    /** Fraction of each layer's resting pitch removed at full spread (flatter, wider flower). */
    public static final float END_LOTUS_FLATTEN = 0.6f;
    public static final int END_SIGIL_FADE_TICKS = 12;
    public static final int END_WIND_FADE_TICKS = 10;
    public static final int END_DRIFT_TICKS = 24;
    public static final float END_DRIFT_DISTANCE = 12.0f;
    public static final float END_DRIFT_LIFT = 1.5f;

    // --- Phase 2D broken end (HP 0 / cancelled / caster gone): crystal shatter + lotus folds and sinks ---
    /** Jittered triangle grid the shards are cut from: rows over [0, SHARD_MAX_POLAR_DEG], cols around. */
    public static final int SHARD_ROWS = 16;
    public static final int SHARD_COLS = 32;
    /** Polar angle from the top the shattering shell reaches (95 = just below the ground line). */
    public static final float SHARD_MAX_POLAR_DEG = 95.0f;
    /** Vertex jitter as a fraction of one grid cell; higher = more irregular crack lines. */
    public static final float SHARD_JITTER = 0.42f;
    /** Voronoi seed count = shard count (random sizes/outlines). */
    public static final int SHARD_CELLS = 56;
    /** Lowest seed height as a fraction of the radius (slightly below ground to cover the rim). */
    public static final float SHARD_SEED_MIN_Y = -0.08f;
    /** Every shard holds in place while its crack gap opens, then flies; hold is staggered per shard. */
    public static final float SHARD_HOLD_TICKS = 2.0f;
    public static final float SHARD_HOLD_JITTER_TICKS = 3.0f;
    public static final int SHARD_TICKS = 28;
    public static final float SHARD_SPEED_MIN = 0.18f;
    public static final float SHARD_SPEED_MAX = 0.42f;
    public static final float SHARD_GRAVITY = 0.035f;
    /** Max spin, radians per tick. */
    public static final float SHARD_SPIN_MAX = 0.35f;
    /** Shards shrink about their own centre so gaps open immediately (reads as "cracked"). */
    public static final float SHARD_START_SHRINK = 0.85f;
    public static final float SHARD_END_SHRINK = 0.45f;
    public static final float SHARD_ALPHA = 0.45f;
    /** Extra brightness right at the break, gone within a few ticks. */
    public static final float SHARD_FLASH_ALPHA = 0.4f;
    public static final float SHARD_R = 0.75f, SHARD_G = 0.95f, SHARD_B = 1.0f;
    public static final int END_CLOSE_TICKS = 24;
    public static final float END_CLOSE_PITCH_DEG = 55.0f;
    public static final float END_CLOSE_SCALE = 0.35f;
    public static final float END_SINK_DEPTH = 1.6f;
    public static final int END_BROKEN_FADE_TICKS = 10;

    public static final float BOLT_CORE_R = 1.0f, BOLT_CORE_G = 0.97f, BOLT_CORE_B = 0.70f;
    public static final float BOLT_GLOW_R = 1.0f, BOLT_GLOW_G = 0.82f, BOLT_GLOW_B = 0.22f;
}
