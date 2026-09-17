package net.offkung.bhspellsx.entity.spells.crystal_hydro_dome;

/**
 * All tuning numbers for crystal_hydro_dome, hardcoded rather than config-backed — same reason
 * AmethystDecreeConstants/EmbracingBosomAoe are hardcoded: a ForgeConfigSpec would let a
 * previously-generated toml silently override an approved balance change on next launch (see
 * bhspellsx/CLAUDE.md's "Forge config files override code defaults" note).
 */
public final class CrystalHydroDomeConstants {
    private CrystalHydroDomeConstants() {
    }

    // --- Shape (hemisphere, centered on the caster's feet position at cast time) ---

    /** Horizontal radius (blocks) of the hemisphere. */
    public static final double RADIUS = 10.0;
    /** RADIUS squared — precomputed for isInside(). */
    public static final double RADIUS_SQUARED = RADIUS * RADIUS;
    /** Vertical height (blocks) of the hemisphere above the center. */
    public static final double HEIGHT = 10.0;
    /** How far below the center's Y a point may still count as inside (terrain/feet tolerance). */
    public static final double BELOW_CENTER_TOLERANCE = -1.0;

    // --- Tags ---

    /** Scoreboard tag added to the caster for the dome's lifetime — Apoli's skill1_dome_watch
     *  action_over_time watches for this to arm the cooldown only after the dome ends. */
    public static final String DOME_TAG = "pers_liming_dome";
    /** Entity tag marking a reflected arrow so the projectile scan doesn't re-process it forever
     *  as it flies back out through the dome boundary. */
    public static final String REFLECTED_TAG = "bhspellsx_crystal_hydro_dome_reflected";

    // --- Lifecycle ---

    /** Total duration (ticks) the dome stays alive if never broken early. Matches
     *  CrystalHydroDomeSpell's castTime — cast completion (onServerCastComplete) is the primary
     *  natural-end trigger now, not this tick count directly (see FALLBACK_END_GRACE_TICKS). */
    public static final int DURATION_TICKS = 120;
    /** Grace period (ticks) past DURATION_TICKS before the dome's own tick()-based natural-end
     *  check fires, as a fallback only, in case cast state is somehow lost and
     *  CrystalHydroDomeSpell.onServerCastComplete(cancelled=false) never fires. */
    public static final int FALLBACK_END_GRACE_TICKS = 5;
    /** Visual-only linger after the dome ends: all gameplay stops at finish(), the entity just
     *  stays loaded this many ticks so clients can play the end/shatter animation, then discards. */
    public static final int END_LINGER_TICKS = 30;
    /** Starting/maximum HP pool for the dome. */
    public static final double DOME_HP = 150.0;

    // --- On-cast open heal/cleanse ---

    /** Heal applied once to everyone inside at the moment the dome opens. */
    public static final double OPEN_HEAL = 50.0;
    /** Heal applied once to everyone inside at the dome's natural end (tick 120, HP > 0 only). */
    public static final double END_HEAL = 40.0;

    // --- Damage split ---

    /** Fraction of damage from an inside-sourced hit (B) the dome absorbs; victim takes the rest. */
    public static final double INSIDE_DOME_SHARE = 0.30;
    /** Fraction of what the dome actually absorbed that is returned to a Player attacker as Counter. */
    public static final double COUNTER_RATIO = 0.50;

    // --- Dome-owned i-frame gating for outside-sourced hits (see CrystalHydroDomeAoe.gateOutsideHit) ---

    /** Default gating window (ticks) used when the incoming DamageSource doesn't request its own
     *  via SpellDamageSource.setIFrames() — matches vanilla LivingEntity's own invulnerableTime
     *  threshold (hurt() only treats >10 as "still invulnerable"). */
    public static final int DEFAULT_IFRAME_WINDOW_TICKS = 10;

    // --- horizontalstop+verticalstop re-application (every tick, so it lapses on its own after
    //     the dome ends) — NOT efn:stop, see EFN_HORIZONTALSTOP_ID/EFN_VERTICALSTOP_ID's javadoc ---

    /** Duration (ticks) re-applied to the caster's horizontalstop/verticalstop each tick the dome
     *  is alive. Amplifier is always 0 — neither effect's logic reads amplifier at all. */
    public static final int STOP_REAPPLY_DURATION_TICKS = 5;

    // --- Projectile layer ---

    /** Extra margin (blocks) added to the dome's radius/height when building the projectile search box,
     *  to catch fast projectiles (arrows move ~3 blocks/tick) whose current position is still outside
     *  the dome but whose next-tick position would cross into it. Doubled alongside RADIUS (was 4.0
     *  at radius 5) — arrow speed itself hasn't changed, so this is more slack than strictly needed,
     *  kept proportional to the dome's new size rather than left at the old absolute value. */
    public static final double PROJECTILE_SCAN_MARGIN = 8.0;
    /** Step size (blocks) used when sampling a projectile's pos -> pos+deltaMovement segment for a
     *  dome-entry crossing. */
    public static final double SEGMENT_SAMPLE_STEP = 0.25;

    // --- Knockback ring (natural end only) ---

    /** Inner horizontal radius (blocks) of the knockback ring, measured from the dome center. */
    public static final double KNOCKBACK_RING_INNER_RADIUS = 10.0;
    /** Outer horizontal radius (blocks) of the knockback ring, measured from the dome center. */
    public static final double KNOCKBACK_RING_OUTER_RADIUS = 13.0;
    /** Vertical reach (blocks) of the knockback ring below/above the dome center. */
    public static final double KNOCKBACK_RING_BELOW = -2.0;
    public static final double KNOCKBACK_RING_ABOVE = 11.0;
    /**
     * Horizontal knockback strength passed to LivingEntity.knockback(strength, dx, dz). The
     * original 0.7 estimate (derived from an assumed ~0.91 air-drag geometric decay) undershot in
     * actual play-testing — observed travel was only ~2 blocks against a target ~4.5 blocks was
     * wanted, meaning ground friction decelerates a knocked-back entity considerably faster than
     * that estimate assumed (most real deceleration happens after landing, governed by per-block
     * friction, not the airborne drag the estimate was based on). Raised to 1.5 (roughly 2x) from
     * the observed ~2-block result to target ~4.5 blocks; the vanilla KNOCKBACK_RESISTANCE
     * attribute still reduces the effective strength per-target on top of this. Retune again in
     * play-testing if it still over/undershoots — this is empirical, not derived.
     */
    public static final double KNOCKBACK_STRENGTH = 1.5;

    // --- Action-bar HP readout (caster only) ---

    /** Periodic keepalive cadence (ticks) — the action bar fades ~3s after its last send, so this
     *  must be well under 60 ticks regardless of whether HP has changed. */
    public static final int HP_READOUT_INTERVAL_TICKS = 10;
    /** Fraction of DOME_HP below which the hp/max text switches from white to red. */
    public static final double HP_READOUT_LOW_THRESHOLD = 0.30;
}
