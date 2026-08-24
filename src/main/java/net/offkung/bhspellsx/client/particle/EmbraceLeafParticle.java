package net.offkung.bhspellsx.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * A dry leaf that drifts and tumbles slowly upward/outward for the whole EmbracingBosomAoe
 * lifetime — modelled on bhspells' own OakLeafParticle (constructor/tick/getRenderType shape,
 * the small per-tick horizontal sway, the friction/lifetime jitter), but with a slower rise and
 * a longer lifetime to match "hang for a while before fading" (reference image 4), and tinted
 * per EmbraceLeafParticleOption instead of a fixed colour.
 */
public class EmbraceLeafParticle extends TextureSheetParticle {
    private static final float BASE_QUAD_SIZE = 0.35f;
    private static final int MIN_LIFETIME_TICKS = 90;
    private static final int LIFETIME_VARIANCE_TICKS = 40;
    // Negative gravity: drifts upward rather than falling, per spec.
    private static final float GRAVITY = -0.004f;
    private static final float BASE_RISE_SPEED = 0.006f;
    private static final float RISE_VARIANCE = 0.006f;
    private static final float HORIZONTAL_KICK = 0.01f;
    private static final float SWAY_PER_TICK = 0.0015f;
    private static final float TUMBLE_SPEED = 0.05f;

    protected EmbraceLeafParticle(ClientLevel level, double x, double y, double z, float r, float g, float b, SpriteSet sprites) {
        super(level, x, y, z);
        this.setColor(r, g, b);
        this.quadSize = BASE_QUAD_SIZE * (0.8f + this.random.nextFloat() * 0.4f);
        this.lifetime = MIN_LIFETIME_TICKS + this.random.nextInt(LIFETIME_VARIANCE_TICKS);
        this.gravity = GRAVITY;
        this.friction -= this.random.nextFloat() * 0.02f;
        this.xd = (this.random.nextFloat() - 0.5f) * HORIZONTAL_KICK;
        this.yd = BASE_RISE_SPEED + this.random.nextFloat() * RISE_VARIANCE;
        this.zd = (this.random.nextFloat() - 0.5f) * HORIZONTAL_KICK;
        this.roll = this.random.nextFloat() * ((float) Math.PI * 2f);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.xd += (this.random.nextFloat() / 500.0f) * (this.random.nextBoolean() ? 1 : -1);
        this.zd += (this.random.nextFloat() / 500.0f) * (this.random.nextBoolean() ? 1 : -1);
        this.oRoll = this.roll;
        this.roll += TUMBLE_SPEED;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<EmbraceLeafParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(EmbraceLeafParticleOption options, ClientLevel level,
                                        double x, double y, double z, double xd, double yd, double zd) {
            return new EmbraceLeafParticle(level, x, y, z,
                    options.getColor().x(), options.getColor().y(), options.getColor().z(), sprites);
        }
    }
}
