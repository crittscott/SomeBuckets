<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

## Project shape

Java 21, Minecraft 1.21.1, package `com.github.crittscott.somebuckets`, mod id `somebuckets`. `common` holds loader-neutral gameplay, item state, config policy, client algorithms, resources, and GameTest scenarios. Fabric, Forge, and NeoForge own registries, events, native fluid interfaces, render adapters, config I/O, loot, and test discovery. Architectury transforms common for each loader; it is not a runtime mod. There are six items and no blocks, block entities, or menus.

## Where decisions live

Owners are in `common` unless a loader is named.

| Decision | Owner |
| --- | --- |
| Item ids, capacities | `BucketDefinitions`; `BBItem.getCapacityUnits`, `JBItem.getCapacity` |
| Stack size, empty versus filled | `VariableStackItem` sizes, applied by `BucketState.afterMutation`; each item's `isEmpty` |
| Big Bucket accepts a fluid unit | `BBItem.canAcceptFluidUnit` |
| Big Bucket world take and place | `BBFluidLogic.tryTake`/`tryPlace`, `tryTakePowder`/`tryPlacePowder`, `resolvePlaceTarget` |
| Source Bucket allowlist | `SBPolicy.allows`/`allowsMilk`/`refresh` |
| Source Bucket target kind; take and place | `SBFluidLogic.classifyTarget`; `tryTake`/`tryPlace`, `tryMilkDispenser` |
| World fluid pickup | `WorldFluidPickup.sourceAt`/`take`/`takeBlock` |
| Where placed fluid lands; checked placement; evaporation | `FluidPlacement.resolveTarget`; `placeFluidChecked`, `emptyContents`; `evaporatesInUltraWarm`, `evaporate` |
| Bucket sounds | `FluidPlacement.resolveBucketSound`; `BucketOperations.fillSound`/`emptySound` |
| Hand-to-hand moves; sneak clear | `FluidBucketItem.tryCrossHandTransfer` → `BucketOperations.tryHeldTransfer` → `HeldTransferSettlement.settle`; `FluidBucketItem.tryShiftClear` |
| Milk moves and cow milking | `MilkTransfers.pourMilk`/`takeMilk`/`milkCow` |
| Actor may act; claim providers | `Protections.mayAct`/`mayRemove`/`mayPickUp`, `register`; `ClaimProtectionProvider.mayAct` |
| Automation actor | `ProtectionContext.dispenser`, `AutomationPlayers.get` |
| Mob may be captured; capture and release | `MBItem.canCapture`/`canAccept`; `capture`, `releaseOldest` |
| Junk/Trash storable items; intake; feeding | `JBItem.canStore`, `isIntakeCandidate`, `absorbItemEntities` (`TBItem` override); `canFeed`, `feedAnimal` |
| Bucket component read/write; finite drain | `BucketState`; `drainFiniteContent`, `clearBucket` |
| Saved pre-component data | `LegacyBucketMigration.migrate` |
| Component definitions and codecs | `ModDataComponentTypes` |
| Lava fuel | `BucketFuel.isLavaFuel` |
| Loader fluid primitives | `BucketOperations` and `FabricBucketOperations`, `ForgeBucketOperations`, `NeoForgeBucketOperations` |
| Dispenser behavior | `BucketDispenseBehavior.execute`, `DispenserTarget.from`, `NonFluidDispensers.register`; loader `Dispensers`/`FabricFluidDispensers` |
| Cauldron behavior | `PowderSnowCauldrons.take`/`place`; `BucketOperations.cauldronTake`/`cauldronPlace`; loader `Cauldrons`/`FabricCauldronInteractions` |
| Structure-loot rewards | `BucketLootTables.rewardsFor`; Forge/NeoForge `AddBucketLootModifier`, Fabric `FabricBucketLoot` |
| Creative order and variants; sound ids | `CreativeBucketCatalog.populate`; `ModSoundIds` |
| Mob Bucket tint | `MobEggColors.resolve` |
| Client rendering algorithms | `client` package (`ClientTextureColors`, `JunkBucketRenderData`, `JunkBucketIcons`) |
| Diagnostics | `diagnostic` package, `DiagnosticsSupport` |
| Bootstrap and registration | `SomeBucketsFabric`, `SomeBucketsForge`, `SomeBucketsNeoForge`, loader `register` packages |

## Loader integration

Loaders install `BucketOperations`, `AutomationPlayers`, and `DiagnosticsSupport`. Common transaction logic delegates native storage, fluid placement, sounds, held transfers, inventory detection, and loader event adaptation through these seams. `StoredFluid` carries identity and variant data; loader adapters convert at boundaries. Forge/NeoForge capabilities and Fabric Transfer API stay loader-native.

`Protections.mayAct` applies the world border to all actors and adds spawn protection and applicable placement restrictions for players. It then consults registered claim providers, stopping on any denial. Direct player removal also calls the loader break hook. Forge and NeoForge add native player pickup and placement checks. Placement uses block snapshots for rollback and drains finite buckets only after approval. Fabric and NeoForge register `FtbChunksProtection` only when FTB Chunks is present; Forge has no matching adapter. The single adapter source under `common/src/compat/java` is compiled into Fabric/NeoForge main outputs only. Automation contexts include the dispenser source and use a loader automation player where native operations need one.

## Persistent and network state

`BucketState` reads and writes item components registered through `ModDataComponentTypes`: fluid, milk, powder snow, captured mobs, and junk contents. `CapturedMobs` stores an entity type plus full FIFO `CompoundTag` snapshots; its stream codec also carries the snapshots. Junk contents carry item stacks and render-layout state. Finite amounts, capacities, mutually exclusive contents, and variable stack size are managed by the item/state code. `LegacyBucketMigration` converts recognized pre-component bucket data; item inventory ticks invoke migration on the server.

## Configuration and presentation

`SBPolicy` is the resolved Source Bucket allowlist. Forge and NeoForge use server config with loader synchronization. Fabric's `FabricServerConfig` reads a JSON server config at server start and data-pack reload; Fabric sends no policy to clients, so a remote Fabric client keeps the default allowlist. Loader clients adapt shared texture-color, Junk rendering, Mob egg-color, and diagnostic algorithms to their rendering APIs.

The shared `somebuckets/bucket_loot.json` supplies reward items, chance, powder-snow payload, and target tables. Fabric builds loot pools at runtime. Forge and NeoForge use native `GlobalLootModifierProvider` implementations to generate checked-in `somebuckets:add_bucket` resources under `src/generated/resources` automatically before archives. Index validation compares sorted entry lists, retaining duplicate detection. This version awards items directly, rather than rolling inject tables.

## GameTests

Shared scenarios are under `common/src/gametest/java`; loader wrappers handle discovery and native APIs. `configureGameTests` creates the shared source set and GameTest Javadoc, and decodes the Base64 empty structure fixture into each loader's singular `structure/` data path. Fabric also adds common's main output to its GameTest classpath and clears its development GameTest world before launch. Data runs prepare test-support outputs because Loom advertises that dev mod.

## Conventions the code currently follows

- Shared code owns capacities, state, policy, transaction sequencing, loot policy, and diagnostic algorithms; loaders own their native interfaces and registries.
- Claim providers are optional and deny by unanimous consent; loader bootstrap alone decides which providers to register.
- Held settlement and milk arithmetic use the shared helpers; world pickup uses its common helper.
- Item components carry bucket state across inventory moves and saves; loader registration uses shared component definitions.
- Reports go to command feedback and report files; logs record lifecycle and resolution milestones.
