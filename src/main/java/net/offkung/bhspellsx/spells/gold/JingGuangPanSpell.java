package net.offkung.bhspellsx.spells.gold;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanWave;

/** One wave per instant cast; Apoli owns the mode and mana, EF owns its predicted pose. */
public final class JingGuangPanSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath("bhspellsx", "jing_guang_pan");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ResourceLocation.fromNamespaceAndPath("bhspells", "gold"))
            .setMaxLevel(1).setCooldownSeconds(0.0).build();

    public JingGuangPanSpell() {
        baseManaCost = manaCostPerLevel = baseSpellPower = spellPowerPerLevel = castTime = 0;
    }

    // Scoped to this server-thread call, consumed by onCast and restored even on rejection/exception.
    private final ThreadLocal<Boolean> waveSide = ThreadLocal.withInitial(() -> false);
    public boolean castWave(ServerPlayer player, boolean left) {
        boolean previous = waveSide.get();
        waveSide.set(left);
        try {
            return attemptInitiateCast(ItemStack.EMPTY, 1, player.serverLevel(), player, CastSource.COMMAND, false, "command");
        } finally {
            if (previous) waveSide.set(true); else waveSide.remove();
        }
    }

    @Override public ResourceLocation getSpellResource() { return SPELL_ID; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return AnimationHolder.none(); }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage", JingGuangPanConstants.DAMAGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        // /cast is intentionally usable without the toggle, but this wave requires a player owner.
        return entity instanceof ServerPlayer && entity.isAlive() && !data.isCasting();
    }

    @Override
    public boolean attemptInitiateCast(ItemStack stack, int spellLevel, Level level, Player player,
            CastSource source, boolean triggerCooldown, String castingEquipmentSlot) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return false;
        MagicData data = MagicData.getPlayerMagicData(serverPlayer);
        // Do not call super: it cancels another cast, stops item use, and queues even INSTANT casts.
        if (data.isCasting() || !canBeCastedBy(spellLevel, source, data, player).isSuccess()
                || !checkPreCastConditions(level, spellLevel, player, data)) return false;
        if (MinecraftForge.EVENT_BUS.post(new SpellPreCastEvent(player, getSpellId(), spellLevel, getSchoolType(), source))) return false;
        if (data.isCasting()) return false; // A pre-cast listener may itself have started another spell.
        onServerPreCast(level, spellLevel, player, data);
        // Iron's dispatches SpellOnCastEvent/onCast/OnClientCastPacket. No casting state was
        // opened, so do not send a completion packet that resets item use or another spell.
        castSpell(level, spellLevel, serverPlayer, source, false);
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource source, MagicData data) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) JingGuangPanWave.spawn(player, waveSide.get());
        super.onCast(level, spellLevel, entity, source, data);
    }
}
