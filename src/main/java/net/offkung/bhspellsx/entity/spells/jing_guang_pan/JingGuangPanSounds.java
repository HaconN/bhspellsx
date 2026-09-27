package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.registry.BHXSoundRegistry;

/** Authoritative one-shot cues: vanilla sound packets reach caster and nearby listeners. */
public final class JingGuangPanSounds {
    public static final float OPEN_1_VOLUME=.35f, OPEN_2_VOLUME=.35f, OPEN_PITCH=1f;
    public static final float WAVE_VOLUME=.18f, WAVE_PITCH_MIN=.95f, WAVE_PITCH_MAX=1.05f;
    public static final float HIT_VOLUME=.22f, HIT_PITCH=1.1f, CLOSE_VOLUME=.20f, CLOSE_PITCH=1.25f;
    public static void toggle(ServerPlayer player, boolean active) {
        var level=player.serverLevel(); var pos=player.position();
        if (active) {
            // Two separate events start on the same tick; a sounds.json list would randomly choose one.
            play(level,pos,BHXSoundRegistry.JING_GUANG_PAN_ACTIVATE_1.get(),OPEN_1_VOLUME,OPEN_PITCH);
            play(level,pos,BHXSoundRegistry.JING_GUANG_PAN_ACTIVATE_2.get(),OPEN_2_VOLUME,OPEN_PITCH);
        } else play(level,pos,SoundEvents.BEACON_DEACTIVATE,CLOSE_VOLUME,CLOSE_PITCH);
    }
    public static void wave(ServerPlayer player) {
        float pitch=WAVE_PITCH_MIN+player.getRandom().nextFloat()*(WAVE_PITCH_MAX-WAVE_PITCH_MIN);
        play(player.serverLevel(),player.getEyePosition(),BHXSoundRegistry.JING_GUANG_PAN_WAVE.get(),WAVE_VOLUME,pitch);
    }
    public static void hit(ServerLevel level, Vec3 pos) {
        var sound=ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.parse("epicfight:entity.hit.blade"));
        if (sound!=null) play(level,pos,sound,HIT_VOLUME,HIT_PITCH);
    }
    private static void play(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null,pos.x,pos.y,pos.z,sound,SoundSource.PLAYERS,volume,pitch);
    }
    private JingGuangPanSounds() {}
}
