package net.offkung.bhspellsx.client.renderer.jing_guang_pan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanWaveShape;
import net.offkung.bhspellsx.network.BHXNetwork;
import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;
import static net.offkung.bhspellsx.client.renderer.jing_guang_pan.JingGuangPanVfxConstants.*;

@Mod.EventBusSubscriber(modid="bhspellsx", value=Dist.CLIENT)
public final class JingGuangPanVfx {
    private static final Map<UUID,Visual> WAVES=new LinkedHashMap<>();
    private static ClientLevel world;
    static final class Visual {
        final BHXNetwork.WavePath source;
        final JingGuangPanWaveShape shape;
        double confirmed, previous, distance;
        int age, fade, previousFade;
        boolean done;
        Visual(BHXNetwork.WavePath packet) { source=packet; shape=new JingGuangPanWaveShape(packet.direction(),packet.yaw(),packet.left()); }
        boolean tick() {
            previous=distance; previousFade=fade;
            distance=Math.min(confirmed,distance+WAVE_SPEED);
            if (done && previous>=confirmed-1.0E-7) fade++;
            return ++age>STALE_TICKS || fade>FADE_TICKS;
        }
    }
    private static void checkWorld() {
        var current=Minecraft.getInstance().level;
        if (current!=world) { WAVES.clear(); world=current; }
    }
    public static void receive(BHXNetwork.WavePath packet) {
        checkWorld();
        if (world==null || !world.dimension().location().equals(packet.dimension())) return;
        if (!WAVES.containsKey(packet.id()) && WAVES.size()>=MAX_WAVES) WAVES.remove(WAVES.keySet().iterator().next());
        Visual visual=WAVES.computeIfAbsent(packet.id(),ignored -> new Visual(packet));
        visual.confirmed=Math.max(visual.confirmed,packet.distance());
        visual.done|=packet.done();
        // Do not discard a short wave when spawn/end arrive in one frame: replay the confirmed
        // path locally from zero, then fade. This never extends the authoritative damage lifetime.
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        checkWorld();
        if (Minecraft.getInstance().isPaused()) return;
        WAVES.values().removeIf(Visual::tick);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        checkWorld();
        if (world!=null) JingGuangPanWaveRenderer.render(event,WAVES.values());
    }
    private JingGuangPanVfx() {}
}
