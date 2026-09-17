package net.offkung.bhspellsx.spells.water;

import com.gametechbc.traveloptics.api.init.TravelopticsSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeAoe;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeConstants;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Li Ming — water (main). CONTINUOUS cast, 120-tick cast time (matches the dome's own
 * DURATION_TICKS) so Iron's shows the real casting animation + cast bar for the dome's whole
 * lifetime.
 * <p>
 * onCast() fires every 10 ticks for the whole cast, including the FINAL pulse (confirmed via
 * decompile of MagicManager.tick(): the CONTINUOUS branch calls castSpell() — which calls
 * onCast() — both on every 10-tick pulse where castDurationRemaining is still > 0, AND again on
 * the terminal pulse where castDurationRemaining has just reached 0, before
 * onServerCastComplete(false) fires in that same call). Only the very first pulse should spawn
 * the dome and do the open burst — determined via
 * `playerMagicData.getCastDurationRemaining() == playerMagicData.getCastDuration()`, true only
 * on the first pulse, since castDurationRemaining strictly decreases every tick after
 * initiateCast() sets it and is never reset except by a genuinely new cast. NOT
 * hasActiveDomeFor(): that check is what caused N5's double-cast bug — the dome had already
 * ended itself (old tick()-based DURATION_TICKS check) by the time the terminal onCast() pulse
 * fired, so hasActiveDomeFor() was already false and a second dome spawned with no mana/cast bar
 * behind it.
 * <p>
 * Cast completion now drives the dome's natural end directly: onServerCastComplete(cancelled=false)
 * calls CrystalHydroDomeAoe.naturalEndFor() (heal + knockback, if the dome is still alive).
 * onServerCastComplete(cancelled=true) — any external cast-cancel (opening a container, casting
 * another spell, death, logout, dimension change; NOT damage, since canBeInterrupted() defaults
 * false for CONTINUOUS) — breaks the dome early the same way HP hitting 0 does, via
 * CrystalHydroDomeAoe.endActiveDomeFor(). Conversely, when the DOME decides to end early on its
 * own (HP 0, caster moved out/dead, logout), it cancels the cast itself
 * (CrystalHydroDomeAoe.finish()) so the cast bar disappears immediately — that call synchronously
 * re-enters this onServerCastComplete(cancelled=true), which is exactly why CrystalHydroDomeAoe
 * guards its whole end-of-life path with a single `ended` flag, not just this class.
 * <p>
 * The dome's own tick()-based DURATION_TICKS check is now a fallback only (fires at
 * DURATION_TICKS + 5, a grace period past when cast completion should have already ended it) —
 * in case cast state is somehow lost (e.g. a bug elsewhere silently drops the cast without ever
 * calling onServerCastComplete). It can only ever END an already-alive dome, never spawn one, so
 * it can't reintroduce N5's bug.
 * <p>
 * See CrystalHydroDomeAoe for the per-tick logic and CrystalHydroDomeEvents for the damage
 * redirect/counter. This class is just AbstractSpell registration, the once-only open
 * cleanse/heal, and cast-site spawn/cancel wiring — same split as amethyst_decree/embracing_bosom.
 * <p>
 * School is TravelopticsSchools.AQUA_RESOURCE, a real compile-time class reference, not a
 * string placeholder like AmethystDecreeSpell's GOLD_SCHOOL_RESOURCE — traveloptics is already
 * a hard runtime dependency of bhspellsx (mods.toml, since Phase 2B's particle-manager calls),
 * unlike bhspells, so there's nothing to swap at merge time. See MERGE.md §C.
 * <p>
 * Mana 0 / cooldown 0 here: gating is entirely Apoli's (see the datapack), not
 * irons_spellbooks' own mana/cooldown system — same pattern as amethyst_decree.
 * Spell Power intentionally never multiplies any number in this spell or in
 * CrystalHydroDomeAoe/Events — every value is a hardcoded constant (CrystalHydroDomeConstants).
 * baseSpellPower/spellPowerPerLevel are 0 and unused for that reason.
 */
public class CrystalHydroDomeSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "crystal_hydro_dome");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(TravelopticsSchools.AQUA_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0.0)
            .build();

    public CrystalHydroDomeSpell() {
        this.baseManaCost = 0;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = CrystalHydroDomeConstants.DURATION_TICKS;
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
        return CastType.CONTINUOUS;
    }

    /** Same fix as AmethystDecreeSpell/StoneCrumbleSpell — the open burst and the Counter can
     *  otherwise land within a target's invulnerability window and get silently dropped. */
    @Override
    public SpellDamageSource getDamageSource(@Nullable Entity projectile, Entity attacker) {
        return super.getDamageSource(projectile, attacker).setIFrames(0);
    }

    /** Fires every 10 ticks for the whole CONTINUOUS cast, including the terminal pulse — see
     *  class javadoc. Only the first pulse (castDurationRemaining == castDuration) spawns the
     *  dome and does the open burst; every later pulse (including the terminal one) is a no-op. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (playerMagicData.getCastDurationRemaining() != playerMagicData.getCastDuration()) {
            return;
        }

        Vec3 center = entity.position();
        openCleanseAndHeal(level, center);
        entity.addTag(CrystalHydroDomeConstants.DOME_TAG);

        CrystalHydroDomeAoe aoe = new CrystalHydroDomeAoe(level);
        aoe.setOwner(entity);
        aoe.setPos(center);
        level.addFreshEntity(aoe);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** cancelled=true: an external cast-cancel (container open, another spell cast, death,
     *  logout, dimension change) — break the dome early, same as HP hitting 0 (no end heal, no
     *  knockback). cancelled=false: natural completion — drives the dome's own natural end
     *  (end heal + knockback), if it's still alive. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
        if (cancelled) {
            CrystalHydroDomeAoe.endActiveDomeFor(entity);
        } else {
            CrystalHydroDomeAoe.naturalEndFor(entity);
        }
    }

    /** On cast (once): every LivingEntity inside the about-to-open dome (excluding armor stands,
     *  including the caster) loses every harmful non-infinite effect and heals OPEN_HEAL. Runs
     *  before the dome entity exists, so this uses the static isInside()/searchBox() helpers with
     *  an explicit center rather than an entity instance. */
    private void openCleanseAndHeal(Level level, Vec3 center) {
        AABB box = CrystalHydroDomeAoe.searchBox(center, 0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof ArmorStand) && e.isAlive() && CrystalHydroDomeAoe.isInside(center, e.position()));
        for (LivingEntity target : targets) {
            CrystalHydroDomeAoe.cleanseHarmfulEffects(target);
            target.heal((float) CrystalHydroDomeConstants.OPEN_HEAL);
        }
    }
}
