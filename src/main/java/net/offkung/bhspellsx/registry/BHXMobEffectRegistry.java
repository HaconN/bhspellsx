package net.offkung.bhspellsx.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspellsx.effect.EmbracingBosomEffect;

public class BHXMobEffectRegistry {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "bhspellsx");

    public static final RegistryObject<MobEffect> EMBRACING_BOSOM =
            MOB_EFFECTS.register("embracing_bosom", EmbracingBosomEffect::new);

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
