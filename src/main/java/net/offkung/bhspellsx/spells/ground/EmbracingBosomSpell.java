package net.offkung.bhspellsx.spells.ground;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhspellsx.entity.spells.embracing_bosom.EmbracingBosomAoe;

/**
 * Phase 1: casts EmbracingBosomAoe at the caster's feet. See that class for the actual
 * heal/buff/damage-reduction mechanics — this spell class is just the AbstractSpell registration
 * and cast-site spawn logic.
 */
public class EmbracingBosomSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "embracing_bosom");

    // MERGE: swap to BHSchoolRegistry.GROUND_RESOURCE once this moves into bhspells proper.
    private static final ResourceLocation GROUND_SCHOOL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("bhspells", "ground");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(GROUND_SCHOOL_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(60.0)
            .build();

    public EmbracingBosomSpell() {
        this.baseManaCost = 60;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 40;
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
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        // Ground-snap from the caster's own position, same pattern as irons_spellbooks'
        // HealingCircleSpell.onCast.
        Vec3 spawnPos = Utils.moveToRelativeGroundLevel(level, entity.position(), 6);
        EmbracingBosomAoe aoe = new EmbracingBosomAoe(level);
        aoe.setOwner(entity);
        aoe.setPos(spawnPos);
        level.addFreshEntity(aoe);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
