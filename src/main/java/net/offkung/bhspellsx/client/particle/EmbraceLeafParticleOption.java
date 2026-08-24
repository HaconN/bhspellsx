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

/**
 * Tint-only ParticleOptions for EmbraceLeafParticle — same shape as bhspells' own
 * ColoredCherryParticleOption (color-only network/command/codec plumbing), so the caller can
 * retune the palette from code without touching the particle or its JSON.
 */
public class EmbraceLeafParticleOption implements ParticleOptions {
    public static final ParticleOptions.Deserializer<EmbraceLeafParticleOption> DESERIALIZER =
            new ParticleOptions.Deserializer<>() {
                @Override
                public EmbraceLeafParticleOption fromCommand(ParticleType<EmbraceLeafParticleOption> type, StringReader reader) throws CommandSyntaxException {
                    reader.expect(' ');
                    float r = reader.readFloat();
                    reader.expect(' ');
                    float g = reader.readFloat();
                    reader.expect(' ');
                    float b = reader.readFloat();
                    return new EmbraceLeafParticleOption(new Vector3f(r, g, b));
                }

                @Override
                public EmbraceLeafParticleOption fromNetwork(ParticleType<EmbraceLeafParticleOption> type, FriendlyByteBuf buf) {
                    return new EmbraceLeafParticleOption(buf.readVector3f());
                }
            };

    public static final Codec<EmbraceLeafParticleOption> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(EmbraceLeafParticleOption::getColor)
    ).apply(instance, EmbraceLeafParticleOption::new));

    private final Vector3f color;

    public EmbraceLeafParticleOption(Vector3f color) {
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
    public ParticleType<EmbraceLeafParticleOption> getType() {
        return BHXParticleRegistry.EMBRACE_LEAF.get();
    }
}
