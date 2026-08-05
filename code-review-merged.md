# Some Buckets — Merged Code Review

This document merges three independent static reviews. Repeated findings have been consolidated;
the underlying claims were not independently verified during the merge. No build or test run was
performed.

## Findings

### High h1 — Fluid pickup bypasses Minecraft's `BucketPickup` contract and can erase waterlogged blocks

`BBFluidLogic.tryTakeWithContext` and `SBFluidLogic.tryTakeWithContext` treat any source
`FluidState` as collectable and replace the containing block with air (`BBFluidLogic.java:62-83`,
`SBFluidLogic.java:101-110`). Minecraft normally delegates pickup to the target block's
`BucketPickup` behavior or corresponding Forge hook.

A waterlogged slab, stair, fence, or similar block exposes a source fluid state, so the current path
can delete the whole block without drops rather than clear its waterlogged property. It can also
drain blocks that deliberately refuse bucket pickup and skips block-specific state changes, sounds,
and fluid-stack data.

Use the block/Forge pickup contract, determine whether the returned content is acceptable before
committing the item mutation, and update the Some Buckets stack only after pickup succeeds. Add
regression coverage for both Big and Source Buckets using a waterlogged block and a custom
`BucketPickup` that refuses or customizes pickup.

### High h2 — `FillBucketEvent` handling does not preserve Forge semantics or consistently identify the mutation

`Protections.onBucketUse` treats `Event.Result.ALLOW` as a successful no-op and discards
`event.getFilledBucket()` (`Protections.java:47-64`). A Forge listener normally uses `ALLOW` with a
result stack to take ownership of the operation. The current behavior reports success while neither
the listener's result nor the mod's normal operation is applied.

Related dispatch and targeting issues were identified:

- A partial Big Bucket posts the event against `takeHit`; if pickup fails and placement falls
  through to `placeHit`, the actual mutation can occur at a different position
  (`BBItem.java:212-220`).
- `SBItem.useOn` duplicates take/place dispatch and can complete without calling
  `Protections.onBucketUse` (`SBItem.java:116-139`). Its event behavior therefore varies with the
  Minecraft interaction entry point.
- Fall-through placement can mutate the adjacent block even though the ordinary right-click-block
  event identifies the originally clicked block.

Choose explicit event semantics that can be honored by a multi-unit bucket. Either translate the
event result into bucket state, continue according to a deliberately documented policy, or use a
separate veto-only hook. Prefer one player world-use path that establishes feasibility and the exact
mutation target before posting the relevant hook. Add end-to-end tests for cancellation, `ALLOW`
with a supplied result, pickup-to-placement fallback, and `use` versus `useOn`.

### High h3 — Dispensers query the far side of sided fluid handlers

`Dispensers.execute` calculates the direction from the dispenser toward its target and uses that
same direction in its synthetic hit (`Dispensers.java:39-52,95`). The face of the target adjacent to
the dispenser is the opposite direction. Both fluid logic classes pass the synthetic hit direction
to `getCapability(ForgeCapabilities.FLUID_HANDLER, face)`.

Unsided tanks hide the error, but a sided machine can reject the interaction or expose a different
tank. Construct dispenser hits with the target's actual contacted face and propagate that face to
the associated checks. Add a GameTest using a deliberately sided fluid handler.

### Medium m1 — Fluid mutations omit vanilla game events

Pickup, placement, powder-snow, and cauldron paths change blocks and play sounds without emitting
the corresponding `GameEvent.FLUID_PICKUP` or `GameEvent.FLUID_PLACE` events. Representative paths
include `BBFluidLogic.java:76-91,215-223,246-256`, `SBFluidLogic.java:71-118`,
`FluidPlacement.java:91-116`, `Cauldrons.java:45-162`, and the direct dispenser-cauldron branches.

These events are observable by sculk sensors, vibration listeners, and other mods. Emit the correct
event at the position actually changed after a successful mutation, including after pickup through
the block-owned pickup contract.

### Medium m2 — Stored food can grow baby animals much faster than vanilla feeding

`JBItem.feedAnimal` passes `remaining / 10` to `AgeableMob.ageUp`
(`JBItem.java:224-226`). The stored age is in ticks, while `ageUp` accepts seconds and applies a
factor of 20. Vanilla's ten-percent growth advance corresponds to approximately `remaining / 200`,
not `remaining / 10`.

The current GameTest only asserts that age increases, so it does not check the vanilla-sized change.
Match vanilla's unit conversion and assert the resulting age, not only its direction.

### Medium m4 — Vanilla criteria and statistics are missing or attached to the wrong action

`SBItem.interactLivingEntity` triggers `CriteriaTriggers.CONSUME_ITEM` when assigning milk from a
cow (`SBItem.java:155-163`), although no item was consumed. Conversely, Big and Source milk-drinking
completion clears effects and awards item-use statistics without firing the consume-item criterion
(`BBItem.java:280-289`, `SBItem.java:178-190`).

The reviews also identified missing filled-bucket criteria for world pickup and incomplete cauldron
statistics: Big Bucket cauldron interactions omit the normal cauldron and item-use statistics, while
Source Bucket cauldron paths award `USE_CAULDRON` but not the item-use statistic.

Follow vanilla's timing and hooks so advancement datapacks and player statistics observe the actual
actions. Add end-to-end tests for milking, drinking, pickup, and cauldron use.

### Medium m5 — Source Bucket transfers perform avoidably many capability transactions

`Transfers.pump` can make up to 512 iterations, each with simulated and executed drains and fills
(`Transfers.java:38-39,153-181`). Because a Source Bucket exposes only 1,000 mB per drain, filling a
Huge Bucket takes 64 iterations and a large modded tank can reach the full ceiling. Each iteration
also recreates fluid stacks, reads Source NBT, and repeats policy work. The ceiling means a larger
destination is not filled "as much as the pair allows," and accumulating advertised tank capacity
in an `int` can overflow for handlers reporting multiple very large tanks.

For hand-to-hand transfer, special-case a known, allowed, assigned Source Bucket: simulate one bulk
fill against the destination and execute it once without draining the infinite source. Preserve the
public 1,000 mB capability behavior for machines if desired. Use a `long` or clamp when accumulating
external tank capacity, and add coverage for transaction counts and transfer bounds.

### Low l1 — The single-tank fluid handlers do not honor tank indices

`AbstractFluidHandler.getFluidInTank` and `isFluidValid` ignore their `tank` arguments, and Big and
Source capacity methods return tank 0's capacity for every index. The handlers report one tank, so
out-of-range indices should not alias tank 0.

Return empty, false, or zero for indices other than zero, or use Forge's standard validation
behavior. Add capability boundary coverage.

### Low l2 — Some modded-fluid presentation and feedback paths bypass stack-aware Forge APIs

`NbtFluidContainerModel` obtains the contained `FluidStack` but calls
`extensions.getStillTexture()` rather than `extensions.getStillTexture(contents)`
(`NbtFluidContainerModel.java:129-140`). Fluids whose still texture depends on stack NBT can
therefore select or recolor the wrong layer.

Pickup and tank-transfer feedback also hard-codes vanilla water/lava sounds in portions of
`BBFluidLogic` and `SBFluidLogic` rather than consulting the fluid type's Forge
`BUCKET_FILL`/`BUCKET_EMPTY` sound actions. Use the stack-aware texture and fluid sound APIs, as the
world-placement path already does.

### Low l5 — Defensive scaffolding and unused abstraction hide internal mistakes

The reviews identified several places where code controlled entirely by this mod is guarded as if
it were an unreliable external interface:

- `IFluidLogic` has no polymorphic consumer. Callers name the concrete Big or Source logic class,
  the interface forces irrelevant no-op methods, and the useful context-aware methods are not part
  of it. The stateless `getInstance()` singletons add ceremony without state or dispatch value.
- Big-Bucket-only cauldron, dispenser, and fluid paths silently invent 2-unit or 2,000 mB fallback
  capacities when the stack is not a `BBItem`; `BBFluidHandler` returns zero capacity. These are
  mod-owned registrations and calls, so incorrect wiring should fail visibly.
- `FluidColorHelper` catches every `Throwable`, suppressing linkage, assertion, VM, and programming
  failures. `BucketMouth` and `ClientFluidColors` also swallow broad runtime exceptions. Catch only
  genuinely recoverable external resource failures.
- Several no-context convenience overloads were reported unused, including
  `FluidPlacement.emptyContents(Level, Player, ...)` and unowned Mob Bucket capture/water/release
  overloads.
- The mutable `CopyOnWriteArrayList` claim-provider registry and closeable registration token mainly
  support temporary test providers. If runtime integrations are fixed during setup, the provider
  seam can remain without runtime mutation and copy-on-write machinery.

Remove `IFluidLogic` and use static logic utilities or direct concrete instances unless real
polymorphism emerges. Delete unused overloads, use direct casts or known item values at internally
controlled call sites, and narrow exception recovery at external resource boundaries.

The shared NBT utility, concrete fluid handlers, `FluidPlacement`, transfer settlement, and claim
provider boundary were considered justified by actual shared contracts. A broad strategy framework
for player, dispenser, and cauldron behavior would add abstraction without simplifying their
deliberate differences.

### Low l6 — GameTests and their fixture are included in production source sets

GameTest classes live under `src/main/java`, and the generated test structure is added to
`sourceSets.main.resources` (`build.gradle:117-132`). Unless separately excluded during packaging,
the release jar includes the test suite and fixture.

Use a dedicated GameTest source set or exclude test classes and structures from the production jar
while retaining the development run configuration.

### Low l7 — Release metadata and build files retain MDK scaffolding

`mods.toml` remains mostly Forge template text, the displayed description is only "Get you some
buckets!", and `build.gradle` retains unused template guidance and setup. ForgeGradle and Librarian
also use the nonreproducible version selectors `[6.0,6.2)` and `1.+`. This makes the unreleased
project visibly unfinished and adds avoidable build noise.

Before release, replace the metadata with a useful description and appropriate support/home links,
remove unused template commentary and publishing/Eclipse setup, pin both build plugins, and add a
logo if desired.

## Overall assessment

The core architecture was judged broadly conventional for a Forge 1.20.1 item-only mod: deferred
registration, item fluid capabilities, cauldron interaction maps, registered dispenser behaviors,
server-side mutations, datapack recipes and tags, resource-pack models, server configuration, and a
stable dispenser fake player are expected Minecraft/Forge mechanisms. Persistent state is bounded
and centralized; capability transfers are normally simulated before execution; Mob Bucket state is
removed only after successful entity insertion; and unrelated item NBT is preserved.

The material data-loss issue is direct source-block removal, and the principal Forge integration
issue is the nonstandard `FillBucketEvent` behavior. The dispenser face error affects sided machine
compatibility. No normal-path item duplication problem was reported in transfer settlement or
crafting remainders.

The design is generally server-friendly: it adds no tick handlers, custom networking, block
entities, or saved-world scans; collection searches are local and use is action-triggered; and
stored entry counts are bounded. The main efficiency concern is Source transfer iteration.

The existing Source allowlist, Mob blacklist tag, datapack recipes, resource-pack models, and named
fake player provide the admin controls expected for this scope. A command layer or additional saved
admin state was not considered warranted. Standard advancement/stat hooks are the notable
admin-facing interoperability gap.

## Suggested order of work

1. Correct source pickup through the Minecraft/Forge pickup contract.
2. Define and implement valid `FillBucketEvent` semantics and exact targeting.
3. Correct dispenser sided-face selection.
4. Fix baby aging, criteria/statistics, and fluid game events.
5. Reduce Source transfer work.
6. Address capability indices, modded-fluid APIs, internal simplification, test packaging, and
   release metadata.
