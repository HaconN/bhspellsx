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
   Mixins reference `efn:horizontalstop`/`efn:verticalstop` at all, so applying that pair
   together (one freezes X/Z, the other freezes Y) gets the same full position-freeze while
   blocking no input themselves — the caster can still look and attack. Separately, while the
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
- **Root:** `efn:horizontalstop` + `efn:verticalstop` reapplied every tick (NOT `efn:stop` —
  see this file's earlier note on why: `efn:stop`'s client mixins block all input, not just
  movement). Caster can look and attack while the dome is up; walking and jumping are locked.
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
