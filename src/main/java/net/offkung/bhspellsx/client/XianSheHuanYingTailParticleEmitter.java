package net.offkung.bhspellsx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client-only home for Xian She Huan Ying's tail-smoke particle spawn. Pulled out of
 * XianSheHuanYingUserEntity (a common-side class — it ticks on the dedicated server too) for
 * exactly the same reason XianSheHuanYingEyeSound was split out of XianSheHuanYingTargetEntity:
 * see that class's javadoc. The entity computes everything common-side (the snake's own world
 * center, via the SHARED XianSheHuanYingSnakePose formula, and the tail-path pixel point already
 * converted to a local x/y offset with its jitter applied); this class does the one thing that
 * must be client-only — reading the LOCAL VIEWER's own camera orientation (each caster's snake
 * faces THEIR OWN viewer's camera, per spec, so this can only be answered on that viewer's own
 * client) — and then spawns the actual particle.
 */
public final class XianSheHuanYingTailParticleEmitter {
    private XianSheHuanYingTailParticleEmitter() {
    }

    /**
     * Rotates the given LOCAL (x, y, {@link XianSheHuanYingConstants#TAIL_PARTICLE_DEPTH_OFFSET})
     * offset — in the snake quad's own billboard space, exactly as {@code renderQuad} interprets
     * its vertices (local +Z = toward the viewer, confirmed from that method's own javadoc; same
     * axis and direction the tail-cloud quads' now-removed depth offset used) — by this client's
     * current camera orientation (the same quaternion the renderer applies via
     * {@code cameraOrientation()}), adds it to the given world-space center, and spawns one
     * {@link XianSheHuanYingConstants#TAIL_PARTICLE} at the result with a slow upward + small
     * random outward drift. The +Z nudge pulls every spawned particle off the snake quad's own
     * plane (avoiding billboard-vs-billboard z-fighting) and in FRONT of it, so the tail
     * texture's own partial-alpha depth writes near the fade can't clip a particle that should be
     * reads as still just above the snake's surface.
     */
    public static void spawnAtLocalOffset(Level level, double centerX, double centerY, double centerZ,
                                          float localX, float localY) {
        Quaternionf cameraOrientation = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        Vector3f local = new Vector3f(localX, localY, XianSheHuanYingConstants.TAIL_PARTICLE_DEPTH_OFFSET);
        local.rotate(cameraOrientation);

        double x = centerX + local.x();
        double y = centerY + local.y();
        double z = centerZ + local.z();

        ResourceLocation particleId = ResourceLocation.tryParse(XianSheHuanYingConstants.TAIL_PARTICLE);
        ParticleType<?> type = particleId != null ? ForgeRegistries.PARTICLE_TYPES.getValue(particleId) : null;
        if (!(type instanceof ParticleOptions options)) {
            return;
        }

        RandomSource random = level.getRandom();
        double driftX = (random.nextFloat() - 0.5f) * 2.0 * XianSheHuanYingConstants.TAIL_PARTICLE_DRIFT_SPEED;
        double driftZ = (random.nextFloat() - 0.5f) * 2.0 * XianSheHuanYingConstants.TAIL_PARTICLE_DRIFT_SPEED;
        level.addParticle(options, x, y, z, driftX, XianSheHuanYingConstants.TAIL_PARTICLE_RISE_SPEED, driftZ);
    }
}
