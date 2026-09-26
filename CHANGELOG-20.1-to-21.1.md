# Changelog

## 0.8.2 — Minecraft 1.21.1

First 1.21.1 release, ported from the 1.20.1 build (0.8.0).

### Platform

- **NeoForge support added** (NeoForge 21.1.1+). Forge (52.1.0+) and Fabric
  (Loader 0.15.11+, Fabric API 0.106.0+1.21.1) continue; the Fabric jar also runs on Quilt.

### Added

- **`/sb eggs`** — operator diagnostic (permission level 2 on a dedicated server, local on
  a client). Walks every entity type and reports the spawn-egg colors the Mob Bucket would
  tint its overlays with, flagging capturable types with no egg or with unusable or
  identical colors. Writes a report to `config/somebuckets/`.
- **`/sb fluids`** — client diagnostic. Walks every source fluid and mirrors the Big and
  Source Bucket bar-color path, flagging fluids that fall back to the default bar color or
  collapse to near-black.
- **Bundled Mob Bucket overlay-color override table** for entities whose spawn eggs do not
  report usable colors; ships with The Bumblezone's bee queen and variant bee. Extendable
  via `somebuckets/mob_egg_colors.json`.
- Editing the Source Bucket allowlist and running `/reload` now applies without a server
  restart on every loader.

### Changed

- **FTB Chunks integration is now Fabric and NeoForge only.** No FTB Chunks build exists
  for Forge on 1.21.1, so Forge loses the dedicated adapter. Vanilla spawn protection, the
  world border, Forge's `FillBucketEvent`, and ordinary interaction events still apply.
- Player fluid, cauldron, milking, storage, and mob operations now explicitly enforce
  vanilla spawn protection and the world border — buckets cannot act inside the
  spawn-protection radius.
- Junk and Trash Buckets now also reject modded container items (backpacks and similar that
  expose an item inventory), alongside the existing bundle, shulker, and opt-out rules.
- Forge and NeoForge arbitrary-fluid placement is gated on block-edit permission wherever
  the pour would destroy a replaceable block, not just fluid-edit permission.
- Restored vanilla feedback and event hooks on bucket automation (Forge `FillBucketEvent`,
  loader interaction events) that other mods hook.
- Declared and compiled against verified minimum loader versions: Forge 52.1.0,
  NeoForge 21.1.1, Fabric Loader 0.15.11, Fabric API 0.106.0+1.21.1.
