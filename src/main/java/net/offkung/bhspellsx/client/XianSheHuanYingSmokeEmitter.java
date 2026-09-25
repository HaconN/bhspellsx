package net.offkung.bhspellsx.client;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import net.offkung.bhspellsx.registry.BHXParticleRegistry;

/**
 * Client-only home for Xian She Huan Ying's smoke particle spawning. Split out of
 * XianSheHuanYingUserEntity (a common-side class) the same way {@code client.sound}'s
 * {@code XianSheHuanYingEyeSound} is — see that class's javadoc for why, even though this one
 * doesn't actually touch {@code net.minecraft.client.*} types (it only needs {@link Level} and
 * the registered {@link net.minecraft.core.particles.SimpleParticleType}, both common), it's kept
 * under {@code client/} per spec ("ผ่านคลาสใต้ client/ (แบบเดียวกับเสียง entity)") for the same
 * organizational reason.
 * <p>
 * Round 15.6 ("ปล่อยควันแบบระเบิดควัน"): spawn positions are no longer uniform across the spawn
 * circle — {@link #rollOffset} pulls each roll toward the center by
 * {@link XianSheHuanYingConstants#SMOKE_CLUMP} and floors the spawn height by
 * {@link XianSheHuanYingConstants#SMOKE_LIFT} (see that method's own javadoc for the exact
 * formulas) — and {@link #spawnBurst} fires {@link XianSheHuanYingConstants#SMOKE_BURST} puffs at
 * once, the first tick the emitter runs for a given entity, allowing each one up to
 * {@link #BURST_MAX_ATTEMPTS} re-rolls to actually land inside the circle (unlike
 * {@link #trySpawn}'s normal one-shot-then-skip behavior), so the burst reliably gets its full
 * count instead of losing ~21% of attempts to the circle-rejection the square roll allows.
 */
public final class XianSheHuanYingSmokeEmitter {
    /** Cap on re-rolls per burst puff — spec: "ไม่เกิน 10 ครั้ง". */
    private static final int BURST_MAX_ATTEMPTS = 10;

    private XianSheHuanYingSmokeEmitter() {
    }

    /** One spawn attempt around (ownerX, ownerZ) at the owner's feet (ownerY). If the roll falls
     *  outside the spawn circle, skips this attempt entirely rather than re-rolling — per spec
     *  ("ถ้า ... ให้ข้ามครั้งนั้นไปเลย ห้ามสุ่มใหม่"). Used by the normal per-tick spawn rate. */
    public static void trySpawn(Level level, double ownerX, double ownerY, double ownerZ, RandomSource random) {
        double[] offset = rollOffset(random);
        if (offset == null) {
            return;
        }
        spawnAt(level, ownerX + offset[0], ownerY + rollHeight(random), ownerZ + offset[1]);
    }

    /** Fires {@link XianSheHuanYingConstants#SMOKE_BURST} puffs at once ("ปุ้งตอนเริ่ม") — unlike
     *  {@link #trySpawn}, a puff that rolls outside the circle gets re-rolled (up to
     *  {@link #BURST_MAX_ATTEMPTS} times) instead of being skipped, so the burst actually spawns
     *  its full count rather than silently losing some to the circle rejection. Caller is
     *  responsible for only calling this once per entity. */
    public static void spawnBurst(Level level, double ownerX, double ownerY, double ownerZ, RandomSource random) {
        for (int i = 0; i < XianSheHuanYingConstants.SMOKE_BURST; i++) {
            double[] offset = null;
            for (int attempt = 0; attempt < BURST_MAX_ATTEMPTS && offset == null; attempt++) {
                offset = rollOffset(random);
            }
            if (offset == null) {
                continue;
            }
            spawnAt(level, ownerX + offset[0], ownerY + rollHeight(random), ownerZ + offset[1]);
        }
    }

    /** Extra VFX A1 (see {@link XianSheHuanYingConstants#XSHY_EXTRA_VFX_MODE}): one vanilla
     *  {@code minecraft:cloud} puff, uniform over the spawn disc (no clump), with a small random
     *  velocity per axis. cloud's constructor ADDS the passed velocity on top of its own scatter
     *  (decompiled), so the passed value does take effect. */
    public static void spawnMistCloud(Level level, double ownerX, double ownerY, double ownerZ, RandomSource random) {
        double[] offset = rollDiscOffset(random);
        double v = XianSheHuanYingConstants.XSHY_MIST_CLOUD_VELOCITY;
        level.addParticle(ParticleTypes.CLOUD,
                ownerX + offset[0],
                ownerY + XianSheHuanYingConstants.XSHY_MIST_MIN_Y
                        + random.nextFloat() * XianSheHuanYingConstants.XSHY_MIST_CLOUD_HEIGHT,
                ownerZ + offset[1],
                (random.nextFloat() * 2.0f - 1.0f) * v,
                (random.nextFloat() * 2.0f - 1.0f) * v,
                (random.nextFloat() * 2.0f - 1.0f) * v);
    }

    /** Extra VFX A2: one vanilla {@code minecraft:white_ash} flake, uniform over the spawn disc.
     *  Velocity passed as 0 — white_ash keeps its own random drift (its constructor scales a
     *  random scatter, then adds the passed velocity). */
    public static void spawnMistAsh(Level level, double ownerX, double ownerY, double ownerZ, RandomSource random) {
        double[] offset = rollDiscOffset(random);
        level.addParticle(ParticleTypes.WHITE_ASH,
                ownerX + offset[0],
                ownerY + XianSheHuanYingConstants.XSHY_MIST_MIN_Y
                        + random.nextFloat() * XianSheHuanYingConstants.SMOKE_HEIGHT,
                ownerZ + offset[1],
                0.0, 0.0, 0.0);
    }

    /** Extra VFX B: {@link XianSheHuanYingConstants#XSHY_FLASH_COUNT} particles of
     *  {@link XianSheHuanYingConstants#XSHY_FLASH_PARTICLE} at the owner, direction uniform over
     *  the sphere, speed {@code XSHY_FLASH_SPEED * random(0.5, 1.0)}. Caller fires this once, with
     *  the main burst. Silently does nothing if the id isn't a registered simple particle. */
    public static void spawnFlashBurst(Level level, double ownerX, double ownerY, double ownerZ, RandomSource random) {
        ResourceLocation id = ResourceLocation.tryParse(XianSheHuanYingConstants.XSHY_FLASH_PARTICLE);
        ParticleType<?> type = id != null ? ForgeRegistries.PARTICLE_TYPES.getValue(id) : null;
        if (!(type instanceof ParticleOptions options)) {
            return;
        }
        double y = ownerY + XianSheHuanYingConstants.XSHY_FLASH_HEIGHT;
        for (int i = 0; i < XianSheHuanYingConstants.XSHY_FLASH_COUNT; i++) {
            double dirY = random.nextFloat() * 2.0 - 1.0;
            double phi = random.nextFloat() * Mth.TWO_PI;
            double ring = Math.sqrt(1.0 - dirY * dirY);
            double speed = XianSheHuanYingConstants.XSHY_FLASH_SPEED * Mth.lerp(random.nextFloat(), 0.5f, 1.0f);
            level.addParticle(options, ownerX, y, ownerZ,
                    Math.cos(phi) * ring * speed, dirY * speed, Math.sin(phi) * ring * speed);
        }
    }

    /** Uniform point in the disc of radius SMOKE_RADIUS (sqrt-distributed radius, no clump). */
    private static double[] rollDiscOffset(RandomSource random) {
        double angle = random.nextFloat() * Mth.TWO_PI;
        double r = XianSheHuanYingConstants.SMOKE_RADIUS * Math.sqrt(random.nextFloat());
        return new double[]{Math.cos(angle) * r, Math.sin(angle) * r};
    }

    /** Rolls x/z uniformly in the square [-R, R]; returns {@code null} if that roll falls outside
     *  the inscribed circle of radius R (caller decides skip vs. re-roll). Otherwise pulls the
     *  point toward the center: {@code k = (d/R)^SMOKE_CLUMP}, {@code x *= k}, {@code z *= k} — a
     *  clump factor below 1 biases density toward the middle of the circle instead of uniform. */
    private static double[] rollOffset(RandomSource random) {
        float radius = XianSheHuanYingConstants.SMOKE_RADIUS;
        double x = (random.nextFloat() * 2.0f - 1.0f) * radius;
        double z = (random.nextFloat() * 2.0f - 1.0f) * radius;
        double distSq = x * x + z * z;
        if (distSq > (double) radius * radius) {
            return null;
        }
        double dist = Math.sqrt(distSq);
        double k = Math.pow(dist / radius, XianSheHuanYingConstants.SMOKE_CLUMP);
        return new double[]{x * k, z * k};
    }

    /** {@code minY = SMOKE_SIZE * 0.5 * SMOKE_LIFT} above the owner's feet (keeps puffs from
     *  visually sinking into the ground), then {@code random(0, max(0.01, SMOKE_HEIGHT - minY))}
     *  on top of that. */
    private static double rollHeight(RandomSource random) {
        double minY = XianSheHuanYingConstants.SMOKE_SIZE * 0.5 * XianSheHuanYingConstants.SMOKE_LIFT;
        double range = Math.max(0.01, XianSheHuanYingConstants.SMOKE_HEIGHT - minY);
        return minY + random.nextFloat() * range;
    }

    private static void spawnAt(Level level, double x, double y, double z) {
        level.addParticle(BHXParticleRegistry.XSHY_SMOKE.get(), x, y, z, 0.0, 0.0, 0.0);
    }
}
