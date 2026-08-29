# Fabric 1.21.1 Port Plan

## Objective

Produce a Fabric artifact for Minecraft 1.21.1 (Fabric Loader 0.19.3, Fabric API 0.116.15+1.21.1)
that compiles, packages, starts under the Fabric GameTest server, and passes the ported automated
test suite while preserving the behavior and invariants documented in `player-view.md` and
`as-built.md`.

The plan is Fabric-first. Common code is in scope where Fabric production or test code depends on it,
but the completed Forge 1.21.1 port must not regress. NeoForge repair is not required. The Fabric
artifact is also the Quilt artifact and needs no separate work.

## Completion gates

The Fabric port is complete only when all of these succeed in the current build environment:

1. `./gradlew.bat :common:compileJava --console=plain`
2. `./gradlew.bat :fabric:compileJava --console=plain`
3. `./gradlew.bat :fabric:processResources --console=plain`
4. `./gradlew.bat :fabric:compileGametestJava --console=plain`
5. `./gradlew.bat :fabric:runGameTestServer --console=plain`
6. `./gradlew.bat :fabric:build --console=plain`
7. `./gradlew.bat :forge:compileJava --console=plain` — regression guard; required only if any stage
   changed `common`

On Windows PowerShell, execution may use `.\gradlew.bat` for the same commands.

The final handoff must also identify any client presentation that remains suitable only for a manual
client smoke test. A manual visual test is recommended but is not an unattended completion gate.

## Session discipline

Execute one stage per session. Begin each session by reading `CLAUDE.md`, this plan,
`fabric-1.21.1-port-process.md`, and `fabric-1.21.1-port-status.md`; `fabric-1.21.1-port-assessment.md`
and the completed `forge-1.21.1-port-status.md` are reference material consulted by section. End the
session when the stage's primary gate passes and leave the handoff in the snapshot.
`fabric-1.21.1-port-process.md` governs how state is split between the overwritten
`fabric-1.21.1-port-status.md` snapshot and the append-only `fabric-1.21.1-port-log.md`: the snapshot
is small and rewritten in place, the log is write-only and never read during execution, and verbose
command output is reduced to a count and a delta before it is recorded.

## Stage summary

| Stage | Work product | Primary gate |
| --- | --- | --- |
| 0 | Baseline diagnostics and persistent status | One diagnostic Fabric compile |
| 1 | Common reconciliation under the Fabric transform | Passing common compile, Forge still green |
| 2 | Fabric bootstrap, registration, identifiers, metadata | Diagnostic Fabric production compile |
| 3 | Fabric Transfer API and fluid transfer core | Diagnostic Fabric production compile |
| 4 | Fabric server systems, interactions, mixins, data | Diagnostic Fabric compile plus resources |
| 5 | Fabric client models and presentation | Passing Fabric production compile |
| 6 | Fabric GameTest source and resource port | Passing GameTest compile |
| 7 | Runtime GameTest stabilization | Passing Fabric GameTest server |
| 8 | Final package and documentation reconciliation | Passing Fabric build |

Until Stage 5, a diagnostic module compile may still fail in a known later-stage subsystem. A stage
finishes only when its own failures are removed and every remaining compiler failure is recorded
against a later stage. A passing compile is mandatory where the table says passing.

Run the diagnostic compile once per stage, after the stage's edits are complete — not once per
substage or per file. Substages organize the implementation work; they are not separate verification
points.

## Stage 0 — Baseline diagnostics

### Scope

- Read `CLAUDE.md`, this plan, `fabric-1.21.1-port-process.md`, and `fabric-1.21.1-port-status.md`;
  the assessment and the completed Forge status file are reference material consulted by section.
- Confirm Gradle sync remains successful; do not alter dependency versions or `fabric/build.gradle`.
- Run one baseline `:fabric:compileJava` diagnostic. If it stops in `:common:compileJava`, record
  that; Stage 1 owns common.
- Classify compiler failures by the stages below.
- Record the outcome and major error groups as one log entry; record the first bounded work unit in
  the snapshot.

### Constraints

- The baseline diagnostic is discovery, not permission for opportunistic edits.
- Do not run `clean`, refresh dependencies, inspect caches, or change the build environment.
- Do not begin repairing Forge or NeoForge.

### Completion criteria

- The snapshot contains a reproducible baseline and a finite Stage 1 work unit; the log contains the
  baseline diagnostic entry.
- No code has been changed solely in response to unclassified errors.

## Stage 1 — Common reconciliation under the Fabric transform

### Scope

- Establish whether `:common:compileJava` still passes. It should; the Forge port left it green.
- Identify and fix common regressions that only the Fabric transform or Fabric consumers surface —
  for example a common interface whose signature changed for Forge but whose only remaining break is
  a Fabric implementer, or the cross-remapped `@Environment` client annotation.
- Make the `StoredFluid` to `FluidVariant` conversion decision: default is a Fabric-module-only
  `CompoundTag <-> DataComponentPatch` helper (mirroring `ForgeFluidStacks`), leaving `StoredFluid`'s
  common shape unchanged. Only change `StoredFluid` itself if the Fabric compile proves the
  boundary-only approach cannot preserve variant data.
- Every change under `common` is followed by `:forge:compileJava` before the stage closes.

### Verification

- `:common:compileJava` must pass.
- `:forge:compileJava` must still pass if `common` changed.
- Record any change to a common type used by Forge and its Forge re-check result.

### Completion criteria

- Common compiles for the Fabric transform.
- Forge production compilation is unregressed.
- The fluid-conversion approach is recorded as an established decision in the snapshot.

## Stage 2 — Fabric bootstrap, registration, identifiers, metadata

### Work units

1. Port `SomeBucketsFabric` initialization order and any changed lifecycle-event or
   `FabricLoader.isModLoaded` signatures; keep the install order for `AutomationPlayers`,
   `BucketOperations`, config, registries, and interaction registration.
2. Port `FabricItems`, `FabricSounds`, `FabricCreativeTabs` registration to current
   `Registry.register`, item-group, and `Item.Properties` shapes.
3. Replace every `new ResourceLocation(...)` in the Fabric module with the correct factory.
4. Set `somebuckets.fabric.mixins.json` `compatibilityLevel` to `JAVA_21`.
5. Confirm both `fabric.mod.json` files resolve their dependency expressions and entrypoints, and
   that the GameTest `fabric.mod.json` and `somebuckets-gametest` Loom mod still match.

### Verification

- Run `:fabric:compileJava` diagnostically once after all Stage 2 work units are implemented.
- Remaining errors must belong to Stages 3–5.

### Completion criteria

- Fabric initialization, registration, and identifiers use current APIs.
- Metadata and mixin config are on the 1.21.1 baseline.

## Stage 3 — Fabric Transfer API and fluid transfer core

### Stage 3A: stack-state copying

- Replace raw `getTag`/`hasTag`/`setTag` copies in `FabricBucketStorage` (`StackBackend`,
  `createSnapshot`, `readSnapshot`) and in `FabricBucketOperations#tryHeldTransfer` with
  component-based copying of the bucket's state components.
- Preserve `SnapshotParticipant` snapshot/restore semantics exactly.

### Stage 3B: `FluidVariant` payload conversion

- Implement the conversion chosen in Stage 1 and apply it at every `FluidVariant.of(...)` /
  `copyNbt()` site in `FabricBucketStorage`, `FabricBucketOperations`, and (client) via Stage 5.
- Preserve modded variant data through insert, extract, and display.

### Stage 3C: storage registration and operations

- Port `FabricFluidStorages` (`FluidStorage.ITEM.registerForItems`) and any changed
  `ContainerItemContext`, `Storage`, `StorageUtil`, `Transaction`, or `InventoryStorage` signatures
  in `FabricBucketOperations` and `FabricFluidPlacement`.
- Preserve one-unit-at-a-time finite transfer, Source Bucket infinity and type-stability, block-
  storage-owns-dispatch, preview-before-authorization, exact-target protection, and multi-count
  foreign-stack settlement through `HeldTransferSettlement` / `MilkTransfers`.
- Port `FabricFluidColors` / `platform` indirection signatures as needed.

### Tests to port or add

- Finite fill/drain simulation and execution, Source Bucket assignment and infinite transfer,
  variant-data preservation, block-storage transfer, multi-count held settlement. These live in the
  Fabric GameTest tree and are ported in Stage 6; note them here.

### Verification

- Run `:fabric:compileJava` diagnostically once after all Stage 3 substages.
- Failures in ingredients, loot, cauldrons, dispensers, or client code may remain for later stages.
- Review every mutation path for transaction and simulation safety.

### Stop conditions specific to this stage

- The boundary-only fluid conversion cannot preserve variant data and a `StoredFluid` redesign is
  required — stop and confirm the shape with the user.
- Legal multi-count settlement cannot be preserved through the common seam.

## Stage 4 — Fabric server systems, interactions, mixins, data

### Stage 4A: custom ingredients

- Port `FabricEmptyBucketIngredient` and `FabricSpawnEggIngredient` to the 1.21 Fabric
  `CustomIngredientSerializer` codec and registry-aware stream-codec model.
- Preserve ids `somebuckets:empty_bucket` and `somebuckets:spawn_egg` and exact match behavior.

### Stage 4B: loot injection

- Port `FabricBucketLoot` to the current `LootTableEvents.MODIFY` signature (`ResourceKey<LootTable>`
  identity, reduced parameters, `HolderLookup.Provider`).
- Replace `SetNbtFunction.setTag` with the current custom-data loot function for the powder-snow
  reward.
- Keep rewards sourced from `BucketLootTables` / `somebuckets/bucket_loot.json`.

### Stage 4C: cauldrons and dispensers

- Port `FabricCauldronInteractions` to `CauldronInteraction.InteractionMap` and
  `ItemInteractionResult`, preserving success and pass-through semantics.
- Port `FabricFluidDispensers` and the `NonFluidDispensers` usage to current dispenser behavior
  registration and the relocated `BlockSource`.
- Port `FabricHeldTransferEvents` to current `UseItemCallback` / event signatures.

### Stage 4D: mixins and fuel

- Re-verify `AbstractFurnaceBlockEntityMixin` targets and descriptors against 1.21.1 mappings; keep
  the finite lava-bucket burn behavior.
- Remove `ItemStackMixin` and its `somebuckets.fabric.mixins.json` entry if the common
  `MAX_STACK_SIZE` component is authoritative on Fabric; otherwise port it. Record which.

### Stage 4E: configuration and resources

- Port `FabricServerConfig` identifier parsing.
- Confirm the Fabric `assets` model JSON and both `fabric.mod.json` files are valid for 1.21.1.

### Verification

- `:fabric:processResources` must pass.
- Parse or inspect every changed JSON resource deterministically.
- Run `:fabric:compileJava` diagnostically; only Stage 5 client errors may remain.

### Completion criteria

- Fabric server production code and data have no known compile errors.
- Loot rewards remain derived from the common manifest.

## Stage 5 — Fabric client models and presentation

### Stage 5A: model loading and fluid container model

- Port `FabricFluidContainerModel` to the current `ModelLoadingPlugin` / model-modifier callback,
  `FabricBakedModel` quad emission, `BakedModel` interface members, `RenderContext` / `QuadEmitter`
  API, and `BakedQuad` vertex-array construction.
- Preserve mask-clipped fluid-layer geometry, the stored-fluid sprite selection through
  `FluidVariantRendering`, and tint-index behavior.

### Stage 5B: colors and item properties

- Port `SomeBucketsFabricClient` predicate registration (`ItemProperties.register`), item color
  providers (`ColorProviderRegistry.ITEM`), and `FabricClientFluidColors`.
- Verify `SpawnEggItem.byId` and `SpawnEggItem#getColor`.
- Preserve milk, powder-snow, empty, and filled visual selection and color-cache invalidation.

### Stage 5C: Junk Bucket renderer

- Port `FabricJunkBucketRenderer` and its model registration to the current dynamic item renderer
  interface and two-argument `ModelResourceLocation`.
- Preserve FIFO visual order, tint, glint, and cover geometry.
- Keep all client classes safe from dedicated-server classloading.

### Verification

- `:fabric:compileJava` must pass.
- Re-run `:common:compileJava` and `:forge:compileJava` if common client interfaces changed.
- Inspect client initializers for physical-side safety.

### Stop conditions specific to this stage

- The custom fluid layer cannot be shown to preserve variant tint semantics.
- A rendering fix requires changing textures, art, or documented presentation behavior.
- The only solution introduces client classloading on a dedicated server.

## Stage 6 — Fabric GameTest port

### Stage 6A: infrastructure

- Port `GameTestSupport` and the Fabric discovery wrappers / `FabricGameTest` annotations.
- Update the GameTest `fabric.mod.json` and template references only as required.
- Keep the shared scenarios (already ported) discoverable through this source set.

### Stage 6B: Fabric-specific tests

Port in coherent groups: state and presentation; recipes, loot, and fuel; Big/Huge and Source
Bucket; Junk/Trash and Mob Bucket; automation and protection; Transfer API and block-capability
coverage. Preserve assertions unless the plan records a Minecraft or Fabric semantic change.

### Verification

- `:fabric:compileGametestJava` must pass.
- Inspect discovery so a passing run cannot result from undiscovered tests.

### Completion criteria

- All existing intended Fabric tests compile and remain discoverable.
- No test disabled, deleted, or weakened to bypass a production failure.

## Stage 7 — Runtime GameTest stabilization

### Work units

Run the complete Fabric GameTest server and address failures in bounded subsystem groups:

1. server bootstrap, registry, resource, or discovery failures;
2. state and serialization failures, including `FluidVariant` round trips;
3. Transfer API and block-storage failures;
4. recipes, loot, cauldrons, fuel, and event failures;
5. storage, Mob Bucket, automation, and protection failures.

A work unit may address several tests only when they share one demonstrated production cause.

### Verification

- Run `:fabric:runGameTestServer --console=plain`.
- A complete successful server exit and reported passing suite are required.
- Apply the three-attempt rule in `fabric-1.21.1-port-process.md` to each bounded failure work unit.

### Test discipline

- Fix production code when a preserved assertion exposes a production defect.
- Update a test only for a demonstrated 1.21.1 or Fabric API change.
- Do not add delays, broaden tolerances, swallow exceptions, or remove assertions to pass.
- Treat hangs, crashes before discovery, and zero discovered tests as failures.

## Stage 8 — Final package and reconciliation

### Work units

1. Re-run the passing common compile, Fabric compile, resource processing, GameTest compile, and
   GameTest server gates if later changes could affect them, plus `:forge:compileJava` if any common
   change was made.
2. Run `:fabric:build --console=plain`.
3. Verify the expected Fabric artifact exists without inspecting or unarchiving it.
4. Reconcile `as-built.md`, `player-view.md`, and `build-env.md` with the completed Fabric state and
   the completed Forge state (both loaders now on 1.21.1).
5. Record NeoForge breakage or remaining work without attempting it.
6. Record any client-only manual smoke checks still recommended.

### Completion criteria

- Every completion gate passes.
- The snapshot says `complete`; the log contains every final passing command and outcome.
- Documentation describes the current 1.21.1 implementation rather than port history.
- No Git or GitHub action has been performed.

## Expected manual smoke test after unattended completion

A later human client smoke test should check, on Fabric:

- Big/Huge/Source fluid tint, including a variant-bearing modded fluid if available;
- milk and powder-snow overrides;
- Mob Bucket empty/filled model and spawn-egg colors;
- Junk Bucket protruding item order, tint, and glint;
- creative-tab contents and prefilled variants;
- tooltips, bars, use animations, and sounds.

Failure of this later smoke test opens a new bounded client work unit; it does not invalidate the
server and packaging evidence already recorded.
