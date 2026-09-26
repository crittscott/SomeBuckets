# Port plan: Minecraft 1.21.3 → 1.21.4

Working status file for the 1.21.4 port. Update the **Status** column as work lands. If a session
ends mid-phase, the "Next step" line says where to resume.

**Next step:** Phase 10 continues: Forge and NeoForge compile; user rebuilds Fabric after the third round of fixes.

## Scope

1.21.4 is a small content drop on the server side. The breaking change is client item rendering:

- Every item needs `assets/<ns>/items/<id>.json` (an item-model definition).
- Removed: `ItemProperties` (model `overrides`/`predicate`), `ItemColor`/`ItemColors`,
  `BakedOverrides`, `BlockEntityWithoutLevelRenderer` (BEWLR), `ModelResourceLocation.inventory`,
  `ItemRenderer.render(..., BakedModel)`.
- Replacements: `ItemModel` / `ItemModel.Unbaked` (custom types by `MapCodec`), `ItemTintSource`,
  `SpecialModelRenderer`, `ItemStackRenderState`, and vanilla item-model types `model`, `composite`,
  `condition` (`minecraft:has_component`), `select`, `range_dispatch`, `special`.
- `SpawnEggItem` no longer stores colors (`getColor` removed); egg colors are `minecraft:constant`
  tints in each egg's client item definition. Egg colors are therefore client-only data.

## Design

All item rendering moves to `common` as vanilla API; loaders only register codecs and supply the
fluid appearance (sprite, tint, luminance) through a client seam.

| Item | Item definition |
| --- | --- |
| Big / Huge | `condition has_component somebuckets:milk_amount` → milk model; else `has_component somebuckets:powder_units` → powder model; else `somebuckets:fluid_bucket` over the vessel model |
| Source | `has_component milk_amount` → milk model; else `somebuckets:fluid_bucket` |
| Mob | `has_component somebuckets:captured_mobs` → filled model with tints `[constant -1, somebuckets:mob_egg 0, somebuckets:mob_egg 1]`; else plain model |
| Junk | `composite [ model junk_bucket, special base=junk_bucket { somebuckets:junk_contents, texture } ]` |
| Trash | `model trash_bucket` with tints `[constant -1, constant 0xFF000000]` (contents were never rendered for Trash on any loader) |

New and changed common client classes (`common/.../client`):

- `FluidBucketModel`: `ItemModel` + `Unbaked` (`model` = vessel). `update` adds the vessel layer, then
  a fluid layer whose `BakedModel` is built from `FluidMaskGeometry` with the fluid's still sprite,
  cached per (sprite, emissive) in the baked instance, tinted through the layer's tint array. Holds
  the `Appearance` seam (`@Nullable Look look(StoredFluid)`) installed by each loader.
- `FluidMaskGeometry`: faces read per bake (no static cache); also packs vanilla `BLOCK`-format
  `BakedQuad`s (shared with the Junk cover).
- `JunkContentsRenderer`: `SpecialModelRenderer<Frame>` + `Unbaked` (`texture`). Absorbs
  `JunkBucketRenderData` (frame cache, owned by the baked instance) and `JunkBucketCoverQuads`
  (cover quads from the texture sprite). Nested items render through their own
  `ItemStackRenderState` via `ItemModelResolver`. Left-hand mirroring follows the former Fabric
  renderer.
- `JunkBucketIcons`: layout only; spans passed in explicitly (no static cache).
- `MobEggColors` moves from `item` to `client`: override table + egg colors read from the egg's
  `items/*.json` (`ClientItem.CODEC`, `BlockModelWrapper.Unbaked` with two `Constant` tints) +
  the `somebuckets:mob_egg` `ItemTintSource`. Cache cleared on resource reload.
- `ClientModelTypes`: ids of the three types. Vanilla's `ID_MAPPER`s are private, so `common`
  cannot register them: NeoForge uses its events; Fabric puts into the `ID_MAPPER`s directly
  (Fabric API's transitive access wideners open them); Forge does the same through
  `forge/src/main/resources/META-INF/accesstransformer.cfg`.

Removed: `FluidBucketItem.CONTENT_*`/`getContentProperty`, `MBItem.FILLED_PROPERTY`/`MODEL_*`/
`getFilledProperty`; all loader BEWLRs, `CustomRendererModel`, `JBModel`, `DelegatingBakedModel`,
`FabricFluidContainerModel`, `FabricJunkBucketRenderer`, per-loader item model JSON, `ForgeJBItem`/`ForgeTBItem`
(their only purpose was the BEWLR), `ForgeBBItem`/`ForgeSBItem.initializeClient`.

`/sb eggs` becomes a client command on every loader (server registration removed).

## Phases

| # | Phase | Status |
| --- | --- | --- |
| 1 | Item definitions (`common/.../assets/somebuckets/items/*.json`); model JSON consolidated into `common` without overrides; per-loader model JSON deleted | done |
| 2 | Common client classes: `FluidBucketModel`, `FluidMaskGeometry`, `JunkContentsRenderer`, `JunkBucketIcons`, `ClientModelTypes`; delete `JunkBucketRenderData`, `JunkBucketCoverQuads` | done |
| 3 | `MobEggColors` → client with tint source; `EggDiagnostics` client-only; drop `DiagnosticsSupport`'s egg-color reliance on `getColor` | done |
| 4 | Remove model-property protocol from `FluidBucketItem`, `MBItem`, `BBItem`/`SBItem` javadoc | done |
| 5 | NeoForge client: register types via `RegisterItemModelsEvent`, `RegisterSpecialModelRendererEvent`, `RegisterColorHandlersEvent.ItemTintSources`; install appearance; reload listener; delete BEWLR classes; remove server `/sb eggs` | done |
| 6 | Forge client: `ClientSetup.registerItemDefinitionTypes()` (access transformer) from the constructor on the client dist; install appearance; reload listener; delete BEWLR classes and `ForgeTBItem`; remove server `/sb eggs` | done |
| 7 | Fabric client: direct `ID_MAPPER` registration; install appearance; `ResourceManagerHelper` reload listener; delete Fabric model/renderer classes, `ColorProviderRegistry`/`ItemProperties` use | done |
| 8 | GameTests: replace `model_predicates_match_java_protocol` with an item-definition check (six files exist; `has_component` ids match `ModDataComponentTypes` and each mode's stack) | done |
| 9 | Docs: `as-built.md`, `player-view.md` (1.21.4, rendering ownership, `/sb eggs` client-only) | done |
| 10 | **User:** build all three loaders; fix compile errors (API names below were written from memory) | todo |
| 11 | **User:** in-game visual checks (list below) | todo |

## Unverified API assumptions (check at first build)

- `ItemModel.update(ItemStackRenderState, ItemStack, ItemModelResolver, ItemDisplayContext,
  ClientLevel, LivingEntity, int)`; `ItemModel.BakingContext.bake(ResourceLocation)`;
  `ResolvableModel.Resolver.resolve(ResourceLocation)`.
- `ItemStackRenderState.newLayer()`, `LayerRenderState.setupBlockModel(BakedModel, RenderType)`,
  `prepareTintLayers(int)`, `setFoilType`; `ItemStackRenderState.render(PoseStack,
  MultiBufferSource, int, int)`; `Minecraft.getItemModelResolver()`;
  `ItemModelResolver.updateForTopItem(state, stack, context, leftHand, level, entity, seed)`.
- `SpecialModelRenderer<T>.render(T, ItemDisplayContext, PoseStack, MultiBufferSource, int, int,
  boolean)`, `extractArgument(ItemStack)`, `Unbaked.bake(EntityModelSet)`.
- `ItemTintSource.calculate(ItemStack, ClientLevel, LivingEntity)`; `Constant(int value)`.
- `BakedModel` abstract methods: `getQuads`, `useAmbientOcclusion`, `isGui3d`, `usesBlockLight`,
  `getParticleIcon`, `getTransforms` (no `isCustomRenderer`).
- `BakedQuad(int[], int tintIndex, Direction, TextureAtlasSprite, boolean shade, int lightEmission)`
  and that item rendering honors `lightEmission` (glowing fluids).
- Architectury Loom picks up `forge/src/main/resources/META-INF/accesstransformer.cfg` without a
  `build.gradle` entry, and Forge applies it at runtime.
- NeoForge 21.4 event names in phase 5.
- Vanilla spawn-egg item definitions carry two `minecraft:constant` tints; `ClientItem.CODEC`,
  `BlockModelWrapper.Unbaked.tints()`, `Constant.value()` are public.
- `FluidVariantAttributes.getLuminance` (Fabric) and `FluidType.getLightLevel()` (Forge/NeoForge).

## Open questions for the user

- **Creaking:** capturing a heart-bound creaking and releasing it elsewhere likely makes it vanish.
  Add `minecraft:creaking` to `mb_blacklist`? (Not done.)

## Visual checks after build

- Big/Huge/Source with water, lava (glows?), a modded fluid; milk; powder snow; empty.
- Fluid layer alignment in GUI, hand (both hands), ground, item frame.
- Junk Bucket contents: layout, cover in front, left hand, block items' lighting in GUI, glint.
- Mob Bucket tint for vanilla and modded mobs; override-table mobs; `/sb eggs` report.
- Trash Bucket black void. Resource reload (F3+T) keeps everything correct.

## Log

- Phases 1–6 written.
- Behavior notes: Junk left-hand mirroring (formerly Fabric-only) now applies on every loader;
  luminous fluids render emissive on every loader (formerly Forge/NeoForge only); nested GUI
  items no longer reset flat lighting (render states don't touch global lighting).
- Phases 7–9 written. Fabric uses a `ResourceManagerHelper` reload listener to clear color caches.
  Render caches (mask faces, junk layouts, cover and fluid quads) now live in baked model instances,
  so they reset with each resource reload without explicit clearing.
- Obsolete loader renderers, item shells, and per-loader model JSON deleted (with user permission).
- Build round 1: `InventoryMenu.BLOCK_ATLAS` → `TextureAtlas.LOCATION_BLOCKS` (removed in 1.21.4);
  vanilla `ID_MAPPER`s are private, so registration moved out of `common` (see Design). Forge 54's
  changelog and release notes show no registration events for these types.
- Build round 2: NeoForge 21.4 replaced `RegisterClientReloadListenersEvent` with
  `AddClientReloadListenersEvent` (id + listener); dropped deprecated `bus =` on NeoForge
  `ClientSetup`. Forge 54 removed `ForgeSpawnEggItem` (all loaders now use `SpawnEggItem.byId`
  directly, so `DiagnosticsSupport.spawnEggFor` was removed) and `Ingredient.items()` returns a
  `Stream`.
- Build round 3 (Fabric): `CustomIngredient.getMatchingItems()` returns a `Stream`; removed the
  dedicated-server `/sb eggs` registration (egg colors are client-only).
