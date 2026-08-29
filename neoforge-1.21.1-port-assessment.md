# NeoForge 1.21.1 Port Assessment

## Purpose and scope

This document records the known technical shape of building a Some Buckets **NeoForge** artifact for
Minecraft 1.21.1 (NeoForge `21.1.248`, NeoForge JavaFML loader). It is an assessment, not an
execution log or a substitute for the staged plan in `neoforge-1.21.1-port-plan.md`.

This task differs from the completed Forge and Fabric 1.21.1 ports in kind. Those were **migrations**:
existing 1.20.1 loader code that was carried to 1.21.1 by classifying compiler errors and repairing
them stage by stage. NeoForge has **no loader code to migrate**. The `neoforge` subproject currently
contains build, dependency, metadata, and packaging scaffolding only:

| Present | State |
| --- | --- |
| `neoforge/build.gradle` | Architectury + Loom + Shadow main-source wiring; `transformProductionNeoForge` bundle; `ftb-chunks-neoforge` `modCompileOnly`; `common/src/compat/java` added to the main source set; `neoforge.mods.toml` token expansion. **No gametest source set, no run config, no loot-modifier generator.** |
| `neoforge/gradle.properties` | `loom.platform=neoforge` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | Tokenized loader/mc/ftb dependency metadata |
| Everything else | **Absent** — no Java, no mixin config, no item model JSON, no GameTest source or resources |

So this is **construction**, guided by two working reference implementations:

- **`forge/src/main` is the structural template.** 37 files, same root package, same subsystem
  decomposition NeoForge will reuse almost one-for-one. NeoForge 21.1 and Forge 52 for MC 1.21.1
  share most non-capability API surface.
- **`fabric/src/main` is the 1.21.1 boundary-pattern precedent.** Where a subsystem changed shape in
  1.21.1 in a way that also lands on NeoForge — component-payload fluid values, codec-based custom
  ingredients, the `MAX_STACK_SIZE` component replacing a per-item stack-size override — the Fabric
  port already solved it once and its solution is the model.

The completed Forge and Fabric ports **must not regress**. Any change under `common/src/main` or
`common/src/gametest` is followed by a Forge production compile **and** a Fabric production compile
(and `:fabric:compileGametestJava` if shared GameTest code changed) before the stage closes. The
Fabric port made zero `common` changes; the strong expectation is that NeoForge can be built
entirely inside `neoforge/` plus the authorized `neoforge/build.gradle` construction.

This is an unreleased mod. Preserving 1.20.1 worlds, item data, or binary compatibility is not a
requirement. The behavior described by `player-view.md` and the invariants in `as-built.md` remain
the target unless NeoForge makes one impossible; a missing API is not permission to change player
behavior.

## Reference material

- `fabric-1.21.1-port-{assessment,plan,process,status,log}.md` — the process this document set is
  adapted from, and the record of how the equivalent 1.21.1 boundary problems were resolved on
  Fabric. No `forge-1.21.1-port-*.md` document set exists in the repository; the Forge port's
  outcome is readable only in `forge/src/main`, `forge/src/gametest`, and `forge/build.gradle`.
- The live `forge/` and `fabric/` modules — authoritative for the current 1.21.1 API shapes the two
  finished loaders settled on.
- NeoForge's own published documentation and API sources, consulted by section when the local
  project and compiler diagnostics are insufficient. Decompiled or remapped Minecraft/Forge sources
  remain prohibited (`CLAUDE.md`).

## What the completed common layer already provides

The Forge port carried the loader-neutral layers to 1.21.1 and the Fabric port confirmed them under
a second transform without changing them:

| Concern | State |
| --- | --- |
| Component-backed bucket state in `NBTUtil` (`minecraft:custom_data`) | Done in common |
| Vanilla `MAX_STACK_SIZE` component as the variable-stack mechanism, written by `NBTUtil` | Done in common |
| Registry-aware nested Junk/Trash stack codecs (`HolderLookup.Provider`) | Done in common |
| `ResourceLocation` factories, `Item.TooltipContext`, use-duration, component equality, relocated `BlockSource`, `ItemInteractionResult` where common touches cauldrons | Done in common |
| Mob Bucket capture/restore against 1.21.1 entity APIs | Done in common |
| `StoredFluid` loader-neutral fluid value with optional variant `CompoundTag` | Done in common |
| Shared GameTest scenario bodies (`common/src/gametest/java/**Scenarios.java`, `SharedGameTestSupport`) | Done, on 1.21.1 GameTest APIs |
| Singular `data/somebuckets/recipe`, `tags/entity_type` directories; `somebuckets/bucket_loot.json` + `BucketLootTables` | Done, shared |
| FTB Chunks adapter in `common/src/compat/java`, reached only through the protection-provider registry | Done, shared |

The NeoForge module consumes these rather than repeating them. `common/src/main` client classes
carry only the cross-remapped `net.fabricmc.api.Environment` annotation; Architectury's NeoForge
transform is expected to remap it to `@OnlyIn` exactly as it does for Forge.

## Construction map: Forge file to NeoForge disposition

Dispositions: **A** copy with package/import adjustment and no behavioral change; **B** adapt for a
renamed NeoForge event, registration, or lifecycle API; **C** rewrite against the NeoForge
capability system; **D** port a 1.21.1 boundary conversion using the Fabric precedent; **N**
NeoForge-specific new file with no Forge analog; **G** produced by `neoforge/build.gradle`, not
hand-written.

| Forge source | Disp. | Notes |
| --- | --- | --- |
| `SomeBucketsForge` | B/N → `SomeBucketsNeoForge` | `@Mod` constructor injection (mod bus / `ModContainer`) replaces `FMLJavaModLoadingContext`; `NeoForge.EVENT_BUS` for game events; capability attach moves to `RegisterCapabilitiesEvent`; dispenser/cauldron registration in `FMLCommonSetupEvent#enqueueWork`. |
| `register/ModItems` | B | `DeferredRegister.createItems` / `DeferredRegister.Items`; `Registries.ITEM`. `BucketDefinitions` unchanged. |
| `register/ModSounds` | B | `DeferredRegister` on `Registries.SOUND_EVENT`. |
| `register/ModCreativeTabs` | B | `DeferredRegister` on `Registries.CREATIVE_MODE_TAB`; `CreativeBucketCatalog.populate` unchanged. |
| `register/ModLootModifiers` | B | NeoForge GLM serializer registry key; `AddBucketLootModifier.CODEC` reused. |
| `loot/AddBucketLootModifier` | A/B | NeoForge kept Forge's `LootModifier` / `IGlobalLootModifier` codec model; only the `net.neoforged.*` package differs. Reward still built from `BucketDefinitions` + `NBTUtil.setPowderUnits`. |
| `crafting/EmptyBucketIngredient` | D | NeoForge `ICustomIngredient` + `IngredientType` + `MapCodec` + `RegistryFriendlyByteBuf` stream codec. Same migration the Fabric port did for `FabricEmptyBucketIngredient`. Preserve id `somebuckets:empty_bucket`, component-sensitive `test`, non-simple matching. |
| `crafting/SpawnEggIngredient` | D | Same; preserve id `somebuckets:spawn_egg`, match all loaded spawn eggs. |
| `config/ServerConfig` | B | `ForgeConfigSpec` → NeoForge `ModConfigSpec` (confirm name); spec shape and `SBPolicy` keys unchanged. |
| `fuel/ForgeFuelEvents` | B/N → NeoForge fuel hook | NeoForge 21.1 has no `FurnaceFuelBurnTimeEvent`; the item-extension `getBurnTime(ItemStack, RecipeType)` hook is the NeoForge path (data-driven `FuelValues` is 1.21.2+). Likely no mixin, unlike Fabric. `BucketFuel.isLavaFuel` / `LAVA_BUCKET_BURN_TIME_TICKS` are common. |
| `platform/ForgeBucketOperations` | C | `BucketOperations` impl; delegates to the capability-layer classes below. `beforeWorldBucketUse` → NeoForge bucket-use event hook. `fluidDisplayName` / `fluidColor` build a NeoForge `FluidStack`. |
| `util/ForgeFluidStacks` | D → `NeoForgeFluidStacks` | `StoredFluid` ↔ NeoForge `FluidStack`. NeoForge `FluidStack` is component-based (`DataComponentPatch`), like Fabric's `FluidVariant`. Boundary helper only; `StoredFluid` unchanged. See "StoredFluid conversion" below. |
| `fluid/FluidProvider` | C | Item capability attach: `RegisterCapabilitiesEvent` + `Capabilities.FluidHandler.ITEM` registered for the BB and SB items; `AttachCapabilitiesEvent` / `ICapabilityProvider` / `LazyOptional` are gone. |
| `fluid/AbstractFluidHandler`, `BBFluidHandler`, `SBFluidHandler` | C | `IFluidHandlerItem` is retained in NeoForge; `FluidStack` API around it is component-based (`getFluidInTank`, `fill`, `drain`, `FluidAction`, `FluidType.BUCKET_VOLUME` all expected to survive). Route content through `NeoForgeFluidStacks`. |
| `fluid/BBFluidLogic`, `SBFluidLogic` | A/C | Domain logic largely portable; touch points are `FluidStack` construction and the item-capability lookup. |
| `fluid/FluidPickup`, `FluidProvider`, `ForgeFluidPlacement` | C | World fluid pickup/placement against NeoForge fluid utilities; `LiquidBlockContainer` / `BucketPickup` signatures already resolved by the Forge and Fabric ports — reuse those. |
| `interaction/Transfers` | C | Block fluid capability via `level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face)`; `FluidUtil.tryFluidTransfer`, `FluidBucketWrapper` exist in NeoForge; `SoundActions` is `net.neoforged.neoforge.common.SoundActions`. |
| `interaction/Cauldrons` | A/B | `CauldronInteraction.InteractionMap` + `ItemInteractionResult` — the shape Forge and Fabric already use on 1.21.1. |
| `interaction/Dispensers` | A/B | `DispenseItemBehavior`, relocated `net.minecraft.core.dispenser.BlockSource`; `NonFluidDispensers` (common) unchanged. |
| `interaction/ForgeHeldTransferEvents` | B | NeoForge interaction events on `NeoForge.EVENT_BUS` (`UseItemOnBlockEvent` / `PlayerInteractEvent.*`). |
| `interaction/Transfers` milk path, `MilkTransfers` usage | A | `MilkTransfers` / `HeldTransferSettlement` are common; supply only the NeoForge "still holds something" predicate. |
| `item/ForgeBBItem`, `ForgeJBItem`, `ForgeMBItem`, `ForgeSBItem`, `ForgeTBItem` | A/B | Thin loader shells over common item classes; `Item.Properties`, `IClientItemExtensions` for the JB renderer. `getBurnTime` override may land here (fuel hook). |
| `client/ClientSetup` | B | NeoForge `FMLClientSetupEvent`, `RegisterColorHandlersEvent.Item`, `RegisterClientReloadListenersEvent`, `ModelEvent.RegisterGeometryLoaders`, `ModelEvent.ModifyBakingResult`; `@EventBusSubscriber(Dist.CLIENT)`. |
| `client/ClientColorHandlers`, `ClientFluidColors`, `SidedFluidColors` | B/D | Color providers; fluid color path builds a NeoForge `FluidStack` (via `NeoForgeFluidStacks`). |
| `client/ClientModelLoaders`, `StoredFluidContainerModel` | B | NeoForge geometry-loader + baked-model-modifier API; the mask-clipped fluid layer is real geometry and must be ported, not dropped. |
| `client/JBModel`, `JBRenderer` | B | NeoForge `IClientItemExtensions#getCustomRenderer` (BEWLR equivalent) + `ModelResourceLocation`. Preserve FIFO order, tint, glint, cover geometry. |
| `client/ClientSetup` reload listeners | B | `RegisterClientReloadListenersEvent`. |
| — | N | `AutomationPlayers` install: NeoForge `FakePlayerFactory` / `FakePlayer` stable fake player `[SomeBuckets]` for dispenser claim checks. |
| — | N | FTB Chunks provider registration guarded by a NeoForge "is mod loaded" check (mirrors Fabric; Forge has no analog because no Forge 1.21.1 FTB artifact). |
| — | N | `somebuckets_gametest` `@Mod` stub, `neoforge.mods.toml` gametest variant, `pack.mcmeta`. |
| `forge/build.gradle` GLM generator (`generateBucketLootModifiers`) | G | NeoForge sibling emitting `neoforge:loot_table_id` conditions and `data/neoforge/loot_modifiers/global_loot_modifiers.json`. |
| `forge/build.gradle` gametest source set + `runs.gameTestServer` + `somebuckets_gametest` loom mod + `configureGameTestStructures(project)` | G | Authorized `neoforge/build.gradle` construction. |

## NeoForge-specific delta areas

### Mod bootstrap and event bus

NeoForge injects the mod event bus (and `ModContainer`) into the `@Mod` constructor; there is no
`FMLJavaModLoadingContext`. Game events subscribe to `NeoForge.EVENT_BUS`; mod-lifecycle events to
the injected bus. `@EventBusSubscriber` no longer takes a `Bus` value in current NeoForge — confirm
the `21.1.248` form. Install order from `SomeBucketsForge` is preserved: `BucketOperations`,
`AutomationPlayers`, config registration, content `DeferredRegister`s, then dispenser/cauldron
registration in common-setup `enqueueWork`.

### Registration

`net.neoforged.neoforge.registries.DeferredRegister` with vanilla `Registries.*` keys (and the
typed `DeferredRegister.Items` / `DeferredRegister` helpers). NeoForge has no `ForgeRegistries`;
`BuiltInRegistries` is used directly where Forge used `ForgeRegistries` for plain vanilla registries.
The custom-serializer registries (ingredient types, GLM serializers) live under NeoForge's registry
keys.

### Capabilities — highest risk

Forge's `AttachCapabilitiesEvent`, `ICapabilityProvider`, `LazyOptional`, and
`ForgeCapabilities.FLUID_HANDLER*` are **entirely gone** in NeoForge. The replacements:

- **Item handler** (`FluidProvider`): register in `RegisterCapabilitiesEvent` with
  `Capabilities.FluidHandler.ITEM` (or `.registerFor…` for the specific BB/SB items), supplying a
  factory that builds a `BBFluidHandler` / `SBFluidHandler` bound to the stack. `IFluidHandlerItem`
  itself is retained (`net.neoforged.neoforge.fluids.capability.IFluidHandlerItem`).
- **Block handler** (`Transfers#blockHandler`): `level.getCapability(Capabilities.FluidHandler.BLOCK,
  pos, state, blockEntity, face)` returns the handler directly (nullable) — no `LazyOptional`.
- **The mod bucket's own handler is an invariant, not an optional** (`requireBucketHandler`): the
  NeoForge lookup returns nullable; keep the fail-fast on `null`.

Simulation/execution (`FluidAction.SIMULATE` / `EXECUTE`), `FluidUtil.tryFluidTransfer`,
`FluidBucketWrapper`, and `FluidType.BUCKET_VOLUME` are all expected to survive on NeoForge. The
transaction-safety obligations from the Forge code (preview before authorization, exact-target
protection, contract-violation reporting, one-unit finite vs infinite Source Bucket, block-storage
owns dispatch) carry over unchanged.

### `StoredFluid` to NeoForge `FluidStack` conversion

This is the central NeoForge-specific design question, and it is the same question the Fabric port
answered for `FluidVariant`. NeoForge's `FluidStack` in 1.21.1 is **component-based** — it carries a
`DataComponentPatch`, not a `CompoundTag`. `StoredFluid` in common carries an optional variant
`CompoundTag` (`variantTag()`), matching the Forge `FluidStack` schema.

Default (mirrors both `ForgeFluidStacks` and `fabric/.../fluid/FabricFluidVariants`): a
NeoForge-module-only `NeoForgeFluidStacks` helper that converts `CompoundTag <-> DataComponentPatch`
at the boundary, leaving `StoredFluid`'s common shape unchanged. The Fabric port took the further
decision to bridge with `DataComponentPatch.CODEC` over plain `NbtOps` (no registry context), so a
component that needs registry context to serialize degrades to a blank patch — water, lava, milk,
and virtually all modded fluids carry no variant components, so this is lossless in practice. The
same "plain ops, graceful degradation" choice is the expected default here; it needs user
ratification (as it did on Fabric) because it is a deliberate, if practically invisible, narrowing.

Only change `StoredFluid` itself if a NeoForge compile proves the boundary-only approach cannot
preserve variant data through a capability transaction — and then stop for user confirmation.

### Custom recipe ingredients

`EmptyBucketIngredient` / `SpawnEggIngredient` currently extend Forge `AbstractIngredient` with a
Forge `IIngredientSerializer`. NeoForge uses `ICustomIngredient` + a registered `IngredientType<T>`
carrying a `MapCodec<T>` and a `StreamCodec<RegistryFriendlyByteBuf, T>`. This is the same contract
the Fabric port moved to (`CustomIngredientSerializer` codec/stream-codec). Register the
`IngredientType`s via `DeferredRegister` on the NeoForge ingredient-type registry. Preserve:

- ids `somebuckets:empty_bucket` and `somebuckets:spawn_egg`;
- `EmptyBucketIngredient` encodes its configured item and stays component-sensitive
  (`NBTUtil.isEmptyBucket`), non-simple so the recipe system calls `test`;
- `SpawnEggIngredient` matches all standard loaded spawn eggs;
- recipe JSON loads without obsolete serializer fields.

### Global loot modifiers

NeoForge retained Forge's GLM architecture (`LootModifier`, `IGlobalLootModifier`, `codecStart`,
the serializer registry). `AddBucketLootModifier` ports at disposition A/B. What changes is the
**data**: the loot-table condition id is `neoforge:loot_table_id` (not `forge:loot_table_id`), and
the aggregate index is `data/neoforge/loot_modifiers/global_loot_modifiers.json`. The Forge build's
`generateBucketLootModifiers` Groovy task in `forge/build.gradle` materializes those resources from
`common/src/main/resources/somebuckets/bucket_loot.json`; `neoforge/build.gradle` needs a sibling
task with the NeoForge namespace. Rewards, chances, targets, and initial contents stay in the common
manifest — no duplicated loader list.

### Cauldrons, dispensers, held-transfer events

- Cauldrons: `CauldronInteraction.InteractionMap` reached through its accessor, functions return
  `ItemInteractionResult` — identical to what `forge/.../interaction/Cauldrons` and
  `fabric/.../interaction/FabricCauldronInteractions` already do on 1.21.1. Mechanical.
- Dispensers: `DispenseItemBehavior`, `net.minecraft.core.dispenser.BlockSource`; `DispenserTarget`
  / `NonFluidDispensers` are common. Mechanical.
- Held-transfer events: `ForgeHeldTransferEvents` subscribes to Forge interaction events; the
  NeoForge equivalents (`UseItemOnBlockEvent`, `PlayerInteractEvent.RightClickItem` /
  `.RightClickBlock`) on `NeoForge.EVENT_BUS`. A targeted block takes precedence over the air
  transfer — preserve that ordering.

### Fuel

NeoForge 21.1 does not expose `FurnaceFuelBurnTimeEvent`. The NeoForge way to give a lava-filled
Big/Huge Bucket a finite burn is the item-extension `getBurnTime(ItemStack, RecipeType)` hook
overridden on the item class (`ForgeBBItem`'s NeoForge counterpart). The data-driven `FuelValues`
furnace refactor is 1.21.2+, so the item hook is the right 1.21.1 mechanism. No mixin is expected
(Fabric needed `AbstractFurnaceBlockEntityMixin`; Forge used an event; NeoForge has a first-class
item hook). Confirm the exact `21.1.248` signature in Stage 4. `BucketFuel.isLavaFuel` and
`FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS` are common and already ported; an allowed lava Source
Bucket must still return permanent fuel.

### Configuration

`ForgeConfigSpec` is `ModConfigSpec` on NeoForge (confirm the package/name). `ModConfig.Type.SERVER`,
`ModConfigEvent.Loading` / `.Reloading` are retained. `SBPolicy` and the config keys/section are
common and unchanged; the file stays `serverconfig/somebuckets-server.toml`. Config code resolves
ids and installs the policy; Source Bucket code does not parse config directly.

### Client models and presentation

NeoForge client APIs parallel Forge with renamed events: `FMLClientSetupEvent`,
`RegisterColorHandlersEvent.Item`, `RegisterClientReloadListenersEvent`,
`ModelEvent.RegisterGeometryLoaders`, `ModelEvent.ModifyBakingResult`. The Junk Bucket dynamic
renderer attaches through `IClientItemExtensions#getCustomRenderer` (NeoForge's BEWLR). Item model
JSONs: the Forge module ships four (`big_bucket_64`, `big_bucket_8`, `junk_bucket`, `source_bucket`)
for its geometry-loader approach; copy those and confirm against the NeoForge geometry loader.
`SpawnEggItem.byId` / `#getColor` moved toward components — verify. The mask-clipping fluid container
model does real geometry work and cannot be replaced by a stock model. Keep every client class safe
from dedicated-server classloading.

### FTB Chunks and the fake player

`dev.ftb.mods:ftb-chunks-neoforge:2101.1.21` is a real 1.21.1 artifact and is already a
`modCompileOnly` dependency; `common/src/compat/java` is already on the NeoForge main source set.
NeoForge keeps FTB Chunks integration like Fabric (Forge dropped it for lack of a 1.21.1 artifact).
Needs: a NeoForge "is `ftbchunks` loaded" check that registers the shared `FtbChunksProtection`
provider, and the `AutomationPlayers` install supplying a NeoForge `FakePlayer` /
`FakePlayerFactory` fake player named `[SomeBuckets]` for dispenser claim checks. Open Parties and
Claims needs no add-on. `Protections` / `ClaimProtections` composition is common.

### GameTest wiring gap

Nothing exists. Stage 6 builds all of it, in `neoforge/build.gradle` and `neoforge/src/gametest`:

- a `gametest` source set that `srcDir`s `common/src/gametest/java` (shared scenarios, already
  ported) plus NeoForge discovery wrappers and NeoForge-specific coverage;
- `loom { mods { somebuckets_gametest { sourceSet … } } }` and a `runs { gameTestServer { … } }`
  with the NeoForge enabled-namespaces property (`neoforge.enabledGameTestNamespaces` — confirm);
- `rootProject.configureGameTestStructures(project)` (decodes `empty_9x6x9.nbt.b64`);
- `neoforge/src/gametest/resources/META-INF/neoforge.mods.toml` gametest variant, `pack.mcmeta`,
  and a `@Mod("somebuckets_gametest")` stub (NeoForge's JavaFML loader requires the `@Mod` class,
  exactly like Forge's `SomeBucketsGameTestMod`);
- NeoForge GameTest discovery (`@GameTestHolder` / `@PrefixGameTestTemplate` and/or a registration
  entrypoint) — resolve the exact `21.1.248` mechanism from NeoForge docs.

The Forge gametest tree (19 classes: shared wrappers + `Forge*` / `ForgeOnly*` capability, fuel,
fill-bucket-event coverage) and the Fabric gametest tree (13 classes) are the templates. NeoForge
needs the shared wrappers plus NeoForge-specific capability/fuel coverage analogous to `Forge*`;
`ForgeOnly*` tests that exercise Forge-only semantics get a NeoForge equivalent only where the
behavior exists on NeoForge.

## Existing verification assets

None for NeoForge. `common/src/gametest/java/**Scenarios.java` and `SharedGameTestSupport` are
already ported and will compile into the NeoForge gametest source set once it exists. The Forge and
Fabric `*GameTests` classes are the porting templates. Tests must not be deleted, weakened, or made
less specific to obtain a green run.

## Risk ranking

| Risk | Area | Reason |
| --- | --- | --- |
| Highest | Capability-layer rewrite (`FluidProvider`, `*FluidHandler`, `Transfers` block lookups) | Forge's capability system is entirely replaced; every BB/SB fluid path and every block-storage interaction routes through it; transaction/simulation semantics must be preserved exactly |
| Highest | `StoredFluid` ↔ NeoForge `FluidStack` component-payload conversion | Central to every fluid path and to variant tint; must round-trip modded data through a capability transaction |
| High | NeoForge GameTest build wiring built from nothing | Source set, run config, discovery mechanism, loot-modifier generator, gametest metadata — all new; a green run is a completion gate |
| High | Client model, geometry-loader, and dynamic-renderer port | Broad renamed-API surface with limited headless coverage; mask-clipped fluid geometry must be preserved |
| Medium-high | Custom ingredients `ICustomIngredient` migration | New codec/stream-codec contract; Fabric precedent exists |
| Medium-high | GLM data namespace + build generator | `neoforge:loot_table_id`, `data/neoforge/...`, new Groovy task; wrong namespace silently drops structure loot |
| Medium | Mod bootstrap / event bus / `@EventBusSubscriber` form | Renamed lifecycle; constructor injection; one-time |
| Medium | Fuel item hook | NeoForge has no burn-time event; confirm `getBurnTime` signature |
| Medium | Config `ModConfigSpec` rename | Mechanical once the name is confirmed |
| Low-medium | FTB Chunks + fake player wiring | Mirrors Fabric; real 1.21.1 artifact already on the classpath |
| Low | Cauldrons, dispensers, identifiers, registration keys | Same shapes Forge/Fabric already use on 1.21.1 |

## Non-goals

- Repairing or reworking Forge or Fabric, beyond the mandatory no-regression compiles after any
  `common` change.
- Any build-environment change other than the explicitly authorized `neoforge/build.gradle`
  construction (gametest source set + run + loom mod + structure hook + NeoForge GLM generator +
  the token-expansion properties those need): no Gradle, Loom, Architectury, Shadow, plugin,
  mapping, or JDK version change; no `neoforge_compile_version` bump; no new dependency, repository,
  or plugin; no change to `common/`, `fabric/`, or `forge/` build scripts.
- Migrating unreleased 1.20.1 saves or item data.
- Introducing a new component architecture, networking layer, test framework, or cross-cutting
  abstraction unless a planned stage proves the existing approach cannot support NeoForge 1.21.1.
- Redesigning player-visible bucket behavior, capacities, gesture priorities, protection rules,
  fuel values, transfer settlement, or persistence ownership to accommodate a compile error.
- Redesigning `StoredFluid`'s common shape without explicit user confirmation.

## Questions deliberately deferred to evidence

Decide these during their named stage from NeoForge's own published docs, the `forge`/`fabric`
module code, and focused compiles/tests:

- the `21.1.248` `@Mod` constructor signature and mod-bus acquisition, and the `@EventBusSubscriber`
  form;
- the item-capability registration call (`RegisterCapabilitiesEvent` shape, `Capabilities.FluidHandler`
  member names) and the block-capability lookup signature;
- whether the `NeoForgeFluidStacks` boundary helper (plain `NbtOps`, graceful degradation) fully
  preserves variant data through a capability transaction, or a `StoredFluid` shape change is forced
  (→ stop for user confirmation);
- the NeoForge `ICustomIngredient` / `IngredientType` codec and stream-codec contract and its
  registry key;
- the NeoForge GLM serializer registry key and the exact `neoforge:` loot-table-id condition id and
  output path;
- the `21.1.248` finite-fuel hook (`IItemExtension#getBurnTime` signature or successor);
- the `ForgeConfigSpec` successor type name/package on NeoForge;
- the NeoForge geometry-loader, baked-model-modifier, and `IClientItemExtensions` custom-renderer
  signatures;
- the NeoForge GameTest discovery mechanism and the enabled-namespaces run property.

If any of these requires choosing new player behavior, a new persistence design, or a new build
dependency, unattended work stops for user direction.
