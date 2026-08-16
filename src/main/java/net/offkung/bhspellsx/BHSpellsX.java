package net.offkung.bhspellsx;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.offkung.bhspellsx.registry.BHXSpellRegistry;
import org.slf4j.Logger;

/**
 * Phase 0 bootstrap mod. Everything in this package is throwaway scaffolding that gets
 * deleted once net.offkung.bhspells.* content is folded into the real bhspells mod
 * (see MERGE.md at the repo root) — do not build on top of this class long-term.
 */
@Mod("bhspellsx")
public class BHSpellsX {
    public static final String MODID = "bhspellsx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BHSpellsX(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        BHXSpellRegistry.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
