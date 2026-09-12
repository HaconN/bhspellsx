package net.offkung.bhspellsx.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;

/**
 * The angular "crystal debris" half of the shard+mote pair (see AmethystDecreeCasterRingEntity/
 * AmethystDecreeTargetCrystalEntity for where the two are always spawned together). Unlike
 * EmbraceMoteParticle (rises, soft glow), this tumbles outward and falls — reads as chipped-off
 * crystal fragments rather than a sparkle. Modelled on the same EmbraceMoteParticle/
 * GoldSparkleParticle base (hasPhysics=false, full-bright).
 */
public class AmethystShardParticle extends TextureSheetParticle {
    private static final float BASE_QUAD_SIZE = 0.09f;
    private static final int MIN_LIFETIME_TICKS = 12;
    private static final int LIFETIME_VARIANCE_TICKS = 10;
    private static final float GRAVITY = 0.04f;
    private static final float OUTWARD_SPEED = 0.05f;
    private static final float POP_SPEED = 0.06f;

    protected AmethystShardParticle(ClientLevel level, double x, double y, double z,
                                     double xd, double yd, double zd, float r, float g, float b, SpriteSet sprites) {
        super(level, x, y, z);
        this.setColor(r, g, b);
        this.hasPhysics = false;
        this.quadSize = BASE_QUAD_SIZE * (0.75f + this.random.nextFloat() * 0.5f);
        this.lifetime = MIN_LIFETIME_TICKS + this.random.nextInt(LIFETIME_VARIANCE_TICKS);
        this.gravity = GRAVITY;
        // Outward direction is carried in via xd/zd (caller passes a normalized-ish push vector);
        // a small extra pop and jitter keeps individual shards from looking identical.
        this.xd = xd * OUTWARD_SPEED + (this.random.nextFloat() - 0.5f) * 0.02f;
        this.yd = yd * POP_SPEED + this.random.nextFloat() * 0.02f;
        this.zd = zd * OUTWARD_SPEED + (this.random.nextFloat() - 0.5f) * 0.02f;
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

    public static class Provider implements ParticleProvider<AmethystShardParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(AmethystShardParticleOption options, ClientLevel level,
                                        double x, double y, double z, double xd, double yd, double zd) {
            return new AmethystShardParticle(level, x, y, z, xd, yd, zd,
                    options.getColor().x(), options.getColor().y(), options.getColor().z(), sprites);
        }
    }
}
