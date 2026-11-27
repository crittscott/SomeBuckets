<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Behavior: `orientation-player.md`.

## Project shape

Some Buckets is a Java 21 mod for Minecraft 1.21.8 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, client type registration and fluid facts, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid facts, config, loot injection, policy networking, and test discovery |

`common` is not a runtime mod; Forge and NeoForge are independent. Loader-owned classes use `Forge`, `NeoForge`, or `Fabric` prefixes. Common imports no loader runtime API except cross-remapped `@Environment`. The common `somebuckets.accesswidener` opens `ItemEntity.target` (Junk intake honors a reserved recipient) and `MobBucketItem.playEmptySound` (a released mob plays its own bucket's sound); Forge and NeoForge handling: `orientation-build.md`. Item components hold all bucket state; no blocks, block entities, menus, or saved-world objects. Fabric's Source policy snapshot is the only custom gameplay payload.

## Where decisions live

Owners are in `common` unless a loader is named.

| Decision | Owner |
| --- | --- |
| Item, ingredient and sound ids; capacities; uncapturable tag | `BucketDefinitions` |
| Stack size, empty versus filled; milk drinkability | `BucketState.afterMutation` with `SomeBucketItem.EMPTY_STACK_SIZE`/`FILLED_STACK_SIZE` |
| Decode-time admission | `SomeBucketItem.verifyComponentsAfterLoad` (`BucketState.restoreSetAside`, `discardInvalidStructure`) |
| What a fluid bucket accepts and yields | each item's `acceptsFluid`/`acceptable`/`insert`/`extractable`/`extract`, `getUnitRemainder`; milk as a fluid: `FluidBucketItem.exposedFluid` |
| Source Bucket allowlist | `SBPolicy.allows`/`allowsMilk`/`isValidContentId`/`refresh`/`replaceFromServer` |
| Source Bucket target kind | `FluidTransactions.classifySourceTarget` |
| World fluid take and place; checked fluid placement | `FluidTransactions.tryTake*`/`tryPlace*`, `takeWorldFluid`, `emptyFluid`; `placeFluidChecked` |
| Where placed fluid lands; evaporation | `FluidTransactions.resolveWorldTarget`, `canHoldPlacedFluid`; `evaporatesInUltraWarm` |
| Hand-to-hand moves; off-hand priority; filling from a stack of empties | `HeldTransfers.tryTransfer`, `tryOffHandPriority`; `fillFromHand` |
| Milking; milk drinking | `HeldTransfers.milkCow`, `FluidBucketItem.interactLivingEntity`; `FluidBucketItem.finishUsingItem` |
| Milk room and admission; powder admission | `FluidBucketItem.milkRoomMb`/`canMilkInto`/`addMilk`; `BBItem.canTakePowderUnit`/`addPowderUnit` |
| Actor may act; checked block placement | `Protections.mayModify`/`mayRemove`/`mayInteractWithEntity`; `BucketOperations.placeChecked` |
| Mob may be captured; capture before mob's reaction; release | `MBItem.canCapture`/`mayCaptureAs`/`canAccept`; `preemptInteraction`; `capture` (snapshot cap `MAX_SNAPSHOT_BYTES`), `releaseOldest` |
| Junk/Trash storable items; intake; feeding, tempting | `JBItem.canStore`, `isIntakeCandidate`, `absorbItemEntities`, `intake` (`TBItem` overrides); `canFeed`, `feedAnimal`, `automationMayFeed`; `holdsStoredMatch` (common Mixins) |
| Bucket state read/write; component codecs | `BucketState`; `ModDataComponentTypes` |
| Saved-format upgrades | `BucketStateMigration.fluidContent`/`capturedMobs`/`junkContents` |
| Lava fuel | `FluidBucketItem.isLavaFuel`; hooks: Fabric `FuelValuesMixin`, Forge `ForgeFuel` and item shells, NeoForge item shells |
| Loader fluid primitives | `BucketOperations` and loader implementations |
| Dispenser and cauldron behavior | `Dispensers.register`; `Cauldrons.register`/`take`/`place` |
| Structure-loot rewards | `BucketLootTables.rewardsFor` |
| Creative order and variants | `CreativeBucketCatalog.populate` |
| Client fluid look and bar color | `ClientPlatform.look`/`barColor` |
| Mob Bucket tint; egg palette sampling and reduction | `MobEggColors.Tint`, `colors`; `EggPaletteSampler`, `TwoColorPalette.extract` |
| Item model types; `/sb` tree | `ClientModelTypes`; `ClientDiagnostics.commandTree` |
| Bootstrap and registration | `SomeBuckets.registerBehaviors`/`logStarted`; `SomeBucketsForge`, `SomeBucketsNeoForge`, `SomeBucketsFabric`, loader `register` packages |

## Loader integration

Each loader installs `BucketOperations` and registers shared behaviors after item registration. Forge uses EventBus 7 event-specific buses; deferred registries receive the mod `BusGroup`. Cancellable listeners return booleans, and block-break checks honor `Result.DENY`. It supplies only loader-specific primitives. `FluidTransactions` and `HeldTransfers` own sequencing, protection, and accounting. Milk remains a distinct mode and exchanges through the loader's milk fluid when available.

`StoredFluid` is the common value; its variant data is a registry-persisted `DataComponentPatch`. Loader converters (`ForgeFluidStacks`, `NeoForgeFluidStacks`, `FabricFluidVariants`) run only at boundaries; Forge's fluid tag travels as the patch's `custom_data`. World pickup, including aquatic Mob Bucket water, goes through `FluidTransactions`; vanilla-rule placement (`emptyFluid`) serves Mob Bucket water and Fabric's arbitrary-fluid output, which debits through its transactional item storage; Forge and NeoForge place through their own fluid types.

`Dispensers` moves the loader's automation actor to the dispenser; Forge caches a plain `ServerPlayer`. Real players supply statistics, criteria, feedback, and protection identity; automation has none. Forge/NeoForge `placeChecked` captures snapshots, posts place events, and restores refusals; Fabric checks Common Protection API first. `FluidTransactions.placeFluidChecked` delays replaced-block drops until placement stands. Tanks and Source cauldrons run from `useOn` inside `asBlockInteraction`, which on Forge and NeoForge suspends `useOn`'s snapshot capture: block use, not building.

Each client installs `ClientPlatform` with fluid appearance, config directory, and loader name; the fluid layer retextures the generated `item/fluid_mask` model's quads. `ClientDiagnostics` builds the `/sb` reports. The reload listener resets egg palettes and preloads fluid colors.

A sided block store is authoritative on refusal. NeoForge and Fabric exclude vanilla cauldrons from block-fluid lookup, so common `Cauldrons` owns them on every loader: Big and Huge through interaction maps, Source Buckets and dispensers through `take`/`place`. Without a store, a modded cauldron is an ordinary block.

## Persistent and network state

`BucketState` is the sole bucket-state reader/writer. `ModDataComponentTypes` defines persistent and stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and `junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize empty state, and maintain derived `MAX_STACK_SIZE` and milk `CONSUMABLE`.

Structural codecs bound finite amounts, whole-bucket milk, powder units, and mob snapshots; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item capacity, exclusivity, and nested-container rejection. Admission runs once, whenever a stack is decoded (`verifyComponentsAfterLoad`), without the loader item-inventory lookup: it first restores readable set-aside junk entries while the bucket has room, then removes malformed owned components rather than clamping them; setters trust their callers' capacity and content checks.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in full. `MBItem` saves and loads those NBT snapshots through registry-aware `TagValueOutput` and `TagValueInput`, with a scoped problem collector. A reported save error refuses capture before aquatic water pickup; a reported load error preserves the snapshot and destination.

`BucketStateMigration` wraps the persistent codecs of `fluid_content`, `captured_mobs`, and `junk_contents`, upgrading raw data wherever a stack is decoded, including in other mods' storage. Those components carry a `schema` field, and `captured_mobs`/`junk_contents` the Minecraft `data_version` of their embedded data (both absent in the 1.21.1 release), run through vanilla's data fixer. Encoding writes only current stamps. A released Forge fluid variant, a raw tag, becomes `minecraft:custom_data` (`BucketOperations.releasedFluidVariantIsRawTag`). Milk and powder components are bare integers and unversioned. Entries that do not decode to storable stacks move to `set_aside`; they are retried on every decode, keep the bucket non-empty, and appear only in the tooltip.

## Configuration and presentation

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register `ModConfig.Type.SERVER`, which the loader synchronizes to clients; config load/reload events refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`. `FabricSBPolicyPayload` sends resolved ids and milk permission on join and after a reload that changed them; clients apply it on the client thread and reset on disconnect.

Resources are shared. Each structure-loot roll is a data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge data providers generate checked-in add-table global modifiers under `src/generated/resources` (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`) from that manifest. `MobEggColors` caches GUI egg snapshots reduced to two sampled Oklab colors by `TwoColorPalette`; resource-pack overrides win. `EggPaletteSampler` renders the egg's GUI item state into a `TextureTarget` on the render thread, reads it back with `copyTextureToBuffer`, and restores the render state it changed; `RenderSystem.queueFencedTask` and the client's `schedule` drive one readback at a time without loader events or Mixins. Idle samplers release their resources and reload retires old work. `MobBucketModel`'s identity includes the current palette; pending state requests redraw.

`FluidBucketModel` appends the baked model, foil state, and resolved fluid appearance to GUI model identity, and marks animated sprites and foil for redraw. `JunkContentsRenderer` bounds stored icons between its cover planes; vanilla's special item-model wrapper owns its render-state integration. Both keep reload-scoped caches.

## GameTests

Cross-loader scenarios and the shared structure fixture live under `common/src/gametest`. Fabric discovers wrappers through its GameTest annotation. Forge and NeoForge register test functions with the loader and load vanilla test instances from the shared and loader-specific test data packs. `SharedGameTestSupport` owns registry-aware entity-snapshot and item-stack persistence helpers.

## Conventions the code currently follows

- Preview, authorize the exact target, then mutate; debit after a placement stands.
- Held transfers try Some Buckets container to other first.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk and feed through the animal's own interaction; automation never feeds an untamed tamable.
- Award `ITEM_USED` by hand only where vanilla does not: item `use`, held transfers, and cauldron interaction-map handlers.
- Check live Mob eligibility at release; never capture a leashed mob or another's owned mob.
- Register `ClientModelTypes` before the first client resource load; render caches live in baked models or reload listeners.
- Build Forge spawn-egg ingredient matches lazily after other mods register items.
