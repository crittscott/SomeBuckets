# Some Buckets As-Built Orientation

Orientation to the repository's build structure, subsystem ownership, persistent data, cross-loader boundaries, and maintenance invariants. `player-view.md` covers observable behavior. Not a spec, not a prose restatement of the code; the code wins when they disagree. No history.

Length budget: 150 lines / 12k characters. If an edit pushes past that, cut something — don't append.

Per-sentence test: every sentence either (a) names the file/class to open to change a behavior, or (b) names an invariant not visible from any single file — a cross-module contract, an ordering requirement, a "keep these in sync". Sentences that only restate what the code does get deleted, as do enumerations of a method's branches or steps. Update in place.

## Repository map

Some Buckets is a Java 21 mod for Minecraft 1.21.1. Root package
`com.github.crittscott.somebuckets`, mod id `somebuckets`. Four modules:

| Module | Contents |
| --- | --- |
| `common` | Loader-neutral item behavior, fluid-transaction orchestration, state, protection contracts, interaction helpers, client algorithms, shared resources, shared GameTest scenarios |
| `forge`, `neoforge` | Parallel peers, identical package layout: registration and lifecycle, capabilities, events, cauldrons, dispensers, rendering, configuration, loot modifiers, GameTest entry points |
| `fabric` | Registration and lifecycle, Transfer API integration, callbacks, mixins, rendering, configuration, loot injection, GameTest entry points |

Architectury Loom transforms `common` into each loader's production jar; `common` is not a runtime
mod and `forge`/`neoforge` share no code directly. The only loader import in common production Java is
Fabric Loader's cross-remapped `@Environment` on client code. `common/src/compat/java` holds the
optional FTB Chunks adapter (Fabric and NeoForge source sets only). The mod registers six items, one
creative tab, and five data component types; registry ids and capacities live in
`item/BucketDefinitions`. There are no blocks, block entities, menus, packets, commands, or
saved-world data — all bucket state lives on item stacks.

## Subsystem ownership

| Area | Primary owner |
| --- | --- |
| Item identities and capacities | `common/.../item/BucketDefinitions` |
| Big and Huge Bucket behavior | `common/.../item/BBItem` (gestures), `common/.../fluid/BBFluidLogic` (world transactions) |
| Source Bucket behavior and allowlist policy | `common/.../item/SBItem` (gestures), `common/.../fluid/SBFluidLogic` (assignment and output), `common/.../config/SBPolicy` |
| Junk and Trash Bucket behavior | `common/.../item/JBItem`, `TBItem` |
| Mob Bucket behavior | `common/.../item/MBItem` |
| Item-stack serialization | `common/.../util/BucketState`, `StoredFluid`, `BucketStackState`, `register/ModDataComponentTypes` |
| Loader fluid primitives | `common/.../platform/BucketOperations` and each loader's implementation |
| World fluid pickup | `common/.../fluid/WorldFluidPickup` (vanilla `BucketPickup`), used by all three loaders |
| Held-transfer settlement, milk-transfer and cow-milking rules | `common/.../interaction/HeldTransferSettlement`, `MilkTransfers` |
| Dispenser geometry and shared non-fluid automation | `common/.../interaction/DispenserTarget`, `NonFluidDispensers` |
| Cauldron transitions | `common/.../interaction/PowderSnowCauldrons` (powder snow); `forge`/`neoforge` `interaction/Cauldrons` (water, lava); Fabric via the Transfer API |
| Authorization and claim composition | `common/.../protection` |
| Furnace policy | `common/.../fuel/BucketFuel`, with loader hooks |
| Creative-tab ordering and variants | `common/.../register/CreativeBucketCatalog` |
| Sound registry ids | `common/.../register/ModSoundIds` |
| Structure-loot policy | `common/src/main/resources/somebuckets/bucket_loot.json`, read by `BucketLootTables` |
| Shared model and texture algorithms | `common/.../client` |
| Loader registration and bootstrap | `SomeBucketsForge`, `SomeBucketsNeoForge`, `SomeBucketsFabric`, and each loader's `register` package |

## Cross-loader seams

`BucketOperations` is the main runtime boundary, installed by each loader before any common
interaction runs. It is a thin set of loader primitives — block-store probing and moves, cauldron
transitions, arbitrary placement, sounds, native powder placement, held-container transfer, fluid
identity, item-handler detection (so Junk and Trash Buckets reject modded backpacks), the Forge
`FillBucketEvent` carve-out. Common `fluid/BBFluidLogic` and `fluid/SBFluidLogic` sequence them with
mode admission, protection, debit/credit, and player accounting; `BBItem`, `SBItem`, and the loader
dispensers call those two.

Mob Bucket aquatic capture and release go through `BucketOperations.takeAquaticSourceWater` /
`placeAquaticSourceWater`; each loader supplies only the fill sound. `WorldFluidPickup` (vanilla
`BucketPickup`) backs every loader's ordinary Big and Source Bucket world pickup and Big Bucket
powder-snow pickup. A modded fluid block that is not a vanilla `BucketPickup` is not world-pickable on
any loader; there is no `IFluidBlock` path.

`StoredFluid` is the loader-neutral fluid value for common code; `ForgeFluidStacks`,
`NeoForgeFluidStacks`, and `FabricFluidVariants` convert it to and from each loader's native type.
Variant data must survive conversion in every direction (`NeoForgeFluidStacksGameTests` guards this);
a NeoForge component needing registry context degrades to a blank patch, lossless for common fluids.

`AutomationPlayers` is the other installed runtime boundary: the stable loader-native fake player for
dispenser claim checks. NeoForge and Fabric install it; Forge does not, having no fake-player utility
and no claim provider that would consult one. `Protections` (in `common/.../protection`) combines
vanilla restrictions with every registered `ClaimProtectionProvider`, owns the provider registry, and
adds vanilla `Level.mayInteract` plus, except for entity interaction, `Player.mayUseItemAt`.

Loader-specific fluid integration is deliberately not abstracted below these seams. Storage and
world-fluid hooks stay native (Forge/NeoForge fluid capabilities, Fabric Transfer API and callbacks);
the loader files owning the rest:

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Water and lava cauldrons | `forge/.../interaction/Cauldrons`, editing `BucketState` directly | `neoforge/.../interaction/Cauldrons`, same; vanilla cauldrons excluded from the generic block-capability lookup | Transfer API; `FabricCauldronInteractions` covers powder snow |
| Fluid dispensers | `forge/.../interaction/Dispensers` | `neoforge/.../interaction/Dispensers` | `FabricFluidDispensers` |
| Furnace consumption | `forge/.../fuel/ForgeFuelEvents` | `IItemExtension#getBurnTime` on `NeoForge{BB,SB}Item` | `AbstractFurnaceBlockEntityMixin` |
| Dynamic item rendering | Forge model loaders and BEWLR | NeoForge geometry loaders and client-extension renderer | Fabric baked-model wrappers and builtin renderer |

Finite lava fuel is consumed one unit per 20,000-tick burn (`common/.../fuel/BucketFuel` plus loader
hooks); an allowed lava Source Bucket is permanent fuel and returns unchanged. `FluidPlacement` owns
only fixed vanilla-water placement for aquatic Mob Bucket release plus shared target/evaporation/sound
helpers; arbitrary stored-fluid placement stays loader-owned (`ForgeFluidPlacement`,
`NeoForgeFluidPlacement`, `FabricFluidPlacement`).

## Persistent item state

`BucketState` is the sole reader and writer of bucket state. Every payload lives in a registered
`DataComponentType` declared in `register/ModDataComponentTypes`, each carrying a `Codec` and a
`StreamCodec`; loader `register` code only enters them into `Registries.DATA_COMPONENT_TYPE`. At every
mutation `BucketState` also rewrites the vanilla `MAX_STACK_SIZE` component for `VariableStackItem`
stacks, so no loader hook is involved.

`fluid_content`, `milk_amount`, `powder_units`, and `captured_mobs` are mutually exclusive — a content
write removes the other three first. `junk_contents` is independent and coexists with any of them; its
layout seed lives inside it, so the seed tracks the stored items.

| Component | Payload |
| --- | --- |
| `fluid_content` | fluid id, amount in millibuckets, optional variant `CompoundTag` |
| `milk_amount` | amount in millibuckets |
| `powder_units` | powder-snow block count |
| `captured_mobs` | entity-type id and the FIFO list of snapshot compounds |
| `junk_contents` | the stored `ItemStack` list and its render-layout seed |

Mutators canonicalize empty state at mutation time and never touch unrelated components; entity
snapshots are FIFO. Common code stores fluids in millibuckets (matching Forge/NeoForge `FluidStack`);
Fabric converts to droplets only at the Transfer API boundary. `common/.../util/BucketStackState`
settles a working copy back onto the real stack; only Fabric's Transfer API path uses it.

## Data and resources

Recipes, the Mob Bucket blacklist tag, translations, sounds, and most item models and textures are
shared; custom recipe ingredient serializers share ids across loaders. `somebuckets/bucket_loot.json`
is the single structure-loot policy loaded by `BucketLootTables` — Fabric builds loot pools from it at
runtime, Forge and NeoForge generate global loot-modifier resources from it during resource
processing; reward, chance, and target-table changes belong in the manifest. `CreativeBucketCatalog`
is the single ordered definition of creative-tab contents and prefilled variants. Shared client code
owns loader-independent model, texture-mask, and Junk Bucket layout algorithms; `JunkBucketRenderData`
caches decoded Junk Bucket contents keyed by `junk_contents` identity, cleared with `JunkBucketIcons`
on resource reload.

## Configuration

`SBPolicy` is the resolved, immutable Source Bucket allowlist used by common behavior. Forge and
NeoForge read it from `serverconfig/somebuckets-server.toml`; Fabric reads
`config/somebuckets-server.json` on server start and datapack reload, so `/reload` re-reads it without
a restart. Until the first read `SBPolicy` serves its shipped default; Source Bucket code does not
parse configuration. FTB Chunks is compile-only and optional on Fabric and NeoForge; common behavior
reaches it only through the protection-provider registry.

## GameTests

Cross-loader scenario bodies and assertions live under `common/src/gametest/java`; loader GameTest
trees hold discovery wrappers and cases exercising loader APIs directly. The root build decodes the
shared fixture `common/src/gametest/fixtures/empty_9x6x9.nbt.b64` into each loader's generated
resources. NeoForge suites carry `@PrefixGameTestTemplate(false)` so it does not re-prepend the
namespace to the already-namespaced template id. Forge tests reading production resources must anchor
`Class.getResourceAsStream` to a production class, since the GameTest mod is a separate JPMS module.
Fabric's GameTest task clears `fabric/run/world` before launch so saved entities cannot leak between
runs.

## Maintenance invariants

- Keep registry ids, capacities, creative variants, fuel rules, sound ids, data component types, and
  loot policy in their shared authorities; loader code only adapts or registers them.
- Keep loader runtime APIs out of `common/src/main/java` apart from the cross-remapped client
  environment annotation, and convert loader-native fluid values only at loader boundaries.
- Install `BucketOperations` and `AutomationPlayers` before any common interaction can run.
- Keep the finite Big/Huge and Source Bucket fluid-gesture orchestration single-copy in
  `BBFluidLogic` and `SBFluidLogic`; `BucketOperations` implementations stay thin loader primitives
  and never re-host sequencing, protection, or accounting.
- Route all persisted bucket state through `BucketState`'s public API; preserve fluid variant data
  and unrelated components, and canonicalize empty state at mutation time.
- Apply `SBPolicy` to every Source Bucket input and output path.
- Preview transactions before authorization and mutation, and protect the exact block or entity
  changed. On every arbitrary-fluid placement path, where the pour would destroy a replaceable block,
  also check `BLOCK_EDIT` at that position, not just `FLUID_EDIT`.
- Assigned Source Bucket sneak-air use clears the assignment only after held-container transfer has
  had priority and before a milk drink.
- Treat a present sided block-fluid store as authoritative even when it refuses — never fall through
  to world-fluid handling. On NeoForge `BlockFluidTransfers` excludes `AbstractCauldronBlock` so the
  dedicated `Cauldrons` path owns cauldron interactions, matching Forge.
- Route world fluid pickup only through `WorldFluidPickup`, and the fixed-water Mob Bucket path via
  `BucketOperations.placeAquaticSourceWater`; arbitrary stored-fluid placement stays loader-owned.
- Route stack-pile settlement and milk arithmetic through `HeldTransferSettlement` and
  `MilkTransfers`, loader code supplying only the "still holds something" predicate; process a
  multi-count foreign stack one unit at a time until the source or the stack is exhausted. Fabric
  block transfers keep block and item storage in one transaction.
- Emit fluid-placement sounds from a success path that includes the acting player without
  double-broadcasting to nearby players.
- A canceled powder-snow placement must not debit the bucket; `BucketOperations.placeStoredPowder`
  checks `BLOCK_EDIT` and debits only on success. On NeoForge that primitive also fires the
  block-place event and finalizes the captured snapshot on the player-use path, since NeoForge defers
  `EntityPlaceEvent` past `useOn` and its held-stack rollback cannot undo the `custom_data` debit.
- Route every Junk and Trash intake through the common storage eligibility rule, and remove a Mob
  Bucket snapshot only after successful world insertion.
- Route cow milking through the animal's own `interact` via `MilkTransfers.milkCow` so modded milking
  is honored; the bucket records its milk unit only after the interaction consumes the action.
  Dispenser automation assigns milk directly.
- Keep server-safe common code free of client initialization; rendering state derives from the same
  `BucketState` components as item behavior.
- Keep shared GameTest scenarios in `common`, loader discovery and API-specific coverage in the
  loader modules.
- Route all logging through `SomeBuckets.LOGGER`: entrypoints and client bootstraps log an `info`
  milestone, `SBPolicy.refresh` and `BucketLootTables` log resolved state, anomalies use
  `warn`/`error`, and nothing logs on per-tick or per-interaction paths.
