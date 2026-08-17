# Merging bhspellsx into bhspells

For the team lead, when it's time to fold this project's content into the real `bhspells`
mod source tree. This is a manual, deliberate step — nothing here is automated.

Note: as of the JPMS split-package fix, **all** source in this repo lives under
`net.offkung.bhspellsx` (never `net.offkung.bhspells`) — see `CLAUDE.md`. The merge
procedure below is what performs the `bhspellsx` → `bhspells` rename; it does not exist
anywhere else in this repo.

## What gets deleted

The mod entrypoint (`BHSpellsX`, `BHSpellsXClient`) and the whole `registry/` package
(`BHXSpellRegistry`, `BHXEntityRegistry`, `BHXMobEffectRegistry`), plus this whole repo's
Gradle/CI scaffolding. None of it ships — it only exists to give the portable content a real
compiler and a real jar to test with.

## Portable content (as of Phase 1)

- `spells/ground/EmbracingBosomSpell.java`
- `entity/spells/embracing_bosom/EmbracingBosomAoe.java`
- `effect/EmbracingBosomEffect.java`
- `event/EmbracingBosomEvents.java` — no `@Mod.EventBusSubscriber` annotation, no modid
  baked in; registered manually (see step 5 below), so it copies over unmodified.
- `client/renderer/NoopEntityRenderer.java` — generic, not embracing_bosom-specific; check
  whether bhspells already has an equivalent before copying this one over too.
- `assets/bhspellsx/lang/en_us.json`

## Merge procedure

1. **Find-replace the package/namespace** — across every portable file listed above,
   replace:
   - `net.offkung.bhspellsx` → `net.offkung.bhspells` (package declarations and imports)
   - the string literal `"bhspellsx"` → `"bhspells"` (ResourceLocation namespaces, entity
     type registry names)

   `EmbracingBosomSpell.java`:

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

   `EmbracingBosomAoe.java`: package decl only
   (`net.offkung.bhspellsx.entity.spells.embracing_bosom` →
   `net.offkung.bhspells.entity.spells.embracing_bosom`), plus its imports of
   `BHXEntityRegistry`/`BHXMobEffectRegistry` get repointed to the real
   `EntityRegistry`/`MobEffectsRegistry` in step 5.

   `EmbracingBosomEffect.java`, `EmbracingBosomEvents.java`: package decl only, same
   pattern. `EmbracingBosomEvents`'s import of `BHXMobEffectRegistry` also gets repointed
   to `MobEffectsRegistry` in step 5.

2. **Move the renamed files** into the bhspells mod's source tree, mirroring the
   subpackage paths under `net/offkung/bhspells/` (e.g.
   `net/offkung/bhspells/entity/spells/embracing_bosom/EmbracingBosomAoe.java`).

3. **Assets** — move `src/main/resources/assets/bhspellsx/` to `assets/bhspells/` in the
   bhspells resource tree (rename the folder, i.e. find-replace the same namespace on the
   asset path), merging `lang/en_us.json` into the existing bhspells lang file rather than
   overwriting it.

4. **Swap the school reference** — once the file lives inside bhspells and can see
   `BHSchoolRegistry` directly, replace the hardcoded constant with the real one:

   ```java
   // before (Phase 0/1 — no compile-time bhspells dependency)
   private static final ResourceLocation GROUND_SCHOOL_RESOURCE =
           ResourceLocation.fromNamespaceAndPath("bhspells", "ground");
   ...
   .setSchoolResource(GROUND_SCHOOL_RESOURCE)

   // after (inside bhspells — bhspells.registry.BHSchoolRegistry is now on the classpath)
   .setSchoolResource(BHSchoolRegistry.GROUND_RESOURCE)
   ```

   The `// MERGE:` comment above `GROUND_SCHOOL_RESOURCE` in `EmbracingBosomSpell.java`
   marks exactly this line.

5. **Registry wiring** — add each entry to bhspells' real registries instead of the
   throwaway `BHX*` ones, then repoint the portable classes' imports to match:

   ```java
   // net/offkung/bhspells/registry/BHSpellRegistry.java, alongside the other RegistryObjects:
   public static final RegistryObject<AbstractSpell> EMBRACING_BOSOM =
           registerSpell(new EmbracingBosomSpell());

   // net/offkung/bhspells/registry/EntityRegistry.java:
   public static final RegistryObject<EntityType<EmbracingBosomAoe>> EMBRACING_BOSOM_AOE =
           ENTITIES.register("embracing_bosom_aoe", () -> EntityType.Builder
                   .<EmbracingBosomAoe>of(EmbracingBosomAoe::new, MobCategory.MISC)
                   .sized(12.0f, 1.2f).clientTrackingRange(64)
                   .build(ResourceLocation.fromNamespaceAndPath("bhspells", "embracing_bosom_aoe").toString()));

   // net/offkung/bhspells/registry/MobEffectsRegistry.java:
   public static final RegistryObject<MobEffect> EMBRACING_BOSOM =
           MOB_EFFECTS.register("embracing_bosom", EmbracingBosomEffect::new);
   ```

   In `EmbracingBosomAoe.java`, repoint `BHXEntityRegistry.EMBRACING_BOSOM_AOE` →
   `EntityRegistry.EMBRACING_BOSOM_AOE` and `BHXMobEffectRegistry.EMBRACING_BOSOM` →
   `MobEffectsRegistry.EMBRACING_BOSOM`. Same for the one reference in
   `EmbracingBosomEvents.java`.

   Register the renderer in bhspells' client entrypoint the same way `BHSpellsXClient` does
   it (`EntityRenderersEvent.RegisterRenderers` → `registerEntityRenderer`), and register
   `EmbracingBosomEvents` on the FORGE bus from bhspells' main mod class constructor
   (`MinecraftForge.EVENT_BUS.register(EmbracingBosomEvents.class)`) — it has no
   `@Mod.EventBusSubscriber` annotation to carry over, by design.

   Then delete `BHXSpellRegistry.java`, `BHXEntityRegistry.java`, `BHXMobEffectRegistry.java`
   entirely — their only job was registering this content under the bootstrap's own
   `DeferredRegister`s.

6. **mods.toml** — no action needed on the bhspells side; bhspells already declares its own
   `irons_spellbooks`/`irons_lib`/`traveloptics` dependencies (it already calls traveloptics
   from `EternalPurificationSpell`, which is what Phase 2B's VFX was modelled on).
   `bhspellsx`'s `mods.toml` is deleted along with the rest of the bootstrap.

## Verifying after merge

- Grep the merged bhspells source tree for `bhspellsx` (package, string literal, or asset
  path) — it should return nothing.
- The moved classes should compile with zero references to the bootstrap mod (no `BHX*`
  imports left).
- Build bhspells, deploy to the modpack, confirm `/cast bhspells:embracing_bosom` still
  works under its new identity: AoE spawns at the caster's feet, heals players in it every
  20 ticks, applies the Embracing Bosom buff every tick (20% damage reduction while active,
  harmful effects on the target expire 30% faster), and the placeholder particle ring/center
  puff still appear.
- Once confirmed, this `bhspellsx` repo can be archived or deleted — it has no further
  purpose after a successful merge of a given phase's content. (If more phases are planned,
  keep the repo and start the next phase's content in a fresh subpackage under
  `net/offkung/bhspellsx/...` instead.)
