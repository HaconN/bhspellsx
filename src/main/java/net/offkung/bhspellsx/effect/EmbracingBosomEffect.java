package net.offkung.bhspellsx.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * No attribute modifiers — the damage reduction and debuff-shortening both live in
 * event/EmbracingBosomEvents.java, which checks for this effect's presence directly.
 * Follows bhspells' PerplexityEffect (net.offkung.bhspells.effect.PerplexityEffect).
 * <p>
 * The heal lives HERE (not on the AoE) so it lingers for the same 2s the buff does after a
 * player leaves the zone — the effect instance keeps ticking during that grace period
 * regardless of what spawned it.
 * <p>
 * EmbracingBosomAoe.EmbracingBosomAoe applies/refreshes this effect to 40 ticks every tick
 * while a player is inside the zone. That refresh means this effect's own remaining-duration
 * counter is useless as a "every 20 ticks" gate — it hovers around 39-40 the whole time
 * someone stays inside, and only actually counts down during the 2s after they leave. Gating
 * on it (e.g. `duration % 20 == 0`, the vanilla RegenerationMobEffect pattern) would fire
 * erratically or not at all while refreshed. Gating on the world's absolute game time instead
 * is refresh-proof: it advances at a fixed rate no matter how often this instance gets
 * replaced, so "every 20 ticks" means the same thing whether the player just walked in or has
 * been standing there for a minute.
 */
public class EmbracingBosomEffect extends MobEffect {
    private static final float HEAL_AMOUNT = 5.0f;
    private static final int HEAL_INTERVAL_TICKS = 20;

    public EmbracingBosomEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8A33D);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Always fire applyEffectTick(); the actual cadence decision happens there, against
        // game time rather than this (refresh-corrupted) duration parameter.
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().getGameTime() % HEAL_INTERVAL_TICKS != 0) {
            return;
        }
        if (livingEntity.getHealth() < livingEntity.getMaxHealth()) {
            livingEntity.heal(HEAL_AMOUNT);
        }
    }
}
