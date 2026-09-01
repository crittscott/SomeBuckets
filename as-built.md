# Some Buckets As-Built Orientation

This document describes the repository's build structure, subsystem ownership, persistent data,
cross-loader boundaries, and maintenance invariants. `player-view.md` describes observable behavior.
The code is authoritative when either document disagrees with it.

It should not contain history and it is not part of a conversation with the user. It should describe
the code as it is. It is not a prose version of the code, it is an orientation.

## Repository map

Some Buckets is a Java 21 mod for Minecraft 1.21.1. Root package
`com.github.crittscott.somebuckets`, mod id `somebuckets`. Four modules:

| Module | Contents |
| --- | --- |
| `common` | Loader-neutral item behavior, state, protection contracts, interaction helpers, client algorithms, shared resources, shared GameTest scenarios |
| `forge`, `neoforge` | Parallel peers with identical package layout: registration and lifecycle, capabilities, events, cauldrons, dispensers, rendering, configuration, loot modifiers, GameTest entry points |
| `fabric` | Registration and lifecycle, Transfer API integration, callbacks, mixins, rendering, configuration, loot injection, GameTest entry points |

Architectury Loom transforms `common`; each loader shadows the result into its production jar and
remaps it. `common` is not a runtime mod. The only loader import in common production Java is Fabric
Loader's cross-remapped `@Environment` on client code, which Architectury remaps to `@OnlyIn` for
Forge and NeoForge.

`forge` and `neoforge` share no code directly — each has its own capability adapters, registration,
events, and rendering — but loader-neutral logic is lifted into `common` (e.g. `fluid/WorldFluidPickup`,
`BBItem.canAcceptFluidUnit`). The fluid layer diverges most, since NeoForge replaced Forge's
capability system.

Shared production resources are under `common/src/main/resources`; resources tied to loader-specific
model, loot, or runtime facilities stay in their module. `common/src/compat/java` holds the optional
FTB Chunks adapter, added to the Fabric and NeoForge source sets; no FTB Chunks build exists for
Forge on this version.

The mod registers six items, one creative tab, and five data component types. Registry ids and
capacities live in `item/BucketDefinitions`, which loader registration consumes. There are no blocks,
block entities, menus, packets, commands, or saved-world data; all bucket state lives on item stacks.

## Subsystem ownership

| Area | Primary owner |
| --- | --- |
| Item identities and capacities | `common/.../item/BucketDefinitions` |
| Big and Huge Bucket behavior | `common/.../item/BBItem` |
| Source Bucket behavior and allowlist policy | `common/.../item/SBItem`, `common/.../config/SBPolicy` |
| Junk and Trash Bucket behavior | `common/.../item/JBItem`, `TBItem` |
| Mob Bucket behavior | `common/.../item/MBItem` |
| Item-stack serialization | `common/.../util/BucketState`, `StoredFluid`, `common/.../register/ModDataComponentTypes` |
| Loader fluid operations | `common/.../platform/BucketOperations` and each loader's implementation |
| World fluid pickup | `common/.../fluid/WorldFluidPickup` (vanilla `BucketPickup`), used by all three loaders |
| Held-transfer settlement, milk-transfer and cow-milking rules | `common/.../interaction/HeldTransferSettlement`, `MilkTransfers` |
| Dispenser geometry and shared non-fluid automation | `common/.../interaction/DispenserTarget`, `NonFluidDispensers` |
| Powder-snow cauldron transitions | `common/.../interaction/PowderSnowCauldrons` |
| Authorization and claim composition | `common/.../protection` |
| Furnace policy | `common/.../fuel/BucketFuel`, with loader hooks |
| Creative-tab ordering and variants | `common/.../register/CreativeBucketCatalog` |
| Sound registry ids | `common/.../register/ModSoundIds` |
| Structure-loot policy | `common/src/main/resources/somebuckets/bucket_loot.json`, read by `BucketLootTables` |
| Shared model and texture algorithms | `common/.../client` |
| Loader registration and bootstrap | `SomeBucketsForge`, `SomeBucketsNeoForge`, `SomeBucketsFabric`, and each loader's `register` package |

Common item classes own gesture selection and domain behavior; loader code owns operations where
loaders differ in transaction, event, storage, placement, or rendering model. Client presentation may
predict state; persistent and world mutations are server-authoritative.

## Cross-loader seams

`BucketOperations` is the main runtime boundary, installed by each loader before any interaction can
reach common item behavior. It covers held-container transfers, block-storage discovery, world pickup
and placement, powder snow, Source Bucket operations, loader-native fluid names and colors, and
whether a stack exposes a loader item-inventory handler (so Junk and Trash Buckets reject modded
backpacks), and classifies an assigned Source Bucket target as matching, blocking, or no fluid before
common gesture dispatch. A storage on the targeted block face owns dispatch; its refusal does not fall through to
ordinary world-fluid handling.

Mob Bucket aquatic capture and release use the same seam (`takeAquaticSourceWater`,
`placeAquaticSourceWater`), both shared vanilla-typed code: capture calls `WorldFluidPickup`, release
calls `FluidPlacement`'s fixed-water path. Each loader supplies only the fill sound, which is
loader-registered for a modded fluid.

`WorldFluidPickup` also backs every loader's ordinary Big and Source Bucket world pickup: `sourceAt`
reports the `StoredFluid` one bucket-volume at a position would yield; `take` runs the vanilla
`BucketPickup#pickupBlock` transaction, plays the caller's sound, and emits the fluid-pickup game
event. `takeBlock` is the same transaction for a `BucketPickup` block with no fluid state — powder
snow — so every loader's Big Bucket powder pickup goes through `pickupBlock` rather than a raw
`setBlock`. A modded fluid block that is not a vanilla `BucketPickup` is not world-pickable on any
loader; there is no `IFluidBlock` path.

`StoredFluid` is the loader-neutral fluid value for common code; `ForgeFluidStacks`,
`NeoForgeFluidStacks`, and `FabricFluidVariants` convert it to and from each loader's native type. The
NeoForge and Fabric converters bridge the optional variant `CompoundTag` and the stack's
`DataComponentPatch`; a NeoForge component needing registry context to serialize degrades to a blank
patch, lossless for water, lava, milk, and typical modded fluids. Variant data must survive
conversion in every direction; `NeoForgeFluidStacksGameTests` asserts a registry-free component
(`minecraft:custom_data`) survives both ways and the item-stack storage path.

`AutomationPlayers` is the other installed runtime boundary: the stable loader-native fake player for
dispenser claim checks. NeoForge and Fabric install it; Forge does not, because Forge 1.21.1 ships no
fake-player utility and has no claim provider that would consult one (`FtbChunksProtection`, the only
`AutomationPlayers.get` caller, never registers on Forge). `Protections` combines vanilla player
restrictions with all registered `ClaimProtectionProvider`s and requires every provider to allow an
action; it also owns the provider registry (`Protections.register`, returning a `Registration`
token). For a player context `Protections` also applies vanilla
`Level.mayInteract` (spawn protection, world border) to every action, including entity interaction,
and the stricter `Player.mayUseItemAt` block-placement gate to every action except entity
interaction. The FTB Chunks adapter registers only when the loader reports FTB Chunks present.

Loader-specific fluid integration is deliberately not abstracted below these seams:

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Item and block fluid storage | Forge fluid capabilities (`AttachCapabilitiesEvent`, `LazyOptional`) | NeoForge fluid capabilities (`RegisterCapabilitiesEvent`, nullable lookups) | Fabric Transfer API |
| World-fluid hooks | Forge events and fluid utilities | NeoForge events and fluid utilities | Fabric callbacks and transfer transactions |
| Water and lava cauldrons | `forge/.../interaction/Cauldrons` | `neoforge/.../interaction/Cauldrons`; vanilla cauldrons are excluded from the generic block-capability lookup so this path owns them | Transfer API and `FabricCauldronInteractions` |
| Fluid dispensers | `forge/.../interaction/Dispensers` | `neoforge/.../interaction/Dispensers` | `FabricFluidDispensers` |
| Furnace consumption | `forge/.../fuel/ForgeFuelEvents` | `IItemExtension#getBurnTime` on `NeoForge{BB,SB}Item` (returns `0`, not `-1`, for non-lava) | `AbstractFurnaceBlockEntityMixin` |
| Dynamic item rendering | Forge model loaders and BEWLR | NeoForge geometry loaders and `RegisterClientExtensionsEvent` renderer | Fabric baked-model wrappers and builtin renderer |

Finite lava-bucket fuel is consumed one unit at a time: each Big or Huge Bucket unit gives one
20,000-tick burn and leaves the bucket one unit lighter, until the last unit leaves it empty. An
allowed lava Source Bucket returns unchanged and is permanent fuel.

`FluidPlacement` is not the general placement adapter; it owns only fixed vanilla-water placement for
aquatic Mob Bucket release, plus shared target-resolution, evaporation, and sound helpers. Its
`emptyContents` runs the protection preview and the ultra-warm branch itself, then delegates the
place/waterlog/destroy transaction to vanilla `BucketItem#emptyContents`; Fabric's arbitrary-fluid
placement reuses its `resolveTarget` and `evaporate`. Placing an arbitrary stored fluid back into the
world otherwise stays loader-owned, so each loader's fluid metadata, vaporization, and block-state
rules apply.

## Persistent item state

`BucketState` is the sole reader and writer of bucket state. Every payload lives in a registered
`DataComponentType` declared in `register/ModDataComponentTypes` (`somebuckets:fluid_content`,
`milk_amount`, `powder_units`, `captured_mobs`, `junk_contents`), each carrying a `Codec` and a
`StreamCodec`; each loader's `register` code (`ModDataComponents` on Forge and NeoForge,
`FabricDataComponents` on Fabric) only enters them into `Registries.DATA_COMPONENT_TYPE`. At every
mutation `BucketState` also rewrites the vanilla `MAX_STACK_SIZE` component for `VariableStackItem` stacks
from the resulting empty-versus-filled state, so no loader hook is involved.

`fluid_content`, `milk_amount`, `powder_units`, and `captured_mobs` are mutually exclusive — a content
write removes the other three first. `junk_contents` is independent and coexists with any of them:

| Component | Payload |
| --- | --- |
| `fluid_content` | fluid id, amount in millibuckets, optional variant `CompoundTag` |
| `milk_amount` | amount in millibuckets |
| `powder_units` | powder-snow block count |
| `captured_mobs` | entity-type id and the FIFO list of snapshot compounds |
| `junk_contents` | the stored `ItemStack` list and its render-layout seed |

The Junk Bucket layout seed lives inside `junk_contents`, so it appears and disappears with the
stored items. `BucketState.getMode` derives the mutually-exclusive `Mode` from which content component is
present.

State mutators leave canonical empty state: a content component whose value has decayed to empty
(zero amount, `Fluids.EMPTY`, or no snapshots) is removed, `junk_contents` is removed once its list
is empty, and unrelated components on the stack are never touched. Entity snapshots are FIFO, and
`captured_mobs` clears with the final snapshot. Common code stores fluids in millibuckets, matching
Forge and NeoForge `FluidStack`; Fabric converts to droplets only at the Transfer API boundary. On
Fabric, `BucketStackState` settles a Transfer API working copy back onto the real stack by copying
those five components plus `MAX_STACK_SIZE` and the count.

## Data and resources

Recipes, the Mob Bucket blacklist tag, translations, sounds, and most item models and textures are
shared. Custom recipe ingredient serializers share ids across loaders, with loader-specific
implementations registered at bootstrap.

`somebuckets/bucket_loot.json` is the single structure-loot policy, loaded by `BucketLootTables`.
Fabric builds loot pools from it at runtime; the Forge and NeoForge builds generate global
loot-modifier resources from it during resource processing (`forge:loot_table_id`,
`neoforge:loot_table_id`). Changes to rewards, chances, initial contents, or target tables belong in
the manifest.

`CreativeBucketCatalog` is the single ordered definition of creative-tab contents and prefilled
variants, consumed by every loader's creative-tab registration.

Shared client code defines loader-independent model predicates, texture-mask geometry, Junk Bucket
layout, and representative-color calculations; loader client packages adapt those to their model and
rendering APIs. Resources used by only one rendering path stay in that loader's tree.
`JunkBucketRenderData` caches each rendered Junk Bucket's decoded contents and arranged layout,
keyed by the identity hash of the immutable `junk_contents` component and confirmed on each hit by a
retained reference to it: the component is replaced wholesale whenever its items or layout seed
change, so identity is an exact change signal and an identity-hash collision only forces a fresh
decode rather than a wrong render. Every loader clears the cache on resource reload alongside
`JunkBucketIcons` (the merged opening-mask, cover-rectangle, and icon-layout geometry).

## Configuration

`SBPolicy` is the resolved, immutable Source Bucket allowlist used by common behavior. Forge and
NeoForge read it from the world server config (`serverconfig/somebuckets-server.toml`) via their
`ServerConfig` on the `ModConfigSpec`, refreshing on config load and reload; Fabric reads
`config/somebuckets-server.json` via `FabricServerConfig` on server start and on datapack reload
(`ServerLifecycleEvents.SERVER_STARTING` and `END_DATA_PACK_RELOAD`), so `/reload` re-reads the
allowlist without a restart. Until the first read `SBPolicy` serves its shipped default snapshot.
Loader config code resolves ids and installs the policy; Source Bucket code does not parse
configuration.
`SBPolicy.refresh` logs the resolved allowlist at `info` on the first load and whenever the resolved
snapshot changes, at `debug` on a reload that resolves to the same snapshot, plus a `warn` per unknown
id on every load and reload; loader config code does not.

FTB Chunks is compile-only and optional on Fabric and NeoForge. Its API stays in
`common/src/compat/java`; common behavior reaches it only through the protection-provider registry.

## GameTests

Cross-loader scenario bodies and assertions live under `common/src/gametest/java`; loader GameTest
trees hold discovery wrappers and cases that exercise loader APIs directly. The root build decodes the
shared fixture `common/src/gametest/fixtures/empty_9x6x9.nbt.b64` into each loader's generated
GameTest resources.

Each loader exposes its GameTest source set as a separate development mod. Forge and NeoForge
discovery depend on the `somebuckets_gametest` Loom mod entry with a matching loader manifest, `@Mod`
stub, and GameTest `pack.mcmeta`. NeoForge scans for `@GameTestHolder(somebuckets)`, and every suite
carries `@PrefixGameTestTemplate(false)`, since NeoForge otherwise prepends the namespace and
lowercased class name to an already-namespaced template id; `GameTestSupport.TEMPLATE` is thus the
bare `empty_9x6x9`. The enabled-namespace run property is `forge.enabledGameTestNamespaces` /
`neoforge.enabledGameTestNamespaces`. Forge tests reading production resources must anchor
`Class.getResourceAsStream` to a production class, since the GameTest mod is a separate JPMS module.
Fabric's GameTest task clears only `fabric/run/world` before launch so saved entities cannot leak
between runs.

## Maintenance invariants

- Keep registry ids, capacities, creative variants, fuel rules, sound ids, data component types, and
  loot policy in their shared authorities; loader code only adapts or registers them.
- Keep loader runtime APIs out of `common/src/main/java` apart from the cross-remapped client
  environment annotation, and convert loader-native fluid values only at loader boundaries.
- Install `BucketOperations` and `AutomationPlayers` before any common interaction can run.
- Route all persisted bucket state through `BucketState` and its `ModDataComponentTypes` components; keep
  `BucketState`'s public API the seam, preserve fluid variant data and unrelated components, and
  canonicalize empty state at mutation time.
- Apply `SBPolicy` to every Source Bucket input and output path.
- Preview transactions before authorization and mutation, and protect the exact block or entity that
  will be accessed or changed. When a fluid pour would destroy an existing replaceable block, check
  `BLOCK_EDIT` at that position in addition to `FLUID_EDIT` (`FluidPlacement.emptyContents`,
  `FabricFluidPlacement.place`); the Forge and NeoForge arbitrary-fluid placement paths delegate that
  destruction to the loader's own `FluidUtil` and do not add the check.
- Assigned fluid Source Bucket gesture dispatch: normal targeted use places; sneak-targeted use takes
  one matching collectible unit; sneak-air use clears the assignment, after held-container transfer
  has had priority. Dispensers instead take matching fluid from their exact front block, else attempt
  placement there, allowing loader-native reactions with a different world fluid.
- Treat a present sided block-fluid store as authoritative even when it refuses a transfer; do not
  fall through to world-fluid handling. On NeoForge the dedicated `Cauldrons` path owns every cauldron
  interaction — `neoforge/.../interaction/BlockFluidTransfers` skips `AbstractCauldronBlock` in the
  block-capability lookup — matching Forge, which has no such capability.
- Route world fluid pickup only through `WorldFluidPickup` (vanilla `BucketPickup`) — `take` for
  fluids, `takeBlock` for powder snow; loader code supplies the fill sound and converts the returned
  `StoredFluid`. Keep arbitrary stored-fluid placement loader-owned, and reach the fixed-water Mob
  Bucket path via `BucketOperations.placeAquaticSourceWater`.
- Preserve legal item-stack settlement during held and machine transfers; route stack-pile settlement
  and milk arithmetic through `HeldTransferSettlement` and `MilkTransfers`, loader code supplying only
  the "still holds something" predicate. Fabric block transfers keep block and item storage in one
  transaction.
- In a held-container transfer, process a multi-count foreign stack one unit at a time until the
  source or the stack is exhausted, for any container exposing the loader's fluid storage. A finite
  Big or Huge Bucket is bounded by its remaining content; an assigned Source Bucket only by the
  foreign stack's size.
- Emit successful fluid-placement sounds from an authoritative success path that includes the acting
  player without duplicating the broadcast to nearby players.
- A canceled powder-snow placement must not debit the bucket. On NeoForge, `BBFluidLogic.tryPlacePowder`
  fires the block-place event and finalizes the captured snapshot itself on the player use path, since
  NeoForge defers `EntityPlaceEvent` past `useOn` return and its held-stack rollback cannot undo the
  `custom_data` debit.
- Route every Junk and Trash intake through the common storage eligibility rule, and remove a Mob
  Bucket snapshot only after successful world insertion.
- Route cow milking through the animal's own `interact` via `MilkTransfers.milkCow` (player paths on
  both sides for prediction) so modded milking behavior is honored; the bucket records its milk unit
  only after the interaction consumes the action. Dispenser automation still assigns milk directly.
- Keep server-safe common code free of client initialization; rendering state derives from the same
  `BucketState` components and shared model-property constants as item behavior.
- Keep shared GameTest scenarios in `common` and loader discovery or API-specific coverage in the
  loader modules.
- Route all logging through `SomeBuckets.LOGGER`. Each loader entrypoint logs an `info` milestone as it
  initializes and its client bootstrap logs one when it runs; Forge and NeoForge log another `info`
  when common setup finishes; the FTB Chunks adapter logs when it engages; `SBPolicy.refresh` logs the
  resolved allowlist at `info` on first load or when it changes and at `debug` on an unchanged reload;
  `BucketLootTables` logs an `info` line when the loot manifest loads and `error` context before
  failing fast on a malformed manifest. Anomalies use `warn`/`error`; nothing logs on per-tick or
  per-interaction paths.
