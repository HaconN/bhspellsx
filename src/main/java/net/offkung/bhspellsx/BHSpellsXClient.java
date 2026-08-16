package net.offkung.bhspellsx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only bootstrap hook. Empty in Phase 0 — no VFX yet (see task spec). Reserved for
 * particle factory / renderer registration in later phases; not part of the merge (this
 * whole bhspellsx package is deleted at merge time, see MERGE.md).
 */
@Mod.EventBusSubscriber(modid = BHSpellsX.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BHSpellsXClient {
}
