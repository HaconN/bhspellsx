# bhspellsx — Phase 0 Forge addon bootstrap

Read this first. `bhspellsx` is a throwaway bootstrap project: a real, buildable Forge mod
used to develop and test new spell content against the real modpack API surface before it
gets folded into the actual `bhspells` mod by the team lead (see `MERGE.md`).

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
| bhspells | 1.20.1-1.1.3-forge |

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
etc.) are merge targets — written as if they already lived inside the real `bhspells` mod,
modulo the namespace. The mod entrypoint (`BHSpellsX`, `BHSpellsXClient`) and the
`registry/` package are throwaway wiring, deleted at merge time.

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

All jars in `libs/` are `compileOnly fg.deobf(...)` — copied read-only from the modpack's
own `mods/` folder, never from CurseMaven or any remote maven (project policy — keeps this
buildable offline and guarantees exact version match with the live pack). They are
`.gitignore`d; see `README.md` for how to repopulate `libs/` on a fresh checkout.

Phase 0's `mods.toml` only hard-depends on `irons_spellbooks` and `irons_lib`. geckolib,
traveloptics, and bhspells are on the compile classpath for later phases but are **not**
runtime dependencies yet — Phase 0's spell doesn't call into any of them beyond a single
hardcoded school `ResourceLocation` string (see `EmbracingBosomSpell` — deliberately not a
compile-time dependency on `bhspells` itself, resolved only at real runtime since bhspells
is always present in the live modpack).
