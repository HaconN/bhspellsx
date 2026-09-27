package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.network.BHXNetwork;
import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;

/** Server-only continuously swept crescent. Client presentation has no damage authority. */
public final class JingGuangPanWave {
    private static final List<JingGuangPanWave> WAVES = new ArrayList<>();
    public static void spawn(ServerPlayer owner, boolean left) { WAVES.add(new JingGuangPanWave(owner, left)); JingGuangPanSounds.wave(owner); }
    public static void tickAll() { WAVES.removeIf(JingGuangPanWave::tick); }
    public static void clearAll() { WAVES.clear(); }
    private final UUID id = UUID.randomUUID();
    private final ServerPlayer owner;
    private final ServerLevel level;
    private final Vec3 origin, direction;
    private final float yaw;
    private final double rollDegrees;
    private final boolean left;
    private final JingGuangPanWaveShape shape;
    private double travelled;
    private JingGuangPanWave(ServerPlayer owner, boolean left) {
        this.owner=owner; this.left=left; level=owner.serverLevel();
        origin=owner.getEyePosition(); direction=owner.getLookAngle().normalize(); yaw=owner.getYRot();
        // Sample once on the server; collision and every client consume this exact angle.
        rollDegrees=(left ? WAVE_LEFT_ROLL : WAVE_RIGHT_ROLL)
                +(owner.getRandom().nextDouble()*2-1)*WAVE_ROLL_JITTER_DEGREES;
        shape=new JingGuangPanWaveShape(direction,yaw,rollDegrees);
    }
    public boolean tick() {
        if (owner.isRemoved() || owner.level()!=level) return true;
        // The convex leading edge is thickness/2 ahead of the anchor; range is measured there.
        double remaining=RANGE-WAVE_THICKNESS/2-travelled;
        double step=Math.min(WAVE_SPEED,remaining);
        Vec3 start=origin.add(direction.scale(travelled)), delta=direction.scale(step);
        AABB sweep=shape.sweptBounds(start,delta);
        double first=Double.POSITIVE_INFINITY;
        LivingEntity target=null;
        for (var block : level.getBlockCollisions(owner,sweep)) {
            for (AABB bounds : block.toAabbs()) first=Math.min(first,shape.hit(bounds,start,delta));
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,sweep,
                e -> e!=owner && e.isAlive() && !e.isSpectator())) {
            double hit=shape.hit(entity.getBoundingBox(),start,delta);
            if (hit<first-1.0E-9) { first=hit; target=entity; } // Blocks win ties.
        }
        boolean contact=Double.isFinite(first);
        travelled+=step*(contact ? first : 1);
        boolean done=contact || remaining-step<1.0E-7;
        // At most three cumulative path updates per ordinary wave. No packets per spark.
        BHXNetwork.wave(owner,new BHXNetwork.WavePath(id,level.dimension().location(),origin,direction,yaw,left,rollDegrees,travelled,done));
        if (target!=null) {
            JingGuangPanSounds.hit(level,origin.add(direction.scale(travelled)));
            var type=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(
                    ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse(DAMAGE_TYPE)));
            target.hurt(new DamageSource(type,owner,owner),DAMAGE);
        }
        return done;
    }
}
