package net.offkung.bhspellsx.client.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.offkung.bhspellsx.registry.BHXParticleRegistry;
import org.joml.Vector3f;

import java.util.Locale;

/** Tint-only ParticleOptions for AmethystShardParticle — mirrors EmbraceMoteParticleOption. */
public class AmethystShardParticleOption implements ParticleOptions {
    public static final ParticleOptions.Deserializer<AmethystShardParticleOption> DESERIALIZER =
            new ParticleOptions.Deserializer<>() {
                @Override
                public AmethystShardParticleOption fromCommand(ParticleType<AmethystShardParticleOption> type, StringReader reader) throws CommandSyntaxException {
                    reader.expect(' ');
                    float r = reader.readFloat();
                    reader.expect(' ');
                    float g = reader.readFloat();
                    reader.expect(' ');
                    float b = reader.readFloat();
                    return new AmethystShardParticleOption(new Vector3f(r, g, b));
                }

                @Override
                public AmethystShardParticleOption fromNetwork(ParticleType<AmethystShardParticleOption> type, FriendlyByteBuf buf) {
                    return new AmethystShardParticleOption(buf.readVector3f());
                }
            };

    public static final Codec<AmethystShardParticleOption> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(AmethystShardParticleOption::getColor)
    ).apply(instance, AmethystShardParticleOption::new));

    private final Vector3f color;

    public AmethystShardParticleOption(Vector3f color) {
        this.color = color;
    }

    public Vector3f getColor() {
        return color;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVector3f(color);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f",
                BuiltInRegistries.PARTICLE_TYPE.getKey(getType()), color.x(), color.y(), color.z());
    }

    @Override
    public ParticleType<AmethystShardParticleOption> getType() {
        return BHXParticleRegistry.AMETHYST_SHARD.get();
    }
}
