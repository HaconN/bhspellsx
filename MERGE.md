# Merging bhspellsx into bhspells

For the team lead, when it's time to fold this project's content into the real `bhspells`
mod source tree. This is a manual, deliberate step — nothing here is automated.

Note: as of the JPMS split-package fix, **all** source in this repo lives under
`net.offkung.bhspellsx` (never `net.offkung.bhspells`) — see `CLAUDE.md`. The merge
procedure below is what performs the `bhspellsx` → `bhspells` rename; it does not exist
anywhere else in this repo.

**This doc now covers two independent spells: `embracing_bosom` and `amethyst_decree`.**
They don't share any portable files, so each has its own "Portable content" list and its
own numbered procedure below (§A and §B) — only the namespace find-replace mechanics and
the final `mods.toml` check are common to both. Read the two ⚠️ warning sections below
before starting either one; they're not optional context, they're the two ways this merge
goes wrong silently.

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

## mods.toml (applies to both spells)

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

Once both spells are confirmed, this `bhspellsx` repo can be archived or deleted — it has
no further purpose after a successful merge. (If more phases are planned, keep the repo
and start the next phase's content in a fresh subpackage under `net/offkung/bhspellsx/...`
instead.)
