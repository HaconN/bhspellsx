package net.offkung.bhspellsx;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.offkung.bhspellsx.client.particle.EmbraceLeafParticle;
import net.offkung.bhspellsx.client.particle.EmbraceMoteParticle;
import net.offkung.bhspellsx.client.renderer.EmbracingBosomRingRenderer;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;
import net.offkung.bhspellsx.registry.BHXParticleRegistry;

/**
 * Client-only bootstrap hook. Not part of the merge (this whole bhspellsx package is deleted at
 * merge time, see MERGE.md).
 */
@Mod.EventBusSubscriber(modid = BHSpellsX.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BHSpellsXClient {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BHXEntityRegistry.EMBRACING_BOSOM_AOE.get(), EmbracingBosomRingRenderer::new);
        // Phase 1: no custom VFX yet, so a no-render renderer — same as bhspells' own house
        // pattern for its invisible AoE entities (DARK_RAIN_FALL, PURPLE_WAVE_PROJECTILE, etc.
        // all use vanilla NoopRenderer, not a hand-rolled one). Still required to register
        // *something* — Forge crashes on the client the moment an instance of an unrendered
        // custom EntityType spawns without one.
        event.registerEntityRenderer(BHXEntityRegistry.AMETHYST_DECREE_AOE.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BHXParticleRegistry.EMBRACE_LEAF.get(), EmbraceLeafParticle.Provider::new);
        event.registerSpriteSet(BHXParticleRegistry.EMBRACE_MOTE.get(), EmbraceMoteParticle.Provider::new);
    }
}
