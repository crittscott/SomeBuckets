<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Structure, state, and loader seams. Behavior: `orientation-player.md`.

## Project shape

Some Buckets is a Java 21 mod for Minecraft 1.21.4 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, client type registration and fluid facts, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid facts, config, loot injection, policy networking, and test discovery |

`common` is not a runtime mod; Forge and NeoForge are independent. Loader-owned classes carry `Forge`, `NeoForge`, or `Fabric` prefixes. Common Java imports no loader runtime API except cross-remapped `@Environment`. The common `somebuckets.accesswidener` opens `ItemEntity.target` so Junk intake can honor its reserved recipient (Forge and NeoForge handling: `orientation-build.md`). Item components hold all bucket state; there are no blocks, block entities, menus, or saved-world objects. Fabric's Source policy snapshot is the only custom gameplay payload.

## Where decisions live

Owners are in `common` unless a loader is named.

| Decision | Owner |
| --- | --- |
| Item, ingredient and sound ids; capacities; uncapturable tag | `BucketDefinitions` |
| Stack size, empty versus filled; milk drinkability | `BucketState.afterMutation` with `SomeBucketItem.EMPTY_STACK_SIZE`/`FILLED_STACK_SIZE` |
| Decode-time admission | `SomeBucketItem.verifyComponentsAfterLoad` (`BucketState.restoreSetAside`, `discardInvalidStructure`) |
| What a fluid bucket accepts and yields | each item's `acceptsFluid`/`acceptable`/`insert`/`extractable`/`extract`, `getUnitRemainder`; milk as a fluid: `FluidBucketItem.exposedFluid` |
| Source Bucket allowlist | `SBPolicy.allows`/`allowsMilk`/`refresh`/`replaceFromServer` |
| Source Bucket target kind | `FluidTransactions.classifySourceTarget` |
| World fluid take and place; checked fluid placement | `FluidTransactions.tryTake*`/`tryPlace*`, `takeWorldFluid`, `emptyFluid`; `placeFluidChecked` |
| Where placed fluid lands; evaporation | `FluidTransactions.resolveWorldTarget`, `canHoldPlacedFluid`; `evaporatesInUltraWarm` |
| Hand-to-hand moves; off-hand priority; filling from a stack of empties | `HeldTransfers.tryTransfer`, `tryOffHandPriority`; `fillFromHand` |
| Milking | `HeldTransfers.milkCow`, `FluidBucketItem.milkInto` |
| Actor may act; checked block placement | `Protections.mayModify`/`mayRemove`/`mayInteractWithEntity`; `BucketOperations.placeChecked` |
| Mob may be captured; capture and release | `MBItem.canCapture`/`mayCaptureAs`/`canAccept`; `capture`, `releaseOldest` |
| Junk/Trash storable items; intake; feeding | `JBItem.canStore`, `isIntakeCandidate`, `absorbItemEntities`, `intake` (`TBItem` overrides); `canFeed`, `feedAnimal`, `automationMayFeed` |
| Bucket state read/write; component codecs | `BucketState`; `ModDataComponentTypes` |
| Saved-format upgrades | `BucketStateMigration.fluidContent`/`capturedMobs`/`junkContents` |
| Lava fuel | `FluidBucketItem.isLavaFuel`; hooks: Fabric `FuelValuesMixin`, Forge `ForgeFuel` and item shells, NeoForge item shells |
| Loader fluid primitives | `BucketOperations` and loader implementations |
| Dispenser and cauldron behavior | `Dispensers.register`; `Cauldrons.register`/`take`/`place` |
| Structure-loot rewards | `BucketLootTables.rewardsFor` |
| Creative order and variants | `CreativeBucketCatalog.populate` |
| Client fluid look and bar color; egg tint | `ClientPlatform.look`/`barColor`; `MobEggColors.Tint`, `override`/`eggColors` |
| Item model types; `/sb` tree | `ClientModelTypes`; `FluidDiagnostics.commandTree` |
| Bootstrap and registration | `SomeBuckets.registerBehaviors`; `SomeBucketsForge`, `SomeBucketsNeoForge`, `SomeBucketsFabric`, loader `register` packages |

## Loader integration

Each loader installs `BucketOperations`, then calls `SomeBuckets.registerBehaviors` once items exist. Implementations provide only primitives whose model differs by loader (automation player, sided `BlockFluidStore`, `placeChecked`, `moveHeldFluid`, sounds, pickup and toss events). `FluidTransactions` and `HeldTransfers` own sequencing, protection, and accounting. Block-store transfers call `FluidBucketItem`'s `acceptable`/`insert` and `extractable`/`extract` rules directly, acting on what a store actually moved: a take credits what fits, any accepted fill debits one unit; loader item storages only convert units around them, so finite versus infinite output is an item rule. Milk stays in milk mode; where a loader milk fluid is enabled, these rules and `FluidBucketItem.exposedFluid` exchange milk as that fluid.

`StoredFluid` is the common value; its variant data is a `DataComponentPatch` persisted with the item's registry context. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and `FabricFluidVariants` convert only at loader boundaries; Forge's fluid tag travels as the patch's `custom_data`. World pickup, including aquatic Mob Bucket water, goes through `FluidTransactions`; vanilla-rule placement (`emptyFluid`) serves Mob Bucket water and Fabric's arbitrary-fluid output, which debits through its transactional item storage; Forge and NeoForge place through their own fluid types.

`Dispensers` moves the loader's automation player to the dispenser as the context actor. Forge's `ForgeAutomationPlayer` is a cached plain `ServerPlayer` (Forge 54 has no fake-player type). `player()` is the real user for statistics, criteria, feedback, and protection events; null for automation. `mayModify` applies vanilla block-use gates to players and the world border to automation; `mayInteractWithEntity` checks only the border; `mayRemove` adds the loader's player break check (Fabric also consults Common Protection API). World placement runs under `placeChecked`: for a real player, Forge and NeoForge capture its block snapshots (suspending capture armed around `useOn`), post the place event for the placed block, and restore on refusal; Fabric consults Common Protection API first. `FluidTransactions.placeFluidChecked` clears a replaced block without drops, dropping it once the placement stands. Tanks and Source Bucket cauldrons run from `useOn`, behind block interaction. Each client installs `ClientPlatform` with the loader's fluid facts, config directory, and name; it derives the fluid model layer, the `/sb fluids` sample, and the Big/Huge bar color (the default on a dedicated server). `ClientPlatform.ColorReloadListener` preloads egg and source-fluid bar colors on resource reload. The one `/sb` Brigadier tree is adapted to each client command source.

A sided block store is authoritative on refusal. NeoForge and Fabric exclude vanilla cauldrons from block-fluid lookup, so common `Cauldrons` owns them on every loader: Big and Huge through interaction maps, Source Buckets and dispensers through `take`/`place`. Without a store, a modded cauldron is an ordinary block.

## Persistent and network state

`BucketState` is the sole bucket-state reader/writer. `ModDataComponentTypes` defines persistent and stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and `junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize empty state, and maintain the derived `MAX_STACK_SIZE` and milk `CONSUMABLE` components.

Structural codecs bound finite amounts, whole-bucket milk, powder units, and mob snapshots; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item capacity, exclusivity, and nested-container rejection. Admission runs once, whenever a stack is decoded (`verifyComponentsAfterLoad`), without the loader item-inventory lookup: it first restores readable set-aside junk entries while the bucket has room, then removes malformed owned components rather than clamping them; setters enforce the same invariants on every write.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in full.

`BucketStateMigration` wraps the persistent codecs of `fluid_content`, `captured_mobs`, and `junk_contents`, upgrading raw data before the current-format codec decodes it, so conversion happens wherever a stack is decoded, including inside other mods' storage. Those components carry a `schema` field (absent means the 1.21.1 release); `captured_mobs` and `junk_contents` also carry the Minecraft `data_version` of their embedded entity and item-stack data (absent means 1.21.1), which is run through vanilla's data fixer, entity snapshots with a temporary `id`. Encoding writes only current stamps. A released Forge fluid variant, a raw tag, becomes `minecraft:custom_data` (`BucketOperations.releasedFluidVariantIsRawTag`). Milk and powder components are bare integers and unversioned. Entries that do not decode to storable stacks move to `set_aside`; they are retried on every decode, keep the bucket non-empty, and appear only in the tooltip.

Inventory insertion and FIFO extraction mutate identically on client and server; menu authority corrects mispredictions.

## Configuration and presentation

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register `ModConfig.Type.SERVER`, which the loader synchronizes to clients; config load/reload events refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`. `FabricSBPolicyPayload` sends resolved ids and milk permission on join and after a reload that changed them; clients apply it on the client thread and reset on disconnect.

Recipes, tags, translations, sounds, models, and textures are shared. Each structure-loot roll is a data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge data providers generate checked-in add-table global modifiers under `src/generated/resources` (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`) from that manifest. Client `MobEggColors` reloads `assets/somebuckets/mob_egg_colors.json`, merged across resource packs, and consults it before the spawn egg's item-definition constant tints; egg colors exist only in client resources.

## GameTests

Cross-loader scenarios and the shared structure fixture live under `common/src/gametest`; loader trees provide discovery wrappers and native-API cases, and each loader's `every_shared_scenario_has_a_loader_wrapper` fails on an unwrapped scenario.

## Conventions the code currently follows

- Route persisted state through `BucketState`; apply `SBPolicy` to every Source input and output.
- Preview, authorize the exact target, then mutate; debit after a placement stands.
- Held transfers try Some Buckets container to other first.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk and feed through the animal's own interaction; automation never feeds an untamed tamable.
- Emit one positioned sound per success, none when the loader declares none.
- Award `ITEM_USED` by hand only where vanilla does not: item `use`, held transfers, and cauldron interaction-map handlers.
- Check live Mob eligibility at release; never capture a leashed mob or another's owned mob.
- Register `ClientModelTypes` before the first client resource load; render caches live in baked models or reload listeners.
- Build Forge spawn-egg ingredient matches lazily after other mods register items.
- Log milestones and anomalies via `SomeBuckets.LOGGER`, never per interaction.
