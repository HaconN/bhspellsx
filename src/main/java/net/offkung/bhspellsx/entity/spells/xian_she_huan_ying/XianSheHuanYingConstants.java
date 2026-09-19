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

    /** Temporary vanilla-particle VFX cadence (round 1 only; replaced by renderers later). */
    public static final int VFX_INTERVAL_TICKS = 4;
}
