package net.offkung.bhspellsx.spells.gold;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.offkung.bhspellsx.client.renderer.crystal.AmethystDecreeSounds;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeConstants;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeAoe;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeCasterRingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Zi Xiaoyu — gold (main) / crystal (sub). PvP CC + damage: fixed burst damage, efn:stop hard
 * root, cataclysm:stun, three vanilla debuffs, and a fixed-damage DoT, all applied in a radius
 * around the caster by AmethystDecreeAoe (see that class for the per-tick work). This class is
 * just AbstractSpell registration + cast-site spawn, same split as bhspells' own
 * FeetStompSpell/FeetStompAoe.
 * <p>
 * MERGE: swap GOLD_SCHOOL_RESOURCE to BHSchoolRegistry.GOLD_RESOURCE once this moves into
 * bhspells proper — same string-id-until-merge pattern EmbracingBosomSpell already uses for
 * bhspells:ground, kept for the same reason (bhspells is a compile-time-only dependency here,
 * not a runtime one; nothing else in this repo requires it to be loaded).
 * <p>
 * Damage is intentionally NOT derived from getSpellPower()/spellLevel anywhere in this spell or
 * in AmethystDecreeAoe — every number is a hardcoded constant (see AmethystDecreeConstants).
 * baseSpellPower/spellPowerPerLevel below are set to 0 and unused for that reason, kept only
 * because AbstractSpell expects them to be set in the constructor.
 */
public class AmethystDecreeSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_decree");

    // MERGE: swap to BHSchoolRegistry.GOLD_RESOURCE once this moves into bhspells proper.
    private static final ResourceLocation GOLD_SCHOOL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("bhspells", "gold");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(GOLD_SCHOOL_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0.0)
            .build();

    public AmethystDecreeSpell() {
        // Mana cost 0, cooldown 0 (above): gating is entirely Apoli's (see Part 2 datapack),
        // not irons_spellbooks' own mana/cooldown system.
        this.baseManaCost = 0;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 20;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage",
                AmethystDecreeConstants.BURST_DAMAGE));
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return this.defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public boolean canBeInterrupted(Player player) {
        return false;
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        return this.getCastTime(spellLevel);
    }

    /** Copied exactly from irons_spellbooks' own FrostwaveSpell (also CastType.LONG, castTime
     *  20 — identical to ours): CHARGE_RAISED_HAND held for the whole cast (playOnce=false ->
     *  HOLD_ON_LAST_FRAME), then TOUCH_GROUND_ANIMATION at cast completion. STOMP was tried
     *  first (matching bhspells' own FeetStompSpell) but STOMP has its own leg-raise windup
     *  baked into the animation itself, so pairing it after an already-held raised-hand pose
     *  produced a visible second windup before the hit landed. TOUCH_GROUND_ANIMATION has no
     *  such windup — Frostwave's actual impact visual comes from particles spawned in its
     *  onCast() (synced to cast completion), not from the finish animation itself. */
    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.TOUCH_GROUND_ANIMATION;
    }

    /** Same fix as StoneCrumbleSpell.getDamageSource() — the burst and the first DoT tick can
     *  land within the target's invulnerability window otherwise, silently dropping the hit. */
    @Override
    public SpellDamageSource getDamageSource(@Nullable Entity projectile, Entity attacker) {
        return super.getDamageSource(projectile, attacker).setIFrames(0);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        AmethystDecreeAoe aoe = new AmethystDecreeAoe(level);
        aoe.setOwner(entity);
        aoe.setPos(entity.position());
        level.addFreshEntity(aoe);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Phase 2 VFX only — spawns the caster-ring crystal entity at the moment the 1s channel
     *  actually starts (not cast completion; onCast above is unchanged). Confirmed via decompile
     *  that this is the correct once-only cast-start hook for a player-cast spell: CastCommand
     *  routes a ServerPlayer target through AbstractSpell.attemptInitiateCast(), which calls
     *  onServerPreCast() exactly once, synchronously, right before starting the channel countdown
     *  — checkPreCastConditions() is not the right hook here (it's on a different code path).
     *  Calling super first preserves the existing cast-start sound. */
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
        AmethystDecreeCasterRingEntity ring = new AmethystDecreeCasterRingEntity(level);
        ring.setPos(entity.position());
        level.addFreshEntity(ring);
        AmethystDecreeSounds.playCastStart(level, entity.getX(), entity.getY(), entity.getZ(), entity.getRandom());
    }
}
