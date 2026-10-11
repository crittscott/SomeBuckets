<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Build structure, ownership, persisted state, loader seams, and invariants the code currently maintains. See `orientation-player.md` for behavior and `build-env.md` for build inputs.

## Repository map

Some Buckets is a Java 17 mod for Minecraft 1.20.1 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, state, protection contracts, interaction helpers, client algorithms, shared resources and GameTest scenarios |
| `forge` | Forge registration, capabilities, events, cauldrons, dispensers, rendering, config, loot and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, rendering, config, loot injection and test discovery |

Architectury Loom transforms `common`; each loader bundles its transformed output with Shadow and remaps the production JAR. Vanilla-only mixins live in `common` under `mixin.common` with `somebuckets-common.mixins.json`, registered by both loaders; currently `TemptGoalMixin` lets held Junk and Trash Bucket contents tempt mobs through `JBItem.holdsStoredMatch`. Common is not a separate runtime mod. Common production Java has no loader runtime imports except Fabric Loader's cross-remapped client `@Environment` annotation. Shared resources live under `common/src/main/resources`; loader metadata and loader-specific rendering resources stay with their loader.

Six items and one creative tab; no blocks, block entities, menus, packets, commands or saved-world objects. All bucket state is item-stack NBT.

## Subsystem ownership

Paths below are relative to the root Java package in `common` unless a loader is named.

| Area | Primary owner |
| --- | --- |
| Item identities and capacities | `item/BucketDefinitions` |
| Big and Huge Bucket behavior | `item/BBItem` |
| Source Bucket behavior and allowlist | `item/SBItem`, `config/SBPolicy` |
| Junk and Trash Bucket behavior | `item/JBItem`, `TBItem` |
| Mob Bucket behavior | `item/MBItem` |
| Serialization and fluid values | `util/NBTUtil`, `StoredFluid` |
| Loader fluid operations | `platform/BucketOperations` and loader implementations |
| Held-transfer settlement and milk arithmetic | `interaction/HeldTransferSettlement`, `MilkTransfers` |
| Dispenser geometry and non-fluid automation | `interaction/DispenserTarget`, `NonFluidDispensers` |
| Powder-snow cauldron transitions | `interaction/PowderSnowCauldrons` |
| Authorization: vanilla gates and loader protection events | `protection/Protections`, loader `permits*` implementations |
| Furnace policy | `fuel/BucketFuel`, with loader hooks |
| Creative ordering and variants | `register/CreativeBucketCatalog` |
| Sound registry IDs | `register/ModSoundIds` |
| Structure loot | `data/somebuckets/loot_tables/inject/*.json`, `somebuckets/bucket_loot.json`, `loot/BucketLootTables` |
| Shared model and texture algorithms | `client` |
| Loader bootstrap and registration | `SomeBucketsForge`, `SomeBucketsFabric`, loader `register` packages |

Common item classes select gestures and own domain behavior. Loader code owns operations whose transaction, event, storage, placement or rendering models differ. Client presentation can predict state; persistent and world mutations are server-authoritative.

## Loader seams

`BucketOperations` is installed before interactions reach common items. It covers held-container transfers, sided block storage, world pickup and placement, powder snow, Source Bucket operations, and loader-native fluid names and colors. It classifies an assigned Source Bucket target as matching fluid, blocking fluid or no fluid before shared gesture dispatch. Present storage on the targeted face owns dispatch: refusal does not fall through to ordinary world-fluid handling.

Mob Bucket held exchange reads a `MobBucketItem`'s entity type and fluid through `mobBucketContents`: Forge widens `getFishType` with an access transformer, Fabric widens `MobBucketItem.type` and `BucketItem.content` with an access widener. The exchange itself, built on vanilla `Bucketable`, lives in `MBItem`. Mob Bucket aquatic capture and release use `takeAquaticSourceWater` and `placeAquaticSourceWater`. Pickup is loader-native. Release delegates to common `fluid/FluidPlacement`, which owns fixed vanilla-water placement for aquatic release and shared sound helpers. General Big, Huge and Source Bucket placement remains loader-owned.

`StoredFluid` carries the shared fluid value. Forge converts to and from `FluidStack`; Fabric converts to and from `FluidVariant` and Transfer API quantities. Variant NBT survives both conversions. Shared fluid quantities are millibuckets; Fabric converts at its API boundary.

`Protections` applies vanilla gates (world border; for players also spawn protection and `mayUseItemAt`), then on the server posts a loader protection event through `BucketOperations`: break (`mayRemove`), place before mutation (`mayPlace`), block use (`mayInteractWithBlock`), item pickup (`mayPickUp`), and, for automation only, entity interaction and block use at a release destination (a player's arrive through vanilla dispatch). A Forge player's powder-snow placement relies on Forge's `useOn` capture. Fabric posts Fabric API break and use callbacks, plus Architectury break, place and pickup events and Common Protection API checks when those mods are installed. Automation posts as the `AutomationPlayers` fake player at the dispenser, holding a copy of the bucket. There is no claim-mod-specific code.

| Concern | Forge | Fabric |
| --- | --- | --- |
| Item and block fluid storage | Fluid capabilities | Transfer API |
| World-fluid hooks | Forge events and fluid utilities | Callbacks and transfer transactions |
| Water and lava cauldrons | `interaction/Cauldrons` | Transfer API and `FabricCauldronInteractions` |
| Fluid dispensers | `interaction/Dispensers` | `FabricFluidDispensers` |
| Variable stack size | Item-shell override | `ItemStackMixin` |
| Furnace consumption | Fuel event adapter | `AbstractFurnaceBlockEntityMixin` |
| Dynamic rendering | Model loaders and BEWLR | Baked-model wrappers and builtin renderer |

Each finite lava unit burns 20,000 ticks and returns the bucket one unit lighter; an allowed lava Source Bucket returns unchanged as permanent fuel.

## Persistent state

`NBTUtil` owns the schema. Fluid-family and Mob Buckets have one mutually exclusive `Mode` payload; Junk and Trash Buckets use an independent item list.

| `Mode` | Payload |
| --- | --- |
| Absent or unrecognized | No mode-based content |
| `fluid` | `FluidStack` with `FluidName`, `Amount` and optional variant `Tag` |
| `milk` | Root `Amount` |
| `powder_snow` | Root `Powder` |
| `entity` | `EntityType` and FIFO `Entities` snapshots |

Junk and Trash contents use `JunkItems`; `JunkLayoutSeed` drives rendered layout and disappears with the last item. Mode and item storage are independent in the serializer.

Mutators canonicalize empty state: exhausted payloads lose marker and keys, empty item storage loses list and seed, and an empty root compound is removed. Unrelated root NBT is preserved. Entity snapshots are FIFO; the type header disappears with the final snapshot.

`VariableStackItem` defines the shared empty-versus-filled stack-size decision. Loader hooks apply it to `ItemStack#getMaxStackSize`; item classes decide whether their own state is empty.

## Data and client resources

Recipes, the `somebuckets:uncapturable` entity tag, translations, sounds, and most models and textures are shared. Custom ingredient serializers have matching IDs on both loaders and loader-specific bootstrap implementations.

Each structure-loot reward is a data-pack loot table `somebuckets:inject/<reward>` holding its item, chance and contents. Classpath `somebuckets/bucket_loot.json` maps rewards to target tables for `BucketLootTables`. Fabric adds a pool referencing each inject table, even when the target table was replaced. Forge's `ForgeBucketLootProvider` generates checked-in `somebuckets:add_table` modifiers (`AddTableLootModifier`) under `forge/src/generated/resources` during `:forge:runData`; manifest edits require regenerating them.

`CreativeBucketCatalog` is the ordered definition of creative contents and prefilled variants. Both loader registrations consume it. Common client code defines model predicates, texture masks, Junk layout and representative colors; loader client packages adapt those results to their rendering APIs. Rendering state derives from the same NBT used by item behavior.

## Configuration

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge's `ServerConfig` reads the world server config and refreshes policy on load and reload. Fabric's `FabricServerConfig` reads `config/somebuckets-server.json` at initialization and server start. Loader config code resolves IDs and installs policy; Source Bucket behavior consumes the resolved policy.

Fabric compiles against Architectury API and Common Protection API as optional, compile-only dependencies; neither is required at runtime.

## Development and GameTests

Cross-loader scenarios live under `common/src/gametest/java`; loader trees supply discovery wrappers and native-API cases. `refuseProtection` stands in for a claim mod via listeners each loader's `GameTestSupport` registers once; place and pickup refusals run only on Forge. The root `configureGameTests` helper creates the source sets, decodes `common/src/gametest/fixtures/empty_9x6x9.nbt.b64` into generated loader test resources, and registers `gametestJavadoc` including package-private shared scenarios.

Each test source set is a separate development mod. Forge discovery requires the matching `somebuckets_gametest` Loom entry, `mods.toml`, `@Mod` stub and `pack.mcmeta`. Forge tests reading production resources anchor `Class.getResourceAsStream` to a production class because the test mod is a separate JPMS module. Fabric's test run clears only `fabric/run/world` to prevent saved entities leaking between runs.

`generateDocs` assembles production and GameTest Javadoc into separate `docs/javadoc` sections, linked from `docs/index.html`. Forge's isolated `datagenLauncher` source set exits after the data launch chain returns to release Architectury runtime workers; it is excluded from production artifacts.

## Invariants currently maintained

- IDs, capacities, creative variants, fuel rules, sound IDs and loot policy have shared authorities; loaders adapt or register them.
- Loader fluid values are converted at boundaries. `BucketOperations` and `AutomationPlayers` are installed before common interactions run.
- Persisted mutations use `NBTUtil`, preserve variant and unrelated NBT, and canonicalize empty state.
- Source Bucket input and output paths apply `SBPolicy`. Assigned-fluid targeted use places; sneak-targeted use takes a matching collectible unit; sneak-air use clears after held transfer has priority. Dispensers take matching fluid in front or attempt placement there, including reactions with another fluid.
- Transfers preview before authorization and mutation and check the exact block or entity accessed. Present sided fluid storage remains authoritative after refusal.
- Held and machine transfers settle legal stacks through `HeldTransferSettlement`; milk arithmetic uses `MilkTransfers`. Fabric block and item storage changes share a transaction.
- Held foreign container stacks are processed one unit at a time until the stack or finite source is exhausted. An assigned Source Bucket is bounded only by the foreign stack's size.
- Successful placement sounds come from authoritative success paths that include the acting player without duplicate nearby broadcasts.
- Junk and Trash intake share the storage eligibility rule. Mob snapshots are removed only after successful world insertion.
- Server-safe common code avoids client initialization. Shared GameTests stay in common; discovery and native-API tests stay in loader modules.
