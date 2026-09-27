package net.offkung.bhspellsx.client;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import yesman.epicfight.api.animation.types.LayerOffAnimation;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanManager;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanPrediction;
import net.offkung.bhspellsx.network.BHXNetwork;
import net.offkung.bhspellsx.registry.BHXAnimationRegistry;
import org.lwjgl.glfw.GLFW;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;

@Mod.EventBusSubscriber(modid = "bhspellsx", value = Dist.CLIENT)
public final class JingGuangPanClient {
    private static boolean active, gliding;
    private static UUID session = new UUID(0, 0);
    private static int sentLeftMask = -1;
    private static LocalPlayerPatch installedPatch;
    private static final JingGuangPanPrediction prediction = new JingGuangPanPrediction();
    private static long clock;
    private static int boostTick = BOOST_TICKS;
    private static double boostY;
    private static final Set<Integer> fadingCandidates = new HashSet<>();
    private static net.minecraft.client.multiplayer.ClientLevel visualLevel;

    @Mod.EventBusSubscriber(modid = "bhspellsx", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                BHXNetwork.clientState = JingGuangPanClient::state;
                BHXNetwork.clientAnimation = JingGuangPanClient::animation;
                BHXNetwork.clientRejected = JingGuangPanClient::rejected;
                BHXNetwork.clientBoost = JingGuangPanClient::boost;
            });
        }
    }
    public static boolean active() { return active && Minecraft.getInstance().player != null; }
    private static void state(BHXNetwork.State packet) {
        if (!session.equals(packet.session()) || !packet.active()) {
            prediction.reset();
            boostTick = BOOST_TICKS; // Existing visual animation is deliberately left alone.
        }
        session = packet.session();
        active = packet.active();
        gliding = packet.gliding();
        sentLeftMask = -1;
        syncBindings();
    }
    public static int leftMask() {
        var keys = new net.minecraft.client.KeyMapping[] {
            com.p1nero.invincible.client.InvincibleKeyMappings.KEY1,
            com.p1nero.invincible.client.InvincibleKeyMappings.KEY2,
            com.p1nero.invincible.client.InvincibleKeyMappings.KEY3,
            com.p1nero.invincible.client.InvincibleKeyMappings.KEY4};
        int mask = 0;
        for (int i = 0; i < keys.length; i++) {
            var key = keys[i].getKey();
            if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE && key.getValue() == 0) mask |= 1 << i;
        }
        return mask;
    }
    private static void syncBindings() {
        if (active() && sentLeftMask != leftMask()) {
            sentLeftMask = leftMask();
            BHXNetwork.bindings(session, sentLeftMask);
        }
    }
    private static LocalPlayerPatch patch() {
        return EpicFightCapabilities.getEntityPatch(Minecraft.getInstance().player, LocalPlayerPatch.class);
    }
    private static void animation(BHXNetwork.Animation packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || packet.entityId() == mc.player.getId()) return;
        var patch = EpicFightCapabilities.getEntityPatch(mc.level.getEntity(packet.entityId()), LivingEntityPatch.class);
        if (patch != null) patch.getClientAnimator().getCompositeLayer(Layer.Priority.HIGHEST)
                .playAnimationInstantly(accessor(packet.left()), patch);
    }
    private static AnimationAccessor<StaticAnimation> accessor(boolean left) {
        return left ? BHXAnimationRegistry.JUDGEMENT_CUT_LEFT : BHXAnimationRegistry.JUDGEMENT_CUT;
    }
    private static void rejected(BHXNetwork.Rejected packet) {
        // An old reply must never roll back a newer prediction or another activation.
        if (!session.equals(packet.session())) return;
        prediction.reject(packet.sequence(), clock, packet.retryTicks());
        // Do not stop the animation already shown to the caster.
    }
    private static void boost(BHXNetwork.Boost packet) {
        if (!active() || !session.equals(packet.session())) return;
        if (packet.stop()) {
            boostTick = BOOST_TICKS;
            JingGuangPanManager.liftTo(Minecraft.getInstance().player, packet.anchorY());
        } else {
            boostY = packet.anchorY();
            boostTick = 0;
        }
    }
    private static void play() {
        var patch = patch();
        if (patch == null || !active() || !prediction.canFire(clock)) return;
        boolean left = prediction.fire(clock);
        patch.getClientAnimator().getCompositeLayer(Layer.Priority.HIGHEST).playAnimationInstantly(accessor(left), patch);
        BHXNetwork.attack(session, prediction.sequence(), left);
    }
    private static void click() {
        if (prediction.canFire(clock)) play();
        else prediction.buffer(clock);
    }
    /** Change only a live natural fade; no scheduled stop can overwrite a subsequent slash. */
    private static void blendLivingMotion(Minecraft mc) {
        if (visualLevel != mc.level) {
            fadingCandidates.clear();
            visualLevel = mc.level;
        }
        Set<Integer> live = new HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) {
            var patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
            if (patch == null) continue;
            var player = patch.getClientAnimator().getCompositeLayer(Layer.Priority.HIGHEST).animationPlayer;
            var real = player.getRealAnimation();
            if (real == BHXAnimationRegistry.JUDGEMENT_CUT || real == BHXAnimationRegistry.JUDGEMENT_CUT_LEFT) {
                live.add(entity.getId());
            } else if (fadingCandidates.contains(entity.getId()) && player.getAnimation() instanceof LayerOffAnimation off) {
                // LayerOffAnimation blends to the CURRENT composed lower layers (flight/idle).
                // EF resolves LayerOff's real animation to EMPTY, so its clock runs at 1x.
                // The player ends on > totalTime; nextDown makes it finish on tick four.
                off.setTotalTime(Math.nextDown(BLEND_OUT_TICKS / 20.0F));
            }
        }
        fadingCandidates.clear();
        fadingCandidates.addAll(live);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.screen != null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (event.getAction() == GLFW.GLFW_PRESS) click();
        event.setCanceled(true); // press and release; no vanilla/EF/Invincible click queue.
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active() && event.isAttack()) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            active = gliding = false;
            installedPatch = null;
            prediction.reset();
            boostTick = BOOST_TICKS;
            fadingCandidates.clear();
            return;
        }
        if (mc.isPaused()) return;
        if (gliding) {
            if (JingGuangPanManager.endGlide(mc.player)) { gliding = false; mc.player.fallDistance = 0; }
            else JingGuangPanManager.clampGlide(mc.player);
        }
        if (event.phase != TickEvent.Phase.END) return;
        clock++;
        blendLivingMotion(mc);
        if (active() && boostTick < BOOST_TICKS) {
            if (!JingGuangPanManager.liftTo(mc.player, boostY + boostOffset(++boostTick))) boostTick = BOOST_TICKS;
        }
        var patch = patch();
        if (patch != null && installedPatch != patch) {
            installedPatch = patch;
            JingGuangPanManager.installAttackListener(patch, JingGuangPanClient::active);
        }
        if (!active()) return;
        syncBindings();
        while (mc.options.keyAttack.consumeClick()) {}
        mc.options.keyAttack.setDown(false);
        if (mc.screen != null) prediction.clearBuffer();
        if (prediction.bufferReady(clock)) play();
    }
    private JingGuangPanClient() {}
}
