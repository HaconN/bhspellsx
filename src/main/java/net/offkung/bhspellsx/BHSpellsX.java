package net.offkung.bhspellsx;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.config.AmethystDecreeConfig;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeAoe;
import net.offkung.bhspellsx.event.EmbracingBosomEvents;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;
import net.offkung.bhspellsx.registry.BHXMobEffectRegistry;
import net.offkung.bhspellsx.registry.BHXParticleRegistry;
import net.offkung.bhspellsx.registry.BHXSpellRegistry;
import org.slf4j.Logger;

/**
 * Phase 0 bootstrap mod. This entrypoint and the registry/ package are throwaway
 * scaffolding, deleted once the portable subpackages under net.offkung.bhspellsx are
 * folded into the real bhspells mod (see MERGE.md at the repo root) — do not build on top
 * of this class long-term.
 */
@Mod("bhspellsx")
public class BHSpellsX {
    public static final String MODID = "bhspellsx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BHSpellsX(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        BHXSpellRegistry.register(modEventBus);
        BHXEntityRegistry.register(modEventBus);
        BHXMobEffectRegistry.register(modEventBus);
        BHXParticleRegistry.register(modEventBus);
        // EmbracingBosomEvents is portable content (no modid baked in) — registered manually
        // here rather than via @Mod.EventBusSubscriber, matching bhspells' own house style
        // (see BypassDamageEvent/SwordDashManager in the real bhspells mod).
        MinecraftForge.EVENT_BUS.register(EmbracingBosomEvents.class);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AmethystDecreeConfig.SPEC);
        modEventBus.addListener(BHSpellsX::checkAmethystDecreeMobEffects);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * amethyst_decree's root/stun depend on efn:stop and cataclysm:stun existing at runtime
     * (looked up by id, not compiled against — see AmethystDecreeAoe). If either mod is ever
     * removed from the pack, the spell would silently stop applying that one effect with no
     * other signal, so this logs a hard ERROR at common setup instead of staying quiet.
     */
    private static void checkAmethystDecreeMobEffects(FMLCommonSetupEvent event) {
        if (ForgeRegistries.MOB_EFFECTS.getValue(AmethystDecreeAoe.EFN_STOP_ID) == null) {
            LOGGER.error("amethyst_decree: mob effect '{}' not found (Epic Fight Nightfall missing/renamed?) — root will not apply.",
                    AmethystDecreeAoe.EFN_STOP_ID);
        }
        if (ForgeRegistries.MOB_EFFECTS.getValue(AmethystDecreeAoe.CATACLYSM_STUN_ID) == null) {
            LOGGER.error("amethyst_decree: mob effect '{}' not found (L_Ender's Cataclysm missing/renamed?) — stun will not apply.",
                    AmethystDecreeAoe.CATACLYSM_STUN_ID);
        }
    }
}
