<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

## Project shape

The build targets Java 25 and Minecraft 26.1. Some Buckets uses package `com.github.crittscott.somebuckets` and mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, client type registration and fluid facts, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid facts, config, loot injection, policy networking, and test discovery |

Architectury Loom bundles transformed `common` into each loader JAR. The shared access widener opens `ItemEntity.target` for Junk intake, per-item cauldron registration, and the model and tint registries. NeoForge converts it to an access transformer, and Forge mirrors it with a build check. Item components hold all bucket state. Fabric's Source policy snapshot is the only custom gameplay payload.

## Where decisions live

Owners are in `common` unless a loader is named.

| Decision | Owner |
| --- | --- |
| Item ids, capacities, uncapturable tag | `BucketDefinitions` |
| Stack size, empty versus filled; derived components | `SomeBucketItem.EMPTY_STACK_SIZE`/`FILLED_STACK_SIZE`, written by `BucketState` after each mutation |
| What a fluid bucket accepts and yields | each `FluidBucketItem`'s `acceptsFluid`/`acceptable`/`insert`/`extractable`/`extract`/`getUnitRemainder` |
| Source Bucket allowlist | `SBPolicy.allows`/`allowsMilk`/`refresh` |
| Source Bucket target kind | `FluidTransactions.classifySourceTarget` |
| World fluid take and place; checked fluid placement | `FluidTransactions.tryTake*`/`tryPlace*`, `emptyFluid`; `placeFluidChecked` |
| Where placed fluid lands; evaporation | `FluidTransactions.resolveWorldTarget`, `canHoldPlacedFluid`; `emptyFluid`, `evaporate` |
| Hand-to-hand moves; filling from a stack of empties | `HeldTransfers.tryTransfer`; `fillFromHand` |
| Actor may act; checked block placement | `Protections.mayModify`/`mayRemove`/`mayInteractWithEntity`, `ProtectionContext.isAutomation`; `BucketOperations.placeChecked` |
| Mob may be captured; capture and release | `MBItem.canCapture`/`mayCaptureAs`/`canAccept`; `capture`, `releaseOldest` |
| Junk/Trash storable items; intake; feeding | `JBItem.canStore`, `isIntakeCandidate`, `absorbItemEntities`, `intake` (`TBItem` overrides); `canFeed`, `feedAnimal`, `automationMayFeed` |
| Bucket component read/write; codecs | `BucketState`; `ModDataComponentTypes` |
| Decode-time admission; saved-format upgrade | `SomeBucketItem.admit`; `BucketStateMigration` |
| Lava fuel; bucket returned after a burn starts | `FluidBucketItem.isLavaFuel`, burn time hooked by Fabric `FuelValuesMixin`, Forge `ForgeFuel`, NeoForge item shells; `FurnaceFuelMixin` via `getUnitRemainder` |
| Loader fluid primitives | `BucketOperations` and loader implementations |
| Dispenser and cauldron behavior | `Dispensers.register`, `Cauldrons.register`/`take`/`place` |
| Structure-loot rewards | `BucketLootTables.rewardsFor` |
| Creative order and variants | `CreativeBucketCatalog.populate` |
| Mod resource ids; shared behavior registration | `SomeBuckets.id`; `SomeBuckets.registerBehaviors` |
| Client fluid appearance; loader client adapter | `ClientPlatform` |
| Item model types; Mob Bucket tint | `ClientModelTypes`; `MobEggColors`, `MobBucketModel` |
| Bootstrap and registration | `SomeBucketsFabric`, `SomeBucketsForge`, `SomeBucketsNeoForge`, loader `register` packages |

## Loader integration

Each loader installs `BucketOperations` and registers shared behaviors after item registration. Forge uses EventBus 7 event-specific buses; deferred registries receive the mod `BusGroup`. Cancellable listeners return booleans, and block-break checks honor `Result.DENY`. Forge uses its fluid-handler capabilities; NeoForge uses `ResourceHandler<FluidResource>` with native `ItemAccess` and transactions; Fabric uses its Transfer API. Loader item storage converts units around `FluidBucketItem`'s admission and extraction rules; the item owns finite versus infinite behavior. Milk remains a distinct mode and exchanges through the loader's milk fluid when available.

`StoredFluid` is the common value; its variant data is a `DataComponentPatch` persisted with the item's registry context. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and `FabricFluidVariants` convert only at loader boundaries; Forge's fluid tag travels as the patch's `custom_data`. Vanilla-rule world placement (`FluidTransactions.emptyFluid`) serves Mob Bucket water and Fabric's arbitrary-fluid output, which debits through its transactional item storage. Forge and NeoForge place through their own fluid types. NeoForge previews abort native transactions; executed transfers commit. Item access preserves common code's in-place bucket references.

`Dispensers` moves the loader's automation actor to the dispenser. Forge caches a plain `ServerPlayer` because it has no fake-player type. Real players supply statistics, criteria, feedback, and protection identity. `Protections` applies vanilla block-use gates and world borders, with loader break checks and optional Fabric Common Protection API checks. Forge/NeoForge `placeChecked` captures block snapshots, posts placement events, and restores refused changes; Fabric checks Common Protection API first. `FluidTransactions.placeFluidChecked` delays replaced-block drops until placement stands. Tanks and Source cauldrons run behind block interaction through `useOn`.

Each loader installs `ClientPlatform` for the bucket fluid layer, Big/Huge bar colors, and `/sb fluids`. Fabric and NeoForge get still sprites from baked fluid models and use variant and stack tint respectively. Forge uses its client fluid-type extension for both. The reload listener prepares egg colors and averages source-fluid sprites once components bind. `FluidDiagnostics.commandTree` supplies the client commands.

A sided block store is authoritative on refusal. NeoForge and Fabric exclude vanilla cauldrons from block-fluid lookup. Common `Cauldrons` registers the Big and Huge Bucket items with the vanilla dispatchers and handles Source Buckets and dispensers through `take` and `place`. Without a store, a modded cauldron is an ordinary block.

## Persistent and network state

`ModDataComponentTypes` defines persistent and stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and `junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize empty state, and maintain the derived `MAX_STACK_SIZE` and milk `CONSUMABLE` components.

Structural codecs bound finite amounts, whole-bucket milk, powder units, and mob snapshots; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item capacity, exclusivity, and nested-container rejection. Admission runs through `SomeBucketItem.admit`, called by the shared `ItemStackMixin` after persistent or network codecs construct a stack from its component patch, without the loader item-inventory lookup: it first restores readable set-aside junk entries while the bucket has room, then removes malformed owned components rather than clamping them; setters enforce the same invariants on every write.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in full. `MBItem` saves and loads those NBT snapshots through registry-aware `TagValueOutput` and `TagValueInput`, with a scoped problem collector. A reported save error refuses capture before aquatic water pickup; a reported load error preserves the snapshot and destination.

`BucketStateMigration` wraps the persistent codecs of `fluid_content`, `captured_mobs`, and `junk_contents`, upgrading raw data before the current-format codec decodes it, so conversion happens wherever a stack is decoded. Those components carry a `schema` field (absent means the 1.21.1 release); `captured_mobs` and `junk_contents` also carry the Minecraft `data_version` of their embedded entity and item-stack data (absent means 1.21.1), which is run through vanilla's data fixer, entity snapshots with a temporary `id`. Encoding writes only current stamps. A released Forge fluid variant, a raw tag, becomes `minecraft:custom_data` (`BucketOperations.releasedFluidVariantIsRawTag`). Milk and powder components are bare integers and unversioned. Entries that do not decode to storable stacks move to `set_aside`; they are retried on every decode, keep the bucket non-empty, and appear only in the tooltip.

Inventory insertion and FIFO extraction mutate identically on client and server; menu authority corrects mispredictions.

## Configuration and presentation

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register `ModConfig.Type.SERVER`; the loader synchronizes it to clients and honors the per-world `serverconfig/somebuckets-server.toml` override. Config load/reload events refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`. `FabricSBPolicyPayload` sends resolved ids and milk permission on join and after a reload that changed them; clients apply it on the client thread, reset on disconnect, and never read their local JSON as remote policy.

Recipes, tags, translations, sounds, models, and textures are shared. Each structure-loot roll is a data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge data providers generate checked-in add-table global modifiers under `src/generated/resources` (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`) from that manifest. `MobEggColors` caches GUI egg snapshots reduced to two sampled Oklab colors by `TwoColorPalette`; resource-pack overrides win. `EggPaletteSampler` queues one asynchronous readback at a time, releasing idle resources and retiring old work on reload. `MobBucketModel` keys GUI models by palette and animates pending samples.

`FluidBucketModel` keys GUI models by model, foil, and fluid appearance; animated sprites and foil request redraw. Vessels use the item atlas, cutout fluids the block atlas. `JunkContentsRenderer` captures display context and resolves its cover sprite on first model update after atlas initialization, preserving animation flags. Covers and child models use deferred submission. Both cache until reload. Fabric color reloads follow models through Resource Loader API v1.

## GameTests

Shared scenarios and structures live under `common/src/gametest`. Fabric discovers annotated wrappers. Forge and NeoForge register test functions and load vanilla test instances from shared and loader-specific data packs. Each loader checks for missing shared scenarios. `SharedGameTestSupport` creates explicit Survival players and owns registry-aware snapshot and stack persistence helpers.

## Conventions the code currently follows

- Preview, authorize the exact target (`mayRemove`; `mayModify` plus `placeChecked` for placement), then mutate; debit after a placement stands.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk and feed through the animal's own interaction; automation never feeds an untamed tamable.
- Check live Mob eligibility at release; never capture a leashed mob or another's owned mob.
- Register `ClientModelTypes` before the first client resource load; render caches live in baked models or reload listeners.
