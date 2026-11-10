<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Structure, state, and loader seams. Behavior: `orientation-player.md`.

## Project shape

Some Buckets is a Java 21 mod for Minecraft 1.21.8 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, client type registration and fluid facts, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid facts, config, loot injection, policy networking, and test discovery |

Architectury Loom transforms `common` into each loader jar; `common` is not a runtime mod, and Forge and NeoForge are independent. Loader-owned classes carry `Forge`, `NeoForge`, or `Fabric` prefixes. Common Java imports no loader runtime API except cross-remapped `@Environment`. The common `somebuckets.accesswidener` opens `ItemEntity.target` so Junk intake can honor its reserved recipient; NeoForge converts it to an access transformer; Forge mirrors it in its own access transformer, checked during `check`. Item components hold all bucket state; there are no blocks, block entities, menus, or saved-world objects. Fabric's Source policy snapshot is the only custom gameplay payload.

## Code map

| Area | Primary owner |
| --- | --- |
| Item identities, all capacities, creative variants | `BucketDefinitions`, `CreativeBucketCatalog` |
| Shared item base: stack size, decode-time restoration and validation | `SomeBucketItem` |
| Fluid container rules, naming, milking; Source policy | `FluidBucketItem` (`BBItem`, `SBItem`), `config/SBPolicy` |
| Furnace-fuel hooks | Fabric `FuelValuesMixin`; Forge `ForgeFuel` and item shells; NeoForge item shells |
| Big/Huge and Source world transactions, world pickup, water placement | `fluid/FluidTransactions` |
| Junk/Trash behavior | `JBItem` with its `intake` rule overridden by `TBItem`; layout state in `BucketState` |
| Mob behavior and tint identity | `MBItem`, `client/MobEggColors` |
| Serialization, validation, admission | `BucketState`, `ModDataComponentTypes` |
| Saved-format upgrades | `util/BucketStateMigration` |
| Loader server primitives | `platform/BucketOperations` plus each loader implementation |
| Held transfer, milk, and hand settlement | `interaction/HeldTransfers` |
| Dispensers and vanilla cauldrons | `interaction/Dispensers`, `interaction/Cauldrons`, registered by `SomeBuckets.registerBehaviors` |
| Authorization | `common/.../protection` |
| Client loader seam and fluid appearance | `client/ClientPlatform` |
| Item rendering | `items/*.json`; `client/FluidBucketModel`, `JunkContentsRenderer`, `MobEggColors.Tint`, registered by id from `ClientModelTypes` |
| Structure loot | `data/somebuckets/loot_table/inject/*.json`, `somebuckets/bucket_loot.json`, `BucketLootTables` |

## Loader integration

Each loader installs `BucketOperations` and registers shared behaviors after item registration. Forge uses EventBus 7 event-specific buses; deferred registries receive the mod `BusGroup`. Cancellable listeners return booleans, and block-break checks honor `Result.DENY`. The interface supplies native automation, sided block-fluid storage, placement, held fluid movement, fluid facts, inventory discovery, sounds, and permission/event hooks. `FluidTransactions` and `HeldTransfers` own sequencing, protection, and accounting. Loader item storage converts units around `FluidBucketItem`'s admission and extraction rules; the item owns finite versus infinite behavior. Milk remains a distinct mode and exchanges through the loader's milk fluid when available.

`StoredFluid` is the common value; its variant data is a `DataComponentPatch` persisted with the item's registry context. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and `FabricFluidVariants` convert only at loader boundaries; Forge's fluid tag travels as the patch's `custom_data`. World pickup, including aquatic Mob Bucket water, always goes through `FluidTransactions`. Vanilla-rule world placement (`FluidTransactions.emptyFluid`) serves Mob Bucket water and Fabric's arbitrary-fluid output, which debits through its transactional item storage; Forge and NeoForge place through their own fluid types.

`Dispensers` moves the loader's automation actor to the dispenser. Forge caches a plain `ServerPlayer` because it has no fake-player type. Real players supply statistics, criteria, feedback, and protection identity; automation has no real `player()`. `Protections` applies vanilla block-use gates and world borders, with loader break checks and optional Fabric Common Protection API checks. Forge/NeoForge `placeChecked` captures block snapshots, posts placement events, and restores refused changes; Fabric checks Common Protection API first. `FluidTransactions.placeFluidChecked` delays replaced-block drops until placement stands. Tanks and Source cauldrons run behind block interaction through `useOn`.

Each client installs `ClientPlatform` with fluid appearance, config directory, and loader name. It supplies fluid models, `/sb fluids` samples, and Big/Huge bar colors; dedicated servers keep the default bar color. Its reload listener preloads egg and Source-fluid colors. `FluidDiagnostics.commandTree` supplies the client `/sb` tree on every loader.

A sided block store is authoritative on refusal. NeoForge and Fabric exclude vanilla cauldrons from block-fluid lookup, so common `Cauldrons` owns them on every loader: Big and Huge through interaction maps, Source Buckets and dispensers through `take`/`place`. Without a store, a modded cauldron is an ordinary block.

## Persistent and network state

`BucketState` is the sole bucket-state reader/writer. `ModDataComponentTypes` defines persistent and stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and `junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize empty state, and maintain the derived `MAX_STACK_SIZE` and milk `CONSUMABLE` components.

Structural codecs bound finite amounts, whole-bucket milk, powder units, and mob snapshots; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item capacity, exclusivity, and nested-container rejection. Admission runs once, whenever a stack is decoded (`verifyComponentsAfterLoad`), without the loader item-inventory lookup: it first restores readable set-aside junk entries while the bucket has room, then removes malformed owned components rather than clamping them; setters enforce the same invariants on every write.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in full, as vanilla does for containers and entity buckets. `MBItem` saves and loads those NBT snapshots through registry-aware `TagValueOutput` and `TagValueInput`, with a scoped problem collector. A reported save error refuses capture before aquatic water pickup; a reported load error preserves the snapshot and destination.

`BucketStateMigration` wraps the persistent codecs of `fluid_content`, `captured_mobs`, and `junk_contents`, upgrading raw data before the current-format codec decodes it, so conversion happens wherever a stack is decoded, including inside other mods' storage. Those components carry a `schema` field (absent means the 1.21.1 release); `captured_mobs` and `junk_contents` also carry the Minecraft `data_version` of their embedded entity and item-stack data (absent means 1.21.1), which is run through vanilla's data fixer, entity snapshots with a temporary `id`. Encoding writes only current stamps. A released Forge fluid variant, a raw tag, becomes `minecraft:custom_data` (`BucketOperations.releasedFluidVariantIsRawTag`). Milk and powder components are bare integers and unversioned. Entries that do not decode to storable stacks move to `set_aside`; they are retried on every decode, keep the bucket non-empty, and appear only in the tooltip.

Inventory insertion and FIFO extraction mutate identically on client and server; menu authority corrects mispredictions.

## Configuration and presentation

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register `ModConfig.Type.SERVER`; the loader synchronizes it to clients and supports the documented per-world `serverconfig/somebuckets-server.toml` override behavior. Config load/reload events refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`. `FabricSBPolicyPayload` sends resolved ids and milk permission on join and after a reload that changed them; clients apply it on the client thread, reset on disconnect, and never read their local JSON as remote policy.

Recipes, tags, translations, sounds, models, and textures are shared. Each structure-loot roll is a data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge data providers generate checked-in add-table global modifiers under `src/generated/resources` (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`) from that manifest. Production and source archive tasks run native datagen automatically; production archives take fresh modifier resources directly, excluding datagen caches. Client `MobEggColors` reloads `assets/somebuckets/mob_egg_colors.json`, merged across resource packs, and consults it before the spawn egg's item-definition constant tints; egg colors exist only in client resources.

`FluidBucketModel` appends the baked model, foil state, and resolved fluid appearance to GUI model identity, and marks animated sprites and foil for redraw. `JunkContentsRenderer` bounds stored icons between its cover planes; vanilla's special item-model wrapper owns its render-state integration. Both keep reload-scoped caches.

## GameTests

Cross-loader scenarios and the shared structure fixture live under `common/src/gametest`. Fabric discovers wrappers through its GameTest annotation. Forge and NeoForge register test functions with the loader and load vanilla test instances from the shared and loader-specific test data packs. Each loader checks for missing shared scenarios. Shared coverage includes Happy Ghast persistence, harness and UUID restoration, rider refusal, and unreadable-snapshot preservation. `SharedGameTestSupport` owns registry-aware entity-snapshot and item-stack persistence helpers.

## Conventions the code currently follows

- Preview, authorize the exact target (`mayRemove`; `mayModify` plus `placeChecked` for placement), then mutate; debit after a placement stands.
- Run player intake into an empty bucket through `HeldTransfers.fillFromHand`, and every held transfer, including off-hand priority, through `HeldTransfers`: Some Buckets container to other first.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk and feed through the animal's own interaction; automation never feeds an untamed tamable.
- Emit one correctly positioned sound per success, none when the loader declares none; loader utility exclusions alone justify `notifyActor`.
- Award `ITEM_USED` by hand only where vanilla does not: item `use`, held transfers, and cauldron interaction-map handlers.
- Check live Mob eligibility at release; never capture a leashed mob or another's owned mob.
- Register `ClientModelTypes` before the first client resource load; render caches live in baked models or reload listeners.
- Build Forge spawn-egg ingredient matches lazily after other mods register items.
