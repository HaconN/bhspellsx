package net.offkung.bhspellsx.entity.spells.amethyst_decree;

/**
 * All tuning numbers for amethyst_decree, hardcoded rather than config-backed. These values are
 * team-approved balance, not per-instance tunables — a ForgeConfigSpec would let someone quietly
 * retune an approved skill on a live instance, which is the opposite of what we want. This
 * matches EmbracingBosomAoe, which is hardcoded for the same reason.
 * <p>
 * Values below were read from the deployed, hand-tuned
 * {@code config/bhspellsx-common.toml} at the time this class replaced
 * {@code AmethystDecreeConfig} (the toml's values, not the old code defaults, which had drifted
 * — see bhspellsx/CLAUDE.md's "Forge config files override code defaults" note for why those two
 * had diverged). That toml is now orphaned (nothing reads it anymore) and was intentionally left
 * on disk rather than deleted.
 */
public final class AmethystDecreeConstants {
    private AmethystDecreeConstants() {
    }

    // --- Gameplay: burst, root/stun, debuffs, DoT ---

    /** Radius (blocks) around the caster affected on cast completion. */
    public static final double RADIUS = 10.0;
    /** Immediate fixed damage dealt to each target on hit. */
    public static final double BURST_DAMAGE = 30.0;
    /** Duration (ticks) of the efn:stop hard root applied to each target. */
    public static final int ROOT_DURATION_TICKS = 60;
    /** Duration (ticks) of the cataclysm:stun applied alongside the root. */
    public static final int STUN_DURATION_TICKS = 60;
    /** Duration (ticks) of the Slowness II / Weakness I / Mining Fatigue II debuffs. */
    public static final int DEBUFF_DURATION_TICKS = 100;
    /** Fixed damage dealt per damage-over-time tick. */
    public static final double DOT_DAMAGE_PER_TICK = 5.0;
    /** Ticks between each damage-over-time application. */
    public static final int DOT_INTERVAL_TICKS = 20;
    /** Number of damage-over-time applications (total DoT duration = DOT_INTERVAL_TICKS * DOT_TICK_COUNT). */
    public static final int DOT_TICK_COUNT = 4;

    // --- Sound: vanilla amethyst SoundEvents only, see AmethystDecreeSounds ---

    public static final float CAST_START_VOLUME = 0.6f;
    public static final float CAST_START_PITCH = 0.7f;
    public static final float RISE_CHIME_VOLUME = 0.4f;
    public static final float RISE_CHIME_PITCH = 1.3f;
    public static final float ERUPTION_VOLUME = 1.6f;
    public static final float ERUPTION_PITCH = 1.0f;
    public static final float SHATTER_VOLUME = 1.3f;
    public static final float SHATTER_PITCH = 0.9f;
    public static final float DOT_CHIME_VOLUME = 0.25f;
    public static final float DOT_CHIME_PITCH = 1.4f;
}
