package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

/**
 * Hardcoded numbers for Xian She Huan Ying (Wu Liang Ye, gold). Same "fixed for this spell"
 * approach as AmethystDecreeConstants — nothing here scales with spell level or spell power.
 */
public final class XianSheHuanYingConstants {
    private XianSheHuanYingConstants() {
    }

    /** Scoreboard tag Apoli puts on the caster while the toggle is on. Its absence is the ONLY
     *  stop signal the user-side entity listens to — see XianSheHuanYingUserEntity. */
    public static final String OWNER_TAG = "wly_snake";

    /** Raycast range used once, at cast time, to pick the locked target. */
    public static final float TARGET_RANGE = 64.0f;

    /** Slowness I is never applied open-ended: a short duration refreshed on an interval, so a
     *  target is freed within SLOW_DURATION_TICKS no matter how the entities die. */
    public static final int SLOW_DURATION_TICKS = 40;
    public static final int SLOW_REFRESH_INTERVAL_TICKS = 10;
    public static final int SLOW_AMPLIFIER = 0;

    /** Faint soul-smoke around the caster, kept as a light accompaniment to the billboard. */
    public static final int SOUL_INTERVAL_TICKS = 8;
    public static final int SOUL_PARTICLE_COUNT = 1;

    // ---- Billboard render tuning (client only reads these; retune here, nothing else) ----------

    /** Snake image, drawn behind the caster. Size in blocks (quad is width x height). */
    public static final float SNAKE_WIDTH = 2.0f;
    public static final float SNAKE_HEIGHT = 2.0f;
    /** How far behind the caster (opposite the direction the player faces) the snake floats. */
    public static final float SNAKE_BACK_DISTANCE = 1.4f;
    /** Height of the snake quad's CENTER above the caster's feet. */
    public static final float SNAKE_CENTER_HEIGHT = 1.6f;

    /** Eye image, floating above the target's head. Size in blocks. Superseded by
     *  EYE_PAIR_SIZE/EYE_PAIR_GAP below (round 6, two mirrored eyes) for the actual quad
     *  dimensions — kept only because the shared Style record still needs a width/height. */
    public static final float EYE_WIDTH = 1.0f;
    public static final float EYE_HEIGHT = 1.0f;
    /** Height of the eye PAIR's CENTER above the target's feet — fallback only, used when the
     *  target can't be found client-side. Normally the renderer uses the target's real
     *  bounding-box height (see EYE_HEIGHT_MARGIN) instead. */
    public static final float EYE_CENTER_HEIGHT = 2.6f;
    /** When the target IS found client-side: margin above its real bounding-box top, added to
     *  the eye pair's center height. */
    public static final float EYE_HEIGHT_MARGIN = 0.3f;

    // ---- Eye pair: two mirrored copies of snake_eye.png above the target's head (round 6) -------

    /** Width and height of a single eye quad. */
    public static final float EYE_PAIR_SIZE = 0.45f;
    /** Center-to-center distance between the two eyes, measured in the VIEWER's screen space (not
     *  world space) so the pair always reads as a fixed-width pair no matter the view angle. */
    public static final float EYE_PAIR_GAP = 0.55f;
    /** "Opening" ease: height goes 0 -> EYE_PAIR_SIZE (width stays fixed), starting this many
     *  ticks after the entity spawns (after the snake has had time to appear first) and taking
     *  EYE_OPEN_TICKS to finish. */
    public static final int EYE_OPEN_DELAY_TICKS = 8;
    public static final int EYE_OPEN_TICKS = 8;
    /** "Closing" ease on dismiss, mirroring EYE_OPEN_TICKS — see the renderer javadoc's note on
     *  why this is NOT currently wired up (needs a decision, not just more code). */
    public static final int EYE_CLOSE_TICKS = 8;

    // ---- Purple weakening aura around the locked target (round 6) ---------------------------------

    /** One particle every this many ticks (client tick, not frame) — deliberately sparse. */
    public static final int AURA_INTERVAL_TICKS = 2;
    /** Horizontal spawn radius around the target's own center. */
    public static final float AURA_RADIUS_MIN = 0.1f;
    public static final float AURA_RADIUS_MAX = 0.4f;
    /** Spawn height as a fraction of the target's own bounding-box height (1.0 = top of head,
     *  0.0 = feet) — "shoulder" is approximated as most of the way up. */
    public static final float AURA_SPAWN_HEIGHT_FRACTION = 0.8f;
    /** Initial downward speed, blocks/tick (vanilla dust particles keep ~90% of their velocity
     *  each tick, so this doesn't need to be large to visibly drift toward the ground). */
    public static final float AURA_FALL_SPEED = 0.02f;
    /** DustParticleOptions' own scale parameter (roughly its render size). */
    public static final float AURA_SCALE = 0.65f;
    /** Purple tint, 0-1 per channel (DustParticleOptions takes an RGB Vector3f). */
    public static final float AURA_COLOR_R = 0.55f;
    public static final float AURA_COLOR_G = 0.15f;
    public static final float AURA_COLOR_B = 0.85f;

    /** Gentle vertical bob (both images): amplitude in blocks, full period in ticks. */
    public static final float BOB_AMPLITUDE = 0.08f;
    public static final float BOB_PERIOD_TICKS = 50.0f;

    /** true = draw at full brightness regardless of world light; false = normal world light. */
    public static final boolean RENDER_FULL_BRIGHT = false;

    // ---- Mist: 6 cloud billboards drawn by the user-side renderer, centered between the caster
    // and the snake image (round 3; radius tightened in round 5 — see CLOUD_ORBIT_RADIUS_MIN/MAX).
    // Round 4's two-group (snake-centered + front-of-player) layout was reverted: it visibly
    // swung the whole formation whenever the caster turned, since both that center point and the
    // front group were tied to yaw. This single free-floating group isn't tied to yaw at all. ----

    public static final int CLOUD_COUNT = 6;
    /** Horizontal distance from the mist group's center. Tightened in round 5 (was 1.2-2.2) to
     *  hug the caster more closely; re-check in game whether CLOUD_ORBIT_RADIUS_MIN still clears
     *  the player model at every camera angle. */
    public static final float CLOUD_ORBIT_RADIUS_MIN = 0.9f;
    public static final float CLOUD_ORBIT_RADIUS_MAX = 1.6f;
    /** Each cloud's own fixed height, biased low (see CLOUD_HEIGHT_BIAS_EXPONENT) so the group
     *  piles near the feet rather than floating at chest height. */
    public static final float CLOUD_HEIGHT_MIN = 0.2f;
    public static final float CLOUD_HEIGHT_MAX = 2.0f;
    /** Exponent applied to the per-cloud height's random [0,1) roll before lerping between MIN and
     *  MAX; >1 pushes more clouds toward CLOUD_HEIGHT_MIN. */
    public static final float CLOUD_HEIGHT_BIAS_EXPONENT = 2.0f;
    /** The whole 6-cloud formation slowly revolves around the group center, together. */
    public static final float CLOUD_ORBIT_PERIOD_TICKS = 25.0f * 20.0f;
    /** Small per-cloud wander around its own fixed spot — not the group orbit above. */
    public static final float CLOUD_DRIFT_DISTANCE = 0.3f;
    public static final float CLOUD_DRIFT_PERIOD_MIN_TICKS = 6.0f * 20.0f;
    public static final float CLOUD_DRIFT_PERIOD_MAX_TICKS = 12.0f * 20.0f;
    /** Multiplies every cloud texture's own aspect size below — the one knob for overall mist
     *  size. */
    public static final float CLOUD_SCALE = 1.4f;
    /** Per-cloud random size variance around CLOUD_SCALE, as a +/- fraction. */
    public static final float CLOUD_SIZE_JITTER = 0.15f;

    // Each cloud texture's own width/height in blocks at CLOUD_SCALE = 1.0 — ratios as given by
    // the source art (cloud_a/b/c/d), so clouds don't all render at the snake's square aspect.
    public static final float CLOUD_A_WIDTH = 0.86f;
    public static final float CLOUD_A_HEIGHT = 0.82f;
    public static final float CLOUD_B_WIDTH = 0.91f;
    public static final float CLOUD_B_HEIGHT = 1.09f;
    public static final float CLOUD_C_WIDTH = 1.27f;
    public static final float CLOUD_C_HEIGHT = 0.51f;
    public static final float CLOUD_D_WIDTH = 0.67f;
    public static final float CLOUD_D_HEIGHT = 0.20f;

    // ---- Appearance: snake + mist grow in from nothing when the entities spawn (round 3) --------

    /** Ticks (client-side tickCount, never server lifecycle) for the snake/each cloud to ease
     *  from scale 0 to full size. */
    public static final int APPEAR_TICKS = 10;
    /** How far below its resting height the snake/a cloud starts, closing to 0 as it eases in. */
    public static final float APPEAR_RISE_DISTANCE = 0.3f;
    /** Extra delay before cloud i (0-based) starts its own ease-in, so the mist looks like it
     *  gathers rather than popping in all at once. */
    public static final int CLOUD_APPEAR_STAGGER_TICKS = 3;

    // ---- [Optional] texture filter switch (round 3) ----------------------------------------------

    /** false = nearest filtering (pixel-sharp, current look). true = linear filtering (blurred).
     *  Toggle to compare in-game; never additive/translucent — still opaque cutout either way. */
    public static final boolean TEXTURE_SMOOTH = false;
}
