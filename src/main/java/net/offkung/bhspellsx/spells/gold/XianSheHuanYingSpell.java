package net.offkung.bhspellsx.spells.gold;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.entity.PartEntity;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingConstants;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingTargetEntity;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingUserEntity;

import java.util.UUID;

/**
 * Wu Liang Ye — gold. Toggle "lock" on one target. Apoli owns the button, the toggle state, the
 * mana drain and the {@link XianSheHuanYingConstants#OWNER_TAG} tag; this spell only runs once
 * per activation (via /cast from Apoli): it raycasts the target and spawns the two entities.
 * Everything after that — slowness, VFX, ending the lock — is XianSheHuanYingUserEntity's job,
 * and its only stop signal is the caster losing the tag. See that class.
 * <p>
 * MERGE: swap GOLD_SCHOOL_RESOURCE to BHSchoolRegistry.GOLD_RESOURCE once this moves into
 * bhspells proper (same string-id-until-merge pattern as AmethystDecreeSpell).
 */
public class XianSheHuanYingSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "xian_she_huan_ying");

    // MERGE: swap to BHSchoolRegistry.GOLD_RESOURCE once this moves into bhspells proper.
    private static final ResourceLocation GOLD_SCHOOL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("bhspells", "gold");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(GOLD_SCHOOL_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0.0)
            .build();

    public XianSheHuanYingSpell() {
        // Mana cost 0, cooldown 0 (above): gating is entirely Apoli's, not irons' own system.
        this.baseManaCost = 0;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
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
        return CastType.INSTANT;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            lockTarget(serverLevel, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Extra-VFX mode handed to the spawned {@link XianSheHuanYingUserEntity}. The real spell always
     *  uses the constant; the temporary XSHY_TEST_ONLY subclass overrides it. */
    protected int getExtraVfxMode() {
        return XianSheHuanYingConstants.XSHY_EXTRA_VFX_MODE;
    }

    private void lockTarget(ServerLevel level, LivingEntity caster) {
        // Activating again while a lock exists: the old one dies first.
        UUID casterId = caster.getUUID();
        for (XianSheHuanYingUserEntity old : level.getEntities(
                EntityTypeTest.forClass(XianSheHuanYingUserEntity.class), e -> casterId.equals(e.getOwnerId()))) {
            old.discard();
        }

        LivingEntity target = findTarget(level, caster);
        if (target == null) {
            // Apoli already spent the mana and tagged the caster: strip the tag so its sync
            // power turns the toggle back off.
            caster.removeTag(XianSheHuanYingConstants.OWNER_TAG);
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("ui.bhspellsx.xian_she_huan_ying_no_target"), true);
            }
            return;
        }

        XianSheHuanYingUserEntity user = new XianSheHuanYingUserEntity(level, caster, target, getExtraVfxMode());
        XianSheHuanYingTargetEntity marker = new XianSheHuanYingTargetEntity(level, target, user.getUUID());
        user.setTargetEntityId(marker.getUUID());
        level.addFreshEntity(user);
        level.addFreshEntity(marker);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.translatable("ui.bhspellsx.xian_she_huan_ying_locked", target.getDisplayName()), true);
        }
    }

    private static LivingEntity findTarget(ServerLevel level, LivingEntity caster) {
        HitResult hit = Utils.raycastForEntity(level, caster, XianSheHuanYingConstants.TARGET_RANGE, true);
        if (!(hit instanceof EntityHitResult entityHit)) {
            return null;
        }
        Entity hitEntity = entityHit.getEntity();
        if (hitEntity instanceof PartEntity<?> part) {
            hitEntity = part.getParent();
        }
        if (hitEntity instanceof LivingEntity living && living != caster && living.isAlive()) {
            return living;
        }
        return null;
    }
}
