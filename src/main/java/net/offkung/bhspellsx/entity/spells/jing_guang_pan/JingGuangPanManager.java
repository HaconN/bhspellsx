package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.network.BHXNetwork;
import net.offkung.bhspellsx.registry.BHXSpellRegistry;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;
import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;

public final class JingGuangPanManager {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final UUID ATTACK_LISTENER = UUID.fromString("e55d1f44-ed95-40eb-bded-4d2f67a57ed9");
    private static final class State {
        UUID session = UUID.randomUUID();
        long haloStarted, haloRevision;
        boolean active, gliding, startFlying;
        int boost;
        double boostY;
        int leftMask;
        long lastStart = Long.MIN_VALUE / 2;
        long lastSequence = -1, lastWave = Long.MIN_VALUE / 2, pending = -1;
        BHXNetwork.Attack pendingRequest;
        PlayerPatch<?> patch;
    }
    public static boolean active(Player player) {
        return player != null && player.isAlive() && player.getTags().contains(ACTIVE_TAG);
    }
    public static boolean endGlide(Player p) {
        return p.onGround() || p.isInWaterOrBubble() || p.onClimbable();
    }
    public static void clampGlide(Player p) {
        Vec3 v = p.getDeltaMovement();
        if (v.y < -GLIDE_SPEED) p.setDeltaMovement(v.x, -GLIDE_SPEED, v.z);
        p.fallDistance = 0;
    }
    /** Absolute curve target avoids adding the lift twice when client movement packets arrive. */
    public static boolean liftTo(Player player, double targetY) {
        double dy = targetY - player.getY();
        var box = player.getBoundingBox();
        boolean clear = player.level().noCollision(player, box.expandTowards(0, dy, 0));
        if (!clear) {
            double low = 0, high = 1;
            for (int i = 0; i < 16; i++) {
                double mid = (low + high) / 2;
                if (player.level().noCollision(player, box.expandTowards(0, dy * mid, 0))) low = mid;
                else high = mid;
            }
            dy *= low;
        }
        player.setPos(player.getX(), player.getY() + dy, player.getZ());
        player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
        player.fallDistance = 0;
        return clear;
    }
    public static void installAttackListener(PlayerPatch<?> patch, java.util.function.BooleanSupplier enabled) {
        patch.getEventListener().addEventListener(PlayerEventListener.EventType.SKILL_CAST_EVENT, ATTACK_LISTENER,
                event -> {
                    if (enabled.getAsBoolean() && event.getSkillContainer().getSlot() == SkillSlots.BASIC_ATTACK) event.setCanceled(true);
                });
    }
    public static void tick(ServerPlayer player) {
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        var patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch != null && state.patch != patch) {
            state.patch = patch;
            installAttackListener(patch, () -> active(player));
        }
        boolean enabled = active(player);
        boolean changed = state.active != enabled;
        boolean activationChanged = changed;
        if (changed) {
            state.active = enabled;
            state.haloRevision = ++nextHaloRevision;
            if (enabled) state.haloStarted = player.level().getGameTime();
            BHXNetwork.halo(player, haloPacket(player, state, false));
            state.session = UUID.randomUUID();
            state.lastSequence = -1;
            state.pending = -1;
            state.pendingRequest = null;
            state.startFlying = enabled;
            state.boost = 0;
            state.boostY = player.getY();
            state.gliding = !enabled && !endGlide(player);
            if (!enabled && player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities(); // mayfly remains owned by Apoli/other ability sources.
            }
        }
        if (changed) {
            BHXNetwork.state(player, state.session, state.active, state.gliding);
            if (enabled) BHXNetwork.boost(player, state.session, state.boostY, false);
        }
        if (enabled) {
            if (state.startFlying && player.getAbilities().mayfly) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                state.startFlying = false;
            }
            if (!changed && state.boost < BOOST_TICKS) {
                double target = state.boostY + boostOffset(++state.boost);
                boolean clear = liftTo(player, target);
                if (!clear) state.boost = BOOST_TICKS;
                if (state.boost == BOOST_TICKS) {
                    // One final authoritative correction, not twelve teleport handshakes.
                    BHXNetwork.boost(player, state.session, player.getY(), true);
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                }
            }
        }
        if (state.gliding && endGlide(player)) {
            state.gliding = false;
            player.fallDistance = 0;
            changed = true;
        }
        if (state.gliding) clampGlide(player);
        if (changed && !activationChanged) BHXNetwork.state(player, state.session, state.active, state.gliding);
        long now = player.serverLevel().getGameTime();
        if (state.pending >= 0 && now >= state.pending) {
            BHXNetwork.Attack request = state.pendingRequest;
            state.pending = -1;
            state.pendingRequest = null;
            if (enabled && now - state.lastWave >= serverCutTicks()) {
                boolean cast = ((net.offkung.bhspellsx.spells.gold.JingGuangPanSpell) BHXSpellRegistry.JING_GUANG_PAN.get())
                        .castWave(player, request != null && request.left());
                if (cast) state.lastWave = now;
                else if (request != null) BHXNetwork.rejected(player, request,
                        (int)Math.max(0, serverReadyAt(state.lastStart, state.lastWave, -1) - now));
            }
        }
    }
    public static void request(ServerPlayer player, BHXNetwork.Attack packet) {
        State state = STATES.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        if (state == null || !active(player) || !state.active || !state.session.equals(packet.session())
                || packet.sequence() <= state.lastSequence) {
            BHXNetwork.rejected(player, packet, CUT_TICKS);
            return;
        }
        state.lastSequence = packet.sequence();
        long ready = serverReadyAt(state.lastStart, state.lastWave, state.pending);
        if (state.pending >= 0 || now < ready) {
            BHXNetwork.rejected(player, packet, (int)Math.max(1, ready - now));
            return;
        }
        state.lastStart = now;
        state.pending = now + WAVE_DELAY_TICKS;
        state.pendingRequest = packet;
        BHXNetwork.animation(player, packet.left());
    }
    public static void bindings(ServerPlayer player, BHXNetwork.Bindings packet) {
        State state = STATES.get(player.getUUID());
        if (state != null && active(player) && state.session.equals(packet.session())) state.leftMask = packet.leftMask() & 15;
    }
    public static boolean blocksCombo(Player player, com.p1nero.invincible.api.skill.ComboType type) {
        State state = STATES.get(player.getUUID());
        return active(player) && state != null && JingGuangPanCombos.left(type, state.leftMask);
    }
    public static boolean protectedFromFall(Player player) {
        State state = STATES.get(player.getUUID());
        return active(player) || (state != null && (state.active || state.gliding));
    }
    public static void clear(ServerPlayer player, boolean notify) {
        BHXNetwork.halo(player, haloPacket(player, null, true));
        player.removeTag(ACTIVE_TAG);
        STATES.remove(player.getUUID());
        if (notify) BHXNetwork.state(player, UUID.randomUUID(), false, false);
    }
    public static void stop() { STATES.clear(); }
    private static long nextHaloRevision;
    private static BHXNetwork.Halo haloPacket(ServerPlayer player, State state, boolean clear) {
        return new BHXNetwork.Halo(player.getUUID(), player.level().dimension().location(),
                state == null ? ++nextHaloRevision : state.haloRevision,
                state == null ? 0 : Math.max(0, player.level().getGameTime()-state.haloStarted),
                state != null && state.active, clear);
    }
    public static void haloTracking(ServerPlayer viewer, ServerPlayer target, boolean start) {
        State state = STATES.get(target.getUUID());
        if (!start || state != null && state.active)
            BHXNetwork.haloTo(viewer, haloPacket(target, state, !start));
    }
    private JingGuangPanManager() {}
}
