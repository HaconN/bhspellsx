package net.offkung.bhspellsx.event;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.offkung.bhspellsx.registry.BHXMobEffectRegistry;

/**
 * Registered manually via MinecraftForge.EVENT_BUS.register(EmbracingBosomEvents.class) from the
 * bootstrap's main mod class — NOT a @Mod.EventBusSubscriber, so this class carries no modid and
 * copies straight into bhspells at merge time. Follows the pattern of bhspells' own
 * net.offkung.bhspells.BypassDamageEvent / SwordDashManager (manually registered, no annotation).
 */
public class EmbracingBosomEvents {
    // The manual re-apply below (onApplicable) calls addEffect(), which re-enters
    // canBeAffected() -> fires a new MobEffectEvent.Applicable. Without this guard that would
    // loop forever (re-shortening an already-shortened instance, ad infinitum).
    private static final ThreadLocal<Boolean> APPLYING_SHORTENED_DEBUFF = ThreadLocal.withInitial(() -> false);

    private static final float DAMAGE_REDUCTION_MULTIPLIER = 0.8f;
    private static final float DEBUFF_DURATION_MULTIPLIER = 0.7f;

    // 4a) Damage reduction 20%.
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.hasEffect(BHXMobEffectRegistry.EMBRACING_BOSOM.get())) {
            return;
        }
        // Vanilla has no "bypasses_magic" tag; bypasses_effects is the tag that governs whether
        // mob-effect-based damage modifiers (Resistance, etc.) apply at all — void damage
        // (out_of_world) and /kill (generic_kill) are both tagged with it in vanilla's damage
        // type data, which covers the spec's two named "absolute" cases along with anything else
        // vanilla considers effect-immune. Confirmed against DamageTypeTags in the mapped Forge
        // jar, not guessed.
        if (event.getSource().is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return;
        }
        event.setAmount(event.getAmount() * DAMAGE_REDUCTION_MULTIPLIER);
    }

    // 4b) Debuff duration -30%.
    //
    // INVESTIGATED: MobEffectEvent.Added is not @Cancelable and MobEffectInstance's duration
    // field has no public setter, confirming the Access Transformer concern. But
    // MobEffectEvent.Applicable is @Event.HasResult, and LivingEntity.canBeAffected() (decompiled
    // from the mapped Forge jar) does:
    //     event = new MobEffectEvent.Applicable(this, instance);
    //     post(event);
    //     if (event.getResult() != DEFAULT) return event.getResult() == ALLOW;
    // i.e. setResult(DENY) makes addEffect() return false before the instance is ever stored —
    // no AT needed. This is the approach below: deny the original, then manually add a shortened
    // replacement, guarded by the ThreadLocal above.
    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (APPLYING_SHORTENED_DEBUFF.get()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        MobEffect effect = instance.getEffect();
        if (effect == BHXMobEffectRegistry.EMBRACING_BOSOM.get()) {
            return;
        }
        if (effect.getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        if (!target.hasEffect(BHXMobEffectRegistry.EMBRACING_BOSOM.get())) {
            return;
        }
        event.setResult(Event.Result.DENY);
        int shortenedDuration = Math.max(1, Math.round(instance.getDuration() * DEBUFF_DURATION_MULTIPLIER));
        MobEffectInstance shortened = new MobEffectInstance(effect, shortenedDuration, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
        APPLYING_SHORTENED_DEBUFF.set(true);
        try {
            target.addEffect(shortened);
        } finally {
            APPLYING_SHORTENED_DEBUFF.set(false);
        }
    }
}
