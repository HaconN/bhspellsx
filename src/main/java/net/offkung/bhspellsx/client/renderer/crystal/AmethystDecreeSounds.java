package net.offkung.bhspellsx.client.renderer.crystal;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeConstants;

/**
 * All five sound cues for amethyst_decree, vanilla amethyst SoundEvents only (see project
 * CLAUDE.md's sound recon report for why: bhspells' own house convention for a broadcast cue
 * heard by everyone is {@code Level#playSound(null, x, y, z, sound, SoundSource, volume, pitch)},
 * confirmed via decompiling SpinStrikeSpell — used here throughout rather than
 * AbstractSpell's own entity-based {@code playSound()} hook, since that hook's volume/pitch are
 * hardcoded (ISB's own convention: 2.0F / 0.9-1.1 jitter) and this spell wants its own tuned
 * values instead.
 * <p>
 * Base volume/pitch per cue are hardcoded constants (see AmethystDecreeConstants — team-approved
 * balance, not per-instance tunables); PITCH_JITTER here is a separate fixed code constant, not
 * one of those tuned values, purely to keep repeated cues (chimes, eruption layers) from
 * sounding machine-gunned.
 * <p>
 * Called only from server-side tick/cast code (same call sites as CrystalDebris) — never from a
 * renderer or other client-only code path.
 */
public final class AmethystDecreeSounds {
    private static final float PITCH_JITTER = 0.1f;

    private AmethystDecreeSounds() {
    }

    private static float jitter(RandomSource random, double basePitch) {
        return (float) basePitch + (random.nextFloat() - 0.5f) * PITCH_JITTER;
    }

    /** Cast start: one low, quiet resonate. */
    public static void playCastStart(Level level, double x, double y, double z, RandomSource random) {
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                AmethystDecreeConstants.CAST_START_VOLUME,
                jitter(random, AmethystDecreeConstants.CAST_START_PITCH));
    }

    /** During the cast: one sparse light chime as the small crystals rise. Call this a handful
     *  of times across the cast window, not every tick. */
    public static void playRiseChime(Level level, double x, double y, double z, RandomSource random) {
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                AmethystDecreeConstants.RISE_CHIME_VOLUME,
                jitter(random, AmethystDecreeConstants.RISE_CHIME_PITCH));
    }

    /** Cast completion: several cluster-place layers with slight pitch variation, so the burst
     *  reads as one crystalline surge rather than a single thin click. */
    public static void playEruption(Level level, double x, double y, double z, RandomSource random, int layers) {
        float volume = AmethystDecreeConstants.ERUPTION_VOLUME;
        double basePitch = AmethystDecreeConstants.ERUPTION_PITCH;
        for (int i = 0; i < layers; i++) {
            level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS,
                    volume, jitter(random, basePitch));
        }
    }

    /** Root end / encasement shatter: the satisfying cue, second-loudest after the eruption. */
    public static void playShatter(Level level, double x, double y, double z, RandomSource random) {
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS,
                AmethystDecreeConstants.SHATTER_VOLUME,
                jitter(random, AmethystDecreeConstants.SHATTER_PITCH));
    }

    /** During the DoT tail: a very faint, sparse chime. Caller is expected to already be gating
     *  frequency (see AmethystDecreeTargetCrystalEntity's ambient-shimmer interval) — this just
     *  plays one instance quietly. */
    public static void playDotChime(Level level, double x, double y, double z, RandomSource random) {
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                AmethystDecreeConstants.DOT_CHIME_VOLUME,
                jitter(random, AmethystDecreeConstants.DOT_CHIME_PITCH));
    }
}
