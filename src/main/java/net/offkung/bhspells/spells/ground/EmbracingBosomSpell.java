package net.offkung.bhspells.spells.ground;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Phase 0 proof-of-pipeline spell. No visual effects beyond a vanilla particle ring —
 * this exists purely to confirm the AbstractSpell registration / cast pipeline works end
 * to end against irons_spellbooks 3.16.1 before any real content is built.
 */
public class EmbracingBosomSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "embracing_bosom");

    // MERGE: swap to BHSchoolRegistry.GROUND_RESOURCE once this moves into bhspells proper.
    private static final ResourceLocation GROUND_SCHOOL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("bhspells", "ground");

    private static final int PARTICLE_RING_POINTS = 12;
    private static final double PARTICLE_RING_RADIUS = 0.8;

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
        entity.heal(1.0f);
        spawnParticleRing(level, entity);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void spawnParticleRing(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double centerX = entity.getX();
        double centerY = entity.getY();
        double centerZ = entity.getZ();
        for (int i = 0; i < PARTICLE_RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / PARTICLE_RING_POINTS;
            double x = centerX + PARTICLE_RING_RADIUS * Math.cos(angle);
            double z = centerZ + PARTICLE_RING_RADIUS * Math.sin(angle);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, centerY + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
