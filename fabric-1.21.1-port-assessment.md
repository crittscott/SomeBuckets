# Fabric 1.21.1 Port Assessment

## Purpose and scope

This document records the known technical shape of the Some Buckets Fabric port from Minecraft
1.20.1 / Fabric Loader 0.15-era to Minecraft 1.21.1 / Fabric Loader 0.19.3 with Fabric API
0.116.15+1.21.1. It is an assessment, not an execution log or a substitute for the staged plan in
`fabric-1.21.1-port-plan.md`.

The target build environment already syncs with Java 21, Minecraft 1.21.1, Fabric Loader 0.19.3, and
Fabric API 0.116.15+1.21.1. `fabric/build.gradle` and `gradle.properties` are already on the 1.21.1
baseline; the build environment is not part of this port.

The port is Fabric-first. Common code may change where Fabric requires it, but the completed Forge
1.21.1 port must not regress: any change under `common/src/main` or `common/src/gametest` is
followed by a Forge production compile before the stage closes. NeoForge runtime work remains out of
scope. Quilt is covered by the Fabric artifact and needs no separate work.

This is an unreleased mod. Preserving 1.20.1 worlds, item data, or binary compatibility is not a
port requirement.

## Overall assessment

The Forge 1.21.1 port already carried the loader-neutral layers to 1.21.1:

- `common/src/main` compiles for Minecraft 1.21.1. The item data component migration, registry-aware
  nested item-stack serialization, `ResourceLocation` factories, `Item.TooltipContext`, use-duration
  signatures, component-aware stack equality, relocated dispenser `BlockSource`, `ItemInteractionResult`
  where common touches cauldrons, and the Mob Bucket entity save/load path are done.
- `NBTUtil` is the sole state-schema owner and now stores the aggregate bucket schema inside the
  built-in `minecraft:custom_data` component, maintains the vanilla `MAX_STACK_SIZE` data component
  at its write boundary, and encodes nested Junk/Trash stacks with explicit `HolderLookup.Provider`
  context.
- `common/src/gametest` shared scenario bodies and assertions are ported to the 1.21.1 GameTest,
  advancement, game-event, and registry APIs.

The Forge port explicitly left Fabric breakage unrepaired. So the Fabric port is:

1. Reconcile `common` under the Fabric transform and consumers, without regressing Forge.
2. Port `fabric/src/main`: entrypoint and registration, Transfer API integration, custom
   ingredients, loot injection, cauldron and dispenser interactions, held-transfer events, mixins,
   configuration, and client rendering.
3. Port `fabric/src/gametest`: the Fabric discovery wrappers and Fabric-specific tests. The shared
   scenarios compiled into this source set are already ported.
4. Stabilize the Fabric GameTest server run.
5. Package and reconcile the orientation documents.

The behavior described by `player-view.md` remains the target unless Minecraft or Fabric makes it
impossible. A compile error is not permission to change player behavior.

## What the completed Forge port already provides

| Concern | State |
| --- | --- |
| Component-backed bucket state in `NBTUtil` | Done in common |
| Registry-aware nested Junk/Trash stack codecs | Done in common |
| `ResourceLocation` factories in common | Done |
| Tooltip, use-duration, equality, game-event signatures in common | Done |
| Mob Bucket capture/restore against 1.21.1 entity APIs | Done in common |
| Vanilla `MAX_STACK_SIZE` component as the variable-stack mechanism | Done in common |
| Shared GameTest scenario bodies | Done in `common/src/gametest` |
| `data/somebuckets/recipe` and `tags/entity_type` singular directories | Done in shared resources |
| Structure-loot manifest `somebuckets/bucket_loot.json` and `BucketLootTables` | Unchanged, shared |

The Fabric port consumes these rather than repeating them.

## Minecraft changes that reach Fabric code

### Item data components — remaining raw NBT sites in the Fabric module

`common` no longer touches raw stack tags, but the Fabric module still does:

- `fabric/.../fluid/FabricBucketStorage` `StackBackend` uses `stack.setTag`, `updated.hasTag`, and
  `updated.getTag` to copy state between a working stack and the real stack inside a transaction, and
  in `createSnapshot`/`readSnapshot`.
- `fabric/.../platform/FabricBucketOperations#tryHeldTransfer` copies the context-updated bucket back
  onto the caller's stack with `bucket.setTag(updatedBucket.hasTag() ? updatedBucket.getTag().copy()
  : null)`.

These must move to component-based copying. The state that matters lives entirely in
`minecraft:custom_data` and `MAX_STACK_SIZE`; the smallest coherent change is a shared helper that
copies the whole component set (or those two components) from one stack to another, used by both the
snapshot participant and the held-transfer settle-back. The transaction snapshot semantics must be
preserved: `SnapshotParticipant` still snapshots and restores the real stack's count and components.

### `StoredFluid` to `FluidVariant` conversion

This is the port's central Fabric-specific design question. `StoredFluid` in common carries an
optional variant `CompoundTag` (`variantTag()`), consistent with the Forge `FluidStack`-style
schema. The Fabric module converts it with `FluidVariant.of(fluid, storedCompoundTag)` and reads it
back with `resource.copyNbt()` in `FabricBucketStorage`, `FabricBucketOperations`, and
`FabricFluidContainerModel`.

In 1.21.1, `FluidVariant`'s payload is component-based (a `DataComponentPatch` / component-change
set), not a `CompoundTag`. The two coherent options:

- keep `StoredFluid` carrying a `CompoundTag` and convert `CompoundTag <-> DataComponentPatch` at the
  Fabric boundary only (a single Fabric conversion helper, mirroring `ForgeFluidStacks`); or
- change `StoredFluid` to carry a loader-neutral component-patch representation and convert on both
  loader sides.

The first option is the expected default: it keeps the change inside the Fabric module, does not
disturb the Forge conversion that already works, and matches the existing "convert loader-native
fluid values only at loader boundaries" invariant. The round trip must preserve modded variant data
in both directions and must survive a Transfer API transaction.

### Identifiers

`fabric/.../crafting/FabricEmptyBucketIngredient` and `FabricSpawnEggIngredient`,
`FabricDispenserFakePlayer`, `FabricFluidContainerModel`, and any other `new ResourceLocation(...)`
sites in the Fabric module must use `ResourceLocation.fromNamespaceAndPath`, `parse`, or
`withDefaultNamespace`.

### Cauldron and interaction result types

`fabric/.../interaction/FabricCauldronInteractions` registers into `CauldronInteraction.EMPTY` and
`CauldronInteraction.POWDER_SNOW` and returns `InteractionResult.sidedSuccess(...)`. In 1.21.1 the
cauldron maps are wrapped (`CauldronInteraction.InteractionMap`, reached through an accessor) and the
interaction functions return `ItemInteractionResult`. This is the same change the Forge port made in
its Stage 5C3; the Forge status file records `InteractionMap#map` and the `ItemInteractionResult`
constants as the working shape.

### Loot functions

`fabric/.../loot/FabricBucketLoot` builds a pool entry with `SetNbtFunction.setTag(CompoundTag)` from
`NBTUtil.createPowderSnowTag(...)`. `SetNbtFunction` is replaced in 1.20.5+ by component-oriented
loot functions; the Huge Powder Snow Bucket reward should use the custom-data loot function
(`SetCustomDataFunction.setCustomData`, or the current equivalent) wrapping the same compound. If a
`CustomData` value is needed, wrap the `CompoundTag` at the call site; do not push `CustomData` into
`NBTUtil`'s public shape unless a later stage proves it necessary.

### Data and resource layout

The shared `data/` directories were already singularized by the Forge port. The Fabric module ships
only `assets/somebuckets/models/item/*.json` (client models) and its two `fabric.mod.json` files,
none of which are affected by the vanilla registry-directory renames. `pack.mcmeta` for shared
resources is owned by the Forge/common resource tree; verify the Fabric production and GameTest
`fabric.mod.json` dependency ranges and the mixin `compatibilityLevel` instead.

### Rendering

Minecraft 1.21 changed portions of the model, vertex, and item-rendering APIs. The Fabric client
wraps a baked model, transforms and emits quads through the Fabric Renderer API, builds `BakedQuad`s
from a raw int vertex array, uses `FluidVariantRendering`, registers through the Fabric model-loading
plugin, and provides a builtin dynamic item renderer for the Junk Bucket. Compile-level signature
changes are expected across all of `fabric/.../client`.

## Fabric API changes that directly affect the project

### Loader bootstrap and metadata

`SomeBucketsFabric` (`ModInitializer`) and `SomeBucketsFabricClient` (`ClientModInitializer`) entry
points are stable in shape. Expected work is limited to:

- `somebuckets.fabric.mixins.json` `compatibilityLevel` from `JAVA_17` to `JAVA_21`, and its `mixins`
  list if `ItemStackMixin` is removed;
- confirming `fabric.mod.json` dependency expressions still resolve (`minecraft`, `fabricloader`,
  `java`, `fabric-api`); the values flow from `gradle.properties`, which is already on 1.21.1;
- the GameTest `fabric.mod.json` entrypoint and `somebuckets-gametest` Loom mod entry.

### Transfer API

The `fabric-transfer-api-v1` core is close to unchanged: `Storage<FluidVariant>`,
`SingleSlotStorage`, `ContainerItemContext`, `Transaction`, `SnapshotParticipant`, `StorageUtil.move`,
`StorageUtil.findExtractableResource`, `FluidStorage.ITEM` / `FluidStorage.SIDED`,
`InventoryStorage.of`, and `FluidConstants.BUCKET` are expected to survive with at most minor
signature adjustments. The bulk of `FabricBucketOperations`' transaction logic should port cleanly.
The exceptions are the `FluidVariant` payload change above and the raw-NBT stack copies.

`FluidVariantAttributes` (`getName`, `getFillSound`, `getEmptySound`) and
`net.fabricmc.fabric.api.entity.FakePlayer` are retained; the Fabric fake player used for dispenser
claim checks stays. Unlike the Forge port, Fabric keeps both its fake player and its FTB Chunks
integration (`ftb-chunks-fabric:2101.1.21` is a real 1.21.1 artifact and is already a
`modCompileOnly` dependency; `common/src/compat/java` remains on the Fabric source set).

### Custom recipe ingredients

`FabricEmptyBucketIngredient` and `FabricSpawnEggIngredient` implement
`net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient` with a `CustomIngredientSerializer`
that today uses `read/write(JsonObject)` and `read/write(FriendlyByteBuf)`. In the 1.21 Fabric API,
the serializer is codec-oriented: a `MapCodec<T>` for data plus a registry-aware packet/stream codec
(`RegistryFriendlyByteBuf`). Both ingredients need a real port, mirroring the Forge Stage 5A change:

- preserve the ids `somebuckets:empty_bucket` and `somebuckets:spawn_egg`;
- `FabricEmptyBucketIngredient` still encodes its configured item and stays component-sensitive
  (`NBTUtil.isEmptyBucket`);
- `FabricSpawnEggIngredient` still matches all standard loaded spawn eggs;
- recipe JSON loads through Fabric API without retaining obsolete serializer fields.

### Loot table events

`fabric/.../loot/FabricBucketLoot` registers `LootTableEvents.MODIFY` with a
`(resourceManager, lootManager, id, tableBuilder, source)` lambda and calls `source.isBuiltin()` and
`BucketLootTables.rewardsFor(id)`. In the 1.21 Fabric API the callback signature changed: the loot
table identity is a `ResourceKey<LootTable>`, and the parameter set is reduced/reordered (a
`HolderLookup.Provider` is available, the `ResourceManager`/`LootDataManager` parameters are gone).
`rewardsFor` takes a `ResourceLocation`, so pass the key's location. Confirm the current
`LootTableSource` predicate name for "built-in".

`BuiltInRegistries.ITEM.get(ResourceLocation)` in `FabricBucketLoot` and `FabricEmptyBucketIngredient`
still returns the item directly in 1.21.1; the `Optional`/`Holder` return change is later. Low risk,
but verify.

### Content registries and fuel

`fabric/.../mixin/AbstractFurnaceBlockEntityMixin` injects into `AbstractFurnaceBlockEntity#isFuel`
and `#getBurnDuration` to give a lava-filled Big/Huge Bucket the same finite-fuel behavior Forge gets
from its fuel event. The injected method targets and descriptors must be re-verified against the
1.21.1 Parchment mappings; the larger data-driven `FuelValues` furnace refactor lands after 1.21.1,
so the current mixin shape is expected to still be viable, but the `getBurnDuration` signature is the
most likely to have moved. `BucketFuel.isLavaFuel` and `FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS`
are common and already ported.

### Variable stack size

`fabric/.../mixin/ItemStackMixin` injects `ItemStack#getMaxStackSize` to return
`VariableStackItem#variableMaxStackSize`. The Forge port replaced its equivalent per-stack override
with the vanilla `MAX_STACK_SIZE` data component, maintained by `NBTUtil` at every write. If that
component is authoritative on Fabric too — it should be, since it is written in common — this mixin is
redundant and should be removed along with its `somebuckets.fabric.mixins.json` entry. Removing it is
preferred over porting it, provided a GameTest confirms empty-versus-filled stack sizes on Fabric.

### Model loading and renderer API

`SomeBucketsFabricClient`, `FabricFluidContainerModel`, `FabricJunkBucketRenderer`, and
`FabricClientFluidColors` use `ModelLoadingPlugin` / `context.modifyModelAfterBake()`, the Fabric
Renderer API (`Renderer`, `RenderMaterial`, `QuadEmitter`, `RenderContext`, `FabricBakedModel`),
`BuiltinItemRendererRegistry` / dynamic item rendering, `ColorProviderRegistry`, `ItemProperties`,
`FluidVariantRendering`, and a two-argument `ModelResourceLocation`. Expect signature changes in the
model-modifier callback, the `FabricBakedModel` quad-emission method, the `BakedModel` interface
members, the `BakedQuad` vertex-array construction, and the dynamic renderer interface. Also verify
`SpawnEggItem.byId` and `SpawnEggItem#getColor` (spawn-egg colors moved toward components). The
custom mask-clipping fluid layer is doing real geometry work and cannot be replaced by a stock model;
it must be ported, not dropped.

## Existing verification assets

- `fabric/src/gametest/java/.../GameTestSupport.java` — Fabric equivalent of the shared support
  helper.
- `fabric/src/gametest/java/.../*GameTests.java` — Fabric discovery wrappers plus Fabric-specific
  coverage: `TransferGameTests`, `BlockCapabilityGameTests`, `CauldronGameTests`,
  `RecipeAndFuelGameTests`, `LootGameTests`, `ProtectionGameTests`, `AutomationGameTests`,
  `PresentationGameTests`, `StateGameTests`, `StorageBucketGameTests`, `BBGameTests`, `SBGameTests`,
  `MBGameTests`.
- `fabric/src/gametest/resources/fabric.mod.json` — GameTest entrypoint and `somebuckets-gametest`
  Loom mod.
- The `gameTestServer` run in `fabric/build.gradle`, which clears `fabric/run/world` before launch.
- The shared structure fixture, decoded into Fabric GameTest resources by the root build.

The shared scenario bodies compiled into this source set are already ported. The Fabric-specific test
classes are expected to need API porting. Tests must not be deleted, weakened, or made less specific
to obtain a green run.

## Risk ranking

| Risk | Area | Reason |
| --- | --- | --- |
| Highest | `StoredFluid` to `FluidVariant` component-payload conversion | Central to every Fabric fluid path and to variant tint; must round-trip modded data |
| High | Fabric client model, renderer, and dynamic item renderer port | Broad signature change with limited headless coverage |
| High | Raw-NBT stack copies in the Transfer API layer | Transaction correctness depends on exact snapshot/restore semantics |
| Medium-high | Custom ingredients codec migration | Fabric API serializer contract changed to codecs |
| Medium-high | Loot table event signature and loot-function change | Callback params and reward NBT function both moved |
| Medium | Cauldron `ItemInteractionResult` and `InteractionMap` | Mechanical, already proven on Forge |
| Medium | Furnace fuel mixin target verification | 1.21.1 mapping check; possible descriptor move |
| Medium | Common reconciliation under the Fabric transform | Unknown size until first Fabric compile |
| Low | Identifiers, mixin compat level, `fabric.mod.json`, removing `ItemStackMixin` | Local and mechanical |

## Non-goals

- Repairing or reworking Forge, except the mandatory no-regression compile after common changes.
- Implementing NeoForge runtime code.
- Any build-environment change: Gradle, Loom, Architectury, plugins, mappings, the JDK, dependency
  versions, or `fabric/build.gradle` structure.
- Migrating unreleased 1.20.1 saves or item data.
- Introducing a new component architecture, networking layer, test framework, or abstraction layer
  unless a planned stage proves the existing approach cannot support Fabric 1.21.1.
- Redesigning player-visible bucket behavior to accommodate a compile error.

## Questions deliberately deferred to evidence

Decide these during their named stage from compiler output, Fabric API sources, and focused tests:

- whether `CompoundTag <-> DataComponentPatch` conversion for `FluidVariant` stays entirely in the
  Fabric module or requires a `StoredFluid` shape change;
- the exact 1.21 `CustomIngredientSerializer` codec and stream-codec contract;
- the exact 1.21 `LootTableEvents.MODIFY` parameter list and the current "built-in" source predicate;
- the current `AbstractFurnaceBlockEntity#getBurnDuration` descriptor under 1.21.1 mappings;
- whether removing `ItemStackMixin` fully preserves variable stack size on Fabric;
- the current Fabric model-modifier callback and `FabricBakedModel` quad-emission signatures.

If any of these requires choosing new player behavior, a new persistence design, or a new build
dependency, unattended work stops for user direction.
