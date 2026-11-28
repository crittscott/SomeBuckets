<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

## Project shape

Some Buckets is a Java 21 mod for Minecraft 1.21.11 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, client type registration and fluid facts, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid facts, config, loot injection, policy networking, and test discovery |

Architectury Loom transforms `common` into each loader jar; `common` is not a runtime mod, and Forge and NeoForge are independent. Loader-owned classes carry `Forge`, `NeoForge`, or `Fabric` prefixes. The common `somebuckets.accesswidener` opens `ItemEntity.target` so Junk intake can honor its reserved recipient, and `MobBucketItem.playEmptySound` so a released mob plays its own bucket's sound; NeoForge converts it; Forge mirrors it in its access transformer, checked during `check`. Item components hold all bucket state; no blocks, block entities, menus, or saved-world objects. Fabric's Source policy snapshot is the only custom gameplay payload.

## Where decisions live

Owners are in `common` unless a loader is named.

| Decision | Owner |
| --- | --- |
| Item, ingredient and sound ids; capacities; uncapturable tag | `BucketDefinitions` |
| Stack size, empty versus filled; derived components | `SomeBucketItem.EMPTY_STACK_SIZE`/`FILLED_STACK_SIZE`, written by `BucketState` |
| What a fluid bucket accepts and yields | each `FluidBucketItem`'s `acceptsFluid`/`acceptable`/`insert`/`extractable`/`extract`/`getUnitRemainder`; milk as a fluid: `exposedFluid` |
| Source Bucket allowlist | `SBPolicy.allows`/`allowsMilk`/`isValidContentId`/`refresh`/`replaceFromServer` |
| Source Bucket target kind | `FluidTransactions.classifySourceTarget` |
| World fluid take and place; checked fluid placement | `FluidTransactions.tryTake*`/`tryPlace*`, `takeWorldFluid`, `emptyFluid`; `placeFluidChecked` |
| Where placed fluid lands; evaporation | `FluidTransactions.resolveWorldTarget`, `canHoldPlacedFluid`; `emptyFluid`, `evaporate` |
| Hand-to-hand moves; off-hand priority; filling from a stack of empties | `HeldTransfers.tryTransfer`, `tryOffHandPriority`; `fillFromHand` |
| Milking; drinking | `HeldTransfers.milkCow`; `FluidBucketItem.finishUsingItem` |
| Milk and powder admission | `FluidBucketItem.canMilkInto`/`addMilk`; `BBItem.canTakePowderUnit`/`addPowderUnit` |
| Actor may act; checked block placement | `Protections.mayModify`/`mayRemove`/`mayInteractWithEntity`, `ProtectionContext.isAutomation`; `BucketOperations.placeChecked` |
| Mob may be captured; capture before mob's reaction; release | `MBItem.canCapture`/`mayCaptureAs`/`canAccept`; `preemptInteraction`; `capture`, `releaseOldest` |
| Junk/Trash storable items; intake; feeding, tempting | `JBItem.canStore`, `isIntakeCandidate`, `absorbItemEntities`, `intake` (`TBItem` overrides); `canFeed`, `feedAnimal`, `automationMayFeed`; `holdsStoredMatch` (common Mixins) |
| Bucket component read/write; codecs | `BucketState`; `ModDataComponentTypes` |
| Decode-time admission; saved-format upgrade | `SomeBucketItem.admit`; `BucketStateMigration` |
| Lava fuel | `FluidBucketItem.isLavaFuel`; Fabric `FuelValuesMixin`, Forge `ForgeFuel`, item shells |
| Loader fluid primitives | `BucketOperations` and loader implementations |
| Dispenser and cauldron behavior | `Dispensers.register`, `Cauldrons.register`/`take`/`place` |
| Structure-loot rewards | `BucketLootTables.rewardsFor` |
| Creative order and variants | `CreativeBucketCatalog.populate` |
| Mod resource ids; shared behavior registration | `SomeBuckets.id`; `SomeBuckets.registerBehaviors`/`logStarted` |
| Client fluid appearance and bar color; loader client adapter | `ClientPlatform.look`/`barColor` |
| Item model types; Mob Bucket tint; `/sb` tree | `ClientModelTypes`; `MobEggColors`, `MobBucketModel`; `ClientDiagnostics.commandTree` |
| Bootstrap and registration | `SomeBuckets<Loader>`, loader `register` packages |

## Loader integration

Each loader installs `BucketOperations` and registers shared behaviors after item registration. Forge uses EventBus 7 event-specific buses; deferred registries receive the mod `BusGroup`. Cancellable listeners return booleans, and block-break checks honor `Result.DENY`. Forge uses its fluid-handler capabilities; NeoForge uses `ResourceHandler<FluidResource>` with native `ItemAccess` and transactions; Fabric uses its Transfer API. Loader item storage defers to `FluidBucketItem`'s admission and extraction rules, including finite versus infinite; only a one-count stack accepts a fill. Milk remains a distinct mode and exchanges through the loader's milk fluid when available.

`StoredFluid` is the common value; its variant data is a `DataComponentPatch` persisted with the item's registry context. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and `FabricFluidVariants` convert only at loader boundaries; Forge's fluid tag travels as the patch's `custom_data`. Vanilla-rule world placement (`FluidTransactions.emptyFluid`) serves Mob Bucket water and Fabric's arbitrary-fluid output, which debits through its transactional item storage. Forge and NeoForge place through their own fluid types. NeoForge previews abort native transactions; executed transfers commit.

`Dispensers` moves the loader's automation actor to the dispenser. Forge caches a plain `ServerPlayer`. Real players supply statistics, criteria, feedback, and protection identity. `Protections` applies vanilla block-use gates, world borders, loader break checks, and optional Fabric Common Protection API checks. Forge/NeoForge `placeChecked` captures block snapshots, posts placement events, and restores refused changes; Fabric checks Common Protection API first. `FluidTransactions.placeFluidChecked` delays replaced-block drops until placement stands. Tanks and Source cauldrons run inside `asBlockInteraction`, which on Forge and NeoForge suspends `useOn`'s snapshot capture. Each loader's mob-capture listener runs `MBItem.preemptInteraction` first.

Each client installs `ClientPlatform` with fluid appearance, config directory, and loader name. It supplies fluid models, diagnostic samples, and Big/Huge bar colors. Its reload listener resets egg palettes and preloads fluid colors. The fluid layer retextures the generated `item/fluid_mask` model's quads. `ClientDiagnostics` builds the client `/sb` reports on every loader.

A sided block store is authoritative on refusal. NeoForge and Fabric exclude vanilla cauldrons from block-fluid lookup, so common `Cauldrons` owns them on every loader: Big and Huge through interaction maps, Source Buckets and dispensers through `take`/`place`. Without a store, a modded cauldron is an ordinary block.

## Persistent and network state

`ModDataComponentTypes` defines persistent and stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and `junk_contents`. Fluid, milk, powder, and mobs are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize empty state, and maintain derived `MAX_STACK_SIZE` and milk `CONSUMABLE`.

Structural codecs bound finite amounts, whole-bucket milk, powder units, and mob snapshots; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item capacity, exclusivity, and nested-container rejection. Admission runs through `SomeBucketItem.admit`, called by the shared `ItemStackMixin` after a codec builds a stack, without the loader item-inventory lookup: it first restores readable set-aside junk entries while the bucket has room, then removes malformed owned components rather than clamping them; setters trust their callers' capacity and content checks.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in full. `MBItem` saves and loads those NBT snapshots through registry-aware `TagValueOutput` and `TagValueInput`, with a scoped problem collector. A reported save error refuses capture before aquatic water pickup; a reported load error preserves the snapshot and destination.

`BucketStateMigration` wraps the persistent codecs of `fluid_content`, `captured_mobs`, and `junk_contents`, upgrading raw data wherever a stack is decoded. Those components carry a `schema` field, and `captured_mobs`/`junk_contents` the Minecraft `data_version` of their embedded data (both absent in the 1.21.1 release), run through vanilla's data fixer (entity snapshots with a temporary `id`). Encoding writes only current stamps. A released raw-tag Forge fluid variant becomes `minecraft:custom_data` (`BucketOperations.releasedFluidVariantIsRawTag`). Milk and powder components are bare integers and unversioned. Entries that do not decode to storable stacks move to `set_aside`; they are retried on every decode, keep the bucket non-empty, and appear only in the tooltip.

Inventory insertion and FIFO extraction mutate identically on client and server; menu authority corrects mispredictions.

## Configuration and presentation

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register `ModConfig.Type.SERVER`; the loader synchronizes it to clients and honors the per-world `serverconfig/somebuckets-server.toml` override. Config load/reload events refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`. `FabricSBPolicyPayload` sends resolved ids and milk permission on join and after a reload that changed them; clients apply it on the client thread, reset on disconnect, and never read local JSON as remote policy.

Resources are shared. Each structure-loot roll is a data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge data providers generate checked-in add-table global modifiers under `src/generated/resources` (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`) from that manifest. `MobEggColors` caches GUI egg snapshots reduced to two sampled Oklab colors by `TwoColorPalette`; resource-pack overrides win. `EggPaletteSampler` queues one asynchronous readback at a time, releasing idle resources and retiring old work on reload. `MobBucketModel` keys GUI models by palette and animates pending samples.

`FluidBucketModel` keys GUI models by model, foil, and fluid appearance; animated sprites and foil request redraw. Vessels use the item atlas, fluids the block atlas. `JunkContentsRenderer` builds cover quads on first submission after atlas initialization and submits through `SubmitNodeCollector`. Both cache until reload.

## GameTests

Shared scenarios and structures live under `common/src/gametest`. Fabric discovers annotated wrappers. Forge and NeoForge register test functions and load vanilla test instances from shared and loader-specific data packs. Each loader checks for missing shared scenarios. `SharedGameTestSupport` creates explicit Survival players and owns registry-aware snapshot and stack persistence helpers.

## Conventions the code currently follows

- Preview, authorize the exact target (`mayRemove`; `mayModify` plus `placeChecked` for placement), then mutate; debit after a placement stands.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk and feed through the animal's own interaction; automation never feeds an untamed tamable.
- Check live Mob eligibility at release; never capture a leashed mob or another's owned mob.
- Register `ClientModelTypes` before the first client resource load; render caches live in baked models or reload listeners.
