package net.offkung.bhspellsx.mixin.client;

import java.util.Map;
import java.util.Queue;
import com.p1nero.invincible.api.skill.ComboNode;
import com.p1nero.invincible.api.skill.ComboType;
import com.p1nero.invincible.client.InputManager;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.event.TickEvent;
import net.offkung.bhspellsx.client.JingGuangPanClient;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanCombos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InputManager.class, remap = false)
public abstract class InvincibleInputMixin {
    @Shadow @Final private static Map<ComboType, KeyMapping> TYPE_KEY_MAP;
    @Shadow @Final private static Map<Integer, Integer> KEY_STATE_CACHE;
    @Shadow @Final private static Queue<Integer> INPUT_QUEUE;
    @Inject(method = "testPressedTime", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bhx$leftOnly(ComboType type, CallbackInfoReturnable<Integer> cir) {
        if (JingGuangPanClient.active() && JingGuangPanCombos.left(type, JingGuangPanClient.leftMask())) cir.setReturnValue(0);
    }
    @Inject(method = "onClientTick", at = @At("HEAD"), remap = false)
    private static void bhx$clearLeftCache(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (!JingGuangPanClient.active()) return;
        TYPE_KEY_MAP.forEach((type, key) -> {
            if (JingGuangPanCombos.left(type, JingGuangPanClient.leftMask())) {
                int code = key.getKey().getValue();
                KEY_STATE_CACHE.put(code, 0);
                INPUT_QUEUE.removeIf(value -> value == code);
            }
        });
    }
}
