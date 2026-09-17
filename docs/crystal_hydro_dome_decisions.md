# Crystal Hydro-Dome — decisions log (from the claude.ai planning chat)

Companion to `docs/recon/crystal_hydro_dome_vfx_handoff_2026-09-17.md` (VFX history). Current VFX
state: the source (`CrystalHydroDomeVisuals`/`CrystalHydroDomeRenderer`) and `MERGE.md` §C.
This file records WHY things are the way they are: spec agreed with the character owner,
what was cut, bugs already hit, and working rules. Source code wins over this file if
they disagree — but ask the user before "fixing" anything listed here as intentional.

---

## 1. Working rules (were in every prompt — keep following them)

- Propose a plan and wait for approval before changing code. Recon (read-only) first
  when the answer depends on real jars/source.
- Don't add features that weren't requested. If something seems missing, ask.
- Don't commit or push. The user pushes.
- Don't paste full source into chat; summarize + cite paths.
- Build must pass before reporting.
- Logic first, VFX after. Numbers approved by the owner/staff are not tuned silently.
- Shared server we don't own: keep it lean. Prefer client-side visuals, no new mixins,
  no packets unless needed, no server per-tick cost for visuals. Cut bloat.
- Never modify or refactor embracing_bosom / amethyst_decree files (already merged
  upstream by OffKunG).
- Decompiled source beats docs and schema files. Apoli silently ignores unknown fields.

## 2. Deploy rules

- Build: `.\gradlew.bat build --console=plain` in `Origins/Mods/bhspellsx`.
- PrismLauncher test client (joins LAN): delete old `*bhspellsx*` jar, copy new one to
  `C:\Users\User\AppData\Roaming\PrismLauncher\instances\Minguye Origins Work 1.0.1 1.0.0\minecraft\mods`.
  Don't block on javaw processes (user runs other instances); stop only if the file is locked.
- Modrinth host profile `D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1`:
  NEVER copy the jar into mods\ (app reports "needs repair or re-import"). Give the user
  the jar path; they delete the old one and use Upload files in the app.
  Once a test ran against an old jar because this step was skipped — verify by hash if
  results look impossible.
- Datapack: `Origins/liming` → `<Modrinth profile>\saves\New World\datapacks\liming\`.
- Test layer (local only): `pers:liming` added to the Personal datapack's
  `origins/origin_layers/origin.json`. Never ship origin_layers.
- Game must be restarted to load a new jar.

## 3. Final gameplay spec (agreed with owner via HaconN, tested and passing)

- Cast: Apoli button → mana 70 (base:spell/mana) → `cast @s bhspellsx:crystal_hydro_dome`.
  Spell is Iron's CONTINUOUS, cast time 120 ticks, Java mana/cooldown 0, school
  `traveloptics:aqua`, no Spell Power scaling.
- Cooldown 30s, starts AFTER the dome ends (Apoli `action_over_time` on tag
  `pers_liming_dome`, `falling_action` → trigger_cooldown, `interval: 1`).
- Hemisphere radius 10, height 10, fixed at cast position. Gameplay shape stays a
  hemisphere; the visual shell extends lower (visual only).
- Open burst (once, first cast pulse only): everyone inside incl. caster — remove
  harmful effects except infinite-duration ones, clear fire + freezing, heal 50.
  Late entrants get nothing from the burst.
- While alive (6s), dome HP 150:
  - Attacker inside / no attacker: victim takes 70%, dome absorbs 30%, no counter.
  - Attacker outside: victim takes 0, dome takes 100% (dome-owned per-victim i-frame,
    10 ticks or the SpellDamageSource override), counter if attacker is a Player.
  - AoE hitting N people inside costs the dome N times and counters N times (intentional).
  - Arrows (AbstractArrow) from outside reflect, keep original owner, dome takes predicted
    damage, shooter still countered. Magic projectiles dissolve, dome takes getDamage().
    Unknown projectiles dissolve for 0. Ender pearls pass. Fishing hooks dissolve.
  - Counter = 50% of what the dome actually took, aqua damage, setIFrames(0), no range limit.
  - Overflow when dome HP runs out is lost; victim inside still only takes 70%.
  - Stacks multiplicatively with embracing_bosom. BYPASSES_INVULNERABILITY ignored.
- Caster rooted with `efn:horizontalstop` + `efn:verticalstop` re-applied each tick
  (look + attack allowed). Chat/inventory can't be opened during the cast (observed,
  dome stays up) — accepted.
- Natural end (cast completes; tick 125 fallback): heal 40 to everyone inside,
  knockback strength 1.5 on everyone in the ring 10–13 blocks out.
- Early break (dome HP 0, caster dead/moved out/logout, cast cancelled by opening a
  container or pressing another skill): no end heal, no knockback, cast bar cleared.
- Action bar (caster only): green "เลือดของโดม", red ❤, `hp/150` white → red under 30%.
  Cleared immediately on end.
- Skill icon: `irons_spellbooks:textures/gui/spell_icons/ice_block.png`.

## 4. Cut / rejected — don't reintroduce without asking

- Melee hitting the bare dome surface (no entity behind): NOT detected. Needed EFN mixin
  or client packet; user rejected as too invasive for a shared server.
- Iron's RecastOverlay dot timer: cut (the cast bar does this job now).
- Ally selection before casting: cut; everyone in range is affected.
- `efn:stop`: blocks ALL input via client mixins. Rejected for the dome.
  amethyst_decree still uses it on purpose (full freeze accepted); README documents this.
- `pehkui:motion 0`: banned by the IT lead.
- Slowness 255: amplifier is a byte on network/NBT, 255 arrives as -1.
- Earlier lotus iterations (7.0 petals, width 0.28, gold disk, stamens, additive shine on
  whole petals): replaced — see the VFX handoff §6.

## 5. Bugs already hit (lessons)

- Apoli power ids must include every folder: `pers:liming/active_i/skill1`
  ("Removed N missing powers" in latest.log, no parse error).
- CONTINUOUS casts call onCast every 10 ticks AND on the terminal pulse → dome spawned
  twice. Fixed by spawning on first pulse only + cast completion drives natural end.
- Canceling LivingAttackEvent means vanilla never sets victim i-frames → needed a
  dome-owned i-frame map, otherwise per-tick damage drained the dome every tick.
- LivingAttackEvent fires client-side for players → server-side guard required.
- `knockback(strength, x, z)` direction is victim→source; ground friction makes
  0.7 only ~2 blocks.
- Non-emissive translucent shader + lightmap disabled = solid black geometry.
  Use `entityTranslucentEmissive` for alpha-blended full-bright visuals.
- Tiny entity bounding box → frustum culling; renderer `shouldRender` returns true.
- Forge config files override code defaults once generated → constants are hardcoded.
- Entity `discard()` at end removes it from clients the same tick → nothing left to animate,
  and the last Counter bolt never shows. Fixed by lingering 30 ticks after finish() with
  gameplay already off (removed from ACTIVE_DOMES).
- `playSound` volume > 1 only widens hearing range, it doesn't get louder. For a heavier
  sound, layer several sounds (and pitches) instead.
- Shards cut from the shell's lat/long grid looked like a sliced pattern. Use jittered
  triangles grouped into random Voronoi cells for crack-like, irregular shards.

## 6. Current state — Phase 2C/2D shipped (tested and approved 2026-09-17)

Source of truth for every visual value (colors, counts, sizes, timings) is
`src/main/java/net/offkung/bhspellsx/client/renderer/CrystalHydroDomeVisuals.java`; drawing
code is `CrystalHydroDomeRenderer.java`. The expected in-game look after merge is listed in
`MERGE.md` §C "Verifying after merge". This section records only what exists and why.

- Shell lightning (2C): client-only, drawn by the renderer over the shell; no server cost.
- Counter bolts (2C): server writes the attacker's offset + a serial into synced entity data
  when a Counter fires (ring buffer, one bolt per Counter); client draws a bolt from a
  randomly tilted point on the shell to the attacker, at any distance. No custom packet
  channel. A viewer who starts tracking mid-dome doesn't replay old bolts.
- Water streaks on the shell and lotus palette: added/changed after the owner reviewed a clip
  (owner asked for a water pattern, pink outer petals, gold heart kept, more streaks).
- End state (2D): synced once at `finish()` as natural vs broken. Gameplay stops immediately
  (dome leaves `ACTIVE_DOMES`); the entity lingers `END_LINGER_TICKS` so clients can animate
  instead of the dome vanishing.
- Natural end (2D): shell expands and fades, water-ring wave marks the knockback zone, lotus
  spreads and fades, drifting petals fly outward. Holy/chime sound.
- Broken end (2D): shell cracks into irregular Voronoi shards that hold briefly then fly,
  fall and fade; lotus folds and sinks; the rest fades. Layered glass/ice break sound with a
  short tinkling tail (layered because volume > 1 doesn't get louder, see §5).
- The owner/user specified what each ending should look like; exact wave/shard parameters
  were implementation choices, then approved in-game.

## 7. Remaining work

- Owner review: the latest build hasn't had the character owner's final reply yet; the user
  judged it done and prepared it for merge on 2026-09-17.
- Delivery: hand to OffKunG per `MERGE.md` §C, with origin id `pers:liming`. After merge, the
  liming datapack's `skill1.json` command must change from `bhspellsx:crystal_hydro_dome` to
  `bhspells:crystal_hydro_dome` (`MERGE.md` §C step 8).
- Tell the character owner (ลี่หมิง's player) before/at delivery:
  - bare-surface melee is not blocked/countered; hits on people inside, arrows, spells
    and AoE are
  - AoE on many people inside can break the dome fast and counter hard
  - opening a container or pressing another skill breaks the dome
  - can't walk/jump or open chat/inventory during the 6s; can look and attack
  - numbers may be rebalanced after testing (staff note)
