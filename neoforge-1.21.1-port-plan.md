# NeoForge 1.21.1 Port Plan

## Objective

Produce a NeoForge artifact for Minecraft 1.21.1 (NeoForge `21.1.248`, NeoForge JavaFML loader,
Java 21) that compiles, packages, starts under the NeoForge GameTest server, and passes a ported
automated test suite equivalent in coverage to the Forge and Fabric suites, while preserving the
behavior and invariants documented in `player-view.md` and `as-built.md`.

This is **construction**, not migration: the `neoforge` subproject has build/metadata scaffolding
only and no loader Java. `forge/src/main` is the structural template; `fabric/src/main` and the
`fabric-1.21.1-port-*.md` documents are the precedent for the 1.21.1 boundary conversions that also
land on NeoForge. `common` is in scope only where NeoForge production or test code genuinely
requires it, and the completed **Forge and Fabric** 1.21.1 ports must not regress.

## Completion gates

The NeoForge port is complete only when all of these succeed in the current build environment:

1. `./gradlew.bat :common:compileJava --console=plain`
2. `./gradlew.bat :neoforge:compileJava --console=plain`
3. `./gradlew.bat :neoforge:processResources --console=plain`
4. `./gradlew.bat :neoforge:compileGametestJava --console=plain`
5. `./gradlew.bat :neoforge:runGameTestServer --console=plain`
6. `./gradlew.bat :neoforge:build --console=plain`
7. Regression guard — required only if any stage changed `common`:
   `./gradlew.bat :forge:compileJava :fabric:compileJava --console=plain`, plus
   `./gradlew.bat :fabric:compileGametestJava --console=plain` if shared GameTest code changed.

On Windows PowerShell, execution may use `.\gradlew.bat` for the same commands.

The final handoff must also identify any client presentation that remains suitable only for a manual
client smoke test. A manual visual test is recommended but is not an unattended completion gate.

## Session discipline

Execute one stage per session. Begin each session by reading `CLAUDE.md`, this plan,
`neoforge-1.21.1-port-process.md`, and `neoforge-1.21.1-port-status.md`;
`neoforge-1.21.1-port-assessment.md` and the `fabric-1.21.1-port-*.md` set are reference material
consulted by section. End the session when the stage's primary gate passes and leave the handoff in
the snapshot. `neoforge-1.21.1-port-process.md` governs how state is split between the overwritten
`neoforge-1.21.1-port-status.md` snapshot and the append-only `neoforge-1.21.1-port-log.md`: the
snapshot is small and rewritten in place, the log is write-only and never read during execution, and
verbose command output is reduced to a count and a delta before it is recorded.

## Stage summary

| Stage | Work product | Primary gate |
| --- | --- | --- |
| 0 | Build-wiring verification, per-file disposition inventory, persistent status seed | One `:neoforge:compileJava` run (vacuous — no sources yet) and a complete inventory in the snapshot |
| 1 | Common check under the NeoForge transform; `StoredFluid` conversion decision | Passing `:common:compileJava`; Forge and Fabric still green; decision recorded |
| 2 | NeoForge bootstrap, registration, identifiers, metadata, creative tab, sounds, items | Diagnostic `:neoforge:compileJava` |
| 3 | Capability layer, `FluidStack` conversion, fluid transfers, world pickup/placement | Diagnostic `:neoforge:compileJava` |
| 4 | Server systems: ingredients, loot modifier + `neoforge:` GLM generator, cauldrons, dispensers, held-transfer events, fuel, config | Passing `:neoforge:processResources` plus diagnostic `:neoforge:compileJava` |
| 5 | Client models, geometry loader, renderers, colors, item properties | Passing `:neoforge:compileJava` |
| 6 | GameTest build wiring and source port | Passing `:neoforge:compileGametestJava` |
| 7 | Runtime GameTest stabilization | Passing `:neoforge:runGameTestServer` |
| 8 | Final package and documentation reconciliation | Passing `:neoforge:build` |

Until Stage 5, a diagnostic `:neoforge:compileJava` is **expected to fail**: it will reference
classes from files not yet written. A stage finishes when its own files exist and compile in
isolation of the missing later-stage files — that is, every remaining error names a symbol owned by
a later stage — and no error is a regression in an earlier stage's files or in `common`, Forge, or
Fabric. A genuinely passing compile is mandatory only where the table says "passing".

Run the diagnostic compile once per stage, after the stage's files are written — not once per file
or per substage. Substages organize the work; they are not separate verification points.

## Stage 0 — Build-wiring verification and disposition inventory

### Scope

- Read `CLAUDE.md`, this plan, `neoforge-1.21.1-port-process.md`, and
  `neoforge-1.21.1-port-status.md`; the assessment and the `fabric-1.21.1-port-*.md` set are
  reference material consulted by section.
- Confirm Gradle sync resolves the `neoforge` subproject and that `neoforge/build.gradle`'s
  `transformProductionNeoForge` bundle, Shadow, and remap chain are wired (they are, per the file).
- Run one `:neoforge:compileJava --console=plain`. With no sources it should be trivially
  successful or a no-op; record the actual result. Do **not** treat this as an error-classification
  baseline — there is no code to classify.
- Produce the **per-file disposition inventory**: for every file in `forge/src/main` and every
  NeoForge-specific new file named in the assessment's construction map, record its target NeoForge
  path, disposition (A/B/C/D/N/G), and the one or two API facts that make it non-trivial. This
  inventory is what the Fabric port's error classification was — it drives the stage assignments.
- Confirm the `neoforge.mods.toml` token set matches `neoforge/build.gradle`'s `replaceProperties`
  and `gradle.properties` (`neoforge_version_range`, `neoforge_loader_version_range`,
  `ftb_chunks_version_range`, etc.).
- Record the outcome and the inventory as one log entry; record the first Stage 1 work unit in the
  snapshot.

### Constraints

- No code, resource, or build-file change in Stage 0. The inventory is discovery, not permission for
  opportunistic edits.
- Do not run `clean`, refresh dependencies, inspect caches, or change the build environment.
- Do not begin repairing or altering Forge or Fabric.

### Completion criteria

- The snapshot contains the complete disposition inventory, a reproducible Stage 0 build result, and
  a finite first Stage 1 work unit; the log contains the Stage 0 entry.
- No file has been created or changed.

## Stage 1 — Common under the NeoForge transform

### Scope

- Run `:common:compileJava`. It should pass unchanged; both prior ports left it green.
- Identify any common break that only Architectury's **NeoForge** transform surfaces — most plausibly
  the cross-remapped `@Environment` → `@OnlyIn` client annotation, or a common interface whose only
  unimplemented method is a NeoForge concern. Expectation: none, exactly as on Fabric.
- Ratify the `StoredFluid` → NeoForge `FluidStack` conversion approach. Default: a
  NeoForge-module-only `NeoForgeFluidStacks` helper doing `CompoundTag <-> DataComponentPatch` at
  the boundary with plain `NbtOps` and graceful degradation (registry-context components become a
  blank patch), `StoredFluid`'s common shape unchanged — mirroring `ForgeFluidStacks` and
  `fabric/.../fluid/FabricFluidVariants`. Record it as an established decision. Adopting the
  graceful-degradation narrowing requires user confirmation, as it did on Fabric.
- Any change under `common` is followed by `:forge:compileJava` **and** `:fabric:compileJava` before
  the stage closes (and `:fabric:compileGametestJava` if shared GameTest code changed).

### Verification

- `:common:compileJava` must pass.
- `:forge:compileJava` and `:fabric:compileJava` must still pass if `common` changed.
- Record any change to a common type used by Forge or Fabric and both re-check results.

### Completion criteria

- Common compiles for the NeoForge transform.
- Forge and Fabric production compilation is unregressed.
- The fluid-conversion approach is recorded as an established decision in the snapshot.

## Stage 2 — Bootstrap, registration, identifiers, metadata

### Work units

1. `SomeBucketsNeoForge` entry point: `@Mod` constructor injection (mod bus / `ModContainer`),
   `NeoForge.EVENT_BUS` for game events, config registration, the `BucketOperations` and
   `AutomationPlayers` installs, the content `DeferredRegister`s, and common-setup `enqueueWork` for
   dispenser/cauldron registration. Preserve the install order from `SomeBucketsForge`.
2. `ModItems`, `ModSounds`, `ModCreativeTabs` on NeoForge `DeferredRegister` with vanilla
   `Registries.*` keys; consume `BucketDefinitions` and `CreativeBucketCatalog` unchanged. Item
   shells `NeoForgeBBItem` / `JBItem` / `MBItem` / `SBItem` / `TBItem` as thin wrappers over the
   common item classes (fuel hook and `IClientItemExtensions` may attach here in later stages).
3. `ServerConfig` on the NeoForge `ModConfigSpec` successor; keys, section, and `SBPolicy` wiring
   unchanged.
4. `AutomationPlayers` NeoForge install with a `FakePlayer` / `FakePlayerFactory` stable fake player
   named `[SomeBuckets]`.
5. FTB Chunks presence check that registers the shared `FtbChunksProtection` provider (mirrors
   Fabric).
6. Confirm `neoforge.mods.toml` resolves its dependency expressions; no gametest metadata yet.

### Verification

- Run `:neoforge:compileJava` diagnostically once after all Stage 2 work units are written.
- Remaining errors must all name symbols owned by Stages 3–5.

### Completion criteria

- NeoForge initialization, registration, identifiers, config, fake player, and optional-mod wiring
  use current NeoForge APIs.
- `neoforge.mods.toml` is on the 1.21.1 baseline.

## Stage 3 — Capability layer, fluid transfer core

### Stage 3A: item capability attach

- `FluidProvider` NeoForge form: register `Capabilities.FluidHandler.ITEM` (or the item-specific
  registration) in `RegisterCapabilitiesEvent` for the BB and SB items, with a factory building a
  stack-bound `BBFluidHandler` / `SBFluidHandler`. No `AttachCapabilitiesEvent`, `ICapabilityProvider`,
  or `LazyOptional`.
- `requireBucketHandler` keeps its fail-fast on a missing capability.

### Stage 3B: `FluidStack` payload conversion

- Implement `NeoForgeFluidStacks` (the Stage 1 decision) and apply it at every `FluidStack`
  construction and component-read site in the handlers, `Transfers`, `*FluidLogic`, and placement.
- Preserve modded variant data through fill, drain, and display.

### Stage 3C: handlers, logic, transfers, world pickup/placement

- `AbstractFluidHandler`, `BBFluidHandler`, `SBFluidHandler` against NeoForge `IFluidHandlerItem`
  (retained) with component-based `FluidStack`; keep tank-0-only, fluid-mode dispatch, and finite vs
  infinite `fillEmpty` / `fillExisting` / `performDrain` policy with exact simulate/execute parity.
- `Transfers` block lookups via `level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state,
  blockEntity, face)`; `FluidUtil.tryFluidTransfer`, `FluidBucketWrapper`, `FluidType.BUCKET_VOLUME`,
  `SoundActions` from `net.neoforged.*`.
- `FluidPickup`, `ForgeFluidPlacement` counterpart, `BBFluidLogic`, `SBFluidLogic` against the
  1.21.1 `BucketPickup` / `LiquidBlockContainer` signatures the Forge and Fabric ports already
  settled.
- `ForgeBucketOperations` counterpart implementing `BucketOperations`; `beforeWorldBucketUse` →
  NeoForge bucket-use hook.
- Preserve: one-unit finite transfer, Source Bucket infinity and type-stability,
  block-storage-owns-dispatch (a present sided handler owns the interaction even when it refuses),
  preview-before-authorization, exact-target protection, contract-violation reporting, and
  multi-count foreign-stack settlement through `HeldTransferSettlement` / `MilkTransfers`.

### Verification

- Run `:neoforge:compileJava` diagnostically once after all Stage 3 substages.
- Failures in ingredients, loot, cauldrons, dispensers, fuel, or client code may remain for later
  stages.
- Review every mutation path for transaction and simulation safety.

### Stop conditions specific to this stage

- The boundary-only `FluidStack` conversion cannot preserve variant data and a `StoredFluid`
  redesign is required — stop and confirm the shape with the user.
- The NeoForge capability model cannot express a documented BB/SB behavior (finite debit, infinite
  source, block-storage dispatch ownership) — stop and report.
- Legal multi-count settlement cannot be preserved through the common seam — stop.

## Stage 4 — Server systems: ingredients, loot, cauldrons, dispensers, events, fuel, config

### Stage 4A: custom ingredients

- `EmptyBucketIngredient` / `SpawnEggIngredient` to NeoForge `ICustomIngredient` + a registered
  `IngredientType` with `MapCodec` + `RegistryFriendlyByteBuf` stream codec; register the types via
  `DeferredRegister` on the NeoForge ingredient-type registry.
- Preserve ids `somebuckets:empty_bucket` / `somebuckets:spawn_egg`, component-sensitive
  `EmptyBucketIngredient` matching, non-simple behavior, and all-spawn-egg matching.

### Stage 4B: global loot modifiers

- `AddBucketLootModifier` NeoForge port (package change only; `CODEC` and `doApply` reused).
- `ModLootModifiers` on the NeoForge GLM serializer registry key.
- **Build:** add `generateBucketLootModifiers` to `neoforge/build.gradle`, reading
  `common/src/main/resources/somebuckets/bucket_loot.json`, emitting
  `data/somebuckets/loot_modifiers/<id>.json` with `neoforge:loot_table_id` conditions and
  `data/neoforge/loot_modifiers/global_loot_modifiers.json`; wire it into `processResources` and the
  main resources srcDir exactly as `forge/build.gradle` does with the `forge:` namespace.

### Stage 4C: cauldrons, dispensers, held-transfer events

- `Cauldrons` counterpart on `CauldronInteraction.InteractionMap` + `ItemInteractionResult`.
- `Dispensers` counterpart on `DispenseItemBehavior` and `net.minecraft.core.dispenser.BlockSource`;
  `NonFluidDispensers` / `DispenserTarget` unchanged.
- `ForgeHeldTransferEvents` counterpart on `NeoForge.EVENT_BUS` interaction events; targeted block
  takes precedence over the air transfer.

### Stage 4D: fuel

- Give the lava-filled Big/Huge Bucket a finite 20,000-tick burn via the NeoForge
  `IItemExtension#getBurnTime` item hook (or its `21.1.248` successor); no mixin. An allowed lava
  Source Bucket returns permanent fuel. `BucketFuel` and the tick constant are common.

### Stage 4E: configuration and resources

- Confirm `ServerConfig` resolves under the `ModConfigSpec` successor.
- Confirm `neoforge.mods.toml` and any `assets` model JSON are valid for 1.21.1.

### Verification

- `:neoforge:processResources` must pass; parse or inspect every generated/changed JSON resource
  deterministically, including the emitted GLM files and the `neoforge:` condition ids.
- Run `:neoforge:compileJava` diagnostically; only Stage 5 client errors may remain.

### Completion criteria

- NeoForge server production code and data have no known compile errors.
- Loot rewards remain derived from the common manifest; GLM resources carry the `neoforge:`
  namespace.

## Stage 5 — Client models and presentation

### Stage 5A: model loading and the fluid container model

- `ClientModelLoaders` / `StoredFluidContainerModel` counterparts on the NeoForge geometry-loader
  and `ModelEvent.RegisterGeometryLoaders` / `ModelEvent.ModifyBakingResult` APIs.
- Preserve mask-clipped fluid-layer geometry, stored-fluid sprite selection, and tint-index
  behavior. Copy the Forge item model JSONs (`big_bucket_64`, `big_bucket_8`, `junk_bucket`,
  `source_bucket`) and confirm them against the geometry loader.

### Stage 5B: colors and item properties

- `ClientSetup` counterpart: `ItemProperties.register` predicates, `RegisterColorHandlersEvent.Item`
  color providers, `RegisterClientReloadListenersEvent`, `FMLClientSetupEvent`.
- `ClientColorHandlers`, `ClientFluidColors`, `SidedFluidColors` counterparts; fluid color path
  through `NeoForgeFluidStacks`.
- Verify `SpawnEggItem.byId` / `#getColor`. Preserve milk, powder-snow, empty, filled selection and
  color-cache invalidation.

### Stage 5C: Junk Bucket renderer

- `JBModel` / `JBRenderer` counterparts on NeoForge `IClientItemExtensions#getCustomRenderer` and
  two-argument `ModelResourceLocation`.
- Preserve FIFO visual order, tint, glint, cover geometry. Keep all client classes safe from
  dedicated-server classloading.

### Verification

- `:neoforge:compileJava` must pass.
- Re-run `:common:compileJava`, `:forge:compileJava`, `:fabric:compileJava` only if common client
  interfaces changed.
- Inspect client initializers for physical-side safety.

### Stop conditions specific to this stage

- The custom fluid layer cannot be shown to preserve variant tint semantics.
- A rendering fix requires changing textures, art, or documented presentation behavior.
- The only solution introduces client classloading on a dedicated server.

## Stage 6 — GameTest build wiring and source port

### Stage 6A: build wiring (authorized `neoforge/build.gradle` construction)

- Add a `gametest` source set that `srcDir`s `common/src/gametest/java` plus
  `neoforge/src/gametest/java`, with the Forge module's compile/runtime-classpath composition.
- Add `loom { mods { somebuckets_gametest { sourceSet sourceSets.gametest } } }` and
  `loom { runs { gameTestServer { server(); … ; property '<neoforge enabled-namespaces>', mod_id;
  source sourceSets.gametest } } }`.
- Call `rootProject.configureGameTestStructures(project)`.
- Add `neoforge/src/gametest/resources/META-INF/neoforge.mods.toml` (gametest variant),
  `pack.mcmeta`, and a `@Mod("somebuckets_gametest")` stub class.

### Stage 6B: discovery wrappers and NeoForge-specific tests

- Port `GameTestSupport` and the discovery wrappers using the NeoForge GameTest discovery mechanism
  (`@GameTestHolder` / `@PrefixGameTestTemplate` and/or a registration entrypoint — resolve from
  NeoForge docs).
- Keep the shared `**Scenarios` bodies discoverable through this source set unchanged.
- Port NeoForge-specific coverage in coherent groups mirroring the Forge `Forge*` / `ForgeOnly*`
  classes: capability transfer and block-capability coverage, cauldrons, recipes/loot/fuel,
  state/presentation, Big/Huge and Source Bucket, Junk/Trash and Mob Bucket, automation and
  protection. Add a NeoForge equivalent of a `Forge*`-only test only where the behavior exists on
  NeoForge.

### Verification

- `:neoforge:compileGametestJava` must pass.
- Inspect the discovery registration so a later passing run cannot result from undiscovered tests.

### Completion criteria

- The NeoForge gametest source set exists, builds, and is wired into a `runGameTestServer` run.
- All intended shared and NeoForge-specific tests compile and are discoverable.
- No test disabled, deleted, or weakened to bypass a production failure.

## Stage 7 — Runtime GameTest stabilization

### Work units

Run the complete NeoForge GameTest server and address failures in bounded subsystem groups:

1. server bootstrap, registry, resource, or discovery failures;
2. state and serialization failures, including `FluidStack` component round trips;
3. capability and block-storage failures;
4. recipes, loot, cauldrons, fuel, and event failures;
5. storage, Mob Bucket, automation, and protection failures.

A work unit may address several tests only when they share one demonstrated production cause.

### Verification

- Run `:neoforge:runGameTestServer --console=plain`.
- A complete successful server exit and a reported passing suite are required.
- Apply the three-attempt rule in `neoforge-1.21.1-port-process.md` to each bounded failure work
  unit.

### Test discipline

- Fix production code when a preserved assertion exposes a production defect.
- Update a test only for a demonstrated 1.21.1 or NeoForge API change.
- Do not add delays, broaden tolerances, swallow exceptions, or remove assertions to pass.
- Treat hangs, crashes before discovery, and zero discovered tests as failures.

## Stage 8 — Final package and reconciliation

### Work units

1. Re-run the passing `:common`, `:neoforge:compileJava`, `:neoforge:processResources`,
   `:neoforge:compileGametestJava`, and `:neoforge:runGameTestServer` gates if later changes could
   affect them, plus `:forge:compileJava` / `:fabric:compileJava` if any `common` change was made.
2. Run `:neoforge:build --console=plain`.
3. Verify the expected NeoForge artifact exists without inspecting or unarchiving it.
4. Reconcile `as-built.md`, `player-view.md`, and `build-env.md`: NeoForge is now an implemented
   runtime module, not scaffolding — update the repository map, subsystem-ownership rows, the
   cross-loader seam table, the `StoredFluid` conversion paragraph (add `NeoForgeFluidStacks`), the
   fuel row, the GameTest section, and `build-env.md`'s "scaffolding only" language.
5. Record any Forge or Fabric follow-up observed (there should be none).
6. Record any client-only manual smoke checks still recommended.

### Completion criteria

- Every completion gate passes.
- The snapshot says `complete`; the log contains every final passing command and outcome.
- Documentation describes the three-loader 1.21.1 implementation rather than port history.
- No Git or GitHub action has been performed.

## Expected manual smoke test after unattended completion

A later human client smoke test should check, on NeoForge:

- Big/Huge/Source fluid tint, including a variant-bearing modded fluid if available;
- milk and powder-snow overrides;
- Mob Bucket empty/filled model and spawn-egg colors;
- Junk Bucket protruding item order, tint, and glint;
- creative-tab contents and prefilled variants;
- tooltips, bars, use animations, and sounds;
- a dispenser acting inside an FTB Chunks claim as the `[SomeBuckets]` fake player.

Failure of this later smoke test opens a new bounded client work unit; it does not invalidate the
server and packaging evidence already recorded.
