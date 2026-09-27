package net.offkung.bhspellsx.mixin.client;

import net.minecraft.client.KeyMapping;
import net.offkung.bhspellsx.client.JingGuangPanClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.events.engine.ControlEngine;
import yesman.epicfight.skill.SkillSlot;
import yesman.epicfight.skill.SkillSlots;

@Mixin(value = ControlEngine.class, remap = false)
public abstract class EpicFightInputMixin {
    @Shadow private KeyMapping reservedKey;
    @Shadow private SkillSlot reservedOrHoldingSkillSlot;
    @Shadow private int reserveCounter;
    @Inject(method = "maybeAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private void bhx$replaceBasic(CallbackInfo ci) {
        if (JingGuangPanClient.active()) ci.cancel();
    }
    @Inject(method = "handleEpicFightKeyMappings", at = @At("HEAD"), remap = false)
    private void bhx$discardBasicReserve(CallbackInfo ci) {
        if (JingGuangPanClient.active() && reservedOrHoldingSkillSlot == SkillSlots.BASIC_ATTACK) {
            reservedKey = null;
            reservedOrHoldingSkillSlot = null;
            reserveCounter = -1;
        }
    }
}
