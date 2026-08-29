# Some Buckets Post-Port Cleanup Register

Working inventory of the structural debt left by the one-loader-at-a-time port from the 1.20.1
Forge/Fabric mod to 1.21.1 Forge + Fabric + NeoForge. `cleanup-plan.md` holds the method and the
order; this file is the list of targets and how each one can actually be fixed.

Nothing here changes observable behavior. `player-view.md` is the contract. Every row is
behavior-preserving restructuring, verified by reading plus the shared GameTest suite.

## Decisions in force

- **Forge stays a co-equal target.** Full support parity, clean seam. NeoForge is only the
  tie-breaker for the shape shared code takes.
- **`NBTUtil` and the `minecraft:custom_data` blob are out of scope.** No schema change, no persisted
  -byte change, no stored-data migration.
- **NeoForge idiom is the reference for shared code.**

## The mechanism constraint (read first)

The first draft of this register assumed a `.java` file could be dropped into a source set compiled
into **both** the `forge` and `neoforge` modules ("Approach A — shared `forge`-family source set").
Verified on contact: **that does not work here.**

- NeoForge renamed nearly every package from `net.minecraftforge.*` to `net.neoforged.*`. A file that
  needs `BakedModelWrapper`, `IFluidHandler`, `DeferredRegister`, `RegisterColorHandlersEvent`, etc.
  must import exactly one spelling, and it will not resolve in the other module.
- `common/src/main` cannot hold these either. `common/build.gradle` compiles with only
  `fabric-loader` + `jsr305` on the classpath — **no Forge or NeoForge symbols at all**. Every
  existing `common` client class (`DelegatingBakedModel`, `ClientTextureColors`,
  `JunkForegroundGeometry`, `JunkIconLayout`) is typed against **vanilla Mojang types only**, guarded
  by the one cross-remapped `@Environment`.
- There is **no `@ExpectPlatform` usage** anywhere in `common`, and `@ExpectPlatform` only redirects
  `static` methods — it cannot help a class whose job is to `extend`/`implement` a loader type.
- `common/src/compat/java` (the FTB Chunks adapter, shared into `neoforge` + `fabric`) works **only**
  because FTB Chunks' API package (`dev.ftb.mods.ftbchunks.*`) is byte-for-byte the same on both
  loaders. It is not a precedent for sharing anything that touches a loader's own API.

### The mechanisms that are actually available

| ID | Mechanism | Applies when | Cost / risk |
| --- | --- | --- | --- |
| **M1** | Extract the vanilla-typed core into `common/src/main`; leave a thin loader shell that does the `extends`/registration/capability part | The dedup-worthy logic needs only `net.minecraft.*` + our own common types + jsr305 | Per-file surgery. Risk scales with how entangled the core is with loader constants (e.g. `IQuadTransformer.STRIDE`). |
| **M2** | `@ExpectPlatform` static hook in `common`, one impl per loader | `common` needs a small loader-specific *computation* and the call site is common | Adds architectury-injectables to `common` compile deps (standard, small). Method-level only. |
| **M3** | Accept the duplication | The loader-bound surface dominates and the neutral core is roughly < 15 lines | Zero. Project principle explicitly allows it ("some duplication beats one more small class"). Cost is "fix it twice" forever. |
| **M4** | In-place idiom fix on the Forge side, no sharing | A Worry-2 row that is just "use the 1.21.1 / NeoForge spelling" | Low. No dedup — modernization only. |
| **M5** | Shared source set for genuinely identical-FQN types only | The file imports zero loader packages, or only ones identical on both | Rare. If it qualifies it usually belongs in `common` (M1) anyway. |
| **M6** | ~~Reconfigure the build so `common` compiles against NeoForge, Architectury remaps to Forge~~ | — | **Ruled out (Step 0, 2026-08-29).** Architectury's transform is one-directional (`TransformForgeLikeToNeoForge`); there is no NeoForge-base-common mode. The only supported "big common" is Forge-idiom source auto-remapped *to* NeoForge — which contradicts the NeoForge-idiom decision and, more decisively, cannot bridge the Forge↔NeoForge `FluidStack` API-shape gap (NBT-tag vs component-based). The hand-written `ForgeFluidStacks` / `NeoForgeFluidStacks` converters in this repo already exist because of that gap. The whole fluid layer would stay duplicated regardless. |

**Consequence:** the shared-source-set premise is gone and, with M6 ruled out (row above), the
realistically recoverable duplication is on the order of a few hundred lines (M1 on `FluidPickup`,
leaf predicates, a spawn-egg color lookup) plus idiom fixes — **not** the full 2,700. The rest is
accepted duplication (M3). Reducing it further would require a `common` fluid-store abstraction
(3 impls over `IFluidHandler` / `Storage<FluidVariant>`), which is the speculative-abstraction the
project principle rejects.

## Baseline: GameTest coverage

Shared scenario bodies in `common/src/gametest/java` (11 files: BB, SB, MB, Loot, Protection,
Automation, State, StorageBucket, Presentation, Recipe, support). Each loader adds a parallel
discovery suite plus loader-only suites (`ForgeOnly*`, `NeoForgeOnly*`, fuel, block-capability,
cauldron, transfer, fill-bucket-event). Strong for world/fluid/automation logic. **Weak for
rendering**: `PresentationGameTests` exists but visual correctness of `JBRenderer` /
`StoredFluidContainerModel` is not meaningfully asserted — treat any change there as higher risk.

---

## Worry 1 — logic duplicated per loader that should be shared

The port copied each finished Forge file into `neoforge/` and applied
`s/net.minecraftforge/net.neoforged.neoforge/` plus small method-name fixes. ~2,700 lines of
`neoforge/src/main` are a mechanical transform of the matching `forge/src/main` file. Because of the
mechanism constraint above, and with M6 ruled out, most of these can only be de-duplicated by M1
(extract the vanilla-typed core), not by moving the file. What M1 cannot reach is accepted as
duplication (M3).

### Fluid / interaction pairs

| Files (`forge/` + `neoforge/`) | ~Lines | What the two copies actually differ by | Fix path | Recoverable now? |
| --- | --- | --- | --- | --- |
| `fluid/AbstractFluidHandler.java` + `BBFluidHandler` + `SBFluidHandler` | 123 + 61 + 61 | `implements IFluidHandlerItem` (divergent pkg); `ForgeFluidStacks`↔`NeoForgeFluidStacks`; `isFluidEqual`↔`sameFluid` | These **are** loader capability adapters. M3 (accept) | No — irreducible (M3) |
| `interaction/Transfers.java` | 499 | Threads `IFluidHandler` / `IFluidHandlerItem` / `FluidUtil` / `ForgeCapabilities` throughout | M1 can lift only leaf policy (settlement ordering, `isOurs`, milk branch already call `common` `HeldTransferSettlement`/`MilkTransfers`). Body stays per-loader. | Partial — small leaf extraction only |
| `interaction/Cauldrons.java` | 223 | `FluidStack` / `IFluidHandler` values; `ForgeFluidStacks` | M1 leaf predicates only; body stays. | Partial |
| `interaction/Dispensers.java` | 131 | `ForgeFluidStacks.get` calls only | M1 leaf only; body stays per-loader. | Partial |
| `fluid/FluidPickup.java` | 107 | Forge block wrappers (`FluidBlockWrapper`, `BucketPickupHandlerWrapper`, `IFluidBlock`) present the vanilla `BucketPickup` contract as `IFluidHandler` | **Done (slices 2 + 4).** `common/fluid/WorldFluidPickup` (`sourceAt` + `take` + `completePlayerPickup`); all three loaders use it; both `forge/` and `neoforge/` `FluidPickup.java` deleted. `BBFluidLogic`/`SBFluidLogic` world-pickup paths converted to `StoredFluid` — Forge and NeoForge take-path code is now byte-identical. | ✓ (all 3) |
| `fluid/SBFluidLogic.java` | 321 | `ForgeFluidStacks`↔`NeoForgeFluidStacks`, `ForgeFluidPlacement`↔`NeoForgeFluidPlacement`, imports | Slice 4: world-pickup path converted to `StoredFluid`; Forge/NeoForge take-path identical. Slice 4b: assignment/allowlist predicate **not** extracted — Forge/NeoForge thread `FluidStack assigned` for the capability path, thin shared surface (M3). Remaining divergence is the loader-`FluidStack` block-capability + placement threading. | Partial |
| `fluid/BBFluidLogic.java` | 373 | Same, **plus** a genuine divergence: NeoForge `tryPlacePowder` runs a `BlockSnapshot` / `EventHooks` hand-rollback because NeoForge defers `EntityPlaceEvent` (documented invariant) | Slices 4 + 4b: world-pickup path + finite-take admission predicate (`BBItem.canAcceptFluidUnit`) now shared; Forge/NeoForge take-path byte-identical. Remaining per-loader: block-capability threading, placement, and the NeoForge powder-place hook. | Partial |

**Caveat 1w (`FluidPickup` M1):** the Forge copy's `IFluidBlock` / `FluidBlockWrapper` branch
handles modded fluid blocks that are `IFluidBlock` but **not** vanilla `BucketPickup`. A vanilla-only
`common` replacement drops that branch. Need to confirm whether any supported modded-fluid world
pickup actually relies on it (NeoForge already dropped `FluidBlockWrapper`, so NeoForge's copy may
already not cover it — check parity first). If NeoForge already lacks it, removing it on Forge is a
consistency fix, not a regression.

### Client pairs

| Files | ~Lines | Differ by | Fix path | Recoverable now? |
| --- | --- | --- | --- | --- |
| `client/JBModel.java` | 29 | pure import pkg; whole body is `extends BakedModelWrapper` | M3 (accept) | No dedup — M3 |
| `client/ClientFluidColors.java` | 26 | `IClientFluidTypeExtensions` + `FluidStack` (divergent); texture-color math already in `common/ClientTextureColors` | M3, or M2 hook feeding `ClientTextureColors` | Marginal |
| `client/JBRenderer.java` | 231 | `BakedModelWrapper`, `IClientItemExtensions`, `IQuadTransformer.STRIDE/POSITION/COLOR/UV0/NORMAL` woven into the quad-packing math, `ModelData`, `@OnlyIn` | M1 possible for the `renderByItem` traversal + a vanilla-typed quad builder (hardcoding stride is dangerous — must match loader vertex format). `JunkForegroundGeometry` rectangle list already in `common`. | Partial, **high risk / low coverage** — own slice later |
| `client/StoredFluidContainerModel.java` | 117 | Forge `DynamicFluidContainerModel` + geometry-loader interfaces vs NeoForge geometry API — genuinely different unbaked-geometry plumbing | Mask/tint math may already be in `common` (`ClientTextureColors`, `BucketMouth`). Shell stays per-loader. | Partial, low priority |
| `client/ClientColorHandlers.java` | 75 | `RegisterColorHandlersEvent` (divergent); `ForgeSpawnEggItem.fromEntityType` vs vanilla `SpawnEggItem.byId`; `IClientFluidTypeExtensions` | **Slice 3 dropped → M3 (accept).** The spawn-egg lookup genuinely differs: Forge needs `ForgeSpawnEggItem.fromEntityType` to see `DeferredSpawnEggItem`-registered modded eggs; NeoForge/Fabric already use vanilla `SpawnEggItem.byId`. Only the ~4-line color formula is shareable — not worth a class. Registration + fluid-tint surface is divergent anyway. | No |

### Registration / config / loot pairs

| Files | ~Lines | Differ by | Fix path | Recoverable now? |
| --- | --- | --- | --- | --- |
| `register/ModItems.java` | 45 | `ForgeXXItem`↔`NeoForgeXXItem` class names; `ForgeRegistries.X`↔`Registries.X`; `RegistryObject`↔`DeferredHolder` | `DeferredRegister` is a divergent package. M3, Item-id/capacity data already centralized in `BucketDefinitions`. | No — M3 |
| `register/ModSounds.java` | ~40 | `ForgeRegistries.SOUND_EVENTS`↔`Registries.SOUND_EVENT`, `RegistryObject`↔`DeferredHolder` | M3, Sound-id strings already in `common/ModSoundIds`. | No — M3 |
| `register/ModCreativeTabs.java` | 34 | registration API only; both already consume `CreativeBucketCatalog` | M3 (accept) | No — M3 |
| `config/ServerConfig.java` | 37 | both wrap `ModConfigSpec`; `SBPolicy` already common | M3 (accept) | No — M3 |
| `loot/AddBucketLootModifier.java` | 51 | 4 lines; codec + registration near-identical, `forge:`/`neoforge:` namespaces already generated from the common manifest | M3 (accept) | No — M3 |

### Genuinely different — leave separate regardless

| File | Why |
| --- | --- |
| `crafting/SpawnEggIngredient.java`, `crafting/EmptyBucketIngredient.java` | Forge `AbstractIngredient` / `IIngredientSerializer` vs NeoForge `ICustomIngredient` / `IngredientType` / stream codecs |
| `fluid/FluidProvider.java` (Forge) vs NeoForge capability registrar | Forge `AttachCapabilitiesEvent` + `LazyOptional` vs NeoForge `RegisterCapabilitiesEvent` + nullable |

### Item-subclass asymmetry

`forge` has 5 item shells (`ForgeBBItem`/`ForgeSBItem` for capability identity;
`ForgeJBItem`/`ForgeMBItem`/`ForgeTBItem` only to override `initializeClient` for the BEWLR
renderer). `neoforge` has 2. `fabric` has 2. The three extra Forge shells exist because Forge 52
registers client item extensions per-item via `initializeClient`, whereas NeoForge uses external
`RegisterClientExtensionsEvent`. **Investigate once** whether Forge 52 exposes any external
registration; if not this is loader-mandated. Low priority. M3 otherwise.

---

## Worry 2 — 1.20.1 patterns kept alive with wrappers or distortions

| # | Location | Pattern | Target | Fix path | Risk | Coverage |
| --- | --- | --- | --- | --- | --- | --- |
| 2a | `forge/client/SidedFluidColors.java` | `DistExecutor.unsafeRunForDist(() -> () -> …, () -> () -> …)` | **✓ slice 1** — now `if (FMLEnvironment.dist == Dist.CLIENT)` + `ClientHolder`, mirroring NeoForge's file | **M4** | Low | Presentation |
| 2b | `forge/client/ClientModelLoaders.java` | `new ModelResourceLocation(id, "inventory")` | **✓ slice 1** — now `ModelResourceLocation.inventory(id)`. The `Loader.NAME` (Forge `String`) vs `Loader.ID` (NeoForge `ResourceLocation`) split is left alone: not an idiom fix — Forge's `ModelEvent.RegisterGeometryLoaders#register` takes a `String`, NeoForge's a `ResourceLocation` | **M4** | Low | Presentation |
| 2c | `neoforge/fluid/BBFluidLogic.tryPlacePowder` | Manual `BlockSnapshot` capture + `EventHooks.onBlockPlace`/`onMultiBlockPlace` + hand-rollback + `markAndNotifyBlock`, because NeoForge defers `EntityPlaceEvent` and its held-stack rollback can't undo the `custom_data` debit | Keep the mechanism (real NeoForge constraint, maintenance invariant). If `BBFluidLogic` is ever shared/lifted, this becomes one named virtual/`@ExpectPlatform` hook, no-op on Forge | M1 (accepted) | **Medium** — the "canceled placement must not debit" invariant | BB scenarios + `NeoForgeOnlyBBGameTests` |
| 2d | `neoforge/util/NeoForgeFluidStacks` | `DataComponentPatch.CODEC` over plain `NbtOps`; registry-context components "degrade to a blank patch" | **Done (slice 7).** `NeoForgeFluidStacksGameTests` asserts a registry-free `custom_data` variant round-trips both directions and through the item-stack `set`/`get` path. No code change. | test-only | Low | `NeoForgeFluidStacksGameTests` |
| 2e | `forge/platform/ForgeBucketOperations.beforeWorldBucketUse` returns `InteractionResultHolder` to fire `ForgeEventFactory.onBucketUse` (`FillBucketEvent`); NeoForge + Fabric no-op it | Seam method shaped around a Forge-only event | **Done (slice 8).** `BucketOperations.beforeWorldBucketUse` javadoc + class javadoc mark it a Forge `FillBucketEvent` carve-out; Fabric's `return null` carries a matching comment. Seam kept. | doc | Low | `FillBucketEventGameTests` (Forge) |
| 2f | `common/fluid/FluidPlacement` + `forge/fluid/ForgeFluidPlacement` + `neoforge/fluid/NeoForgeFluidPlacement` + `fabric/platform/FabricFluidPlacement` | Four placement classes | Confirm the `common` one hasn't accreted general-placement duty beyond its fixed-water aquatic responsibility; check the three loader copies for logic that is actually vanilla-only | M1 audit | Medium | BB/SB placement, MB aquatic |
| 2g | `forge/fluid/FluidPickup` imports `IFluidBlock` / `FluidBlockWrapper` | Forge-only legacy fluid-block wrapper; NeoForge dropped `FluidBlockWrapper` | **Done (slice 4).** `forge/fluid/FluidPickup.java` deleted; the `IFluidBlock` branch is gone. Forge now matches NeoForge and Fabric: world pickup is vanilla `BucketPickup`-only. A modded fluid *block* that is not a `BucketPickup` is no longer pickable on Forge — already the case on NeoForge/Fabric. | M1 | Low–Med | BlockCapability + BB |
| 2h | `forge` + `neoforge` `register/*` | `DeferredRegister`+`RegistryObject` (Forge) vs `DeferredRegister`+`DeferredHolder` (NeoForge); `ForgeRegistries.X` vs `Registries.X` | Divergent `DeferredRegister` package; not shareable. Accepted. | M3 | Low | all discovery suites |

---

## Worry 3 — Forge privilege baked in during the 1.20.1 era

| # | Location | Forge-first shape | NeoForge-privileged target | Fix path |
| --- | --- | --- | --- | --- |
| 3a | Every near-verbatim pair in Worry 1 | The NeoForge copy is `sed`-derived from the Forge copy; Forge spelling wins every delta | Whatever ends up shared (M1 extractions) is authored in NeoForge idiom, Forge adapts. Pairs that stay duplicated (M3) keep their own spellings. | M1 per file |
| 3b | `common/platform/BucketOperations` | 20-method seam designed against Forge's 1.20.1 capability model; `beforeWorldBucketUse` returns a Forge-style `InteractionResultHolder` | **Done (slice 8).** Method-by-method read: `beforeWorldBucketUse` is the only Forge-shaped method; now labeled as such in the contract. The rest are already in vanilla + `StoredFluid` terms. Class javadoc forbids adding loader-shaped methods for one platform. | doc |
| 3c | `as-built.md` says `neoforge` "reuses the `forge` module's structure and most of its non-capability code" | Documentation enshrines Forge as the template | **Done (slice 9).** `as-built.md` reframes `forge`/`neoforge` as parallel peers that share no code directly but lift loader-neutral logic into `common`; `WorldFluidPickup` added to the ownership table, seams section, and a maintenance invariant. | doc |
| 3d | No FTB Chunks build for Forge on 1.21.1; `common/src/compat/java` wired into `neoforge` + `fabric` only | Already correct — noted so cleanup doesn't "fix" it | Leave as is | none |
| 3e | **The whole `common` base.** `common` is transformed from a vanilla+Fabric base and cannot see NeoForge symbols. NeoForge — the dominant 1.21.1 loader — is a downstream consumer just like Forge | Structural "privilege NeoForge" would need `common` to compile against NeoForge. **Not possible:** Architectury's transform is Forge→NeoForge only (Step 0). Addressed only at the idiom level — M1 extractions use NeoForge spelling. | none (ruled out) |

---

## Cross-cutting: the Fabric asymmetry

`fabric/platform/FabricBucketOperations` is **719 lines** and inlines every BB/SB/powder/source
decision. Forge and NeoForge layer the same behavior as
`BucketOperations` → `BBFluidLogic` / `SBFluidLogic` / `Transfers`. Fabric therefore holds a third
copy of the mode/capacity/variant/FIFO/protection-order logic. Any `common` policy fragment created
by an M1 extraction must replace the Fabric inline copy in the same slice, or the third copy just
drifts. `FluidPickup` M1 (above) is the clearest shared win across all three.

---

## Strategic fork — resolved

Step 0 (the M6 spike) was researched to conclusion on 2026-08-29 and **M6 is ruled out** (see the M6
row above and `cleanup-plan.md` → "Step 0"). There is no build-config change that lets the fluid
layer move to `common`, and NeoForge-idiom shared code is not what Architectury's transform produces.

**The path is incremental (M1 / M2 / M4, no build-environment change).**
Realistically recovers: `FluidPickup` unified into `common` (also de-inlines the Fabric copy); leaf
-predicate extraction from `BB`/`SB` logic (shared with Fabric); spawn-egg color into `common`; the
2a/2b idiom fixes; the 2d/2f/2g audits. Order-of ~400–700 lines of duplication removed plus
modernization. `AbstractFluidHandler` + handlers, `Transfers`, `Cauldrons`, `Dispensers`, all
`register/*`, `config/ServerConfig`, `loot/AddBucketLootModifier` **stay duplicated**, accepted per
the project's "some duplication beats one more small class" principle. Ships in small
independently-verifiable slices.

Worry 3 ("privilege NeoForge") is therefore addressed at the level it can be: each M1 extraction is
authored in NeoForge idiom, Forge-only seam methods are labeled as such, and `as-built.md` stops
describing `neoforge` as tracking `forge`. It is not addressed structurally, because the tool does
not allow it.
