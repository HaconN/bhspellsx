package net.offkung.bhspellsx.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;

/**
 * Client-only home for Xian She Huan Ying's "being watched" cue. Pulled out of
 * XianSheHuanYingTargetEntity (a common-side class — it ticks on the dedicated server too)
 * because referencing {@code net.minecraft.client.*} types there, even guarded by an
 * {@code isClientSide()} branch, is unsafe: bytecode verification resolves the types a METHOD
 * references the first time that method is invoked, and on a dedicated server
 * {@code XianSheHuanYingTargetEntity.tick()} genuinely runs (it's the server-authoritative
 * lifecycle) — so the guarded branch's own client-type references would still need resolving the
 * moment {@code tick()} is entered, class present or not. Keeping every client-type reference in
 * a class that is only ever CALLED from inside a common class's {@code isClientSide()} branch
 * (never referenced in a field type, return type, or unconditionally-run code path) is what
 * actually avoids the crash — this class exists for exactly that separation, mirroring how
 * {@code client/renderer} and {@code client/particle} are already split out from the common-side
 * entity/spell classes in this project.
 */
public final class XianSheHuanYingEyeSound {
    private XianSheHuanYingEyeSound() {
    }

    /** Plays the cue on the LOCAL client only, and only if the local player IS the locked target
     *  (matched by entity id, since the target can be any LivingEntity but here we only care
     *  whether it's this client's own player). No-op if there's no local player yet, the local
     *  player isn't the target, or {@link XianSheHuanYingConstants#EYE_SOUND} doesn't resolve to
     *  a registered sound. */
    public static void playIfLocalPlayerIsTarget(int targetEntityId) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.getId() != targetEntityId) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(XianSheHuanYingConstants.EYE_SOUND);
        SoundEvent sound = soundId != null ? ForgeRegistries.SOUND_EVENTS.getValue(soundId) : null;
        if (sound != null) {
            player.playSound(sound, XianSheHuanYingConstants.EYE_SOUND_VOLUME, XianSheHuanYingConstants.EYE_SOUND_PITCH);
        }
    }
}
