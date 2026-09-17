# Crystal Hydro-Dome — Phase 2 recon, Part 1: melee hitting the dome surface

Recon only — no code changes made as part of this investigation. This fork ran out of
budget partway through, so some sub-questions are confirmed with file:line citations and
others are explicitly flagged as gaps rather than guessed at. A follow-up pass should close
those gaps before implementation.

## Requirement

A player standing OUTSIDE the dome who swings their weapon at the dome's surface (the
swing connects with nothing — no entity, no block, since the dome deliberately has no
entity hitbox) should still damage the dome and get Countered, exactly as if they'd hit an
entity inside it. The dome must NOT gain an entity hitbox — that would physically block
clicks from inside the dome and extend collision past the actual sphere shape. See
`entity/spells/crystal_hydro_dome/CrystalHydroDomeAoe.java` for the existing mechanics this
needs to integrate with: `isInside()` (manual sphere/hemisphere test, no vanilla collision),
the HP pool (`absorb()`), and the outside-Player Counter (`applyCounter()`).

## Q1 — Vanilla server-side "swung at nothing" signal

**Confirmed: none exists.**

`ServerboundSwingPacket` (`net/minecraft/network/protocol/game/ServerboundSwingPacket.java`)
carries only an `InteractionHand` — no target, no hit-result. Its handler,
`ServerGamePacketListenerImpl.handleAnimate` (line 1358-1362), just calls
`this.player.swing(hand)` — a cosmetic arm-swing broadcast sent unconditionally on every
left-click, hit or miss. Actual entity-attack resolution goes through a **separate** packet
(`ServerboundInteractPacket`), sent by the client only when its own raycast already hit an
entity in reach; block-breaking uses yet another packet family.

**Server-side, a swing at empty air is indistinguishable from a swing at an out-of-range
block or at nothing at all** — `PlayerInteractEvent.LeftClickEmpty` (client-only) is the only
place vanilla itself notices this, and it never reaches the server.

**Minimal client-side detector needed instead**: on attack input, check the client's own
`Minecraft.hitResult` (already computed every frame for interaction/mining) — if its type is
`HitResult.Type.MISS`, the swing hit nothing; if `BLOCK` or `ENTITY`, it didn't. Send a small
custom Forge network packet in the `MISS` case, carrying the swing's origin/look vector (and
ideally a same-swing key, see Q4), so the server can test that ray/reach volume against the
dome's `isInside()` sphere.

## Q2 — Epic Fight's attack resolution

**Confirmed: a real per-frame collider, not a raycast-on-click.**

Package root is `yesman.epicfight` (not `io.redspace` — that's Iron's Spellbooks). Melee
attacks are driven by `AttackAnimation`
(`yesman/epicfight/api/animation/types/AttackAnimation.java`), each with `Phase[] phases`
(line 63) carrying a `Collider`. `Phase.getCollidingEntities(...)` (line 476-488) calls
`collider.updateAndSelectCollideEntity(entitypatch, animation, prevElapsedTime, elapsedTime,
joint, attackSpeed)` (line 484) — a genuine swept-volume query run every animation tick
during the phase's active window, returning real `Entity` hits, not a single click-time
raycast. `Collider` (`yesman/epicfight/api/collider/Collider.java:25-30`) is abstract,
constructed from a `Vec3 center` + `AABB outerAABB`; concrete subclasses presumably define
the actual sphere/OBB shape but were not explored in this pass.

An `AttackPhaseEndEvent` fires via `playerpatch.getEventListener().triggerEvents(...)`
(line 152) — EFN's own internal event-listener system (not a Forge event), giving a hook at
phase *end* only.

**Gap (unconfirmed, needs a follow-up pass):**
- No per-tick "collider is active now" hook was found within budget — the natural
  interception point looks like it would be alongside/near the `Phase.getCollidingEntities`
  call site itself (mixin or a wrapping call), not a public event.
- `Collider` subclass shapes and the reach/collider-size attribute lookup
  (`entitypatch.getColliderMatching(hand)`, referenced at line 482) were not read.

## Q3 — Damage prediction without a real target

**Not verified this pass — flagged as a gap.**

For vanilla: `Player.attack()` is the known entry point that assembles attribute damage +
enchantment bonus (Sharpness etc.) + attack-strength-cooldown scaling (the 0.2→1.0 ramp) +
crit chance/multiplier, but whether a clean sub-routine exists that computes the resulting
number *without* an actual target entity was not confirmed — needs a direct read of
`Player.attack()` in `net/minecraft/world/entity/player/Player.java`.

For Epic Fight: a likely candidate is somewhere on a weapon-capability class (seen only via
grep hits, e.g. `yesman/epicfight/world/capabilities/item/CapabilityItem.java`), not read in
this pass. Unconfirmed whether Epic Fight damage is computable independent of an actual hit,
or only ever materializes via its own `DamageSource` construction at hit-time (in which case
it would need similar hand-assembly to vanilla).

## Q4 — Double-counting key

**Partially confirmed.**

No explicit vanilla "swing sequence id" is surfaced to mods. `Entity.tickCount` at the
server tick when the swing/attack packet is handled is the natural same-swing key — two
effects (a dome-surface-hit and a real entity hit) originating from the same server tick's
attack action are almost certainly the same swing.

For Epic Fight, `AttackAnimation.Phase` carries no per-instance id found in this pass, but
the `Phase` object reference itself, plus the attacking `LivingEntityPatch`'s current
animation elapsed-time window (`[prevElapsedTime, elapsedTime]` for that tick), is a
plausible — but not fully confirmed — same-swing key.

## Proposed design (draft, pending the flagged gaps)

- **Vanilla path**: client checks `Minecraft.hitResult.getType() == MISS` on attack input; if
  true, sends a custom packet with the swing's ray/reach info to the server via a new Forge
  network channel handler (not `handleAnimate` itself — that's vanilla's own unmodifiable
  handler). Same-swing key: `serverPlayer.tickCount` at receipt.
- **Epic Fight path**: hook at (or mixin near) `AttackAnimation.Phase.getCollidingEntities` —
  after EFN's own call returns its entity list, additionally test the phase's collider volume
  (`Vec3 center` + `AABB outerAABB`) against the dome's `isInside()`/`searchBox()` for a
  miss-but-in-reach-of-dome case. Same-swing key: the `Phase` instance + elapsed-time window
  for that tick.
- **Dedup in the dome**: track `(attackerUUID, sameSwingKey)` transiently — e.g. a small
  per-tick set cleared every tick — in `CrystalHydroDomeAoe`/`CrystalHydroDomeEvents`. If a
  real `LivingAttackEvent` hit is already recorded for that key this tick, skip the
  surface-hit charge (or vice versa, whichever resolves first in a given tick).

## Gaps to close before implementation

1. Epic Fight `Collider` subclass shapes and the reach/collider-size attribute lookup (Q2
   tail).
2. A per-tick (not just per-phase-end) Epic Fight collider-active hook, if one exists, as a
   cleaner interception point than mixining near `getCollidingEntities`.
3. Vanilla and Epic Fight no-target damage computation (Q3, in full — this pass found no
   confirmed API for either engine).
4. Confirming an Epic Fight per-swing instance id, or validating the `Phase` +
   elapsed-time-window key as a reliable substitute (Q4 tail).

Part 2 (rendering a transparent dome, for Phase 2A) was deferred to a separate session and
is not covered by this document.
