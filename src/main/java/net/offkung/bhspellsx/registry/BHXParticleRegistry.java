package net.offkung.bhspellsx.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspellsx.client.particle.AmethystShardParticleOption;
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

    public static final RegistryObject<ParticleType<AmethystShardParticleOption>> AMETHYST_SHARD =
            PARTICLE_TYPES.register("amethyst_shard", () -> new ParticleType<>(false, AmethystShardParticleOption.DESERIALIZER) {
                @Override
                public Codec<AmethystShardParticleOption> codec() {
                    return AmethystShardParticleOption.CODEC;
                }
            });

    /** Round 15: Xian She Huan Ying's caster-side smoke. Fixed color (no runtime tint), so a
     *  plain SimpleParticleType is enough — no custom ParticleOptions/Codec needed. */
    public static final RegistryObject<SimpleParticleType> XSHY_SMOKE =
            PARTICLE_TYPES.register("xshy_smoke", () -> new SimpleParticleType(false));

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
