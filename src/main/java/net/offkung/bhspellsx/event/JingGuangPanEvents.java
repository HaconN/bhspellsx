package net.offkung.bhspellsx.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanManager;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanWave;

public final class JingGuangPanEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) JingGuangPanManager.tick(player);
    }
    @SubscribeEvent public static void waves(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) JingGuangPanWave.tickAll();
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && JingGuangPanManager.active(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (JingGuangPanManager.active(event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && JingGuangPanManager.protectedFromFall(player)) {
            event.setCanceled(true);
            player.fallDistance = 0;
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) JingGuangPanManager.clear(p, true); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) JingGuangPanManager.clear(p, true); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) JingGuangPanManager.clear(p, false); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) JingGuangPanManager.clear(p, true); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) JingGuangPanManager.clear(p, true); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { JingGuangPanManager.stop(); JingGuangPanWave.clearAll(); }
}
