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

    /** Ticks both entities spend in their "dismissing" state (see XianSheHuanYingUserEntity's
     *  class javadoc) between an end condition becoming true and the actual discard — long enough
     *  for the client to play the reverse-appear/eye-close animation. */
    public static final int DISMISS_TICKS = 10;

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
     *  bounding-box height (see EYE_HEIGHT_MARGIN) instead. Bumped +0.15 in round 7 alongside
     *  EYE_HEIGHT_MARGIN when EYE_PAIR_SIZE grew, so the bigger pair doesn't sink into the head. */
    public static final float EYE_CENTER_HEIGHT = 2.75f;
    /** When the target IS found client-side: margin above its real bounding-box top, added to
     *  the eye pair's center height. Bumped 0.3 -> 0.45 in round 7: the eye pair is centered on
     *  this height, so growing EYE_PAIR_SIZE by 0.3 (round 7) grows its half-height by 0.15,
     *  which would otherwise push the bottom edge down into the target's head by that much. */
    public static final float EYE_HEIGHT_MARGIN = 0.45f;

    // ---- Eye pair: two mirrored copies of snake_eye.png above the target's head (round 6) -------

    /** Width and height of a single eye quad. */
    public static final float EYE_PAIR_SIZE = 0.75f;
    /** Center-to-center distance between the two eyes, measured in the VIEWER's screen space (not
     *  world space) so the pair always reads as a fixed-width pair no matter the view angle. */
    public static final float EYE_PAIR_GAP = 0.9f;
    /** "Opening" ease: height goes 0 -> EYE_PAIR_SIZE (width stays fixed), starting this many
     *  ticks after the entity spawns (after the snake has had time to appear first) and taking
     *  EYE_OPEN_TICKS to finish. Uses ease-out-back (see EYE_OPEN_OVERSHOOT) — closing does not,
     *  it stays a plain ease-in collapse. */
    public static final int EYE_OPEN_DELAY_TICKS = 8;
    public static final int EYE_OPEN_TICKS = 5;
    /** Ease-out-back overshoot constant (Penner's standard c1), OPENING ONLY. 0 degenerates to a
     *  plain, strong ease-out cubic with no overshoot; ~1.7 is the conventional "back" default
     *  (a small overshoot past EYE_PAIR_SIZE before settling). Closing is unaffected. */
    public static final float EYE_OPEN_OVERSHOOT = 1.7f;
    /** "Closing" ease on dismiss (plain ease-in, no overshoot) — see renderEyeSide. */
    public static final int EYE_CLOSE_TICKS = 8;

    // ---- "Being watched" sound cue, heard by the target only, once per lock (round 8) -----------

    /** Full ResourceLocation string of the sound event to play — swap this to point at any
     *  registered sound (vanilla or custom) without touching code. Points at the custom
     *  bhspellsx cue registered in BHXSoundRegistry (assets/bhspellsx/sounds.json +
     *  sounds/xian_she_huan_ying_eye_stare.ogg); started as a placeholder pointing at vanilla's
     *  "minecraft:entity.enderman.stare" before that custom sound existed. */
    public static final String EYE_SOUND = "bhspellsx:xian_she_huan_ying_eye_stare";
    public static final float EYE_SOUND_VOLUME = 0.4f;
    public static final float EYE_SOUND_PITCH = 1.0f;

    // ---- Purple weakening aura around the locked target: falling lines, not particles (round 7,
    // replacing round 6's DustParticleOptions spawner). Drawn directly by the renderer every
    // frame from time alone (age/phase per line) — no per-line state, no particle entities. -------

    /** How many lines are visible around the target at once. Halved (8 -> 4) in round 8. */
    public static final int AURA_LINE_COUNT = 4;
    /** Horizontal distance from the target's own center axis — never 0, so lines ring the body
     *  instead of bunching at the chest. */
    public static final float AURA_LINE_RADIUS_MIN = 0.3f;
    public static final float AURA_LINE_RADIUS_MAX = 0.6f;
    /** Each line's own length (it's a falling segment, not a full-height beam). */
    public static final float AURA_LINE_LENGTH_MIN = 0.3f;
    public static final float AURA_LINE_LENGTH_MAX = 0.6f;
    /** Line thickness. */
    public static final float AURA_LINE_WIDTH = 0.05f;
    /** Fall speed, blocks/tick. */
    public static final float AURA_LINE_FALL_SPEED = 0.06f;
    /** How far above the target's own head a line's fall starts (it always ends at the target's
     *  feet, i.e. the ground reference — see the renderer). */
    public static final float AURA_LINE_START_ABOVE_HEAD = 0.3f;
    /** Purple tint, 0-1 per channel, drawn full-bright over a plain white texture. */
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
