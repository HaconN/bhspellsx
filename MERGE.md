# Merging bhspellsx into bhspells

For the team lead, when it's time to fold this project's content into the real `bhspells`
mod source tree. This is a manual, deliberate step — nothing here is automated.

Note: as of the JPMS split-package fix, **all** source in this repo lives under
`net.offkung.bhspellsx` (never `net.offkung.bhspells`) — see `CLAUDE.md`. The merge
procedure below is what performs the `bhspellsx` → `bhspells` rename; it does not exist
anywhere else in this repo.

**This doc covers three independent spells: `embracing_bosom`, `amethyst_decree`, and
`crystal_hydro_dome`.** They don't share any portable files, so each has its own "Portable
content" list and its own numbered procedure below (§A, §B, §C) — only the namespace
find-replace mechanics and the final `mods.toml` check are common to all. Read the two ⚠️
warning sections below before starting any of them; they're not optional context, they're
the two ways this merge goes wrong silently. The RenderType warning applies to
`crystal_hydro_dome`'s renderer too (see §C).

---

## ⚠️ READ BEFORE TOUCHING embracing_bosom's RING RENDERER

`client/renderer/EmbracingBosomRingRenderer.java` builds its own `RenderType.CompositeState`
by hand instead of calling one of vanilla's own `RenderType.xxx()` factory methods. **This
looks over-engineered and is exactly the kind of thing that gets "cleaned up" during a
merge — don't.** Specifically: **do not replace the shader in `buildRingRenderType()`**
(`GameRenderer.getRendertypeEntityTranslucentEmissiveShader()`) **with a different one, and
do not swap the whole thing back to a plain `RenderType.eyes()`/`RenderType.entityCutout()`/
etc. call, even though those look simpler and "more normal."**

Doing so will not show up in ordinary testing. The ring will render perfectly for every
player who isn't running a shaderpack — which is most testing, most of the time. It will
render **invisible** for any player running Oculus/Iris, silently, with no error.

**Why:** Iris does not generically intercept custom RenderTypes/shaders. It patches ~27
*specific* `GameRenderer.get*Shader()` getter methods by exact method identity
(`MixinGameRenderer`), each routed to one of a closed set of real deferred-rendering
programs (`ShaderKey`). Anything not on that list gets no shaderpack routing and can end up
rendering into the wrong stage of the pipeline. Two things were tried and rejected before
landing on the current shader:
- `GameRenderer.getPositionColorTexShader()` — *is* on Iris's list, but its override always
  routes to `ShaderKey.TEXTURED_COLOR` (a generic 2D/UI-quad program), not the
  terrain→entities→translucent→composite chain a world-space effect needs. This is what
  made the ring vanish under Oculus the first time.
- `GameRenderer.getRendertypeEnergySwirlShader()` (what irons_spellbooks' own magic-glow
  RenderTypes use) — Iris routes this to `ShaderKey.ENTITIES_CUTOUT`, a real stage, but
  binary-alpha-discard rather than smooth blending. Would have flattened every soft haze
  layer and broken the fade in/out.

`getRendertypeEntityTranslucentEmissiveShader()` is the one that's both alpha-blended *and*
correctly routed by Iris to `ShaderKey.ENTITIES_EYES_TRANS`, a real entity-context deferred
gbuffer stage. That's not a stylistic choice — it's the only option out of everything tried
that is simultaneously visible without shaders, alpha-correct, and visible *with* Oculus.

The full reasoning (plus the earlier `RenderType.eyes()` cull/blend bugs that came before
this) is written up in detail in the class javadoc at the top of
`EmbracingBosomRingRenderer.java` — read it before changing anything in
`buildRingRenderType()`, `ADDITIVE_ALPHA_TRANSPARENCY`, or the vertex format. If a shader
substitution ever seems necessary post-merge, re-verify against Iris's actual mixin classes
(decompile the installed Oculus jar — don't assume), the same way this was originally
root-caused.

Does not apply to `amethyst_decree` — its renderers use a plain vanilla `EntityModel`, no
custom `RenderType` at all (see §B below).

**Does apply to `crystal_hydro_dome`:** `CrystalHydroDomeRenderer.buildDomeRenderType()` is a
deliberate copy of the same composite state (same shader, `SRC_ALPHA/ONE` additive blend, no
cull, no depth write). Its lotus pass uses vanilla `RenderType.entityTranslucentEmissive(...)`
— same shader, normal alpha blend. Don't swap either for a "simpler" RenderType. The
non-emissive `getRendertypeEntityTranslucentShader()` was tried for the lotus and rendered
the petals solid black: its fragment shader always multiplies by the lightmap, and the
hand-built state had the lightmap disabled. See the comment above `LOTUS_RENDER_TYPE`.

---

## ⚠️ READ BEFORE DELETING BHSpellsX — the mob effect check must be RE-HOMED, not deleted

This is the single most important thing in this update. **Do not let this check disappear
during the merge.**

**What it is:** `BHSpellsX.checkAmethystDecreeMobEffects(FMLCommonSetupEvent)`, wired up via
`modEventBus.addListener(BHSpellsX::checkAmethystDecreeMobEffects)` in `BHSpellsX`'s
constructor. At common setup, it looks up `efn:stop` and `cataclysm:stun` by
`ResourceLocation` via `ForgeRegistries.MOB_EFFECTS.getValue(...)` and logs a hard `LOGGER.error(...)`
naming whichever id is missing.

**Why it exists:** `AmethystDecreeAoe.applyRootAndStun()` resolves both of those mob effects
the same way, at the moment a target is hit, and simply skips `target.addEffect(...)` for
whichever one comes back `null`. There is no exception, no other code path, and nothing in
the spell's own tooltip or cast feedback that would ever reveal this.

**What breaks if this check is dropped:** if Epic Fight Nightfall or L_Ender's Cataclysm is
ever removed from the pack, renamed, or fails to load before bhspells — `amethyst_decree`
silently degrades into a damage-only spell with no root and no stun. It still deals its 30
damage, still applies the three vanilla debuffs, still runs its DoT and its full VFX/sound —
everything *looks* like it worked. The CC, which is half the spell's PvP purpose, is just
gone, with nothing in the log, nothing in-game, and nothing on the tooltip to say so. This
check is the *only* thing that would ever surface that failure.

**The trap:** `BHSpellsX` and `BHSpellsXClient` are exactly the "throwaway bootstrap"
classes the "What gets deleted" section below says to delete wholesale. A merge that
follows that section literally, without reading this warning first, deletes this check
along with the rest of the class and replaces it with nothing.

**Required action:** move (don't delete) `checkAmethystDecreeMobEffects` into bhspells' own
`FMLCommonSetupEvent` handling — wherever its main mod class already does common setup —
keeping the exact same `ResourceLocation` ids (`efn:stop`, `cataclysm:stun`) and the same
log-an-error-don't-crash behavior. This is intentionally a soft dependency (see the
`mods.toml` note at the end of this doc) — do not turn it into a hard `mods.toml` dependency
on `efn`/`cataclysm` as a "fix"; the soft check with a loud log message is the design.

---

## What gets deleted

The mod entrypoint (`BHSpellsX`, `BHSpellsXClient`) and the whole `registry/` package
(`BHXSpellRegistry`, `BHXEntityRegistry`, `BHXMobEffectRegistry`, `BHXParticleRegistry`),
plus this whole repo's Gradle/CI scaffolding. None of it ships — it only exists to give the
portable content a real compiler and a real jar to test with.

**Before deleting `BHSpellsX`, extract `checkAmethystDecreeMobEffects` per the warning
above.** Everything else in these classes really is throwaway.

## Portable content

### embracing_bosom

- `spells/ground/EmbracingBosomSpell.java`
- `entity/spells/embracing_bosom/EmbracingBosomAoe.java`
- `effect/EmbracingBosomEffect.java`
- `event/EmbracingBosomEvents.java` — no `@Mod.EventBusSubscriber` annotation, no modid
  baked in; registered manually (see step A5 below), so it copies over unmodified.
- `client/renderer/EmbracingBosomRingRenderer.java` — **read the warning section above
  first.** The Phase 2A ring VFX: five rotating layers, spawn-converge easing, fade in/out.
- `client/renderer/RingLayer.java` — the immutable per-layer config record
  `EmbracingBosomRingRenderer` reads from.
- `client/particle/EmbraceLeafParticle.java` (+ inner `Provider`) — Phase 2C ambient leaf.
- `client/particle/EmbraceLeafParticleOption.java` — its tintable `ParticleOptions`.
- `client/particle/EmbraceMoteParticle.java` (+ inner `Provider`) — Phase 2C ambient spark.
- `client/particle/EmbraceMoteParticleOption.java` — its tintable `ParticleOptions`.
- `assets/bhspellsx/textures/mob_effect/embracing_bosom.png`
- `assets/bhspellsx/textures/entity/ring/*.png` (nine textures — the ring layer stack)
- `assets/bhspellsx/textures/particle/embrace_leaf.png`, `embrace_mote.png`
- `assets/bhspellsx/particles/embrace_leaf.json`, `embrace_mote.json`

### amethyst_decree

- `spells/gold/AmethystDecreeSpell.java`
- `entity/spells/amethyst_decree/AmethystDecreeAoe.java` — gameplay: burst, root/stun, three
  vanilla debuffs, DoT. Also declares `EFN_STOP_ID`/`CATACLYSM_STUN_ID` — see the mob effect
  warning above, these are the same two ids that check resolves.
- `entity/spells/amethyst_decree/AmethystDecreeCasterRingEntity.java`,
  `AmethystDecreeTargetCrystalEntity.java` — Phase 2 VFX-only entities, no damage/targeting
  of their own.
- `entity/spells/amethyst_decree/AmethystDecreeConstants.java` — every tuned number
  (radius, damage, durations, sound volume/pitch). Hardcoded on purpose, not config-backed —
  copies over unmodified, nothing to swap.
- `client/renderer/AmethystDecreeCasterRingRenderer.java`,
  `AmethystDecreeTargetCrystalRenderer.java` — **read the layer-definition step below
  first**, these depend on it.
- `client/renderer/crystal/CasterRingLayout.java`, `TargetCrystalLayout.java`,
  `CrystalTransform.java`, `CrystalAnim.java` — pure placement/timing math, no
  registry/namespace references at all, copy over unmodified (package decl only).
- `client/renderer/crystal/CrystalUnitModel.java` — the vanilla `EntityModel`/`ModelPart`
  crystal geometry. Declares `SMALL_LAYER`/`LARGE_LAYER` (`ModelLayerLocation`s) with the
  `"bhspellsx"` namespace baked in — needs the swap.
- `client/renderer/crystal/CrystalDebris.java` — shard+mote particle spawn helper.
- `client/renderer/crystal/AmethystDecreeSounds.java` — all 5 sound cues, vanilla amethyst
  `SoundEvents` only, no custom audio asset.
- `client/particle/AmethystShardParticle.java` (+ inner `Provider`),
  `AmethystShardParticleOption.java`.
- `assets/bhspellsx/lang/en_us.json` — contains both spells' entries already; see step 3
  under embracing_bosom's procedure, same merge-not-overwrite instruction applies.
- `assets/bhspellsx/textures/entity/amethyst_crystal.png`
- `assets/bhspellsx/textures/particle/amethyst_shard.png`
- `assets/bhspellsx/particles/amethyst_shard.json`

### crystal_hydro_dome

- `spells/water/CrystalHydroDomeSpell.java` — CONTINUOUS 120-tick cast; first pulse spawns the
  dome + open cleanse/heal; `onServerCastComplete` drives natural end vs early break.
- `entity/spells/crystal_hydro_dome/CrystalHydroDomeAoe.java` — all gameplay (damage redirect,
  dome HP, Counter, projectile layer, root, action-bar HP, end heal/knockback), plus the synced
  data the renderer reads (counter bolts, end state) and the end sounds.
- `entity/spells/crystal_hydro_dome/CrystalHydroDomeConstants.java` — every gameplay number,
  hardcoded on purpose (no config).
- `event/CrystalHydroDomeEvents.java` — `LivingAttackEvent`/`LivingDamageEvent` redirect and
  login tag cleanup; no annotation, registered manually.
- `client/renderer/CrystalHydroDomeRenderer.java` — **read the RenderType warning above.** All
  VFX: shell, water streaks, lotus, wind, drifting petals, ground sigil, shell lightning, counter bolts,
  natural-end wave, broken-end shatter. Hand-built meshes, no model/texture of its own.
- `client/renderer/CrystalHydroDomeVisuals.java` — every visual tuning number.
- `assets/bhspellsx/lang/en_us.json` — four `crystal_hydro_dome` keys (see §C step 3).

No textures, particle types, sounds, or model layers of its own — the renderer binds
`forge:textures/white.png` and colors by vertex; sounds are vanilla `SoundEvents` plus
irons_spellbooks' `SoundRegistry`.

---

## §A. embracing_bosom merge procedure

1. **Find-replace the package/namespace** — across every file listed under embracing_bosom
   above, replace:
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
   `EntityRegistry`/`MobEffectsRegistry` in step A5. Its `LIFETIME_TICKS` constant is `public`
   specifically so `EmbracingBosomRingRenderer` can read it — keep that visibility when you
   move the file (it's a deliberate cross-class dependency, not an oversight).

   `EmbracingBosomEffect.java`, `EmbracingBosomEvents.java`: package decl only, same
   pattern. `EmbracingBosomEvents`'s import of `BHXMobEffectRegistry` also gets repointed
   to `MobEffectsRegistry` in step A5.

   `EmbracingBosomRingRenderer.java`, `RingLayer.java`, the two particle classes, and their
   two `ParticleOptions` classes: package decl only, same pattern. Their asset-path string
   literals (texture `ResourceLocation`s under `textures/entity/ring/` and
   `textures/particle/`) also need the `"bhspellsx"` → `"bhspells"` swap.

2. **Move the renamed files** into the bhspells mod's source tree, mirroring the
   subpackage paths under `net/offkung/bhspells/` (e.g.
   `net/offkung/bhspells/entity/spells/embracing_bosom/EmbracingBosomAoe.java`).

3. **Assets** — move `src/main/resources/assets/bhspellsx/` to `assets/bhspells/` in the
   bhspells resource tree (rename the folder, i.e. find-replace the same namespace on the
   asset path), merging `lang/en_us.json` into the existing bhspells lang file rather than
   overwriting it (it now has both spells' entries — bring both over). This is a blanket
   folder move, so it covers the ring textures, particle textures, particle JSONs, and the
   mob effect icon automatically — nothing extra to do per-file here as long as the whole
   folder moves together. (amethyst_decree's assets live in the same folder — see §B step 3,
   this is one physical move covering both spells.)

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

   // net/offkung/bhspells/registry/ParticleRegistry.java, following whatever pattern
   // bhspells' existing tinted particles (e.g. ColoredCherryParticleOption's registration)
   // already use — EmbraceLeafParticleOption/EmbraceMoteParticleOption follow that exact
   // pattern, so the registration shape should already match:
   public static final RegistryObject<ParticleType<EmbraceLeafParticleOption>> EMBRACE_LEAF =
           PARTICLE_TYPES.register("embrace_leaf", () -> new ParticleType<>(false, EmbraceLeafParticleOption.DESERIALIZER) {
               @Override
               public Codec<EmbraceLeafParticleOption> codec() { return EmbraceLeafParticleOption.CODEC; }
           });
   public static final RegistryObject<ParticleType<EmbraceMoteParticleOption>> EMBRACE_MOTE =
           PARTICLE_TYPES.register("embrace_mote", () -> new ParticleType<>(false, EmbraceMoteParticleOption.DESERIALIZER) {
               @Override
               public Codec<EmbraceMoteParticleOption> codec() { return EmbraceMoteParticleOption.CODEC; }
           });
   ```

   In `EmbracingBosomAoe.java`, repoint `BHXEntityRegistry.EMBRACING_BOSOM_AOE` →
   `EntityRegistry.EMBRACING_BOSOM_AOE` and `BHXMobEffectRegistry.EMBRACING_BOSOM` →
   `MobEffectsRegistry.EMBRACING_BOSOM`. Same for the one reference in
   `EmbracingBosomEvents.java`.

   In `EmbraceLeafParticleOption.java` and `EmbraceMoteParticleOption.java`, repoint their
   `getType()` methods from `BHXParticleRegistry.EMBRACE_LEAF.get()` /
   `BHXParticleRegistry.EMBRACE_MOTE.get()` to the real `ParticleRegistry` equivalents added
   above.

   Register the renderer in bhspells' client entrypoint the same way `BHSpellsXClient` does
   it (`EntityRenderersEvent.RegisterRenderers` → `registerEntityRenderer`). **Also move the
   particle sprite-set registration** — `BHSpellsXClient` has a separate handler,
   `onRegisterParticleProviders` (`RegisterParticleProvidersEvent`), with
   `event.registerSpriteSet(...)` calls (one per particle type, pointed at each particle
   class's `Provider`). This is easy to miss since it's a separate event from the entity
   renderer one — it needs a home in bhspells' client entrypoint too, or the leaf/mote
   particles will register their `ParticleType` but never actually render (no crash, they'll
   just silently never appear — same failure shape as the RenderType bug above, different
   cause).

   Register `EmbracingBosomEvents` on the FORGE bus from bhspells' main mod class
   constructor (`MinecraftForge.EVENT_BUS.register(EmbracingBosomEvents.class)`) — it has no
   `@Mod.EventBusSubscriber` annotation to carry over, by design.

   Then delete `BHXSpellRegistry.java`, `BHXEntityRegistry.java`, `BHXMobEffectRegistry.java`,
   `BHXParticleRegistry.java` entirely (once §B's registrations are also moved off them) —
   their only job was registering this content under the bootstrap's own `DeferredRegister`s.

---

## §B. amethyst_decree merge procedure

1. **Find-replace the package/namespace** — across every file listed under amethyst_decree
   above, same two replacements as §A step 1:
   - `net.offkung.bhspellsx` → `net.offkung.bhspells`
   - the string literal `"bhspellsx"` → `"bhspells"`

   `AmethystDecreeSpell.java`:

   ```java
   // before
   package net.offkung.bhspellsx.spells.gold;
   ...
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_decree");

   // after
   package net.offkung.bhspells.spells.gold;
   ...
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspells", "amethyst_decree");
   ```

   Do **not** touch `AmethystDecreeAoe.EFN_STOP_ID`/`CATACLYSM_STUN_ID` — those are
   `efn:stop`/`cataclysm:stun`, external mod ids, unrelated to the `bhspellsx`→`bhspells`
   rename.

   `AmethystDecreeAoe.java`, `AmethystDecreeCasterRingEntity.java`,
   `AmethystDecreeTargetCrystalEntity.java`, `AmethystDecreeConstants.java`: package decl
   only. The three entity classes' `BHXEntityRegistry` imports get repointed in step B5.
   `AmethystDecreeAoe.java` also imports `BHXSpellRegistry` (for `getDamageSource()`'s spell
   lookup) — repointed in the same step.

   `client/renderer/crystal/CrystalUnitModel.java`: package decl plus its two
   `ModelLayerLocation` namespace literals and its texture `ResourceLocation`:

   ```java
   // before
   ResourceLocation.fromNamespaceAndPath("bhspellsx", "textures/entity/amethyst_crystal.png");
   new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_crystal_small"), "main");
   new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_crystal_large"), "main");

   // after
   ResourceLocation.fromNamespaceAndPath("bhspells", "textures/entity/amethyst_crystal.png");
   new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("bhspells", "amethyst_crystal_small"), "main");
   new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("bhspells", "amethyst_crystal_large"), "main");
   ```

   `CasterRingLayout.java`, `TargetCrystalLayout.java`, `CrystalTransform.java`,
   `CrystalAnim.java`, `CrystalDebris.java`, `AmethystDecreeSounds.java`,
   `AmethystDecreeCasterRingRenderer.java`, `AmethystDecreeTargetCrystalRenderer.java`,
   `AmethystShardParticle.java`, `AmethystShardParticleOption.java`: package decl only.
   `CrystalDebris.java` and `AmethystShardParticleOption.java` also import
   `BHXParticleRegistry` — repointed in step B5.

2. **Move the renamed files** into bhspells' source tree, mirroring the subpackage paths
   under `net/offkung/bhspells/` (e.g. `net/offkung/bhspells/spells/gold/AmethystDecreeSpell.java`,
   `net/offkung/bhspells/client/renderer/crystal/CrystalUnitModel.java`).

3. **Assets** — same physical folder move as §A step 3 (`assets/bhspellsx/` →
   `assets/bhspells/`); amethyst_decree's crystal texture, shard particle texture, and
   particle JSON all move along with it automatically.

4. **Swap the school reference**, same pattern as §A step 4:

   ```java
   // before
   private static final ResourceLocation GOLD_SCHOOL_RESOURCE =
           ResourceLocation.fromNamespaceAndPath("bhspells", "gold");
   ...
   .setSchoolResource(GOLD_SCHOOL_RESOURCE)

   // after
   .setSchoolResource(BHSchoolRegistry.GOLD_RESOURCE)
   ```

   The `// MERGE:` comment above `GOLD_SCHOOL_RESOURCE` in `AmethystDecreeSpell.java` marks
   this exact line.

5. **Registry wiring**:

   ```java
   // net/offkung/bhspells/registry/BHSpellRegistry.java:
   public static final RegistryObject<AbstractSpell> AMETHYST_DECREE =
           registerSpell(new AmethystDecreeSpell());

   // net/offkung/bhspells/registry/EntityRegistry.java (three entries):
   public static final RegistryObject<EntityType<AmethystDecreeAoe>> AMETHYST_DECREE_AOE =
           ENTITIES.register("amethyst_decree_aoe", () -> EntityType.Builder
                   .<AmethystDecreeAoe>of(AmethystDecreeAoe::new, MobCategory.MISC)
                   .sized(22.0f, 1.2f).clientTrackingRange(64)
                   .build(ResourceLocation.fromNamespaceAndPath("bhspells", "amethyst_decree_aoe").toString()));
   public static final RegistryObject<EntityType<AmethystDecreeCasterRingEntity>> AMETHYST_DECREE_CASTER_RING =
           ENTITIES.register("amethyst_decree_caster_ring", () -> EntityType.Builder
                   .<AmethystDecreeCasterRingEntity>of(AmethystDecreeCasterRingEntity::new, MobCategory.MISC)
                   .sized(22.0f, 4.0f).clientTrackingRange(64)
                   .build(ResourceLocation.fromNamespaceAndPath("bhspells", "amethyst_decree_caster_ring").toString()));
   public static final RegistryObject<EntityType<AmethystDecreeTargetCrystalEntity>> AMETHYST_DECREE_TARGET_CRYSTAL =
           ENTITIES.register("amethyst_decree_target_crystal", () -> EntityType.Builder
                   .<AmethystDecreeTargetCrystalEntity>of(AmethystDecreeTargetCrystalEntity::new, MobCategory.MISC)
                   .sized(2.0f, 3.0f).clientTrackingRange(64)
                   .build(ResourceLocation.fromNamespaceAndPath("bhspells", "amethyst_decree_target_crystal").toString()));

   // net/offkung/bhspells/registry/ParticleRegistry.java, same shape as EMBRACE_LEAF/EMBRACE_MOTE:
   public static final RegistryObject<ParticleType<AmethystShardParticleOption>> AMETHYST_SHARD =
           PARTICLE_TYPES.register("amethyst_shard", () -> new ParticleType<>(false, AmethystShardParticleOption.DESERIALIZER) {
               @Override
               public Codec<AmethystShardParticleOption> codec() { return AmethystShardParticleOption.CODEC; }
           });
   ```

   Repoint references off the throwaway registries: `AmethystDecreeAoe.java`'s
   `BHXEntityRegistry.AMETHYST_DECREE_AOE` → `EntityRegistry.AMETHYST_DECREE_AOE` and
   `BHXSpellRegistry.AMETHYST_DECREE` → `BHSpellRegistry.AMETHYST_DECREE`;
   `AmethystDecreeCasterRingEntity.java`'s and `AmethystDecreeTargetCrystalEntity.java`'s
   `BHXEntityRegistry.*` references to their respective `EntityRegistry.*` equivalents;
   `AmethystShardParticleOption.java`'s `getType()` from `BHXParticleRegistry.AMETHYST_SHARD.get()`
   to `ParticleRegistry.AMETHYST_SHARD.get()`; `CrystalDebris.java`'s same import.

   Register both renderers in bhspells' client entrypoint's `RegisterRenderers` handler:

   ```java
   event.registerEntityRenderer(EntityRegistry.AMETHYST_DECREE_AOE.get(), NoopRenderer::new);
   event.registerEntityRenderer(EntityRegistry.AMETHYST_DECREE_CASTER_RING.get(), AmethystDecreeCasterRingRenderer::new);
   event.registerEntityRenderer(EntityRegistry.AMETHYST_DECREE_TARGET_CRYSTAL.get(), AmethystDecreeTargetCrystalRenderer::new);
   ```

   Move the particle sprite-set registration the same way §A step 5 describes for the leaf/mote
   particles: `event.registerSpriteSet(ParticleRegistry.AMETHYST_SHARD.get(), AmethystShardParticle.Provider::new)`
   inside bhspells' `RegisterParticleProvidersEvent` handler.

6. **Register the model layer definitions — a step embracing_bosom never needed.**
   `RegisterLayerDefinitions` is a **separate Forge event from `RegisterRenderers`**
   (`EntityRenderersEvent.RegisterLayerDefinitions`, not
   `EntityRenderersEvent.RegisterRenderers`). embracing_bosom's ring is hand-drawn — it has
   no vanilla `EntityModel` and never touched this event, so if you're merging from muscle
   memory built on embracing_bosom, it's easy to assume registering the entity renderer is
   the only wiring step and skip this entirely.

   `CrystalUnitModel`'s `SMALL_LAYER`/`LARGE_LAYER` are real `ModelLayerLocation`s backing a
   real `ModelPart` tree (`CubeListBuilder`-built, mirrors irons_spellbooks' own
   `IceSpikeModel`). Both renderers call `context.bakeLayer(CrystalUnitModel.SMALL_LAYER)` /
   `LARGE_LAYER` in their constructors — if the layer was never registered, `bakeLayer()`
   throws the moment the renderer is constructed at client startup, crashing on launch. This
   is a *loud* failure (unlike most of the other gotchas in this doc), but a confusing one if
   you don't already know this event exists, since the stack trace points at Forge's model
   registry internals, not at anything in this mod's own code.

   `BHSpellsXClient` currently does this:

   ```java
   @SubscribeEvent
   public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
       event.registerLayerDefinition(CrystalUnitModel.SMALL_LAYER, CrystalUnitModel::createSmallLayer);
       event.registerLayerDefinition(CrystalUnitModel.LARGE_LAYER, CrystalUnitModel::createLargeLayer);
   }
   ```

   Add an equivalent `@SubscribeEvent` handler (same shape, same two calls, `bhspellsx` →
   `bhspells` already applied to the `ModelLayerLocation`s via step B1) to bhspells' client
   entrypoint. It can live in the same class as the renderer registration or its own handler
   — Forge doesn't care, as long as it fires before any `AmethystDecreeCasterRingRenderer`/
   `AmethystDecreeTargetCrystalRenderer` is constructed, which registering it as a normal
   `@SubscribeEvent` guarantees.

7. **Re-home the mob effect check.** See the ⚠️ warning section near the top of this doc —
   do this as part of this merge, not as an afterthought. This is not optional cleanup, it's
   the thing that keeps a future silent CC failure visible in the log.

---

## §C. crystal_hydro_dome merge procedure

Melee swung at the dome's bare surface (no entity behind the swing) is intentionally not
detected or countered — only hits on entities inside the dome, incoming projectiles, and AoE
damage are redirected/countered; this was investigated and cancelled (see
`docs/recon/crystal_hydro_dome_phase2_part1_melee.md`), not an oversight to fix during merge.

1. **Find-replace the package/namespace** — across every file listed under crystal_hydro_dome
   in the "Portable content" section above, same two replacements as §A/§B step 1:
   - `net.offkung.bhspellsx` → `net.offkung.bhspells`
   - the string literal `"bhspellsx"` → `"bhspells"`

   `CrystalHydroDomeSpell.java`:

   ```java
   // before
   package net.offkung.bhspellsx.spells.water;
   ...
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspellsx", "crystal_hydro_dome");

   // after
   package net.offkung.bhspells.spells.water;
   ...
   private static final ResourceLocation SPELL_ID =
           ResourceLocation.fromNamespaceAndPath("bhspells", "crystal_hydro_dome");
   ```

   Do **not** touch `CrystalHydroDomeAoe.EFN_HORIZONTALSTOP_ID`/`EFN_VERTICALSTOP_ID`
   (`efn:horizontalstop`/`efn:verticalstop`) or either tag constant (`DOME_TAG` =
   `pers_liming_dome`, `REFLECTED_TAG`) — they aren't registry namespaces. `DOME_TAG` in
   particular is read by the liming datapack's cooldown power and must stay exactly
   `pers_liming_dome`. `REFLECTED_TAG`'s value (`bhspellsx_crystal_hydro_dome_reflected`) does
   contain the text `bhspellsx`, so the post-merge grep will hit it; it's only a runtime
   entity tag set and read inside `CrystalHydroDomeAoe`, so renaming it to `bhspells_...` or
   leaving it is equally safe.

   **Why `horizontalstop`+`verticalstop` and not `efn:stop`** (same effect amethyst_decree
   still uses for its root): in-game testing found `efn:stop` locks ALL input, not just
   movement — decompiling the deployed EFN jar found this is enforced by 4 client-side Mixins
   (`MixinKeyboardHandler`, `MixinMouseHandler`, `MixinKeyMapping`) that all gate on the mere
   *presence* of `efn:stop` specifically, blocking every keypress, click, and mouse-look —
   including opening the inventory, F3, and the pause menu, not just movement. None of those
   Mixins reference `efn:horizontalstop`/`efn:verticalstop` at all. In-game testing on
   2026-09-26 found that `horizontalstop` alone only slows the player: walking, jumping,
   and attacking still work; it does not lock X/Z. Keep the pair as the dome's current
   setup, but its observed movement restriction may also come from Iron's CONTINUOUS
   casting. The pair has not been tested independently of casting, nor has `verticalstop`
   alone, so a full position freeze is not confirmed. Separately, while the
   dome's cast is active the player can't open chat or inventory (observed in-game; the dome
   stays up). Opening a container or pressing another skill cancels the cast and breaks the
   dome. `amethyst_decree`'s own use of `efn:stop` was deliberately left untouched — that's a
   separate, still-pending decision, not an oversight.

   `CrystalHydroDomeAoe.java`: package decl only. Also imports `BHXEntityRegistry` (for its
   `(Level)` convenience constructor) and `BHXSpellRegistry` (for `getDamageSource()`'s spell
   lookup) — both repointed in step C5.

   `CrystalHydroDomeRenderer.java`, `CrystalHydroDomeVisuals.java`: package decl only (plus the
   renderer's static import of `CrystalHydroDomeVisuals.*`). The RenderType names inside
   (`"bhspellsx_crystal_hydro_dome"`, `"bhspellsx_dome_additive_alpha"`) are debug labels only;
   rename or keep, either works.

   `CrystalHydroDomeConstants.java`, `CrystalHydroDomeEvents.java`: package decl only.
   `CrystalHydroDomeEvents.java` has no `@Mod.EventBusSubscriber` annotation and carries no
   modid; its `@SubscribeEvent` methods are static and the class is registered manually from
   the main mod class constructor (`MinecraftForge.EVENT_BUS.register(CrystalHydroDomeEvents.class)`,
   see step 5) — no annotation to update.

2. **Move the renamed files** into bhspells' source tree, mirroring the subpackage paths
   under `net/offkung/bhspells/` (e.g. `net/offkung/bhspells/spells/water/CrystalHydroDomeSpell.java`,
   `net/offkung/bhspells/entity/spells/crystal_hydro_dome/CrystalHydroDomeAoe.java`).

3. **Assets** — none besides lang. All VFX are hand-built meshes in `CrystalHydroDomeRenderer`
   (colored by vertex over `forge:textures/white.png`) plus native `minecraft:glow` spawned
   client-side from `CrystalHydroDomeAoe.tick()`. No texture, particle type, sound file, or model
   layer to move. (The old Phase 1 `spawnBoundaryVfx()` splash particles no longer exist.)

   **Lang keys**: `assets/bhspellsx/lang/en_us.json` has four entries for this spell —
   `spell.bhspellsx.crystal_hydro_dome`, `spell.bhspellsx.crystal_hydro_dome.guide`, and the two
   action-bar HP readout keys `ui.bhspellsx.crystal_hydro_dome_hp_label` (read by
   `CrystalHydroDomeAoe.buildHpReadoutComponent()`) and `ui.bhspellsx.crystal_hydro_dome_hp_value`
   (the `%s/%s` hp/max format, args supplied in code — colors are applied via `Style` in
   `buildHpReadoutComponent()`, not baked into either lang value). All four need the same
   `bhspellsx` → `bhspells` key-prefix rename as step 1's Java changes, merged into bhspells'
   own `en_us.json` rather than moved as a separate file.

4. **School reference — nothing to swap, unlike §B step 4.** `CrystalHydroDomeSpell` already
   references `com.gametechbc.traveloptics.api.init.TravelopticsSchools.AQUA_RESOURCE` as a real
   compile-time class field, not a `GOLD_SCHOOL_RESOURCE`-style `ResourceLocation` string
   placeholder — traveloptics is already a hard runtime dependency of `bhspellsx` (see
   `bhspellsx/CLAUDE.md`'s Dependency jars section), so there was never a reason to defer this
   reference to merge time. The import survives the move unchanged.

5. **Registry wiring**:

   ```java
   // net/offkung/bhspells/registry/BHSpellRegistry.java:
   public static final RegistryObject<AbstractSpell> CRYSTAL_HYDRO_DOME =
           registerSpell(new CrystalHydroDomeSpell());

   // net/offkung/bhspells/registry/EntityRegistry.java:
   public static final RegistryObject<EntityType<CrystalHydroDomeAoe>> CRYSTAL_HYDRO_DOME_AOE =
           ENTITIES.register("crystal_hydro_dome_aoe", () -> EntityType.Builder
                   .<CrystalHydroDomeAoe>of(CrystalHydroDomeAoe::new, MobCategory.MISC)
                   .sized(12.0f, 6.0f).clientTrackingRange(64)
                   .build(ResourceLocation.fromNamespaceAndPath("bhspells", "crystal_hydro_dome_aoe").toString()));
   ```

   Repoint `CrystalHydroDomeAoe.java`'s `BHXEntityRegistry.CRYSTAL_HYDRO_DOME_AOE` →
   `EntityRegistry.CRYSTAL_HYDRO_DOME_AOE` and `BHXSpellRegistry.CRYSTAL_HYDRO_DOME` →
   `BHSpellRegistry.CRYSTAL_HYDRO_DOME`.

   Register the renderer in bhspells' client entrypoint's `RegisterRenderers` handler. It is
   the real VFX renderer now, **not** `NoopRenderer` (Phase 1 used Noop; that's stale):

   ```java
   event.registerEntityRenderer(EntityRegistry.CRYSTAL_HYDRO_DOME_AOE.get(), CrystalHydroDomeRenderer::new);
   ```

   Register `CrystalHydroDomeEvents` on the FORGE bus from bhspells' main mod class
   constructor (`MinecraftForge.EVENT_BUS.register(CrystalHydroDomeEvents.class)`), same as
   `BHSpellsX` does today.

6. **No `RegisterLayerDefinitions` or particle-provider step needed** — unlike §B step 6,
   crystal_hydro_dome has no `EntityModel`/`ModelLayerLocation` and no custom particle type.

   Things that look removable but aren't, all in `CrystalHydroDomeAoe`:
   - `defineSynchedData()`/`onSyncedDataUpdated()` and the `DATA_COUNTER_*`/`DATA_END_STATE`
     accessors — this is how the client learns about Counters (bolt target, 8-slot ring buffer)
     and which ending to play. No custom packet channel exists; don't add one.
   - The `ended` → `lingerTicks` branch in `tick()` — after `finish()` the dome is already out of
     `ACTIVE_DOMES` (no more gameplay), but the entity stays loaded `END_LINGER_TICKS` (30) so
     clients can play the end/shatter animation. Discarding immediately makes the dome just
     vanish and hides the last Counter bolt.
   - `SoundRegistry` import — irons_spellbooks' `HOLY_CAST`/`ICE_BLOCK_IMPACT`. bhspells already
     depends on irons_spellbooks, nothing to add.
   - `CrystalHydroDomeRenderer.shouldRender()` always `true` — the entity's bounding box is far
     smaller than the dome, so default frustum culling hides it.

7. **Re-home the mob effect check.** `BHSpellsX.checkAmethystDecreeMobEffects` (despite its
   name — see the ⚠️ warning section near the top of this doc) already covers **both**
   amethyst_decree's `efn:stop`/`cataclysm:stun` and crystal_hydro_dome's
   `efn:horizontalstop`/`efn:verticalstop` in one method. Moving the whole method over in one
   piece (as the warning section already instructs) carries crystal_hydro_dome's check along
   automatically — no separate action needed here, just don't split the method up or drop the
   `CrystalHydroDomeAoe.EFN_HORIZONTALSTOP_ID`/`EFN_VERTICALSTOP_ID` block while moving it.

8. **Update the liming datapack's cast command — outside this repo, easy to forget.**
   `Origins/liming/data/pers/powers/liming/active_i/skill1.json` runs
   `cast @s bhspellsx:crystal_hydro_dome`. After the merge the spell id is
   `bhspells:crystal_hydro_dome`; change that one command or the button silently does nothing
   (mana is still spent by the Apoli side). Origin id stays `pers:liming`. The local test copy
   of `origin_layers/origin.json` is not part of the delivery.

9. **Not a merge step — flagging so it isn't mistaken for one:** during Phase 1 testing, the
   Modrinth App profile on the local test machine started rejecting any `bhspellsx-*.jar`
   copied directly into its `mods\` folder ("needs repair or re-import"), because that launcher
   only trusts mod files added through its own "Upload files" UI (see
   `bhspellsx/CLAUDE.md`'s Testing loop section). This is purely a local dev-machine deployment
   quirk of one specific launcher on one specific test setup — it has nothing to do with
   bhspells itself or the merge, and doesn't need any accommodation in the merged mod, the
   `mods.toml`, or the build. Ignore it here.

---

## mods.toml (applies to all three spells)

No action needed on the bhspells side; bhspells already declares its own
`irons_spellbooks`/`irons_lib`/`traveloptics` dependencies (it already calls traveloptics
from `EternalPurificationSpell`, which is what Phase 2B's VFX was modelled on).
`bhspellsx`'s `mods.toml` is deleted along with the rest of the bootstrap.

`amethyst_decree` has **no** hard `mods.toml` dependency on `efn` or `cataclysm` — this is
deliberate (see the mob effect warning above), not an oversight to fix. Don't add one.

---

## Verifying after merge

Common: grep the merged bhspells source tree for `bhspellsx` (package, string literal, or
asset path) — it should return nothing. The moved classes should compile with zero
references to the bootstrap mod (no `BHX*` imports left).

### embracing_bosom

Build bhspells, deploy to the modpack, confirm `/cast bhspells:embracing_bosom` still
works under its new identity. What "working" looks like (this is the full Phase 0-2C
feature set, not a placeholder):
- **On cast:** an inward-converging amber dust burst (radius 6 → 0.4 over ~8 ticks) at the
  caster's feet.
- **The ring:** five layered, independently-rotating amber ring/haze textures spawn
  oversized and ease inward to their resting radius with a brief rotational "spin-up,"
  fading in as they land; they then rotate continuously and hold for the AoE's ~8s
  lifetime, fading out (with a slight inward contraction) over the last second.
  **Check this both with and without an Oculus/Iris shaderpack loaded** — see the warning
  section at the top of this file. It looking correct unshadered proves nothing about the
  shaderpack case.
- **The column:** a sparse rising column of white END_ROD sparks plus amber dust near the
  base, sustained for the AoE's whole lifetime.
- **Ambient particles:** small amber leaves drifting slowly upward/outward (low density,
  long-ish lived) and small brighter amber motes rising quickly (higher density,
  short-lived, full-bright/glowing) — both taper off over the AoE's last second, together
  with the ring's fade-out.
- **Mechanics:** heals players in the zone every 20 ticks, applies the Embracing Bosom
  buff every tick (20% damage reduction while active, harmful effects on the target expire
  30% faster, both linger ~2s after leaving the zone), and the buff icon shows the real
  art (amber ring/motes), not a flat placeholder circle.

### amethyst_decree

Cast it against another player with Epic Fight Nightfall and L_Ender's Cataclysm both
present, confirm:
- **On cast start:** a low quiet resonate; small violet-blue crystals begin rising sparsely
  around the caster over the 1s channel, with a handful of light chimes as they do.
- **On cast completion:** ~30 damage burst to everyone in the (10-block) radius; several
  layered crystalline "cluster-place" sounds landing as one surge; large burst spikes erupt
  in a structured ring + scatter pattern; each hit target gets its own crystal encasement
  around the legs and a UUID-following crystal that keeps up with them if they're not fully
  rooted.
- **Root/stun (60 ticks):** target is hard-rooted (can't move) and stunned; Slowness II,
  Weakness I, Mining Fatigue II applied (100 ticks).
- **At tick 60:** the leg encasement shatters (a satisfying "cluster-break" sound, the
  second-loudest cue) and gives way to a smaller leg-crystal cluster that shrinks over the
  remaining DoT.
- **DoT (4 ticks × 20 = 80 more ticks):** 5 damage every 20 ticks, with very sparse faint
  chimes, low volume.
- **Then re-test with Epic Fight Nightfall or L_Ender's Cataclysm absent** (temporarily
  remove one from the test instance) — confirm the re-homed mob effect check (see the
  warning section) actually logs its error, and that the spell still deals damage/debuffs/
  DoT with just that one CC component missing rather than crashing.

### crystal_hydro_dome

Verified in-game (gameplay through Phase 1, VFX through Phase 2D, 2026-09-17) — this is the
confirmed-working behavior to match after merge, not a placeholder spec. Full agreed spec
and the reasons behind it: `docs/crystal_hydro_dome_decisions.md`.
- **Cast:** `CastType.CONTINUOUS`, 120-tick (6s) cast time — real Iron's casting animation +
  cast bar for the whole duration, not an instant no-bar cast. `onCast()` fires every 10
  ticks per Iron's own CONTINUOUS pulse cadence (including the terminal pulse), but the dome
  and its open burst spawn only on the first pulse
  (`playerMagicData.getCastDurationRemaining() == playerMagicData.getCastDuration()`) —
  casting the skill never produces a second, cast-less/mana-less dome.
- **Natural end:** driven by cast completion — `onServerCastComplete(cancelled=false)` calls
  `CrystalHydroDomeAoe.naturalEndFor()` (end heal + knockback). The dome's own tick-based
  check is a fallback only, at `DURATION_TICKS + FALLBACK_END_GRACE_TICKS` (tick 125), in case
  cast state is somehow lost — it can only end an already-alive dome, never spawn one.
- **Early break:** `onServerCastComplete(cancelled=true)` — opening a container, casting
  another skill, death, logout, dimension change — breaks the dome immediately (no end heal,
  no knockback) via `CrystalHydroDomeAoe.endActiveDomeFor()`, and the cast bar disappears
  right away rather than lingering.
- **Caster movement effects:** `efn:horizontalstop` + `efn:verticalstop` reapplied every tick (NOT `efn:stop` —
  see this file's earlier note on why: `efn:stop`'s client mixins block all input, not just
  movement). `horizontalstop` alone only slows the player; walking, jumping, and attacking
  still work (tested 2026-09-26). The dome's observed movement restriction may also come
  from CONTINUOUS casting; the pair has not been tested independently, and `verticalstop`
  alone has not been tested. Do not treat this as a confirmed position lock.
  Chat/inventory can't be opened during the cast (observed in testing, accepted) — opening a
  container cancels the cast and breaks the dome.
- **Size:** hemisphere radius/height 10 blocks; knockback ring 10–13 blocks out, -2..+11
  vertical.
- **HUD:** action-bar HP readout (green Thai label + red heart + white/red hp/max, switching
  to red under 30%), caster-only, coalesced to at most one send per tick, clearing
  immediately on any dome end.
- **VFX while alive** (check with and without an Oculus/Iris shaderpack):
  - Translucent cyan shell with brighter edges and a bright band at ground level.
  - Water pattern on the shell: 24 soft wavy streaks flowing slowly around the dome and
    fading in/out, plus short bright flecks.
  - Three-layer lotus: pink outer/middle petals, gold inner petals, two gold wind ribbons in
    the center, native glow sparks.
  - 24 small pink petals drifting up through the whole dome; ground ring + arcs with two
    gold pulses moving inward.
  - Thin, fast yellow lightning flickering over the shell surface.
- **Counter bolt:** a yellow bolt from a point on the dome surface near the attacker (tilted
  up to 20° per bolt) all the way to the attacker, at any distance. An AoE that Counters
  several players in one tick shows one bolt each (up to 8 per tick).
- **Natural end:** shell swells to ~13 blocks and fades, water rings sweep out to ~13.5
  (the knockback zone), lotus spreads to ~10 blocks and fades, drifting petals fly outward;
  holy + amethyst chime sound.
- **Early break:** shell cracks into irregular shards that hold for a moment, then fly out,
  fall and fade; lotus folds shut and sinks into the ground; everything else fades; layered
  glass/ice crash with a short tinkling tail.
- **After an end:** the entity lingers ~1.5s for the animation, but damage redirect, Counter
  and projectile blocking stop at the moment of the end, and cooldown starts then too.

Once all three spells are confirmed, this `bhspellsx` repo can be archived or deleted — it
has no further purpose after a successful merge. (If more phases are planned, keep the repo
and start the next phase's content in a fresh subpackage under `net/offkung/bhspellsx/...`
instead.)

---

## Jing Guang Pan — เนตรชำระศักดิ์สิทธิ์ (净光判) — merge handover

The gold toggle remains datapack-driven; each wave is now one Iron's instant cast,
`bhspellsx:jing_guang_pan`, school `bhspells:gold`. The source datapack is `Origins/bai_long_lian`, origin `pers:bai_long_lian`. It owns the
`base:spell/mana` drain (2 per 20 ticks), toggle and Apoli creative-flight permission.
`pers_jing_guang_pan` is the server authority tag. Java sends explicit state/session
packets to the caster; scoreboard tags are not assumed to synchronize to the client.
The visible `active_self` power carries the verified Traveloptics `spectral_blink.png`
icon, final Thai name/description and cooldown 0. The hidden `toggle` has no icon field.
The drain follows the existing snake pattern: Apoli can debit on the first eligible
tick, not necessarily one full second after enabling. Minimum mana to enable is 2;
the post-debit zero check disables immediately, with a 1-tick watchdog for external
mana depletion or Java removing the tag. No additional mana cost for a wave.

### Merge files and dependencies

- New Iron's spell: `spells/gold/JingGuangPanSpell.java`; integrate its entry in
  `registry/BHXSpellRegistry.java` and name/guide in `assets/bhspellsx/lang/en_us.json`.
- `assets/bhspellsx/textures/gui/spell_icons/jing_guang_pan.png` is a **temporary icon**,
  copied byte-for-byte from Traveloptics 6.3.0's
  `assets/traveloptics/textures/gui/spell_icons/spectral_blink.png`. Iron's 3.16.1
  `getSpellIconResource()` is final and derives the path from the spell namespace/name;
  it cannot directly use the cross-mod texture path used by Apoli.

- Portable Java: `entity/spells/jing_guang_pan/JingGuangPanConstants.java`,
  `JingGuangPanManager.java`, `JingGuangPanWave.java`, `JingGuangPanCombos.java`,
  `JingGuangPanPrediction.java`;
  `event/JingGuangPanEvents.java`; `client/JingGuangPanClient.java`.
- Integrate bootstrap `network/BHXNetwork.java` and the event/network wiring in
  `BHSpellsX.java` into the destination mod's networking/event setup.
- Integrate both accessors in `registry/BHXAnimationRegistry.java`; copy both clips
  and their small `data/` metadata JSONs under
  `assets/bhspellsx/animmodels/animations/biped/spells/` (rename namespace at merge).
- Since Phase 1, Epic Fight **20.14.17** is a `compileOnly` dependency. Phase 2 adds
  Invincible **20.14.8.2** as `compileOnly`. Both exact deployed jars go in ignored
  `libs/`; neither dependency is bundled. `META-INF/mods.toml` now declares both
  mandatory minimum runtime requirements: Epic Fight `[20.14.17,)` and Invincible
  `[20.14.8.2,)`, BOTH sides, AFTER ordering. Minimum ranges allow newer versions;
  compileOnly stays pinned to the verified deployed APIs. Both mods remain mandatory
  because Java calls their APIs directly; optional mixins do not make Invincible itself
  optional. EFN/Avalon are not additional compile dependencies.
- Carry the three mixin classes listed below, `bhspellsx.mixins.json`,
  `bhspellsx.invincible.mixins.json` and the jar
  manifest's `MixinConfigs` entry into the target build. Targets use mod-owned,
  unmapped names/descriptors with `remap=false`; no vanilla injection/refmap is used.
- `tools/mirror_judgement_cut.py` is an authoring tool, never a packaged asset.
- Technical instructions were moved byte-for-byte from `CLAUDE.md` to `AGENTS.md`;
  `CLAUDE.md` contains only `@AGENTS.md`. The existing deployment rule text is intact;
  deployment requires the current task's explicit authorization.

### Input mixins and their scope

1. `mixin/client/EpicFightInputMixin` injects `ControlEngine.maybeAttack` HEAD to
   replace basic attacks while active, and `handleEpicFightKeyMappings` HEAD to
   discard only a reserved BASIC_ATTACK. It does not clear dodge/guard/innate reserves.
   EF `SKILL_CAST_EVENT` is also canceled for BASIC_ATTACK on client and server.
   The event alone is insufficient for input cleanup: `ControlEngine.maybeAttack`
   can still call `reserveKey` after an unsuccessful cast and then `lockHotkeys`.
   The HEAD injection prevents those side effects and clears an existing basic reserve.
   This Epic Fight mixin remains required, with `defaultRequire: 1`.
2. `mixin/client/InvincibleInputMixin` injects `InputManager.testPressedTime` HEAD
   to reject only combo types containing a KEY_1..KEY_4 actually bound to mouse-left.
   `onClientTick` HEAD clears only those input cache entries; right-click/dodge/other
   key entries remain. Pure right-click and DODGE/WEAPON_INNATE types are not blocked.
3. `mixin/InvincibleComboMixin` injects the typed overload
   `ComboBasicAttack.executeOnServer(SkillContainer, ComboType, int, long)` HEAD.
   This is needed because Invincible's own `SkillContainerMixin.requestCasting`
   bypasses Epic Fight's normal SKILL_CAST_EVENT. Only types containing a left-bound
   key are canceled. The client reports its four-key mouse-left binding mask with
   the current session, since the server has no access to client key bindings.
   The mask cannot select DODGE/WEAPON_INNATE. It is input metadata, not an anti-cheat
   guarantee for a modified client. Custom skills that bypass this overload and
   execute nodes directly are outside this hook.

The two Invincible mixins use a separate config with `required: false` and
`defaultRequire: 0`. Missing target classes can be skipped, and missing injection
methods do not fail the required-injection count. This is the simplest optional-hook
setup; the Epic Fight config keeps its original strict behavior. If an Invincible
update prevents these hooks from applying, left-click with an Invincible weapon may
still execute its combo while Jing Guang Pan is active. Check startup warnings and
retest after updates. These settings do not guarantee compatibility with arbitrary
changes to Invincible APIs called directly by Java, and cannot bypass the mandatory
dependency check when the entire mod is absent.

Every mixin is a no-op while the skill is off. Forge mouse-left press interception
replaces vanilla hits/block breaking; server AttackEntityEvent/BreakEvent also reject
the vanilla paths. No global attack suppression, other-skill cancellation, or blanket
Invincible cache reset is installed. Previously running unrelated attacks are not
retroactively canceled. Client classes/mixins are loaded only on the physical client.

### Animation, mirror and timing

- Right: `bhspellsx:biped/spells/judgement_cut` (trimmed to source frames 0–30).
- Left: `bhspellsx:biped/spells/judgement_cut_left`.
- Both are non-looping BIPED StaticAnimation, speed 1.05, zero transition,
  COMPOSITE_LAYER / HIGHEST, no mask. Their full pose replaces lower-priority living
  motion, including creative flight. Input playback uses `playAnimationInstantly`
  to begin frame zero without spending a tick in LinkAnimation. Other animations at
  the same/higher priority can still interrupt; no movement/camera state is locked.
- Run `python tools/mirror_judgement_cut.py` from any directory. It exchanges all
  eight `_R`/`_L` pairs: Thigh, Leg, Knee, Shoulder, Arm, Hand, Tool and Elbow; the four
  central joints stay named Root, Torso, Chest and Head. Each row-major 4x4 matrix is
  reflected as `S M S`, with `S=diag(-1,1,1,1)`. Translation X changes sign; the
  rotation's XY/XZ/YX/ZX terms change sign (rotation about Y/Z reverses). Reflection
  is across the character's YZ plane. Epic Fight's Blender-to-Minecraft root Rx(-90)
  leaves X unchanged and commutes with S. Swapping joint bases alone or only negating
  translation would be wrong. The script checks double reflection and never writes
  the original; timestamps, scales and duration are preserved.
- First click/right, then alternate left/right at each accepted client restart.
  Natural end or disabling resets the next attack to right without resetting wave
  spacing. Prediction uses a client tick clock, last start and predicted wave release,
  never accessor elapsed time: an interrupted/missing accessor cannot bypass the cut.
  Both clips keep frames 0–30 exactly, including Root, and last 0.5/1.05 seconds
  (~9.52 ticks). The left clip is regenerated with the existing mirror script.
  The live natural LayerOffAnimation is adjusted to 4 ticks, blending to current
  lower-layer flight/idle. EF resolves its real animation to EMPTY, so this fade
  advances at 1x. No delayed stop is scheduled; a newer slash cannot be stopped by
  the old fade. This works for rendered observers and continues after disabling.
- Tunables in `JingGuangPanConstants`: speed **1.05**; release **2 ticks**; cut
  **5 ticks**; buffer **4 ticks**, capacity one; range **7 blocks**; travel speed
  **2.8 blocks/tick**; shared swept crescent hitbox (Phase 3 below); damage **4** using
  **bhspells:gold_spell_bypass**; boost **4 blocks / 12 ticks**; clip end **frame 30**
  (changing this requires resource trimming and mirror regeneration); blend out
  **4 ticks**; glide downward cap **0.12 blocks/tick**. Phase 3 replaces vanilla dust.
  Datapack tunables: activation threshold **2 mana**, debit **2/20 ticks**, watchdog
  **1 tick**, cooldown **0**. Client cut is 5 ticks; server minimum is 4 with the 1-tick tolerance. Release remains 2 ticks; only the client buffers.
### Flight, wave and lifecycle behavior

Activation runs the same anchored smoothstep curve on both sides:
`height * (3*u*u - 2*u*u*u)`, with `u = tick / duration`. Height and duration are
constants. Absolute Y targets prevent double addition when client movement packets
arrive; X/Z and look remain unchanged. Both sides sweep the player's bounds against
ceilings (16 binary-search iterations if obstructed) and stop the remaining boost
on collision. Session-scoped start/stop packets and one final server position
correction replace per-step teleport handshakes. Client interpolation supplies
between-tick motion. Java waits for Apoli's `mayfly` grant before setting `flying`
once; no velocity impulse is used. Disabling turns flying off but does not strip
`mayfly` from other sources.
Glide clamps only downward velocity on both sides, resets fallDistance and cancels
LivingFallEvent through the landing tick. Ground (`onGround`, matching origins:on_block),
water/bubbles, climbable blocks, reactivation and dimension change end it; no
action_on_land dependency. Death/logout/respawn/dimension change clear the tag/session.
Datapack removal also clears its tag. Physics under the full Epic Fight pack still
requires in-game verification.

Each eligible click starts a predicted visual and sends session + monotonic sequence.
Ineligible clicks do not play or alternate; only one click within the four pre-cut
ticks is buffered. Both sides share `readyAt` for cut/release spacing.
Server checks active state, rejects old/duplicate requests, allows only one pending
release and enforces at least 4 ticks between waves (client cut 5 minus server tolerance 1). It has no input buffer or lag
compensation. A rejected network request can leave a predicted visual without a wave;
the server replies with a retry delay, which resets logical combo state only for the
current session/latest prediction and never stops the already shown pose. Older replies
cannot roll back newer predictions. No positive acknowledgement or caster playback
echo is required. Observers receive accepted animations. Protocol is **6**, so
both sides must use the updated jar.
Disabling clears the client buffer and pending server release but lets the current
visual finish. Already emitted waves finish their travel; dimension changes discard
old-dimension waves.

The wave is a server-only swept set of 12 convex prisms, **not an entity**. It starts
at the actual eye/hitbox position, samples yaw/pitch at release, and travels 2.8 blocks/tick.
Shared geometry tests block collision shapes and living-entity AABBs, stopping at the
first contact; blocks win ties. Range is 7 blocks measured at the leading edge, using
WAVE_THICKNESS/2 (.11) to subtract the leading projection from maximum anchor travel. No vanilla
dust remains. The client renderer replays the confirmed path without extending damage.
Damage is **4, bhspells:gold_spell_bypass**, with caster attribution, no Spell Power
or weapon scaling. Deployed bhspells 1.3.0 already includes this type in
`minecraft:bypasses_cooldown`; no damage-type files/tags are added here. Base sustained
DPS is **16** at 20 TPS before armor, crits and external elemental modifiers.
No animation attack phase or slash entity is added. Visual speed tails and selected sound cues are described below.

The old occasional 8→12 has a matching external cause in deployed
**ApothicAttributes-1.20.1-1.3.7.jar**: `AttributeEvents.apothCriticalStrike`
handles LivingHurtEvent at HIGH priority, requires a living attacker, rolls its
`attributeslib:crit_chance` (default 0.05), and multiplies by `crit_damage` (default 1.5).
It sends the cyan APOTH_CRIT particles seen alongside the spike. There is no damage-type
exclusion: both gold_magic and gold_spell_bypass qualify; new base 4 can become 6
(12 after an external x2). Equipment/powers may change the actual attributes. This is
a code-supported explanation, not a runtime event trace of the recording. Separately,
bhspells FireBodyHitEvent applies x1.5 when FireBodyManager is active at level >=3;
that state was not established in the clip. Neither multiplier is changed here.
The original Root translation remains visual and can move the model outside F3+B;
the mirror reverses that lateral offset. Do not use the rendered sword/model as the
wave's collision origin.

### One wave = one Iron's spell cast

`JingGuangPanSpell` is INSTANT, level 1, RARE, school `bhspells:gold`, with zero
base/per-level mana, power and cast time, and default cooldown 0. Fixed wave damage
continues to use the existing vanilla DamageSource with `bhspells:gold_spell_bypass`;
it does not switch to SpellDamageSource or apply Spell Power. `onCast` alone spawns
one wave; the wave helper owns its live list and ticks at the same ServerTick END.
The manager calls the registered spell at the existing 2-tick release deadline.
Successful casts alone update lastWave. Failed casts send the existing rejection for
the saved pending session/sequence, not the latest unrelated request.

Only this spell overrides `attemptInitiateCast`. It rejects if MagicData is already
casting, without canceling or changing that cast. Otherwise it checks canBeCastedBy
and checkPreCastConditions, posts cancellable SpellPreCastEvent, and invokes Iron's
castSpell synchronously with cooldown triggering disabled. castSpell still dispatches
SpellOnCastEvent, onCast and OnClientCastPacket. It opens no casting state and sends
no start/finish packets: the base initiation path would stop item use, cancel another
cast, and queue even INSTANT spells until MagicManager's tick. No completion/reset
callback is needed for a state never opened. Start animation is none, finish is pass,
and both sounds are empty; the existing predicted Epic Fight pose is untouched.

Both left-click release and `/cast @s bhspellsx:jing_guang_pan 1` use this override
with CastSource.COMMAND (no Iron's mana consumption). The command works while the
mode is off: one wave, no mode activation and no animation. The wave requires a
ServerPlayer owner; unsupported non-player command targets fail the precondition.
The command's return count is not proof of success because Iron's CastCommand ignores
the boolean result for players. Direct server API callers receive the boolean.

Traveloptics Blackout/Casting/Frozen Sight and Geomancy's Casting effect can reject
through SpellPreCastEvent. Their own warning messages/sounds and held-item event
reactions remain external behavior; no global suppression is added. Counterspell
cannot interrupt this synchronous instant between ticks or erase a non-entity wave
already emitted. No persistent casting state or recast is created. Left-click cut,
buffer, prediction, alternating sides, flight/boost/glide and datapack mana are unchanged.

### Required in-game checks

Test empty hand and arbitrary held weapons; creative flight and camera turning during
both full clips; right-left-right replay and reset after end/off; early ignored clicks,
one buffered click, and restart at/after cut; mana drain/zero and off before the 2-tick
release; no extra wave mana; ceiling-safe one-time boost; glide to ground/water/ladder,
reactivate and change dimensions without fall damage; aim upward/downward, nearest
living target vs wall, 7-block leading-face limit and bypass i-frame DPS; no adjacent incidental
damage or slash entities; selected sound cues and visual-only tails; F3+B model offset; two-client playback; Invincible
left/compound-left blocked while right/dodge/other keys work, including rebound keys,
then all normal input restored with skill off. Runtime tests are not replaced by build.


### Jing Guang Pan - Phase 3, step 1: crescent wave VFX

Own renderer; no additional dependency. The pibo plan is cancelled and replaced by the halo. Animation,
input prediction, cast timing, mana/flight/boost/glide and damage remain unchanged.
`JingGuangPanSpell.castWave` carries the accepted left/right side through the synchronous
Iron cast; direct `/cast` defaults to the right wave without playing an animation.

- Shared `entity/spells/jing_guang_pan/JingGuangPanWaveShape.java`: 12 convex prisms,
  radius 1.0, arc 150 degrees, maximum band width 0.22, depth 0.24, right/left roll
  -22/+22 degrees plus server jitter in [-4,+4). Ends taper to points. Renderer and continuous SAT collision use
  the same vertices. Block voxel boxes and living-entity AABBs are swept against
  actual prisms; enclosing AABB is only a broad phase. Earliest contact wins;
  blocks win ties. Leading face stops at 7 blocks; anchor travel is derived from the shared shape.
- `JingGuangPanWave.java` removes server dust and sends cumulative confirmed path
  updates (normally three per wave) through `BHXNetwork.WavePath` to caster and
  players tracking caster. Protocol is 6: update both client and server jars.
  Packets carry UUID, dimension, origin, direction, yaw, side, final roll, distance and end flag.
- Portable client package `client/renderer/jing_guang_pan/`: `JingGuangPanVfx`,
  `JingGuangPanWaveRenderer`, `JingGuangPanRenderTypes`, `JingGuangPanVfxConstants`.
  Bootstrap callback is installed by `BHSpellsXClient` during client setup.
  Replay only confirmed path, including when all packets arrive together, then fade
  for 2 ticks; no extension of damage or travel. Clear on world change, discard
  stale visuals after 20 ticks, cap at 256 waves, cull beyond 64 blocks.
- Alpha body plus SRC_ALPHA/ONE additive flow/glow/sparks, depth tested, no depth
  writes, double sided. Uses the existing Oculus-compatible entity translucent
  emissive shader getter, NEW_ENTITY, full-bright and NO_OVERLAY; no custom shader
  or shader mod required. Luminous geometry does not guarantee bloom or world light.
  Actual Oculus/shaderpack appearance still requires in-game QA.
- Body alpha .82, glow alpha .14 / width 1.8x, flow alpha .50 / width 1.25x,
  scroll .025 U/tick, 8 sparks with half-size .045-.075 blocks. Render mesh has 72
  segments versus 12 collision prisms. Core draws upper/lower surfaces; blade depth .24,
  radial band .22 with sin(pi*t)^.7 taper; tip alpha fade covers 14% at each end.
  A camera-facing leading-edge ribbon (.065 width, .9 alpha) and 3 short visual tails
  (.9 length, .045 width, .38 alpha) improve edge-on visibility without changing collision.
- Textures in `assets/bhspellsx/textures/vfx/jing_guang_pan/`: `crescent_core.png`
  512x128, `energy_flow.png` 512x128, `soft_glow.png` 256x64, `spark.png` 64x64.
  Straight RGBA, transparent transverse edges; flow/glow seamless along U.
  Warm gold D8AE48, dark gold 98702B, cream FFF3CF, warm white FFFBEF, jade 83BDB3.
  Dark gold preserves daylight contrast beneath luminous lines.
- Regenerate with Python + numpy + Pillow: `python tools/generate_jing_guang_pan_vfx.py`.
  Palette and procedural parameters live in the script. Reference previews (not game
  screenshots): `tools/previews/jing_guang_pan/textures_dark_light.png` and
  `waves_right_left.png`. Only four texture PNGs ship; script/previews stay outside jar.

Validate without shaders and with Oculus: daylight/night, both slash directions,
vertical aim, thin targets, block edges and empty crescent interior; nearest target
only, range 7/speed 2.8/damage 4 gold_spell_bypass, two clients, short point-blank
waves and batched packets. No impact VFX is introduced; the status halo is described below.


### Wave roll and dummy rendering compatibility

- Base roll: right **-22 degrees**, left **+22 degrees**. `WAVE_ROLL_JITTER_DEGREES = 4.0`
  adds a uniform offset in [-4, +4) degrees, sampled once per wave on the server.
  The final `rollDegrees` double drives server swept-prism geometry and is sent in every
  WavePath update. Clients construct the same geometry from it without resampling.
  `/cast` without animation uses the right-side default plus the same jitter.
- **Dummy compatibility warning (user's in-game test):** hitting MmmMmmMmmMmm's dummy,
  even with a prior bare-hand hit, makes waves displaced/north-only on the attacker's
  screen, while an observer sees correct positions. Test wave appearance against mobs
  or players, not the dummy. This is a rendering interaction, not evidence of incorrect
  spell damage/collision. The exact cause is NOT established from code alone.
- Recon: deployed `dummmmmmy-1.20-2.0.12-forge.jar`. `TargetDummyRenderer` uses the normal
  entity path with armor/cape/shield/elytra layers. `DamageNumberParticle.render` runs in
  the particle pass, creates a local PoseStack, enables depth/blending, changes blend
  factors, and flushes Minecraft's shared BufferSource with endBatch(). It has no explicit
  restoration of incoming depth/blend state (text RenderTypes can reset state at flush).
  No direct global model-view/projection writes or leaked event PoseStack were found in
  this mod. Hay uses particles; DPS uses the vanilla action bar and health uses a boss bar.
- Damage numbers go to the dummy's attacker tracker (300-tick timeout), while body hit
  animation goes to all tracking clients. This explains a different rendering workload
  after a prior punch and on attacker versus observer. Our wave runs AFTER_PARTICLES with
  shared buffers, making interaction plausible. It does not prove spatial displacement:
  blend state alone cannot explain north-only visibility. Vanilla/other-mod immunity
  and the exact corrupt state still require a runtime frame/state capture.
- No renderer fix here. Smallest future diagnostic: disable dummy damage numbers alone,
  then DPS separately, and compare matrices/buffer state at AFTER_PARTICLES. If particle
  stage contamination is confirmed, test moving only the wave event stage to AFTER_ENTITIES
  (before particles), verifying transparency/Oculus; or restore only the state proven to
  leak. Do not replace coordinate transforms speculatively. Why the reverted isolated-
  buffer/matrix-reset renderer disappeared entirely remains unproven without a capture
  of that build; mathematical coordinate tests do not validate the in-game render pipeline.


### Halo renderer and status synchronization

The halo replaces the cancelled pibo/ribbon plan. It is visual-only and follows interpolated
player position/body yaw, upright in world Y; it never follows Epic Fight chest joints.
New client files in `client/renderer/jing_guang_pan/`: `JingGuangPanHaloConstants`,
`JingGuangPanHaloVfx`, `JingGuangPanHaloRenderer`. Register `BHXNetwork.clientHalo` in
client setup. Constants hold dimensions, opacity, timing, palette, mesh quality and spark tuning.

- Center 1.75 blocks above feet, diameter 1.65, back gap .30 beyond body half-depth .125.
  Ray plane radius 1.23; rim tube radius .018. Render AFTER_ENTITIES, before particles,
  using its own HaloRenderTypes factory (alpha/additive, depth test, no depth writes).
- Open over 8 ticks: smooth alpha 0->1 and scale .85->1. Close fades over 8 ticks.
  Rays rotate 3 degrees/sec; brightness pulse +/-4% per 80 ticks. Twelve small sparks.
- Owner-only rear inner light: smoothstep of camera-to-back dot from 0 to .85; minimum intensity .10.
  Multiply the inner alpha-blended pass by (1-rearWeight), reaching ZERO at the rear.
  Keep only the inner additive pass at the rear, which cannot darken the player's colors.
  Ring, rays and sparks do not receive the rear fade. First-person self is fully hidden;
  isInvisible hides immediately (including teammates), with no fade or position leak.
- Protocol **6**, separate Halo packet id 7: player UUID, dimension, monotonically
  increasing server revision, activation age, active flag and immediate-clear flag.
  State transitions go to tracking players and self. StartTracking sends active snapshots;
  StopTracking clears that viewer. Existing clear paths broadcast immediate removal on
  death/logout/dimension change/respawn. Client drops old-world/dead/removed entities and
  allows a short spawn-packet grace interval. No extra per-tick network position packets.
  Control-state, mana, flight, wave combat and wave renderer remain unchanged.
- Four original procedurally generated textures: halo_ring 512x512, halo_inner 256x256,
  halo_rays 512x512, halo_spark 64x64, RGBA straight alpha with transparent margins.
  Artwork is generated by `tools/generate_jing_guang_pan_halo.py`, inspired by the user's
  gold-white halo reference (no wings; no copied reference pixels). The generator now
  writes directly to `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/`;
  it reads primary tuning from HaloConstants. Previews remain under tools/previews only.
  Earlier tools/generated halo copies have been removed.
- In-game verification still required: two clients, joining tracking while already active,
  leaving/re-entering range, death/respawn/logout/dimension change, rapid toggle, invisibility,
  first-person self, body yaw while flying/shooting, daylight/night and Oculus shaderpacks.
  Use players or mobs, never the target dummy for visual validation. Changing the halo's
  render stage is not a claimed fix for the separate existing wave/dummy interaction.


### Halo brightness correction (2026-09-27)

Halo rendering now uses its own `JingGuangPanHaloRenderTypes`, with the vanilla eyes shader
and explicit SRC_ALPHA blend factors, culling disabled, depth test and no depth writes.
Do not substitute vanilla RenderType.eyes(), whose blend defaults differ. The wave factory
and wave assets are unchanged. The previous entity-translucent-emissive vertex shader
applied minecraft_mix_light (0.4..1 brightness) despite FULL_BRIGHT UV2, and its fragment
shader discarded texture alpha below 0.1. Eyes removes both diffuse shading and that cutoff.
Verified Oculus 1.8.0 maps the getter to ENTITIES_EYES / SpiderEyes / FULLBRIGHT; shaderpacks
can still override blending, fog and post-processing, so actual appearance needs in-game
verification. Both vanilla shader paths apply fog fade, not fog-color tint; no blanket fog
removal was introduced without evidence of it causing the reported near-player problem.

All four halo and four wave textures had warm RGB, not black, at zero alpha. Filtering is
bilinear with mipmaps disabled, so no black-fringe repair was warranted. Halo ring/inner
pigment is now 55% gold + 45% cream; ray pigment is 75% gold + 25% cream. Deep gold is limited
to a narrow outer ring shoulder (24% mix), replacing the broad 32% dark-gold contribution.
The generator/preview now uses bilinear RGBA sampling instead of nearest-neighbor sampling;
its alpha and additive compositing match the intended custom blend states, with unit diffuse
lighting and no near-range fog. It does not simulate shaderpack post-processing. No in-game
screenshots were accessible in the request; conclusions are based on code/assets and the
reported symptoms, not a claim of a verified in-game visual match.


### Owner-only rear halo fade (2026-09-27)

Rear fade applies only when the rendered halo's player is Minecraft's local player.
The owner retains the existing 10% additive-only rear view, unchanged front view and
first-person hiding. Observers always use the full front-view inner alpha/additive passes,
regardless of viewing angle. Body-yaw orientation is unchanged. Existing preview rear panels depict the
owner's view, not the newly full-strength observer rear view.


### Current aura: gold sparkle only (2026-09-28)

The full-body renderer motes have been removed; halo and rim sparks stay unchanged.
HaloVfx emits only client-local bhspells:gold_sparkle using existing synced halo state.
End-rod emission and its ID/rate constants have been removed. Gold settings are unchanged:
1 particle every 10 ticks (2/sec/player at 20 TPS), annulus .35-.70 blocks, initial height
.10-1.80, outward velocity .0025 and upward .006 blocks/tick, render distance 64 blocks.
Emission requires active/alive/visible player and excludes first-person self; no extra
packets or forced particle setting. Existing particles finish their own lifetime after
emission stops. AURA_GOLD_SPARKLE_ID supports parameterless SimpleParticleType replacements.

GoldSparkleParticle in bhspells 1.3.0 accepts supplied initial velocity directly, then vanilla
particle ticking applies its .05 gravity and drag; it rises briefly and falls rather than
maintaining the requested upward speed. Lifetime is 15-24 ticks. No physics override.


### Jing Guang Pan chosen sounds (2026-09-28)

Source credit: **[ที่มาเสียง]** (credit/link not supplied; fill before publication).
User-provided asset/bai_long_lian/skill1-1.mp3 (2.088s), skill1-2.mp3 (8.04s), skill2.mp3
(1.296s) converted to mono 48kHz Ogg Vorbis under sounds/jing_guang_pan/activate_1.ogg,
activate_2.ogg and wave.ogg. No trimming or source-file edits. Register three events in
BHXSoundRegistry and sounds.json. Activation plays both open events on the SAME server tick,
not as random alternatives. Each volume .35, pitch 1. Wave sound plays only when an actual
wave spawns (including /cast), volume .18 and server-random pitch .95-1.05. Rejected/buffered
clicks alone make no shot sound. The 1.296s source tail overlaps at four shots/second.

Living-target contact plays epicfight:entity.hit.blade at the wave's collision position,
volume .22 / pitch 1.1, including contact with a damage-immune target; block contact stays
silent. Normal active->inactive transition (including mana exhaustion) plays vanilla
block.beacon.deactivate, volume .20 / pitch 1.25. Login/logout/death/dimension cleanup does
not invent additional toggle sounds. JingGuangPanSounds holds all cue volumes/pitches.
ServerLevel.playSound uses the PLAYERS category and vanilla network delivery to caster and
nearby players; no client prediction double-play or protocol change. Full clips play to
completion; toggling off does not stop the previous activation tail. In-game sound balance
and rapid-fire overlap still need listening tests with mobs/players (not the target dummy).

### Complete Jing Guang Pan handover file inventory

Paths below are relative to bhspellsx. Shared wiring files contain other spells: merge
only the Jing Guang Pan entries, do not overwrite the destination mod's registries.
Rename Java package/resource namespace bhspellsx to bhspells consistently when integrating;
retain external pers, epicfight, minecraft and existing bhspells references.

- `AGENTS.md`
- `CLAUDE.md`
- `MERGE.md`
- `build.gradle`
- `src/main/java/net/offkung/bhspellsx/BHSpellsX.java`
- `src/main/java/net/offkung/bhspellsx/BHSpellsXClient.java`
- `src/main/java/net/offkung/bhspellsx/client/JingGuangPanClient.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanHaloConstants.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanHaloRenderTypes.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanHaloRenderer.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanHaloVfx.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanRenderTypes.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanVfx.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanVfxConstants.java`
- `src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanWaveRenderer.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanCombos.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanConstants.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanManager.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanPrediction.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanSounds.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanWave.java`
- `src/main/java/net/offkung/bhspellsx/entity/spells/jing_guang_pan/JingGuangPanWaveShape.java`
- `src/main/java/net/offkung/bhspellsx/event/JingGuangPanEvents.java`
- `src/main/java/net/offkung/bhspellsx/mixin/InvincibleComboMixin.java`
- `src/main/java/net/offkung/bhspellsx/mixin/client/EpicFightInputMixin.java`
- `src/main/java/net/offkung/bhspellsx/mixin/client/InvincibleInputMixin.java`
- `src/main/java/net/offkung/bhspellsx/network/BHXNetwork.java`
- `src/main/java/net/offkung/bhspellsx/registry/BHXAnimationRegistry.java`
- `src/main/java/net/offkung/bhspellsx/registry/BHXSoundRegistry.java`
- `src/main/java/net/offkung/bhspellsx/registry/BHXSpellRegistry.java`
- `src/main/java/net/offkung/bhspellsx/spells/gold/JingGuangPanSpell.java`
- `src/main/resources/META-INF/mods.toml`
- `src/main/resources/assets/bhspellsx/animmodels/animations/biped/spells/data/judgement_cut.json`
- `src/main/resources/assets/bhspellsx/animmodels/animations/biped/spells/data/judgement_cut_left.json`
- `src/main/resources/assets/bhspellsx/animmodels/animations/biped/spells/judgement_cut.json`
- `src/main/resources/assets/bhspellsx/animmodels/animations/biped/spells/judgement_cut_left.json`
- `src/main/resources/assets/bhspellsx/lang/en_us.json`
- `src/main/resources/assets/bhspellsx/sounds.json`
- `src/main/resources/assets/bhspellsx/sounds/jing_guang_pan/activate_1.ogg`
- `src/main/resources/assets/bhspellsx/sounds/jing_guang_pan/activate_2.ogg`
- `src/main/resources/assets/bhspellsx/sounds/jing_guang_pan/wave.ogg`
- `src/main/resources/assets/bhspellsx/textures/gui/spell_icons/jing_guang_pan.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/crescent_core.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/energy_flow.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/halo_inner.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/halo_rays.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/halo_ring.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/halo_spark.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/soft_glow.png`
- `src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan/spark.png`
- `src/main/resources/bhspellsx.invincible.mixins.json`
- `src/main/resources/bhspellsx.mixins.json`
- `tools/generate_jing_guang_pan_halo.py`
- `tools/generate_jing_guang_pan_vfx.py`
- `tools/mirror_judgement_cut.py`

Authoring-only previews: tools/previews/jing_guang_pan/ and
tools/previews/jing_guang_pan_halo/. Do not package tools, previews, tests or libs.
The right keyframe originated from EFN's
assets/efn/animmodels/animations/biped/yamato/dmcyamato_judgementcut.json; only its
keyframe was copied, then trimmed to frames 0-30. The two local assets/.../data metadata
files are our own layer/priority settings, not EFN trail_effects data.

### Team tuning locations

- Damage 4, damage type gold_spell_bypass, range 7, wave speed 2.8, client cut 5 ticks,
  server tolerance 1 tick (minimum 4), release 2 and buffer 4: JingGuangPanConstants.java.
- Boost 4 blocks/12 ticks and glide .12: same constants file.
- Mana/โซล 2 per 20 ticks: source Origins/bai_long_lian/data/pers/powers/bai_long_lian/active_i/
  jing_guang_pan_drain.json (debit + interval); jing_guang_pan_skill.json (minimum to enable);
  jing_guang_pan_watch.json (external depletion watchdog). Change related thresholds together.
- Wave visual settings: JingGuangPanVfxConstants.java; halo/aura: JingGuangPanHaloConstants.java;
  palette/texture generation: the two tools/generate_jing_guang_pan_*.py scripts.
- Sound volume/pitch: JingGuangPanSounds.java. Iron's default cooldown/mana 0: JingGuangPanSpell.java.

Datapack origin is pers:bai_long_lian. Supply its zip separately; the IT lead must ADD this
origin to the live server's shared origin layer without replacing existing entries.
The deliverable contains no origin_layers and no README. Client/server mod versions must
match protocol 6. Use mobs/players, never Dummmmmmy, for VFX validation; occasional x1.5
spell damage is Apothic Attributes critical damage (default 5%), not a skill defect.
