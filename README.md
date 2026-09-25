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
- **`crystal_hydro_dome`** — Li Ming's 10-block warding dome (6 s, caster held in place):
  damage to anyone inside is redirected into the dome's own 150 HP pool, outside player
  attackers are struck back with lightning, projectiles are blocked at the boundary, and
  allies inside are healed on open and close. Aqua school (traveloptics).
- **`xian_she_huan_ying`** — Wu Liangye's toggle hunt: locks one target, target gets Slowness I
  + Darkness I, eye-pair VFX over both, smoke/mist around the caster. Gold school.
  Meant to be driven by the Apoli datapack `wu_liang_ye`, which owns the toggle, the mana drain
  and the caster's buffs through the `wly_snake` tag. To test with a bare `/cast`, run
  `/tag @s add wly_snake` first, and stop the lock with `/tag @s remove wly_snake`.

## Target environment

Minecraft 1.20.1, Forge 47.4.20, irons_spellbooks 1.20.1-3.16.1. Full table in `CLAUDE.md`.

`bhspellsx` also needs `bhspells` at runtime (its spells use the `bhspells:gold` / `bhspells:ground`
school ids), even though `mods.toml` doesn't declare it — without it the game crashes when the
creative inventory opens.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`. There is no `runClient`/`runServer` — this modpack can't
run in a dev environment. Test by copying the built jar into the real modpack's `mods/`
folder and launching that profile.

## Testing on a dedicated server

The dev environment can't run, but we still need to confirm no client class leaks into
server-side code, which only shows up on a real dedicated server. Use a Forge 47.4.20 server
with the minimal mod set below (copied from the profile, plus the freshly built
`bhspellsx-0.1.0.jar`), kept in `IT Origins Work\server test\mods`:

```
ApothicAttributes-1.20.1-1.3.7.jar
L_Enders_Cataclysm-3.16.jar
Placebo-1.20.1-8.6.3.jar
aaa_particles-forge-1.20.1-2.2.0.jar
alexscaves-2.0.2.jar
bendy-lib-forge-4.0.0.jar
bhspells-1.20.1-1.3.0-forge.jar
bhspellsx-0.1.0.jar
bhweapons-0.3.2.jar
citadel-2.6.3-1.20.1.jar
curios-forge-5.14.1+1.20.1.jar
epic-fight-20.14.17-mc1.20.1-forge.jar
epic_fight_avalon-20.12.6.4.jar
epicfight-extra-1.2-mc1.20.1-forge-all.jar
geckolib-forge-1.20.1-4.8.3.jar
irons_lib-1.20.1-1.0.2.jar
irons_spellbooks-1.20.1-3.16.1.jar
lionfishapi-2.8.jar
player-animation-lib-forge-1.0.2-rc1+1.20.jar
traveloptics-6.3.0-1.20.1.jar
```

- The client instance uses the same mods (Oculus is fine to add).
- `epicfight-extra` and `epic_fight_avalon` must be on both the server and the client, otherwise
  the join fails with a mismatched mod channel list.
- An `ERROR ... efn:... not found` at server start is expected when Epic Fight Nightfall isn't
  installed (the root/freeze effects are skipped; nothing else breaks).
- Pass = the server reaches `Done`, a client can join, and `/cast` of every `bhspellsx` spell
  produces no crash and no new `ERROR`.

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
