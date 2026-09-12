package net.offkung.bhspellsx.client.renderer.crystal;

/**
 * One crystal instance's placement, relative to the owning entity's own position (dx/dy/dz are
 * offsets, not world coordinates) — computed once by a layout class (CasterRingLayout /
 * TargetCrystalLayout) and cached, never recomputed per frame.
 *
 * @param riseDelayTicks ticks after the group's own rise window starts before this specific
 *                        instance begins rising — gives a scattered, non-simultaneous pop-up
 *                        instead of every crystal appearing on the same tick.
 * @param large           which stamp unit to draw (see CrystalUnitModel).
 */
public record CrystalTransform(double dx, double dy, double dz, float yawDeg, float tiltDeg, float scale,
                                int riseDelayTicks, boolean large) {
}
