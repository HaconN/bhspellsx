package net.offkung.bhspellsx.mixin;

import com.p1nero.invincible.api.skill.ComboType;
import com.p1nero.invincible.skill.ComboBasicAttack;
import net.minecraft.world.entity.player.Player;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanCombos;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.skill.SkillContainer;

@Mixin(value = ComboBasicAttack.class, remap = false)
public abstract class InvincibleComboMixin {
    @Inject(method = "executeOnServer(Lyesman/epicfight/skill/SkillContainer;Lcom/p1nero/invincible/api/skill/ComboType;IJ)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void bhx$leftOnly(SkillContainer container, ComboType type, int pressedTime, long interval, CallbackInfo ci) {
        if (JingGuangPanManager.blocksCombo((Player)container.getExecutor().getOriginal(), type)) ci.cancel();
    }
}
