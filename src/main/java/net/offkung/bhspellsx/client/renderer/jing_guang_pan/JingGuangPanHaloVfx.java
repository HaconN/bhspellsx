package net.offkung.bhspellsx.client.renderer.jing_guang_pan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.offkung.bhspellsx.network.BHXNetwork;
import static net.offkung.bhspellsx.client.renderer.jing_guang_pan.JingGuangPanHaloConstants.*;

@Mod.EventBusSubscriber(modid="bhspellsx", value=Dist.CLIENT)
public final class JingGuangPanHaloVfx {
    private static final Map<UUID,Visual> HALOS=new HashMap<>();
    private static ClientLevel world;
    private static long clock;
    static final class Visual {
        long revision, received;
        double age, closeAlpha;
        boolean active, seen;
        double age(float partial) { return age+clock-received+partial; }
        float alpha(float partial) {
            return (float)(active ? smooth(age(partial)/OPEN_TICKS)
                    : closeAlpha*(1-smooth((clock-received+partial)/CLOSE_TICKS)));
        }
    }
    private static void checkWorld() {
        var current=Minecraft.getInstance().level;
        if (current!=world) { HALOS.clear(); world=current; clock=0; }
    }
    public static void receive(BHXNetwork.Halo packet) {
        checkWorld();
        if (world==null || !world.dimension().location().equals(packet.dimension())) return;
        Visual old=HALOS.get(packet.player());
        if (old!=null && packet.revision()<old.revision) return;
        if (packet.clear()) { HALOS.remove(packet.player()); return; }
        if (!packet.active() && old==null) return;
        Visual v=new Visual(); v.revision=packet.revision(); v.received=clock;
        v.age=packet.age(); v.active=packet.active();
        v.closeAlpha=old==null ? 0 : old.alpha(0); v.seen=old!=null && old.seen;
        HALOS.put(packet.player(),v);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        checkWorld();
        if (world==null || Minecraft.getInstance().isPaused()) return;
        clock++;
        HALOS.entrySet().removeIf(entry -> {
            var v=entry.getValue(); var player=world.getPlayerByUUID(entry.getKey());
            if (player!=null) { v.seen=true; if (!player.isAlive() || player.isRemoved()) return true; }
            else if (v.seen || clock-v.received>SPAWN_GRACE_TICKS) return true;
            return !v.active && clock-v.received>=CLOSE_TICKS;
        });
        if (clock%AURA_GOLD_INTERVAL_TICKS==0) emitAura(AURA_GOLD_SPARKLE_ID,AURA_GOLD_PER_BURST);
    }
    private static void emitAura(String particleId, int count) {
        var mc=Minecraft.getInstance();
        var id=ResourceLocation.tryParse(particleId);
        if (id==null || !(BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType particle)) return;
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        for (var entry:HALOS.entrySet()) {
            if (!entry.getValue().active) continue;
            var player=world.getPlayerByUUID(entry.getKey());
            if (player==null || !player.isAlive() || player.isRemoved() || player.isInvisible() || player.isSpectator()) continue;
            if (player==mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (player.position().distanceToSqr(camera)>RENDER_DISTANCE*RENDER_DISTANCE) continue;
            for (int i=0;i<count;i++) {
                double angle=world.random.nextDouble()*2*Math.PI;
                double radius=Math.sqrt(AURA_RADIUS_MIN*AURA_RADIUS_MIN+world.random.nextDouble()
                        *(AURA_RADIUS_MAX*AURA_RADIUS_MAX-AURA_RADIUS_MIN*AURA_RADIUS_MIN));
                double y=AURA_START_Y_MIN+world.random.nextDouble()*(AURA_START_Y_MAX-AURA_START_Y_MIN);
                world.addParticle(particle,player.getX()+Math.cos(angle)*radius,player.getY()+y,
                        player.getZ()+Math.sin(angle)*radius,Math.cos(angle)*AURA_OUTWARD_SPEED,
                        AURA_UPWARD_SPEED,Math.sin(angle)*AURA_OUTWARD_SPEED);
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        checkWorld(); if (world==null) return;
        var mc=Minecraft.getInstance();
        for (var entry:HALOS.entrySet()) {
            var player=world.getPlayerByUUID(entry.getKey());
            if (player==null || !player.isAlive() || player.isInvisible() || player.isSpectator()) continue;
            if (player==mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            JingGuangPanHaloRenderer.render(event,player,entry.getValue());
        }
    }
    private JingGuangPanHaloVfx() {}
}
