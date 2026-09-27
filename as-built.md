# Some Buckets As-Built Orientation

Repository orientation for build structure, ownership, persisted state, loader seams, and maintenance
invariants. `player-view.md` covers observable behavior. The code wins when either document disagrees.
Keep this file within 150 lines and 12,000 characters; update in place and remove obsolete text.

## Repository map

Some Buckets is a Java 21 mod for Minecraft 1.21.4 under
`com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, cauldrons, dispensers, client type registration and fluid appearance, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid appearance, config, loot injection, policy networking, and test discovery |

Architectury Loom transforms `common` into each loader jar; `common` is not a runtime mod, and Forge
and NeoForge share no code directly. Common production Java has no loader runtime imports except the
cross-remapped client `@Environment`. Registry ids and capacities live in `item/BucketDefinitions`.
There are no blocks, block entities, menus, or saved-world objects; item components hold bucket state.
The only custom gameplay payload is Fabric's Source Bucket policy snapshot.

## Subsystem ownership

| Area | Primary owner |
| --- | --- |
| Item identities, capacities, creative variants | `BucketDefinitions`, `CreativeBucketCatalog` |
| Big/Huge gestures and transactions | `BBItem`, `fluid/BBFluidLogic` |
| Source gestures, transactions, policy | `SBItem`, `fluid/SBFluidLogic`, `config/SBPolicy` |
| Junk/Trash behavior | `JBItem`, `TBItem`; deterministic layout state in `BucketState` |
| Mob behavior and tint identity | `MBItem`, `client/MobEggColors` |
| Serialization, validation, admission | `BucketState`, `ModDataComponentTypes` |
| Legacy conversion | `util/LegacyBucketMigration` |
| Loader fluid primitives | `platform/BucketOperations` plus each loader implementation |
| World pickup and held settlement | `WorldFluidPickup`, `HeldTransferSettlement`, `MilkTransfers` |
| Dispensers | `DispenserTarget`, `BucketDispenseBehavior`, `FluidDispensers`, `NonFluidDispensers` |
| Vanilla cauldrons | `interaction/Cauldrons` on every loader |
| Authorization | `common/.../protection` |
| Item rendering | `items/*.json`; `client/FluidBucketModel`, `JunkContentsRenderer`, `MobEggColors.Tint`, registered by id from `ClientModelTypes` |
| Diagnostics | `common/.../diagnostic`; loader `DiagnosticsSupport` installers |
| Structure loot | `data/somebuckets/loot_table/inject/*.json`, `somebuckets/bucket_loot.json`, `BucketLootTables` |

## Cross-loader seams

Each loader installs `BucketOperations` before common interaction. Implementations provide native
block storage, placement, sounds, powder placement, held transfers, fluid identity,
inventory detection, and Forge-event adaptation; `BBFluidLogic` and `SBFluidLogic` own sequencing,
protection, and accounting once.

`StoredFluid` is the common value; its variant data is a `DataComponentPatch` persisted with the item's
registry context. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and `FabricFluidVariants` convert only at
loader boundaries; Forge's fluid tag travels as the patch's `custom_data`. World pickup always
uses `WorldFluidPickup`; aquatic Mob Bucket water uses `BucketOperations.takeAquaticSourceWater` and
`placeAquaticSourceWater`; arbitrary stored-fluid placement stays loader-owned.

Every loader installs an `AutomationPlayers` fake player; `DispenserTarget` moves it to the dispenser,
facing outward, and makes it the dispenser context's actor. `ProtectionContext.actor()` faces vanilla checks, loader events, and native operations;
`player()` is the real user for statistics, criteria, and feedback, and is null for automation.
`Protections` applies vanilla spawn-protection, world-border, and build checks to the actor.
`DiagnosticsSupport` supplies the config directory and loader name; each client
installs the fluid-color probe and the `FluidBucketModel.Appearance` (still sprite, tint, luminance).
Forge (from its item `RegisterEvent`) and Fabric (at client bootstrap) register `ClientModelTypes`
directly with vanilla's id mappers; NeoForge uses its item-model, special-renderer, and tint-source
registration events.

Forge/NeoForge capabilities and Fabric Transfer API remain native. A present sided block store is
authoritative even when it refuses. NeoForge and Fabric exclude vanilla cauldrons from generic
block-fluid lookup, so common `Cauldrons` owns them on every loader through its interaction-map
entries and `Cauldrons.take`/`place`.

## Persistent and network state

`BucketState` is the sole bucket-state reader/writer. `ModDataComponentTypes` defines persistent and
stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and
`junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs
are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize
empty state, and maintain the derived `MAX_STACK_SIZE` and milk `CONSUMABLE` components.

Structural codecs bound finite amounts, whole-bucket milk, powder units, mob snapshots, and junk
entries; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item
capacity, exclusivity, and nested-container rejection. Admission runs once, whenever a stack is
decoded (`verifyComponentsAfterLoad`), without the loader item-inventory lookup, and removes
malformed owned components rather than clamping them; setters enforce the same invariants on every
write. Junk rendering independently caps and rejects recursive storage entries.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in
full, as vanilla does for container contents and entity buckets.

`LegacyBucketMigration` detects recognized keys without copying unrelated custom data, data-fixes
detached candidates, previews combined state through `BucketState`, and commits only after full
validation. Failure moves recognized fields beneath `SomeBucketsLegacyMigrationQuarantine`, removes
the old retry marker, logs once, and prevents later DataFixer work.

Junk layout transitions mix the previous seed, incoming registry id, moved amount, and resulting
entry count deterministically. Inventory insertion and FIFO extraction execute the same mutations on
client and server; ordinary menu authority corrects stale predictions.

## Configuration and data

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register
`ModConfig.Type.SERVER`; the loader synchronizes it to clients and supports the documented per-world
`serverconfig/somebuckets-server.toml` override behavior on Minecraft 1.21.4. Config load/reload events
refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`.
`network/FabricSBPolicyPayload` sends resolved fluid ids plus milk permission on join and broadcasts
after reload; the client applies it on the client thread and resets to shipped defaults on disconnect.
A multiplayer client never reads its local JSON as remote policy.

Recipes, tags, translations, sounds, models, and textures are shared. Each structure-loot roll is a
data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps
each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge generate
add-table global modifiers (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`)
during resource processing. Client `MobEggColors` reloads `assets/somebuckets/mob_egg_colors.json`,
merged across resource packs, and consults it before the spawn egg's item-definition constant tints;
egg colors exist only in client resources.

## GameTests

Cross-loader scenarios live in `common/src/gametest/java`; loader trees provide discovery wrappers and
native-API cases, and each loader's `every_shared_scenario_has_a_loader_wrapper` fails on an unwrapped
scenario. The root build decodes the shared base64 fixture. NeoForge wrappers use
`@PrefixGameTestTemplate(false)`. Forge resource tests anchor streams to production classes. Fabric
clears its saved GameTest world before launch.

## Maintenance invariants

- Keep ids, capacities, components, fuel, sounds, creative variants, and loot policy in shared authorities.
- Install `BucketOperations`, `AutomationPlayers`, and `DiagnosticsSupport` before common interaction.
- Keep `BBFluidLogic` and `SBFluidLogic` single-copy; loader primitives do not re-host orchestration.
- Route persisted state through `BucketState`; apply `SBPolicy` to every Source input and output.
- Preview before authorization and mutation; protect the exact target.
- Keep held pile settlement in `HeldTransferSettlement` and milk arithmetic in `MilkTransfers`.
- Run player intake into an empty bucket through `HeldTransferSettlement.fillFromHand`.
- Route every held transfer, including off-hand priority, through `tryHeldTransfer`: Some Buckets container to other first.
- Debit powder only after successful protected placement; on NeoForge, suspend snapshot capture around it.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk cows and feed animals through their own interaction; dispensers act as the automation player.
- Emit one correctly positioned sound per success; loader utility exclusions alone justify `notifyActor`.
- Check live Mob eligibility at release.
- Resolve Mob colors only through `MobEggColors`; never read spawn-egg colors directly elsewhere.
- Register `ClientModelTypes` before the first client resource load; keep render caches owned by
  baked model instances or cleared by the loader client reload listener.
- Build Forge spawn-egg ingredient matches lazily after other mods register items.
- Log lifecycle/resolution milestones and anomalies through `SomeBuckets.LOGGER`, never per interaction.
- Keep `/sb` findings in command feedback and overwritten reports, never the logger.
