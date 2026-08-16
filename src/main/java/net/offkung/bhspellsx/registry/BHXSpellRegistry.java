package net.offkung.bhspellsx.registry;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspells.spells.ground.EmbracingBosomSpell;

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

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}
