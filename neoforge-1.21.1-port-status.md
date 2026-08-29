# NeoForge 1.21.1 Port Status

This is the compact execution snapshot. It is overwritten in place whenever the position changes and
is never appended to. Per-command history and completed-stage notes live in the append-only
`neoforge-1.21.1-port-log.md`, which is not read during execution.

`neoforge-1.21.1-port-process.md` governs how this snapshot is maintained.

## Current position

| Field | Value |
| --- | --- |
| Overall state | **COMPLETE** — all 8 stages done; three-loader 1.21.1 implementation shipped |
| Current stage | Stage 8 — done. Port finished. |
| Current work unit | None |
| Work-unit state | complete |
| Failed verification attempts used | 0 for Stage 7 WU1 (passing suite on the first post-fix run) |
| Stable documents read this session | Yes (`CLAUDE.md`, plan, process, status) |
| Forge compile state | Passing; Forge GameTests run green (user, Stage 8) |
| Fabric compile state | Passing; Fabric GameTests run green (user, Stage 8) |
| Last command | User ran `:neoforge:build` and the GameTest suites on all three loaders (Stage 8) |
| Last result | **All green** — NeoForge builds and packages; Forge, NeoForge, and Fabric GameTest suites all pass |
| Last updated | 2026-08-29 |

## Stage 7 outcome

Baseline run: 174 discovered, 2 required failures with distinct NeoForge-specific causes. One
correction pass (two NeoForge-only production files, no `common` change, no test change); post-fix
run **passed all 174** with a clean exit. **0 failed verification attempts** (baseline run is not an
attempt; the first post-implementation run passed).

**The two failures and their fixes (both now established decisions):**

1. `player_source_cauldron_round_trip_assigns_and_remains_infinite` — NeoForge 21.1 exposes a
   `Capabilities.FluidHandler.BLOCK` for vanilla cauldrons (Forge does not), so
   `SBFluidLogic`/`BBFluidLogic` routed cauldron take/place through the generic block path in
   `Transfers`, skipping `Cauldrons.complete()` → no `USE_CAULDRON` stat, no `FILLED_BUCKET`
   criterion, no cauldron game events. **Fix:** `neoforge/.../interaction/Transfers.java`
   `blockHandler()` returns `null` for any `AbstractCauldronBlock`, so the dedicated `Cauldrons`
   path owns cauldrons exactly as on Forge. Single chokepoint (all block-side lookups route through
   `blockHandler()`); the dispenser BB/SB behaviors already call `Cauldrons.*` first and are
   unaffected.
2. `powder_snow_place_event_cancellation_is_atomic` — NeoForge's `CommonHooks.onPlaceItemIntoWorld`
   defers `EntityPlaceEvent` until after `useOn` returns, and its held-stack rollback restores from
   a **live `DataComponentMap` reference** that cannot undo a `custom_data` mutation made during
   `useOn`; a canceled place still debited the Big Bucket. **Fix:** `neoforge/.../fluid/BBFluidLogic.java`
   `tryPlacePowder`, player path only (`level.captureBlockSnapshots` already true): fire
   `EventHooks.onBlockPlace`/`onMultiBlockPlace` for the captured snapshot before debiting, then
   finalize the snapshot in place — restore on cancel (return `false`, no debit), else
   `BlockState.onPlace` + `level.markAndNotifyBlock` and debit — and clear
   `level.capturedBlockSnapshots` so the outer wrapper does not re-fire. Dispenser path
   (`captureBlockSnapshots` false) keeps the direct `place()` behavior. `ITEM_USED` accounting is
   unchanged (the wrapper still awards it once on success via its empty-snapshot `else` branch).

## Stage 6 outcome

Authorized `neoforge/build.gradle` construction (gametest source set + Loom `somebuckets_gametest`
mod + `runs.gameTestServer` + `configureGameTestStructures`), 18 gametest Java files under
`neoforge/src/gametest/java`, 2 gametest resources, and a 2-file **production fuel correction** under
`neoforge/src/main` (see established decisions). `:neoforge:compileGametestJava` **passed on the
first try (0 failed attempts)** — the genuinely-passing gate Stage 6 requires. No `common` change;
no Forge/Fabric regression guard needed. Only warning is the pre-existing `SharedGameTestSupport`
deprecation note (present on Forge/Fabric too).

**NeoForge `1.21.1` GameTest API confirmed (from published NeoForge `1.21.1` source + a passing compile):**

- **`net.neoforged.neoforge.gametest.GameTestHolder`** (`String value()`, default `"minecraft"`) and
  **`net.neoforged.neoforge.gametest.PrefixGameTestTemplate`** (`boolean value()`, default `true`).
- Template id built by the patched `GameTestRegistry.turnMethodIntoTestFunction` as
  `getTemplateNamespace(m) + ":" + (prefix ? classname_lc + "." : "") + template`. `getTemplateNamespace`
  = `@GameTest.templateNamespace()` else `@GameTestHolder.value()` else `"minecraft"`; `prefix` =
  `true` unless `@PrefixGameTestTemplate(false)`. Unlike Forge, the namespace and class-name prefix
  are applied **even when `template` already contains a namespace** → a bare Forge-style
  `template = "somebuckets:empty_9x6x9"` resolves to `somebuckets:bbgametests.somebuckets:empty_9x6x9`.
  ⇒ every NeoForge test class carries `@GameTestHolder(SomeBuckets.MODID)` **and**
  `@PrefixGameTestTemplate(false)`, and `GameTestSupport.TEMPLATE = "empty_9x6x9"` (bare path). Effective
  id `somebuckets:empty_9x6x9` → `data/somebuckets/structure/empty_9x6x9.nbt` (written by the shared
  `configureGameTestStructures`, same as Forge/Fabric).
- Discovery: `GameTestHooks.registerGametests()` scans `ModList` scan data for `@GameTestHolder`, adds
  every declared method, and `GameTestRegistry.register(method, enabledNamespaces)` keeps a `@GameTest`
  method only if `enabledNamespaces` contains its template namespace. No `RegisterGameTestsEvent`
  needed — same model as Forge. Run property is **`neoforge.enabledGameTestNamespaces`** (set to
  `mod_id`).
- `forgeTemplate 'gameTestServer'` in `loom { runs {} }` is valid for NeoForge
  (`ModPlatform.assertForgeLike` → FORGE || NEOFORGE); no separate `neoForgeTemplate`. Kept
  `environment 'gametestserver'` and `server()` from the Forge shape.
- Block-capability test fixture: NeoForge has no per-BE `getCapability` override, and
  `RegisterCapabilitiesEvent#registerBlockEntity` binds the provider parameter to
  `BlockEntityType.STRUCTURE_BLOCK`'s declared `StructureBlockEntity` type (the fixture doesn't
  extend it). So `SomeBucketsGameTestMod`'s `@Mod(IEventBus modEventBus)` constructor adds a
  `RegisterCapabilitiesEvent` listener calling
  `event.registerBlock(Capabilities.FluidHandler.BLOCK, (level,pos,state,be,side) -> be instanceof
  SidedFluidBlockEntity s && side == s.exposedFace ? s.handler : null, Blocks.STRUCTURE_BLOCK)`.
  The fixture now exposes a plain `net.neoforged.neoforge.fluids.capability.templates.FluidTank`.
- Event buses in tests: `net.neoforged.neoforge.common.NeoForge.EVENT_BUS`;
  `IEventBus.addListener(Class<T>, Consumer<T>)` and
  `addListener(EventPriority, boolean, Class<T>, Consumer<T>)` (`net.neoforged.bus.api.EventPriority`);
  `post(T)` returns the event; `unregister(Object)`.
  `net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent` (`getPos()`, `ICancellableEvent`),
  `net.neoforged.neoforge.event.entity.EntityJoinLevelEvent` (`getLevel()`, `getEntity()`,
  `ICancellableEvent`),
  `net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(Player, InteractionHand)`.
- Fuel query in tests: **`ItemStack#getBurnTime(RecipeType<?>)`** (`IItemStackExtension`) replaces
  Forge's `ForgeHooks.getBurnTime(stack, RecipeType)`. It throws `IllegalStateException` if the item
  hook returns a negative value — see the Stage-4 fuel correction.

## Stage 5 outcome

Eight `client/` files + four item model JSONs. NeoForge `1.21.1` client-model API was resolved
up front from NeoForge's published source, so the compile **passed on the first try (0 failed
attempts)** — the genuinely-passing `:neoforge:compileJava` that Stage 5 requires. No `common`
change; no Forge/Fabric regression guard.

**NeoForge `1.21.1` client API confirmed (from published source + a passing compile):**

- `net.neoforged.neoforge.client.model.DynamicFluidContainerModel` exists with the Forge API intact:
  `implements IUnbakedGeometry<DynamicFluidContainerModel>`, `withFluid(Fluid)`, `Loader.INSTANCE`,
  `Loader.read(JsonObject, JsonDeserializationContext)`. `IUnbakedGeometry.bake(IGeometryBakingContext,
  ModelBaker, Function<Material,TextureAtlasSprite>, ModelState, ItemOverrides)` and
  `IGeometryLoader.read` are identical to Forge. Geometry classes live under
  `net.neoforged.neoforge.client.model[.geometry]`.
- `net.neoforged.neoforge.client.event.ModelEvent`: `RegisterGeometryLoaders.register(ResourceLocation
  key, IGeometryLoader<?> loader)` (key is a `ResourceLocation`, not Forge's `String`);
  `ModifyBakingResult.getModels()` → `Map<ModelResourceLocation, BakedModel>` (mutable).
- **No `IItemExtension#initializeClient`, no `RegisterClientExtensionsEvent` in `client/event/`** on
  NeoForge `1.21.1`. The client item-extensions event is
  `net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent` (mod bus) with
  `registerItem(IClientItemExtensions extensions, Item... items)`.
  `IClientItemExtensions.getCustomRenderer()` → `BlockEntityWithoutLevelRenderer`. ⇒ no
  `NeoForge{JB,TB,MB}Item` subclass — `ModItems` keeps the base classes; `ClientSetup` wires the
  shared `JBRenderer` extension to `JUNK_BUCKET` + `TRASH_BUCKET`.
- `net.neoforged.neoforge.client.event.{RegisterColorHandlersEvent.Item, RegisterClientReloadListenersEvent}`;
  `net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions` (`of(Fluid)` /
  `getTintColor(FluidStack)` / `getStillTexture(FluidStack)`);
  `net.neoforged.neoforge.client.model.{BakedModelWrapper, IQuadTransformer}` (`STRIDE/POSITION/COLOR/UV0/NORMAL`),
  `net.neoforged.neoforge.client.model.data.ModelData`.
- `net.neoforged.api.distmarker.{Dist, OnlyIn}`; `net.neoforged.fml.loading.FMLEnvironment.dist`
  replaces the removed `DistExecutor`; `net.neoforged.fml.event.lifecycle.FMLClientSetupEvent`;
  `@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD)`.
- Vanilla: `ModelResourceLocation.inventory(ResourceLocation)`; `SpawnEggItem.byId(EntityType)`
  (replaces `ForgeSpawnEggItem.fromEntityType`, matches Fabric).

## Stage 4 outcome

Seven files written + two edits + one `neoforge/build.gradle` addition. Run 1 had 6 errors: 2 real
(NeoForge `21.1` `ICustomIngredient` declares `Stream<ItemStack> getItems()`, not
`Stream<Holder<Item>> items()` — the `1.21.x` branch and the disposition inventory were wrong for
`21.1.248`), 4 the Stage 5 `client.SidedFluidColors` forward ref. One correction pass. Run 2:
`:neoforge:processResources` **passed** (`generateBucketLootModifiers` emitted 8 JSON files);
`:neoforge:compileJava` fails with exactly 2 errors, both `client.SidedFluidColors` (Stage 5). Every
Stage 4 file compiles clean → **Stage 4 pass**. 1 failed attempt. No `common` change; no
Forge/Fabric regression guard needed.

**GLM resources verified deterministically** (`neoforge/build/generated/loot/resources/`):
`data/neoforge/loot_modifiers/global_loot_modifiers.json` (`replace: false`, 7 `somebuckets:`
entries) + 7 `data/somebuckets/loot_modifiers/<id>.json` (one per manifest reward); every condition
is `neoforge:loot_table_id`; zero bare `forge:` ids; zero `data/forge/` output; all 8 JSON files
parse.

**NeoForge server API confirmed by the passing Stage-4 compile** (reuse in Stage 5+):

- `net.neoforged.neoforge.common.crafting.ICustomIngredient`: `boolean test(ItemStack)`,
  **`Stream<ItemStack> getItems()`**, `boolean isSimple()`, `IngredientType<?> getType()`.
- `IngredientType<T extends ICustomIngredient>` is a record
  `(MapCodec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)`; a one-arg
  `(MapCodec<T>)` convenience ctor also exists. Registered on
  `NeoForgeRegistries.Keys.INGREDIENT_TYPES` via `DeferredRegister<IngredientType<?>>`.
- `net.neoforged.neoforge.common.loot.{LootModifier, IGlobalLootModifier}` retain Forge's
  `codecStart` / `doApply` / `codec()` shape; serializer registry key
  `NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS`; use `DeferredHolder`, not
  `RegistryObject`.
- Vanilla `CauldronInteraction.{EMPTY,WATER,LAVA,POWDER_SNOW}.map().put(item, fn)` and
  `ItemInteractionResult.{sidedSuccess, PASS_TO_DEFAULT_BLOCK_INTERACTION}` unchanged from
  Forge/Fabric 1.21.1.
- Events: `net.neoforged.fml.common.EventBusSubscriber` (no `bus` element needed → game bus);
  `net.neoforged.bus.api.SubscribeEvent(priority = …, receiveCanceled = false)` +
  `net.neoforged.bus.api.EventPriority`; `net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem`
  with `isCanceled()` / `setCanceled(boolean)` / `setCancellationResult(InteractionResult)` /
  `getEntity()` → `Player` / `getHand()`.
- Fuel: `IItemExtension#getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType)` → `int`
  — **see the Stage-6 correction: the return must be `>= 0` (`0` = not fuel), not Forge's `-1`.**
  No `FurnaceFuelBurnTimeEvent` override, no mixin.

## Stage 3 outcome

Eleven files written under `neoforge/src/main/java`; all compile in isolation of the missing
Stage 4/5 classes they reference. Run 1 (`:neoforge:compileJava`) had 23 errors: 4 real Stage-3
defects + 19 forward refs. One correction pass fixed the 4; run 2 has 17 errors, every one a
Stage 4/5 forward reference. No error in any Stage 3 file's own logic. No `common` change.

**The 4 real Stage-3 defects and their fixes:**

- `net.neoforged.neoforge.fluids.IFluidBlock` and `...capability.wrappers.FluidBlockWrapper` **do
  not exist on NeoForge** (legacy Forge API dropped). Fix: `fluid/FluidPickup` keeps only the
  vanilla `BucketPickup` branch — modern modded source blocks implement it directly, as Fabric
  already relies on.
- `BucketPickupHandlerWrapper`'s constructor is **`(Player, BucketPickup, Level, BlockPos)`** on
  NeoForge. Fix: `handlerFor` threads a `@Nullable Player` (null for the simulate-only `available`
  query, the real player for `take`).
- `net.neoforged.neoforge.event.EventHooks` has **no `onBucketUse`** (NeoForge 1.21.1 has no
  `FillBucketEvent` successor). Fix: `NeoForgeBucketOperations.beforeWorldBucketUse` returns `null`,
  identical to `FabricBucketOperations`.

**NeoForge fluid/capability API confirmed by the passing Stage-3 compile:**

- `new FluidStack(Fluid, int)` 2-arg ctor works; `FluidStack#applyComponents(DataComponentPatch)`,
  `#getComponentsPatch()`, `#copy()`, `#setAmount(int)`, `#getHoverName()` all present;
  `FluidStack.isSameFluidSameComponents(a, b)` static. **No `getTag()`**.
- `net.neoforged.neoforge.fluids.FluidType`: `BUCKET_VOLUME`, `getSound(SoundAction)` (contextless
  form compiles), `isVaporizedOnPlacement(level, pos, fs)`, `canBePlacedInLevel(level, pos, fs)`;
  `Fluid#getFluidType()`.
- `net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM` (`ItemCapability`) and
  `.BLOCK` (`BlockCapability<IFluidHandler, Direction>`);
  `RegisterCapabilitiesEvent#registerItem(cap, (stack, ctx) -> handler, Item...)`;
  `RegisterCapabilitiesEvent#registerBlock(BlockCapability, IBlockCapabilityProvider, Block...)` with
  provider `(Level, BlockPos, BlockState, @Nullable BlockEntity, C) -> @Nullable T`;
  `level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face)` → `@Nullable IFluidHandler`
  (no `LazyOptional`); `stack.getCapability(Capabilities.FluidHandler.ITEM)` → `@Nullable IFluidHandlerItem`.
- `net.neoforged.neoforge.fluids.capability.{IFluidHandler, IFluidHandlerItem}` retained;
  `IFluidHandler.FluidAction.SIMULATE/EXECUTE`; `net.neoforged.neoforge.fluids.FluidUtil`
  `tryFluidTransfer(dest, src, max, doTransfer)` / `tryPlaceFluid(player, level, hand, pos, src, fs)`;
  `...capability.wrappers.{FluidBucketWrapper, FluidBucketWrapper(ItemStack)}`;
  `...capability.templates.FluidTank(int)` (`setFluid`/`getFluid`); `net.neoforged.neoforge.common.SoundActions`.

## Stage 2 outcome

Eight files written under `neoforge/src/main/java`; all compile.

**NeoForge API facts confirmed by this compile** (reuse in later stages):

- `@Mod(MODID)` on a `final` class; constructor injects `net.neoforged.bus.api.IEventBus modEventBus,
  net.neoforged.fml.ModContainer modContainer`. A single-`IEventBus` constructor is also valid (used
  by the Stage-6 `SomeBucketsGameTestMod` stub).
- `net.neoforged.fml.ModList.get().isLoaded("ftbchunks")` for optional-mod detection.
- `modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, spec)`;
  `net.neoforged.fml.event.config.ModConfigEvent.Loading` / `.Reloading` with
  `event.getConfig()`, `config.getSpec()`, `config.getFileName()`;
  `net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent` with `event.enqueueWork(Runnable)`.
- `net.neoforged.neoforge.common.ModConfigSpec` (Forge's `ForgeConfigSpec`): `ModConfigSpec.Builder`,
  `.push`/`.comment`/`.pop`/`.build`, `ModConfigSpec.ConfigValue<List<? extends String>>` with
  `.get()`/`.set(...)`, `defineListAllowEmpty(String path, Supplier default, Supplier newElement,
  Predicate validator)`.
- `net.neoforged.neoforge.registries.DeferredRegister` / `.create(ResourceKey<Registry<T>>, String)`
  with vanilla `Registries.*` keys; `net.neoforged.neoforge.registries.DeferredHolder<R, T>` with
  `.get()`; `deferredRegister.register(IEventBus)`.
- `net.neoforged.neoforge.common.util.FakePlayerFactory.get(ServerLevel, GameProfile)` → `ServerPlayer`.
- `IItemExtension#hasCraftingRemainingItem(ItemStack)` / `#getCraftingRemainingItem(ItemStack)` and
  `IItemStackExtension#getCraftingRemainingItem()` overridable/available on NeoForge.
- `new Item.Properties()` needs no id in 1.21.1 / NeoForge 21.1.

## Stage 1 outcome

- **`:common:compileJava` passes** (exit 0, UP-TO-DATE) — unchanged since the Forge and Fabric
  ports left it green.
- **Loader-import scan clean.** `common/src/main/java` has only the five sanctioned
  `net.fabricmc.api.EnvType` / `Environment` client imports.
- **`StoredFluid` confirmed** as `record StoredFluid(Fluid fluid, int amount, @Nullable CompoundTag
  variantTag)`.
- **Fluid-conversion approach ratified by the user**: mirror Fabric exactly.

## Stage 0 outcome

- **Build wiring verified.** Gradle resolves `:neoforge`; Architectury Loom `1.17.491` / Plugin
  `3.5.169`; NeoForge `21.1.248` and `ftb-chunks-neoforge:2101.1.21` resolve.
- **`neoforge.mods.toml` token check passed.**

## Disposition inventory

Source of truth for stage assignment. Every file in `forge/src/main` plus NeoForge-specific new
files. Dispositions: **A** copy w/ import swap; **B** renamed NeoForge event/registration/lifecycle
API; **C** capability-system rewrite; **D** 1.21.1 boundary conversion via the Fabric precedent; **N**
NeoForge-specific new file; **G** produced by `neoforge/build.gradle`. Package root unchanged.

### Stage 2 — bootstrap, registration, config, identifiers, metadata — **DONE**

`SomeBucketsNeoForge`, `register/ModItems`, `ModSounds`, `ModCreativeTabs`, `config/ServerConfig`,
`item/NeoForgeBBItem`, `item/NeoForgeSBItem`, `protection/NeoForgeDispenserFakePlayer`, FTB Chunks
provider wiring — all written and compiling.

### Stage 3 — capability layer and fluid transfer core — **DONE**

`util/NeoForgeFluidStacks`, `fluid/FluidProvider`, `fluid/AbstractFluidHandler`, `fluid/BBFluidHandler`,
`fluid/SBFluidHandler`, `fluid/BBFluidLogic`, `fluid/SBFluidLogic`, `fluid/FluidPickup`,
`fluid/NeoForgeFluidPlacement`, `platform/NeoForgeBucketOperations`, `interaction/Transfers` — all
written and compiling.

### Stage 4 — server systems — **DONE**

`crafting/EmptyBucketIngredient`, `crafting/SpawnEggIngredient`, `loot/AddBucketLootModifier`,
`register/ModLootModifiers`, `interaction/Cauldrons`, `interaction/Dispensers`,
`interaction/NeoForgeHeldTransferEvents`, `item/NeoForge{BB,SB}Item` fuel hooks, and the
`neoforge/build.gradle` GLM generator — all written; `:neoforge:processResources` passes.

### Stage 5 — client — **DONE**

Eight `client/` files + four item model JSONs; `:neoforge:compileJava` **passes with 0 errors**.

### Stage 6 — GameTest — **DONE**

| Item | Disp | Result |
| --- | --- | --- |
| `neoforge/build.gradle` gametest wiring | G | **DONE.** `gametest` source set (`srcDir common/src/gametest/java` + `compileClasspath/runtimeClasspath` from `sourceSets.main`); `loom.mods.somebuckets_gametest`; `loom.runs.gameTestServer` (`server()`, `environment 'gametestserver'`, `forgeTemplate 'gameTestServer'`, `property 'neoforge.enabledGameTestNamespaces', mod_id`, `source sourceSets.gametest`); `rootProject.configureGameTestStructures(project)`. |
| `neoforge/src/gametest/resources/META-INF/neoforge.mods.toml` | B | **DONE.** GameTest variant, hardcoded values (not token-expanded — `processGametestResources` has no `expand`); deps on `neoforge` / `minecraft` / `somebuckets`. |
| `neoforge/src/gametest/resources/pack.mcmeta` | A | **DONE.** `pack_format` 15 (1.21.1), verbatim from Forge. |
| `gametest/SomeBucketsGameTestMod` | A/N | **DONE.** `@Mod("somebuckets_gametest")` + `(IEventBus modEventBus)` ctor adding the `RegisterCapabilitiesEvent` → `registerBlock(Capabilities.FluidHandler.BLOCK, …, Blocks.STRUCTURE_BLOCK)` listener for the sided-tank fixture. |
| `gametest/GameTestSupport` | B/C | **DONE.** Forge helpers verbatim (`big8/big64/source/junk/trash/mob`, `tryBig/Powder/Source…WithContext` via common `BBFluidLogic`/`SBFluidLogic` singletons); `TEMPLATE = "empty_9x6x9"` (bare); `SidedFluidBlockEntity` rewritten around a plain `FluidTank` + `registerTestCapabilities`. |
| `gametest/{BB,MB,Automation,Presentation,Protection,RecipeAndFuel,StorageBucket}GameTests` | A | **DONE.** `@GameTestHolder` swap + `@PrefixGameTestTemplate(false)`; bodies delegate to shared `*Scenarios` unchanged. |
| `gametest/CauldronGameTests` | A/B | **DONE.** + `net.minecraftforge.fluids.FluidType` → `net.neoforged.neoforge.fluids.FluidType`. |
| `gametest/StateGameTests` | C | **DONE.** + `ForgeCapabilities.FLUID_HANDLER_ITEM.orElseThrow` → `Capabilities.FluidHandler.ITEM` null-check; `net.minecraftforge.fluids.*` → `net.neoforged.neoforge.fluids.*`; dropped unused `ForgeFluidStacks`; "Forge fluid" message → "NeoForge fluid". |
| `gametest/SBGameTests` | C | **DONE.** Inline allow-list test: caps null-check as above; `ForgeHooks.getBurnTime(x, RecipeType)` → `x.getBurnTime(RecipeType)`. |
| `gametest/BlockCapabilityGameTests` | C | **DONE.** + `net.minecraftforge.fluids.FluidStack` → `net.neoforged.neoforge.fluids.FluidStack`; fixture + `assertTank` unchanged in shape. |
| `gametest/NeoForgeFuelGameTests` | B/N | **DONE** (was `ForgeFuelGameTests`). `ForgeHooks.getBurnTime` → `ItemStack#getBurnTime(RecipeType)`; method names kept. |
| `gametest/NeoForgeOnlyBBGameTests` | B/N | **DONE** (was `ForgeOnlyBBGameTests`). `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`; `net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent` (`addListener(Class, Consumer)`). Powder-snow place-event cancellation atomicity. |
| `gametest/NeoForgeOnlyMBGameTests` | B/N | **DONE** (was `ForgeOnlyMBGameTests`). `net.neoforged.neoforge.event.entity.EntityJoinLevelEvent`. Rejected aquatic spawn preserves water + snapshot. |
| `gametest/TransferGameTests` | A/B | **DONE.** Two event-bus tests: `NeoForge.EVENT_BUS`, `net.neoforged.bus.api.EventPriority`, `net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(Player, InteractionHand)`. |
| `forge/.../FillBucketEventGameTests` | — | **NOT PORTED.** NeoForge 1.21.1 has no fill-bucket event (`NeoForgeBucketOperations.beforeWorldBucketUse` returns `null`). No analog exists. |

### Stage 4 fuel correction (made during Stage 6)

`item/NeoForgeBBItem` and `item/NeoForgeSBItem`: `getBurnTime(...)` non-lava return changed
**`-1` → `0`**. NeoForge 1.21.1's `IItemStackExtension#getBurnTime` throws `IllegalStateException`
when the `IItemExtension#getBurnTime` item hook returns a negative value; the NeoForge contract is
`>= 0` with `0` meaning "not fuel". Neither bucket item has a `furnace_fuels.json` entry to defer
to, so `0` is behaviorally identical to Forge's `-1` "defer to vanilla". `neoforge/src/main` only —
no `common` change, no Forge/Fabric regression guard. Verified by the passing
`:neoforge:compileJava` re-run and by `NeoForgeFuelGameTests` reading `ItemStack#getBurnTime`.

## Completion gate record

| Gate | Status | Evidence |
| --- | --- | --- |
| `:common:compileJava` | Passing | exit 0, UP-TO-DATE (2026-08-29) |
| `:forge:compileJava` (regression guard) | Not re-run | Required only if `common` changes; none made |
| `:fabric:compileJava` (regression guard) | Not re-run | Required only if `common` changes; none made |
| `:fabric:compileGametestJava` (regression guard) | Not re-run | Required only if shared GameTest code changes; none made |
| `:neoforge:compileJava` | **Passing** | BUILD SUCCESSFUL, 0 errors (re-ran green after the fuel fix, 2026-08-29) |
| `:neoforge:processResources` | Passing | `generateBucketLootModifiers` emits 8 valid `neoforge:`-namespaced JSON files (2026-08-29) |
| `:neoforge:compileGametestJava` | **Passing** | BUILD SUCCESSFUL, 0 errors, first try (2026-08-29) |
| `:neoforge:runGameTestServer` | **Passing** | BUILD SUCCESSFUL, "All 174 required tests passed", clean server exit (2026-08-29, after Stage 7 WU1 fix) |
| `:neoforge:build` | **Passing** | User-run (Stage 8); artifact produced |
| Forge + Fabric GameTest suites (regression) | **Passing** | User-run on all three loaders (Stage 8); no regression from the two Stage 7 `neoforge/src/main` fixes (no `common`/Forge/Fabric change) |

## Established technical decisions

- **`StoredFluid` ↔ NeoForge `FluidStack` — RATIFIED (Stage 1); IMPLEMENTED (Stage 3B):**
  `NeoForgeFluidStacks` converts the optional variant `CompoundTag` ↔ `DataComponentPatch` via
  `DataComponentPatch.CODEC` over plain `NbtOps.INSTANCE`; registry-context components degrade to a
  blank patch. `StoredFluid`'s common shape unchanged. Round trip exercised by the Stage 7 GameTests.
- **`beforeWorldBucketUse` returns `null` on NeoForge (Stage 3):** no pre-dispatch bucket-use event
  exists. FTB Chunks protection runs through `ClaimProtectionProvider`. No documented behavior
  regresses. ⇒ `FillBucketEventGameTests` has no NeoForge analog (Stage 6).
- **`fluid/FluidPickup` drops the legacy fluid-block branch (Stage 3):** NeoForge has no
  `IFluidBlock` / `FluidBlockWrapper`; world pickup uses only vanilla `BucketPickup`, matching Fabric.
- **Fuel — item hook, corrected in Stage 6:** `IItemExtension#getBurnTime(ItemStack, @Nullable
  RecipeType<?>)` on `NeoForge{BB,SB}Item` returns `LAVA_BUCKET_BURN_TIME_TICKS` for allowed lava,
  else **`0`** (NOT `-1` — NeoForge forbids a negative item-hook result). No event, no mixin. SB stays
  permanent fuel via its unchanged crafting remainder.
- **Custom ingredients (Stage 4A):** NeoForge `21.1` `ICustomIngredient` declares
  `Stream<ItemStack> getItems()`. `IngredientType<T>` record on `NeoForgeRegistries.Keys.INGREDIENT_TYPES`.
  Recipe JSON unchanged.
- **GLM data namespace (Stage 4B):** `neoforge:loot_table_id`;
  `data/neoforge/loot_modifiers/global_loot_modifiers.json`. Rewards still from common `bucket_loot.json`.
- **NeoForge GameTest wiring (Stage 6):** every test class carries
  `@GameTestHolder(SomeBuckets.MODID)` **and** `@PrefixGameTestTemplate(false)`; `TEMPLATE` is the
  bare path `"empty_9x6x9"` because NeoForge prepends the namespace and (by default) the class name
  to the `@GameTest.template` value even when it already has a namespace. Run property
  `neoforge.enabledGameTestNamespaces = somebuckets`. `forgeTemplate 'gameTestServer'` is the
  NeoForge run template (no `neoForgeTemplate`). Fixture block capability is registered by the
  gametest `@Mod` stub via `RegisterCapabilitiesEvent#registerBlock` for `Blocks.STRUCTURE_BLOCK`.
- **FTB Chunks:** NeoForge keeps the integration (real `ftb-chunks-neoforge:2101.1.21`).
- **Mixins:** NeoForge ships no mixin config. Confirmed through Stage 7 (runtime).
- **Vanilla cauldrons bypass the generic fluid-capability path (Stage 7):** NeoForge 21.1 exposes
  `Capabilities.FluidHandler.BLOCK` for cauldrons; `Transfers.blockHandler()` returns `null` for
  `AbstractCauldronBlock` so the dedicated `Cauldrons` path owns every cauldron interaction (stat,
  criterion, game events) exactly as on Forge.
- **Powder-snow place-event atomicity (Stage 7):** in the player `useOn` path,
  `BBFluidLogic.tryPlacePowder` fires `EventHooks.onBlockPlace` and finalizes the captured block
  snapshot itself (restore-on-cancel before any debit; `markAndNotifyBlock` + debit on success;
  clears `capturedBlockSnapshots`). Required because NeoForge's `CommonHooks.onPlaceItemIntoWorld`
  defers the event and its live-`DataComponentMap` held-stack rollback cannot undo a `custom_data`
  debit. Dispenser path unchanged.

## Blockers

None.

## Next action

None — the NeoForge 1.21.1 port is complete. Stage 8 done: the user built `:neoforge:build` and ran
the GameTest suites on all three loaders (all green), and `as-built.md`, `player-view.md`, and
`build-env.md` (plus the `build-env/neoforge/build.gradle` snapshot copy) have been reconciled to the
three-loader 1.21.1 state. No Git or GitHub action performed.

### Recommended manual client smoke test (not an unattended gate)

Per the plan, a later human client smoke test on NeoForge should check: Big/Huge/Source fluid tint
(including a variant-bearing modded fluid if available); milk and powder-snow overrides; Mob Bucket
empty/filled model and spawn-egg colors; Junk Bucket protruding item order, tint, and glint;
creative-tab contents and prefilled variants; tooltips, bars, use animations, and sounds; a
dispenser acting inside an FTB Chunks claim as the `[SomeBuckets]` fake player.

### Resolved Stage 7 watch-items (all confirmed green at runtime, 2026-08-29)

- **Structure discovery:** all 174 tests discovered and ran; template id resolution correct
  (`data/somebuckets/structure/empty_9x6x9.nbt`).
- **`neoforge.enabledGameTestNamespaces = somebuckets`:** log shows "Enabled Gametest Namespaces:
  [somebuckets]", 174 tests discovered (>0).
- **`forgeTemplate 'gameTestServer'` + `environment 'gametestserver'`:** run launched, server
  bootstrapped and exited cleanly.
- **Fixture block capability:** `BlockCapabilityGameTests` passed — `SomeBucketsGameTestMod` wires
  `RegisterCapabilitiesEvent#registerBlock` correctly.
- **`NeoForgeOnly{BB,MB}GameTests`:** mob release fires `EntityJoinLevelEvent` (MB test passes).
  Powder-snow placement does **not** fire `EntityPlaceEvent` synchronously inside `place()` on
  NeoForge (deferred by `CommonHooks.onPlaceItemIntoWorld`) — handled by the Stage 7 WU1
  `tryPlacePowder` fix; BB test now passes.
- **`SharedGameTestSupport` deprecation note** is pre-existing (Forge/Fabric too) — not an issue.
