package net.offkung.bhspellsx.client.renderer.crystal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Two reusable "stamp" crystal shapes — a small one (background scatter / leg crystals) and a
 * large one (burst spikes / root encasement) — built the same way irons_spellbooks' own
 * IceSpikeModel is: plain vanilla CubeListBuilder cuboids, no model file, no GeckoLib. Each is a
 * core box plus one or two angled "shard" boxes for the faceted look.
 * <p>
 * These are baked ONCE per renderer (via EntityRendererProvider.Context#bakeLayer) and then
 * ModelPart#render() is called once per crystal INSTANCE with a different PoseStack transform
 * each time (see AmethystDecreeCasterRingRenderer / AmethystDecreeTargetCrystalRenderer) — that's
 * the "one entity draws many crystals" instancing this whole VFX design relies on.
 * <p>
 * Box dimensions here are mirrored exactly in the UV layout baked into
 * assets/bhspellsx/textures/entity/amethyst_crystal.png — if a box's (w,h,d) changes here, the
 * texture's UV regions for that box must be regenerated to match (see the generator script noted
 * in that texture's own history; regenerate rather than hand-edit the PNG).
 */
public class CrystalUnitModel {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "textures/entity/amethyst_crystal.png");

    public static final ModelLayerLocation SMALL_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_crystal_small"), "main");
    public static final ModelLayerLocation LARGE_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_crystal_large"), "main");

    public static LayerDefinition createSmallLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("small_core",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, -7.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.ZERO);
        root.addOrReplaceChild("small_shard",
                CubeListBuilder.create().texOffs(0, 9).addBox(-1.0f, -4.0f, -1.0f, 2.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(1.5f, -1.5f, 0.5f, 0.0f, 0.0f, 0.45f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createLargeLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("large_core",
                CubeListBuilder.create().texOffs(10, 0).addBox(-2.0f, -14.0f, -2.0f, 4.0f, 14.0f, 4.0f),
                PartPose.ZERO);
        root.addOrReplaceChild("large_shard_a",
                CubeListBuilder.create().texOffs(10, 19).addBox(-1.5f, -8.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offsetAndRotation(2.5f, -3.0f, 1.0f, 0.0f, 0.0f, 0.35f));
        root.addOrReplaceChild("large_shard_b",
                CubeListBuilder.create().texOffs(23, 19).addBox(-1.5f, -8.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offsetAndRotation(-2.5f, -3.0f, -1.0f, 0.0f, 0.0f, -0.35f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Draws one crystal instance: translates to the transform's offset, rotates to its
     * yaw/tilt, applies its scale (with the Y-flip baked in, same trick
     * irons_spellbooks' IceSpikeRenderer uses so boxes can be authored growing "up" from a
     * pivot at their own base), then applies the buried rise/sink offset as a further
     * translate — same order of operations as IceSpikeRenderer.render(), copied deliberately
     * rather than re-derived, since the sign of every one of these steps depends on the ones
     * before it.
     *
     * @param riseOffset -1.0 (fully buried) .. 0.0 (fully risen), from CrystalAnim.
     */
    public static void renderInstance(ModelPart smallUnit, ModelPart largeUnit, PoseStack poseStack,
                                       VertexConsumer consumer, int light, int overlay,
                                       CrystalTransform transform, float riseOffset) {
        renderInstance(smallUnit, largeUnit, poseStack, consumer, light, overlay, transform, riseOffset, 1.0f);
    }

    /**
     * Same as the 8-arg overload, with an extra uniform scale multiplier on top of the
     * transform's own scale — used for the target-side leg crystals shrinking gradually over
     * their DoT lifetime (see AmethystDecreeTargetCrystalRenderer) without needing a second
     * per-instance scale baked into CrystalTransform itself.
     */
    public static void renderInstance(ModelPart smallUnit, ModelPart largeUnit, PoseStack poseStack,
                                       VertexConsumer consumer, int light, int overlay,
                                       CrystalTransform transform, float riseOffset, float extraScale) {
        poseStack.pushPose();
        poseStack.translate(transform.dx(), transform.dy(), transform.dz());
        poseStack.mulPose(Axis.YP.rotationDegrees(transform.yawDeg()));
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.tiltDeg()));
        float scale = transform.scale() * extraScale;
        poseStack.scale(scale, -scale, scale);
        // 16 model units = 1 block; a full crystal (~14 units tall) sinks its own height below
        // ground when riseOffset = -1.
        poseStack.translate(0.0, -riseOffset * 16.0, 0.0);
        ModelPart unit = transform.large() ? largeUnit : smallUnit;
        unit.render(poseStack, consumer, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
    }
}
