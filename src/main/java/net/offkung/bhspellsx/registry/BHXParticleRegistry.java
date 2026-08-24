package net.offkung.bhspellsx.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspellsx.client.particle.EmbraceLeafParticleOption;
import net.offkung.bhspellsx.client.particle.EmbraceMoteParticleOption;

public class BHXParticleRegistry {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "bhspellsx");

    public static final RegistryObject<ParticleType<EmbraceLeafParticleOption>> EMBRACE_LEAF =
            PARTICLE_TYPES.register("embrace_leaf", () -> new ParticleType<>(false, EmbraceLeafParticleOption.DESERIALIZER) {
                @Override
                public Codec<EmbraceLeafParticleOption> codec() {
                    return EmbraceLeafParticleOption.CODEC;
                }
            });

    public static final RegistryObject<ParticleType<EmbraceMoteParticleOption>> EMBRACE_MOTE =
            PARTICLE_TYPES.register("embrace_mote", () -> new ParticleType<>(false, EmbraceMoteParticleOption.DESERIALIZER) {
                @Override
                public Codec<EmbraceMoteParticleOption> codec() {
                    return EmbraceMoteParticleOption.CODEC;
                }
            });

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
