# Merging bhspellsx into bhspells

For the team lead, when it's time to fold this project's content into the real `bhspells`
mod source tree. This is a manual, deliberate step — nothing here is automated.

Note: as of the JPMS split-package fix, **all** source in this repo lives under
`net.offkung.bhspellsx` (never `net.offkung.bhspells`) — see `CLAUDE.md`. The merge
procedure below is what performs the `bhspellsx` → `bhspells` rename; it does not exist
anywhere else in this repo.

---

## ⚠️ READ BEFORE TOUCHING THE RING RENDERER

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

---

## What gets deleted

The mod entrypoint (`BHSpellsX`, `BHSpellsXClient`) and the whole `registry/` package
(`BHXSpellRegistry`, `BHXEntityRegistry`, `BHXMobEffectRegistry`, `BHXParticleRegistry`),
plus this whole repo's Gradle/CI scaffolding. None of it ships — it only exists to give the
portable content a real compiler and a real jar to test with.

## Portable content (current)

- `spells/ground/EmbracingBosomSpell.java`
- `entity/spells/embracing_bosom/EmbracingBosomAoe.java`
- `effect/EmbracingBosomEffect.java`
- `event/EmbracingBosomEvents.java` — no `@Mod.EventBusSubscriber` annotation, no modid
  baked in; registered manually (see step 5 below), so it copies over unmodified.
- `client/renderer/EmbracingBosomRingRenderer.java` — **read the warning section above
  first.** The Phase 2A ring VFX: five rotating layers, spawn-converge easing, fade in/out.
- `client/renderer/RingLayer.java` — the immutable per-layer config record
  `EmbracingBosomRingRenderer` reads from.
- `client/renderer/NoopEntityRenderer.java` — generic, not embracing_bosom-specific; check
  whether bhspells already has an equivalent before copying this one over too. (Only
  actually relevant if bhspells ever needs an unrendered custom entity elsewhere —
  `EmbracingBosomAoe` itself is rendered by `EmbracingBosomRingRenderer`, not this.)
- `client/particle/EmbraceLeafParticle.java` (+ inner `Provider`) — Phase 2C ambient leaf.
- `client/particle/EmbraceLeafParticleOption.java` — its tintable `ParticleOptions`.
- `client/particle/EmbraceMoteParticle.java` (+ inner `Provider`) — Phase 2C ambient spark.
- `client/particle/EmbraceMoteParticleOption.java` — its tintable `ParticleOptions`.
- `assets/bhspellsx/lang/en_us.json`
- `assets/bhspellsx/textures/mob_effect/embracing_bosom.png`
- `assets/bhspellsx/textures/entity/ring/*.png` (nine textures — the ring layer stack)
- `assets/bhspellsx/textures/particle/embrace_leaf.png`, `embrace_mote.png`
- `assets/bhspellsx/particles/embrace_leaf.json`, `embrace_mote.json` (particle definition
  JSONs — texture-list files, one per particle type)

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
   `EntityRegistry`/`MobEffectsRegistry` in step 5. Its `LIFETIME_TICKS` constant is `public`
   specifically so `EmbracingBosomRingRenderer` can read it — keep that visibility when you
   move the file (it's a deliberate cross-class dependency, not an oversight).

   `EmbracingBosomEffect.java`, `EmbracingBosomEvents.java`: package decl only, same
   pattern. `EmbracingBosomEvents`'s import of `BHXMobEffectRegistry` also gets repointed
   to `MobEffectsRegistry` in step 5.

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
   overwriting it. This is a blanket folder move, so it covers the ring textures, particle
   textures, particle JSONs, and the mob effect icon automatically — nothing extra to do
   per-file here as long as the whole folder moves together.

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
   particle sprite-set registration** — `BHSpellsXClient` has a second handler,
   `onRegisterParticleProviders` (`RegisterParticleProvidersEvent`), with two
   `event.registerSpriteSet(...)` calls (one per particle type, pointed at each particle
   class's `Provider`). This is easy to miss since it's a separate event from the entity
   renderer one — both need a home in bhspells' client entrypoint, or the leaf/mote
   particles will register their `ParticleType` but never actually render (no crash, they'll
   just silently never appear — same failure shape as the RenderType bug above, different
   cause).

   Register `EmbracingBosomEvents` on the FORGE bus from bhspells' main mod class
   constructor (`MinecraftForge.EVENT_BUS.register(EmbracingBosomEvents.class)`) — it has no
   `@Mod.EventBusSubscriber` annotation to carry over, by design.

   Then delete `BHXSpellRegistry.java`, `BHXEntityRegistry.java`, `BHXMobEffectRegistry.java`,
   `BHXParticleRegistry.java` entirely — their only job was registering this content under
   the bootstrap's own `DeferredRegister`s.

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
  works under its new identity. What "working" looks like now (this is the full Phase
  0-2C feature set, not a placeholder):
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
- Once confirmed, this `bhspellsx` repo can be archived or deleted — it has no further
  purpose after a successful merge of a given phase's content. (If more phases are planned,
  keep the repo and start the next phase's content in a fresh subpackage under
  `net/offkung/bhspellsx/...` instead.)
