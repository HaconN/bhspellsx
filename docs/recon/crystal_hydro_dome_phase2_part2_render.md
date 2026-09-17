# Crystal Hydro-Dome — Phase 2 recon, Part 2: rendering the dome (Phase 2A)

Recon only — no code changes made as part of this investigation. Part 1 (melee hitting the
dome surface) is in `crystal_hydro_dome_phase2_part1_melee.md` and is not redone here.

## Requirement recap

Radius-10 hemisphere, very transparent (clearly see-through) but readable via a
fresnel-like brighter edge, visible from both inside and outside the dome, and at least
visible (not necessarily correct) under Oculus/Iris shaderpacks.

`CrystalHydroDomeConstants.RADIUS = 10.0` (confirmed,
`CrystalHydroDomeConstants.java:16`). The dome entity (`CrystalHydroDomeAoe`, extends
`AoeEntity`) is currently registered with vanilla `NoopRenderer` in
`BHSpellsXClient.java:36` — no real renderer exists yet.

---

## Q1 — embracing_bosom's ring RenderType: is it suitable for the dome?

**Yes, directly — same shader/state, different geometry.** Read in full from
`EmbracingBosomRingRenderer.java:195-219` (`buildRingRenderType`):

- **Shader**: `GameRenderer::getRendertypeEntityTranslucentEmissiveShader` — the same shader
  vanilla's `RenderType.entityTranslucentEmissive()` uses. This is the *Oculus fix*: per the
  class's own javadoc (lines 58-97), this was root-caused by disassembling the shipped
  `oculus-mc1.20.1-1.8.0.jar`'s `MixinGameRenderer`, which `@Inject`s into ~27 specific
  `GameRenderer.get*Shader()` getters by exact method identity and routes each (under a
  shaderpack) to a fixed `ShaderKey`. This particular getter routes to
  `ShaderKey.ENTITIES_EYES_TRANS`, a real alpha-blended deferred entity gbuffer stage — not
  `ShaderKey.TEXTURED_COLOR` (generic 2D/UI quad program, what `getPositionColorTexShader()`
  gets, which is why the first attempt vanished under a shaderpack) and not
  `ShaderKey.ENTITIES_CUTOUT` (binary alpha-discard, what irons_spellbooks' own
  `getRendertypeEnergySwirlShader()`-based magic-glow RenderTypes get — confirmed
  independently below in Q5, would wreck a smooth fresnel fade).
- **Blend**: a hand-built `TransparencyStateShard` (`ADDITIVE_ALPHA_TRANSPARENCY`,
  lines 180-187) using `blendFunc(SRC_ALPHA, ONE)` — additive (never darkens the
  framebuffer, safe over any background) but, unlike vanilla's `eyes()`
  (`blendFunc(ONE, ONE)`, confirmed dead in the javadoc's post-mortem), actually respects
  per-vertex alpha. This is exactly what a "very transparent, brighter at the edge" fresnel
  look needs: base faces near-zero alpha, edge-on faces higher alpha, and the additive blend
  makes the edge read as a glow rather than a flat brightness bump.
- **Cull**: `CullStateShard(false)` — explicit, backfaces are drawn. Required for a
  hemisphere seen from both inside and outside, same reasoning as the ring (readable "from
  below" per its own comment at line 356) — a sphere/hemisphere seen from inside is looking
  at what would otherwise be a backface.
- **Depth**: `WriteMaskStateShard(true, false)` — depth **test** stays on (still occluded by
  solid terrain/blocks — a dome behind a wall won't show through it), depth **write** is
  off. This is what lets multiple overlapping translucent surfaces (see Q3) blend via alpha
  instead of z-fighting or randomly occluding each other by draw order.
- **Overlay**: `OverlayStateShard(true)` with `OverlayTexture.NO_OVERLAY` per vertex — this
  shader `texelFetch`s the overlay sampler (confirmed against its GLSL, per the javadoc);
  leaving it `false` samples an unbound texture unit.
- **Vertex format**: `DefaultVertexFormat.NEW_ENTITY` (Position/Color/UV0/UV1/UV2/Normal) —
  needed because the chosen shader declares all of those, even the ones unused
  (UV2/lightmap is declared but unused by this shader's GLSL, per the ring's own comment at
  `renderer/EmbracingBosomRingRenderer.java:395`).

Nothing here is ring-specific — it's a general "translucent, unlit-enough, Iris-safe,
double-sided" entity RenderType. The dome should reuse this exact
shader/blend/cull/depth/overlay/format combination and only change the mesh (sphere instead
of annulus) and per-vertex alpha computation (fresnel-by-view-angle instead of a flat
per-layer constant). Recommend factoring `buildRingRenderType`'s `CompositeState` into a
small shared helper (e.g. a `bhspellsx` render-state constants/utility class) rather than
copy-pasting it a second time, since both call sites want byte-for-byte the same state.

One difference to design in: the ring is additive-only and never needs to look "dark" —
a hemisphere surface likely wants to stay additive too (per the spec's "very transparent,
readable via brighter edge" framing, which is exactly an additive glow-edge look, not a
darkening frosted-glass look). If the true owner wants the dome to visibly *tint/darken* the
world behind it (not just glow), that needs a genuine standard-alpha `TransparencyStateShard`
(`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`) instead of additive — a design decision to confirm with
the spell's owner, not assumed here.

---

## Q2 — CPU per-vertex fresnel alpha for a radius-10 hemisphere, ~24×48 segments: feasible?

**Feasible; no existing sphere/shield analog to mirror, but the ring's own build pattern
generalizes directly.**

- **No translucent sphere/dome mesh code exists anywhere in `is_src` or this repo.**
  `ShieldRenderer`/`ShieldModel` (Q5, below) is a real 3D model (Blockbench/GeckoLib-style
  `ModelPart`, not a hand-built CPU mesh) with a single constant alpha (`1.0F` passed to
  `model.render(...)`, `ShieldRenderer.java:46`) — no per-vertex or per-face alpha variation
  at all, let alone view-angle-driven. It is not a usable template for fresnel shading.
- **`EmbracingBosomRingRenderer.buildAnnulus`/`quadVertex`** (lines 357-398) is the closest
  real precedent in this codebase: a fresh CPU mesh built every frame with primitive
  floats only (no per-vertex heap allocation — `Vector3f`/list-building patterns from
  `RenderHelper.QuadBuilder` are deliberately *not* used here for this reason), each vertex
  independently given its own `color(r,g,b,a)`. That's already the exact shape of what
  fresnel shading needs: per-vertex alpha computed however you like before calling
  `.color(...)`, since the shader just consumes whatever vertex-color alpha it's given.
- **Fresnel term**: for each hemisphere vertex, compute `dot(vertexNormal, viewDir)` where
  `viewDir` is `normalize(vertexWorldPos - cameraPos)` (or the equivalent local-space
  camera position via the inverse of the entity's translation, cheaper than transforming
  every vertex to world space) — grazing angles (`dot` near 0) get high alpha, face-on
  (`dot` near 1, camera looking straight at that patch of the dome) get low alpha. This is
  plain CPU trig/vector math per vertex, nothing GPU-side, consistent with how the ring
  already computes its per-vertex UV (`quadVertex`'s `uvScale` math) — same category of
  cost.
- **Triangle count sanity check**: ~24×48 segments hemisphere, drawn as 24×48 = 1152 quads
  (2304 triangles, ~4608 vertices/frame if quads are unindexed like the ring's `QUADS`
  mode). The existing ring already draws 5 layers × 48 segments × 2 triangles = 480
  triangles/frame with no measured issue (per the class's own triangle-count comment at
  `EmbracingBosomRingRenderer.java:132-134`); ~4600 vertices of straightforward per-vertex
  float math built fresh every frame is a modest step up from that, not a different order of
  magnitude, and should be comfortably fine given the ring's existing budget note. Camera
  position for the fresnel term is available every frame from the renderer's
  `EntityRendererProvider.Context`/render call the same way any vanilla renderer gets it
  (`Minecraft.getInstance().gameRenderer.getMainCamera()` or the `partialTicks`-consistent
  camera passed into rendering) — not something this pass traced to an exact call site, but
  it's a standard, always-available lookup, not a novel problem.
- **Indexing note (not fully verified this pass)**: unlike the ring (a thin annulus, 2
  triangles/segment), a full hemisphere is normally built as a triangle strip/fan per
  latitude band, which can roughly halve the "4 vertices per quad" cost above if migrated
  to `QUADS` mode is kept for simplicity (matching the ring's existing convention) — either
  works; this is a minor implementation choice, not a feasibility blocker.

**Gap**: exact camera-position access pattern (which vanilla API call, from inside an
`EntityRenderer<T>.render(...)` override, gives the current camera's world position for the
fresnel `viewDir`) was not pinned down to a file:line citation this pass — needs a direct
read of `EntityRenderer`/`Camera` before implementation, but this is a well-trodden vanilla
API, not a research risk.

---

## Q3 — Translucency sorting against water, particles, other translucent entities

**No dedicated sorting fix exists or was found — this inherits the same known-accepted
limitation the ring already has, just at larger scale.**

- The chosen shader/RenderType renders in the **entity translucent** pass. Depth **write**
  is off (`WriteMaskStateShard(true, false)`), depth **test** stays on. This means:
  - The dome mesh's own front and back hemisphere surfaces (both rasterized, since culling
    is off) don't z-fight against each other and blend correctly regardless of draw order,
    because additive blending (`SRC_ALPHA, ONE`) is order-independent-ish for correctness
    (no "wrong surface wins" outcome, just accumulated glow) — this is the same property
    the ring already relies on for its 5 stacked layers.
  - Against **other opaque/cutout geometry** (solid terrain, blocks): correctly occluded,
    since depth test is on and terrain writes real depth.
  - Against **water** (a translucent **chunk** layer, not an entity): **not reliably
    depth-sorted**. Vanilla's non-Fabulous pipeline renders translucent chunk geometry as
    its own pass, and entity-translucent buffers (all `RenderType`s like this one, batched
    through `MultiBufferSource.BufferSource`) are flushed in **RenderType-registration/call
    order**, not resorted per-pixel against the water chunk pass. Depth **test** protects
    against being drawn through solid opaque geometry, but two translucent passes with one
    or both not writing depth can end up compositing in the "wrong" visual order (e.g. dome
    painted, then water painted over it regardless of which is actually nearer the camera,
    or vice versa) — this is a known, generic Minecraft translucency limitation, not
    specific to this RenderType, and nothing in `is_src` or this repo works around it.
  - Against **particles**: same category of issue — particles are their own render pass,
    not per-pixel sorted against arbitrary entity-translucent RenderTypes.
  - Against **other translucent entities** (a second dome, a Shield, etc.): same — whichever
    is submitted to the buffer/flushed first wins visually where they overlap, not whichever
    is actually nearer.
- **Which RenderType minimizes this**: none found in `is_src` does anything beyond what's
  already used here (test-on/write-off, additive). This is the closest to "as sorted as it
  gets" without genuinely custom transparency handling — the `.setWriteMaskState(true,
  false)` pattern (and the additive blend's order-independence for the dome's *own*
  self-overlap) is a mitigation, not a fix, for the terrain/water/particle cross-pass
  issue. A true fix (e.g. rendering the dome into its own manually depth-sorted pass, or
  writing depth from just the nearest hemisphere surface) was not found in any existing
  code this pass and was out of scope to design from scratch here — flag as a known
  trade-off to accept for Phase 2A, same as embracing_bosom already accepts it, not a
  regression specific to the dome.

---

## Q4 — Frustum culling: tiny bounding box vs. a radius-10 hemisphere

**Confirmed root cause, confirmed existing fix pattern, confirmed this dome is currently
exposed to the bug (no real renderer registered yet).**

- `AoeEntity.m_6972_ (getDimensions)` (`AoeEntity.java:271-273`, decompiled):
  ```java
  public EntityDimensions m_6972_(Pose pPose) {
     return EntityDimensions.m_20395_(this.getRadius() * 2.0F, 1.2F);
  }
  ```
  Width scales correctly with `setRadius()` (confirmed — not the bug), but **height is
  hardcoded to `1.2`** regardless of radius. `CrystalHydroDomeAoe`'s own class javadoc
  (lines 54-58) already documents this exact fact for the same reason Part 1 flagged it for
  hit-detection: "AoeEntity.m_6972_ hardcodes height to 1.2 regardless of setRadius(), which
  would truncate a 5-block-tall hemisphere" — at the current `RADIUS = 10.0`, the real
  hemisphere is 10 blocks tall, but the entity's actual bounding box (which vanilla's
  frustum-culling `shouldRender` check is built from) is only 1.2 blocks tall, centered at
  the entity's feet. A camera looking at the upper 8+ blocks of the dome, with the entity's
  tiny box out of frame (e.g. the dome's origin point below/behind the camera's near
  frustum edge), will have the whole dome culled even though most of its visible surface is
  on-screen.
- **Confirmed existing fix pattern, used by every large/off-center VFX entity found in
  `is_src`**: override `EntityRenderer<T>.shouldRender(...)` to unconditionally return
  `true`, bypassing the frustum/bounding-box test entirely. Found identically in three
  places — `EldritchBlastRenderer.java:42-44`, `RayOfFrostRenderer.java:42`,
  `SunbeamRenderer.java:24` (all beam/ray-type entities whose true visible extent doesn't
  match a small hitbox):
  ```java
  public boolean shouldRender(EldritchBlastVisualEntity pLivingEntity, Frustum pCamera, double pCamX, double pCamY, double pCamZ) {
     return true;
  }
  ```
  No `noCulling`-named field/flag or `getBoundingBoxForCulling` override was found anywhere
  in `is_src` — irons_spellbooks' own convention is exclusively the `shouldRender() { return
  true; }` override, not inflating the bounding box or a "no cull" entity flag. (There's
  also an unrelated `EntityRendererMixin` (`mixin/EntityRendererMixin.java`) that injects
  into `shouldRender` globally for casting entities — a different mechanism for a different
  purpose (forcing render during any active spellcast animation), not applicable here since
  it's keyed off `ClientMagicData`/`isCasting()`, not entity size.)
- **`EmbracingBosomRingRenderer` does NOT currently override `shouldRender`** — it relies on
  the default `AoeEntity` bounding box (width = `radius*2` = 12 for its 6-block max ring
  layer, height 1.2). Since its rings are flat (no vertical extent beyond `yOffset`), the
  default box likely covers its visible geometry well enough in practice, but this was not
  explicitly verified this pass and is a separate, pre-existing question outside this
  document's scope (not something Phase 2A needs to fix).
- **Recommendation for the dome**: `CrystalHydroDomeRenderer` (or whatever it's named) must
  override `shouldRender(...)` to return `true` unconditionally, mirroring
  `EldritchBlastRenderer`'s pattern exactly — the dome's real extent (10-block-tall
  hemisphere) is even further from its `1.2`-tall bounding box than any of the three
  existing examples, so this is not optional.

---

## Q5 — Iron's other translucent magic visuals under shaders: Shield, "golden_gate", bubble/barrier

**`ShieldEntity`/`ShieldRenderer` found and read in full; no "golden_gate" or generic
bubble/barrier renderer exists anywhere in this modpack's decompiled
`irons_spellbooks-3.16.1` (`is_src`)** — grepped for `golden_gate`, `bubble`, `barrier`,
`dome`, `sphere` across all of `is_src`; only hits were two unrelated particle classes
(`AcidBubbleParticle`, `TintedBubblePopParticle`, cosmetic pop-particle effects, not a
mesh/dome renderer). If "golden_gate" exists, it's in a different mod not present in this
pack's `is_src`, or the name/feature doesn't exist in this irons_spellbooks version — flagged
as not found rather than guessed at.

**`ShieldRenderer`** (`entity/spells/shield/ShieldRenderer.java`, full file read):
- Draws a **real 3D model** (`ShieldModel`, a `ModelPart`/Blockbench-style mesh via GeckoLib
  conventions, `context.m_174023_(ShieldModel.LAYER_LOCATION)`), not a hand-built CPU mesh —
  not a template for a hand-rolled hemisphere.
- RenderType: `RenderHelper.CustomerRenderType.magicSwirl(...)` (`RenderHelper.java:97-99`),
  built on shader field `f_110135_` — this is the obfuscated/intermediate field reference for
  the same `getRendertypeEnergySwirlShader()` shader CLAUDE.md's project notes already
  identify. Independently confirmed here via a second `is_src` read: `RenderHelper`'s
  `MAGIC`/`MAGIC_NO_CULL`/`magicSwirl` RenderTypes (used respectively by various
  ground-magic-glow effects and by the shield) all share this same shader field. Per this
  project's own prior Oculus-jar disassembly (documented in
  `EmbracingBosomRingRenderer.java`'s class javadoc, lines 76-80), this shader routes to
  `ShaderKey.ENTITIES_CUTOUT` under Iris — a real gbuffers stage, but **binary alpha
  discard**, not smooth blending.
- **Alpha is hardcoded to `1.0F`** (`ShieldRenderer.java:46`,
  `this.model.m_7695_(poseStack, consumer, 15728880, OverlayTexture.f_118083_, 0.65F, 0.65F,
  0.65F, 1.0F)`) — no per-vertex, per-face, or view-angle-driven alpha variation anywhere in
  this class. Combined with the cutout-style shader, this confirms the Shield is not
  designed for, and doesn't demonstrate, soft/graduated translucency at all — it's a
  textured, swirling, but ultimately opaque-or-nothing surface at the shader level.
- No shaderpack-aware code of any kind exists in `ShieldRenderer`/`RenderHelper` —
  irons_spellbooks does not special-case Oculus/Iris anywhere found in `is_src`. The
  shader-routing awareness (which specific `GameRenderer.get*Shader()` getters Iris
  recognizes, and which real gbuffers stage each routes to) is entirely this project's own
  prior work (`bhspellsx`'s Oculus-jar disassembly), not something inherited from Iron's
  code.

**Recommendation**: base the dome on `EmbracingBosomRingRenderer`'s
`getRendertypeEntityTranslucentEmissiveShader()` composite (Q1), not on anything from
Iron's Shield/magic-glow RenderHelper. The energy-swirl shader family Iron uses everywhere
(`magic`, `magicNoCull`, `magicSwirl`, all sharing the same `ENTITIES_CUTOUT`-routed shader)
is confirmed unsuitable for a soft fresnel fade for the same reason CLAUDE.md already
records for the ring: binary alpha discard would wreck a graduated edge-brightness effect.
There is no existing Iron visual — Shield or otherwise — that does what this dome needs to
do; `embracing_bosom`'s own ring is the only real precedent in the whole search space.

---

## Gaps to close before implementation

1. **`travel_src` (traveloptics decompile) was not available and was not re-decompiled this
   pass** — no decompiler tool was readily available in this session, and traveloptics is a
   stateless static particle-burst utility (per this project's own `CLAUDE.md`, Phase 2B
   notes), not a mesh/shader renderer, so it's unlikely to contain anything relevant to a
   hand-built translucent hemisphere mesh — but this was not verified by inspection and
   should be closed out (or explicitly waived) before this recon is treated as complete.
2. Exact vanilla API call, from inside an `EntityRenderer<T>.render(...)` override, to get
   the current camera world position for the fresnel `viewDir` term (Q2) — not pinned to a
   file:line citation this pass.
3. Hemisphere mesh topology choice (24×48 `QUADS` like the ring vs. a triangle
   strip/fan-per-band) was not decided — either is feasible per Q2, but not chosen.
4. Whether the dome should read as a pure additive glow-edge (matches the ring's own style
   and the spec's "brighter edge" framing most directly) or a genuine alpha-tinting/darkening
   surface (would need a different `TransparencyStateShard`, not additive) — a design
   decision for the spell's owner, not resolved here (see the note at the end of Q1).
5. Q3's translucency-sorting-vs-water/particles limitation has no fix identified, only the
   same trade-off `embracing_bosom` already accepts — if this turns out to look bad in
   practice for a much larger, always-present dome (vs. the ring's transient burst), a real
   fix (e.g. a dedicated sorted pass) would need separate design work not attempted here.
