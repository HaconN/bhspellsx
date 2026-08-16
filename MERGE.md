# Merging bhspellsx into bhspells

For the team lead, when it's time to fold this project's content into the real `bhspells`
mod source tree. This is a manual, deliberate step — nothing here is automated.

Note: as of the JPMS split-package fix, **all** source in this repo lives under
`net.offkung.bhspellsx` (never `net.offkung.bhspells`) — see `CLAUDE.md`. The merge
procedure below is what performs the `bhspellsx` → `bhspells` rename; it does not exist
anywhere else in this repo.

## What gets deleted

The mod entrypoint (`BHSpellsX`, `BHSpellsXClient`) and the `registry/BHXSpellRegistry.java`
package, plus this whole repo's Gradle/CI scaffolding. None of it ships — it only exists to
give the portable content a real compiler and a real jar to test with.

## Merge procedure

1. **Find-replace the package/namespace** — across every file under
   `src/main/java/net/offkung/bhspellsx/` that is being copied (i.e. everything except
   `BHSpellsX.java`, `BHSpellsXClient.java`, and `registry/`), replace:
   - `net.offkung.bhspellsx` → `net.offkung.bhspells` (package declarations and imports)
   - the string literal `"bhspellsx"` → `"bhspells"` (ResourceLocation namespaces)

   As of this Phase 0 drop that's `EmbracingBosomSpell.java`, one occurrence each:

   ```java
   // before
   package net.offkung.bhspellsx.spells.ground;
   ...
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspellsx", "embracing_bosom");

   // after
   package net.offkung.bhspells.spells.ground;
   ...
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspells", "embracing_bosom");
   ```

   Do **not** touch the `GROUND_SCHOOL_RESOURCE` constant — it's already
   `ResourceLocation.fromNamespaceAndPath("bhspells", "ground")` and was only ever a
   hardcoded stand-in for `BHSchoolRegistry.GROUND_RESOURCE`.

2. **Move the renamed files** into the bhspells mod's source tree, mirroring the
   subpackage path under `net/offkung/bhspells/` (e.g.
   `net/offkung/bhspells/spells/ground/EmbracingBosomSpell.java`).

3. **Assets** — move `src/main/resources/assets/bhspellsx/` to `assets/bhspells/` in the
   bhspells resource tree (rename the folder, i.e. find-replace the same namespace on the
   asset path), merging `lang/en_us.json` into the existing bhspells lang file rather than
   overwriting it.

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

- Grep the merged bhspells source tree for `bhspellsx` (package, string literal, or asset
  path) — it should return nothing.
- `EmbracingBosomSpell` should compile with zero references to the bootstrap mod.
- Build bhspells, deploy to the modpack, confirm `/cast bhspells:embracing_bosom` still
  works (heals 1 HP, spawns the particle ring) under its new identity.
- Once confirmed, this `bhspellsx` repo can be archived or deleted — it has no further
  purpose after a successful merge of a given phase's content. (If more phases are planned,
  keep the repo and start the next phase's content in a fresh subpackage under
  `net/offkung/bhspellsx/...` instead.)
