# Recon: bhspells' Eternal Purification (Nature) — how its crystal/smoke visuals stay readable

Recon only — no code changes made as part of this investigation. Read from the decompiled
reference tree at `Origins/Mods/_reference/bhspells-1.3.0-decompiled/extracted/` (bhspells
1.3.0's own classes/assets), cross-checked against official-mapped vanilla source
(`forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar`, extracted to a scratch temp dir
this session) for exact `RenderType` composite-state definitions, and against the
`geckolib-forge-1.20.1-4.8.3.jar` / `traveloptics-6.3.0-1.20.1.jar` jars already vendored in
`bhspellsx/libs/` (via `javap -c` on the relevant classes, since no decompiled source tree
exists for either) for the two library-provided rendering pieces bhspells builds on.

## 1 — Spell class, entities, renderers, particles

`EternalPurificationSpell` (`net/offkung/bhspells/spells/nature/EternalPurificationSpell.java`)
spawns, on cast:
- **4× `PurificationPillarEntity`** in a square around the caster (±8 blocks each axis,
  `PILLAR_SQUARE_OFFSET`), each a `GeoEntity` (GeckoLib) — this is the "crystal shard" visual.
  Model: `geo/purification_pillar.geo.json`. Renderer:
  `PurificationPillarEntityRenderer extends GeoEntityRenderer<PurificationPillarEntity>`.
  Two extra render layers are attached in its constructor: a `TORenderEmissiveLayer` (from
  traveloptics) and a custom `PurificationPillarEntityLayer`.
- **1× `LotusPetal`**, also a `GeoEntity` — a *separate*, smaller decorative prop placed at the
  caster's feet (`LotusPetal lotusPetal = new LotusPetal(...); lotusPetal.m_146884_(entity.m_20182_())`,
  `EternalPurificationSpell.java:100-102`). Model: `geo/lotus_flower1.geo.json`. Renderer:
  `LotusPetalRenderer extends GeoEntityRenderer<LotusPetal>`.
- **Particles**: `AdvancedSphereParticleManager`/`AdvancedCylinderParticleManager` (traveloptics,
  same static-utility pattern our own `EmbracingBosomAoe` already uses) for cast-in/pillar-removal
  dust bursts, plus `PurificationPillarEntity`'s own per-tick and per-shockwave particle spawns
  (§4 below covers the smoke specifically).

**Important scope note for this recon**: the *lotus* in Eternal Purification is a real GeckoLib
model prop, not a hand-built CPU mesh like our `CrystalHydroDomeRenderer`'s lotus — its geometry,
petal shape, and animation come entirely from `lotus_flower1.geo.json` /
`lotus_flower1.animation.json` (a Blockbench asset), not from Java math. Its *RenderType* choice
is still directly comparable (§2, §6) and turns out to be the closer match to our own current
mistake, not a model to imitate structurally.

## 2 — RenderType per visual piece

| Piece | RenderType | Blend | Depth write | Cull | Lighting | Geometry |
|---|---|---|---|---|---|---|
| Pillar base model | `RenderType.entityCutoutNoCull(texture)` (GeckoLib's own default — see below) | **None** (`NO_TRANSPARENCY` — alpha-test/cutout, not blended) | On (opaque, default `WriteMaskStateShard`) | **Off** (`NO_CULL` — double-sided) | Real world lighting (`LightmapStateShard(LIGHTMAP)`) — responds to torches/daylight like any mob | GeckoLib model (`purification_pillar.geo.json`) |
| Pillar passive glow layer | `RenderType.eyes(purification_pillar_layer_passive.png)` | Additive, `blendFunc(ONE, ONE)` (`ADDITIVE_TRANSPARENCY`) | **Off** (`COLOR_WRITE` only) | Default (`CullStateShard(true)` = whatever's already active — same "silent single-sided" gap our own CLAUDE.md documents for `eyes()`) | Full-bright (`packedLight=15728880`, hardcoded by `TORenderEmissiveLayer`'s 3-arg constructor) | Re-renders the same GeckoLib model geometry, tinted white, alpha 0.8 |
| Pillar stage glow layer (base/resonate) | `RenderType.eyes(purification_pillar_layer_base\|resonate.png)` | Same as above | Same as above | Same as above | Full-bright (hardcoded `15728880` in `PurificationPillarEntityLayer.render`) | Re-renders the same model, tinted by `getEmissiveBrightness()` (0.0-0.8, eased per `Stage`), alpha always `1.0` |
| Lotus petal prop | `RenderType.energySwirl(lotus_flower1.png, 0.0F, 0.0F)` (`LotusPetalRenderer.getRenderType`, override) | Additive (`ADDITIVE_TRANSPARENCY`, same as eyes — `RenderType.java` confirms `energySwirl` also sets `.setTransparencyState(ADDITIVE_TRANSPARENCY)`) | **Off** | **Off** (`NO_CULL`, explicit) | Uses `LIGHTMAP`+`OVERLAY` shards (world-lit, not forced full-bright) | GeckoLib model (`lotus_flower1.geo.json`) |

Sources for the exact composite states (official-mapped `RenderType.java`, this session's
extraction):
- `entityCutoutNoCull(ResourceLocation)`: shader `RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER`,
  `NO_TRANSPARENCY`, `NO_CULL`, `LIGHTMAP`, `OVERLAY` (`RenderType.java:47-50`). This is
  **GeckoLib's own default** — `GeoModel.getRenderType(T, ResourceLocation)` (decompiled via
  `javap -c` on `geckolib-forge-1.20.1-4.8.3.jar`, since no source tree exists for it) compiles
  to a single `invokestatic RenderType.entityCutoutNoCull` call, and neither
  `PurificationPillarEntityModel` nor `PurificationPillarEntityRenderer` overrides
  `getRenderType(...)`, so the pillar's base body uses this untouched.
- `eyes(ResourceLocation)`: shader `RENDERTYPE_EYES_SHADER`, `ADDITIVE_TRANSPARENCY`
  (`blendFunc(ONE, ONE)`), `WriteMaskState = COLOR_WRITE` (depth write off), **no explicit
  `setCullState`** (`RenderType.java:95-98`) — this is the exact same `eyes()` this project's
  own `EmbracingBosomRingRenderer` tried first and rejected (see its class javadoc / MERGE.md's
  "READ BEFORE TOUCHING embracing_bosom's RING RENDERER" section) for two reasons: the missing
  cull-state call leaves whatever cull mode was already active (not reliably double-sided), and
  `blendFunc(ONE,ONE)` means source-alpha is *not* part of the blend equation — a per-vertex or
  per-layer alpha value (like this layer's `0.8F`/`glowIntensity`) has no effect on how much it
  blends, only on the raw vertex-color value GeckoLib bakes in (immaterial once ONE,ONE ignores
  it). bhspells "gets away with" `eyes()` here specifically *because* these are secondary glow
  layers stacked on top of an already-fully-readable opaque base, not the thing carrying the
  actual silhouette/color.
- `energySwirl(ResourceLocation, float, float)`: shader `RENDERTYPE_ENERGY_SWIRL_SHADER`,
  `ADDITIVE_TRANSPARENCY`, `NO_CULL`, `LIGHTMAP`, `OVERLAY`, plus a `u,v` texture-offset shard
  for a scrolling effect (called here with `0.0F, 0.0F` — no scroll) (`RenderType.java:268-270`).
  This is the **same shader this project's own CLAUDE.md already flags** as Iris-routed to
  `ShaderKey.ENTITIES_CUTOUT` (binary alpha discard, not smooth blending) — rejected for
  `embracing_bosom`'s ring for exactly that reason.

## 3 — Textures

All confirmed to exist on disk and inspected pixel-by-pixel (Python/Pillow, this session) for
size, alpha-channel usage, and luminance range (a proxy for "painted shading present" vs. a flat
single-color fill):

| File | Size | Alpha | Luminance (opaque px) | Notes |
|---|---|---|---|---|
| `textures/entity/purification_pillar/purification_pillar.png` | 256×256 | full 0-255 range, ~13% of canvas painted (rest transparent — normal for a GeckoLib UV sheet) | 38-181 (range 143) | Real painted shading — this is the base cutout body's texture, the "solid-looking white/cyan crystal" the user is describing |
| `textures/entity/purification_pillar/purification_pillar_layer_passive.png` | 256×256 | full range, ~2% painted | 51-181 (range 130) | Glow-layer sprite, shading present, always applied via `TORenderEmissiveLayer` at alpha 0.8 |
| `textures/entity/purification_pillar/purification_pillar_layer_base.png` | 256×256 | full range, ~2% painted | 51-181 (range 130) | Stage-0/1 emissive overlay |
| `textures/entity/purification_pillar/purification_pillar_layer_resonate.png` | 256×256 | full range, ~6% painted | 51-181 (range 130) | Stage-2/3/4 emissive overlay (swapped in by `PurificationPillarEntityLayer.getEmissiveTexture`) |
| `textures/entity/lotus_flower/lotus_flower1.png` | 16×16 | full range, ~54% painted | 62-246 (range 184) | Tiny flat-plane-style GeckoLib texture for the (unrelated, additive) lotus prop |

The base pillar texture (256×256, real shading, luminance range 143) is doing the heavy lifting
for "readable on any background": it's a normally-lit, alpha-tested, painted texture — the same
category as a vanilla mob skin, not a glow sprite. The three "layer" textures are much sparser
(2-6% of the canvas actually painted) because they only need to cover the specific UV islands
that should glow, layered on top of, not replacing, the base.

## 4 — Smoke

**`ParticleTypes.CAMPFIRE_COSY_SMOKE`** — vanilla, not custom. (Decompiled bhspells sources show
the SRG name `ParticleTypes.f_123777_`; cross-referenced against
`mcp_config-1.20.1-20230612.114412`'s `joined.tsrg` (obf↔SRG) and `client_mappings.txt`
(official↔obf) this session to resolve it to the real name — obf field `an` in obf class `iv`,
which `client_mappings.txt` maps to `net.minecraft.core.particles.ParticleTypes` /
`CAMPFIRE_COSY_SMOKE`.)

Spawned from `PurificationPillarEntity.createBaseShockwaveParticlesAt`
(`PurificationPillarEntity.java:425-433`), **not every tick** — it's a periodic burst, not a
continuous emitter:
```
int smokeCount = Math.round(12.0F * radiusScale);   // radiusScale = shockwaveRadius / 6.0F
```
At `EternalPurificationSpell.getShockwaveRadius()` = 12.0, `radiusScale` = 2.0, so
**24 smoke particles per burst, per pillar**. Bursts happen roughly every 40 ticks
(`age - lastShockwaveAge >= 40` in `PurificationPillarEntity.m_8119_`/tick, line 304), and all 4
pillars start with the same counters, so in practice ~96 smoke particles land together roughly
every 2 seconds while the group is alive — not a steady per-tick trickle. (There's a second,
functionally dead-looking `isShockwaveEmitter()`-gated copy of the same check right after it in
the decompiled source, lines 329-337, whose condition is always false immediately after the
first block already updated `lastShockwaveAge` — not investigated further, out of scope for a
render-focused recon.) Each burst also spawns basalt/blackstone block-dust and separate ground
debris via `BlockParticleOption`, alongside the smoke — the smoke is one layer of a multi-particle
burst, not the only effect.

## 5 — Shader compatibility signals

**No explicit Oculus/Iris-aware code exists anywhere in bhspells' Eternal Purification** — no
comment, no shader-getter selection logic, nothing analogous to this project's own
`EmbracingBosomRingRenderer` javadoc/MERGE.md writeup. The readability under shaders instead
falls out of a structural choice, not a deliberate shader pick:

- The base pillar body uses **GeckoLib's own untouched default**
  (`entityCutoutNoCull`/`RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER`) — the same shader family every
  vanilla mob and the overwhelming majority of modded creatures use. Iris/Oculus *must* route
  standard entity-cutout rendering correctly for the game to be playable at all with any
  shaderpack (every mob in the game depends on it) — this project's own prior Oculus-jar
  disassembly (documented in `EmbracingBosomRingRenderer`'s class javadoc) found ~27 specific
  `GameRenderer.get*Shader()` getters Iris intercepts by exact identity; the standard entity
  cutout/opaque getters are certainly among the best-tested of that set, precisely because every
  mob's visibility depends on them. This recon pass did **not** re-disassemble the Oculus jar to
  independently re-confirm that specific getter's `ShaderKey` routing (flagged as a gap below) —
  but the inference is about as safe as this kind of inference gets, since the alternative (mob
  rendering broken under any shaderpack) would be a showstopper bug in Iris itself, not a
  per-mod risk.
- The additive glow layers (`eyes()`/`energySwirl()`) carry the *same* Iris-routing risk this
  project already root-caused for its own ring (`eyes()` → `ShaderKey.TEXTURED_COLOR`, a flat
  2D/UI program, not a world-space entity stage — likely to vanish or misbehave under a
  shaderpack; `energySwirl()` → `ShaderKey.ENTITIES_CUTOUT`, real but binary-alpha-discard). If
  either glow layer vanishes or looks wrong under a shaderpack, the pillar still reads perfectly
  fine — it's a normally-lit cutout crystal underneath either way. **This is the actual
  mechanism**: readability-under-shaders here comes from *not depending on the additive layer to
  carry the silhouette*, not from picking an Iris-safe additive shader the way this project did
  for `embracing_bosom`.
- The lotus prop's `energySwirl()`-only RenderType has no such safety net — it's the entire
  visual, so if Iris mishandles that specific shader getter the same way it (would, unverified
  this pass) mishandle `eyes()`, the whole prop could vanish or look wrong under a shaderpack.
  This is a real, if minor and cosmetic, weak point in bhspells' own asset, structurally similar
  to the problem this recon was asked to solve for our own lotus.

## 6 — Recommendation for our lotus petals

**Don't try to make the existing additive mesh "less additive" — swap to a standard alpha-blend
RenderType with real vertex color, closer to `entityTranslucentCull`/`entityTranslucent` than to
`eyes()`/`energySwirl()`/our current `getRendertypeEntityTranslucentEmissiveShader()` copy.**

Concretely, build a new composite state (still hand-built, since we have no texture yet and need
untextured solid color — same reasoning `CrystalHydroDomeRenderer.buildDomeRenderType()` already
documents for why it can't just call a vanilla factory method) with:
- **Shader**: `GameRenderer.getRendertypeEntityTranslucentShader()` (vanilla's real
  non-emissive translucent-entity shader — same family the dome's shader is drawn from, but the
  *non*-emissive sibling) instead of the `...EntityTranslucentEmissive` one currently reused from
  the dome. This is a genuine alpha-blend (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA` — real "see less of
  what's behind it," not "add brightness on top of it") and, being one of vanilla's standard
  entity RenderTypes, sits in the same well-supported category as `entityCutoutNoCull` above —
  not independently re-verified against the Oculus jar's specific `ShaderKey` routing this pass
  (flagged as a gap below, same caveat as §5), but far more likely to be handled than a bespoke
  choice.
- **Transparency**: real alpha blend (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`), not the dome's
  `SRC_ALPHA, ONE` additive shard — this is the actual fix: on grass, an additive petal can only
  ever brighten the green underneath (reads as a pale/washed-out tint), while a real alpha blend
  lets the petal's own color (near-white cyan) actually replace/darken-toward what's visible,
  which is what makes something look like a solid (if soft-edged) object instead of a light
  source.
- **Cull**: keep `CullStateShard(false)` — unchanged, still needed for two-sided petals.
- **Depth**: keep write off / test on — unchanged, still the right choice for a handful of
  overlapping soft petal quads (see §3 of the earlier dome recon, `crystal_hydro_dome_phase2_part2_render.md`,
  for the general translucency-sorting caveat, which still applies here).
- **Lighting**: this is a judgment call, not dictated by the pillar's example — the pillar's
  *base* body is real-world-lit (`LIGHTMAP`), not full-bright, which is part of why it reads as a
  solid physical object rather than a glow effect. Our lotus currently hardcodes `FULL_BRIGHT`
  UV2 and a constant up-normal (`CrystalHydroDomeRenderer.renderPetalLayer`, "No lighting" per
  the Phase 2B spec) — switching to `LightmapStateShard(true)` and real per-vertex lightmap
  coordinates (`LightTexture.pack(...)` from world light at the dome's position, sampled once
  per frame, not full-bright) would make the petals dim correctly at night/in shade like the
  pillar does, at the cost of implementing real lightmap sampling that doesn't currently exist
  anywhere in this renderer. Flagging as a design choice for the user to confirm, not assuming
  it — the current spec explicitly asked for "No lighting," and this recommendation only covers
  the RenderType/blend swap the user asked about, not a silent reversal of that earlier spec.
- **Texture**: keep the current `forge:textures/white.png` (a texture is still required by these
  shaders even for solid color, same as the dome) — no new asset needed to fix the additive
  problem; the fix is the transparency shard, not the texture.

**Sorting issue when drawn inside the additive dome**: our lotus is currently drawn *after* the
dome shell, into the *same* `VertexConsumer`/`RenderType` (`CrystalHydroDomeRenderer.render()`
calls `renderLotus(...)` right after the dome's own per-vertex loop, both via `RENDER_TYPE`, see
`crystal_hydro_dome_phase2_part2_render.md` Q3 for the general class of issue). If the lotus
switches to a **different** `RenderType` (which this recommendation requires — it needs its own
non-additive composite state, distinct from `RENDER_TYPE`), it becomes a **second translucent
batch**, and Minecraft's `MultiBufferSource.BufferSource` flushes different `RenderType`s in
registration/call order, not resorted by depth against each other. Concretely:
- The dome shell (additive, depth-write-off) and the lotus (alpha-blend, depth-write-off) will
  each look internally correct (each batch's own triangles blend against whatever was already in
  the framebuffer when *that batch* draws), but which one visually "wins" where they overlap
  (e.g. dome shell crossing right through the lotus's petals near the ground rim) depends on
  draw order between the two batches, not which is actually nearer the camera. Since the lotus is
  emitted from inside the same `render()` call right after the dome loop, it will currently
  always draw after the dome shell for that frame — meaning the lotus should reliably composite
  "on top of" the dome shell's own fragments at any overlap, but this is an ordering guarantee
  from the code structure, not a depth-sorted guarantee, so it would only look wrong if the
  camera situation somehow needed the dome shell to appear in front of the lotus at a given pixel
  (unlikely given the lotus sits at the dome's floor/center and the shell is a much larger
  radius-10+ surface, but worth a visual check after the RenderType split, same spirit as the
  existing "look through at water/particles, note artifacts, don't fix" test already on this
  project's standard test list).

## Gaps

1. This pass did **not** re-disassemble the installed Oculus/Iris jar to confirm
   `getRendertypeEntityCutoutNoCullShader()`'s or `getRendertypeEntityTranslucentShader()`'s
   exact `ShaderKey` routing — §5/§6's claims about them being "safe" rest on their being
   standard, heavily-depended-upon vanilla entity shaders (the same inference-strength argument
   used for GeckoLib's default), not on the same direct-disassembly evidence this project used
   for `embracing_bosom`'s `getRendertypeEntityTranslucentEmissiveShader()` choice. Worth doing
   before treating §6's RenderType recommendation as fully de-risked the way the dome's own
   shader choice is.
2. Whether GeckoLib's vertex-writing path (`GeoRenderer.reRender`) pre-multiplies RGB by the
   alpha argument before submission (which would make `TORenderEmissiveLayer`'s `0.8F`/
   `glowIntensity` alpha meaningfully dim the glow layers despite `eyes()`'s `ONE,ONE` blend
   ignoring true alpha blending) was not traced further than the bytecode shown in §2 — doesn't
   change any conclusion in this doc, just an unresolved side detail.
