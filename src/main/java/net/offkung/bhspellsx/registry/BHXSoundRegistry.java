package net.offkung.bhspellsx.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Custom sound events for bhspellsx — currently just Xian She Huan Ying's "being watched" cue
 * (see XianSheHuanYingTargetEntity, client tick only). Same DeferredRegister&lt;SoundEvent&gt;
 * pattern bhspells' own BHSoundRegistry uses (decompiled reference at
 * Origins/Mods/_reference/bhspells-1.3.0-decompiled/.../registry/BHSoundRegistry.java) — a
 * matching entry in assets/bhspellsx/sounds.json plus the .ogg file under
 * assets/bhspellsx/sounds/ is the other half; nothing else is needed to make a custom sound work.
 */
public class BHXSoundRegistry {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "bhspellsx");

    public static final RegistryObject<SoundEvent> XIAN_SHE_HUAN_YING_EYE_STARE =
            registerSoundEvent("xian_she_huan_ying_eye_stare");

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("bhspellsx", name)));
    }
}
