# Forge 1.21.1 Port Plan

## Objective

Produce a Forge 52.1.16 artifact for Minecraft 1.21.1 that compiles, packages, starts under the
Forge GameTest server, and passes the ported automated test suite while preserving the behavior and
invariants documented in `player-view.md` and `as-built.md`.

The plan is Forge-first. Common code is in scope where Forge production or test code depends on it.
Fabric/Quilt and NeoForge repair are not required for completion of this plan.

## Completion gates

The Forge port is complete only when all of these commands succeed in the current build environment:

1. `./gradlew.bat :common:compileJava --console=plain`
2. `./gradlew.bat :forge:compileJava --console=plain`
3. `./gradlew.bat :forge:processResources --console=plain`
4. `./gradlew.bat :forge:compileGametestJava --console=plain`
5. `./gradlew.bat :forge:runGameTestServer --console=plain`
6. `./gradlew.bat :forge:build --console=plain`

On Windows PowerShell, execution may use `.\gradlew.bat` for the same commands.

The final handoff must also identify any client presentation that remains suitable only for a manual
client smoke test. A manual visual test is recommended but is not an unattended completion gate.

## Session discipline

Execute one stage per session. Begin each session by reading `CLAUDE.md`, this plan,
`forge-1.21.1-port-process.md`, and `forge-1.21.1-port-status.md`; the assessment is reference
material consulted by section. End the session when the stage's primary gate passes and leave the
handoff in the snapshot. `forge-1.21.1-port-process.md` governs how state is split between the
overwritten `forge-1.21.1-port-status.md` snapshot and the append-only `forge-1.21.1-port-log.md`:
the snapshot is small and rewritten in place, the log is write-only and never read during
execution, and verbose command output is reduced to a count and a delta before it is recorded.

## Stage summary

| Stage | Work product | Primary gate |
| --- | --- | --- |
| 0 | Baseline diagnostics and persistent status | One diagnostic Forge compile |
| 1 | Component-backed common item state | Focused inspection and diagnostic common compile |
| 2 | Minecraft 1.21.1 common API port | Passing common production compile |
| 3 | Forge bootstrap and content registration | Diagnostic Forge production compile |
| 4 | Forge capabilities and fluid transfer core | Diagnostic Forge production compile |
| 5 | Forge server systems and data | Diagnostic Forge compile plus resources |
| 6 | Forge client models and presentation | Passing Forge production compile |
| 7 | Forge GameTest source and resource port | Passing GameTest compile |
| 8 | Runtime GameTest stabilization | Passing Forge GameTest server |
| 9 | Final package and documentation reconciliation | Passing Forge build |

Until Stage 6, a diagnostic module compile may still fail in a known later-stage subsystem. A stage
can finish only when its own failures are removed and every remaining compiler failure is recorded
against a later stage. A passing compile is mandatory wherever the table explicitly says passing.

Run the diagnostic compile once per stage, after the stage's edits are complete — not once per
substage or per file. Substages organize the implementation work; they are not separate
verification points.

## Stage 0 — Baseline diagnostics

### Scope

- Read `CLAUDE.md`, `forge-1.21.1-port-plan.md`, `forge-1.21.1-port-process.md`, and
  `forge-1.21.1-port-status.md`; the assessment is reference material consulted by section.
- Confirm that Gradle sync remains successful; do not alter established dependency versions.
- Run one baseline `:forge:compileJava` diagnostic.
- Classify compiler failures by the stages below.
- Record the outcome and major error groups as one log entry; record the first bounded work unit in
  the snapshot.

### Constraints

- The baseline diagnostic is discovery, not permission for opportunistic edits.
- Do not run `clean`, refresh dependencies, inspect caches, or change the build environment.
- Do not repair Fabric failures encountered incidentally.

### Completion criteria

- The snapshot contains a reproducible baseline and a finite Stage 1 work unit; the log contains the
  baseline diagnostic entry.
- No code has been changed solely in response to unclassified errors.

## Stage 1 — Component-backed common item state

### Stage 1A: simple state boundary

Port `NBTUtil` operations that do not serialize nested item stacks:

- read and write the built-in custom-data component;
- preserve unrelated custom data during mutation;
- preserve the existing Mode model;
- port finite fluid, milk, powder snow, Mob Bucket snapshots, and layout seed;
- canonicalize empty state by removing empty payload keys and an empty component;
- return detached copies where callers may mutate returned compounds;
- update comments and names so they describe the current component-backed design.

Update direct users only as required by the new state boundary. Do not distribute persistence logic
through item classes.

### Stage 1B: nested Junk/Trash stacks

- Select the smallest explicit `HolderLookup.Provider` plumbing that supports current item-stack
  codecs.
- Update `getStoredItems` and `setStoredItems` or their replacements.
- Thread registry context through Junk/Trash gameplay, settlement, dispenser, rendering, and test
  call sites that encode or decode stored stacks.
- Keep simple count, mode, fluid, and entity reads context-free when the API permits.
- Preserve FIFO order, item components, stack count, glint, and variant data.

### Tests to port or add

- State scenarios for every Mode and canonical empty state.
- Junk/Trash round-trip scenarios containing a nontrivial component-bearing stack.
- Stored-fluid variant round trip.
- Mutator preservation of unrelated custom data.

Tests may be edited now but do not need to compile until Stage 7 if their own Minecraft test API has
not yet been ported.

### Verification

- Inspect every remaining raw stack-tag call under `common/src/main/java` with `rg`.
- Run one diagnostic `:common:compileJava` at the end of Stage 1, after both substages are
  implemented — not once per substage.
- Stage 1 may leave only errors assigned to Stage 2 common API changes.

### Stop conditions specific to this stage

- The built-in custom-data component cannot express the required state without loss.
- Registry context would require a global singleton or a Forge type in common production code.
- A proposed fix changes stored-item capacity, order, equality, or settlement behavior.

## Stage 2 — Minecraft 1.21.1 common API port

### Stage 2A: mechanical identifiers and signatures

- Replace `ResourceLocation` constructors with the correct factories.
- Port tooltip signatures to `Item.TooltipContext`.
- Port item use-duration signatures.
- Replace tag-based stack equality with component-aware equality.
- Update annotations and imports only where the target API requires it.

### Stage 2B: interactions, registries, and entities

- Port holder/resource-key changes used by common code.
- Port game-event calls without changing when events fire.
- Port Mob Bucket entity type lookup, capture serialization, entity restoration, and release.
- Port spawn-egg and bucketable interactions used by common behavior.
- Port any changed interaction-result signatures in common interfaces and item implementations.
- Keep server mutation authoritative and retain existing client prediction behavior.

### Stage 2C: shared client utility compile surface

- Port common baked-model wrapper and texture/color utility signatures.
- Keep client initialization out of server-safe common classes.
- Do not redesign Forge rendering here; Stage 6 owns loader client behavior.

### Verification

- `:common:compileJava` must pass.
- Search common production code for the removed APIs identified in the assessment.
- Re-read changed item methods against their 1.20.1 behavior and `player-view.md`.

### Completion criteria

- Common production source compiles for Minecraft 1.21.1.
- Any Fabric break caused by changed common interfaces is recorded but not repaired.

## Stage 3 — Forge bootstrap and registration

### Work units

1. Convert `SomeBucketsForge` to constructor-injected loading context.
2. Register configuration through the current context API.
3. Retain initialization order for `AutomationPlayers`, `BucketOperations`, registries, setup work,
   and config refresh.
4. Remove the Forge FTB Chunks import, loaded-mod check, and registration call.
5. Port item, sound, creative-tab, and global registry declarations as required.
6. Confirm Forge metadata still matches the entry point, versions, and GameTest production
   dependency.

### Verification

- Run `:forge:compileJava` diagnostically once, after all Stage 3 work units are implemented.
- Remaining errors must belong to Stages 4–6.
- Inspect the entry point for client-only imports and ordering regressions.

### Completion criteria

- Forge initializes its common services and content through current Forge lifecycle APIs.
- No Forge production source refers to FTB Chunks.

## Stage 4 — Forge capabilities and fluid transfer core

### Stage 4A: item capability attachment

- Replace obsolete item capability initialization with the Forge 52 item provider hook.
- Adapt `FluidProvider` only as required by the new provider lifetime and query API.
- Keep bucket state in common component data.
- Ensure capability simulation remains non-mutating.

### Stage 4B: fluid value conversion and handlers

- Port `ForgeFluidStacks` while preserving optional variant data.
- Port `AbstractFluidHandler`, `BBFluidHandler`, and `SBFluidHandler` signatures.
- Preserve one-tank semantics, capacity, fill/drain rules, and owning-container results.

### Stage 4C: held and block transfers

- Port capability discovery on held items and block entities.
- Port `FluidUtil`, fluid placement, pickup wrappers, sound actions, and event hooks as required.
- Preserve preview-before-authorization and exact-target protection checks.
- Preserve multi-count foreign-container settlement and Source Bucket infinity.

### Tests to port or add

- Capability presence on Big, Huge, and Source Buckets.
- Finite fill/drain simulation and execution.
- Source Bucket assignment and infinite transfer.
- Variant-data preservation.
- Block capability transfer and multi-count held settlement.

### Verification

- Run `:forge:compileJava` diagnostically once at the end of Stage 4, after all substages are
  implemented — not once per substage.
- A compile failure in ingredients, loot, cauldrons, or client code may remain for later stages.
- Review every mutation path for simulation safety.

### Stop conditions specific to this stage

- Current Forge capabilities require a different persistence owner.
- Legal multi-count settlement cannot be preserved through the current common seam.
- A fix would replace Forge capabilities with a private inventory convention.

## Stage 5 — Forge server systems and data

### Stage 5A: custom ingredients and recipes

- Port both ingredient classes to Forge 52's codec and registry-aware buffer model.
- Preserve ingredient ids and exact match behavior.
- Update the six recipe files to current directories and result stack format.
- Remove obsolete dual-loader JSON fields only when they are invalid for the Forge target; do not
  repair Fabric during this stage.

### Stage 5B: loot modifiers

- Convert the modifier and deferred register to the required map-codec types.
- Preserve conditions, target loot tables, chance, item, and optional powder units.
- Keep generation sourced from `bucket_loot.json`.
- Verify generated resources appear under the expected Forge locations.

### Stage 5C: interactions and automation

- Port cauldron result types and maps.
- Port dispenser behavior registration and execution.
- Port held-transfer player events and fill-bucket hooks.
- Port fake-player use and protection calls without adding a Forge FTB adapter.
- Port furnace fuel handling and remainders.

### Stage 5D: resources and metadata

- Move vanilla data directories to their 1.21 names.
- Update `pack.mcmeta` files for production and Forge GameTests.
- Check item model JSON, sounds, tags, language, and generated loot JSON for current syntax.

### Tests to port or add

- Recipe matching rejects filled Some Buckets inputs.
- Spawn-egg ingredient matches appropriate eggs.
- Loot generation and resource policy agree.
- Cauldron, dispenser, fill-bucket event, protection, and fuel scenarios retain their assertions.

### Verification

- `:forge:processResources` must pass.
- Parse or inspect every changed JSON resource deterministically.
- Run `:forge:compileJava` diagnostically; only Stage 6 client errors may remain.

### Completion criteria

- Forge server production code and data have no known compile errors.
- Generated loot policy remains derived from the common manifest.

## Stage 6 — Forge client models and presentation

### Stage 6A: standard fluid model decision

- Check current Forge documentation and compiler-visible APIs for the standard dynamic fluid model.
- Determine whether the existing stack-aware item color handler supplies variant tint correctly.
- If equivalent, switch bucket model JSON to the Forge loader and remove only the now-redundant
  custom geometry-loader registration and implementation.
- Otherwise, port `NbtFluidContainerModel` to the current geometry baking API.

### Stage 6B: item properties and colors

- Port model predicate registration.
- Port fluid, Mob Bucket, and Trash Bucket item color registration.
- Preserve milk, powder snow, empty, and filled visual selection.
- Port reload listener registration and color-cache invalidation.

### Stage 6C: Junk Bucket rendering

- Port baked-model replacement and `ModelResourceLocation` construction.
- Port `JBModel`, `JBRenderer`, quad transformation, render-pass, and BEWLR APIs.
- Preserve FIFO visual order, tint, glint, cover geometry, and reload behavior.
- Keep all client classes safe from dedicated-server classloading.

### Verification

- `:forge:compileJava` must pass.
- Re-run `:common:compileJava` if common client interfaces changed.
- Inspect client event subscribers for physical-side restrictions.
- Presentation GameTests are ported in Stage 7; a later manual client smoke test remains advisable.

### Stop conditions specific to this stage

- Replacing the custom fluid model cannot be shown to preserve variant tint semantics.
- A rendering fix requires changing textures, art, or documented presentation behavior.
- The only proposed solution introduces client classloading on a dedicated server.

## Stage 7 — Forge GameTest port

### Stage 7A: test infrastructure

- Port the Forge GameTest support mod and discovery annotations.
- Port shared structure-template references and helper APIs.
- Update GameTest `mods.toml` and `pack.mcmeta` only as required.
- Keep production-resource lookup anchored correctly across the separate GameTest module.

### Stage 7B: shared scenarios

Port shared scenarios in coherent groups:

1. state and presentation;
2. recipes, loot, and fuel;
3. Big/Huge and Source Bucket behavior;
4. Junk/Trash and Mob Bucket behavior;
5. automation and protection.

### Stage 7C: Forge-specific tests

Port capability, transfer, cauldron, fill-bucket event, Forge-only fluid, fuel, loot, and protection
tests. Preserve assertions unless the plan explicitly records a Minecraft 1.21.1 semantic change.

### Verification

- `:forge:compileGametestJava` must pass.
- `:forge:processGametestResources` must pass if exposed as a separate task.
- Inspect discovery coverage so a passing run cannot result from undiscovered tests.

### Completion criteria

- All existing intended Forge tests compile and remain discoverable.
- No test has been disabled, deleted, or weakened to bypass a production failure.

## Stage 8 — Runtime GameTest stabilization

### Work units

Run the complete Forge GameTest server and address failures in bounded subsystem groups:

1. server bootstrap, registry, resource, or discovery failures;
2. state and serialization failures;
3. fluid capability and transfer failures;
4. recipes, loot, cauldrons, fuel, and event failures;
5. storage, Mob Bucket, automation, and protection failures.

A work unit may address several tests only when they share one demonstrated production cause.

### Verification

- Run `:forge:runGameTestServer --console=plain`.
- A complete successful server exit and reported passing suite are required.
- Apply the three-attempt rule in `forge-1.21.1-port-process.md` to each bounded failure work unit.

### Test discipline

- Fix production code when a preserved assertion exposes a production defect.
- Update a test only for a demonstrated 1.21.1 test API or intentional semantic change.
- Do not add delays, broaden tolerances, swallow exceptions, or remove assertions merely to pass.
- Treat hangs, crashes before discovery, and zero discovered tests as failures.

## Stage 9 — Final package and reconciliation

### Work units

1. Re-run the passing common compile, Forge compile, resource processing, GameTest compile, and
   GameTest server gates if later changes could affect them.
2. Run `:forge:build --console=plain`.
3. Verify the expected Forge artifact exists without inspecting or unarchiving it.
4. Reconcile `as-built.md`, `player-view.md`, and `build-env.md` with the completed Forge state.
5. Record Fabric/Quilt and NeoForge breakage or remaining work without attempting it.
6. Record any client-only manual smoke checks still recommended.

### Completion criteria

- Every completion gate passes.
- The snapshot says `complete`; the log contains every final passing command and outcome.
- Documentation describes the current 1.21.1 Forge implementation rather than the port history.
- No Git or GitHub action has been performed.

## Expected manual smoke test after unattended completion

The automated port can complete without launching an interactive client, but a later human client
smoke test should check:

- Big/Huge/Source fluid tint, including a variant-bearing modded fluid if available;
- milk and powder-snow overrides;
- Mob Bucket empty/filled model and spawn-egg colors;
- Junk Bucket protruding item order, tint, and glint;
- creative-tab contents and prefilled variants;
- tooltips, bars, use animations, and sounds.

Failure of this later smoke test opens a new bounded client work unit; it does not invalidate the
server and packaging evidence already recorded.
