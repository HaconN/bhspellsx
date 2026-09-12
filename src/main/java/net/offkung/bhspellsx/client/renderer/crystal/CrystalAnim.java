package net.offkung.bhspellsx.client.renderer.crystal;

import net.minecraft.util.Mth;

/**
 * Generalized version of irons_spellbooks' IceSpikeEntity.getPositionOffset() — same buried
 * -1..0 offset curve (eased with a sine, matching IceSpike's own math exactly), but parameterized
 * over an arbitrary rise window and sink window instead of IceSpike's fixed wait/rise/rest/lower
 * ticks, since our two entities each need several independent crystal groups rising and sinking
 * on different schedules.
 * <p>
 * Purely a function of tick counts — no synced state. Both the server (which never renders) and
 * every client compute this identically from the entity's own tickCount, same as IceSpike does;
 * see AmethystDecreeCasterRingEntity / AmethystDecreeTargetCrystalEntity class javadocs for why
 * that's safe here.
 */
public final class CrystalAnim {
    private CrystalAnim() {
    }

    /**
     * @return -1.0 (fully buried) before {@code riseStart}, easing to 0.0 (fully risen) over
     * {@code riseTicks}, then held at 0.0 until {@code sinkStart}, easing back to -1.0 over
     * {@code sinkTicks}. Never rises again after sinking (matches this VFX's one-shot lifecycle,
     * unlike IceSpike which never re-rises either but is structured slightly differently).
     */
    public static float positionOffset(float age, float riseStart, float riseTicks, float sinkStart, float sinkTicks) {
        if (age < riseStart) {
            return -1.0f;
        } else if (age < riseStart + riseTicks) {
            float f = (age - riseStart) / riseTicks;
            return ease(f) - 1.0f;
        } else if (age < sinkStart) {
            return 0.0f;
        } else if (age < sinkStart + sinkTicks) {
            float f = Mth.clamp((age - sinkStart) / sinkTicks, 0.0f, 1.0f);
            return -ease(f);
        } else {
            return -1.0f;
        }
    }

    /** Same family of sine ease IceSpikeEntity.getPositionOffset() uses (sin(f*pi)/pi + f) for a
     *  quick start/end rather than linear motion — not a bit-for-bit copy of its asymmetric
     *  rise/lower formulas, just the same easing shape reused for both directions here. */
    private static float ease(float f) {
        return Mth.sin(f * (float) Math.PI) / (float) Math.PI + f;
    }
}
