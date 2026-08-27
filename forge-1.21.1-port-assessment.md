# Forge 1.21.1 Port Assessment

## Purpose and scope

This document records the known technical shape of the Some Buckets Forge port from Minecraft
1.20.1 / Forge 47 to Minecraft 1.21.1 / Forge 52. It is an assessment, not an execution log or a
substitute for the staged plan in `forge-1.21.1-port-plan.md`.

The target build environment already syncs with Java 21, Minecraft 1.21.1, and Forge 52.1.16. The
port is Forge-first. Common code may change where Forge requires it, and temporary Fabric breakage is
acceptable. NeoForge and Fabric/Quilt repair are outside this port.

Forge has no FTB Chunks artifact for Minecraft 1.21.1. The Forge artifact therefore drops its direct
FTB Chunks integration. Fabric and NeoForge retain their own integration source and dependencies.

This is an unreleased mod. Preserving 1.20.1 worlds, item data, or binary compatibility is not a port
requirement. Any retained serialized layout should be retained because it is coherent for 1.21.1,
not to provide legacy support.

## Overall assessment

The dominant change is Minecraft's item data component system introduced in 1.20.5. Some Buckets
stores almost every gameplay state on an `ItemStack`, so the common state layer is the first major
dependency of the port. Forge's overall architecture is more stable: deferred registration, events,
fluid stacks, fluid handlers, capabilities, model events, and global loot modifiers still have
recognizable successors.

The likely port shape is:

1. Replace raw root item NBT access with a 1.21.1 component-backed state boundary.
2. Supply registry context where nested item stacks are encoded or decoded.
3. Apply Minecraft signature and identifier changes in common code.
4. Update Forge bootstrap, capabilities, recipes, loot, interactions, and rendering.
5. Port the existing GameTest suite and use it as the main unattended runtime check.

The behavior described by `player-view.md` remains the target unless Minecraft or Forge makes that
behavior impossible. A compile error is not permission to change player behavior.

## Minecraft changes that directly affect the project

### Item data components

Raw `ItemStack#getTag`, `getOrCreateTag`, and `setTag` are replaced by item data components. Vanilla
provides `minecraft:custom_data` for arbitrary persistent item data.

The smallest coherent first port is to keep the existing aggregate bucket schema inside the built-in
custom-data component rather than immediately registering several custom component types. This keeps
all state ownership in `NBTUtil`, avoids loader-specific component registration during the first
Forge port, and preserves the existing mutually exclusive payload model:

| State | Existing logical payload |
| --- | --- |
| Finite or source fluid | Mode, fluid id, amount, optional variant data |
| Milk | Mode and amount |
| Powder snow | Mode and block count |
| Mob storage | Mode, entity type, FIFO entity snapshots |
| Junk/Trash storage | FIFO item stacks and layout seed |

Component values must be treated as immutable. Mutators should copy or update the custom-data value
and then replace it on the stack. Callers must not retain a mutable compound borrowed from a stack.
Canonical empty-state rules remain in force: an exhausted payload loses its keys, an empty junk
store loses its list and layout seed, and an empty custom-data component is removed.

Registering a dedicated `somebuckets:bucket_state` component remains a possible later refinement,
but it is not part of the initial port unless the built-in custom-data component proves inadequate.

### Registry-aware item serialization

Nested `ItemStack` encoding and decoding now require registry lookup context. The current Junk and
Trash serialization methods do not accept a level, registry access, or `HolderLookup.Provider`.
Porting them will require one of the following coherent changes:

- pass a lookup provider into the state methods and through the relevant Junk/Trash call paths; or
- move nested-stack encoding behind a context-bearing operation while keeping simple state reads
  context-free.

The first option is the expected default because it makes registry dependence explicit. The final
shape should avoid global registry guesses and should not leak Forge types into common code.

Stack equality also changes from tag-based terminology to component-based terminology. Every
`isSameItemSameTags` use should become the 1.21.1 component-aware equivalent without weakening the
comparison.

### Item and interaction signatures

Expected direct migrations include:

- tooltip overrides use `Item.TooltipContext` instead of a nullable `Level`;
- item use duration receives the using entity;
- cauldron interaction methods use the item-specific interaction result;
- recipe APIs and item serialization use holder lookup providers;
- more registry-backed arguments and results use holders or resource keys;
- selected entity creation, spawn-egg, game-event, and save/load signatures changed.

These are mostly local adaptations, but `MBItem` deserves separate runtime attention because it
captures and restores complete entity snapshots.

### Identifiers

`ResourceLocation` constructors are private in 1.21.1. Existing construction sites must use the
appropriate factory:

- `fromNamespaceAndPath` for separate namespace and path;
- `parse` for a fully qualified string;
- `withDefaultNamespace` where Minecraft is deliberately the default.

This is a mechanical change in common registration ids, configuration parsing, loot policy, tags,
model properties, and client texture paths.

### Data and resource layout

Minecraft 1.21 singularizes vanilla data directories. The checked-in resources need at least these
moves:

- `data/somebuckets/recipes` to `data/somebuckets/recipe`;
- `data/somebuckets/tags/entity_types` to `data/somebuckets/tags/entity_type`.

Recipe result item stacks use the current item-stack form, including `id` rather than the old
`item` result field. `pack.mcmeta` must declare an appropriate 1.21.1 format.

Forge's own `data/forge/loot_modifiers` and `data/<modid>/loot_modifiers` locations are Forge data
locations and should not be singularized merely because vanilla registry directories changed.

### Rendering

Minecraft 1.21 substantially changed portions of the rendering and vertex APIs. Some Buckets does
not build arbitrary world meshes, but its Forge client code wraps baked models, transforms quads,
uses a block-entity-without-level renderer, and replaces a baked inventory model. Compile-level
signature changes are expected in that area.

## Forge 52 changes that directly affect the project

### Bootstrap and configuration

The Forge mod entry point should receive its loading context through constructor injection. The
context supplies the mod event bus and handles configuration registration. Static loading-context
access in `SomeBucketsForge` should be replaced.

The Forge entry point also still refers to the shared FTB Chunks adapter even though the Forge build
no longer compiles the compatibility source set. Removing that Forge-only registration path is an
intentional feature adjustment already established by the dependency decision.

### Item fluid capabilities

Forge still provides fluid capabilities and the existing fluid handler interfaces. The expected
change is primarily how an item stack receives its capability provider: the 1.20.1
`initCapabilities(ItemStack, CompoundTag)` override is no longer the target hook in Forge 52.

`ForgeBBItem`, `ForgeSBItem`, and `FluidProvider` should be adapted to the current item capability
attachment hook while preserving these invariants:

- the handler always acts on the owning stack;
- state persists through the common component-backed state layer, not capability-private storage;
- simulations do not mutate state;
- finite buckets remain bounded and Source Buckets remain infinite but type-stable;
- held and block transfers preserve legal stack settlement.

Block-entity capability lookup and the core `IFluidHandler` transaction model are expected to remain
close to the current design.

### Forge fluid values

Forge `FluidStack` remains the loader-native fluid value. The common `StoredFluid` boundary should
remain. The port must preserve optional fluid variant data when converting in both directions.

The Minecraft item-component migration does not by itself require converting Forge `FluidStack`'s
variant payload into a registered Some Buckets component. It can remain nested inside the common
bucket-state compound.

### Custom recipe ingredients

Forge custom ingredients have a codec-oriented serializer and registry-aware network buffer API.
Both custom ingredients require a real port rather than import renames:

- `EmptyBucketIngredient` must encode its configured item and remain component-sensitive;
- `SpawnEggIngredient` must continue matching all standard loaded spawn eggs;
- both must preserve the ids `somebuckets:empty_bucket` and `somebuckets:spawn_egg`;
- recipe JSON must load through Forge 52 without retaining obsolete serializer fields solely for
  the abandoned 1.20.1 implementation.

The Forge implementation should use the current Forge ingredient facility rather than inventing a
parallel recipe parser.

### Global loot modifiers

The global-loot-modifier serializer registry and modifier codec use the current map-codec form.
`AddBucketLootModifier` and `ModLootModifiers` need matching generic and builder changes.

The build-generated modifier resources should remain generated from
`common/src/main/resources/somebuckets/bucket_loot.json`. The manifest remains the single loot-policy
authority.

### Events and interactions

The following Forge-facing areas require compile and behavioral verification:

- player held-transfer interception and cancellation results;
- cauldron map registration and item-specific results;
- dispenser behaviors and fake-player use;
- fill-bucket event integration;
- furnace fuel event and crafting remainder behavior;
- block and item capability discovery;
- config load/reload events;
- client registration events.

The goal is adaptation, not replacement. Existing common gesture and settlement logic remains the
behavioral authority.

### Client models and colors

Forge retains custom geometry loaders, baked-model events, baked-model wrappers, item client
extensions, and dynamic fluid-container models, but method signatures changed.

The custom `NbtFluidContainerModel` may no longer be necessary. The current Forge fluid-container
model appears designed to leave runtime tinting to an item color handler, and Some Buckets already
has a stack-aware handler. During the client stage, the preferred decision order is:

1. verify whether Forge's standard fluid-container loader preserves stack-specific variant color;
2. if it does, remove the redundant custom geometry wrapper and update model JSON;
3. if it does not, port the custom wrapper with the smallest current Forge model API surface.

Removing the custom loader is not authorized merely to make compilation easier; it requires
equivalent rendering semantics supported by documentation and tests where possible.

The Junk Bucket renderer remains a separate concern because it draws stored item models protruding
from the bucket and cannot be replaced by the fluid-container model.

## Existing verification assets

The repository already contains a substantial automated GameTest suite:

- shared scenario bodies under `common/src/gametest/java`;
- Forge discovery and Forge-specific tests under `forge/src/gametest/java`;
- a generated shared structure fixture;
- Forge tests for state, recipes, loot, capabilities, transfers, cauldrons, fuel, automation,
  protection, presentation, Mob Buckets, and storage buckets;
- a dedicated Forge `runGameTestServer` configuration.

These tests are expected to require API porting before they can validate production behavior. Tests
must not be deleted, weakened, or made less specific merely to obtain a green run.

## Risk ranking

| Risk | Area | Reason |
| --- | --- | --- |
| Highest | Component-backed bucket state | Central to every item family and stack-size rule |
| High | Nested item-stack serialization | Requires registry context across existing call boundaries |
| High | Mob snapshot runtime behavior | Entity save/load is complex and stateful |
| Medium-high | Custom ingredients and recipe data | Codec and data-format migration |
| Medium-high | Forge capability attachment and transfer settlement | Automation and multi-count stacks depend on it |
| Medium | Client model and renderer port | Compile changes plus limited headless visual coverage |
| Medium | Cauldrons, dispensers, events, and fuel | Several Forge/vanilla signatures and side effects |
| Medium-low | Global loot modifiers | Small implementation but codec and generated-data coupling |
| Low | Bootstrap, identifiers, directory moves, and metadata | Local and mostly mechanical |

## Non-goals

- Repairing Fabric or validating Quilt during the Forge port.
- Implementing NeoForge runtime code.
- Supporting Forge FTB Chunks on 1.21.1.
- Migrating unreleased 1.20.1 saves or item data.
- Replacing Gradle, Loom, Architectury, the JDK, the IDE, or the operating system.
- Introducing a new component architecture, networking layer, test framework, or abstraction layer
  unless a planned stage proves the existing approach cannot support Forge 1.21.1.
- Redesigning player-visible bucket behavior to accommodate a compile error.

## Questions deliberately deferred to evidence

These decisions should be made during their named stage from compiler output, Forge documentation,
and focused tests:

- the exact registry-context plumbing for Junk/Trash nested item stacks;
- the current Forge 52 item capability provider hook and provider lifetime;
- the current registration path for Forge custom ingredient serializers;
- whether the standard Forge fluid-container model fully replaces `NbtFluidContainerModel`;
- which existing entity snapshot calls need adaptation beyond signature changes.

If any of these requires choosing new player behavior, a new persistence design, or a new build
dependency, unattended work stops for user direction.
