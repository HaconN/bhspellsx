package net.offkung.bhspellsx.registry;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.offkung.bhspellsx.BHSpellsX;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

/** Common registration; client layer settings live in the animation's data JSON. */
@Mod.EventBusSubscriber(modid = BHSpellsX.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BHXAnimationRegistry {
    public static AnimationManager.AnimationAccessor<StaticAnimation> JUDGEMENT_CUT;
    public static AnimationManager.AnimationAccessor<StaticAnimation> JUDGEMENT_CUT_LEFT;

    private BHXAnimationRegistry() {
    }

    @SubscribeEvent
    public static void registerAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder("bhspellsx", builder -> {
            // Zero transition keeps replay requests at frame zero instead of blending
            // through a wind-up. Epic Fight still processes its link on the next tick.
            JUDGEMENT_CUT = builder.nextAccessor("biped/spells/judgement_cut", accessor ->
                new StaticAnimation(0.0F, false, accessor, Armatures.BIPED)
                    .addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER,
                        (animation, patch, speed, previousTime, elapsedTime) -> net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.PLAY_SPEED)
            );
            JUDGEMENT_CUT_LEFT = builder.nextAccessor("biped/spells/judgement_cut_left", accessor ->
                new StaticAnimation(0.0F, false, accessor, Armatures.BIPED)
                    .addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER,
                        (animation, patch, speed, previousTime, elapsedTime) -> net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.PLAY_SPEED)
            );
        });
    }
}
