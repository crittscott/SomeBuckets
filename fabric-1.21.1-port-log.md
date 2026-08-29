# Fabric 1.21.1 Port Log

Append-only history of the Fabric 1.21.1 port. Entries are added during execution and are never
edited or removed. Nothing in the standard work loop reads this file; it exists as the audit trail
for a human reviewer. The live execution position is in `fabric-1.21.1-port-status.md`.

## Verification history

- 2026-08-27: Port document set created (`fabric-1.21.1-port-assessment.md`,
  `fabric-1.21.1-port-plan.md`, `fabric-1.21.1-port-process.md`, `fabric-1.21.1-port-status.md`, and
  this log). No code, resources, or build files changed. Execution has not begun; Stage 0 baseline
  diagnostic is the next action.

- 2026-08-28: Stage 0 baseline diagnostic. Command `.\gradlew.bat :fabric:compileJava
  --console=plain`, exit 1. `:common:compileJava` UP-TO-DATE (passing); `:fabric:compileJava` FAILED
  with 47 errors, every one in `fabric/src/main` (no `common`, no `fabric/src/gametest`). Delta: none
  (first run). Error groups and stage classification:
  - Stage 2 (identifiers), 6: private/1-arg `new ResourceLocation(...)` in
    `client/FabricFluidContainerModel:57`, `client/FabricJunkBucketRenderer:30`,
    `crafting/FabricEmptyBucketIngredient:20` and `:38`, `crafting/FabricSpawnEggIngredient:17`,
    `register/FabricCreativeTabs:17`.
  - Stage 3A (Transfer API stack-state copying), 9: raw `getTag`/`hasTag`/`setTag` in
    `fluid/FabricBucketStorage:189` and `:201`, `platform/FabricBucketOperations:131`.
  - Stage 3B (`FluidVariant` payload conversion), 6: `CompoundTag` -> `DataComponentPatch` at
    `FluidVariant.of(...)` and missing `FluidVariant.copyNbt()` in `fluid/FabricBucketStorage:57`,
    `:88`, `:98`, `:139`, `platform/FabricBucketOperations:657`, `platform/FabricFluidPlacement:70`.
  - Stage 3C (changed MC signatures in fluid ops), 3: `BucketPickup#pickupBlock` now
    `(Player, LevelAccessor, BlockPos, BlockState)` at `platform/FabricBucketOperations:642`;
    `LiquidBlockContainer#canPlaceLiquid` now `(Player, BlockGetter, BlockPos, BlockState, Fluid)` at
    `platform/FabricFluidPlacement:50` and `:85`.
  - Stage 4A (custom ingredient codec migration), 10: `CustomIngredientSerializer#getPacketCodec()`
    unimplemented plus obsolete `@Override read/write(JsonObject|FriendlyByteBuf)` in
    `crafting/FabricEmptyBucketIngredient:32,35,41,46,50` and
    `crafting/FabricSpawnEggIngredient:30,32,33,34,35`.
  - Stage 4B (loot injection), 3: `SetNbtFunction` removed (import `loot/FabricBucketLoot:9`, use
    `:31`); `LootTableEvents.MODIFY` lambda parameter types changed (`:18`).
  - Stage 4C (cauldrons and dispensers), 5: relocated `net.minecraft.core.BlockSource` at
    `interaction/FabricFluidDispensers:8,32,65`; `CauldronInteraction.EMPTY` / `.POWDER_SNOW` now
    `InteractionMap` with no `put` at `interaction/FabricCauldronInteractions:26,27`.
  - Stage 5A (model loading), 1: `modelContext.id()` gone from the model-modifier `Context` at
    `client/FabricFluidContainerModel:79`.
  - Stage 5B (client fluid conversion sites), 3: `CompoundTag` -> `DataComponentPatch` at
    `FluidVariant.of(...)` in `client/FabricClientFluidColors:13` and `:21`,
    `client/FabricFluidContainerModel:104` (applies the Stage 3B helper).
  - Stage 5C (Junk Bucket renderer), 1: `NBTUtil.getStoredItems` now requires a
    `HolderLookup.Provider` at `client/FabricJunkBucketRenderer:50`.
  No Stage 1 common regression was surfaced by the Fabric transform. No code, resources, or build
  files changed.

- 2026-08-28: Stage 1 verification. Command `.\gradlew.bat :common:compileJava --console=plain`,
  exit 0, `:common:compileJava` UP-TO-DATE, 0 errors. Delta from Stage 0: none (`:common` was
  already green). Inspection: `common/src/main` loader imports are limited to
  `net.fabricmc.api.EnvType` / `Environment` on five client classes (`BucketMouth`,
  `ClientTextureColors`, `DelegatingBakedModel`, `JunkForegroundGeometry`, `JunkIconLayout`) — the
  sanctioned cross-remapped annotation; no Forge, Architectury, or other Fabric API in common. No
  common regression is surfaced by the Fabric transform; every Stage 0 error is a Fabric call site.
  `StoredFluid` keeps `@Nullable CompoundTag variantTag` (defensively copied); `ForgeFluidStacks`
  converts `StoredFluid <-> FluidStack` entirely inside the forge module. Fluid-conversion decision
  ratified: a Fabric-module-only `StoredFluid <-> FluidVariant` helper doing `CompoundTag <->
  DataComponentPatch` at that boundary, `StoredFluid`'s common shape unchanged; implemented in Stage
  3B and applied to client sites in Stage 5B. No code, resources, or build files changed; Forge not
  re-compiled because common did not change.

- 2026-08-28: Stage 2 diagnostic. Command `.\gradlew.bat :fabric:compileJava --console=plain`,
  exit 1, 41 errors (all in `fabric/src/main`). Delta from Stage 0 baseline: -6, exactly the six
  `new ResourceLocation(...)` sites (`register/FabricCreativeTabs:17`,
  `crafting/FabricSpawnEggIngredient:17`, `crafting/FabricEmptyBucketIngredient:20` and `:38`,
  `client/FabricJunkBucketRenderer:30`, `client/FabricFluidContainerModel:57`) now on
  `ResourceLocation.fromNamespaceAndPath` / `ResourceLocation.parse`. No new errors introduced.
  `somebuckets.fabric.mixins.json` `compatibilityLevel` set to `JAVA_21`. `SomeBucketsFabric`,
  `FabricItems`, `FabricSounds` unchanged (registration and lifecycle APIs unchanged for 1.21.1);
  both `fabric.mod.json` files valid; `fabric/build.gradle:29` `somebuckets-gametest` Loom mod entry
  matches the gametest manifest. Remaining 41 errors classified: Stage 3A 9, Stage 3B 6, Stage 3C 3,
  Stage 4A 10, Stage 4B 3, Stage 4C 5, Stage 5A 1, Stage 5B 3, Stage 5C 1. No common change; Forge
  unaffected.

- 2026-08-28: Stage 3 diagnostic. Command `.\gradlew.bat :fabric:compileJava --console=plain`,
  exit 1, 23 errors (all in `fabric/src/main`). Delta from Stage 2: -18, exactly the Stage 3 set
  (3A 9, 3B 6, 3C 3). No new errors. Design decision recorded: variant payload bridged with
  `DataComponentPatch.CODEC` over `NbtOps.INSTANCE`, no registry context (user choice
  "plain NbtOps, graceful degradation" 2026-08-28) - a component needing registry context to
  serialize degrades to a blank patch; water/lava/milk and virtually all modded fluids carry no
  variant components. New helper `fabric/.../fluid/FabricFluidVariants` (StoredFluid <-> FluidVariant).
  3A: new helper `fabric/.../util/BucketStackState` copies count + `custom_data` (defensive
  `CustomData.of(copyTag())`) + `max_stack_size`; replaces raw `setTag`/`getTag`/`hasTag` in
  `FabricBucketStorage.StackBackend` (`replace`, `readSnapshot`) and
  `FabricBucketOperations.tryHeldTransfer`; `createSnapshot` still `stack.copy()`. 3C:
  `BucketPickup#pickupBlock(player, level, pos, state)` now passes the real acting player (was
  player-less); `LiquidBlockContainer#canPlaceLiquid(null, level, pos, state, fluid)` at both
  `FabricFluidPlacement` sites - null player keeps the prior player-agnostic check.
  `FabricFluidStorages` needed no change. Remaining 23 classified: Stage 4A 10, Stage 4B 3,
  Stage 4C 5, Stage 5A 1, Stage 5B 3, Stage 5C 1. No common change; Forge unaffected.

- 2026-08-28: Stage 4 diagnostic. Command `.\gradlew.bat :fabric:processResources :fabric:compileJava
  --console=plain`. First run: `:fabric:processResources` FAILED with "Failed to clean up stale
  outputs" (Gradle infrastructure, no code cause); retried once with the identical command and no
  environment change per the process. Retry: `:fabric:processResources` PASSED;
  `:fabric:compileJava` exit 1 with 5 errors, all in `fabric/src/main/client`. Delta from Stage 3:
  -18, exactly the Stage 4 set (4A 10, 4B 3, 4C 5). No new errors. 4A: both custom ingredients on
  the 1.21 `CustomIngredientSerializer` contract (`getCodec(boolean)` + `getPacketCodec()`;
  `MapCodec` via `RecordCodecBuilder` / `MapCodec.unit`; `StreamCodec` via
  `ByteBufCodecs.registry(Registries.ITEM).map(...)` / `StreamCodec.unit`); ids preserved. 4B:
  `FabricBucketLoot` moved from `fabric.api.loot.v2` to `v3` (`MODIFY` lambda is now
  `(key, tableBuilder, source, registries)`, `key.location()` to `rewardsFor`); `SetNbtFunction` ->
  `SetCustomDataFunction.setCustomData(CompoundTag)`. 4C: `FabricCauldronInteractions` to
  `CauldronInteraction.EMPTY/POWDER_SNOW.map().put(...)` and `ItemInteractionResult`
  (`sidedSuccess(level.isClientSide())` / `PASS_TO_DEFAULT_BLOCK_INTERACTION`);
  `FabricFluidDispensers` import `net.minecraft.core.BlockSource` ->
  `net.minecraft.core.dispenser.BlockSource`. 4D: `ItemStackMixin` deleted and dropped from
  `somebuckets.fabric.mixins.json` - the common `MAX_STACK_SIZE` component is authoritative
  (`BBItem` etc. call `properties.stacksTo(EMPTY_STACK_SIZE)`, `NBTUtil.setData` rewrites it at every
  mutation, `BucketStackState` propagates it); confirm empty-vs-filled sizes with a GameTest in
  Stage 6/7. `AbstractFurnaceBlockEntityMixin` compiles; `isFuel`/`getBurnDuration` target
  descriptors to be runtime-verified in Stage 7. 4E: `FabricServerConfig`, `FabricHeldTransferEvents`,
  `NonFluidDispensers`, and the `assets` model JSON needed no change. Remaining 5 errors: Stage 5A 1,
  Stage 5B 3, Stage 5C 1. No common change; Forge unaffected.

## Files changed by port execution

- 2026-08-28 (Stage 2):
  - `fabric/src/main/java/.../register/FabricCreativeTabs.java` — `ResourceLocation` factory.
  - `fabric/src/main/java/.../crafting/FabricSpawnEggIngredient.java` — `ResourceLocation` factory.
  - `fabric/src/main/java/.../crafting/FabricEmptyBucketIngredient.java` — `ResourceLocation`
    factory (two sites: `fromNamespaceAndPath` for the id, `parse` for the JSON `item` value).
  - `fabric/src/main/java/.../client/FabricJunkBucketRenderer.java` — `ResourceLocation` factory.
  - `fabric/src/main/java/.../client/FabricFluidContainerModel.java` — `ResourceLocation` factory.
  - `fabric/src/main/resources/somebuckets.fabric.mixins.json` — `compatibilityLevel` -> `JAVA_21`.

- 2026-08-28 (Stage 3):
  - `fabric/src/main/java/.../util/BucketStackState.java` — NEW: in-place copy of a bucket's count,
    `custom_data`, and `max_stack_size` between stacks.
  - `fabric/src/main/java/.../fluid/FabricFluidVariants.java` — NEW: `StoredFluid` <-> `FluidVariant`
    conversion, `DataComponentPatch.CODEC` over `NbtOps`.
  - `fabric/src/main/java/.../fluid/FabricBucketStorage.java` — `variant()` via `FabricFluidVariants`;
    `resource.copyNbt()` -> `FabricFluidVariants.variantTag(resource)` (3 sites); `StackBackend`
    `replace`/`readSnapshot` via `BucketStackState.copy`.
  - `fabric/src/main/java/.../platform/FabricBucketOperations.java` — `tryHeldTransfer` settle-back
    via `BucketStackState.copy`; `pickupBlock(player, level, pos, state)`; `variant(StoredFluid)` via
    `FabricFluidVariants`.
  - `fabric/src/main/java/.../platform/FabricFluidPlacement.java` — `canPlaceLiquid(null, ...)`
    (2 sites); variant via `FabricFluidVariants.toVariant(fluid, tag)`.

- 2026-08-28 (Stage 4):
  - `fabric/src/main/java/.../crafting/FabricEmptyBucketIngredient.java` — rewritten `Serializer` to
    the 1.21 `CustomIngredientSerializer` codec contract.
  - `fabric/src/main/java/.../crafting/FabricSpawnEggIngredient.java` — same; added `INSTANCE`.
  - `fabric/src/main/java/.../loot/FabricBucketLoot.java` — loot API v2 -> v3, `SetNbtFunction` ->
    `SetCustomDataFunction`.
  - `fabric/src/main/java/.../interaction/FabricCauldronInteractions.java` — `InteractionMap.map()`
    + `ItemInteractionResult`.
  - `fabric/src/main/java/.../interaction/FabricFluidDispensers.java` — relocated `BlockSource`
    import.
  - `fabric/src/main/java/.../mixin/ItemStackMixin.java` — DELETED (redundant).
  - `fabric/src/main/resources/somebuckets.fabric.mixins.json` — dropped the `ItemStackMixin` entry.

## Completed stages

- 2026-08-28: Stage 0 — Baseline diagnostics. One `:fabric:compileJava` diagnostic run; 47 errors,
  all in `fabric/src/main`, classified against Stages 2-5 (see Verification history above).
  `:common:compileJava` passing. First Stage 1 work unit recorded in the snapshot. No code changed.

- 2026-08-28: Stage 1 — Common reconciliation under the Fabric transform. `:common:compileJava`
  confirmed passing (exit 0); common loader-import scan clean; no Fabric-transform-only common
  regression. `StoredFluid` -> `FluidVariant` conversion ratified as a Fabric-module-only
  boundary helper with `StoredFluid` unchanged. No code, resources, or build files changed; Forge
  not re-compiled (common unchanged). First Stage 2 work unit recorded in the snapshot.

- 2026-08-28: Stage 2 — Fabric bootstrap, registration, identifiers, metadata. All six
  `new ResourceLocation(...)` sites in `fabric/src/main` moved to `ResourceLocation.fromNamespaceAndPath`
  / `.parse`; mixin `compatibilityLevel` -> `JAVA_21`. Bootstrap, item/sound registration, and both
  `fabric.mod.json` files needed no change for 1.21.1. Diagnostic `:fabric:compileJava` exit 1, 41
  errors (down from 47), all classified against Stages 3-5; no new failures; no common change.
  First Stage 3 work unit recorded in the snapshot.

- 2026-08-28: Stage 3 — Fabric Transfer API and fluid transfer core. 3A stack-state copying
  (`BucketStackState`), 3B `FluidVariant` payload conversion (`FabricFluidVariants`,
  `DataComponentPatch.CODEC` over `NbtOps`, plain / graceful-degradation per user decision), 3C
  changed vanilla fluid-op signatures (`pickupBlock`, `canPlaceLiquid`). Diagnostic
  `:fabric:compileJava` exit 1, 23 errors (down from 41); all 18 Stage 3 errors cleared; remaining
  23 classified to Stages 4-5; no new failures; no common change. First Stage 4 work unit recorded
  in the snapshot.

- 2026-08-28: Stage 4 — Fabric server systems, interactions, mixins, data. 4A custom-ingredient
  codec migration, 4B loot injection (v3 event + `SetCustomDataFunction`), 4C cauldrons/dispensers
  (`InteractionMap` + `ItemInteractionResult`, relocated `BlockSource`), 4D `ItemStackMixin` removed
  (component authoritative; furnace mixin deferred to Stage 7 runtime check), 4E config/resources
  verified unchanged. `:fabric:processResources` passed (after one identical-command retry past a
  Gradle stale-outputs infra failure); diagnostic `:fabric:compileJava` exit 1, 5 errors (down from
  23), all Stage 5 client; no new failures; no common change. First Stage 5 work unit recorded in
  the snapshot.

- 2026-08-28: Stage 5 — Fabric client models and presentation. Three-part client fix:
  5A model loading — `ModelModifier.AfterBake.Context#id()` renamed to `#resourceId()` in
  fabric-model-loading-api-v1 2.1.0 (bundled in fabric-api 0.116.15+1.21.1); updated the call in
  `FabricFluidContainerModel.registerModels()` and added an `id == null` guard (the callback now
  fires for top-level models with a null `resourceId()`). `ModelLoadingPlugin.register`,
  `context.modifyModelAfterBake()`, `addModels(...)`, the `FabricBakedModel`/`RenderContext`/
  `QuadEmitter`/`BakedQuad` renderer-API usage, and the mask-clipped fluid-layer geometry needed no
  change under fabric-renderer-api-v1 3.4.1. 5B client fluid conversion — three
  `FluidVariant.of(fluid, CompoundTag)` sites (`FabricClientFluidColors` color/tint,
  `FabricFluidContainerModel.emitItemQuads`) now call the Stage 3 boundary helper
  `FabricFluidVariants.toVariant(StoredFluid)`. 5C Junk renderer — `NBTUtil.getStoredItems` now
  needs a `HolderLookup.Provider`; pass `minecraft.level.registryAccess()` with a null-level guard
  that yields an empty content list. `SpawnEggItem.byId`/`getColor`, `ItemProperties.register`,
  `ColorProviderRegistry.ITEM`, and the dynamic renderer / two-arg `ModelResourceLocation` usage in
  `SomeBucketsFabricClient` / `FabricJunkBucketRenderer` compiled unchanged; no further client
  signature breaks surfaced. `.\gradlew.bat :fabric:compileJava --console=plain` exit 0 — PASS
  (0 errors, down from 5); deprecation notes only; `:common:compileJava` UP-TO-DATE so Forge not
  re-compiled. No common change, no resource change, no build-file change. Primary gate for Stage 5
  met; session stops at the stage boundary.

- 2026-08-29: Stage 6 — Fabric GameTest source and resource port. Baseline diagnostic
  `:fabric:compileGametestJava` exit 1, 24 errors, all in `fabric/src/gametest` (none in the
  already-ported `common/src/gametest`), all classifiable to this stage. Fixes, mirroring the
  completed Forge gametest port where an equivalent file exists:
  `StateGameTests` (6 sites) `stack.getOrCreateTag().putString/getString("Unrelated", ...)` ->
  shared `GameTestSupport.updateCustomData` / `copyCustomData` component helpers.
  `BlockCapabilityGameTests` + `CauldronGameTests` `EventRecorder`: `GameEventListener.handleGameEvent`
  now takes `Holder<GameEvent>`; event list, `count(...)` param, and the override signature retyped
  to `Holder<GameEvent>` (import `net.minecraft.core.Holder`); the `recorder.count(GameEvent.FLUID_*)`
  call sites then resolve unchanged.
  `CauldronGameTests`: `CauldronInteraction.EMPTY/POWDER_SNOW` are `CauldronInteraction.InteractionMap`
  — `.containsKey(item)` -> `.map().containsKey(item)`; the `interact(...)` helper parameter is
  `CauldronInteraction.InteractionMap` and it returns `ItemInteractionResult` (via `interactions.map()
  .get(...)`), local result vars retyped, `.consumesAction()` unchanged; dropped the now-unused
  `java.util.Map` import.
  `LootGameTests`: `new ResourceLocation("minecraft", "chests/"+path)` -> `ResourceLocation
  .fromNamespaceAndPath(...)`; `server.getLootData().getLootTable(id)` -> `server.reloadableRegistries()
  .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id))` (imports `ResourceKey`, `Registries`).
  `GameTestSupport` (`fabric/src/gametest`): `SidedFluidBlockEntity` used
  `FluidVariant.of(fluid, CompoundTag)` and `variant.copyNbt()` -> Stage 3 boundary helper
  `FabricFluidVariants.toVariant(StoredFluid)` and `FabricFluidVariants.variantTag(FluidVariant)`.
  `.\gradlew.bat :fabric:compileGametestJava --console=plain` exit 0 — PASS (0 errors, down from 24);
  no new errors, no assertion or test removed/weakened. Discovery cross-checked: all 13 `*GameTests`
  classes are listed in `fabric/src/gametest/resources/fabric.mod.json` `fabric-gametest` entrypoints
  and every entry maps to a present class. `:common` UP-TO-DATE (no common change) so Forge not
  re-compiled. No resource or build-file change. Primary gate for Stage 6 met; session stops at the
  stage boundary.

- 2026-08-29: Stage 7 — Runtime GameTest stabilization. `:fabric:runGameTestServer --console=plain`
  run by the user. Fabric Loader 0.19.3 / Fabric API 0.116.15+1.21.1 / MC 1.21.1, mixin
  compatibility level JAVA_21, somebuckets + somebuckets-gametest loaded. 167 game tests discovered
  and run in 4 batches (50/50/50/17); "All 167 required tests passed :)" in 3.039 s; server saved
  all dimensions and shut down cleanly; BUILD SUCCESSFUL in 23s. One benign log line:
  `Ignoring unknown Source Bucket allowed content 'missingmod:removed_fluid' in SBGameTests` — an
  intentional fixture exercising the documented "unknown fluid ids are ignored and logged" path, not
  a failure. No production or test change required; no assertion touched; `AbstractFurnaceBlockEntityMixin`
  targets, the `ItemStackMixin`-removal stack sizes, the `FabricFluidVariants` round trip, and the
  new `reloadableRegistries().getLootTable(ResourceKey)` idiom all held at runtime. No common change,
  Forge not re-compiled. Primary gate for Stage 7 met on the first attempt; session stops at the
  stage boundary.

- 2026-08-29: Stage 8 — Final package and documentation reconciliation. `:fabric:build` run by the
  user (reported BUILD SUCCESSFUL) and the Fabric artifact was manually loaded and tested in game;
  one minor client issue observed, deferred by user decision ("fix later"). Documentation
  reconciled to the completed 1.21.1 state of both loaders:
  `as-built.md` — "Java 17 / Minecraft 1.20.1" -> "Java 21 / Minecraft 1.21.1"; added a note that a
  fourth `neoforge` subproject exists with build scaffolding only (no runtime Java); `StoredFluid`
  conversion paragraph now names `ForgeFluidStacks` and `FabricFluidVariants` (CompoundTag <->
  DataComponentPatch); removed the stale "Variable item stack size | Forge item-shell override |
  ItemStackMixin" cross-loader row (both loaders now rely on the common `MAX_STACK_SIZE` data
  component written by `NBTUtil`) and updated the `VariableStackItem` paragraph accordingly;
  furnace-consumption row now cites `forge/.../fuel/ForgeFuelEvents`; "Persistent item state" now
  states the whole schema lives in the built-in `minecraft:custom_data` component with
  `MAX_STACK_SIZE` maintained at the same write boundary, and the canonical-empty-state wording
  refers to the custom-data component rather than a raw root compound.
  `player-view.md` — one wording tweak: "expose a Forge fluid tank" -> "expose a loader fluid tank
  (Forge fluid capability or Fabric Transfer API storage)"; player behavior is otherwise unchanged
  by the port.
  `build-env.md` — already fully on the 1.21.1 baseline (updated during build setup); no change.
  No code, test, or resource change in Stage 8. No Git or GitHub action. Fabric 1.21.1 port
  complete: all completion gates pass (`:common:compileJava`, `:fabric:compileJava`,
  `:fabric:processResources`, `:fabric:compileGametestJava`, `:fabric:runGameTestServer` 167/167,
  `:fabric:build`). No `common` change was made anywhere in the port, so Forge remained green
  throughout and was never re-compiled. Deferred: NeoForge runtime implementation (out of scope,
  untouched — scaffolding only); one minor client issue seen in the manual smoke test, to be
  recorded and fixed later.
