package net.offkung.bhspellsx.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;

/**
 * Registered manually via MinecraftForge.EVENT_BUS.register(...) from the bootstrap's main mod
 * class, same as CrystalHydroDomeEvents.
 * <p>
 * Apoli's toggle state and the caster's scoreboard tag both survive death/logout/dimension
 * change, but the entities do not. Stripping the tag on those events lets Apoli's sync power
 * see "toggle on, tag missing" and switch the toggle off, so a stale toggle can't keep draining
 * mana with no lock behind it. The entities end themselves on the same conditions independently.
 */
public class XianSheHuanYingEvents {
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        strip(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        strip(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        strip(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        strip(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        strip(event.getEntity());
    }

    private static void strip(LivingEntity entity) {
        if (entity instanceof Player && !entity.level().isClientSide()) {
            entity.removeTag(XianSheHuanYingConstants.OWNER_TAG);
        }
    }
}
