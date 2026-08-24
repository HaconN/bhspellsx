package net.offkung.bhspellsx.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.offkung.bhspellsx.BHSpellsX;

/**
 * Pure data for one rotating ring layer drawn by {@link EmbracingBosomRingRenderer}. Immutable so
 * the layer stack can be retuned (radii, speeds, alpha, colour) without touching any rendering
 * code — see EmbracingBosomRingRenderer.LAYERS for the actual configured stack.
 *
 * @param texture           white-on-transparent source texture, tinted at render time.
 * @param radius            outer radius in world blocks.
 * @param innerRadius        inner radius in world blocks; 0 renders a full disc instead of an
 *                          annulus. <b>Non-zero values are effectively broken — see the warning
 *                          below and the one on {@code uvScale} in
 *                          {@code EmbracingBosomRingRenderer.buildAnnulus()}.</b>
 * @param rotationSpeed     degrees of rotation per tick about the entity's Y axis; sign is direction.
 * @param startAngleJitter  jitter amplitude in degrees — the actual per-entity start angle is a
 *                          deterministic hash of the entity's UUID and this layer's index, scaled
 *                          into [0, startAngleJitter). Same entity always gets the same start angle;
 *                          different entities desync from each other and from other layers.
 * @param alpha             base opacity, 0..1, before lifecycle fade in/out is applied.
 * @param tintOverrideRGB   0xRRGGBB tint colour multiplied onto the (white) texture, or {@code null}
 *                          to fall back to the renderer's default tint — see {@link #resolveTint}.
 * @param yOffset           height above the entity's render origin, in blocks — stacking order /
 *                          z-fight avoidance between layers, since the RenderType below disables
 *                          depth-write.
 */
public record RingLayer(
        ResourceLocation texture,
        float radius,
        float innerRadius,
        float rotationSpeed,
        float startAngleJitter,
        float alpha,
        Integer tintOverrideRGB,
        float yOffset) {

    /**
     * ⚠️ {@code innerRadius > 0} (an annulus) does NOT sample the expected part of the texture,
     * and cost a full debugging round to find. {@code EmbracingBosomRingRenderer.buildAnnulus()}'s
     * {@code uvScale} is computed from {@code outer} (this layer's {@code radius}) ALONE —
     * {@code innerRadius} never enters the UV math. So a layer's inner edge always samples texture
     * radius {@code innerRadius / radius} from centre, not texture radius {@code 0} — the annulus
     * only ever sees the outer {@code 1 - (innerRadius / radius)} fraction of the texture, and
     * anything painted inside that radius in the source art is never drawn at all (not dim —
     * genuinely never sampled). This is exactly what made {@code highlight_rim} invisible when it
     * was an annulus with art painted further in than that outer sliver. Fixing this generally
     * would mean reworking {@code uvScale} for a code path with no other user (highlight_rim was,
     * and remains, the only annulus in the stack) — not done. If a future layer genuinely needs to
     * be an annulus, fix {@code uvScale} to map across {@code [innerRadius, radius]} instead of
     * {@code [0, radius]} first, and confirm the fix by checking a texture with content deliberately
     * placed near its own centre actually renders.
     */
    public RingLayer {
        if (innerRadius > 0.0f) {
            BHSpellsX.LOGGER.warn(
                    "[bhspellsx] RingLayer for {} has innerRadius={} > 0 (annulus) — "
                            + "EmbracingBosomRingRenderer's uvScale maps UV across the full disc "
                            + "[0, radius], not [innerRadius, radius], so only the outer "
                            + "1-(innerRadius/radius) fraction of the texture is ever sampled; "
                            + "anything painted further in will not render. See RingLayer's own "
                            + "javadoc before shipping this.",
                    texture, innerRadius);
        }
    }

    /** Convenience constructor for the common case: no per-layer tint override. */
    public RingLayer(ResourceLocation texture, float radius, float innerRadius, float rotationSpeed,
                      float startAngleJitter, float alpha, float yOffset) {
        this(texture, radius, innerRadius, rotationSpeed, startAngleJitter, alpha, null, yOffset);
    }

    /** This layer's own tint if set, otherwise {@code defaultTintRGB}. */
    public int resolveTint(int defaultTintRGB) {
        return tintOverrideRGB != null ? tintOverrideRGB : defaultTintRGB;
    }
}
