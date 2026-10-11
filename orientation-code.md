<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Build structure, ownership, persisted state, loader seams, and invariants the code currently maintains. `orientation-player.md` covers observable behavior; `build-env.md` describes build inputs and tasks.

## Repository map

Some Buckets is a Java 17 mod for Minecraft 1.20.1 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, state, protection contracts, interaction helpers, client algorithms, shared resources and GameTest scenarios |
| `forge` | Forge registration, capabilities, events, cauldrons, dispensers, rendering, config, loot and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, rendering, config, loot injection and test discovery |

Architectury Loom transforms `common`; each loader bundles its transformed output with Shadow and remaps the production JAR. Vanilla-only mixins live in `common` under `mixin.common` with `somebuckets-common.mixins.json`, registered by both loaders; currently `TemptGoalMixin` lets held Junk and Trash Bucket contents tempt mobs through `JBItem.holdsStoredMatch`. Common is not a separate runtime mod. Common production Java has no loader runtime imports except Fabric Loader's cross-remapped client `@Environment` annotation. Shared resources live under `common/src/main/resources`; loader metadata and loader-specific rendering resources stay with their loader. The optional FTB Chunks adapter under `common/src/compat/java` is compiled into both loader source sets.

The mod registers six items and one creative tab. There are no blocks, block entities, menus, packets, commands or saved-world objects; all bucket state is item-stack NBT.

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
| Authorization and claim composition | `protection` |
| Furnace policy | `fuel/BucketFuel`, with loader hooks |
| Creative ordering and variants | `register/CreativeBucketCatalog` |
| Sound registry IDs | `register/ModSoundIds` |
| Structure-loot policy | `somebuckets/bucket_loot.json`, `loot/BucketLootTables` |
| Shared model and texture algorithms | `client` |
| Loader bootstrap and registration | `SomeBucketsForge`, `SomeBucketsFabric`, loader `register` packages |

Common item classes select gestures and own domain behavior. Loader code owns operations whose transaction, event, storage, placement or rendering models differ. Client presentation can predict state; persistent and world mutations are server-authoritative.

## Loader seams

`BucketOperations` is installed before interactions reach common items. It covers held-container transfers, sided block storage, world pickup and placement, powder snow, Source Bucket operations, and loader-native fluid names and colors. It classifies an assigned Source Bucket target as matching fluid, blocking fluid or no fluid before shared gesture dispatch. Present storage on the targeted face owns dispatch: refusal does not fall through to ordinary world-fluid handling.

Mob Bucket held exchange reads a `MobBucketItem`'s entity type and fluid through `mobBucketContents`: Forge widens `getFishType` with an access transformer, Fabric widens `MobBucketItem.type` and `BucketItem.content` with an access widener. The exchange itself, built on vanilla `Bucketable`, lives in `MBItem`. Mob Bucket aquatic capture and release use `takeAquaticSourceWater` and `placeAquaticSourceWater`. Pickup is loader-native. Release delegates to common `fluid/FluidPlacement`, which owns fixed vanilla-water placement for aquatic release and shared sound helpers. General Big, Huge and Source Bucket placement remains loader-owned.

`StoredFluid` carries the shared fluid value. Forge converts to and from `FluidStack`; Fabric converts to and from `FluidVariant` and Transfer API quantities. Variant NBT survives both conversions. Shared fluid quantities are millibuckets; Fabric converts at its API boundary.

`AutomationPlayers` supplies the stable loader-native fake player for dispenser claim checks. `Protections` combines vanilla restrictions with registered `ClaimProtectionProvider`s; `ClaimProtections` requires every provider to allow the action. The FTB adapter is installed only when FTB Chunks is present.

| Concern | Forge | Fabric |
| --- | --- | --- |
| Item and block fluid storage | Fluid capabilities | Transfer API |
| World-fluid hooks | Forge events and fluid utilities | Callbacks and transfer transactions |
| Water and lava cauldrons | `interaction/Cauldrons` | Transfer API and `FabricCauldronInteractions` |
| Fluid dispensers | `interaction/Dispensers` | `FabricFluidDispensers` |
| Variable stack size | Item-shell override | `ItemStackMixin` |
| Furnace consumption | Fuel event adapter | `AbstractFurnaceBlockEntityMixin` |
| Dynamic rendering | Model loaders and BEWLR | Baked-model wrappers and builtin renderer |

Each finite lava unit supplies one 20,000-tick furnace burn and leaves the same bucket with one fewer unit. The remainder can burn again until empty. An allowed lava Source Bucket returns unchanged and supplies permanent fuel.

## Persistent state

`NBTUtil` owns the schema. Fluid-family and Mob Buckets have one mutually exclusive `Mode` payload; Junk and Trash Buckets use an independent item list.

| `Mode` | Payload |
| --- | --- |
| Absent or unrecognized | No mode-based content |
| `fluid` | `FluidStack` with `FluidName`, `Amount` and optional variant `Tag` |
| `milk` | Root `Amount` |
| `powder_snow` | Root `Powder` |
| `entity` | `EntityType` and FIFO `Entities` snapshots |

Junk and Trash contents use `JunkItems`. `JunkLayoutSeed` belongs to rendered Junk layout and disappears with the last item. Mode and item storage are independent in the serializer, although each item family uses only its relevant portion.

Mutators canonicalize empty state: exhausted mode payloads lose their marker and payload keys, empty item storage loses its list and seed, and an empty root compound is removed. Unrelated root NBT is preserved. Entity snapshots are FIFO; the type header disappears with the final snapshot.

`VariableStackItem` defines the shared empty-versus-filled stack-size decision. Loader hooks apply it to `ItemStack#getMaxStackSize`; item classes decide whether their own state is empty.

## Data and client resources

Recipes, the Mob Bucket blacklist tag, translations, sounds, and most models and textures are shared. Custom ingredient serializers have matching IDs on both loaders and loader-specific bootstrap implementations.

`somebuckets/bucket_loot.json` is the shared structure-loot policy. `BucketLootTables` reads it at runtime. Fabric creates loot pools from it; Forge's `ForgeBucketLootProvider` generates checked-in global modifiers under `forge/src/generated/resources` during `:forge:runData`. Manifest edits require regenerating those resources. Forge modifiers use `somebuckets:add_bucket`, preserving each reward's item, chance, target tables and powder-snow contents.

`CreativeBucketCatalog` is the ordered definition of creative contents and prefilled variants. Both loader registrations consume it. Common client code defines model predicates, texture masks, Junk layout and representative colors; loader client packages adapt those results to their rendering APIs. Rendering state derives from the same NBT used by item behavior.

## Configuration

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge's `ServerConfig` reads the world server config and refreshes policy on load and reload. Fabric's `FabricServerConfig` reads `config/somebuckets-server.json` at initialization and server start. Loader config code resolves IDs and installs policy; Source Bucket behavior consumes the resolved policy.

FTB Chunks is optional and compile-only on both loaders. Its API is confined to `common/src/compat/java`; ordinary common behavior reaches it through the protection-provider registry.

## Development and GameTests

Cross-loader scenarios and assertions live under `common/src/gametest/java`; loader trees supply discovery wrappers and native-API cases. The root `configureGameTests` helper creates the source sets, decodes `common/src/gametest/fixtures/empty_9x6x9.nbt.b64` into generated loader test resources, and registers `gametestJavadoc` including package-private shared scenarios.

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
