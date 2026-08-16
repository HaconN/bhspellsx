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

## Two-package merge convention

- `net/offkung/bhspellsx/` — bootstrap-only. `@Mod` entry point, client event bus
  subscriber, the local `DeferredRegister` (`BHXSpellRegistry`). **Deleted entirely** at
  merge time.
- `net/offkung/bhspells/` — portable content (spell classes, and in later phases entities,
  particles, etc.). Written as if it already lives inside the real `bhspells` mod. **Copied
  verbatim** into the real bhspells source tree at merge time; only the registry wiring and
  namespace strings change (see `MERGE.md`).

When adding new content, always ask "does this belong in the bootstrap, or is it portable
spell content?" before picking a package. When in doubt, portable — the bootstrap package
should only ever contain wiring.

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
