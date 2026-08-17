package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Renders nothing. For AoE-style entities whose visuals are all custom particles/sound spawned
 * from the entity's own tick() — the entity itself has no model, so a real renderer would just
 * be dead weight. Registering this is still required: Forge crashes on the client the moment an
 * instance of an unrendered custom EntityType spawns without one.
 */
public class NoopEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/pig/pig.png");

    public NoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return DUMMY_TEXTURE;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // intentionally no-op
    }
}
