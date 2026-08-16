# bhspellsx

Phase 0 bootstrap Forge mod for the Mingyue Eclipse "guzilan" origin work. Builds a real,
loadable jar that registers one no-op `irons_spellbooks` spell (`Embracing Bosom`) purely to
prove the registration/cast pipeline works against the exact modpack API surface before any
real content is built. See `CLAUDE.md` for the full project conventions and `MERGE.md` for
how this gets folded into the real `bhspells` mod later.

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

`libs/*.jar` is gitignored — these are redistributed mod jars, not something this repo
should ship or commit. On a fresh checkout, copy the following from the modpack's own mods
folder before building:

```
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\irons_spellbooks-1.20.1-3.16.1.jar
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\irons_lib-1.20.1-1.0.2.jar
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\geckolib-forge-1.20.1-4.8.3.jar
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\traveloptics-6.3.0-1.20.1.jar
D:\Game\Modrinth App\profiles\Minguye Origins Work 1.0.1\mods\bhspells-1.20.1-1.1.3-forge.jar
```

into this project's `libs/` folder. Exact filenames matter — `build.gradle` references them
by name.
