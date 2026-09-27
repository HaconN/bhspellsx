package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

public final class JingGuangPanConstants {
    public static final String ACTIVE_TAG = "pers_jing_guang_pan";
    public static final float PLAY_SPEED = 1.05F;
    public static final int WAVE_DELAY_TICKS = 2;
    public static final int CUT_TICKS = 5;
    public static final int SERVER_TIMING_TOLERANCE_TICKS = 1;
    public static final int BUFFER_TICKS = 4;
    public static final double RANGE = 7.0;
    public static final double WAVE_SPEED = 2.8;
    public static final float DAMAGE = 4.0F;
    public static final double BOOST_HEIGHT = 4.0;
    public static final int BOOST_TICKS = 12;
    public static final int BLEND_OUT_TICKS = 4;
    public static final int CLIP_END_FRAME = 30;
    public static final double CLIP_TICKS = CLIP_END_FRAME / 60.0 * 20.0 / PLAY_SPEED;
    public static final double WAVE_RADIUS = 1.0;
    public static final double WAVE_ARC_DEGREES = 150.0;
    public static final double WAVE_THICKNESS = 0.22;
    public static final double WAVE_DEPTH = 0.24;
    public static final double WAVE_TAPER_POWER = 0.7;
    public static final double WAVE_RIGHT_ROLL = -22.0, WAVE_LEFT_ROLL = 22.0;
    public static final double WAVE_ROLL_JITTER_DEGREES = 4.0;
    public static final int WAVE_SEGMENTS = 12;
    public static final String DAMAGE_TYPE = "bhspells:gold_spell_bypass";
    public static double boostOffset(int tick) {
        double u = Math.max(0.0, Math.min(1.0, (double) tick / BOOST_TICKS));
        return BOOST_HEIGHT * u * u * (3.0 - 2.0 * u);
    }
    /** Earliest request time; both sides use the same cut and release spacing. */
    public static long readyAt(long start, long wave, long pending) {
        return Math.max(start + CUT_TICKS, Math.max(wave + CUT_TICKS - WAVE_DELAY_TICKS, pending));
    }
    /** Server-only arrival tolerance; client prediction always keeps CUT_TICKS. */
    public static int serverCutTicks() {
        return Math.max(1, CUT_TICKS - Math.max(0, SERVER_TIMING_TOLERANCE_TICKS));
    }
    public static long serverReadyAt(long start, long wave, long pending) {
        int spacing = serverCutTicks();
        return Math.max(start + spacing, Math.max(wave + spacing - WAVE_DELAY_TICKS, pending));
    }
    public static final double GLIDE_SPEED = 0.12;
    private JingGuangPanConstants() {}
}
