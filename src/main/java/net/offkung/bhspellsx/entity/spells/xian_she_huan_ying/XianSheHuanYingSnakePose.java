package net.offkung.bhspellsx.entity.spells.xian_she_huan_ying;

/**
 * Shared math for "where is the snake quad's own center, relative to the caster". Used by both
 * the client-only renderer ({@code XianSheHuanYingBillboardRenderer}, driven off the caster's
 * camera-interpolated position/yaw every frame) and the tail-particle emission in
 * {@code XianSheHuanYingUserEntity}'s client tick (driven off the caster's plain per-tick
 * position/yaw, no partial-tick interpolation needed for a particle spawn point). Kept as a
 * single formula so a future tuning change (e.g. SNAKE_BACK_DISTANCE) can't drift between the two
 * call sites. Pure math, no Minecraft types at all — safe to call from anywhere, either side.
 */
public final class XianSheHuanYingSnakePose {
    private XianSheHuanYingSnakePose() {
    }

    /**
     * World-space offset of the snake quad's center from the caster's own position, given the
     * caster's yaw (radians; Minecraft convention: forward = (-sin(yaw), +cos(yaw)) in (x, z)) and
     * the same {@code bob}/{@code rise} values the caller already computes for its own purposes
     * (the gentle idle bob, and the appear/dismiss rise — both purely additive to Y here, exactly
     * as the renderer already applied them before this method existed).
     *
     * @return {dx, dy, dz} — add to the caster's own (x, y, z) to get the snake center's world
     *         position.
     */
    public static double[] centerOffset(double yawRad, double bob, double rise) {
        double back = XianSheHuanYingConstants.SNAKE_BACK_DISTANCE;
        double dx = Math.sin(yawRad) * back;
        double dz = -Math.cos(yawRad) * back;
        double dy = XianSheHuanYingConstants.SNAKE_CENTER_HEIGHT + bob - rise;
        return new double[]{dx, dy, dz};
    }
}
