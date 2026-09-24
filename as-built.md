# Some Buckets As-Built Orientation

Repository orientation for build structure, ownership, persisted state, loader seams, and maintenance
invariants. `player-view.md` covers observable behavior. The code wins when either document disagrees.
Keep this file within 150 lines and 12,000 characters; update in place and remove obsolete text.

## Repository map

Some Buckets is a Java 21 mod for Minecraft 1.21.3 under
`com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client algorithms, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, cauldrons, dispensers, rendering, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, rendering, config, loot injection, policy networking, and test discovery |

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
| Mob behavior and tint identity | `MBItem`, `MobEggColors` |
| Serialization, validation, admission | `BucketState`, `ModDataComponentTypes`, `CapturedMobNetworkRegistry` |
| Legacy conversion | `util/LegacyBucketMigration` |
| Loader fluid primitives | `platform/BucketOperations` plus each loader implementation |
| World pickup and held settlement | `WorldFluidPickup`, `HeldTransferSettlement`, `MilkTransfers` |
| Dispensers | `DispenserTarget`, `BucketDispenseBehavior`, `NonFluidDispensers`, loader fluid dispensers |
| Authorization | `common/.../protection` |
| Rendering algorithms | `common/.../client`; loader render adapters |
| Diagnostics | `common/.../diagnostic`; loader `DiagnosticsSupport` installers |
| Structure loot | `common/src/main/resources/somebuckets/bucket_loot.json`, `BucketLootTables` |

## Cross-loader seams

Each loader installs `BucketOperations` before common interaction. Implementations provide native
block storage, cauldrons, placement, sounds, powder placement, held transfers, fluid identity,
inventory detection, and Forge-event adaptation; `BBFluidLogic` and `SBFluidLogic` own sequencing,
protection, and accounting once.

`StoredFluid` is the common value. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and
`FabricFluidVariants` convert only at loader boundaries and preserve variant data. World pickup always
uses `WorldFluidPickup`; aquatic Mob Bucket water uses `BucketOperations.takeAquaticSourceWater` and
`placeAquaticSourceWater`; arbitrary stored-fluid placement stays loader-owned.

`AutomationPlayers` supplies dispenser identities where the loader supports them. `Protections`
combines vanilla checks with registered `ClaimProtectionProvider`s. `DiagnosticsSupport` supplies the
config directory, loader name, and spawn-egg lookup; each client installs the fluid-color probe.

Forge/NeoForge capabilities and Fabric Transfer API remain native. A present sided block store is
authoritative even when it refuses. NeoForge excludes cauldrons from generic block-fluid lookup so its
dedicated `Cauldrons` path owns them, matching Forge.

## Persistent and network state

`BucketState` is the sole bucket-state reader/writer. `ModDataComponentTypes` defines persistent and
stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and
`junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs
are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize
empty state, and maintain `MAX_STACK_SIZE`.

Structural codecs bound finite amounts, powder units, mob snapshots, and junk entries. `BucketState`
adds enclosing-item capacity, exclusivity, nested-container, and summary rejection. Server admission
removes malformed owned components rather than clamping them, both after creative admission and
before interactions; Junk rendering independently caps and rejects recursive storage entries.

`CapturedMobs` persists full FIFO entity snapshots plus a content UUID. Its stream codec sends only
UUID, type, and count. `CapturedMobNetworkRegistry` resolves a returned summary to the exact
server-session value, rejects forged or stale hints, clears at server start/stop, and caps issued
entries at 65,536 with least-recently-used expiry. Client consumers use only type and count;
recognized creative round-trips cannot replace authoritative snapshots, while expired tokens fail
closed at admission.

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
`serverconfig/somebuckets-server.toml` override behavior on Minecraft 1.21.3. Config load/reload events
refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`.
`network/FabricSBPolicyPayload` sends resolved fluid ids plus milk permission on join and broadcasts
after reload; the client applies it on the client thread and resets to shipped defaults on disconnect.
A multiplayer client never reads its local JSON as remote policy.

Recipes, tags, translations, sounds, models, and textures are shared. `bucket_loot.json` is the single
loot policy: Fabric builds pools at runtime; Forge and NeoForge generate global modifiers during
resource processing. `MobEggColors` reads `somebuckets/mob_egg_colors.json` before loader egg lookup.

## GameTests

Cross-loader scenarios live in `common/src/gametest/java`; loader trees provide discovery wrappers and
native-API cases. The root build decodes the shared base64 fixture. NeoForge wrappers use
`@PrefixGameTestTemplate(false)`. Forge resource tests anchor streams to production classes. Fabric
clears its saved GameTest world before launch.

## Maintenance invariants

- Keep ids, capacities, components, fuel, sounds, creative variants, and loot policy in shared authorities.
- Install `BucketOperations`, `AutomationPlayers`, and `DiagnosticsSupport` before common interaction.
- Keep `BBFluidLogic` and `SBFluidLogic` single-copy; loader primitives do not re-host orchestration.
- Route persisted state through `BucketState`; apply `SBPolicy` to every Source input and output.
- Preview before authorization and mutation; protect the exact target and any replaced block.
- Keep held pile settlement in `HeldTransferSettlement` and milk arithmetic in `MilkTransfers`.
- Debit powder only after successful protected placement; preserve NeoForge's deferred-place handling.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk cows through their own interaction for player use; dispenser automation assigns directly.
- Emit one correctly positioned sound per success; loader utility exclusions alone justify `notifyActor`.
- Keep full Mob snapshots persistent, summary-only on the wire, and live eligibility checked at release.
- Resolve Mob colors only through `MobEggColors`; never read spawn-egg colors directly elsewhere.
- Build Forge spawn-egg ingredient matches lazily after other mods register items.
- Log lifecycle/resolution milestones and anomalies through `SomeBuckets.LOGGER`, never per interaction.
- Keep `/sb` findings in command feedback and overwritten reports, never the logger.
