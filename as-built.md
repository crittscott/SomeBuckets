# Some Buckets As-Built Orientation

This document describes the repository's build structure, subsystem ownership, persistent data,
cross-loader boundaries, and maintenance invariants. `player-view.md` describes observable behavior.
The code is authoritative when either document disagrees with it.

## Project structure

Some Buckets targets Minecraft 1.20.1 and Java 17. Its mod id is `somebuckets`, and its root package
is `com.github.crittscott.somebuckets`. The workspace contains `common`, `forge`, and `fabric`
modules targeting Forge 47.4.0 and Fabric API `0.92.2+1.20.1`.

Both loaders register the same creative tab and six item ids:

| Registry name | Role |
| --- | --- |
| `big_bucket_8` | Finite eight-unit fluid, milk, or powder-snow container |
| `big_bucket_64` | Finite sixty-four-unit container |
| `source_bucket` | Infinite source and sink for one allowed content |
| `junk_bucket` | FIFO storage for nine item-stack entries |
| `trash_bucket` | One-entry storage with destructive replacement |
| `mob_bucket` | FIFO storage for eight mobs of one entity type |

All six items implement `VariableStackItem`, which stacks a bucket to 16 while empty and 1 once it
holds any content, matching vanilla's own empty-versus-filled bucket stack sizes. Each common item
constructor self-enforces a `stacksTo(16)` baseline regardless of what the registration call site
passes in. The per-stack value comes from a loader hook: Forge's `IForgeItem#getMaxStackSize(ItemStack)`,
overridden by each Forge item shell (`ForgeBBItem`, `ForgeSBItem`, `ForgeJBItem`, `ForgeTBItem`,
`ForgeMBItem`); Fabric has no equivalent per-stack hook, so `fabric/.../mixin/ItemStackMixin` injects
into `ItemStack#getMaxStackSize()` for any item implementing `VariableStackItem`.

The mod registers no blocks, block entities, menus, packets, commands, or saved-world objects.
Bucket contents live entirely on item stacks. There are no advancements, loot tables, networking,
JEI integration, or data-generation outputs.

## Build and packaging

The root build uses Gradle 8.11. All three modules use Mojang official mappings layered with
Parchment `2023.09.03-1.20.1`. Architectury Loom is not used.

| Module | Tooling | Role |
| --- | --- | --- |
| `common` | ModDevGradle LegacyForge in MCP/common mode | Shared Java and resources; compile target, not a runtime dependency |
| `forge` | ModDevGradle LegacyForge | Forge runtime implementation, metadata, client integration, and GameTests |
| `fabric` | Fabric Loom | Fabric runtime implementation, Transfer API integration, client integration, and furnace and item-stack-size mixins |

The configured toolchain is ModDevGradle LegacyForge 2.0.77, Fabric Loom 1.9.2, Fabric Loader
0.16.9, and Fabric API `0.92.2+1.20.1`.

`buildSrc` defines two convention plugins. `multiloader-common` supplies Java 17, repositories, and
compilation defaults. `multiloader-loader` adds the raw `common` Java source to each loader's
compilation and merges `common` resources into each loader's resource output. Loader JARs contain
the common classes directly and do not depend on a common runtime JAR.

Only common source and resources cross that boundary; common dependency declarations do not.
Compile-only libraries used by common source must therefore be available to every loader
compilation that ingests it. A loader module must not add `implementation project(":common")`.

Shared recipes, tags, translations, most models, textures, sounds, and `pack.mcmeta` live under
`common/src/main/resources`. Forge and Fabric provide separate fluid-container and Junk Bucket
models. Forge uses a fluid geometry loader and BEWLR; Fabric wraps generated fluid models after
baking and uses a builtin dynamic renderer for the Junk Bucket. Each loader expands its metadata and
the shared `pack.mcmeta` during resource processing.

Neither loader depends on Architectury API. FTB Chunks presence is checked natively on each loader:
Forge's `SomeBucketsForge` uses `ModList.get().isLoaded("ftbchunks")`, Fabric's `SomeBucketsFabric`
uses `FabricLoader.getInstance().isModLoaded("ftbchunks")`. FTB Chunks is compile-only and optional
in both loader descriptors.

Both loader modules define client and dedicated-server development runs. No Gradle GameTest or data
run is configured. Forge GameTest sources reside in `forge/src/main/java` and compile with the main
source set, but the Forge JAR excludes the `gametest` package and its structure fixture. Fabric has
no GameTest sources.

## Runtime layering

Both entrypoints install two loader providers before registering behavior:

- `BucketOperations` supplies loader-owned fluid transactions, target previews, world pickup,
  powder-snow operations, event interception, and fluid presentation.
- `AutomationPlayers` supplies the stable loader-native fake player used by shared dispenser feeding
  and claim checks.

Shared item classes own gesture selection, operation order, milk behavior, item and entity storage,
names, tooltips, bars, and crafting-unit remainders. `NBTUtil` owns persistent representation.
`FluidPlacement`, `Protections`, and `NonFluidDispensers` own loader-neutral world placement,
authorization, and Mob/Junk/Trash dispenser behavior.

The main ownership boundaries are:

| Area | Responsibility |
| --- | --- |
| `common/.../item/` | Shared behavior for all six items |
| `common/.../util/` | Loader-neutral item NBT and `StoredFluid` |
| `common/.../platform/BucketOperations` | Interface installed by each loader entrypoint |
| `common/.../fluid/FluidPlacement` | Vanilla-style world fluid placement |
| `common/.../protection/` | Action contexts, vanilla checks, claim-provider composition, and automation-player indirection |
| `common/.../interaction/NonFluidDispensers` | Mob and storage-bucket dispenser behavior |
| `forge/.../fluid/`, `interaction/`, `platform/` | Forge capabilities, world pickup, cauldrons, transfers, and fluid dispenser selection |
| `forge/.../event/`, `protection/`, `compat/` | Forge held-transfer/fuel events, `FillBucketEvent`, fake player, and FTB Chunks adapter |
| `forge/.../client/` | Forge fluid models and colors, predicates, item tints, and Junk rendering |
| `fabric/.../fluid/`, `interaction/`, `platform/` | Transfer API storage, world fluid operations, cauldrons, held transfers, and fluid dispensers |
| `fabric/.../protection/`, `compat/` | Fabric fake player and FTB Chunks adapter |
| `fabric/.../client/`, `fuel/`, `mixin/` | Fabric models, colors, Junk rendering, and furnace behavior |

Client calls perform prediction where Minecraft expects it. Persistent state and world mutations are
server-authoritative.

## Persistent item state

Both loaders use the same item-stack schema. Content-bearing buckets use a string `Mode`
discriminator; a missing or unknown value reads as `none`.

| Mode | Additional keys | Items |
| --- | --- | --- |
| `none` | none | Empty or unassigned content buckets |
| `fluid` | `FluidStack` | Big, Huge, Source |
| `milk` | `Amount` | Big, Huge, Source |
| `powder_snow` | `Powder` | Big, Huge |
| `entity` | `EntityType`, `Entities` | Mob |

`FluidStack` contains `FluidName`, `Amount`, and an optional `Tag`, matching Forge's serialized
shape on both loaders. `StoredFluid` carries the loader-neutral fluid, mB amount, and copied variant
tag in Java. `ForgeFluidStacks` converts to and from `FluidStack`; Fabric converts to and from
`FluidVariant` and droplets.

Finite fluid and milk removal goes through `NBTUtil.drainFiniteContent`. Empty content normalizes by
removing `Mode` and its mode-specific payload. An empty Some Buckets root tag is removed without
disturbing unrelated item NBT.

Junk and Trash Buckets serialize item stacks under `JunkItems`. Capacity counts list entries, not
individual items. Compatible stacks merge before consuming another entry. Every intake path uses
`JBItem.canStore`, which delegates the storage restriction to
`Item.canFitInsideContainerItems`.

Mob Buckets store one entity type id and a FIFO list of snapshots created with `saveWithoutId`.
Release restores the saved state and UUID. A new UUID is assigned only when the saved UUID belongs
to another loaded entity in any server level. The oldest snapshot is removed only after
`addFreshEntity` succeeds.

Big and Huge Buckets expose finite loader-native fluid stores of 8,000 and 64,000 mB. Source Buckets
expose one bucket unit per public storage operation without losing their assignment. Forge exposes
`IFluidHandlerItem`; Fabric exposes `Storage<FluidVariant>` and accepts only whole mB at 81 droplets
per mB. Milk and powder snow are not exposed through either fluid API.

## Source Bucket policy

Forge uses `serverconfig/somebuckets-server.toml` with `sourceBucket.allowedContents`. Fabric uses
`config/somebuckets-server.json` with `allowedContents`. Both default to:

```text
minecraft:water
minecraft:lava
somebuckets:milk
```

Each loader resolves its configured ids into the immutable `SBPolicy` snapshot. Forge refreshes it
on config load and reload. Fabric loads it during mod initialization and at server start. Unknown
fluid ids are ignored and logged.

The policy is checked when a Source Bucket acquires or supplies fluid, accepts compatible fluid as
a sink, places content, supplies furnace fuel, or consumes milk. Removing an assignment from the
allowlist leaves its NBT and name intact but makes it inert. Sneak-use reset is available. The
policy does not apply to Big or Huge Buckets.

## Protection

Protected operations carry a `ProtectionContext`, an exact target, and one of five actions:
`FLUID_EDIT`, `BLOCK_EDIT`, `BLOCK_INTERACT`, `ENTITY_INTERACT`, or `ENTITY_RELEASE`. Player contexts
contain the real player and hand. Dispenser contexts contain the dispenser position. Feasibility is
normally established before authorization, and authorization precedes mutation.

`Protections` applies vanilla player checks and then asks every registered `ClaimProtectionProvider`.
`ClaimProtections` requires unanimous approval. FTB Chunks is the only bundled provider. Forge
registers `FtbChunksProtection` behind `Platform.isModLoaded`; Fabric registers
`FabricFtbChunksProtection` behind `FabricLoader.isModLoaded`.

For dispenser actions, both FTB adapters position the stable `[SomeBuckets]` fake player at the
dispenser and temporarily put a copy of the acting bucket in its main hand. Entity interaction and
entity release both map to FTB Chunks' `INTERACT_ENTITY` action.

There is no Open Parties and Claims adapter. On Forge its standard interaction hooks and dispenser
wrapper provide compatibility. Other claim systems receive vanilla player restrictions and, on
Forge, the ordinary interaction events used by each operation. No general event covers automated
feeding, mob capture/release, or item vacuum/ejection, so those actions require a dedicated claim
provider for reliable protection. Fabric has no equivalent to Forge's `FillBucketEvent`.

## Fluid and cauldron integration

Forge gives a sided block fluid capability first refusal. Without one, it performs world pickup
through an `IFluidBlock` or a source-only `BucketPickup`. Fabric gives sided Transfer API storage
first refusal and otherwise uses a source-only `BucketPickup`. A present block store owns dispatch
even when it refuses the transaction.

`FluidPlacement` handles generic world output for both loaders. It resolves the clicked block or one
neighbor along the clicked face, supports `LiquidBlockContainer` waterlogging, breaks replaceable
non-liquid blocks with drops, and evaporates water in ultra-warm dimensions. Player placement may
use the neighboring target; dispenser placement is restricted to the block directly in front.

Forge implements water, lava, and powder-snow cauldron transitions in `Cauldrons`. Big/Huge player
callbacks are registered in the vanilla cauldron maps; Source and dispenser paths call the same
transition methods directly. Fabric treats water and lava cauldrons as Transfer API storage.
`FabricCauldronInteractions` supplies Big/Huge powder-snow player callbacks, and
`FabricFluidDispensers` handles powder-snow cauldrons for automation.

Forge player world-fluid paths call `FillBucketEvent` against the resolved mutation target unless a
block capability owns the operation. Fabric's `beforeWorldBucketUse` implementation returns `null`.
Successful paths emit the applicable game event and player statistics or criteria implemented by
that path.

Held and block transfers simulate before execution. Big and Huge Buckets are finite stores. Source
Buckets pump a held destination in one gesture but expose at most one bucket unit per machine-facing
storage call. Forge's off-hand transfer event uses `player.getBlockReach()` to distinguish an air
click; Fabric's callback uses a fixed 5-block raycast.

## Crafting and client resources

Both loaders register custom ingredient serializers named `somebuckets:empty_bucket` and
`somebuckets:spawn_egg`. The empty-bucket ingredient checks a specified Some Buckets item for
`Mode.NONE`. The spawn-egg ingredient accepts every registered `SpawnEggItem`.

`mob_bucket.json` and `big_bucket_64.json` both include Forge's `type` and Fabric's `fabric:type`
discriminator fields on their custom ingredients.

Big, Huge, and Source models use the `somebuckets:bb_content` predicate for empty, fluid, milk, and
powder-snow states. Forge's fluid model uses the stored fluid's still texture and tint. Fabric wraps
the three generated fluid override models after baking, removes their static tint layer, and emits
front, back, and exposed-edge quads for the opaque pixels of the active content mask. Those quads
reference the stored variant's live animated still sprite, use its runtime tint, and render with an
opaque material so the fluid texture's alpha cannot expose the scene behind the bucket. Variant NBT
participates in loader fluid colors. Average sprite color is used only for bars and other
single-color presentation; its cache and the mask cache are cleared on model reload.

Mob Buckets use the `somebuckets:filled` predicate and spawn-egg colors. Junk rendering delegates
each stored stack to Minecraft's `ItemRenderer`, preserving its model, tint, render passes, and
glint. Both loaders derive the opening and foreground from the active resource-pack mask. Fabric
uses separate south- and north-facing foreground geometry and mirrors stored-item depth for
left-hand display contexts. Fabric installs its texture-derived representative fluid-color resolver
on the client; the server-safe bridge uses the ordinary fallback bar color.

`work/`, `src/TODO.txt`, and `user-TODO.txt` are not runtime or build inputs.

## Maintenance invariants

- Common Java imports no Forge or Fabric APIs. Loader fluid types are converted at the module
  boundary rather than stored in `NBTUtil`.
- Every `VariableStackItem` self-enforces a `stacksTo(16)` baseline in its own common constructor, so
  the per-stack loader hook (Forge's `getMaxStackSize(ItemStack)` override, Fabric's mixin) is never
  load-bearing for a caller that forgets to pass the right base `Properties`.
- Common source is compiled independently in each loader. Compile-only dependencies must be present
  on every compilation that imports them; no runtime common project dependency is used.
- Exhausted content is normalized through `NBTUtil`, and every Source Bucket input and output path
  applies `SBPolicy`.
- Capability and Transfer API operations simulate before authorization and execution. Protection is
  checked against the position that will actually change or be accessed.
- World pickup uses the block's pickup contract. Generic output uses `FluidPlacement`. A Big/Huge
  Bucket's powder-snow take-then-place priority inverts when the player sneaks at an existing
  powder-snow block, so placement is reachable without a separate confirmation gesture.
- Forge cauldrons are implemented in `Cauldrons`. Fabric water/lava uses Transfer API storage, while
  powder snow uses `FabricCauldronInteractions` and `FabricFluidDispensers`.
- Dispenser block access uses the face adjacent to the dispenser. Non-fluid dispenser actions use a
  dispenser `ProtectionContext` and the loader fake player where an actual player object is needed.
- Every Junk/Trash intake passes through `JBItem.canStore`. Trash world lookup uses
  `TBItem.findFirstNearby` so one-item intake does not scan an entire pile.
- A Mob Bucket snapshot is removed only after successful world insertion. Required aquatic water is
  placed through `FluidPlacement`, and capturing a water-dwelling mob removes the water source block
  it occupies through the vanilla `BucketPickup` contract, so a release/recapture cycle nets no fluid.
- Transfer settlement preserves legal item stacks. Source Bucket machine-facing storage is capped at
  one bucket per public operation.
- Fabric fluid item layers use the stored variant's live atlas sprite; average sprite color is only
  for non-textured presentation. Fluid mask and representative-color caches invalidate on model
  reload.
- Stored Junk items use the normal loader `ItemRenderer`; predicates and model resource values match
  their Java constants.
