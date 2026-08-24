# bhspellsx — Forge addon bootstrap for bhspellsx:embracing_bosom

Read this first. `bhspellsx` is a throwaway bootstrap project: a real, buildable Forge mod
used to develop and test new spell content against the real modpack API surface before it
gets folded into the actual `bhspells` mod by the team lead (see `MERGE.md`). It currently
holds one complete spell, `bhspellsx:embracing_bosom` — a ground-targeted support zone with
mechanics (heal/buff/damage-reduction/debuff-shortening), a full custom VFX stack (a
five-layer rotating ring renderer, a traveloptics particle column, and two custom ambient
particle types), and a real buff icon. See `MERGE.md` for the exact file list and merge
procedure; this file is the technical "how it works and why" reference.

---

## Target environment (verified — do not deviate without re-verifying)

| Component | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| MCP mappings | 20230612.114412 (official) |
| irons_spellbooks | 1.20.1-3.16.1 (3.x API) |
| irons_lib | 1.20.1-1.0.2 |
| geckolib | 4.8.3 |
| traveloptics | 6.3.0-1.20.1 |
| bhspells | 1.20.1-1.1.4-forge |

Source of truth for this table: recon against
`D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods` — re-verify against that
directory (not memory) before bumping any version here.

## The one hard rule: never touch `api/backwards_compat/`

irons_spellbooks 3.16.1 ships a 51-class `io/redspace/ironsspellbooks/api/backwards_compat/`
package — a compatibility shim for mods still written against the older 2.x API. **Never
import from it.** Everything this project needs lives in the real `api/` packages
(`api/spells`, `api/config`, `api/registry`, `api/util`, `api/magic`, `api/entity`,
`api/attribute`, `api/item`, `api/events`, `api/network`). If a class you need only seems
to exist under `backwards_compat`, that's a signal you're solving the problem the 2.x way —
stop and re-check the real `AbstractSpell`/`DefaultConfig` surface instead.

Confirmed abstract methods on `AbstractSpell` (3.16.1): `getSpellResource()`,
`getDefaultConfig()`, `getCastType()`. Mana/power/cast-time are `protected` fields set in
the spell's constructor — not part of `DefaultConfig`. `DefaultConfig` build chain:
`new DefaultConfig().setMinRarity(...).setSchoolResource(...).setMaxLevel(...).setCooldownSeconds(...).build()`.

## Package convention: everything lives under `net.offkung.bhspellsx`

**All source lives under `net.offkung.bhspellsx`. Subpackage names mirror `bhspells`
exactly.** Portable classes (`spells/`, `entity/`, `effect/`, `event/`, `client/particle/`,
`client/renderer/`) are merge targets — written as if they already lived inside the real
`bhspells` mod, modulo the namespace. The mod entrypoint (`BHSpellsX`, `BHSpellsXClient`) and
the `registry/` package (including `BHXParticleRegistry`) are throwaway wiring, deleted at
merge time. See `MERGE.md` for the exact current file list — it's kept in sync with what's
actually portable, more precisely than this paragraph.

**NEVER declare a package under `net.offkung.bhspells`** (no trailing `x`). JPMS forbids
two modules exporting the same package — `bhspellsx` and the real `bhspells` mod loaded
side by side in the same modpack, both declaring `net.offkung.bhspells.*`, crashes the game
at module resolution *before mod loading even starts*:

```
java.lang.module.ResolutionException: Modules bhspellsx and bhspells
export package net.offkung.bhspells.spells.ground to module transition
```

This already happened once (Phase 0 originally put `EmbracingBosomSpell` under
`net.offkung.bhspells.spells.ground`, colliding with the real bhspells jar present in the
pack) and was fixed by moving everything to `net.offkung.bhspellsx.*`. Do not reintroduce
it. When adding new content, always ask "does this belong in the bootstrap (`BHSpellsX`,
`BHSpellsXClient`, `registry/`), or is it portable content bound for the merge?" before
picking a subpackage — but either way it starts with `net.offkung.bhspellsx`, never
`net.offkung.bhspells`. The merge step (see `MERGE.md`) is what performs the
`bhspellsx` → `bhspells` rename, and only there.

Resource-location strings (registry ids, hardcoded school references) are unaffected by
this — `bhspellsx:embracing_bosom` and `bhspells:ground` are fine as-is; this rule is about
Java package declarations only.

## Testing loop — there is no dev-environment launch

This modpack cannot run via `runClient`/`runServer` — it's 70+ interdependent mods,
several running through Sinytra Connector, way outside what a ForgeGradle userdev
environment can reproduce. `build.gradle` deliberately has **no `runs {}` block**. The
actual test loop is:

1. `./gradlew build`
2. Copy `build/libs/bhspellsx-<version>.jar` into the real modpack's mods folder
   (`D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\`)
3. Launch that profile for real and test in-game.

Never suggest `runClient`/`runServer` as a way to verify a change here — it will not work
and is not the intended workflow.

## Dependency jars

**irons_spellbooks and irons_lib come from CurseMaven, not `libs/`, as of Phase 1.**
`compileOnly fg.deobf(files("libs/irons_spellbooks-....jar"))` looked fine in Phase 0
(one no-op spell, no inherited vanilla method overrides) but breaks silently the moment you
extend a class that inherits from vanilla `Entity` (e.g. `AoeEntity`). A flat-dir jar is
never remapped by ForgeGradle, so vanilla-inherited method names stay in SRG form while the
workspace uses official mappings — an `@Override` compiles cleanly but never actually
overrides anything at runtime, **no error, entity just never ticks**. Confirmed and fixed in
Phase 1 STEP 0 (see the throwaway `TICK 1`..`TICK 5` probe in the Phase 1 commit history).

CurseMaven coordinates, pinned to the exact modpack versions (do not bump without
re-verifying against the CurseForge file page):

```groovy
compileOnly fg.deobf("curse.maven:irons-spells-n-spellbooks-855414:8237094") // = 1.20.1-3.16.1
compileOnly fg.deobf("curse.maven:irons-lib-1492763:7907335")                // = 1.20.1-1.0.2
```

The `repositories {}` block needs a **plain `maven { url; content { includeGroup ... } } }`**
for `https://cursemaven.com` — do not wrap it in `exclusiveContent {}`. That was tried first
and broke FG's internal deobf-substitution mechanism: the build failed with
`Could not find curse.maven:...:<fileId>_mapped_official_1.20.1` even though the raw
artifact and its POM were reachable directly (verified with `curl`). Plain `maven{}` +
`content{}` is what FG's own repo-substitution logic expects; `exclusiveContent{}` isn't.

geckolib, traveloptics, and bhspells stay in `libs/` (`compileOnly fg.deobf(files(...))`) —
we only ever call their static helpers, never extend their classes, so the SRG-name risk
above doesn't apply to them. Pulled read-only from the modpack's own `mods/` folder, never
from CurseMaven, per project policy for these three. They're `.gitignore`d; see `README.md`
for how to repopulate `libs/` on a fresh checkout.

`mods.toml` hard-depends on `irons_spellbooks`, `irons_lib`, and (as of Phase 2B)
`traveloptics` — `EmbracingBosomAoe` calls `AdvancedCylinderParticleManager` at runtime, so
it stopped being compile-classpath-only. geckolib and bhspells are still on the compile
classpath but **not** runtime dependencies — nothing in this repo calls into them beyond a
single hardcoded school `ResourceLocation` string (see `EmbracingBosomSpell` — deliberately
not a compile-time dependency on `bhspells` itself, resolved only at real runtime since
bhspells is always present in the live modpack).

## Traveloptics particle managers (Phase 2B)

`com.gametechbc.traveloptics.api.particle.AdvancedCylinderParticleManager` /
`AdvancedSphereParticleManager` etc. are misleadingly named — despite "Manager", they're
stateless static utility classes (`public abstract class` with only `static` methods, no
persistent state, no vanilla inheritance at all). A single call is one immediate one-shot
burst; there's no built-in animation. House style (see bhspells' `EternalPurificationSpell`)
gets "continuous" effects by calling the static method every tick/interval from your own
tick loop, and gets animated parameters (e.g. a shrinking radius) by calling it repeatedly
across several ticks with a manually interpolated value — `EmbracingBosomAoe`'s cast-in
converge burst does exactly this over 8 ticks.

These calls are **server-side only** — they early-return on `level.isClientSide` and
delegate to irons_spellbooks' `MagicManager.spawnParticles`, which iterates all players and
calls `ServerLevel#sendParticles` per player. Call them from server-side tick code, same as
our own earlier `ServerLevel#sendParticles` placeholder VFX.

`particleType` is typed as the `ParticleOptions` interface, not a fixed `ParticleTypes`
enum — any implementation works, confirmed via bhspells passing `BlockParticleOption`. This
means vanilla's `DustParticleOptions(Vector3f color, float scale)` works too, giving an
arbitrary RGB tint with no custom particle type needed — that's how `EmbracingBosomAoe` hits
the amber palette (`0xE8A33D`) without waiting on a Phase 2C custom particle.

Static-method calls resolve by exact descriptor at compile time (no virtual dispatch), so
the SRG-name risk that forced irons_spellbooks/irons_lib onto CurseMaven does **not** apply
here — traveloptics stays flat-dir in `libs/`. Re-verify this reasoning if a future phase
ever needs to *extend* a traveloptics class instead of just calling its static methods.

## Ring renderer, custom RenderType, and the Oculus/Iris constraint (Phase 2A)

`client/renderer/EmbracingBosomRingRenderer.java` draws the five-layer rotating ring VFX
directly (no GeckoLib, no model file — a ring can't be built from Blockbench cubes). Layer
config (texture, radius, inner radius, rotation speed, alpha, tint, y-offset) lives in the
immutable `client/renderer/RingLayer.java` record, retunable without touching rendering
code. Mesh is built fresh every frame with no per-segment heap allocation — see the triangle
count comment in `buildAnnulus()` before adding more segments or layers.

**⚠️ The custom `RenderType` in `buildRingRenderType()` is not incidental complexity — it is
required, and easy to break by "simplifying." Full detail is in the class's own javadoc;
this is the summary. Read the javadoc before changing the shader, vertex format, or any
`RenderStateShard` in that method.**

Short version of what happened, in order:
1. **First attempt** used `RenderType.eyes(texture)`. Two real bugs, both found by decompiling
   the actual bytecode, not guessed: `eyes()` never sets a `CullStateShard`, so it inherits
   the builder default of "leave whatever cull state is already active" — i.e. it does NOT
   disable culling despite looking like an emissive/additive type that should. And its
   `ADDITIVE_TRANSPARENCY` uses `blendFunc(ONE, ONE)` — source factor `ONE`, not `SRC_ALPHA`
   — so per-vertex alpha was silently discarded at the blend stage; fades and per-layer alpha
   would have been visually inert even once the mesh was visible. Both root-caused by
   decompiling `RenderType`/`RenderStateShard`, not from memory or assumption.
2. **Fixed those two bugs** with a hand-built `CompositeState` (explicit `CullStateShard(false)`,
   a custom `SRC_ALPHA/ONE` transparency shard — still additive/never-darkens, but now
   alpha-aware) on top of `GameRenderer.getPositionColorTexShader()`. This rendered correctly
   with no shaderpack loaded — and was **completely invisible under Oculus/Iris**, with no
   error, since it looks identical to broken code from a testing standpoint if you never load
   a shaderpack.
3. **Root-caused by disassembling the actual `oculus-mc1.20.1-*.jar` Mixins shipped in this
   modpack** (not guessed, not inferred from Iris's public docs): Iris does not generically
   intercept custom RenderTypes/shaders. One Mixin (`MixinGameRenderer`) `@Inject`s into ~27
   *specific* `GameRenderer.get*Shader()` getters by exact method identity, each routed (when
   a shaderpack is active) to one of a closed, fixed `ShaderKey` enum of real deferred
   gbuffers programs. Anything not on that list gets no shaderpack routing.
   `getPositionColorTexShader()` *is* on the list, but its override always routes to
   `ShaderKey.TEXTURED_COLOR` (a generic 2D/UI-quad program) regardless of context — not the
   terrain→entities→translucent→composite chain a world-space effect needs to land in. That
   mismatch is why it disappeared under Iris.
4. **Fixed** by switching the shader to `GameRenderer.getRendertypeEntityTranslucentEmissiveShader()`
   (the same `ShaderInstance` vanilla's `RenderType.entityTranslucentEmissive()` uses) and the
   vertex format to `NEW_ENTITY`. Confirmed via the same Mixin disassembly that this exact
   shader routes to `ShaderKey.ENTITIES_EYES_TRANS` — a real, alpha-blended, entity-context
   deferred gbuffer stage — during normal entity rendering. (For reference: irons_spellbooks'
   own ground-magic-glow RenderTypes use `getRendertypeEnergySwirlShader()`, which Iris routes
   to `ShaderKey.ENTITIES_CUTOUT` — a real stage too, but binary-alpha-discard, which would
   have wrecked this effect's fades and soft haze layers. Rejected for that reason, not
   because it's wrong in general.) This shader also actually samples the overlay texture
   (`texelFetch(Sampler1, UV1, 0)`), unlike every shader tried before it — `OverlayStateShard`
   has to be `true` with a real `OverlayTexture.NO_OVERLAY` UV1 coordinate, or it samples an
   unbound texture unit.

**If a shader/RenderType change ever seems necessary here, re-verify against Iris's actual
compiled Mixin classes (decompile the installed Oculus jar's `MixinGameRenderer`,
`ShaderKey`, `ProgramId`) — the same way this was originally root-caused. Do not assume a
"more normal-looking" RenderType is safe just because it renders fine without a shaderpack
loaded; that tells you nothing about the Iris case.** See `MERGE.md`'s warning section too —
this is the single highest-risk thing in the whole handover.

One accepted trade-off from step 4: this shader applies vanilla's fake directional entity
lighting (`minecraft_mix_light`) rather than being purely emissive — since the ring mesh has
a fixed straight-up normal, this is one uniform brightness multiplier across the whole mesh
(not spatially varying), floored so it stays visible in caves/at night, but dimmer than true
full-bright. Compensated for with higher per-layer alpha values in `RingLayer`'s config, not
by fighting the shader.

## Phase 2C: custom ambient particles (leaves and motes)

`client/particle/EmbraceLeafParticle.java` and `EmbraceMoteParticle.java` (each with an
inner `Provider`) are ordinary `TextureSheetParticle` subclasses, modelled directly on
bhspells' own `OakLeafParticle`/`GoldSparkleParticle` (constructor/tick/`getRenderType()`
shape, the small per-tick horizontal sway, the friction/lifetime jitter). `EmbraceLeafParticleOption`
and `EmbraceMoteParticleOption` are tintable `ParticleOptions` implementations — color-only
`Codec`/`Deserializer`/network plumbing, following bhspells' `ColoredCherryParticleOption`
pattern exactly, registered via `registry/BHXParticleRegistry.java`. This lets the palette
stay retunable from code (`AMBER_TINT` in `EmbracingBosomAoe`) without touching the particle
classes or their JSON.

Both particle types are registered client-side in `BHSpellsXClient`'s
`onRegisterParticleProviders` (`RegisterParticleProvidersEvent` → `registerSpriteSet`) — a
**separate** event handler from the entity renderer registration; easy to forget when
copying this pattern elsewhere, since a missed `registerSpriteSet` call fails silently (the
`ParticleType` still registers fine, the particle just never renders — no crash, no log).

Emission is entirely server-side, from `EmbracingBosomAoe.tick()` — not from the particle
classes themselves. Positions are sampled uniformly across the zone's disc (sqrt-distributed
radius, not vanilla's box-jitter) via one `ServerLevel#sendParticles` call per particle, so
placement matches the actual circular zone rather than approximating it with a square.
Emission rate for both types tapers linearly to zero over the AoE's last 20 ticks — same
window `EmbracingBosomRingRenderer` fades the ring over, so everything winds down together
rather than particles cutting off hard while the ring is still visibly fading.

## Known quirk: `/effect give` prints a false failure message on a shortened debuff

`event/EmbracingBosomEvents.java`'s debuff-shortening handler (`onApplicable`) works by
denying the original `MobEffectEvent.Applicable` (`event.setResult(Event.Result.DENY)`) and
then manually re-applying a shortened replacement. This is the only viable hook —
`MobEffectInstance`'s `duration` field is private with no public setter (confirmed by
decompiling it from the mapped Forge jar; the only code that touches it post-construction is
package-private), so there is no way to mutate the incoming instance in place without an
Access Transformer, and `MobEffectEvent.Added` isn't cancellable either. Denying was the
only clean path found.

Side effect: anything that reads `LivingEntity.addEffect()`'s own return value for the
*original* call — including vanilla's `/effect give` command — sees `false` (because we
denied it) and prints its normal failure message ("Unable to apply this effect (target is
either immune to effects, or has something stronger)"), even though our manual re-apply
succeeds immediately after and the shortened debuff is actually sitting on the target. This
is cosmetic only; the effect is genuinely applied. No fix without an AT — if one is written
later for other reasons, revisit this.

**Never** let the shortening logic touch an infinite-duration effect. `/effect give <player>
<effect> infinite` sets `MobEffectInstance.duration = MobEffectInstance.INFINITE_DURATION`
(`-1`, confirmed by decompile — `isInfiniteDuration()` is exactly `duration == -1`).
`onApplicable` checks `instance.isInfiniteDuration()` (plus a `>= 1_000_000` tick ceiling for
effectively-infinite-but-technically-finite roleplay durations) and passes those straight
through untouched — no deny, no shortening. This server is used for staff to apply infinite
effects for scene purposes; breaking that is worse than the cosmetic message above.
