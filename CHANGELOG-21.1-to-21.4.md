# Changelog

## 0.8.2 — Minecraft 1.21.4

First 1.21.4 release, ported from the 1.21.1 build.

### Platform

- **Minecraft 1.21.4.** Requires Forge 54.1.0+, NeoForge 21.4.139+, or Fabric Loader 0.16.9+ with Fabric API 0.110.5+1.21.4.
- **FTB Chunks integration removed.** There is no FTB Chunks build for 1.21.4, so Some Buckets no longer has a dedicated claim-mod adapter on any loader. Vanilla protection, ordinary loader interaction and block events, Fabric's optional Common Protection API integration, and claim mods' own dispenser hooks still apply.

### Upgrading from 1.21.1

- **Worlds upgrade one release at a time.** Bucket contents saved by the 1.21.1 release are converted as they load, including buckets held in other mods' storage, and stored mobs and items are upgraded to 1.21.4. Back up the world first.
- **1.20.1 worlds must be opened with the 1.21.1 release first.** Any 1.20.1 bucket data that release never converted is left in place, unused.

### Changed

- **Junk and Trash Buckets set aside items that can no longer be loaded**, such as items from a removed mod, instead of losing the whole bucket. The tooltip reports how many are set aside, and each returns to the bucket once it loads again and there is room.

- **Stacks of empty buckets fill one at a time, as vanilla buckets do.** Using a stack of empty Some Buckets fills one; it goes into your inventory, or drops at your feet if nothing fits. Creative mode keeps the empties. This applies to fluids, powder snow, milk, mobs, items, cauldrons, and tanks. A stack of empty Junk or Trash Buckets must be split to a single bucket before it accepts items in an inventory.
- **Held-transfer overflow goes to your inventory** instead of dropping on the ground; only what does not fit is dropped.
- **Junk and Trash Buckets follow vanilla item-pickup rules.** A player's bucket leaves items dropped for another player, honors Forge and NeoForge pickup-event vetoes, and counts toward picked-up statistics.
- **Protection now follows the corresponding vanilla action.** Real-player block changes obey spawn protection, the world border, adventure-mode item-use rules, and the appropriate loader break or place hooks. Entity interactions such as milking, feeding, capture, and item intake obey the world border and their loader interaction hooks. A denial leaves both the bucket and target unchanged.
- **Dispensers act through a stable automation-player identity named `[SomeBuckets]` on every loader.** Like vanilla dispensers, they obey the world border but not player spawn-protection or adventure-mode checks, and they earn no statistics or advancements. Claim mods that guard dispensers through their own hooks can still deny the action. Dispensers milk and feed through the animal's own interaction.
- **Mob Buckets no longer capture leashed mobs or another player's owned mob.** An owner can still capture their own mob, while dispensers capture no owned mob. Dispensers also refuse to feed an untamed tamable animal, so they cannot become its owner.
- **Off-hand transfers behave the same on every loader:** the Some Buckets container gives before it takes.
- **Filled fluid buckets now provide correct crafting remainders.** A Big or Huge Bucket used as a recipe ingredient returns with one fluid, milk, or powder-snow unit removed; an assigned Source Bucket returns unchanged. Empty buckets provide no remainder.
- **NeoForge Source Bucket allowlist is now per-world**, in the world save's `serverconfig/somebuckets-server.toml`, matching Forge. Admins who edited the old global `config/somebuckets-server.toml` on NeoForge should carry their settings over.
- **Fabric servers now send their Source Bucket allowlist to clients** on join and after `/reload`. The payload is bounded, applied on the client thread, and cleared on disconnect so policy cannot leak between servers.
- **Fabric structure loot** now adds bucket rolls even when a data pack replaces the target loot table, as Forge and NeoForge already did. Each reward is also exposed as a separate `somebuckets:inject/<reward>` loot table so data packs can tune or disable individual rolls.
- Drinking milk from a Big, Huge, or Source Bucket uses vanilla's drinking mechanics.
- Player world-fluid pickup and Mob Bucket capture fire the filled-bucket advancement trigger. Placing fluids fires the placed-block trigger, and filling cauldrons awards the cauldron statistic.
- NeoForge leaves modded cauldrons to their own fluid handling.
- On Fabric and NeoForge, lava-filled buckets use the current vanilla lava-bucket fuel value. Forge uses 20,000 ticks because its item fuel hook supplies no current fuel-values context.
- **Item appearance uses 1.21.4 item definitions** (`assets/somebuckets/items/*.json`). Resource packs made for the 1.21.1 item models need updating.
- **`/sb eggs` is now client-only**, because spawn-egg colors are client resources in 1.21.4. It also flags spawn eggs whose item definition supplies no colors.
- The bundled Mob Bucket color overrides now include Wilder Nature's animals in addition to The Bumblezone's bee queen and variant bee.

### Fixed

- Releasing an aquatic mob from a Mob Bucket no longer triggers sculk sensors twice.
- Failed or denied fluid, powder-snow, tank, and cauldron operations no longer debit the bucket or leave a rejected block placement behind. An aquatic-mob release denied by protection does not place water or remove the stored mob. On Forge and NeoForge, a cancelled placement also restores replaceable blocks without producing drops.
- A third-party fluid tank that executes less than it promised during simulation no longer duplicates fluid: a bucket receives only what was actually drained, and any nonzero tank fill consumes one whole finite-bucket unit.
- Corrected bucket fill and empty sounds, Mob Bucket capture sounds, sculk game events, and ultra-warm water-evaporation effects.
- Modded fluid variant data is preserved intact on Forge, NeoForge, and Fabric, including through save migration and network synchronization.
- Invalid or impossible bucket component combinations no longer survive load or network synchronization; readable Junk and Trash entries are preserved or set aside instead of being silently lost.
- Removed an unused internal `fluid_model_probe` item from Forge and NeoForge.
