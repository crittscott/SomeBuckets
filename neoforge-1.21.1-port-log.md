# NeoForge 1.21.1 Port Log

Append-only history of the NeoForge 1.21.1 port. Entries are added during execution and are never
edited or removed. Nothing in the standard work loop reads this file; it exists as the audit trail
for a human reviewer. The live execution position is in `neoforge-1.21.1-port-status.md`.

## Verification history

- 2026-08-29: Port document set created (`neoforge-1.21.1-port-assessment.md`,
  `neoforge-1.21.1-port-plan.md`, `neoforge-1.21.1-port-process.md`,
  `neoforge-1.21.1-port-status.md`, and this log), adapted from the completed
  `fabric-1.21.1-port-*.md` set. The task is construction, not migration: `neoforge/` holds
  `build.gradle`, `gradle.properties`, and a tokenized `neoforge.mods.toml` only — no loader Java,
  no GameTest source set or run, no loot-modifier generator. `forge/src/main` (37 files) is the
  structural template; `fabric/src/main` and the Fabric port documents are the precedent for the
  1.21.1 boundary conversions (component-payload fluid values, codec custom ingredients,
  `MAX_STACK_SIZE` component). Both the Forge and Fabric 1.21.1 ports are complete and must not
  regress. No code, resources, or build files changed. Execution has not begun; the Stage 0
  build-wiring verification and per-file disposition inventory is the next action.

- 2026-08-29: Stage 0 — Baseline build-wiring verification and disposition inventory.
  Command `./gradlew.bat :neoforge:compileJava --console=plain`, exit 0, `BUILD SUCCESSFUL in 5s`:
  `:common:compileJava` UP-TO-DATE (passing), `:neoforge:compileJava` executed against an empty
  source set (no output). Delta: none (first run). Confirmed: Gradle resolves the `:neoforge`
  subproject; Architectury Loom 1.17.491 / Architectury Plugin 3.5.169; the `common` dependency
  (`namedElements` + `transformProductionNeoForge` bundle), NeoForge `21.1.248`, and
  `ftb-chunks-neoforge:2101.1.21` all resolve without error. Shadow / `remapJar` are wired in
  `neoforge/build.gradle` but only run under `:neoforge:build` (deferred to the Stage 8 gate).
  `neoforge.mods.toml` token check passed — every `${...}` placeholder is supplied by
  `neoforge/build.gradle` `replaceProperties` and resolves from `gradle.properties`; `logoFile`
  target `somebuckets-logo-1.png` exists at `common/src/main/resources/` (shadow-bundled, as on
  Forge). No gametest metadata exists yet (Stage 6 builds it). Full per-file disposition inventory
  (all 37 `forge/src/main` files plus NeoForge-specific new files, classified A/B/C/D/N/G against
  Stages 2–6) recorded in the snapshot; it replaces the Fabric port's error classification as the
  stage-assignment source of truth. No code, resource, or build-file change. First Stage 1 work
  unit recorded in the snapshot. Primary gate for Stage 0 met; session stops at the stage boundary.

- 2026-08-29: Stage 1 — Common under the NeoForge transform. Command
  `./gradlew.bat :common:compileJava --console=plain`, exit 0, `:common:compileJava` UP-TO-DATE,
  0 errors. Delta from Stage 0: none. Loader-import scan of `common/src/main/java`: no
  `net.minecraftforge` / `net.neoforged` / `dev.architectury` / `cpw.mods`; the only
  non-`net.minecraft` imports are `net.fabricmc.api.EnvType` / `Environment` on five client classes
  (`BucketMouth`, `ClientTextureColors`, `DelegatingBakedModel`, `JunkIconLayout`,
  `JunkForegroundGeometry`) — the sanctioned cross-remapped annotation. No NeoForge-transform-only
  common regression. `StoredFluid` confirmed as `record (Fluid, int, @Nullable CompoundTag)`,
  loader-neutral. Fluid-conversion decision ratified by the user: a NeoForge-module-only
  `NeoForgeFluidStacks` helper doing `CompoundTag` <-> `DataComponentPatch` via
  `DataComponentPatch.CODEC` over plain `NbtOps.INSTANCE` (graceful degradation), `StoredFluid`
  unchanged — the exact analog of `ForgeFluidStacks` / `FabricFluidVariants`. No code, resource, or
  build-file change; Forge and Fabric not re-compiled (common unchanged). First Stage 2 work unit
  recorded in the snapshot. Primary gate for Stage 1 met; session stops at the stage boundary.

- 2026-08-29: Stage 2 — NeoForge bootstrap, registration, identifiers, metadata. Eight files
  written under `neoforge/src/main/java` (see "Files changed" below). Command
  `./gradlew.bat :neoforge:compileJava --console=plain`, BUILD FAILED, 14 errors. Delta from Stage 0
  (empty): +14, every one in `SomeBucketsNeoForge.java` and every one a classified forward reference
  to a not-yet-written later-stage class — Stage 3: `fluid.FluidProvider` (import + `.register`
  call), `platform.NeoForgeBucketOperations` (import + `new`); Stage 4A: `crafting` package absent
  (2 imports), `EmptyBucketIngredient.register`, `SpawnEggIngredient.register`; Stage 4B:
  `register.ModLootModifiers` (import + `.register` call); Stage 4C: `interaction.Cauldrons` /
  `interaction.Dispensers` (2 imports + 2 `.register()` calls). No error in any Stage 2 file; the
  shared `common/src/compat/java/.../FtbChunksProtection` compiled against `ftb-chunks-neoforge`.
  Per `neoforge-1.21.1-port-process.md` an error naming only a later-stage symbol is not a failed
  attempt for a pre-Stage-5 diagnostic gate — this is a Stage 2 success (0 attempts used). NeoForge
  API surface confirmed by the clean parts of the compile: `@Mod` constructor injection of
  `net.neoforged.bus.api.IEventBus` + `net.neoforged.fml.ModContainer`; `ModContainer.registerConfig`;
  `net.neoforged.fml.event.config.ModConfigEvent.Loading/.Reloading`; `FMLCommonSetupEvent`;
  `net.neoforged.fml.ModList.get().isLoaded`; `net.neoforged.neoforge.common.ModConfigSpec` with the
  4-arg supplier `defineListAllowEmpty(String, Supplier, Supplier, Predicate)`;
  `net.neoforged.neoforge.registries.DeferredRegister` / `DeferredHolder` with vanilla `Registries.*`
  keys; `net.neoforged.neoforge.common.util.FakePlayerFactory`; `IItemExtension`
  `hasCraftingRemainingItem` / `getCraftingRemainingItem` still overridable. No `common` change;
  `:common` UP-TO-DATE; Forge/Fabric untouched. First Stage 3 work unit recorded in the snapshot.
  Primary gate for Stage 2 met; session stops at the stage boundary.

- 2026-08-29: Stage 3 — Capability layer and fluid transfer core (disposition C: NeoForge replaced
  Forge's capability system). Eleven files written under `neoforge/src/main/java` (see "Files
  changed" below).
  - Verification run 1: `./gradlew.bat :neoforge:compileJava --console=plain`, BUILD FAILED,
    23 errors. Delta from Stage 2 (14): +9. Four were real Stage-3 defects, all from API facts the
    disposition inventory assumed but the compiler refuted: `net.neoforged.neoforge.fluids.IFluidBlock`
    and `...capability.wrappers.FluidBlockWrapper` do not exist on NeoForge (legacy Forge API dropped);
    `BucketPickupHandlerWrapper`'s constructor is `(Player, BucketPickup, Level, BlockPos)` on NeoForge,
    not `(BucketPickup, Level, BlockPos)`; `net.neoforged.neoforge.event.EventHooks` has no
    `onBucketUse` (NeoForge 1.21.1 has no `FillBucketEvent` successor). The other 19 were classified
    forward references to Stage 4A (`crafting.*`), 4B (`register.ModLootModifiers`), 4C
    (`interaction.Cauldrons` / `Dispensers`), and Stage 5 (`client.SidedFluidColors`). This counts as
    failed attempt 1.
  - Correction (attempt-1 edit): `fluid/FluidPickup` — dropped the `IFluidBlock` / `FluidBlockWrapper`
    branch (modern modded source blocks implement vanilla `BucketPickup` directly, as Fabric already
    does), threaded a `@Nullable Player` through `handlerFor`, and constructed
    `new BucketPickupHandlerWrapper(player, pickup, level, pos)` (null player for the simulate-only
    `available` query). `platform/NeoForgeBucketOperations.beforeWorldBucketUse` — returns `null`,
    matching `FabricBucketOperations`; NeoForge exposes no pre-dispatch bucket-use event and our FTB
    Chunks protection runs through `ClaimProtectionProvider`, not a fill-bucket event, so no documented
    behavior regresses.
  - Verification run 2: `./gradlew.bat :neoforge:compileJava --console=plain`, BUILD FAILED,
    17 errors. Delta from run 1: -6. Zero errors remain in any Stage 3 file's own logic; all 17 name
    Stage 4/5 symbols only (`SomeBucketsNeoForge` bootstrap forward refs to `crafting.*`,
    `ModLootModifiers`, `Cauldrons`, `Dispensers`; `SBFluidLogic` -> `interaction.Cauldrons` x5;
    `NeoForgeBucketOperations` -> `client.SidedFluidColors` x2). Per the pre-Stage-5 rule this is a
    Stage 3 success. `:common:compileJava` UP-TO-DATE — no `common` change, so no Forge/Fabric
    regression guard required. 1 failed attempt used.
  - NeoForge fluid API confirmed by the passing Stage-3 compilation (reuse in later stages):
    `new FluidStack(Fluid, int)` 2-arg ctor; `FluidStack#applyComponents(DataComponentPatch)`,
    `#getComponentsPatch()`, `#copy()`, `#setAmount(int)`, `#getHoverName()`;
    `FluidStack.isSameFluidSameComponents(a, b)` static; `net.neoforged.neoforge.fluids.FluidType`
    with `BUCKET_VOLUME`, `getSound(SoundAction)`, `isVaporizedOnPlacement`, `canBePlacedInLevel`;
    `net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM` / `.BLOCK`,
    `RegisterCapabilitiesEvent#registerItem(ItemCapability, (stack, ctx) -> handler, Item...)`,
    `level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction)` (nullable, no LazyOptional),
    `stack.getCapability(Capabilities.FluidHandler.ITEM)` (nullable);
    `net.neoforged.neoforge.fluids.capability.{IFluidHandler,IFluidHandlerItem}` with
    `IFluidHandler.FluidAction.SIMULATE/EXECUTE`; `net.neoforged.neoforge.fluids.FluidUtil`
    `tryFluidTransfer` / `tryPlaceFluid`; `...capability.wrappers.{FluidBucketWrapper,
    BucketPickupHandlerWrapper}`; `net.neoforged.neoforge.common.SoundActions`.
  Primary gate for Stage 3 met; session stops at the stage boundary.

- 2026-08-29: Stage 4 — Server systems (custom ingredients, loot modifier + generator, cauldrons,
  dispensers, held-transfer events, fuel, config). Seven files written under `neoforge/src/main/java`
  plus two edits and one `neoforge/build.gradle` addition (see "Files changed" below).
  - Verification run 1: `./gradlew.bat :neoforge:compileJava :neoforge:processResources
    --console=plain`, BUILD FAILED, 6 errors. Two real Stage-4 defects: NeoForge `21.1`
    `ICustomIngredient` declares `getItems()` (not `items()`) returning `Stream<ItemStack>` (not
    `Stream<Holder<Item>>` as the `1.21.x` branch and the disposition inventory assumed). Four were
    the classified Stage 5 `client.SidedFluidColors` forward reference (import + use x2, counted
    once each in two files -> here just `NeoForgeBucketOperations`). `compileJava` failing blocked
    `processResources` this run. Failed attempt 1.
  - Correction: renamed the override to `getItems()` and changed its return type to
    `Stream<ItemStack>` in both ingredient classes — `EmptyBucketIngredient` returns
    `Stream.of(new ItemStack(item))`, `SpawnEggIngredient` returns
    `BuiltInRegistries.ITEM.stream().filter(SpawnEggItem.class::isInstance).map(ItemStack::new)`
    (the Fabric `getMatchingStacks` logic as a stream). `IngredientType(MapCodec, StreamCodec)`
    two-arg record constructor confirmed from NeoForge `1.21.1` source.
  - Verification run 2: same command. `:neoforge:processResources` **executed and passed**;
    `:neoforge:generateBucketLootModifiers` ran and produced 8 JSON files. `:neoforge:compileJava`
    BUILD FAILED with exactly 2 errors, both `NeoForgeBucketOperations.java` -> `client.SidedFluidColors`
    (Stage 5). Every Stage 4 file compiles clean -> Stage 4 pass. 1 failed attempt used.
  - GLM resources inspected deterministically: `data/neoforge/loot_modifiers/global_loot_modifiers.json`
    (`replace: false`, 7 `somebuckets:` entries) + 7 `data/somebuckets/loot_modifiers/<id>.json`
    files, one per manifest reward; all conditions use `neoforge:loot_table_id`; zero bare `forge:`
    ids, zero `data/forge/` output; all 8 JSON files parse.
  - `:common` unchanged (`:common:processResources` UP-TO-DATE); no Forge/Fabric regression guard
    required. NeoForge API confirmed: `ICustomIngredient` { `boolean test(ItemStack)`,
    `Stream<ItemStack> getItems()`, `boolean isSimple()`, `IngredientType<?> getType()` };
    `IngredientType<T>(MapCodec<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>)`;
    `NeoForgeRegistries.Keys.{INGREDIENT_TYPES, GLOBAL_LOOT_MODIFIER_SERIALIZERS}`;
    `net.neoforged.neoforge.common.loot.{LootModifier, IGlobalLootModifier}` with `codecStart`;
    `net.minecraft.core.cauldron.CauldronInteraction.{EMPTY,WATER,LAVA,POWDER_SNOW}.map().put(...)`
    + `ItemInteractionResult` unchanged from Forge/Fabric; `net.neoforged.fml.common.EventBusSubscriber`
    (no `bus` arg -> game bus) + `net.neoforged.bus.api.SubscribeEvent(priority, receiveCanceled)` +
    `net.neoforged.bus.api.EventPriority`; `PlayerInteractEvent.RightClickItem` with `isCanceled` /
    `setCanceled` / `setCancellationResult`; `IItemExtension#getBurnTime(ItemStack, @Nullable
    RecipeType<?>)` -> `int` (`-1` = no override).
  Primary gate for Stage 4 met; session stops at the stage boundary.

- 2026-08-29: Stage 5 — Client models and presentation (highest client risk). Eight files written
  under `neoforge/src/main/java/.../client/` plus four item model JSONs (see "Files changed" below).
  NeoForge `1.21.1` client-model API was resolved up front from NeoForge's published source
  (`raw.githubusercontent.com/neoforged/NeoForge/1.21.1/...` and the GitHub contents API), so no
  guessing was needed:
  - `DynamicFluidContainerModel` exists on NeoForge `1.21.1` with the Forge API intact
    (`implements IUnbakedGeometry<DynamicFluidContainerModel>`, `withFluid(Fluid)`, `Loader.INSTANCE`,
    `Loader.read(JsonObject, JsonDeserializationContext)`); `IUnbakedGeometry.bake(IGeometryBakingContext,
    ModelBaker, Function<Material,TextureAtlasSprite>, ModelState, ItemOverrides)` and
    `IGeometryLoader.read` are byte-identical to Forge.
  - `ModelEvent.RegisterGeometryLoaders.register(ResourceLocation, IGeometryLoader<?>)` — the key is a
    `ResourceLocation` on NeoForge (Forge used a `String`); `ModelEvent.ModifyBakingResult.getModels()`
    -> `Map<ModelResourceLocation, BakedModel>` unchanged.
  - `IItemExtension#initializeClient` and `RegisterClientExtensionsEvent` in
    `client/event/` do NOT exist on NeoForge `1.21.1`; the client item-extensions event is
    `net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent` with
    `registerItem(IClientItemExtensions extensions, Item... items)` (mod bus). `IClientItemExtensions.getCustomRenderer()`
    -> `BlockEntityWithoutLevelRenderer`. So no `NeoForge{JB,TB,MB}Item` subclass is needed —
    `ModItems` keeps registering the base `JBItem` / `TBItem`, and `ClientSetup` attaches the shared
    `JBRenderer` extension to both via `RegisterClientExtensionsEvent`.
  - `IItemExtension#getBurnTime(ItemStack, @Nullable RecipeType<?>)` (Stage 4) re-confirmed present in
    NeoForge `1.21.1` `IItemExtension.java`.
  - Verification: `./gradlew.bat :neoforge:compileJava --console=plain`, **BUILD SUCCESSFUL, 0
    errors** — a genuinely passing gate (the Stage 5 requirement; no forward-ref allowance). All of
    Stages 2–5 now compile. **0 failed attempts.** No `common` change (only new `neoforge/` files +
    resource JSONs); no Forge/Fabric regression guard required.
  Primary gate for Stage 5 met; session stops at the stage boundary.

## Files changed by port execution

- 2026-08-29 (Stage 2) — all new under `neoforge/src/main/java/com/github/crittscott/somebuckets/`:
  - `SomeBucketsNeoForge.java` — `@Mod` entry point; install order and lifecycle wiring ported from
    `SomeBucketsForge`. Wires (forward, for later stages) `NeoForgeBucketOperations`,
    `FluidProvider.register`, `ModLootModifiers.register`, `EmptyBucketIngredient.register`,
    `SpawnEggIngredient.register`, `Dispensers.register`, `Cauldrons.register`.
  - `register/ModItems.java` — NeoForge `DeferredRegister` + `DeferredHolder` for the six items;
    `JBItem` / `MBItem` / `TBItem` registered directly, `NeoForgeBBItem` / `NeoForgeSBItem` for BB/SB.
  - `register/ModSounds.java` — NeoForge `DeferredRegister` for `tb_eject`.
  - `register/ModCreativeTabs.java` — NeoForge `DeferredRegister` for the creative tab; body is
    Forge's verbatim.
  - `config/ServerConfig.java` — `net.neoforged.neoforge.common.ModConfigSpec`; 4-arg supplier
    `defineListAllowEmpty`.
  - `item/NeoForgeBBItem.java`, `item/NeoForgeSBItem.java` — crafting-remainder shells.
  - `protection/NeoForgeDispenserFakePlayer.java` — `FakePlayerFactory`-backed `[SomeBuckets]` fake
    player; installed via `AutomationPlayers.install`.

- 2026-08-29 (Stage 3) — all new under `neoforge/src/main/java/com/github/crittscott/somebuckets/`,
  ported from the like-named `forge/src/main` files with `net.minecraftforge.fluids.*` retargeted to
  `net.neoforged.neoforge.fluids.*` and the capability system rewritten:
  - `util/NeoForgeFluidStacks.java` — the ratified boundary helper: `get`/`set` bridge `StoredFluid`
    <-> NeoForge `FluidStack`; `of(Fluid, int, CompoundTag)`, `resized(FluidStack, int)` (copy +
    `setAmount`, components preserved), `sameFluid` (`isSameFluidSameComponents`); `CompoundTag` <->
    `DataComponentPatch` via `DataComponentPatch.CODEC` over `NbtOps.INSTANCE`, graceful degradation.
  - `fluid/FluidProvider.java` — `register(IEventBus)` adds a `RegisterCapabilitiesEvent` listener
    that `registerItem`s `Capabilities.FluidHandler.ITEM` with a stack-bound `BBFluidHandler` for
    `BIG_BUCKET_64` / `BIG_BUCKET_8` and `SBFluidHandler` for `SOURCE_BUCKET`. No
    `AttachCapabilitiesEvent` / `ICapabilityProvider` / `LazyOptional`.
  - `fluid/AbstractFluidHandler.java`, `fluid/BBFluidHandler.java`, `fluid/SBFluidHandler.java` —
    `net.neoforged.neoforge.fluids.capability.IFluidHandlerItem`; tank-0-only, fluid-mode dispatch,
    finite vs infinite `fillEmpty`/`fillExisting`/`performDrain` with simulate/execute parity; all
    `FluidStack` construction routed through `NeoForgeFluidStacks`.
  - `fluid/BBFluidLogic.java`, `fluid/SBFluidLogic.java` — domain logic unchanged from Forge;
    `ForgeFluidStacks` -> `NeoForgeFluidStacks`, `ForgeFluidPlacement` -> `NeoForgeFluidPlacement`,
    `isFluidEqual` -> `NeoForgeFluidStacks.sameFluid`. `SBFluidLogic` still forward-references
    `interaction.Cauldrons` (Stage 4C).
  - `fluid/FluidPickup.java` — vanilla `BucketPickup` via NeoForge `BucketPickupHandlerWrapper(Player,
    BucketPickup, Level, BlockPos)`; the legacy `IFluidBlock` / `FluidBlockWrapper` branch removed
    (absent on NeoForge).
  - `fluid/NeoForgeFluidPlacement.java` — `FluidUtil.tryPlaceFluid`, `FluidType` vaporize/placeable
    checks, vanilla `LiquidBlockContainer.canPlaceLiquid`; body unchanged from `ForgeFluidPlacement`.
  - `interaction/Transfers.java` — block lookups via
    `level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face)` (nullable, `getBlockEntity`
    null-guard dropped); `requireBucketHandler` / `handler` via
    `stack.getCapability(Capabilities.FluidHandler.ITEM)`; `SoundActions`, `FluidUtil`,
    `FluidBucketWrapper` from `net.neoforged.*`; `BlockTransferResult` dispatch-ownership,
    contract-violation reporting, `pump` / `pumpUnlimited`, and the `HeldTransferSettlement` /
    `MilkTransfers` seam preserved.
  - `platform/NeoForgeBucketOperations.java` — implements `BucketOperations`; `beforeWorldBucketUse`
    returns `null` (no NeoForge event); `fluidDisplayName` / `fluidColor` build a NeoForge
    `FluidStack` via `NeoForgeFluidStacks`; still forward-references `client.SidedFluidColors`
    (Stage 5).

- 2026-08-29 (Stage 4) — under `neoforge/src/main/java/com/github/crittscott/somebuckets/`:
  - `crafting/EmptyBucketIngredient.java`, `crafting/SpawnEggIngredient.java` — NeoForge
    `ICustomIngredient` records/classes; `MapCodec` + `StreamCodec` in an
    `IngredientType<>(CODEC, STREAM_CODEC)`, registered via a `DeferredRegister<IngredientType<?>>`
    on `NeoForgeRegistries.Keys.INGREDIENT_TYPES`; `register(IEventBus)` matches the bootstrap call.
    Ids `somebuckets:empty_bucket` / `somebuckets:spawn_egg`, `NBTUtil.isEmptyBucket` test,
    `isSimple()` false / true preserved. `getItems()` returns `Stream<ItemStack>`.
  - `loot/AddBucketLootModifier.java` — `net.minecraftforge.common.loot.*` -> `net.neoforged.neoforge.common.loot.*`;
    `CODEC` / `doApply` / `codecStart` body verbatim.
  - `register/ModLootModifiers.java` — `DeferredRegister<MapCodec<? extends IGlobalLootModifier>>` on
    `NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS`; `DeferredHolder` replaces
    `RegistryObject`; registers `add_bucket -> AddBucketLootModifier.CODEC`.
  - `interaction/Cauldrons.java` — disposition B; only `net.minecraftforge.fluids.*` ->
    `net.neoforged.neoforge.fluids.*` + `ForgeFluidStacks` -> `NeoForgeFluidStacks`,
    `isFluidEqual` -> `NeoForgeFluidStacks.sameFluid`; vanilla `CauldronInteraction.*.map().put`
    + `ItemInteractionResult` unchanged.
  - `interaction/Dispensers.java` — disposition A/B; same fluid-import retarget; vanilla
    `BlockSource` / `DefaultDispenseItemBehavior` / `DispenserBlock.registerBehavior`;
    `DispenserTarget` / `NonFluidDispensers` common.
  - `interaction/NeoForgeHeldTransferEvents.java` — `@EventBusSubscriber(modid = MODID)` (game bus),
    `@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)` on
    `PlayerInteractEvent.RightClickItem`; `player.pick` / `blockInteractionRange` /
    `Transfers.tryTransferEither` unchanged. Auto-discovered; not referenced by the bootstrap.
  - `item/NeoForgeBBItem.java`, `item/NeoForgeSBItem.java` (edited) — added
    `getBurnTime(ItemStack, @Nullable RecipeType<?>)` -> `BucketFuel.isLavaFuel(stack) ?
    FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS : -1`. No fuel event or mixin. SB burns without
    depletion because its crafting remainder is the unchanged bucket.
  - `neoforge/build.gradle` (edited) — added `generateBucketLootModifiers`, a namespace sibling of
    the Forge task: reads `common/.../somebuckets/bucket_loot.json`, emits
    `data/somebuckets/loot_modifiers/<id>.json` (`neoforge:loot_table_id` conditions) and
    `data/neoforge/loot_modifiers/global_loot_modifiers.json`; wired into `sourceSets.main.resources`
    srcDir, `processResources` and `sourcesJar` `dependsOn`.

- 2026-08-29 (Stage 5) — under `neoforge/src/main/java/com/github/crittscott/somebuckets/client/`,
  ported from the like-named `forge/.../client/` files with `net.minecraftforge.client.*` ->
  `net.neoforged.neoforge.client.*` and `net.minecraftforge.api.distmarker.*` ->
  `net.neoforged.api.distmarker.*`:
  - `ClientSetup.java` — `@EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Bus.MOD)`;
    `FMLClientSetupEvent` (`ItemProperties.register` x4), `RegisterColorHandlersEvent.Item`,
    `RegisterClientReloadListenersEvent`, `ModelEvent.RegisterGeometryLoaders` / `.ModifyBakingResult`,
    and a new `RegisterClientExtensionsEvent` handler
    (`event.registerItem(JBRenderer.createItemExtensions(), JUNK_BUCKET, TRASH_BUCKET)`).
  - `ClientColorHandlers.java` — `RegisterColorHandlersEvent.Item#register`; `IClientFluidTypeExtensions`
    from `net.neoforged.neoforge.client.extensions.common`; `ForgeSpawnEggItem.fromEntityType` ->
    vanilla `SpawnEggItem.byId(EntityType)` (matches Fabric); tint via `NeoForgeFluidStacks`.
  - `ClientFluidColors.java` — package swaps only; `IClientFluidTypeExtensions` +
    `ClientTextureColors` (common) unchanged.
  - `SidedFluidColors.java` — `DistExecutor` (removed on NeoForge) replaced with an
    `FMLEnvironment.dist == Dist.CLIENT` guard delegating to a private nested `ClientHolder` so a
    dedicated server never classloads `ClientFluidColors`. `FluidStack` -> `net.neoforged.neoforge.fluids`.
  - `ClientModelLoaders.java` — `ModelEvent` from `net.neoforged.*`; geometry-loader key is a
    `ResourceLocation` (`StoredFluidContainerModel.Loader.ID`) not a `String`;
    `ModelResourceLocation.inventory(BucketDefinitions.JUNK_BUCKET_ID)`.
  - `StoredFluidContainerModel.java` — near-verbatim: `DynamicFluidContainerModel`, `IUnbakedGeometry`,
    `IGeometryLoader`, `IGeometryBakingContext`, `BakedModelWrapper` all from
    `net.neoforged.neoforge.client.model[.geometry]`; `Loader.NAME` (String) -> `Loader.ID`
    (ResourceLocation); `ForgeFluidStacks` -> `NeoForgeFluidStacks`.
  - `JBModel.java` — `BakedModelWrapper` from `net.neoforged.*`; `applyTransform` / `isCustomRenderer`
    unchanged.
  - `JBRenderer.java` — `IClientItemExtensions` from `net.neoforged.neoforge.client.extensions.common`,
    `BakedModelWrapper` / `IQuadTransformer` / `ModelData` from `net.neoforged.neoforge.client.model[.data]`;
    `BlockEntityWithoutLevelRenderer`, `getRenderPasses`, both `getQuads` overloads, and the shared
    `JunkIconLayout` / `JunkForegroundGeometry` / `BucketMouth` calls unchanged.
  - `assets/somebuckets/models/item/{big_bucket_64,big_bucket_8,junk_bucket,source_bucket}.json` —
    copied from Forge; `"parent": "forge:item/default"` -> `"neoforge:item/default"` in the three
    fluid-container models; `junk_bucket.json` (`minecraft:item/generated`) verbatim. All parse.
  No `NeoForge{JB,TB,MB}Item` subclass needed — `RegisterClientExtensionsEvent` replaces Forge's
  `initializeClient` override and `ModItems` already registers the base item classes.

## Completed stages

- 2026-08-29: Stage 0 — Baseline build-wiring verification and disposition inventory. One
  `:neoforge:compileJava` run (exit 0, empty source set, `:common` UP-TO-DATE). Build wiring and
  `neoforge.mods.toml` tokens verified. Complete per-file disposition inventory produced in the
  snapshot. No file created or changed.

- 2026-08-29: Stage 1 — Common under the NeoForge transform. `:common:compileJava` confirmed
  passing (exit 0, UP-TO-DATE); loader-import scan clean (only the cross-remapped `@Environment` on
  five client classes); no NeoForge-transform-only common regression. `StoredFluid` <->
  `FluidStack` conversion ratified by the user as a NeoForge-module-only boundary helper
  (`NeoForgeFluidStacks`, `DataComponentPatch.CODEC` over plain `NbtOps`, graceful degradation),
  `StoredFluid` unchanged. No code, resource, or build-file change; Forge/Fabric not re-compiled
  (common unchanged). First Stage 2 work unit recorded in the snapshot.

- 2026-08-29: Stage 2 — NeoForge bootstrap, registration, identifiers, metadata. Eight new files
  under `neoforge/src/main/java` (`SomeBucketsNeoForge`, `register/ModItems`, `register/ModSounds`,
  `register/ModCreativeTabs`, `config/ServerConfig`, `item/NeoForgeBBItem`, `item/NeoForgeSBItem`,
  `protection/NeoForgeDispenserFakePlayer`). Diagnostic `:neoforge:compileJava` BUILD FAILED with 14
  errors, all in `SomeBucketsNeoForge.java`, all classified forward references to Stage 3
  (`FluidProvider`, `NeoForgeBucketOperations`) and Stage 4 (`crafting.*`, `ModLootModifiers`,
  `Cauldrons`, `Dispensers`); no error in any Stage 2 file. 0 failed attempts (later-stage-symbol
  errors do not count against a pre-Stage-5 diagnostic). No `common` change; Forge/Fabric untouched.
  First Stage 3 work unit recorded in the snapshot.

- 2026-08-29: Stage 3 — Capability layer and fluid transfer core. Eleven new files under
  `neoforge/src/main/java` (`util/NeoForgeFluidStacks`, `fluid/FluidProvider`,
  `fluid/AbstractFluidHandler`, `fluid/BBFluidHandler`, `fluid/SBFluidHandler`, `fluid/BBFluidLogic`,
  `fluid/SBFluidLogic`, `fluid/FluidPickup`, `fluid/NeoForgeFluidPlacement`, `interaction/Transfers`,
  `platform/NeoForgeBucketOperations`). Diagnostic `:neoforge:compileJava`: run 1 BUILD FAILED, 23
  errors — 4 real Stage-3 defects (`IFluidBlock` / `FluidBlockWrapper` absent on NeoForge;
  `BucketPickupHandlerWrapper` ctor gained a leading `Player`; `EventHooks.onBucketUse` has no
  NeoForge successor), 19 classified Stage 4/5 forward refs. One correction pass (drop the legacy
  fluid-block branch and thread `@Nullable Player` in `FluidPickup`; `beforeWorldBucketUse` returns
  `null` as on Fabric). Run 2 BUILD FAILED, 17 errors, every one a Stage 4/5 forward reference
  (`crafting.*`, `ModLootModifiers`, `Cauldrons`, `Dispensers`, `client.SidedFluidColors`); no Stage 3
  file has an error in its own logic — a Stage 3 pass. 1 failed attempt used. No `common` change
  (`:common` UP-TO-DATE); Forge/Fabric regression guard not required. Ratified `NeoForgeFluidStacks`
  conversion implemented; NeoForge component-based `FluidStack` and capability APIs confirmed. First
  Stage 4 work unit recorded in the snapshot.

- 2026-08-29: Stage 4 — Server systems. Seven new files (`crafting/EmptyBucketIngredient`,
  `crafting/SpawnEggIngredient`, `loot/AddBucketLootModifier`, `register/ModLootModifiers`,
  `interaction/Cauldrons`, `interaction/Dispensers`, `interaction/NeoForgeHeldTransferEvents`), two
  edits (`item/NeoForgeBBItem`, `item/NeoForgeSBItem` gain `getBurnTime`), and a `neoforge/build.gradle`
  `generateBucketLootModifiers` addition. Run 1 (`:neoforge:compileJava :neoforge:processResources`)
  BUILD FAILED, 6 errors — 2 real (`ICustomIngredient` on NeoForge `21.1` declares
  `Stream<ItemStack> getItems()`, not `Stream<Holder<Item>> items()`), rest the Stage 5
  `client.SidedFluidColors` forward ref. One correction (rename + retype the override in both
  ingredient classes). Run 2: `:neoforge:processResources` **passed**, `generateBucketLootModifiers`
  emitted 8 JSON files (validated: `neoforge:loot_table_id` conditions, `data/neoforge/` index,
  7 reward files matching the manifest, all parse, no stray `forge:` namespace);
  `:neoforge:compileJava` BUILD FAILED with exactly 2 errors, both `client.SidedFluidColors`
  (Stage 5). Every Stage 4 file compiles clean — a Stage 4 pass. 1 failed attempt used. No `common`
  change; Forge/Fabric regression guard not required. First Stage 5 work unit recorded in the
  snapshot.

- 2026-08-29: Stage 5 — Client models and presentation. Eight `client/` files + four item model
  JSONs. NeoForge `1.21.1` client-model API resolved from NeoForge's own published source before
  writing (`DynamicFluidContainerModel` intact; `ModelEvent` geometry-loader key is a
  `ResourceLocation`; `RegisterClientExtensionsEvent.registerItem(IClientItemExtensions, Item...)`
  is the client item-extensions mechanism — no `initializeClient`, no loader item subclass needed).
  `./gradlew.bat :neoforge:compileJava` **BUILD SUCCESSFUL, 0 errors** — the genuinely-passing gate
  Stage 5 requires; all of Stages 2–5 compile. 0 failed attempts. No `common` change; no
  Forge/Fabric regression guard. Next: Stage 6 (GameTest build wiring + source port).

- 2026-08-29: Stage 6 — GameTest build wiring and source port. Authorized `neoforge/build.gradle`
  construction (a `gametest` source set srcDir'ing `common/src/gametest/java` with
  compile/runtime classpath from `sourceSets.main`; `loom.mods.somebuckets_gametest`;
  `loom.runs.gameTestServer` with `server()` / `environment 'gametestserver'` /
  `forgeTemplate 'gameTestServer'` / `property 'neoforge.enabledGameTestNamespaces', mod_id` /
  `source sourceSets.gametest`; `rootProject.configureGameTestStructures(project)`). Eighteen Java
  files under `neoforge/src/gametest/java` (`GameTestSupport`, `SomeBucketsGameTestMod`, thirteen
  ported wrapper suites, `NeoForgeFuelGameTests`, `NeoForgeOnlyBBGameTests`, `NeoForgeOnlyMBGameTests`)
  plus `neoforge.mods.toml` and `pack.mcmeta` under `neoforge/src/gametest/resources`. Two
  `neoforge/src/main` edits: `item/NeoForgeBBItem` and `item/NeoForgeSBItem` `getBurnTime` non-lava
  return `-1` -> `0` (Stage-4 correction: NeoForge 1.21.1's `IItemStackExtension#getBurnTime` throws
  on a negative item-hook value; `0` = not fuel, behaviorally identical to Forge's `-1` since neither
  item has a `furnace_fuels.json` entry). `forge/.../FillBucketEventGameTests` deliberately not
  ported (NeoForge 1.21.1 has no fill-bucket event). NeoForge GameTest facts resolved from published
  NeoForge 1.21.1 source: `net.neoforged.neoforge.gametest.{GameTestHolder, PrefixGameTestTemplate}`;
  `GameTestRegistry.turnMethodIntoTestFunction` prepends `<holder-namespace>:` and (unless
  `@PrefixGameTestTemplate(false)`) the lowercased class name to `@GameTest.template` even when it
  already carries a namespace, so every suite gets `@GameTestHolder(MODID)` + `@PrefixGameTestTemplate(false)`
  and `GameTestSupport.TEMPLATE = "empty_9x6x9"` (bare); run property `neoforge.enabledGameTestNamespaces`;
  `forgeTemplate` covers NeoForge (`ModPlatform.isForgeLike`). Fixture block capability registered by
  the gametest `@Mod("somebuckets_gametest")` stub's `(IEventBus)` constructor via
  `RegisterCapabilitiesEvent#registerBlock(Capabilities.FluidHandler.BLOCK, provider, Blocks.STRUCTURE_BLOCK)`.
  `./gradlew.bat :neoforge:compileGametestJava` **BUILD SUCCESSFUL, 0 errors, first try** (0 failed
  attempts) — the genuinely-passing gate Stage 6 requires; `:neoforge:compileJava` also re-ran green
  after the fuel fix; only warning is the pre-existing `SharedGameTestSupport` deprecation note. No
  `common` change; Forge/Fabric regression guard not required. Next: Stage 7 (runtime GameTest
  stabilization via `:neoforge:runGameTestServer`).

- 2026-08-29: Stage 7 — runtime GameTest stabilization. Baseline
  `./gradlew.bat :neoforge:runGameTestServer --console=plain`: exit 1, server bootstrapped and
  exited cleanly, 174 tests discovered, **2 required failures** —
  `player_source_cauldron_round_trip_assigns_and_remains_infinite` ("did not award exactly two
  cauldron-use statistics") and `powder_snow_place_event_cancellation_is_atomic` ("canceled powder
  placement debited the Big Bucket"). Distinct NeoForge-specific causes, one bounded correction pass
  (WU1), two `neoforge/src/main` files, no `common`/test change.
  Cause 1: NeoForge 21.1 exposes `Capabilities.FluidHandler.BLOCK` for vanilla cauldrons (Forge does
  not; NeoForge-only source branches confirmed via published NeoForge docs and the `1.21.x` diff of
  `BlockItem`/`CommonHooks`), so `SBFluidLogic`/`BBFluidLogic` routed cauldron take/place through the
  generic `Transfers` block path, bypassing `Cauldrons.complete()` (which awards `USE_CAULDRON`,
  fires `FILLED_BUCKET`, emits cauldron game events). Fix: `neoforge/.../interaction/Transfers.java`
  `blockHandler()` returns `null` for `AbstractCauldronBlock` — the single chokepoint for all
  block-side capability lookups; dedicated `Cauldrons` path then owns cauldrons as on Forge. Dispenser
  BB/SB behaviors already call `Cauldrons.*` first — unaffected.
  Cause 2: NeoForge's `CommonHooks.onPlaceItemIntoWorld` (verified against published 1.21.x source)
  defers `EntityPlaceEvent` until after `useOn` returns; its held-stack rollback does
  `itemstack.applyComponents(components)` where `components = itemstack.getComponents()` is a live
  `PatchedDataComponentMap` reference captured before `useOn`, so it cannot undo a `custom_data`
  mutation made during `useOn`. A canceled place therefore left the Big Bucket debited. Fix:
  `neoforge/.../fluid/BBFluidLogic.java` `tryPlacePowder`, player path only
  (`level.captureBlockSnapshots` already true): after `place()`, clone+clear
  `level.capturedBlockSnapshots`, fire `EventHooks.onBlockPlace`/`onMultiBlockPlace`; on cancel
  restore snapshots (`restoringBlockSnapshots` guard) and return `false` before any debit; on success
  `BlockState.onPlace` + `level.markAndNotifyBlock` then debit. Dispenser path
  (`captureBlockSnapshots` false) keeps the direct `place()`; `ITEM_USED` accounting unchanged (outer
  wrapper still awards it once via its empty-snapshot `else` branch).
  Post-fix `./gradlew.bat :neoforge:runGameTestServer --console=plain`: **BUILD SUCCESSFUL**, "All
  174 required tests passed", 3.09 s, clean server exit. Delta from baseline: 2 failures -> 0; 174
  discovered both runs. **0 failed verification attempts** (baseline is not a post-implementation
  attempt; first post-fix run passed). No `common` change; Forge/Fabric/`common` guards not
  required. Stage 7 primary gate satisfied. Next: Stage 8 (final package + doc reconciliation via
  `:neoforge:build`).

- 2026-08-29: Stage 7 complete. `:neoforge:runGameTestServer` passing (174/174, clean exit). All
  Stage 7 watch-items from the snapshot confirmed green at runtime: test discovery (174 > 0),
  `neoforge.enabledGameTestNamespaces` ("Enabled Gametest Namespaces: [somebuckets]"),
  `forgeTemplate 'gameTestServer'` run launch, gametest `@Mod` stub block-capability wiring
  (`BlockCapabilityGameTests` pass), `EntityJoinLevelEvent` on mob release (`NeoForgeOnlyMBGameTests`
  pass). Cumulative gate record: `:common:compileJava`, `:neoforge:compileJava`,
  `:neoforge:processResources`, `:neoforge:compileGametestJava`, `:neoforge:runGameTestServer` all
  passing; `:neoforge:build` remains for Stage 8. No Git or GitHub action performed.

- 2026-08-29: Stage 8 — final package and documentation reconciliation. The user ran `:neoforge:build`
  (artifact produced) and the full GameTest suites on all three loaders (Forge, NeoForge, Fabric) —
  all green; the two Stage 7 `neoforge/src/main` fixes touched no `common`/Forge/Fabric code so there
  is no regression. Synced the `build-env/neoforge/build.gradle` snapshot to the active file (Stage 6
  gametest + GLM-generator construction; the only build-input drift). Reconciled the three
  orientation docs to the three-loader 1.21.1 state: `as-built.md` (repository map now four modules
  with a `neoforge` row; `SomeBucketsNeoForge` in the bootstrap row; `common/src/compat/java` on the
  Fabric+NeoForge source sets; `StoredFluid` conversion paragraph adds `NeoForgeFluidStacks` /
  `DataComponentPatch.CODEC` over plain `NbtOps`; cross-loader seam table gains a NeoForge column
  covering capabilities, cauldrons, dispensers, the `getBurnTime` fuel hook, and the client renderer;
  GameTest section documents `@GameTestHolder` + `@PrefixGameTestTemplate(false)` and the bare
  `TEMPLATE`; two new maintenance invariants for the cauldron-capability bypass and the powder-snow
  place-event finalization; loot/config paragraphs cover the `neoforge:` GLM namespace and the shared
  `serverconfig/somebuckets-server.toml`), `player-view.md` (fluid-tank / machine-transfer /
  held-transfer wording includes NeoForge capabilities; FTB Chunks integration is Fabric + NeoForge,
  none on Forge for this version; config-file and structure-loot-modifier and texture-clip wording
  generalized), `build-env.md` ("scaffolding only" language removed; NeoForge is a full loader module
  with a gametest source set and loot-modifier generator; GameTest-support-mod version row lists all
  three). Port complete: every completion gate passes, the snapshot reads `complete`, docs describe a
  three-loader 1.21.1 implementation. No Git or GitHub action performed.
