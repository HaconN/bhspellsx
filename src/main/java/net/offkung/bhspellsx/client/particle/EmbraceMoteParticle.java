package net.offkung.bhspellsx.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;

/**
 * A small warm spark that rises quickly and burns out fast — modelled on bhspells' own
 * GoldSparkleParticle (hasPhysics=false so it passes through blocks, small quadSize, full-bright
 * getLightColor), but single-texture (no animated sprite cycling — spec asked to keep the art
 * simple) and tinted per EmbraceMoteParticleOption.
 */
public class EmbraceMoteParticle extends TextureSheetParticle {
    private static final float BASE_QUAD_SIZE = 0.1f;
    private static final int MIN_LIFETIME_TICKS = 15;
    private static final int LIFETIME_VARIANCE_TICKS = 12;
    private static final float GRAVITY = -0.03f;
    private static final float BASE_RISE_SPEED = 0.03f;
    private static final float RISE_VARIANCE = 0.02f;
    private static final float HORIZONTAL_SCATTER = 0.015f;

    protected EmbraceMoteParticle(ClientLevel level, double x, double y, double z, float r, float g, float b, SpriteSet sprites) {
        super(level, x, y, z);
        this.setColor(r, g, b);
        this.hasPhysics = false;
        this.quadSize = BASE_QUAD_SIZE * (0.8f + this.random.nextFloat() * 0.4f);
        this.lifetime = MIN_LIFETIME_TICKS + this.random.nextInt(LIFETIME_VARIANCE_TICKS);
        this.gravity = GRAVITY;
        this.xd = (this.random.nextFloat() - 0.5f) * HORIZONTAL_SCATTER;
        this.yd = BASE_RISE_SPEED + this.random.nextFloat() * RISE_VARIANCE;
        this.zd = (this.random.nextFloat() - 0.5f) * HORIZONTAL_SCATTER;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public static class Provider implements ParticleProvider<EmbraceMoteParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(EmbraceMoteParticleOption options, ClientLevel level,
                                        double x, double y, double z, double xd, double yd, double zd) {
            return new EmbraceMoteParticle(level, x, y, z,
                    options.getColor().x(), options.getColor().y(), options.getColor().z(), sprites);
        }
    }
}
