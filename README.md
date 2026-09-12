# bhspellsx

A staging area, not a shipping mod. `bhspellsx` builds a real, loadable Forge jar so new
`irons_spellbooks` spells can be developed and tested against the exact modpack API surface
before being folded into the real `bhspells` mod by the team lead. See `MERGE.md` for that
procedure and `CLAUDE.md` for the project's technical conventions and known gotchas.

## What's currently in it

- **`embracing_bosom`** — warding-circle channel: heals players standing in it and reduces
  damage taken while blessed. Ground school.
- **`amethyst_decree`** — Zi Xiaoyu's PvP burst: damage + hard root + stun + debuffs + DoT
  in a radius around the caster. Gold school.

## Target environment

Minecraft 1.20.1, Forge 47.4.20, irons_spellbooks 1.20.1-3.16.1. Full table in `CLAUDE.md`.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`. There is no `runClient`/`runServer` — this modpack can't
run in a dev environment. Test by copying the built jar into the real modpack's `mods/`
folder and launching that profile.

## Repopulating `libs/`

`libs/*.jar` is gitignored, so **a fresh clone will not build until these are placed by
hand.** `irons_spellbooks` and `irons_lib` are pulled from CurseMaven automatically by
`build.gradle` — do not put jars for those two in `libs/`, they aren't referenced there.
Only these three come from `libs/` (flat-dir, exact filenames matter — `build.gradle`
references them by name):

```
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\geckolib-forge-1.20.1-4.8.3.jar
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\traveloptics-6.3.0-1.20.1.jar
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\bhspells-1.20.1-1.3.0-forge.jar
```

Copy all three into this project's `libs/` folder before building.
