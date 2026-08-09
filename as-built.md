# Some Buckets As-Built Orientation

This is an orientation and maintenance guide to the repository as it exists. It identifies the
build structure, loader status, major subsystems, persistent data, and design boundaries a
maintainer should understand before making a change. It is deliberately incomplete:
`player-view.md` describes current player-facing behavior, and the code is authoritative when
either document is wrong.

## Project at a glance

Some Buckets is an unreleased Minecraft 1.20.1 mod for Java 17 in a three-module Forge/Fabric
workspace. Its mod id is `somebuckets`; its root package is
`com.github.crittscott.somebuckets`.

The functional implementations target Forge 47.4.0 and Fabric API 0.92.2. Forge is the established,
in-game-tested implementation. Fabric now registers the complete mod surface and mirrors the same
player-facing behavior through loader-specific adapters; its build and in-game parity still require
user verification.

Each loader adds six unstackable items and one creative tab. The mod adds no
blocks, block entities, menus, packets, commands, or saved-world objects. Bucket contents live
entirely on the item stack. Each loader supplies a server configuration feeding the shared Source
Bucket allowlist policy.

| Registry name | Role |
| --- | --- |
| `big_bucket_8` | Finite eight-unit fluid, milk, or powder-snow container |
| `big_bucket_64` | Finite sixty-four-unit version |
| `source_bucket` | Infinite source and sink for one allowed content |
| `junk_bucket` | FIFO storage for nine item stacks |
| `trash_bucket` | One-stack storage with destructive replacement |
| `mob_bucket` | FIFO storage for eight mobs of one entity type |

The project does not preserve old development data formats. If an item schema changes before
release, migrate or replace the data instead of adding compatibility branches.

## Build and module layout

The root build includes `common`, `forge`, and `fabric`. It uses Gradle 8.11 and applies the same
Mojang official mappings plus Parchment `2023.09.03-1.20.1` to all three compilations.
Architectury Loom is not used.

| Module | Tooling | Current responsibility and status |
| --- | --- | --- |
| `common` | ModDevGradle LegacyForge in MCP/common mode | Shared state, item behavior, policy, protection, interaction helpers, and common assets/data; not a distributable mod |
| `forge` | ModDevGradle LegacyForge | Complete and playable Forge implementation, Forge metadata, and Forge-only GameTests |
| `fabric` | Fabric Loom | Fabric registrations, Transfer API integration, lifecycle/config, client presentation, mixin, and metadata |

The configured toolchain versions are ModDevGradle LegacyForge 2.0.77, Fabric Loom 1.9.2, Fabric
Loader 0.16.9, Fabric API `0.92.2+1.20.1`, and Architectury API 9.2.14.

`buildSrc` supplies two convention plugins. `multiloader-common` sets Java 17, shared repositories,
and compilation defaults. `multiloader-loader` makes each loader compile the raw Java source from
`common` and merge the resources from `common` into its own output. The loader JARs therefore
contain the common classes directly. There is no separate common runtime JAR, and loader modules
must not add `implementation project(":common")`.

Compile-only dependencies do not follow the raw common source into a loader compilation. Each
loader must declare any library needed by the common source on its own compile classpath. Runtime
dependencies must also be represented accurately in that loader's metadata.

Shared recipes, tags, translations, most models, textures, sounds, and `pack.mcmeta` live under
`common/src/main/resources`. The fluid-container and Junk Bucket inventory models are split by
loader because Forge uses its geometry/BEWLR path while Fabric uses generated/builtin models and a
Fabric dynamic renderer. Each loader expands its own metadata and the shared `pack.mcmeta` during
resource processing.

Architectury API is declared as a required runtime dependency in both loader descriptors. The
Forge entrypoint currently uses `Platform.isModLoaded` to guard optional FTB Chunks integration.
FTB Chunks remains compile-only and optional in loader metadata.

### Loader entrypoints and boundaries

`SomeBucketsForge` is the real entrypoint. It registers the Forge server config, items, sounds,
creative tab, custom recipe ingredients, dispenser behaviors, cauldron adapters, and the optional
FTB Chunks protection provider. Forge event subscribers provide held-container transfer and furnace
fuel behavior, and Forge client events install the models, predicates, colors, and renderers.

`SomeBucketsFabric` installs the shared platform bridges, loads the Fabric JSON server config,
registers custom ingredients, items, sounds, Transfer API item storage, the creative tab, dispenser
behavior, and optional FTB Chunks protection. `SomeBucketsFabricClient` owns Fabric predicates,
colors, model loading, and Junk Bucket rendering. A Fabric-only furnace mixin supplies the dynamic
NBT-sensitive fuel checks that Forge receives through its fuel event.

`common/util/NBTUtil` now owns the shared persistent schema without importing a loader fluid type.
`StoredFluid` carries a fluid, mB amount, and optional variant tag. Forge converts at its boundary
through `ForgeFluidStacks`; Fabric converts to droplets and `FluidVariant` at its Transfer API
boundary. Item behavior, non-fluid dispenser behavior, protection, and vanilla world placement are
shared; registration, fluid transactions, config, recipes, optional integrations, and client hooks
remain loader-owned.

## Code map

| Area | Owns |
| --- | --- |
| `common/.../SomeBuckets` | Shared mod id and logger |
| `common/.../config/SBPolicy` | Loader-neutral, immutable Source Bucket allowlist snapshot |
| `common/.../protection/` | Permission contexts, action categories, provider registry, and vanilla permission checks |
| `common/.../fluid/FluidPlacement` | Loader-neutral vanilla-style world fluid placement |
| `common/.../util/` | Loader-neutral item NBT and `StoredFluid` representation |
| `common/.../item/` | Shared behavior for all six bucket items |
| `common/.../platform/BucketOperations` | Loader-installed fluid/world interaction seam used by shared items |
| `common/.../interaction/NonFluidDispensers` | Shared Mob/Junk/Trash dispenser behavior |
| `common/src/main/resources/` | Recipes, tags, translations, models, textures, sounds, and `pack.mcmeta` |
| `forge/.../SomeBucketsForge` | Forge entrypoint and lifecycle/bootstrap coordination |
| `forge/.../register/` | Forge item, sound, and creative-tab registration |
| `forge/.../config/ServerConfig` | Forge server configuration and refresh of `SBPolicy` |
| `forge/.../item/` | Thin Forge shells for capabilities, crafting remainders, and custom renderers |
| `forge/.../util/ForgeFluidStacks` | Forge `FluidStack` conversion at the shared-state boundary |
| `forge/.../fluid/*FluidHandler` | Forge item fluid capabilities |
| `forge/.../fluid/*FluidLogic` | Big/Source Bucket fluid operations |
| `forge/.../fluid/FluidPickup` | Forge/vanilla world fluid pickup adapters |
| `forge/.../interaction/Cauldrons` | Physical cauldron transitions and their accounting |
| `forge/.../interaction/Transfers` | Held-item and Forge block-capability fluid transactions |
| `forge/.../interaction/Dispensers` | All Forge dispenser behavior registration and selection |
| `forge/.../event/` | Forge event subscribers for cross-hand transfers and furnace fuel |
| `forge/.../protection/` | Forge bucket event bridge and dispenser fake-player identity |
| `forge/.../compat/ftbchunks/` | Optional Forge FTB Chunks provider |
| `forge/.../crafting/` | Forge registration of the two custom recipe ingredients |
| `forge/.../client/` | Forge models, colors, predicates, and Junk/Mob rendering |
| `forge/.../gametest/` | Forge-only development tests, excluded from the release JAR |
| `fabric/.../SomeBucketsFabric` | Fabric common bootstrap and registration coordination |
| `fabric/.../fluid/` | Fabric Transfer API storage backed by shared NBT |
| `fabric/.../platform/FabricBucketOperations` | Held, block-storage, world-fluid, powder, and cauldron transactions |
| `fabric/.../interaction/FabricFluidDispensers` | Fabric Big/Huge/Source dispenser selection |
| `fabric/.../config`, `crafting`, `compat` | Fabric config, custom ingredients, and FTB Chunks adapter |
| `fabric/.../client` | Fabric predicates, colors, dynamic Junk renderer, and model loading |
| `fabric/.../mixin` | Dynamic furnace fuel admission and burn-duration hooks |

The functional layering is: `NBTUtil` owns representation; shared item classes choose an operation;
loader `BucketOperations` perform fluid transactions; shared policy, protection, placement, and
non-fluid interaction services enforce common rules. Mutations are server-authoritative.

## Persistent item state

Both loader implementations read and write the same item-stack schema.

Content-bearing buckets use a string `Mode` discriminator. Missing or unknown modes read as empty.

| Mode | Additional keys | Items |
| --- | --- | --- |
| `none` | none | Empty or unassigned content buckets |
| `fluid` | `FluidStack` | Big, Huge, Source |
| `milk` | `Amount` | Big, Huge, Source |
| `powder_snow` | `Powder` | Big, Huge |
| `entity` | `EntityType`, `Entities` | Mob |

Fluid stack NBT is preserved, including mod-defined payload data. Finite fluid and milk removal goes
through `NBTUtil.drainFiniteContent`. Empty content must normalize back to `none`, and empty
Some Buckets tags are removed without disturbing unrelated item NBT.

Junk and Trash Buckets store serialized item stacks in `JunkItems`. Capacity counts list entries,
not individual items. Compatible stacks merge before consuming another entry. Every intake route
must enforce `Item.canFitInsideContainerItems` through `JBItem.canStore`.

Mob Buckets store an entity type and snapshots created without the entity id. Release restores the
saved state and UUID, assigning a new UUID only if the old one is already in use by a loaded entity.

Big, Huge, and Source Buckets expose one loader-native fluid store. Big and Huge are finite stores of
8,000 and 64,000 mB. Source reports one bucket per public operation but does not lose its assigned
content. Forge exposes `IFluidHandlerItem`; Fabric exposes `Storage<FluidVariant>` and converts exact
whole mB at 81 droplets per mB. Milk and powder snow are not exposed as fluids.

## Important system boundaries

### Source Bucket policy

On Forge, `serverconfig/somebuckets-server.toml` contains `sourceBucket.allowedContents`. Fabric uses
`config/somebuckets-server.json` with `allowedContents`. Both default to water, lava, and the special
non-fluid id `somebuckets:milk`.

Each loader passes configured ids to the loader-neutral `SBPolicy`, which resolves them into an
immutable snapshot. Forge refreshes on its config load/reload events; Fabric loads during mod
initialization and again when a server starts.

The allowlist is checked whenever a Source Bucket acquires, supplies, places, consumes, or destroys
content. Removing an assigned content leaves its NBT and name intact but makes the bucket inert until
reset. The policy never restricts Big or Huge Buckets. Unknown ids are ignored and logged.

### Protection

Protected operations carry a `ProtectionContext`, an exact target, and an action such as block edit,
fluid edit, entity interaction, or entity release. Feasibility is established first; permission is
checked before mutation. Player contexts contain the real player and actual hand. Automation
contexts identify the dispenser position without putting a Forge fake-player type in common code.

`ClaimProtections` is now a loader-neutral provider registry. During Forge common setup,
`SomeBucketsForge` checks `Platform.isModLoaded("ftbchunks")` and then registers the Forge-specific
`FtbChunksProtection` provider. That provider supplies the stable fake player `[SomeBuckets]` for
automation checks. Open Parties and Claims compatibility comes from its ordinary Forge hooks and
dispenser wrapper. Player Junk/Trash operations also pass through the mod's protection layer. A
denial must leave the intended bucket, block, fluid, cauldron, or entity mutation undone.

**Known Forge limitation:** no claim mod other than FTB Chunks has a bundled provider. Player
actions are still covered by `level.mayInteract`/`player.mayUseItemAt` and Forge's ordinary
`FillBucketEvent`/`BlockEvent.EntityPlaceEvent`/`PlayerInteractEvent.EntityInteract` hooks, which
most Forge claim mods already use. Automation-driven `ENTITY_INTERACT`/`ENTITY_RELEASE` operations
have no equivalent general event fallback. Under another claim mod, dispenser feeding, mob
capture/release, and item vacuum/ejection may therefore be undenied. Adding coverage means writing
and registering another `ClaimProtectionProvider` for that loader.

Fabric registers an equivalent FTB Chunks provider when the mod is present. Fabric has no general
equivalent to Forge's `FillBucketEvent`, so compatibility with other claim mods depends on a future
`ClaimProtectionProvider`; vanilla player restrictions still apply.

### Vanilla and loader integration

Forge world fluid pickup uses the block's own `IFluidBlock` or `BucketPickup` contract; it does not
replace the block with air. Shared placement follows vanilla target selection, waterlogging,
replacement, and ultra-warm evaporation rules. Player placement may fall through to the neighboring
block, while dispenser placement is restricted to the block directly in front.

Forge player world-bucket paths post `FillBucketEvent` against the block the operation will actually
change. Successful operations emit the corresponding game event and award vanilla-style statistics
and criteria where vanilla has an equivalent. Cauldron transitions are physically implemented in
`Cauldrons`, even though player, Source Bucket, and dispenser selection paths remain separate.

Block and held-item fluid transfers use Forge capabilities or Fabric Transfer API and simulate
before executing. Big and Huge Buckets are ordinary finite stores. Source Buckets have special
hand-to-hand pumping so they can fill a destination in one gesture; machines still see one bucket
per operation. Fabric refuses sub-mB transfers so the shared saved amount remains exact.

## Behavior landmarks

The full observable Forge behavior belongs in `player-view.md`. These points explain the main
internal families:

- Big and Huge Buckets share finite-content logic for fluids, milk, and powder snow. Empty buckets
  take, full buckets place, and partial buckets try to take compatible content before placing.
- Source Buckets reuse much of the fluid machinery but represent an allowed, permanent assignment.
  They are infinite both as a source and as a compatible sink.
- Junk and Trash Buckets share inventory gestures, animal feeding, ejection, dispenser structure,
  and overridable intake/eject sound hooks. Junk is a nine-entry FIFO; Trash is a one-entry
  destructive replacement container.
- Mob Buckets store full entity snapshots, restrict a load to one exact entity type, and remove a
  snapshot only after the entity successfully enters the world. Aquatic release delegates required
  water placement to the shared `FluidPlacement` class.
- Every item has loader-registered dispenser behavior and remains in the dispenser. Selection
  rules are family-specific, but the underlying mutations reuse player-operation primitives where
  practical.

Crafting uses two custom ingredients: `somebuckets:empty_bucket` rejects filled Big or Source
Buckets, and `somebuckets:spawn_egg` accepts spawn eggs. The shared recipe carries Forge's `type`
and Fabric's `fabric:type`; each loader registers its native serializer. `work/` is reference
material only.

## Client presentation

Each loader owns its client lifecycle registration. Big, Huge, and Source Buckets use the
`somebuckets:bb_content` predicate to select
empty, fluid, milk, or powder-snow presentation. Forge fluid models use the actual stack's still
texture and tint. Fabric uses the shipped fluid mask colored from the still texture's average and
the variant tint. Both loaders preserve NBT-dependent variant colors in models and bars.

Mob Buckets use `somebuckets:filled` and spawn-egg colors. Junk Bucket rendering delegates each
stored stack to Minecraft's normal `ItemRenderer`, preserving custom models, tint, render passes,
and glint. Forge masks icons to its resource-driven mouth geometry; Fabric draws them between two
vessel passes so opaque bucket pixels cover the contents.

## Development and packaging boundaries

The Forge module configures client and dedicated-server development runs. The earlier GameTest and
data-generation run configurations have not been recreated under ModDevGradle LegacyForge. The
GameTest Java sources remain in the Forge module and are excluded from the release JAR.

Fabric has one mixin configuration. `AbstractFurnaceBlockEntityMixin` supplies stack-sensitive fuel
slot admission and burn time for lava-filled buckets; Fabric Item API supplies the stack-aware
remainder hook. Forge continues to use its fuel event and requires no mixin.

Do not distribute a common JAR. Forge remains the verified behavior baseline; the Fabric output is
implemented but should not be released until its build, dedicated-server startup, and in-game parity
matrix have been run by the user.

## Maintenance checklist

Before adding a new mutation, transfer path, or loader implementation, check the relevant items:

- Decide explicitly whether the code is loader-neutral or loader-specific. Common code must not
  import Forge or Fabric APIs.
- Remember that common source is compiled independently inside each loader. Redeclare its
  compile-only dependencies where needed, and do not add a runtime common project dependency.
- Normalize exhausted content and use the shared `NBTUtil` rather than editing content NBT ad hoc.
- Keep loader fluid types outside `NBTUtil`; convert through `ForgeFluidStacks` or Fabric
  `FluidVariant` adapters at the loader boundary.
- Enforce `SBPolicy` at every new Source Bucket input or output boundary on every loader.
- Simulate capability or storage transactions before authorization and execution.
- Check the actual mutation target with the correct protection context and action.
- Use block-owned fluid pickup and placement contracts.
- Emit the matching game event and vanilla-style statistic or criterion where applicable.
- Play sound feedback unconditionally on both sides, not only `if (!level.isClientSide)`; a
  server-only call is silently excluded from the acting player's own broadcast.
- Keep Forge cauldron transitions in `Cauldrons`; Fabric cauldrons are Transfer API storage except
  for the explicit powder-snow dispenser transition.
- Keep dispenser hits and sided fluid access aimed at the face adjacent to the dispenser.
- Route every Junk/Trash intake through `JBItem.canStore`; use `TBItem.findFirstNearby` for Trash
  world lookup so a one-item operation does not scan an entire pile.
- Remove a Mob Bucket snapshot only after successful insertion, and delegate aquatic water placement
  to `FluidPlacement`.
- Preserve legal item stacks and in-place mutation when settling transfers.
- Preserve the Source Bucket's public one-bucket-per-operation machine-transfer behavior on both
  loader APIs.
- Keep each loader's client lifecycle registration centralized, and keep Java predicate values
  synchronized with the shared resource models.
- Continue delegating stored Junk items to the loader's normal `ItemRenderer` path rather than
  approximating their sprites.
- Treat `player-view.md` as the Forge behavior baseline; document intentional loader differences
  instead of allowing silent drift.

The functional Forge implementation intentionally has one server config and no networking, JEI
integration, loot tables, blocks, or block entities. `src/TODO.txt` is exploratory and may be stale.
