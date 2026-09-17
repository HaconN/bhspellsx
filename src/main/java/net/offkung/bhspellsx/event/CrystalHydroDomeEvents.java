package net.offkung.bhspellsx.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeAoe;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeConstants;

/**
 * Registered manually via MinecraftForge.EVENT_BUS.register(CrystalHydroDomeEvents.class) from
 * the bootstrap's main mod class, same as EmbracingBosomEvents — no modid baked in, so this
 * copies straight into bhspells at merge time.
 * <p>
 * Two damage handlers, split by where the attacker is relative to the dome:
 * <p>
 * A) LivingAttackEvent (LOW): attacker is a LivingEntity outside the dome. Cancels the hit
 * entirely — the victim takes nothing, the dome absorbs the full (capped) amount, and Counters
 * on what it actually absorbed. Fires before vanilla's own i-frame rejection (LivingEntity.hurt()
 * checks invulnerableTime/lastHurt only after this event), so this replicates that rejection
 * first — see onLivingAttack's comment for the one thing that couldn't be replicated exactly.
 * <p>
 * B) LivingDamageEvent (LOWEST): attacker is null, non-living, or inside the dome. No cancel —
 * splits the already-final (post-armor/enchant) amount 30/70 between the dome and the victim.
 * No Counter. LOWEST so this reads the amount after every other damage-modifying handler
 * (embracing_bosom's 0.8x, Iron's Oakskin/SpiderAspect/Blight, etc.) has already run — those
 * compose multiplicatively with the 0.30/0.70 split regardless of order, per the recon.
 * <p>
 * Deliberately no dedupe across either event: one AoE hit landing on N victims inside the dome
 * costs the dome N times and Counters N times. Per spec, this is intentional, not a bug.
 */
public class CrystalHydroDomeEvents {
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        // Unlike LivingDamageEvent (which only fires from actuallyHurt(), itself only reached
        // after LivingEntity.hurt()'s own isClientSide bail-out — confirmed by decompile, so it
        // can never reach onLivingDamage below), LivingAttackEvent fires unconditionally, on both
        // sides, for both mobs (LivingEntity.hurt() posts it before that bail-out) and players
        // (Player.hurt() posts it via ForgeHooks.onPlayerAttack as its very first line, with no
        // client check at all). Without this guard, a client-side prediction call to hurt() would
        // cancel the event, run gateOutsideHit()/absorb()/applyCounter() against the client's own
        // (separate) copy of the dome entity, and deal real Counter damage from client code.
        if (victim.level().isClientSide()) {
            return;
        }
        if (victim instanceof ArmorStand) {
            return;
        }
        CrystalHydroDomeAoe dome = CrystalHydroDomeAoe.findDomeContaining(victim);
        if (dome == null) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (event.getAmount() <= 0) {
            return;
        }

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || dome.isInside(attacker.position())) {
            // Not an "outside attacker" hit — left for the LivingDamageEvent handler (B) instead.
            return;
        }

        // Always cancel — the victim never takes outside damage, full stop. The dome tracks its
        // own per-victim i-frame state instead of relying on the victim's real vanilla
        // invulnerableTime/lastHurt: since we always cancel here, vanilla's own hurt() never runs
        // for this hit, so it never sets those fields — a real per-tick source (a beam/ray spell,
        // for instance) would otherwise drain the dome fresh every single tick. See
        // CrystalHydroDomeAoe.gateOutsideHit for the state machine and how it honors a spell's own
        // SpellDamageSource.setIFrames() override instead of always using the 10-tick default.
        event.setCanceled(true);
        double gated = dome.gateOutsideHit(victim, source, event.getAmount());
        double absorbed = dome.absorb(gated);
        dome.applyCounter(attacker, absorbed);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ArmorStand) {
            return;
        }
        CrystalHydroDomeAoe dome = CrystalHydroDomeAoe.findDomeContaining(victim);
        if (dome == null) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0) {
            return;
        }

        Entity attackerEntity = source.getEntity();
        boolean attackerOutside = attackerEntity instanceof LivingEntity attacker && !dome.isInside(attacker.position());
        if (attackerOutside) {
            // Already handled (and, if it landed, already canceled) by onLivingAttack above.
            return;
        }

        double domeShare = amount * CrystalHydroDomeConstants.INSIDE_DOME_SHARE;
        dome.absorb(domeShare);
        event.setAmount((float) (amount * (1.0 - CrystalHydroDomeConstants.INSIDE_DOME_SHARE)));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CrystalHydroDomeAoe.endActiveDomeFor(player);
        }
    }

    /** Crash safety: if the server went down mid-dome with no logout event, the dome entity is
     *  simply gone on restart (never persisted — see CrystalHydroDomeAoe.shouldBeSaved), but the
     *  scoreboard tag lives in the player's own saved data and would otherwise linger forever. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().removeTag(CrystalHydroDomeConstants.DOME_TAG);
    }
}
