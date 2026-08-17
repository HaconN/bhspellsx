package net.offkung.bhspellsx.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * No attribute modifiers — the damage reduction and debuff-shortening both live in
 * event/EmbracingBosomEvents.java, which checks for this effect's presence directly.
 * Follows bhspells' PerplexityEffect (net.offkung.bhspells.effect.PerplexityEffect).
 */
public class EmbracingBosomEffect extends MobEffect {
    public EmbracingBosomEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8A33D);
    }
}
