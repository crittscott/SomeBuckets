# High-Level Unified Code-Review Plan

The unified todo should be organized by coupled subsystem and dependency, not by the seven review
files or their individual priority labels. Repeated findings should become one canonical task, with
the reviews retained as supporting evidence.

The player guide and as-built description define the behavioral baseline: unless a task explicitly
corrects Forge/Minecraft incompatibility, player-visible behavior remains unchanged.

## Architectural stance

- `BBItem`, `JBItem`, `MBItem`, `SBItem`, and `TBItem` remain the principal landmarks.
- New bucket-specific names use the abbreviations: `BB...`, `JB...`, `MB...`, `SB...`, `TB...`.
- Prefer broad, coherent existing owners over many narrowly named service classes.
- Extract shared code only when it represents an actual transaction or invariant, not merely
  similar-looking control flow.
- Avoid inheritance between BB and SB merely to share gesture routing.
- Tests, constants, documentation, and dead-code removal belong to the task that changes their
  owning subsystem.

This modifies two recommendations from the organization review:

- Do not create three top-level `BigBucketDispenser`, `SourceBucketDispenser`, and
  `MobBucketDispenser` classes. Keep `Dispensers` as the visible automation subsystem, divided
  internally into well-named BB/SB/MB/JB/TB sections or private nested behaviors.
- Do not automatically split `NBTUtil` into three top-level classes. Keep one persistence landmark,
  but remove gameplay policy from it and organize its APIs clearly by BB/SB, JB/TB, and MB schema.

Defer extracting the common BB/SB `use` prelude. A small amount of obvious duplication is preferable
to a generic abstraction full of policy switches.

## Proposed work packages

### 0. Resolve architectural and compatibility decisions

These decisions affect several later tasks and should be explicit todo entries rather than buried in
implementation work.

1. Decide the proper `FillBucketEvent.ALLOW` contract for multi-unit buckets.
   - Either support compatible listener-supplied results, or stop presenting a cancellation-only hook
     as full Forge event parity.
   - This decision affects BB/SB world use, powder snow, and aquatic MB release.
2. Decide whether transfer overflow is:
   - an internally protected `ENTITY_RELEASE`; or
   - ordinary player item dropping that should not use the mod's claim layer.

   The policy must then be consistent with JB/TB ejection.
3. Adopt the target ownership map below.

| Owner | Responsibility |
| --- | --- |
| `BBItem` / `SBItem` | Gesture routing and bucket-specific player policy |
| `BBFluidLogic` / `SBFluidLogic` | Finite/infinite dispatch and SB allowlist policy |
| `FluidPickup` / `FluidPlacement` | Vanilla-style world fluid mutations |
| `Cauldrons` | All physical cauldron transitions; callers retain selection policy |
| `Transfers` or renamed `FluidTransfers` | Item/block capability transactions, pumping, settlement, and shared fluid feedback |
| `NBTUtil` | Serialization and normalization only |
| `Dispensers` | All automation routing, internally divided by bucket family |
| `protection` package | All protection contexts, checks, providers, and fake-player behavior |

### 1. Make interaction and protection boundaries reliable

This should precede broad refactoring because it establishes the mutation contracts everything else
will use.

- Move the offhand transfer event handler late enough that earlier protection listeners can veto
  before mutation.
- Require non-null `ProtectionContext` for JB/TB absorption and feeding.
- Remove nullable-player-to-unowned-automation conversion.
- Pass the actual `InteractionHand`; do not infer it and fall back to the main hand.
- Make malformed player protection contexts fail rather than become automation.
- Implement the chosen transfer-overflow permission policy before either hand is mutated.
- Move `Protections` into the `protection` package without creating another facade.
- Add the cancellation-ordering and overflow-denial coverage identified by the protection review.

### 2. Restore native Minecraft/Forge behavior at the two type-changing edges

These are the most important behavioral corrections.

- Rework BB powder-snow placement as block placement:
  - Forge place-event compatibility;
  - `PLACED_BLOCK`;
  - `BLOCK_PLACE`;
  - custom debit of one stored unit only after successful placement.
- Route aquatic MB water placement through the existing vanilla-style fluid-placement owner:
  - ultra-warm evaporation;
  - resolved-target protection;
  - the decided bucket-event contract;
  - correct water sound and game event.
- Award `ITEM_USED` for successful player MB release.
- Fire `FILLED_BUCKET` after successful player MB capture, but not dispenser capture.
- Add the exact event, criterion, statistic, cancellation, and ultra-warm tests from the reinvention
  review.

### 3. Converge the fluid transaction subsystem

This is where the organization, duplication, dead-code, constants, and reinvention reviews overlap
most heavily.

- Replace every semantic `1000` with `FluidType.BUCKET_VOLUME`.
- Give finite draining one mutation implementation; make `BBFluidHandler` use it and remove redundant
  normalization.
- Expand the existing transfer owner rather than creating separate `BlockFluidTransfers` and
  `FluidSounds` classes:
  - block-capability discovery;
  - simulate/authorize/execute ordering;
  - all-or-nothing one-unit block transactions;
  - fluid-specific fill/empty sound resolution;
  - fluid game events;
  - player statistics.
- Use Forge's finite handler-transfer facility where its contract matches; retain the custom
  unlimited SB pump and stack settlement.
- Make the mod's own BB/SB item capability fail fast while retaining optional handling for foreign
  capabilities.
- Remove the now-unused SB item-handler lookup/argument as part of this refactor.
- Keep `FluidPickup` and `FluidPlacement`; the reviews identify them as appropriately scoped existing
  owners.

### 4. Give cauldron transitions one owner

Keep `Cauldrons` rather than adding `CauldronTransfers`.

- Move all supported physical transitions into `Cauldrons`.
- Let BB player use, SB player use, and dispenser use retain their different eligibility and ordering
  policies.
- Centralize:
  - protection;
  - state mutation;
  - sound;
  - game event;
  - optional player statistics and criteria.
- Replace cauldron literals with `LayeredCauldronBlock.MAX_FILL_LEVEL` and `Block.UPDATE_ALL`.
- Preserve separate player and dispenser selection rules in tests.

### 5. Clean up persistent storage without multiplying classes

Keep `NBTUtil`, but make its responsibility truthful.

- Organize it into clearly separated BB/SB, JB/TB, and MB sections.
- Move MB admission policy and capacity out to the MB owner.
- Move crafting-remainder behavior out of generic persistence code if practical.
- Centralize `MBItem.MAX_MOBS`.
- Make TB merge-or-replace one operation used by cursor, slot, and entity intake.
- Remove redundant tag-null checks, clamps, and misleading recovery branches where internal contracts
  already guarantee valid state.
- Correct the false "all bucket contents" and "empty bucket" documentation.

### 6. Reorganize automation and client wiring

- Retain one visible `Dispensers` subsystem.
- Replace the large implicit type cascade with explicit BB/SB/MB/JB/TB internal handlers or private
  nested behaviors.
- Carry `ServerLevel` through dispenser-only code and remove impossible client branches.
- Keep the adjacent-face calculation and dispenser protection context centralized.
- Consolidate client lifecycle wiring into a short owner such as `ClientSetup`; do not proliferate
  bucket-specific presentation classes.

### 7. Presentation constants and resource protocols

This is lower risk and should follow the structural work.

- Derive dynamic translation keys from description IDs rather than capacity.
- Centralize Java-side model predicate IDs and named predicate values.
- Add focused Java/resource agreement tests.
- Use `SomeBuckets.MODID` throughout Java.
- Name MB capacity, fuel duration, drinking duration, pickup radius, item-bar geometry/colors,
  cauldron values, and rendering scale where useful.
- Do not create a new class solely to hold two or three cosmetic constants; local constants are
  acceptable.

### 8. Documentation and final cleanup

Documentation should follow the refactors so it describes the surviving architecture.

- Fully document the protection SPI and registration lifetime.
- Document `AbstractFluidHandler` hooks.
- Document public mutation methods transactionally: protection ownership, server mutation, failure
  atomicity, feedback, and return meaning.
- Add short class contracts to the five item classes and major subsystem owners.
- Correct MB release/capture and TB lookup contracts.
- Convert private algorithm Javadocs into ordinary implementation comments.
- Remove the unused GameTest helpers, the SB forwarding method, and any dead parameters left after
  consolidation.
- Update `as-built.md` and `player-view.md` only where deliberately corrected behavior is observable.

## Shape of each final todo entry

Each canonical item should contain:

- **Outcome**
- **Behavior preserved or intentionally corrected**
- **Findings absorbed**, with links to the relevant review sections
- **Owning class or subsystem**
- **Implementation direction**
- **Dependencies**
- **Required coverage**
- **Documentation affected**
- **Completion conditions**

This structure prevents "replace magic numbers," "remove duplication," and "add Javadocs" from
becoming disconnected global passes. For example, the block-capability task would absorb its
duplicated transactions, `1000` literals, sound drift, missing game events, capability overguards,
dead SB argument, documentation, and tests in one coherent change.
