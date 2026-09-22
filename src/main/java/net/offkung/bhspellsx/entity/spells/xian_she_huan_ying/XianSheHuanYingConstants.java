package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

/**
 * Hardcoded numbers for Xian She Huan Ying (Wu Liang Ye, gold). Same "fixed for this spell"
 * approach as AmethystDecreeConstants — nothing here scales with spell level or spell power.
 * <p>
 * Round 12 removed every caster-side VFX (the snake image, the 6-cloud mist, the tail-fade
 * particles) — the caster now shows the same eye pair the target does. Constants for all of that
 * (SNAKE_*, CLOUD_*, TAIL_*, APPEAR_*) have been deleted along with the code that used them.
 */
public final class XianSheHuanYingConstants {
    private XianSheHuanYingConstants() {
    }

    /** Scoreboard tag Apoli puts on the caster while the toggle is on. Its absence is the ONLY
     *  stop signal the user-side entity listens to — see XianSheHuanYingUserEntity. */
    public static final String OWNER_TAG = "wly_snake";

    /** Ticks both entities spend in their "dismissing" state (see XianSheHuanYingUserEntity's
     *  class javadoc) between an end condition becoming true and the actual discard — long enough
     *  for the client to play the eye-close animation. */
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

    // ---- Eye pair: shown above BOTH the caster's own head and the locked target's head (round 6
    // introduced it target-side only; round 12 made the caster show the identical pair too). ------

    /** Height of the eye pair's CENTER above the subject's feet — fallback only, used when the
     *  subject (caster or target) can't be found client-side. Normally the renderer uses the
     *  subject's real bounding-box height (see EYE_HEIGHT_MARGIN) instead. */
    public static final float EYE_CENTER_HEIGHT = 2.75f;
    /** When the subject IS found client-side: margin above its real bounding-box top, added to
     *  the eye pair's center height. */
    public static final float EYE_HEIGHT_MARGIN = 0.45f;

    /** Width and height of a single eye quad. */
    public static final float EYE_PAIR_SIZE = 0.75f;
    /** Center-to-center distance between the two eyes, measured in the VIEWER's screen space (not
     *  world space) so the pair always reads as a fixed-width pair no matter the view angle. */
    public static final float EYE_PAIR_GAP = 0.9f;
    /** "Opening" ease: height goes 0 -> EYE_PAIR_SIZE (width stays fixed), starting this many
     *  ticks after the entity spawns and taking EYE_OPEN_TICKS to finish. Uses ease-out-back (see
     *  EYE_OPEN_OVERSHOOT) — closing does not, it stays a plain ease-in collapse. Identical for
     *  the caster's own eyes and the target's, per spec ("จังหวะลืมตาของผู้ใช้เท่ากับของเป้า"). */
    public static final int EYE_OPEN_DELAY_TICKS = 8;
    public static final int EYE_OPEN_TICKS = 5;
    /** Ease-out-back overshoot constant (Penner's standard c1), OPENING ONLY. 0 degenerates to a
     *  plain, strong ease-out cubic with no overshoot; ~1.7 is the conventional "back" default
     *  (a small overshoot past EYE_PAIR_SIZE before settling). Closing is unaffected. */
    public static final float EYE_OPEN_OVERSHOOT = 1.7f;
    /** "Closing" ease on dismiss (plain ease-in, no overshoot). */
    public static final int EYE_CLOSE_TICKS = 8;

    // ---- "Being watched" sound cue, heard by the target only, once per lock. Unaffected by round
    // 12 — the caster never gets a sound cue for its own eyes. ------------------------------------

    /** Full ResourceLocation string of the sound event to play — swap this to point at any
     *  registered sound (vanilla or custom) without touching code. Points at the custom
     *  bhspellsx cue registered in BHXSoundRegistry (assets/bhspellsx/sounds.json +
     *  sounds/xian_she_huan_ying_eye_stare.ogg). */
    public static final String EYE_SOUND = "bhspellsx:xian_she_huan_ying_eye_stare";
    public static final float EYE_SOUND_VOLUME = 0.4f;
    public static final float EYE_SOUND_PITCH = 1.0f;

    // ---- Purple weakening aura around the locked target only: falling lines, not particles.
    // Round 12: still target-only — the caster never gets these. Drawn directly by the renderer
    // every frame from time alone (age/phase per line) — no per-line state, no particle entities.

    /** How many lines are visible around the target at once. */
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

    /** Gentle vertical bob (the eye pair, either side): amplitude in blocks, full period in ticks. */
    public static final float BOB_AMPLITUDE = 0.08f;
    public static final float BOB_PERIOD_TICKS = 50.0f;

    /** true = draw at full brightness regardless of world light; false = normal world light. */
    public static final boolean RENDER_FULL_BRIGHT = false;

    // ---- [Optional] texture filter switch ----------------------------------------------------------

    /** false = nearest filtering (pixel-sharp, current look). true = linear filtering (blurred).
     *  Toggle to compare in-game; never additive/translucent — still opaque cutout either way. */
    public static final boolean TEXTURE_SMOOTH = false;
}
