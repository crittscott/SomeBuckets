# Changelog

## 0.8.2 — Minecraft 1.21.4

First 1.21.4 release, ported from the 1.21.1 build.

### Platform

- **Minecraft 1.21.4.** Requires Forge 54.1.0+, NeoForge 21.4.139+, or Fabric Loader 0.16.9+
  with Fabric API 0.110.5+1.21.4.
- **FTB Chunks integration removed.** There is no FTB Chunks build for 1.21.4, so Some Buckets
  no longer has a dedicated claim-mod adapter on any loader. Vanilla spawn protection, the world
  border, Forge's `FillBucketEvent`, ordinary loader interaction events, and Open Parties and
  Claims' own hooks still apply.

### Changed

- **Stacks of empty buckets fill one at a time, as vanilla buckets do.** Using a stack of empty
  Some Buckets fills one; it goes into your inventory, or drops at your feet if nothing fits.
  Creative mode keeps the empties. Applies to fluids, powder snow, milk, mobs, items, cauldrons,
  and tanks. A stack of empty Junk or Trash Buckets must be split to a single bucket before it
  accepts items in an inventory.
- **Held-transfer overflow goes to your inventory** instead of dropping on the ground; only what
  does not fit is dropped.
- **Junk and Trash Buckets follow vanilla item-pickup rules.** A player's bucket leaves items
  dropped for another player, honors Forge and NeoForge pickup-event vetoes, and counts toward
  picked-up statistics.
- **Dispensers act as a fake player named `[SomeBuckets]` on every loader.** It is subject to
  permission checks, including spawn protection, but earns no statistics or advancements.
  Dispensers now feed and milk animals through the animal's own interaction.
- **Off-hand transfers behave the same on every loader:** the Some Buckets container gives before
  it takes.
- **NeoForge Source Bucket allowlist is now per-world**, in the world save's
  `serverconfig/somebuckets-server.toml`, matching Forge. Admins who edited the old global
  `config/somebuckets-server.toml` on NeoForge should carry their settings over.
- **Fabric servers now send their Source Bucket allowlist to clients** on join and after
  `/reload`.
- **Fabric structure loot** now adds bucket rolls even when a data pack replaces the target loot
  table, as Forge and NeoForge already did.
- Drinking milk from a Big, Huge, or Source Bucket uses vanilla's drinking mechanics.
- Placing fluids awards the vanilla placed-block advancement trigger, and filling cauldrons
  awards the cauldron statistic.
- NeoForge leaves modded cauldrons to their own fluid handling.
- **Item appearance uses 1.21.4 item definitions** (`assets/somebuckets/items/*.json`). Resource
  packs made for the 1.21.1 item models need updating.
- **`/sb eggs` is now client-only**, because spawn-egg colors are client resources in 1.21.4. It
  also flags spawn eggs whose item definition supplies no colors.

### Fixed

- Releasing an aquatic mob from a Mob Bucket no longer triggers sculk sensors twice.
- Corrected several sound and water-evaporation effects.
- Modded fluid variant data is preserved intact on Forge, NeoForge, and Fabric.
- The server now rejects malformed bucket data, and Mob Buckets send only a type-and-count
  summary to clients instead of full mob data.
- Conversion of bucket data from 1.20.1 worlds now either completes fully or sets the
  unconvertible data aside once, without partial changes or repeated retries.
- Removed an unused internal `fluid_model_probe` item from Forge and NeoForge.
