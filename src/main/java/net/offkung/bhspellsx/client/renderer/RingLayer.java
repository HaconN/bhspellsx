package net.offkung.bhspellsx.client.renderer;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure data for one rotating ring layer drawn by {@link EmbracingBosomRingRenderer}. Immutable so
 * the layer stack can be retuned (radii, speeds, alpha, colour) without touching any rendering
 * code — see EmbracingBosomRingRenderer.LAYERS for the actual configured stack.
 *
 * @param texture           white-on-transparent source texture, tinted by tintRGB at render time.
 * @param radius            outer radius in world blocks.
 * @param innerRadius        inner radius in world blocks; 0 renders a full disc instead of an annulus.
 * @param rotationSpeed     degrees of rotation per tick about the entity's Y axis; sign is direction.
 * @param startAngleJitter  jitter amplitude in degrees — the actual per-entity start angle is a
 *                          deterministic hash of the entity's UUID and this layer's index, scaled
 *                          into [0, startAngleJitter). Same entity always gets the same start angle;
 *                          different entities desync from each other and from other layers.
 * @param alpha             base opacity, 0..1, before lifecycle fade in/out is applied.
 * @param tintRGB           0xRRGGBB tint colour multiplied onto the (white) texture.
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
        int tintRGB,
        float yOffset) {
}
