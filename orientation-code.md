<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Structure, ownership, state, loader seams and invariants. Behavior: `orientation-player.md`; build: `orientation-build.md`.

## Project shape

Some Buckets is a Java 17 mod for Minecraft 1.20.1 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, state, protection contracts, interaction helpers, client algorithms, shared resources and GameTest scenarios |
| `forge` | Forge registration, capabilities, events, rendering, config, loot and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, rendering, config, loot injection and test discovery |

Architectury Loom transforms `common`; each loader bundles it with Shadow and remaps the production JAR. Vanilla-only mixins live in `common` (`mixin.common`, `somebuckets-common.mixins.json`): `TemptGoalMixin` (held Junk/Trash contents tempt mobs) and `ItemEntityAccessor` (an item entity's intended collector). Common production Java has no loader runtime imports except Fabric Loader's client `@Environment` annotation.

Six items and one creative tab; no blocks, menus or saved-world objects. All bucket state is item-stack NBT. The only packet is Fabric's Source Bucket policy sync; the only commands are the client-side `/sb fluids` and `/sb eggs` diagnostics (`diagnostic` package).

## Code map

Paths below are relative to the root Java package in `common` unless a loader is named.

| Area | Primary owner |
| --- | --- |
| Item identities and capacities | `item/BucketDefinitions` |
| Big and Huge Bucket behavior | `item/BBItem` |
| Source Bucket behavior and allowlist | `item/SBItem`, `config/SBPolicy` |
| Junk and Trash Bucket behavior | `item/JBItem`, `TBItem` |
| Mob Bucket behavior | `item/MBItem` |
| Serialization, validation and load-time admission | `util/BucketState`, `StoredFluid` |
| Shared item base: stack size, bars, load admission | `item/SomeBucketItem`, `FluidBucketItem` |
| Loader fluid primitives | `platform/BucketOperations` and loader implementations |
| Fluid transactions: world, tanks, Source Buckets, sounds | `fluid/FluidTransactions` |
| Hand-to-hand transfers, milk, settlement | `interaction/HeldTransfers` |
| Cauldrons and dispensers | `interaction/Cauldrons`, `Dispensers` |
| Authorization: vanilla gates and loader protection events | `protection/Protections`, `ProtectionContext`, loader `permits*` implementations |
| Furnace policy | `FluidBucketItem.isLavaFuel`, with loader hooks |
| Client loader adapter | `client/ClientPlatform` |
| Creative ordering and variants | `register/CreativeBucketCatalog` |
| Sound registry IDs | `register/ModSoundIds` |
| Structure loot | `data/somebuckets/loot_tables/inject/*.json`, `somebuckets/bucket_loot.json`, `loot/BucketLootTables` |
| Shared model and texture algorithms | `client` |
| Loader bootstrap and registration | `SomeBucketsForge`, `SomeBucketsFabric`, loader `register` packages |

## Loader integration

`BucketOperations` holds only primitives whose model differs by loader: held-container moves (`moveHeldFluid`), sided block storage (`blockFluidStore`), the automation player, protection-event posting, checked placement (`placeChecked`), the pre-use world-bucket hook (Forge's `FillBucketEvent`, cancel-only), loader fluid names and sounds, and arbitrary-fluid placement. Orchestration is common: `FluidTransactions` owns world pickup and placement, tank exchange, Source Bucket target classification (matching, blocking or no fluid) and sounds; `HeldTransfers` owns hand-to-hand moves; `Cauldrons` and `Dispensers` register vanilla cauldron interactions and dispenser behaviors for both loaders. Tanks and cauldrons are handled in `useOn` (so a player's block use arrives through vanilla dispatch), world fluids in `use`.

Mob Bucket held exchange lives in `MBItem`. Mob Bucket ↔ Mob Bucket moves snapshots unchanged; other mob buckets are read through `mobBucketContents` (Forge AT, Fabric access widener) and converted with `Bucketable` (its default-data helpers for other mobs). Classpath `somebuckets/mob_bucket_items.json`, read on both sides, links mob types to modded buckets `Bucketable` cannot express. Forge item shells expose `ForgeBucketFluidHandler`; Fabric registers `FabricBucketStorage` with the Transfer API. Both delegate fill and drain rules to the item's `acceptable`/`insert`/`extractable`/`extract` methods.

`StoredFluid` is the shared fluid value in millibuckets; Forge converts to `FluidStack`, Fabric to `FluidVariant` and droplets at its API boundary. Variant NBT survives both.

`Protections` takes a `ProtectionContext(actor, hand)`, applies vanilla gates (world border; for players also spawn protection and `mayUseItemAt`), then on the server posts a loader protection event through `BucketOperations`: break (`mayRemove`), item pickup (`mayPickUp`), and, for automation only, block use (`mayInteractWithBlock`) and entity interaction (a player's arrive through vanilla dispatch). Placement goes through `placeChecked`, which checks the block actually placed: Forge suspends any outer `useOn` snapshot capture, captures the placement, posts `onBlockPlace`/`onMultiBlockPlace` and restores the world without drops on refusal; Fabric checks before placing. Fabric also posts Architectury events and Common Protection API checks when installed. Automation acts as the loader automation player (`ForgeAutomationPlayer`, `FabricDispenserFakePlayer`) moved to the dispenser, holding a copy of the bucket. No claim-mod-specific code.

| Concern | Forge | Fabric |
| --- | --- | --- |
| Item and block fluid storage | Fluid capabilities | Transfer API |
| World-fluid hooks | Forge events and fluid utilities | Callbacks and transfer transactions |
| Fluid identity | `ForgeFluidStacks` | `FabricFluidVariants` |
| Policy on clients | Forge config sync | `FabricSBPolicyNetworking` payload |
| Variable stack size | Item-shell override | `ItemStackMixin` |
| Furnace consumption | Fuel event adapter | `AbstractFurnaceBlockEntityMixin` |
| Dynamic rendering | Model loaders and BEWLR | Baked-model wrappers and builtin renderer |

Each finite lava unit burns 20,000 ticks and returns the bucket one unit lighter; an allowed lava Source Bucket returns unchanged as permanent fuel.

## Persistent and network state

`BucketState` owns the schema. Fluid-family and Mob Buckets have one mutually exclusive `Mode` payload; Junk and Trash Buckets use an independent item list.

| `Mode` | Payload |
| --- | --- |
| Absent or unrecognized | No mode-based content |
| `fluid` | `FluidStack` with `FluidName`, `Amount` and optional variant `Tag` |
| `milk` | Root `Amount` |
| `powder_snow` | Root `Powder` |
| `entity` | `EntityType` and FIFO `Entities` snapshots |

Junk and Trash contents use `JunkItems`; `JunkLayoutSeed` drives rendered layout and disappears with the last item. `JunkSetAside` holds entries that could not be loaded (unknown item, or beyond capacity); they are logged once, neither counted nor dropped, and restored to `JunkItems` once loadable and there is room. Mode and item storage are independent in the serializer.

Setters validate against the item (capacity, allowed content) before writing and throw on violation. `SomeBucketItem.verifyTagAfterLoad` runs `BucketState.admitLoadedTag`, which logs and removes malformed mode content and sets aside unloadable junk, so the rest of the code sees only valid state. Mutators canonicalize empty state: exhausted payloads lose marker and keys, empty item storage loses list and seed, and an empty root compound is removed. Unrelated root NBT is preserved. Entity snapshots are FIFO; the type header disappears with the final snapshot.

`SomeBucketItem` defines the shared empty-versus-filled stack-size decision (16 empty, 1 filled). Loader hooks apply it to `ItemStack#getMaxStackSize`; item classes decide whether their own state is empty.

## Configuration and presentation

Recipes, the `somebuckets:uncapturable` entity tag, the `somebuckets:held_transfer_excluded` item tag (`FluidBucketItem.allowsHeldTransfer`), translations, sounds, and most models and textures are shared. Custom ingredient serializers have matching IDs on both loaders and loader-specific bootstrap implementations.

Each structure-loot reward is a loot table `somebuckets:inject/<reward>`; classpath `somebuckets/bucket_loot.json` maps rewards to target tables (`BucketLootTables`). Fabric adds a pool per inject table; Forge uses checked-in `somebuckets:add_table` modifiers generated by `:forge:runData`, which runs automatically before archive tasks.

`CreativeBucketCatalog` orders creative contents and prefilled variants for both loaders. Common client code defines model predicates, texture masks, Junk layout, representative colors (`ClientTextureColors`) and `MobBucketColors` (a Mob Bucket's tint from a spawn egg or `mob_bucket_colors.json`); `ClientPlatform` receives loader fluid textures, tints and spawn-egg lookup at client setup and clears its caches on resource reload. Rendering derives from the same NBT as behavior.

## Configuration

`SBPolicy` is the resolved immutable Source Bucket allowlist; `refresh` reports whether it changed. Forge's `ForgeServerConfig` reads the world server config and refreshes policy on load and reload. Fabric's `FabricServerConfig` reads `config/somebuckets-server.json` at initialization, server start and data-pack reload; `FabricSBPolicyNetworking` sends the resolved list on join and broadcasts it when it changes, and the client resets to defaults on disconnect.

## GameTests

Cross-loader scenarios live under `common/src/gametest/java`; loader trees supply discovery wrappers and native-API cases. Each loader's `every_shared_scenario_has_a_loader_wrapper` fails if a shared scenario lacks a wrapper and is not a listed exclusion. `refuseProtection` stands in for a claim mod via listeners each loader's `GameTestSupport` registers once; place and pickup refusals run only on Forge.

Each test source set is a separate development mod. Forge discovery requires the matching `somebuckets_gametest` Loom entry, `mods.toml`, `@Mod` stub and `pack.mcmeta`. Forge tests reading production resources anchor `Class.getResourceAsStream` to a production class because the test mod is a separate JPMS module.

## Conventions the code currently follows

- IDs, capacities, creative variants, fuel rules, sound IDs and loot policy have shared authorities; loaders adapt or register them.
- Loader fluid values are converted at boundaries. `BucketOperations` is installed before common interactions run.
- Persisted mutations use `BucketState`, validate first, preserve variant and unrelated NBT, and canonicalize empty state.
- Source Bucket input and output paths apply `SBPolicy`. Assigned-fluid targeted use places; sneak-targeted use takes a matching collectible unit; sneak-air use clears after held transfer has priority. Dispensers take matching fluid in front or attempt placement there, including reactions with another fluid.
- Transfers preview before authorization and mutation and check the exact block or entity accessed. Present sided storage owns dispatch; refusal does not fall through to world fluids.
- Filling from a stack of empties uses `HeldTransfers.fillFromHand` (vanilla `ItemUtils.createFilledResult`): one fills, the rest stay. Dispensers work on a one-item copy and insert or eject extra results. Held-transfer results go to the inventory before dropping. Tanks are credited for the amount actually moved. Fabric block and item storage changes share a transaction.
- Held foreign container stacks are processed one unit at a time until the stack or finite source is exhausted. An assigned Source Bucket is bounded only by the foreign stack's size.
- Junk and Trash intake share the storage eligibility rule. Mob snapshots are removed only after successful world insertion.
- Server-safe common code avoids client initialization.
