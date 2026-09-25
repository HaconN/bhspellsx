package net.offkung.bhspellsx.registry;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspellsx.spells.gold.AmethystDecreeSpell;
import net.offkung.bhspellsx.spells.gold.XianSheHuanYingSpell;
import net.offkung.bhspellsx.spells.gold.XianSheHuanYingTestSpell; // XSHY_TEST_ONLY — ลบก่อนส่ง
import net.offkung.bhspellsx.spells.ground.EmbracingBosomSpell;
import net.offkung.bhspellsx.spells.water.CrystalHydroDomeSpell;

/**
 * Registers into irons_spellbooks' own SpellRegistry (SPELL_REGISTRY_KEY), same pattern
 * bhspells' own BHSpellRegistry uses. MERGE: at merge time, fold this single entry into
 * bhspells' real BHSpellRegistry.SPELLS instead of keeping a second DeferredRegister.
 */
public class BHXSpellRegistry {
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, "bhspellsx");

    public static final RegistryObject<AbstractSpell> EMBRACING_BOSOM =
            registerSpell(new EmbracingBosomSpell());

    public static final RegistryObject<AbstractSpell> AMETHYST_DECREE =
            registerSpell(new AmethystDecreeSpell());

    public static final RegistryObject<AbstractSpell> CRYSTAL_HYDRO_DOME =
            registerSpell(new CrystalHydroDomeSpell());

    public static final RegistryObject<AbstractSpell> XIAN_SHE_HUAN_YING =
            registerSpell(new XianSheHuanYingSpell());

    // XSHY_TEST_ONLY — ลบก่อนส่ง (3 test spells: same class as the real spell, different extra-VFX mode)
    public static final RegistryObject<AbstractSpell> XSHY_TEST_A =
            registerSpell(new XianSheHuanYingTestSpell("xshy_test_a", 1));
    // XSHY_TEST_ONLY — ลบก่อนส่ง
    public static final RegistryObject<AbstractSpell> XSHY_TEST_B =
            registerSpell(new XianSheHuanYingTestSpell("xshy_test_b", 2));
    // XSHY_TEST_ONLY — ลบก่อนส่ง
    public static final RegistryObject<AbstractSpell> XSHY_TEST_AB =
            registerSpell(new XianSheHuanYingTestSpell("xshy_test_ab", 3));

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}
