# Some Buckets — Merged Code Review

This document merges three independent static reviews. Repeated findings have been consolidated;
the underlying claims were not independently verified during the merge. No build or test run was
performed.

## Findings

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

The principal Forge integration issue is the nonstandard `FillBucketEvent` behavior. The dispenser
face error affects sided machine compatibility. No normal-path item duplication problem was reported in transfer settlement or
crafting remainders.

The design is generally server-friendly: it adds no tick handlers, custom networking, block
entities, or saved-world scans; collection searches are local and use is action-triggered; and
stored entry counts are bounded. The main efficiency concern is Source transfer iteration.

The existing Source allowlist, Mob blacklist tag, datapack recipes, resource-pack models, and named
fake player provide the admin controls expected for this scope. A command layer or additional saved
admin state was not considered warranted. Standard advancement/stat hooks are the notable
admin-facing interoperability gap.

## Suggested order of work

1. Define and implement valid `FillBucketEvent` semantics and exact targeting.
2. Correct dispenser sided-face selection.
3. Fix baby aging and criteria/statistics.
4. Reduce Source transfer work.
5. Address capability indices, modded-fluid APIs, internal simplification, test packaging, and
   release metadata.
