package net.offkung.bhspellsx.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.offkung.bhspellsx.client.renderer.crystal.CrystalAnim;
import net.offkung.bhspellsx.client.renderer.crystal.CrystalTransform;
import net.offkung.bhspellsx.client.renderer.crystal.CrystalUnitModel;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeTargetCrystalEntity;

/**
 * Draws the target-side crystals — the large foot encasement during the root, swapped for small
 * leg crystals after the shatter (AmethystDecreeTargetCrystalEntity fires the shatter particle
 * burst at that same tick, masking the swap). All choreography timing lives here, not in the
 * entity, so it can be retuned without touching the entity's follow/lifetime/sync logic.
 */
public class AmethystDecreeTargetCrystalRenderer extends EntityRenderer<AmethystDecreeTargetCrystalEntity> {
    private static final int RISE_TICKS = 6;
    // Effectively "never" for a phase that should just hold at fully-risen until swapped out.
    private static final float NO_SINK = Float.MAX_VALUE;
    // Leg crystals shrink continuously across the DoT tail instead of sinking at the very end —
    // tuning pass 2: a separate sink-into-ground right as they're also shrinking away read as two
    // competing "disappear" mechanisms, so this replaced the old sink-based fade for this phase
    // entirely rather than combining with it. MIN_SCALE keeps a sliver visible rather than
    // popping to exactly zero on the last frame.
    private static final float LEG_SHRINK_MIN_SCALE = 0.05f;

    // Group C ("target strikes") — 1-2 large spikes rising the instant this entity spawns (i.e.
    // the instant the burst lands), on the SAME relative schedule as the caster ring's own burst
    // spikes (2-tick rise, 60-tick hold, 30-tick sink — see AmethystDecreeCasterRingRenderer),
    // not tied to rootDurationTicks at all. Rendered unconditionally alongside whichever of the
    // encasement/leg-crystal phases is currently showing — it's a separate, independent layer.
    private static final int STRIKE_RISE_TICKS = 2;
    private static final int STRIKE_SINK_START = 60;
    private static final int STRIKE_SINK_TICKS = 30;

    private final ModelPart smallUnit;
    private final ModelPart largeUnit;

    public AmethystDecreeTargetCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.smallUnit = context.bakeLayer(CrystalUnitModel.SMALL_LAYER);
        this.largeUnit = context.bakeLayer(CrystalUnitModel.LARGE_LAYER);
    }

    @Override
    public void render(AmethystDecreeTargetCrystalEntity entity, float entityYaw, float partialTicks,
                        PoseStack poseStack, MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTicks;
        int rootTicks = entity.getRootDurationTicks();
        int totalTicks = entity.getTotalLifetimeTicks();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));

        if (age < rootTicks) {
            for (CrystalTransform t : entity.getOrComputeEncasement()) {
                float offset = CrystalAnim.positionOffset(age, 0, RISE_TICKS, NO_SINK, 0);
                if (offset <= -1.0f) {
                    continue;
                }
                CrystalUnitModel.renderInstance(smallUnit, largeUnit, poseStack, consumer, light, OverlayTexture.NO_OVERLAY, t, offset);
            }
        } else {
            // Rise in at the shatter, then hold fully risen (no sink) — see LEG_SHRINK_MIN_SCALE
            // javadoc for why shrinking replaces sinking as the tail-end "disappear" here.
            float legAge = age - rootTicks;
            float legTotal = Math.max(1.0f, totalTicks - rootTicks);
            float shrinkProgress = Mth.clamp(legAge / legTotal, 0.0f, 1.0f);
            float extraScale = Mth.lerp(shrinkProgress, 1.0f, LEG_SHRINK_MIN_SCALE);
            for (CrystalTransform t : entity.getOrComputeLegCrystals()) {
                float offset = CrystalAnim.positionOffset(age, rootTicks, RISE_TICKS, NO_SINK, 0);
                if (offset <= -1.0f) {
                    continue;
                }
                CrystalUnitModel.renderInstance(smallUnit, largeUnit, poseStack, consumer, light, OverlayTexture.NO_OVERLAY, t, offset, extraScale);
            }
        }

        for (CrystalTransform t : entity.getOrComputeStrikeSpikes()) {
            float offset = CrystalAnim.positionOffset(age, 0, STRIKE_RISE_TICKS, STRIKE_SINK_START, STRIKE_SINK_TICKS);
            if (offset <= -1.0f) {
                continue;
            }
            CrystalUnitModel.renderInstance(smallUnit, largeUnit, poseStack, consumer, light, OverlayTexture.NO_OVERLAY, t, offset);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AmethystDecreeTargetCrystalEntity entity) {
        return CrystalUnitModel.TEXTURE;
    }
}
