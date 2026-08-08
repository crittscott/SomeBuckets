# Unified Code-Review Todo

This is the executable task list derived from the seven `code-review-*.md` reports,
`high-level-plan.md`, `player-view.md`, and `as-built.md`. The review files are evidence; this file is
the canonical work queue.

The mod is feature complete and working. Preserve documented behavior unless a task explicitly
corrects a Minecraft/Forge compatibility defect. Do not add features while completing this list.

## How to use this list

- Check a leaf item only after its code and associated test source or documentation work is complete.
- Check a parent task only after all of its children are checked.
- Work in task-ID order unless a dependency explicitly permits otherwise.
- When stopping mid-task, add a short `IN PROGRESS:` note beneath that task naming the next leaf.
- Treat `BB`, `JB`, `MB`, `SB`, and `TB` as the required bucket-family names. Do not introduce
  `BigBucket...`, `JunkBucket...`, `MobBucket...`, `SourceBucket...`, or `TrashBucket...` class names.
- Prefer existing, broad owners over new top-level classes. A new class needs a coherent domain that
  cannot reasonably live in an existing owner.
- Do not build or run tests as part of these tasks unless the user explicitly asks. Add and inspect
  test source, then tell the user which build or GameTest run is warranted.

## Target ownership

| Owner | Responsibility |
| --- | --- |
| `BBItem` / `SBItem` | Player gesture routing and bucket-specific policy |
| `BBFluidLogic` / `SBFluidLogic` | Finite/infinite dispatch and SB allowlist policy |
| `FluidPickup` / `FluidPlacement` | Vanilla-style world fluid pickup and placement |
| `Cauldrons` | Physical cauldron transitions and their observable effects |
| `Transfers` | Item/block capability transactions, pumping, settlement, and shared fluid feedback |
| `NBTUtil` | Serialization, deserialization, and normalization only |
| `Dispensers` | All dispenser automation, internally separated by bucket family |
| `protection` package | Contexts, checks, providers, and dispenser identity |
| `ClientSetup` | Client lifecycle and presentation registration |

Do not create separate top-level cauldron-transfer, block-fluid-transfer, fluid-sound, or per-bucket
dispenser service classes. Keep the small BB/SB `use` prelude duplicated unless later work makes the
shared contract substantially larger and still semantically identical.

---

## T00 — Record the two compatibility decisions

These decisions gate T10, T20, and T30. Record the selected policy directly under each decision before
implementing dependent work.

- [x] **T00-A — Decide the `FillBucketEvent.ALLOW` contract.**

  **Decision — honor compatible `ALLOW` results and reject incompatible results.** A compatible
  survival result is one nonempty stack of the exact input item: the listener may replace the
  bucket's NBT state, but may not replace its item/tier or create an illegal stack. Creative-mode
  `ALLOW` retains the original bucket, matching Forge's helper. Cancellation fails; `DEFAULT` and a
  non-canceling `DENY` continue normal BB/SB dispatch; compatible `ALLOW` returns the supplied bucket
  and skips dispatch; incompatible `ALLOW` fails before mod-owned bucket or world mutation.

  BB/SB player pickup and placement retain this event at the exact resolved target. BB powder-snow
  pickup retains it, but T20-A removes it from powder-snow output when that path becomes native block
  placement. Aquatic MB water placement does not post it: MB release cannot honor the event's held
  empty-bucket replacement contract and instead uses its ordinary interaction hooks, the shared
  native-style placement transaction, and the mod's protection layer.

  - [x] Determine which listener-supplied results can be reconciled with a multi-unit NBT container
    without replacing or losing the existing bucket.
  - [x] Choose and record one contract:
    - honor compatible `ALLOW` results and reject incompatible results without mutation; or
    - stop posting `FillBucketEvent` on operations that cannot honor its result contract and use only
      the applicable ordinary interaction/protection hooks there.
  - [x] Define the treatment of `DEFAULT`, cancellation, `DENY`, compatible `ALLOW`, and incompatible
    `ALLOW` explicitly.
  - [x] Identify every affected entry point: BB/SB pickup and placement, BB powder-snow placement, and
    aquatic MB water placement.
  - [x] Replace the existing test that merely locks in ignored `ALLOW` behavior with tests for the
    selected contract, including an unsupported pickup for which a listener supplies a result.
  - [x] Update `as-built.md` so it describes the selected event contract without claiming parity that
    the implementation does not provide.

- [x] **T00-B — Decide the protection policy for transfer settlement drops.**

  **Decision — settlement overflow is ordinary player inventory dropping.** `Transfers.settle`
  continues to use `Player.drop` and does not call the internal claim-provider facade. This differs
  intentionally from JB/TB air, block-targeted, and dispenser ejection: those gestures explicitly
  release stored contents into the world, while a transfer drop is incidental fallout from rebuilding
  legal held stacks after a player inventory transaction. Acceptance coverage uses an assigned Source
  Bucket and a stack of sixteen empty buckets: one filled bucket remains in hand and fifteen untouched
  buckets drop even while a registered provider would reject `ENTITY_RELEASE`.

  - [x] Choose and record whether overflow created by `Transfers.settle` is a protected
    `ENTITY_RELEASE` or ordinary player inventory dropping outside the internal claim-provider layer.
  - [x] Compare the decision with JB/TB air ejection, block-targeted ejection, and dispenser ejection;
    record why any difference is intentional.
  - [x] If protected, require authorization at `player.blockPosition()` with a representative
    `ItemEntity` before either held stack is mutated. Not applicable under the selected policy.
  - [x] If treated as ordinary dropping, update the protection documentation so it does not imply that
    every custom item-entity creation passes through `Protections.mayAct`.
  - [x] Define acceptance coverage for a stacked-container transfer that must drop at least one result.

---

## T10 — Make protection and interaction boundaries explicit

Depends on T00-B for settlement behavior. Sources: protection findings 1–2; guarding findings 1–3;
organization finding 7.

T00 verification (2026-08-07): user reports all tests pass and the changed behavior works in-game.

- [x] **T10-A — Make storage mutation contexts mandatory.**

  Audit result: every production caller already passed `ProtectionContext.player(...)` or
  `ProtectionContext.dispenser(...)`, and the direct protection GameTests already passed an explicit
  dispenser context. No storage test used null to mean unattributed automation, so no
  `unownedAutomation()` substitution was needed. The nullable feeding actor remains independent and
  deliberately selects the stable dispenser fake player.

  - [x] Change `JBItem.absorbItemEntities`, `JBItem.absorbItemEntity`, `JBItem.feedAnimal`, and the TB
    overrides so `ProtectionContext` is required.
  - [x] Remove every `context != null` branch that currently permits mutation without authorization.
  - [x] Update all player callers to pass a player context and all dispenser callers to pass a
    dispenser context.
  - [x] Update GameTests that intentionally model unattributed automation to pass
    `ProtectionContext.unownedAutomation()` explicitly.
    No affected GameTest used that call shape.
  - [x] Keep the feeding actor separate from authorization: a nullable feeder may still deliberately
    select the dispenser fake player, but it must not disable the permission check.
  - [x] Inspect each changed call path and confirm that denial leaves the bucket, target entity, and
    world unchanged.

- [x] **T10-B — Remove implicit conversion from a missing player to automation.**

  The player convenience overloads now construct only player contexts. Direct BB/SB fluid GameTests
  call the context overloads with `ProtectionContext.unownedAutomation()` explicitly; no production
  call site creates an unowned context.

  - [x] Make the player convenience overloads in `BBFluidLogic` and `SBFluidLogic` require a real
    `Player`.
  - [x] Keep context overloads for explicit player, dispenser, and unowned-automation use.
  - [x] Replace null-player GameTest calls with the intended explicit context.
  - [x] Confirm there is no production route by which a missing player can reuse a fake player's stale
    position.

- [x] **T10-C — Carry the actual hand through player contexts.**

  BB/SB fluid convenience methods now accept the hand their item entry point already knows. JB, TB,
  MB, cauldron, and milking entry points were audited and already passed their known hand. The player
  factory requires both player and hand; the stack-identity factory no longer exists.

  - [x] Change player-context construction so the caller supplies the known `InteractionHand`.
  - [x] Remove stack-identity hand inference and the fallback to `MAIN_HAND`.
  - [x] Update BB, SB, JB, TB, and MB player interaction entry points that create a protection context.
  - [x] In `FtbChunksProtection`, treat a non-null context player as the required `ServerPlayer` and use
    the required hand directly.
  - [x] Remove the fallbacks that reclassify a malformed player context as automation or main-hand use.
  - [x] Add or update coverage proving that main-hand and offhand contexts reach the provider with the
    correct hand.

- [x] **T10-D — Fix cancellation ordering for the foreign-main-hand transfer route.**

  `NBEvents` now runs at `LOWEST`, excludes canceled events at registration, and retains an explicit
  cancellation guard before resolving either held stack. Event-bus coverage verifies both an earlier
  `HIGH`-priority veto with unchanged hands/world and the successful uncanceled air-click route.

  Verification follow-up (2026-08-07): the full parallel GameTest run first reported that the
  successful event-bus route did not consume its synthetic event. A second run showed that both
  event-bus tests failed their fixture's pre-post `MISS` assertion. Production and fixture raytraces
  now use interpolation time `1.0F`, so they read the current player look direction. Each global
  event-bus test also runs in its own batch after four setup ticks. Reverification is pending.

  - [x] Register `NBEvents.onRightClickItem` at the latest appropriate event priority, with canceled
    events excluded.
  - [x] Check cancellation before calling any mutating transfer method.
  - [x] Preserve the route only for the case in which the foreign container is in the main hand and a
    supported Some Buckets container is in the offhand.
  - [x] Cancel the event only after a transfer actually succeeds.
  - [x] Add an ordering GameTest or event-bus test in which an earlier protection listener vetoes the
    interaction and neither hand nor the world changes.
  - [x] Add the corresponding success test to ensure the later priority does not suppress valid
    transfers.

- [x] **T10-E — Preserve the selected ordinary settlement-drop boundary.**

  Source inspection confirms settlement still chooses a content-bearing result for the hand, groups
  compatible results into legal stacks, and sends only the remaining piles through ordinary
  `Player.drop`. The completed T10-D ordering lets earlier Forge interaction listeners veto before
  this settlement path can run.

  - [x] Keep settlement drops on the ordinary `Player.drop` path without an internal claim-provider
    preflight.
  - [x] Ensure T10-D cancellation ordering gives ordinary interaction listeners their opportunity to
    veto before either handler executes.
  - [x] Preserve the current legal-stack settlement rule: keep one useful stack in hand
    and drop only results that cannot share that slot.
  - [x] Add acceptance coverage using a transfer that fans out into multiple result stacks while an
    internal provider would deny `ENTITY_RELEASE`.

- [x] **T10-F — Put the protection facade in the protection package.**

  The single `Protections` facade now sits beside the context, action, provider registry, provider
  SPI, and dispenser identity in `com.github.crittscott.somebuckets.protection`. All production and
  GameTest references use that package; the old utility-package facade has been removed.

  - [x] Move `util/Protections.java` to the `protection` package without introducing a second facade.
  - [x] Update production and GameTest imports.
  - [x] Confirm that all protection types are discoverable under one package boundary.

- [x] **T10 — Complete the protection-boundary package.**

---

## T20 — Restore native placement and Mob Bucket observability

Depends on T00-A and T10. Sources: reinvention findings 1–4 and its test gaps.

- [x] **T20-A — Route BB powder-snow output through the block-placement contract.**

  Powder output now uses the vanilla powder-snow `BlockItem` placement primitive inside Forge's
  captured snapshot/place-event transaction. BB target resolution uses the same `BlockPlaceContext`;
  claim checks precede the transaction, and BB debit/stat work follows a successful commit. Player
  output therefore inherits native placement checks, `PLACED_BLOCK`, and a placed-state
  `BLOCK_PLACE` game event. Powder output no longer posts `FillBucketEvent`; pickup still does.

  - [x] Replace the direct `Level.setBlock` placement path with the Minecraft/Forge block-item
    placement pipeline, or an equivalent transaction that posts the same Forge place event before
    commitment.
  - [x] Keep BB's custom multi-unit storage debit separate from the world-placement primitive.
  - [x] Resolve the exact adjacent placement target by the same rules used for the actual mutation.
  - [x] Check protection at that exact target before world or item mutation.
  - [x] Debit one powder unit only after placement succeeds.
  - [x] Emit `GameEvent.BLOCK_PLACE` with the placed block-state context, not `FLUID_PLACE`.
  - [x] Fire `CriteriaTriggers.PLACED_BLOCK` for a successful server-player placement.
  - [x] Preserve the existing `Stats.ITEM_USED` award exactly once.
  - [x] Ensure cancellation or placement failure leaves both the world and bucket unchanged.
  - [x] Add coverage for ordinary placement, Forge place-event cancellation, protection denial,
    `PLACED_BLOCK`, the exact game-event type, and final-unit normalization.

- [x] **T20-B — Reuse the world-fluid placement owner for aquatic MB release.**

  Aquatic release now short-circuits already-wet targets and otherwise delegates to
  `FluidPlacement.emptyContents` with an explicit hit and face-offset disabled. `ENTITY_RELEASE`
  remains first, followed by `FLUID_EDIT` inside the shared placement transaction. Native
  ultra-warm behavior is explicit: water evaporates, then a successfully inserted aquatic mob is
  removed from the bucket; a later rejected insertion retains the snapshot without rolling back
  water that already committed.

  Verification follow-up (2026-08-07): in-game checks confirm ordinary aquatic release creates water
  and Nether release evaporates water while releasing a recapturable cod. The ordinary-water fixture
  passed after it stopped capturing and re-adding the same UUID in one tick. The synthetic Nether
  fixture continued to miss the released entity despite isolated and delayed observation, contradicting
  the verified game behavior, so that unreliable test was removed.

  - [x] Replace `MBItem.placeWaterFor`'s independent waterlogging, replacement, and source-placement
    algorithm with the applicable `FluidPlacement` operation.
  - [x] Supply an explicit hit/face and disable player-style fall-through when dispenser or MB release
    semantics require the exact target.
  - [x] Preserve the separate `ENTITY_RELEASE` and `FLUID_EDIT` permission checks at the resolved
    destination.
  - [x] Apply the T00-A bucket-event contract before required water mutation.
  - [x] Preserve the rule that an already-wet destination produces no water sound or fluid game event.
  - [x] Preserve `GameEvent.ENTITY_PLACE` only after `addFreshEntity` succeeds.
  - [x] Preserve the existing non-rollback rule when water is committed but a later entity-join hook
    rejects the mob.
  - [x] Define and test the native-parity result in an ultra-warm dimension: no persistent illegal
    water block may be created, and bucket/entity state must follow the selected vanilla-compatible
    release contract.
  - [x] Add player and dispenser tests for waterlogging, source placement, existing water, collision
    failure, final spawn rejection, event cancellation, and ultra-warm behavior.

- [x] **T20-C — Add missing MB player statistics and criteria.**

  `MBItem.capture` now fires `FILLED_BUCKET` only when its successful context carries a real
  `ServerPlayer`, after storage/removal completes. `releaseOldest` awards the MB item-use statistic
  only after entity insertion, snapshot removal, and normalization succeed. Coverage includes player
  success, automation and failed capture, collision/protection failure, and canceled final insertion.

  Verification follow-up (2026-08-07): `runData` initially reported a missing `Entity` import in
  `MBGameTests`; the import has been added. Reverification is pending.

  - [x] After a successful player capture has appended the snapshot and removed the live mob, fire
    `CriteriaTriggers.FILLED_BUCKET` with the now-filled MB stack.
  - [x] Do not fire the criterion for dispenser capture or failed capture.
  - [x] Award `Stats.ITEM_USED` exactly once after successful player release.
  - [x] Do not award the stat for dispenser release, collision failure, protection denial, or rejected
    entity insertion.
  - [x] Add positive and negative listener/stat coverage for all of these cases.

- [x] **T20-D — Implement and document the selected `FillBucketEvent` behavior.**

  BB/SB fluid pickup and placement and BB powder pickup use the compatible-`ALLOW` contract at the
  resolved mutation target. T00 deliberately excludes native powder block output and aquatic MB
  water placement because neither operation can honor a listener-supplied held-bucket result; those
  routes use their ordinary block/entity interaction hooks and the internal protection layer.
  Cancellation and incompatible results return before mod-owned mutation, and no source or
  maintained documentation describes ignored `ALLOW` as parity.

  - [x] Apply the T00-A contract consistently to BB and SB world pickup/placement.
  - [x] Apply it to BB powder-snow placement only if T00-A retains the event for block placement.
    T00-A excludes powder-snow output; its native `EntityPlaceEvent` path is covered under T20-A.
  - [x] Apply it to aquatic MB water placement.
    T00-A excludes aquatic MB placement because MB cannot honor a held-bucket replacement result.
  - [x] Ensure every posted event names the position the implementation will actually mutate.
  - [x] Ensure cancellation/denial happens before bucket, block, fluid, or entity mutation.
  - [x] Remove or rewrite comments and tests that describe ignored `ALLOW` as Forge parity.

- [x] **T20 — Complete native-placement and MB-observability work.**

T10/T20 verification (2026-08-07): user reports `runData` and `runGameTests` pass and the changed
behavior works in-game.

---

## T30 — Converge fluid and capability transactions

Depends on T10. T30-D also depends on the event rules recorded in T00-A. Sources: organization
finding 2; dead-code findings 2–4; guarding finding 4; magic-numbers finding 1; reinvention findings
4–7.

- [x] **T30-A — Replace the fluid-unit literal with Forge's contract.**
  - [x] Replace semantic bucket-volume `1000` literals with `FluidType.BUCKET_VOLUME` in
    `BBFluidLogic`, `SBFluidLogic`, `SBFluidHandler`, `Cauldrons`, `Dispensers`, `BBItem`, `Transfers`,
    `NBTUtil`, `FuelHandler`, and `ModCreativeTabs`.
  - [x] Distinguish bucket-volume uses from unrelated numerical literals before replacing them.
  - [x] Add local conversion helpers only where they make unit-to-mB multiplication or whole-unit
    division clearer.
  - [x] Preserve mB precision for finite capability transfers smaller than one bucket; restrict exact
    one-unit behavior only to world, cauldron, and configured block transactions.

  Completion note (2026-08-07): all semantic bucket-volume literals in the named owners now use
  `FluidType.BUCKET_VOLUME`. The remaining `1000` there is the unrelated seconds value in the lava
  fuel comment. No conversion helper was added because the direct constant keeps multiplication and
  whole-unit rounding explicit. Generic finite capability transfers retain arbitrary mB precision;
  existing exact-unit world, cauldron, vanilla-container, and configured block transactions retain
  their prior behavior.

- [x] **T30-B — Give finite content draining one implementation.**
  - [x] Define the authoritative finite drain contract in the NBT persistence owner: return the
    removed content, subtract the requested amount, and normalize an exhausted mode.
  - [x] Make `BBFluidHandler.performDrain` delegate mutation to that operation instead of reproducing
    read/min/subtract/store/clear logic.
  - [x] Ensure the operation handles the content kinds it intentionally supports; do not document it
    as fluid-only if milk also uses it.
  - [x] Remove post-drain `normalizeEmptyState` calls from BB logic, cauldrons, and dispensers where the
    drain contract already normalizes.
  - [x] Add or update coverage for partial drain, exact final drain, simulation, milk, and unrelated
    NBT preservation.

  Completion note (2026-08-07): `NBTUtil.drainFiniteContent` is now the sole fluid/milk debit
  implementation and returns the removed mB amount. `BBFluidHandler` runs that operation against the
  real stack for execution and a copy for simulation. World placement, cauldrons, dispensers, milk
  drinking, crafting remainders, and milk hand transfers use the same normalizing operation; redundant
  post-drain normalization was removed. State GameTests cover partial and final fluid drains,
  simulation, partial and final milk drains, and unrelated-NBT preservation.

- [x] **T30-C — Make the mod bucket's own capability a required invariant.**
  - [x] Retrieve the BB/SB stack's `FLUID_HANDLER_ITEM` capability once at the bucket-specific entry
    point and fail visibly if it is absent.
  - [x] Continue treating a foreign block or item capability as optional and allowed to refuse
    simulation or execution.
  - [x] Remove repeated `orElse(null)` lookups of the mod's own item handler.
  - [x] Remove the SB item-handler local, argument, and parameter that are currently fetched and passed
    but unused.
  - [x] Ensure absence of the mod's own capability can no longer change dispatch from tank handling to
    world-fluid handling.

  Completion note (2026-08-07): BB/SB entry points now require their own item capability with a
  descriptive `IllegalStateException`, while sided foreign block capabilities remain nullable and
  may still refuse transactions. Capability presence no longer participates in block-versus-world
  dispatch, repeated nullable item lookups are gone, and SB placement no longer passes an unused item
  handler.

- [x] **T30-D — Centralize block-capability one-unit transfers in `Transfers`.**
  - [x] Define one shared operation for taking exactly one bucket volume from a block handler into a BB
    or SB item handler.
  - [x] Define one shared operation for placing exactly one bucket volume from a BB or SB item handler
    into a block handler.
  - [x] Let existing finite/infinite item handlers express consumption versus non-consumption; do not
    add a boolean `isSourceBucket` transaction mode.
  - [x] Centralize sided block-capability discovery using the actual contacted face.
  - [x] Preserve transaction order: discover, simulate source, simulate destination, authorize exact
    block interaction, execute, then emit feedback.
  - [x] Require a complete one-bucket-volume transaction for these block-click paths; a partial result
    must fail without mutation.
  - [x] Award `Stats.ITEM_USED` exactly once for successful player transactions and never for
    automation.
  - [x] Emit `GameEvent.FLUID_PICKUP` after a successful block drain and `FLUID_PLACE` after a
    successful block fill, attributed to the player or null for automation.
  - [x] Do not fire `FILLED_BUCKET` for capability-mediated drains.
  - [x] Replace the duplicated BB/SB block transaction bodies with calls to the shared operations.
  - [x] Add coverage for BB, SB, player, dispenser, sided capability, partial refusal, protection
    denial, exact game-event type, and mutation atomicity.

  Completion note (2026-08-07): `Transfers` now owns sided discovery and exact one-bucket-volume
  take/place transactions, returning a tri-state result so handler presence retains dispatch even on
  refusal. BB and SB supply their finite/infinite item handlers to the same operations. Successful
  transactions perform exact protection, stat, sound, and `FLUID_PICKUP`/`FLUID_PLACE` feedback;
  player item routing excludes capability transactions from `FillBucketEvent`/`FILLED_BUCKET`.
  `BlockCapabilityGameTests` adds a sided test tank and covers BB/SB, player/automation/dispenser,
  partial refusal, protection denial, exact event type, and atomic state.

  Verification follow-up (2026-08-07): the first 157-test parallel run exposed fixture isolation in
  pre-existing global event listeners: a player-interaction veto also caught another test's player,
  and a cod-insertion veto also caught other aquatic releases. Both listeners are now scoped to their
  own player/entity and level. The new capability-protection provider is likewise scoped to its test
  level and target. The blocked-input Junk Bucket assertion now identifies its own input entity in a
  tighter fixture-local area instead of counting unrelated neighboring-test item entities.

- [x] **T30-E — Centralize fluid feedback without adding a sound-only class.**
  - [x] Put the shared fill/empty sound resolution under the existing transfer subsystem.
  - [x] Resolve the moved fluid's registered `SoundActions.BUCKET_FILL` or `BUCKET_EMPTY` sound.
  - [x] Use the existing vanilla water/lava fallback only when the fluid type defines no sound.
  - [x] Make world pickup, world placement, block capability transfer, and held-item transfer use the
    same resolver.
  - [x] Use `COW_MILK` for successful automated SB milking.
  - [x] Remove duplicated `fillSound`/`emptySound` methods after all callers have moved.
  - [x] Add focused coverage or a test hook for water, lava, a fluid with a registered custom sound,
    fallback behavior, and dispenser milking.

  Completion note (2026-08-07): `Transfers` owns fill/empty resolution, preferring the moved fluid's
  registered sound and applying the water/lava fallback only when absent. World pickup/placement,
  block capabilities, and held-item Forge-fluid transfers all call it. Automated SB milking now uses
  the shared `COW_MILK` hook. State GameTests cover water, lava, injected registered-sound precedence,
  both fallback branches, and the automated-milking selection; the existing dispenser-milking test
  covers the full automation path.

- [x] **T30-F — Use Forge's finite handler-transfer facility where compatible.**
  - [x] Replace the ordinary finite `Transfers.pump` transaction with Forge's handler-to-handler
    transfer facility if it preserves the required single-round semantics against foreign handlers.
  - [x] Retain the custom `pumpUnlimited` path for direct SB hand transfer.
  - [x] Retain the public SB capability's one-bucket-per-call limit for machines and pipes.
  - [x] Retain custom stack settlement for stacked container items.
  - [x] Remove the negative-capacity clamp; trust the foreign handler contract consistently with its
    simulation and execution results.
  - [x] Preserve in-place mutation for single-item container stacks whose handlers retain the stack
    reference.
  - [x] Add regression coverage for finite-to-finite, SB-to-finite full-capacity pumping, finite-to-SB
    assignment/sink behavior, stacked buckets, and foreign handler refusal.

  Completion note (2026-08-07): ordinary finite pumping now delegates one transfer round to
  `FluidUtil.tryFluidTransfer` with the destination and source handlers in Forge's expected order.
  The custom unlimited SB path, public one-bucket SB capability, settlement, and single-stack
  in-place mutation remain unchanged. Existing `TransferGameTests` cover finite pairs, full-capacity
  SB output, finite-to-SB assignment and sink behavior, stacked settlement, and refusal by a filled
  foreign container.

- [x] **T30-G — Consolidate duplicated pickup accounting.**
  - [x] Give successful world-fluid pickup one owner for `FILLED_BUCKET` and `ITEM_USED` accounting.
  - [x] Keep capability drains excluded from `FILLED_BUCKET`.
  - [x] Ensure client prediction never fires server criteria, stats, sounds, or game events.
  - [x] Remove the duplicated BB/SB pickup-criterion helpers after callers use the shared contract.

  Completion note (2026-08-07): `FluidPickup.completePlayerPickup` now owns the item-use statistic
  and filled-bucket criterion after acquired content has been stored. It rejects client-side calls
  and no-player automation, and `FluidPickup.take` emits sound and the fluid game event only on the
  server. Capability drains continue to use their separate statistic-only transaction. BB and SB
  world-pickup GameTests assert exactly one item-use award and a fired filled-bucket criterion.

- [x] **T30 — Complete fluid-transaction convergence.**

---

## T40 — Give cauldron transitions one owner

Depends on T30-A, T30-B, and T30-E. Sources: organization finding 1; dead-code finding 1;
magic-numbers finding 3.

- [x] **T40-A — Define cauldron operations in the existing `Cauldrons` class.**
  - [x] Define explicit operations for taking full water, lava, and powder snow.
  - [x] Define explicit operations for placing water, lava, and powder snow where that caller supports
    it.
  - [x] Use a private nested result/operation type if structured results are needed; do not add a
    top-level `CauldronTransfers` class.
  - [x] Make each operation identify the content moved and whether mutation succeeded.
  - [x] Keep eligibility and ordering outside the physical transition: finite versus infinite,
    take-first versus place-first, SB allowlist, and dispenser asymmetries remain caller policy.

- [x] **T40-B — Centralize the physical transaction and feedback.**
  - [x] Recognize only the supported full/empty cauldron states.
  - [x] Authorize the exact cauldron position before mutating block or bucket state.
  - [x] Apply the cauldron block-state transition once.
  - [x] Apply the finite debit/fill or infinite SB assignment/supply exactly once through the caller's
    handler contract.
  - [x] Play the matching vanilla or registered sound once.
  - [x] Emit `FLUID_PICKUP` or `FLUID_PLACE` once at the cauldron position.
  - [x] For player operations, award `ITEM_USED` and `USE_CAULDRON` once.
  - [x] Fire `FILLED_BUCKET` only for genuine cauldron pickup by a player.
  - [x] Ensure every failed eligibility, protection, or transaction check leaves both cauldron and
    bucket unchanged.

- [x] **T40-C — Move every caller to the shared cauldron operations.**
  - [x] Replace the registered BB/Huge player cauldron mutation bodies with calls to `Cauldrons`.
  - [x] Replace SB take and placement cauldron mutation bodies in `SBFluidLogic`.
  - [x] Replace BB/Huge dispenser cauldron mutation bodies in `Dispensers`.
  - [x] Replace SB dispenser cauldron mutation bodies in `Dispensers`.
  - [x] Preserve player partial-BB take-first selection.
  - [x] Preserve SB allowlist checks at every input and output boundary.
  - [x] Preserve the dispenser powder-snow asymmetry documented in `player-view.md`.

- [x] **T40-D — Remove cauldron magic values.**
  - [x] Replace full layered-cauldron level `3` with
    `LayeredCauldronBlock.MAX_FILL_LEVEL`, or one named project constant if the mapped constant is not
    accessible.
  - [x] Replace block update flag `3` with `Block.UPDATE_ALL`.
  - [x] Ensure the two unrelated concepts never share an unexplained literal on the same transition.

- [x] **T40-E — Cover every selection shape after consolidation.**
  - [x] Add or update player tests for finite water/lava/powder pickup and placement.
  - [x] Add or update player tests for SB assignment, infinite placement, and disallowed content.
  - [x] Add or update dispenser tests for BB and SB in both directions.
  - [x] Assert stats, criteria, sound, game event, protection denial, and state atomicity where
    applicable.

  Completion note (2026-08-07): `Cauldrons` now owns explicit water, lava, and powder-snow take/place
  transactions. Fluid operations use the supplied BB/SB handler so finite debit and Source Bucket
  infinity remain handler policy; callers retain ordering, allowlist, and dispenser asymmetry policy.
  All BB map, SB, and dispenser mutation bodies delegate to this owner. GameTest source covers finite
  and Source player round trips, vanilla accounting and game events, allowlist/protection denial,
  BB/SB dispenser round trips, and the powder-snow dispenser asymmetry. Sound selection continues
  through the T30-tested shared resolver. Per project instructions, verification was not run.

- [x] **T40 — Complete cauldron consolidation.**

---

## T50 — Purify persistent-state ownership and storage operations

Depends on T30-B. Sources: organization finding 4; dead-code finding 5; guarding findings 6–7;
magic-numbers finding 2; Javadocs findings 4–5.

- [x] **T50-A — Reorganize `NBTUtil` as persistence-only infrastructure.**
  - [x] Divide the file visibly into BB/SB content state, MB snapshot state, JB/TB stored-item state,
    and shared tag cleanup.
  - [x] Keep getters free of tag attachment and mutation.
  - [x] Keep setters responsible for writing only their own schema and preserving unrelated root NBT.
  - [x] Keep empty-schema cleanup responsible for removing empty Some Buckets keys and then an empty
    root tag.
  - [x] Remove gameplay-policy decisions from this class.
  - [x] Do not split the domains into new top-level persistence classes.

- [x] **T50-B — Move MB admission policy to `MBItem`.**
  - [x] Define `MBItem.MAX_MOBS` as the sole capacity constant.
  - [x] Move same-species and capacity admission checks out of `NBTUtil` and into MB behavior.
  - [x] Use `MAX_MOBS` for capture admission, tooltip denominator, and item-bar fraction.
  - [x] Preserve FIFO snapshot serialization and release order in `NBTUtil`.
  - [x] Add boundary coverage for empty, seventh, eighth, and rejected ninth captures plus species
    mismatch.

- [x] **T50-C — Move crafting behavior out of persistence code.**
  - [x] Move finite-bucket crafting-remainder policy from `NBTUtil` to `BBItem` or another existing
    item-behavior owner.
  - [x] Preserve empty BB/SB consumption, finite one-unit debit, and assigned SB unchanged remainder.
  - [x] Preserve non-fluid finite modes such as milk and powder snow.
  - [x] Keep NBT helpers limited to reading and changing stored state.
  - [x] Add or update remainder coverage for empty, one-unit, multi-unit, and assigned SB cases.

- [x] **T50-D — Make invalid stored amounts fail at their source.**
  - [x] Remove silent negative-to-zero clamps from `setAmount` and `setPowderUnits`.
  - [x] Give those setters an explicit nonnegative precondition and fail visibly when it is violated.
  - [x] Inspect every reported caller and ensure subtraction occurs only after sufficient-content
    simulation or checking.
  - [x] Retrieve the tag once in `normalizeEmptyState` after a non-empty mode has been established;
    remove impossible repeated null returns.
  - [x] Preserve normalization of zero fluid, milk, powder, and entity content to `Mode.NONE`.

- [x] **T50-E — Give TB one merge-or-replace operation.**
  - [x] Implement one private TB operation that accepts the current stored stack and incoming stack
    and reports the new stored value plus any unconsumed incoming value.
  - [x] Enforce `JBItem.canStore` before empty insertion, merge, or destructive replacement.
  - [x] Preserve the exact rule: merge only when the item matches and the entire incoming amount fits;
    otherwise destroy the old stack and replace it with as much of the incoming stack as one stack can
    hold.
  - [x] Adapt inventory slot insertion to the shared operation.
  - [x] Adapt cursor/`SlotAccess` insertion to the shared operation.
  - [x] Adapt item-entity absorption to the shared operation.
  - [x] Ensure item entities are discarded only when fully consumed and otherwise retain the correct
    remainder.
  - [x] Preserve one-entity-per-interaction lookup through `TBItem.findFirstNearby`.
  - [x] Add equivalent-case tests across all three interaction shapes.

- [x] **T50-F — Correct persistent-state contracts.**
  - [x] Narrow `isEmptyBucket` documentation to mode-based BB/SB/MB state rather than all bucket
    families.
  - [x] Narrow `clearBucket` documentation to the fields it actually clears; do not claim it removes
    `JunkItems` unless implementation is deliberately changed to do so.
  - [x] Describe the finite drain operation accurately for fluid and milk.
  - [x] Document that `getStoredItems` returns a detached mutable list.
  - [x] Document that `setStoredItems` skips empty entries and removes an empty storage key.
  - [x] Document MB entity-list FIFO behavior and unrelated-NBT preservation.

- [x] **T50 — Complete persistent-state and storage cleanup.**

  Completion note (2026-08-07): `NBTUtil` is now persistence-only and visibly grouped by stored
  domain. MB admission and its sole capacity constant live in `MBItem`; finite crafting-remainder
  policy lives in `BBItem`. Negative amount writes fail before mutation, normalization retrieves its
  tag once, and TB slot, cursor, and item-entity intake share one merge-or-replace calculation that
  preserves oversized entity remainders. GameTest source covers the new boundaries and equivalent
  interaction shapes. Per project instructions, verification was not run.

---

## T60 — Reorganize dispenser automation without top-level class proliferation

Depends on T20, T30, and T40 so the internal behaviors delegate to stable primitives. Sources:
organization finding 3; guarding finding 8.

- [x] **T60-A — Make `Dispensers` the single visible automation owner.**
  - [x] Replace the implicit Mob/SB/otherwise-BB type cascade with explicitly registered family
    behaviors.
  - [x] Use short private nested behavior names such as `BBBehavior`, `SBBehavior`, `MBBehavior`, and
    `StorageBehavior` if separate behavior objects are useful.
  - [x] Move `StorageBucketDispenser` into `Dispensers` as a private nested behavior and remove the
    top-level class if Forge registration permits the same clean contract.
  - [x] Do not introduce top-level per-bucket dispenser classes.
  - [x] Make an invalid item/behavior registration fail visibly rather than fall through to a guessed
    BB cast.

- [x] **T60-B — Centralize dispenser target construction.**
  - [x] Compute the block directly in front, the face adjacent to the dispenser, the synthetic hit,
    and `ProtectionContext.dispenser(sourcePos)` once.
  - [x] Use the adjacent face, opposite the firing direction, for sided fluid capability lookup and
    protection.
  - [x] Represent this as a private nested value if it reduces repeated parameters.
  - [x] Preserve the stable `[SomeBuckets]` fake-player identity and source-position update.

- [x] **T60-C — Make dispenser-only APIs server-only.**
  - [x] Carry `ServerLevel` through MB dispenser helpers.
  - [x] Remove client-side early returns from dispenser-only MB logic.
  - [x] Remove `!level.isClientSide` guards around SB dispenser cauldron mutation.
  - [x] Remove the client guard from dispenser-only SB milking.
  - [x] Keep side checks only in helpers genuinely shared with client-predicted item use.

- [x] **T60-D — Preserve each automation selection contract.**
  - [x] BB/SB world placement remains limited to the directly adjacent block.
  - [x] BB collects powder-snow blocks only while empty and does not fill a powder-snow cauldron from
    a dispenser.
  - [x] SB milk acquisition remains empty-only and policy-gated.
  - [x] JB/TB priority remains feed, collect, then eject only when no input target blocks output.
  - [x] TB continues to query and process only one eligible item entity per pulse.
  - [x] MB remains capture-first; any remaining mob blocks release; release occurs only into a mob-free
    front space.
  - [x] Every mutation continues to use the dispenser protection context and fake-player rules.

- [x] **T60-E — Add registration and behavior regression coverage.**
  - [x] Exercise each of the six registered items through its actual registered dispenser behavior.
  - [x] Assert that the bucket remains in the dispenser after every supported operation.
  - [x] Cover the adjacent-face sided-capability rule.
  - [x] Cover fake-player claim denial for fluid, cauldron, feeding/ejection, capture, and release paths
    where fixtures permit.

- [x] **T60 — Complete dispenser reorganization.**

  Completion note (2026-08-07): `Dispensers` now registers explicit private BB, SB, MB, and storage
  behaviors and owns one nested target value for the server level, adjacent block and face, synthetic
  hit, and dispenser protection context. The former top-level `StorageBucketDispenser` is removed;
  mismatched behavior items fail visibly. MB automation and SB milking are server-only, while shared
  player-predicted helpers retain their side gates. Existing automation coverage plus a Huge Bucket
  registration test exercises all six items through real dispensers, and a scoped six-path fixture
  verifies adjacent-face protection and failure atomicity for fluid, cauldron, feeding, ejection,
  capture, and release denial. Per project instructions, verification was not run.

  Verification (2026-08-07): user confirms T60 is complete.

---

## T70 — Remove surviving impossible-state guards

Run after structural work so effort is not spent cleaning code that later disappears. Sources:
guarding findings 5 and 7–10. Valid external-interface guards listed in that review must remain.

- [x] **T70-A — Remove impossible raytrace and hit fallbacks.**
  - [x] In `BBItem`, test `HitResult.Type.MISS` without accepting a null POV result.
  - [x] Do the same in `SBItem` and `NBEvents`.
  - [x] Make `FluidPlacement.emptyContents` require a non-null `BlockHitResult`.
  - [x] Remove the invented `Direction.UP` and disabled-fall-through behavior for a null hit.
  - [x] Require non-player callers to construct an explicit face/hit suited to their semantics.

- [x] **T70-B — Reject invalid constructor and registry invariants visibly.**
  - [x] Validate JB/TB capacity when the item is constructed and remove `getBarWidth`'s nonpositive
    capacity fallback.
  - [x] Remove settlement fallback from an impossible max stack size below one.
  - [x] Remove `JunkIconLayout.seedFor`'s null registry-key substitute for a live stored item.
  - [x] Preserve ordinary empty-stack handling; only impossible registered-item states should fail.

- [x] **T70-C — Simplify SB configuration resolution.**
  - [x] Rely on the Forge config validator for `ResourceLocation` syntax instead of parsing with a
    second nullable branch.
  - [x] After `ForgeRegistries.FLUIDS.containsKey(id)` succeeds, rely on the registry to return its
    value.
  - [x] Preserve the supported case of a syntactically valid unknown ID after an optional mod is
    removed.
  - [x] Preserve one warning per unknown ID per config load/reload, including the config filename.
  - [x] Preserve immutable resolved policy snapshots and boundary checks.

- [x] **T70-D — Audit that genuine external guards remain.**
  - [x] Retain handling for absent foreign capabilities and block entities.
  - [x] Retain simulate/execute refusal by foreign handlers.
  - [x] Retain missing registered fluid sounds with vanilla fallback.
  - [x] Retain `IOException` handling around resource-pack reads without widening catches.
  - [x] Retain nullable override/model texture results where the API permits them.
  - [x] Retain optional FTB Chunks availability, claim denial, collision failure, pickup/placement
    refusal, and rejected `addFreshEntity`.

- [x] **T70 — Complete fail-fast cleanup.**

  Completion note (2026-08-07): BB, SB, and the foreign-main-hand event route now trust Minecraft's
  non-null hit-result contract. `FluidPlacement.emptyContents` requires an explicit hit, and every
  player, Mob Bucket, and dispenser caller supplies the face and fall-through semantics it needs.
  Storage bucket construction rejects nonpositive capacity, transfer settlement trusts registered
  items' positive stack limits, and Junk rendering fails visibly for an unregistered live item.
  Source Bucket policy resolution now relies on the validated config syntax and registry containment
  contract while retaining de-duplicated unknown-ID warnings and immutable snapshots. The external
  guard audit retained optional capabilities and block entities, handler refusal, sound fallback,
  narrow resource I/O catches, nullable client API results, optional FTB Chunks state, protection
  denial, collision and world-operation refusal, and rejected entity insertion. Per project
  instructions, verification was not run.

---

## T80 — Consolidate client wiring, identifiers, and presentation constants

Depends on T50 and T60 only where moved owners affect imports. Sources: organization finding 6;
dead-code finding 6; magic-numbers findings 4–9.

- [x] **T80-A — Establish one client lifecycle owner.**
  - [x] Create or designate `ClientSetup` as the discoverable client bootstrap.
  - [x] Move item-property registration out of the common `SomeBuckets` entry point.
  - [x] Consolidate color, model-loader, and resource-reload registration entry points under that
    bootstrap while leaving renderer implementations in focused client classes.
  - [x] Keep Forge-required `initializeClient` item hooks, but make them delegate to clearly named
    client extension factories.
  - [x] Keep any common-to-client color bridge explicit in name and responsibility.
  - [x] Remove `SBItem.getContentProperty` and register the shared property function directly.

- [x] **T80-B — Remove capacity-based translation identity.**
  - [x] Make `BBItem.getName` derive its translation-key base from the item's description ID rather
    than treating capacity eight as BB and everything else as Huge.
  - [x] Apply the same description-ID approach to SB dynamic names.
  - [x] Append only the content suffix for water, lava, milk, powder snow, or registered fluid
    variants.
  - [x] Preserve every existing key in `en_us.json` and every player-visible name from
    `player-view.md`.
  - [x] Add a focused resource/name test for empty and filled BB, Huge, and SB items.

- [x] **T80-C — Name and verify the model-property protocol.**
  - [x] Define Java constants for `somebuckets:bb_content` and `somebuckets:filled`.
  - [x] Define named BB/SB predicate values for empty, Forge fluid, milk, and powder snow.
  - [x] Define named MB predicate values for empty and filled.
  - [x] Make registration and property-return methods use those constants.
  - [x] Add a resource test that reads the shipped model JSON and verifies property IDs and threshold
    values against the Java protocol.

- [x] **T80-D — Use the canonical mod namespace in Java.**
  - [x] Replace Java-side `"somebuckets"` namespace literals with `SomeBuckets.MODID` in
    `EmptyBucketIngredient`, `SpawnEggIngredient`, `MBItem`, `BucketMouth`, `ClientColorHandlers`, and
    `ModCreativeTabs`.
  - [x] Leave namespace literals in JSON resources unchanged.
  - [x] Keep the stable fake-player identity `[SomeBuckets]` literal and unlocalized.

- [x] **T80-E — Name presentation and behavior literals locally.**
  - [x] Name the item-bar width in BB, JB, and MB presentation code without creating a constants-only
    top-level class.
  - [x] Name the shared default blue and BB content colors where they are used.
  - [x] Name the milk drinking duration in BB and SB.
  - [x] Name lava-bucket furnace burn time in `FuelHandler`.
  - [x] Name JB pickup radius, dispenser ejection speed, and ultra-warm evaporation particle count.
  - [x] Replace NBT element-type literal `10` with `Tag.TAG_COMPOUND`.
  - [x] Define one package-level item-model coordinate size for `BucketMouth`, `JunkIconLayout`, and
    `JBRenderer`.
  - [x] Do not name conventional zeros, ones, tank index zero, ordinary loop bounds, sound volume and
    pitch, or bit masks unless local meaning is genuinely unclear.

- [x] **T80-F — Preserve client failure boundaries.**
  - [x] Keep child-stack rendering delegated to Minecraft's `ItemRenderer`.
  - [x] Keep stack-aware still-texture and tint resolution for NBT-defined fluids.
  - [x] Keep both required quad overrides and self-preserving transforms on behavior-changing model
    wrappers.
  - [x] Keep `IOException` as the narrow resource-read catch and allow internal rendering bugs to fail
    visibly.

- [x] **T80 — Complete client and presentation cleanup.**

  Completion note (2026-08-07): `ClientSetup` is now the single client lifecycle bootstrap for item
  properties, colors, model loaders, baking replacement, and reload listeners. Dynamic BB/Huge/SB
  names derive from registered description IDs, and focused GameTests lock every dynamic English
  name plus the Java/model-JSON predicate protocol. Java identifiers use the canonical mod namespace;
  presentation, drinking, fuel, pickup, ejection, evaporation, and item-model coordinate literals are
  named at their existing owners. The sided fluid-color bridge and Junk client-extension factory are
  explicit, while stack-aware fluid rendering, delegated child rendering, wrapper behavior, and narrow
  resource I/O catches remain intact. Per project instructions, verification was not run.

---

## T90 — Complete API documentation after code shape stabilizes

Depends on T10–T80. Sources: all Javadocs findings.

- [x] **T90-A — Document the claim-protection SPI completely.**
  - [x] Add a class contract to `ClaimProtectionProvider` and document `mayAct` parameters, nullable
    values, target semantics, and the meaning of `false`.
  - [x] Document `ClaimProtections.register`, provider composition, registration lifetime, and the
    `Registration.close()` unregistration behavior.
  - [x] Document the single-threaded registration/read assumption.
  - [x] Document `ProtectionContext`, each factory, player/dispenser/unowned distinctions, required
    hand rules, nullable target entity, and automation source.
  - [x] Document every `ProtectionAction` in terms of the world mutation or interaction it represents.
  - [x] Document the meaning of target position and face for block, fluid, and entity operations.

- [x] **T90-B — Document the fluid-handler subclass contract.**
  - [x] Add a class contract to `AbstractFluidHandler`.
  - [x] Document the preconditions and return value of `fillEmpty`.
  - [x] Document the preconditions and return value of `fillExisting`, including fluid equality.
  - [x] Document `performDrain`, simulation requirements, and mutation ownership.
  - [x] Document `canAcceptFluid` and the content/mode checks already completed by the base class.
  - [x] Let concrete handler overrides inherit the base contract; comment only the finite/infinite
    policy differences.

- [x] **T90-C — Document public mutation transactions.**
  - [x] Add concise class contracts to `BBFluidLogic` and `SBFluidLogic` explaining dispatch versus
    shared transaction ownership.
  - [x] Document each public `tryTake`/`tryPlace` entry point: prediction, server mutation, protection,
    feedback, return meaning, and failure atomicity.
  - [x] Document `FluidPlacement.emptyContents`, including target selection, fall-through, protection,
    and the caller's responsibility for item debit.
  - [x] Document JB/TB collection, feeding, and insertion mutation results.
  - [x] Rewrite `Transfers.tryTransferOne` and `tryTransferEither` around accepted inputs, ordered
    direction, client/server behavior, settlement, side effects, and return meaning.
  - [x] Remove temporal wording such as "callers use today" and implementation-history commentary.

- [x] **T90-D — Add concise contracts to the five item landmarks.**
  - [x] Document `BBItem` as finite single-content storage shared by BB and Huge tiers.
  - [x] Document `SBItem` as infinite policy-gated assignment and supply/sink behavior.
  - [x] Document `JBItem` as FIFO stack-entry storage and the shared JB/TB interaction base.
  - [x] Document `TBItem` as one-entry merge-or-destructive-replacement storage.
  - [x] Document `MBItem` as same-species FIFO entity snapshots with delayed removal on release.

- [x] **T90-E — Correct MB and TB edge contracts.**
  - [x] Describe `MBItem.needsWater` by its actual `Bucketable`/water-mob classification rather than a
    claim about suffocation.
  - [x] Document capture success as snapshot appended plus live mob discarded; document failure as no
    mutation.
  - [x] Document release collision/protection behavior, UUID collision repair across loaded levels,
    removal only after successful insertion, and non-rollback of already placed water.
  - [x] Document `TBItem.findFirstNearby` as the first eligible query result, explicitly not the nearest
    entity.

- [x] **T90-F — Convert implementation narration to ordinary comments.**
  - [x] Keep public/package client-rendering Javadocs focused on abstraction and stable guarantees.
  - [x] Convert private mask scanning, row merging, coordinate conversion, nested-render translation,
    render-pass traversal, cache-key, and quad-overload narration to ordinary comments.
  - [x] Replace historical and hypothetical phrasing with direct statements of the invariant.
  - [x] Do not add boilerplate Javadocs to straightforward Forge overrides, registry constants, or
    event subscribers.

- [x] **T90 — Complete the documentation pass.**

  Completion note (2026-08-07): the protection SPI now documents provider composition, context
  identity, target semantics, action meanings, and registration lifetime/threading. Fluid handlers,
  BB/SB transaction entry points, `FluidPlacement`, held transfers, and JB/TB mutations state their
  simulation, mutation, authorization, feedback, and return contracts. The five item landmarks and
  Source Bucket policy have concise class contracts; MB capture/release and TB query-order edges are
  explicit. Private client-rendering algorithm narration is now ordinary commentary while public and
  package contracts retain the rendering invariants.

  Post-T80 re-audit (2026-08-07): T90-A through T90-F were checked again against the completed client
  and presentation reshaping. The new `ClientSetup` bootstrap documents its lifecycle boundary;
  `ClientColorHandlers` and `ClientModelLoaders` now identify themselves as delegated implementations.
  BB/SB/MB landmark and predicate contracts describe description-ID naming and the named model-state
  protocols, `SidedFluidColors` documents its physical-side and fallback behavior, and the Junk client
  extension factory retains its focused contract. The protection, handler, transaction, storage,
  MB/TB edge, and renderer-failure contracts remain accurate. Per project instructions, no build,
  test, or Javadoc tool was run.

---

## T95 — Remove residual dead code and stale duplication

Depends on all structural tasks so code is removed only once. Sources: dead-code findings 3, 6, and
7 plus the final review audit.

- [x] Remove any unused SB capability parameter or lookup not already removed by T30-C.
- [x] Remove `SBItem.getContentProperty` if not already removed by T80-A.
- [x] Remove unused `GameTestSupport.big64()`.
- [x] Remove unused `GameTestSupport.snapshotTag()`.
- [x] Remove unused `GameTestSupport.assertTagEquals()` and its now-unused `CompoundTag` import.
- [x] Search production Java for the old duplicated BB/SB block-capability transaction bodies and
  remove any remnants.
- [x] Search production Java for old independent cauldron mutation bodies outside `Cauldrons` and
  remove any remnants.
- [x] Search TB interaction paths for duplicate merge-or-replace branches outside the canonical
  operation.
- [x] Confirm no compatibility branch or legacy item-format support was introduced during the work.
- [x] **T95 — Complete residual cleanup.**

  Completion note (2026-08-07): `SBItem.getContentProperty` and the obsolete SB placement-handler
  argument were already removed by T80-A and T30-C. The three unused `GameTestSupport` helpers and
  their `CompoundTag` import are now removed. Source inspection finds one BB/SB block-capability
  transaction owner in `Transfers`, one physical cauldron owner in `Cauldrons`, and one TB
  merge-or-replace operation used by all three intake shapes. No legacy schema branch was added.

---

## T99 — Final traceability and handoff

Depends on T00–T95.

- [x] **T99-A — Audit every review finding against this completed work.**
  - [x] Audit `code-review-organization.md` findings 1–7; record each as completed or deliberately
    declined with the architectural reason.
  - [x] Audit `code-review-dead-code.md` findings 1–7.
  - [x] Audit `code-review-guarding.md` findings 1–10 and confirm all listed valid guards remain.
  - [x] Audit `code-review-magic-numbers.md` findings 1–9 and the localization conclusion.
  - [x] Audit `code-review-javadocs.md` findings 1–8.
  - [x] Audit `code-review-reinvention.md` findings and every listed test gap.
  - [x] Audit `code-review-protection.md` findings and every listed test gap.

  Audit result (2026-08-07): organization findings 1, 2, 6, and 7 are completed by T40, T30,
  T80, and T10-F. Finding 3 is completed with private family behaviors inside `Dispensers`, avoiding
  the review's proposed top-level class proliferation. Finding 4 is completed by making `NBTUtil`
  persistence-only and visibly grouping its schemas; splitting it into top-level state classes was
  deliberately declined. Finding 5's small BB/SB gesture prelude remains deliberately duplicated
  because its policies differ and the queue explicitly retains that boundary.

  Dead-code findings 1–6 map to T40, T30-D/E/G, T30-C, T30-B, T50-E, and T80-A; finding 7 is T95.
  Guarding findings 1–10 map to T10-A/B/C, T30-C, T70-A, T50-D, T30-F/T50-D/T70-B, T60-C, T70-C,
  and T70-A. T70-D reconfirmed every supported external guard listed by the review. Magic-number
  findings 1–9 map to T30-A, T50-B, T40-D, and T80-B–E; the localization conclusion remains valid.
  Javadoc findings 1–8 map to T90-A–F plus the corrected persistence contracts in T50-F.

  Reinvention findings map to T20-A–D and T30-D–F. Their event, criterion, statistic, sound,
  protection, and atomicity gaps now have focused source coverage. Aquatic MB water placement is
  deliberately outside `FillBucketEvent` under T00-A because it cannot honor that event's held-item
  result contract; it uses the native-style placement transaction and ordinary interaction hooks.
  The synthetic ultra-warm entity-observation fixture was removed after proving unreliable, while
  the behavior was verified in-game and the delegated evaporation primitive remains covered.

  Protection findings map to T10-D and the T00-B/T10-E ordinary-settlement-drop decision. The
  feasible public, registered-map, fake-player, and denial paths have regression source. Direct
  end-to-end tests against optional FTB Chunks and Open Parties and Claims remain deliberately
  outside the fixture: their availability and external hooks are retained as genuine integration
  boundaries rather than simulated as those mods.

- [x] **T99-B — Reconcile maintained documentation.**
  - [x] Update `as-built.md` architecture, ownership, event contracts, and maintenance invariants to
    match the completed implementation.
  - [x] Update `player-view.md` only for observable corrections such as native powder-snow hooks,
    ultra-warm aquatic release behavior, or changed transfer denial behavior.
  - [x] Remove maintenance warnings that existed solely because cauldron or block transactions had
    multiple owners.
  - [x] Ensure neither document describes intent as current behavior; source remains authoritative.

  Completion note (2026-08-07): `as-built.md` records the consolidated transfer, cauldron,
  dispenser, protection, persistence, event, criterion, statistic, sound, and client ownership.
  Its maintenance rules point to the shared owners rather than duplicated mutation bodies.
  `player-view.md` changes only the observable native powder-snow placement hooks and ultra-warm
  aquatic release result. Both documents continue to describe implemented behavior, not intent.

- [x] **T99-C — Perform read-only completion checks.**
  - [x] Inspect every changed file for imports, signatures, call-site consistency, and stale comments.
  - [x] Search for bucket-volume literal `1000`, cauldron-state/update literal `3`, repeated namespace
    literals, obsolete class names, nullable protection contexts, and old helper names; classify any
    legitimate survivors explicitly.
  - [x] Search for new bucket-specific class names and confirm they use BB/JB/MB/SB/TB abbreviations.
  - [x] Confirm no unnecessary top-level helper classes were added.
  - [x] Confirm every changed mutation path checks protection before mutation and emits feedback only
    after success.
  - [x] Confirm test source covers every intentionally corrected event, criterion, statistic, sound,
    protection, and failure-atomicity contract.

  Completion note (2026-08-07): the pending diff passes `git diff --check`. Production Java has no
  semantic bucket-volume `1000`, cauldron level/update `3`, duplicate namespace declaration,
  nullable mutation-context parameter, obsolete transaction/helper name, or legacy-format branch.
  Literal `1000` remains in GameTests as an explicit expected boundary amount; the sole standalone
  Java namespace declaration is `SomeBuckets.MODID` (resource paths and translation keys retain
  their complete string identifiers). Nullable player, hand, entity, sound, capability, model,
  and resource values remain only where the Minecraft/Forge or automation contract permits them.
  Existing long-form names are presentation/test/configuration types, not newly introduced bucket
  service classes. New top-level types are the coherent client bootstrap/side bridge, moved
  protection facade, and focused GameTest classes. The shared mutation owners simulate and
  authorize before execution and emit feedback only after success; focused tests cover the corrected
  observable and failure-atomicity contracts.

- [ ] **T99-D — Hand off verification to the user.**
  - [x] List the exact build and GameTest suites warranted by the completed changes without running
    them.
    Run `.\gradlew.bat build`, `.\gradlew.bat runData`, and
    `.\gradlew.bat runGameTestServer` from the project root.
  - [x] Record user-reported failures beneath the owning task and reopen its checkbox if necessary.
    No unresolved user-reported failure is pending at handoff.
  - [ ] After user verification succeeds, record the result here.

- [ ] **T99 — Complete the unified code-review remediation.**
