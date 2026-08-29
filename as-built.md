# Some Buckets As-Built Orientation

This document describes the repository's build structure, subsystem ownership, persistent data,
cross-loader boundaries, and maintenance invariants. `player-view.md` describes observable behavior.
The code is authoritative when either document disagrees with it.

It should not contain history and it is not part of a conversation with the user. It should describe
the code as it is. It is not a prose version of the code, it is an orientation.

## Repository map

Some Buckets is a Java 21 mod for Minecraft 1.21.1. The root package is
`com.github.crittscott.somebuckets`, the mod id is `somebuckets`, and the Gradle build contains three
implemented modules:

| Module | Purpose |
| --- | --- |
| `common` | Loader-neutral item behavior, state, protection contracts, interaction helpers, client algorithms, shared resources, and shared GameTest scenarios |
| `forge` | Forge registration and lifecycle, capabilities, events, cauldrons, dispensers, rendering, configuration, loot modifiers, and Forge GameTest entry points |
| `fabric` | Fabric registration and lifecycle, Transfer API integration, callbacks, mixins, rendering, configuration, loot injection, and Fabric GameTest entry points |

Architectury Loom compiles and transforms `common`; each loader module bundles the appropriate
transformed output into its production jar with Shadow and remaps that jar. `common` is not a
separate runtime mod. Loader APIs remain in loader source sets; the only loader import in common
production Java is Fabric Loader's cross-remapped `@Environment` annotation on common client code.

A fourth subproject, `neoforge`, exists in the build with dependency, metadata, and packaging
scaffolding only; it has no loader Java, GameTest source, or runtime implementation. `build-env.md`
covers its build configuration.

Shared production resources are under `common/src/main/resources`. Loader metadata and resources
that depend on loader-specific model, loot, or runtime facilities stay in the corresponding loader
module. `common/src/compat/java` contains the shared optional FTB Chunks adapter and is added to both
loader source sets.

The mod registers six items and one creative tab. Registry ids and capacities are centralized in
`item/BucketDefinitions`; loader registration classes should consume those definitions rather than
repeat them. The mod has no blocks, block entities, menus, packets, commands, or saved-world data.
All bucket state is attached to item stacks.

## Subsystem ownership

| Area | Primary owner |
| --- | --- |
| Item identities and capacities | `common/.../item/BucketDefinitions` |
| Big and Huge Bucket behavior | `common/.../item/BBItem` |
| Source Bucket behavior and allowlist policy | `common/.../item/SBItem`, `common/.../config/SBPolicy` |
| Junk and Trash Bucket behavior | `common/.../item/JBItem`, `TBItem` |
| Mob Bucket behavior | `common/.../item/MBItem` |
| Item-stack serialization | `common/.../util/NBTUtil`, `StoredFluid` |
| Loader fluid operations | `common/.../platform/BucketOperations` and each loader's implementation |
| Held-transfer settlement and milk-transfer rules | `common/.../interaction/HeldTransferSettlement`, `MilkTransfers` |
| Dispenser geometry and shared non-fluid automation | `common/.../interaction/DispenserTarget`, `NonFluidDispensers` |
| Powder-snow cauldron transitions | `common/.../interaction/PowderSnowCauldrons` |
| Authorization and claim composition | `common/.../protection` |
| Furnace policy | `common/.../fuel/BucketFuel`, with loader hooks |
| Creative-tab ordering and variants | `common/.../register/CreativeBucketCatalog` |
| Sound registry ids | `common/.../register/ModSoundIds` |
| Structure-loot policy | `common/src/main/resources/somebuckets/bucket_loot.json`, read by `BucketLootTables` |
| Shared model and texture algorithms | `common/.../client` |
| Loader registration and bootstrap | `SomeBucketsForge`, `SomeBucketsFabric`, and each loader's `register` package |

The common item classes own gesture selection and domain behavior. Loader code owns operations for
which Forge and Fabric have different transaction, event, storage, placement, or rendering models.
Client-side presentation may predict state, but persistent and world mutations are
server-authoritative.

## Cross-loader seams

`BucketOperations` is the main runtime boundary. Each loader installs its implementation before
registering interactions that can reach common item behavior. It covers held-container transfers,
block storage discovery, world pickup and placement, powder snow, Source Bucket operations, and
loader-native fluid names and colors. It also classifies an assigned Source Bucket target as
matching fluid, blocking fluid, or no fluid before common gesture dispatch. A storage exposed by the
targeted block face owns dispatch; refusal does not fall through to treating the block as an ordinary
world fluid.

Mob Bucket aquatic capture and release take and place one water source through this same seam
(`takeAquaticSourceWater`, `placeAquaticSourceWater`). Capture's pickup side is loader-native on both
loaders. Release's placement side is not: both loaders call the shared `FluidPlacement` fixed-water
path, since neither loader's native fluid-placement utility was a drop-in fit for a caller that has no
fluid-holding container to drain or swap.

`StoredFluid` is the loader-neutral fluid value used by common code. `forge/.../util/ForgeFluidStacks`
converts it to and from `FluidStack`; `fabric/.../fluid/FabricFluidVariants` converts it to and from
`FluidVariant`, bridging its optional variant `CompoundTag` and the variant's `DataComponentPatch`.
Variant data must survive conversion in both directions.

`AutomationPlayers` is the other installed runtime boundary. It supplies the stable loader-native
fake player used for dispenser claim checks. `Protections` combines vanilla player restrictions with
all registered `ClaimProtectionProvider`s, and `ClaimProtections` requires every provider to allow an
action. The shared FTB Chunks adapter is registered only when the loader reports that FTB Chunks is
present.

Loader-specific fluid integration is deliberately not abstracted below these seams:

| Concern | Forge | Fabric |
| --- | --- | --- |
| Item and block fluid storage | Forge fluid capabilities | Fabric Transfer API |
| World-fluid hooks | Forge events and fluid utilities | Fabric callbacks and transfer transactions |
| Water and lava cauldrons | `forge/.../interaction/Cauldrons` | Transfer API and `FabricCauldronInteractions` |
| Fluid dispensers | `forge/.../interaction/Dispensers` | `FabricFluidDispensers` |
| Furnace consumption | `forge/.../fuel/ForgeFuelEvents` | `AbstractFurnaceBlockEntityMixin` |
| Dynamic item rendering | Forge model loaders and BEWLR | Fabric baked-model wrappers and builtin renderer |

Finite lava-bucket fuel is consumed one unit at a time. Each Big or Huge Bucket unit supplies one
20,000-tick burn and leaves the same bucket with one fewer unit; the furnace can consume that
remainder again until the final unit leaves the bucket empty. An allowed lava Source Bucket instead
returns unchanged and remains permanent fuel.

`common/.../fluid/FluidPlacement` is not the general Big, Huge, or Source Bucket placement adapter.
It owns the fixed vanilla-water placement needed by aquatic Mob Bucket release and shared sound
helpers. Arbitrary stored-fluid placement remains loader-owned.

## Persistent item state

`NBTUtil` is the sole schema owner. The whole bucket schema lives inside the built-in
`minecraft:custom_data` component, read and written through `CustomData`; `NBTUtil` also rewrites the
vanilla `MAX_STACK_SIZE` component at that same write boundary. It stores a mutually exclusive `Mode`
payload for fluid-family and Mob Buckets, plus an independent item list for Junk and Trash Buckets.

| `Mode` | Payload |
| --- | --- |
| absent or unrecognized | No mode-based content |
| `fluid` | `FluidStack`, containing `FluidName`, `Amount`, and optional variant `Tag` |
| `milk` | root `Amount` |
| `powder_snow` | root `Powder` |
| `entity` | `EntityType` and FIFO `Entities` snapshots |

Junk and Trash contents are stored in `JunkItems`. `JunkLayoutSeed` belongs to the rendered Junk
Bucket layout and is removed with the last stored item. Mode-based state and stored-item state are
independent in the serializer even though each item family uses only its relevant portion.

State mutators must leave canonical empty state: exhausted mode payloads lose the mode marker and
their payload keys, empty item storage loses its list and layout seed, the `minecraft:custom_data`
component is removed once its tag is empty, and unrelated custom-data keys are preserved. Entity
snapshots are FIFO and the entity header is cleared with the final snapshot. Common code stores
fluids in millibuckets; Fabric converts only at the Transfer API boundary.

`VariableStackItem` defines the shared empty-versus-filled stack-size decision. `NBTUtil` writes the
result to the vanilla `MAX_STACK_SIZE` data component on every state mutation, so no loader hook is
involved; item classes remain responsible for determining whether their own state is empty.

## Data and resources

Recipes, the Mob Bucket blacklist tag, translations, sounds, and most item models and textures are
shared resources. Custom recipe ingredient serializers have the same ids on both loaders, with
loader-specific implementations registered during bootstrap.

`somebuckets/bucket_loot.json` is the single structure-loot policy. `BucketLootTables` loads it for
shared runtime access. Fabric creates loot pools from it at runtime; the Forge build generates global
loot-modifier resources from it. Changes to rewards, chances, initial contents, or target tables
belong in the manifest, not in duplicated loader lists.

`CreativeBucketCatalog` is the single ordered definition of creative-tab contents and prefilled
variants. Both loader creative-tab registrations consume it.

Shared client code defines loader-independent model predicates, texture-mask geometry, Junk Bucket
layout, and representative-color calculations. Forge and Fabric client packages adapt those results
to their respective model and rendering APIs. Resources needed only by one rendering path remain in
that loader's resource tree.

## Configuration

`SBPolicy` is the resolved, immutable Source Bucket allowlist used by common behavior. Forge reads it
from the world server config through `ServerConfig` and refreshes it on config load and reload.
Fabric reads `config/somebuckets-server.json` through `FabricServerConfig` during initialization and
again when a server starts. Loader config code resolves ids and installs the resulting policy;
Source Bucket code should not parse configuration directly.

FTB Chunks is compile-only and optional on both loaders. Its API is confined to
`common/src/compat/java`; ordinary common behavior reaches it only through the protection-provider
registry.

## GameTests

Cross-loader scenario bodies and assertions are under `common/src/gametest/java`. Loader GameTest
trees provide discovery wrappers and cases that exercise loader APIs directly. The shared structure
fixture is stored as `common/src/gametest/fixtures/empty_9x6x9.nbt.b64`; the root build decodes it into
each loader's generated GameTest resources.

Each loader exposes its GameTest source set as a separate development mod. Forge discovery depends
on the `somebuckets_gametest` Loom mod entry, matching `mods.toml`, `@Mod` stub, and GameTest
`pack.mcmeta`. Tests that read production resources on Forge must anchor
`Class.getResourceAsStream` to a production class because the GameTest mod is a separate JPMS
module. Fabric's GameTest task clears only `fabric/run/world` before launch so saved entities cannot
leak between runs.

## Maintenance invariants

- Keep registry ids, capacities, creative variants, fuel rules, sound ids, and loot policy in their
  existing shared authorities; loader code adapts or registers them.
- Keep loader runtime APIs out of `common/src/main/java`, apart from the existing cross-remapped
  client environment annotation. Convert loader-native fluid values only at loader boundaries.
- Install `BucketOperations` and `AutomationPlayers` before common interactions can run.
- Use `NBTUtil` for all persisted bucket state, preserve variant and unrelated NBT, and canonicalize
  empty state at mutation time.
- Apply `SBPolicy` to every Source Bucket input and output path.
- For a player holding an assigned fluid Source Bucket, normal targeted use places, sneak-targeted
  use takes one matching collectible unit, and sneak-air use clears the assignment after
  held-container transfer has had priority. Dispensers instead take matching fluid from their exact
  front block and otherwise attempt placement there, allowing loader-native reactions with a
  different world fluid.
- Preview transactions before authorization and mutation. Protect the exact block or entity that
  will be accessed or changed.
- Treat a present sided block-fluid store as authoritative even when it refuses a transfer.
- Keep general fluid placement in loader code; use common `FluidPlacement` only for its fixed-water
  Mob Bucket responsibility and shared helpers. Reach that responsibility through
  `BucketOperations.placeAquaticSourceWater`, not a direct call.
- Preserve legal item-stack settlement during held and machine transfers. Fabric block transfers
  keep block storage and item storage in one transaction. Route stack-pile settlement and milk-
  transfer arithmetic through the common `HeldTransferSettlement` and `MilkTransfers` classes; loader
  code supplies only the domain-specific "still holds something" predicate settlement needs.
- Process a multi-count foreign stack in a held-container transfer one unit at a time until the
  source or the stack is exhausted, for any container that exposes the loader's fluid storage, not
  only vanilla buckets. A finite Big/Huge Bucket is bounded by its own remaining content; an assigned
  Source Bucket never runs dry, so only the foreign stack's size bounds it.
- Emit successful fluid placement sounds from an authoritative success path that includes the
  acting player without duplicating the broadcast to nearby players.
- Route every Junk and Trash intake through the common storage eligibility rule, and remove a Mob
  Bucket snapshot only after successful world insertion.
- Keep server-safe common code free of client initialization. Rendering state derives from the same
  NBT and shared model-property constants used by item behavior.
- Keep shared GameTest scenarios in `common` and loader discovery or API-specific coverage in the
  loader modules.
