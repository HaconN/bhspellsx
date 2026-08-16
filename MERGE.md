# Merging bhspellsx into bhspells

For the team lead, when it's time to fold this project's content into the real `bhspells`
mod source tree. This is a manual, deliberate step — nothing here is automated.

## What gets deleted

The entire `net/offkung/bhspellsx/` package (bootstrap `@Mod` class, client event bus
subscriber, `registry/BHXSpellRegistry.java`) and this whole repo's Gradle/CI scaffolding.
None of it ships — it only exists to give the portable content a real compiler and a real
jar to test with.

## What gets copied

1. **Java sources** — copy `src/main/java/net/offkung/bhspells/` verbatim into the
   `bhspells` mod's source tree at the same relative path
   (`net/offkung/bhspells/spells/ground/EmbracingBosomSpell.java`). No package rename
   needed — it was already written as if it lived there.

2. **Assets** — move `src/main/resources/assets/bhspellsx/` to `assets/bhspells/` in the
   bhspells resource tree, merging `lang/en_us.json` into the existing bhspells lang file
   rather than overwriting it.

3. **Find-replace the namespace** — inside the copied Java file(s), replace every
   `"bhspellsx"` string literal with `"bhspells"`. As of this Phase 0 drop that's exactly
   one occurrence: the `SPELL_ID` `ResourceLocation` in `EmbracingBosomSpell`:

   ```java
   // before
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspellsx", "embracing_bosom");
   // after
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspells", "embracing_bosom");
   ```

   Do **not** touch the `GROUND_SCHOOL_RESOURCE` constant — it's already
   `ResourceLocation.fromNamespaceAndPath("bhspells", "ground")` and was only ever a
   hardcoded stand-in for `BHSchoolRegistry.GROUND_RESOURCE`.

4. **Swap the school reference** — once the file lives inside bhspells and can see
   `BHSchoolRegistry` directly, replace the hardcoded constant with the real one:

   ```java
   // before (Phase 0 — no compile-time bhspells dependency)
   private static final ResourceLocation GROUND_SCHOOL_RESOURCE =
           ResourceLocation.fromNamespaceAndPath("bhspells", "ground");
   ...
   .setSchoolResource(GROUND_SCHOOL_RESOURCE)

   // after (inside bhspells — bhspells.registry.BHSchoolRegistry is now on the classpath)
   .setSchoolResource(BHSchoolRegistry.GROUND_RESOURCE)
   ```

   The `// MERGE:` comment above `GROUND_SCHOOL_RESOURCE` in `EmbracingBosomSpell.java`
   marks exactly this line.

5. **Registry wiring** — add the spell to bhspells' real `BHSpellRegistry` instead of the
   throwaway `BHXSpellRegistry`:

   ```java
   // in net/offkung/bhspells/registry/BHSpellRegistry.java, alongside the other RegistryObjects:
   public static final RegistryObject<AbstractSpell> EMBRACING_BOSOM =
           registerSpell(new EmbracingBosomSpell());
   ```

   Then delete `BHXSpellRegistry.java` entirely — its only job was registering this one
   spell under the bootstrap's own `DeferredRegister`.

6. **mods.toml** — no action needed on the bhspells side; bhspells already declares its own
   `irons_spellbooks`/`irons_lib` dependencies. `bhspellsx`'s `mods.toml` is deleted along
   with the rest of the bootstrap.

## Verifying after merge

- `EmbracingBosomSpell` should compile with zero references to the `bhspellsx` namespace or
  package.
- Grep the merged bhspells source tree for `"bhspellsx"` — it should return nothing.
- Build bhspells, deploy to the modpack, confirm `/cast bhspells:embracing_bosom` still
  works (heals 1 HP, spawns the particle ring) under its new identity.
- Once confirmed, this `bhspellsx` repo can be archived or deleted — it has no further
  purpose after a successful merge of a given phase's content. (If more phases are planned,
  keep the repo and start the next phase's content in a fresh `net/offkung/bhspells/...`
  subpackage instead.)
