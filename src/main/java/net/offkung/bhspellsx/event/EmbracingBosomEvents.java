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
    // Anything at or above this is treated the same as a true infinite effect (see onApplicable).
    // ~13.9 hours — comfortably past anything a real potion/spell duration would ever reach, but
    // well within range of a staff-applied "very long" roleplay duration that isn't literally -1.
    private static final int EFFECTIVELY_INFINITE_DURATION_TICKS = 1_000_000;

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
    // INVESTIGATED (both parts, before writing anything):
    //
    // Duration mutation in place: decompiled MobEffectInstance from the mapped Forge jar.
    // Its `duration` field is private with no public setter; the only thing that touches it
    // post-construction is the package-private `setDetailsFrom`/`update`, both unreachable from
    // here. So there is no clean in-place mutation path — reflection or an Access Transformer
    // are the only ways in, and this file was told to stop and report before reaching for an AT.
    // Reporting instead: no AT written.
    //
    // Cancellation mechanism: MobEffectEvent.Added is not @Cancelable and (per the above) its
    // instance isn't mutable, confirming that route is closed too. MobEffectEvent.Applicable is
    // @Event.HasResult though, and LivingEntity.canBeAffected() (also decompiled) does:
    //     event = new MobEffectEvent.Applicable(this, instance);
    //     post(event);
    //     if (event.getResult() != DEFAULT) return event.getResult() == ALLOW;
    // i.e. setResult(DENY) makes addEffect() return false before the instance is ever stored —
    // no AT needed for the deny+reapply itself. This is the approach below: deny the original,
    // then manually add a shortened replacement, guarded by the ThreadLocal above.
    //
    // Known side effect of denying in Applicable (accepted, see CLAUDE.md): commands/callers
    // that read addEffect()'s own return value (e.g. vanilla /effect give) see `false` for the
    // original call and print their normal failure message, even though our manual re-apply
    // succeeds right after. No clean fix found without the mutation path above. Per the brief,
    // this cosmetic message is far less harmful than breaking infinite effects (2a), so it's
    // left as-is and documented rather than blocked on.
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
        // Infinite/effectively-infinite durations pass through completely untouched: no deny,
        // no shortening, no re-apply. MobEffectInstance.INFINITE_DURATION == -1 (confirmed by
        // decompile, not assumed) is what /effect give <player> <effect> infinite produces;
        // duration * 0.7 on that sentinel (or any other negative value) would corrupt it, and
        // this is a roleplay server where staff rely on infinite effects working exactly as
        // given.
        if (instance.isInfiniteDuration() || instance.getDuration() < 0
                || instance.getDuration() >= EFFECTIVELY_INFINITE_DURATION_TICKS) {
            return;
        }
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
