package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;

/** Client-owned timing only; intentionally independent of animation accessor state. */
public final class JingGuangPanPrediction {
    private static final long NEVER = Long.MIN_VALUE / 2;
    private long sequence, lastStart = NEVER, lastWave = NEVER, notBefore = NEVER;
    private boolean buffered, nextLeft;

    public long sequence() { return sequence; }
    public boolean canFire(long now) { return now >= ready(now); }
    private long ready(long now) {
        return Math.max(notBefore, readyAt(lastStart, lastWave, lastWave > now ? lastWave : -1));
    }
    public void buffer(long now) {
        if (!canFire(now) && now >= ready(now) - BUFFER_TICKS) buffered = true;
    }
    public boolean bufferReady(long now) { return buffered && canFire(now); }
    public void clearBuffer() { buffered = false; }

    /** Call only after confirming a patch is available and this request is eligible. */
    public boolean fire(long now) {
        if (!canFire(now)) throw new IllegalStateException("Ineligible prediction");
        if (now - lastStart >= CLIP_TICKS) nextLeft = false;
        boolean left = nextLeft;
        lastStart = now;
        lastWave = now + WAVE_DELAY_TICKS;
        nextLeft = !left;
        buffered = false;
        sequence++;
        return left;
    }
    public void reject(long rejectedSequence, long now, int retryTicks) {
        if (rejectedSequence != sequence) return;
        buffered = nextLeft = false;
        // Rejection cannot undo the pose already shown. Keep its cut/release spacing,
        // otherwise a one-tick retry can restart the pose two ticks after the last slash.
        notBefore = Math.max(notBefore, now + Math.max(0, retryTicks));
    }
    public void reset() {
        buffered = nextLeft = false;
        lastStart = lastWave = notBefore = NEVER;
        sequence = 0;
    }
}
