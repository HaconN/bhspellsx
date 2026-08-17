package net.offkung.bhspellsx.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspellsx.entity.spells.embracing_bosom.EmbracingBosomAoe;

public class BHXEntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "bhspellsx");

    public static final RegistryObject<EntityType<EmbracingBosomAoe>> EMBRACING_BOSOM_AOE =
            ENTITY_TYPES.register("embracing_bosom_aoe", () -> EntityType.Builder
                    .<EmbracingBosomAoe>of(EmbracingBosomAoe::new, MobCategory.MISC)
                    .sized(12.0f, 1.2f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "embracing_bosom_aoe").toString()));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
