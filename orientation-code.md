<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

## Project shape

Java 21, Minecraft 1.21.1, package `com.github.crittscott.somebuckets`, mod id `somebuckets`. `common` holds loader-neutral gameplay, item state, config policy, client algorithms, resources, and GameTest scenarios. Fabric, Forge, and NeoForge own registries, events, native fluid interfaces, render adapters, config I/O, loot, and test discovery. Architectury transforms common for each loader; it is not a runtime mod. There are six items and no blocks, block entities, or menus.

## Code map

| Area | Owners |
| --- | --- |
| Identities and capacities | `item/BucketDefinitions`, `register/CreativeBucketCatalog` |
| Fluid gestures and transactions | `BBItem`, `SBItem`, `FluidBucketItem`, `fluid/BBFluidLogic`, `SBFluidLogic`, `FluidPlacement`, `WorldFluidPickup` |
| Item storage and mobs | `JBItem`, `TBItem`, `MBItem` |
| State and serialization | `util/BucketState`, `BucketStackState`, `StoredFluid`, `register/ModDataComponentTypes` |
| Saved-format conversion | `util/LegacyBucketMigration` |
| Loader fluid operations | `platform/BucketOperations` and each loader implementation |
| Held transfers | `interaction/HeldTransferSettlement`, `MilkTransfers`, loader held-transfer hooks |
| Automation | `DispenserTarget`, `BucketDispenseBehavior`, `NonFluidDispensers`, loader fluid dispensers |
| Cauldrons | `PowderSnowCauldrons` and loader-native fluid handling |
| Protection | `Protections`, `ProtectionContext`, `ProtectionAction`, `ClaimProtectionProvider`, `AutomationPlayers` |
| Rendering and diagnostics | `client/*`, `item/MobEggColors`, `diagnostic/*`, loader adapters |
| Loot | `loot/BucketLootTables`, loader `AddBucketLootModifier`, Fabric loot callbacks, native data providers |

## Loader integration

Loaders install `BucketOperations`, `AutomationPlayers`, and `DiagnosticsSupport`. Common transaction logic delegates native storage, fluid placement, sounds, held transfers, inventory detection, and loader event adaptation through these seams. `StoredFluid` carries identity and variant data; loader adapters convert at boundaries. Forge/NeoForge capabilities and Fabric Transfer API stay loader-native.

`Protections.mayAct` applies vanilla player world-border/spawn checks and, except for entity interaction, placement restrictions. It then consults registered claim providers, stopping on any denial. Fabric and NeoForge register `FtbChunksProtection` only when FTB Chunks is present; Forge has no matching adapter. The single adapter source under `common/src/compat/java` is compiled into Fabric/NeoForge main outputs only. Automation contexts include the dispenser source and use a loader automation player where native operations need one.

## Persistent and network state

`BucketState` reads and writes item components registered through `ModDataComponentTypes`: fluid, milk, powder snow, captured mobs, and junk contents. `CapturedMobs` stores an entity type plus full FIFO `CompoundTag` snapshots; its stream codec also carries the snapshots. Junk contents carry item stacks and render-layout state. Finite amounts, capacities, mutually exclusive contents, and variable stack size are managed by the item/state code. `LegacyBucketMigration` converts recognized pre-component bucket data; item inventory ticks invoke migration on the server.

## Configuration and presentation

`SBPolicy` is the resolved Source Bucket allowlist. Forge and NeoForge use server config with loader synchronization; Fabric owns a JSON server config and synchronizes policy through `FabricSBPolicyPayload`. Clients do not substitute local config for a remote server's policy. Loader clients adapt shared texture-color, Junk rendering, Mob egg-color, and diagnostic algorithms to their rendering APIs.

The shared `somebuckets/bucket_loot.json` supplies reward items, chance, powder-snow payload, and target tables. Fabric builds loot pools at runtime. Forge and NeoForge use native `GlobalLootModifierProvider` implementations to generate checked-in `somebuckets:add_bucket` resources under `src/generated/resources` automatically before archives. Index validation compares sorted entry lists, retaining duplicate detection. This version awards items directly, rather than rolling inject tables.

## GameTests

Shared scenarios are under `common/src/gametest/java`; loader wrappers handle discovery and native APIs. `configureGameTests` creates the shared source set and GameTest Javadoc, and decodes the Base64 empty structure fixture into each loader's singular `structure/` data path. Fabric also adds common's main output to its GameTest classpath and clears its development GameTest world before launch. Data runs prepare test-support outputs because Loom advertises that dev mod.

## Conventions the code currently follows

- Shared code owns capacities, state, policy, transaction sequencing, loot policy, and diagnostic algorithms; loaders own their native interfaces and registries.
- Claim providers are optional and deny by unanimous consent; loader bootstrap alone decides which providers to register.
- Held settlement and milk arithmetic use the shared helpers; world pickup uses its common helper.
- Item components carry bucket state across inventory moves and saves; loader registration uses shared component definitions.
- Reports go to command feedback and report files; logs record lifecycle and resolution milestones.
