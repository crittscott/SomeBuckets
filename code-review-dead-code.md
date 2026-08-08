# Targeted code review: dead and duplicated code

## Scope and method

This review examined the current Java sources, GameTests, runtime models, recipes, textures, language
file, and entity tag. It did not read any other code-review reports, modify implementation code, or
build/run the project. Forge event subscribers and GameTest methods were treated as framework entry
points even when ordinary text searches find no Java caller.

## Summary

There are no orphaned production classes or runtime resources apparent in the current tree. The six
registered items account for the six primary item models and recipes; every auxiliary model is
referenced by an override; every runtime texture is referenced by a model or by the Junk Bucket mask
loader; and the entity tag is read by `MBItem`.

There is, however, one dead production argument and its associated lookup, three unused GameTest
helpers, and one unnecessary forwarding method. The more important result is duplicated transaction
logic. Cauldron operations and block-capability fluid transfers have evolved into parallel Big Bucket,
Source Bucket, player, and dispenser implementations. These currently agree on most mechanics, but a
change to protection, events, sounds, state mutation, or statistics has to be repeated correctly in
several places.

## Findings

### 1. Medium: cauldron transactions are implemented repeatedly

The same water/lava cauldron state changes, protection checks, sounds, game events, and bucket-NBT
updates appear in several independent paths:

- finite player use in `interaction/Cauldrons.java:48-190`;
- Source Bucket pickup and placement in `fluid/SBFluidLogic.java:83-124` and `:256-305`;
- Source Bucket dispenser use in `interaction/Dispensers.java:52-93`;
- finite Big Bucket dispenser use in `interaction/Dispensers.java:95-164`.

The need for player and dispenser *selection* logic to remain separate does not require each branch
to own its own low-level cauldron mutation. For example, replacing a full water cauldron with an empty
one is spelled out in `Cauldrons.java:106-128`, `SBFluidLogic.java:86-103`,
`Dispensers.java:63-72`, and `Dispensers.java:128-138`. Empty-cauldron fills are similarly repeated.

This is the highest-risk duplication in the project because the branches have different surrounding
requirements (finite versus infinite contents, player statistics/criteria versus automation, and
Source Bucket policy). A future change can easily update the obvious path but omit one sibling. The
as-built maintenance rule that every cauldron change must inspect both player and dispenser paths is
itself evidence of this maintenance cost.

Recommendation: keep player/dispenser dispatch separate, but extract small transaction primitives for
the shared operations: inspect a supported cauldron, authorize its exact position, apply the state
transition, emit the event/sound, and return a result describing what was moved. Let the caller remain
responsible for finite-bucket charging and player-only awards. This avoids forcing dissimilar policies
into one large abstraction while removing the repeated world mutations.

### 2. Medium: Big and Source block-capability transfer logic is substantially duplicated

`BBFluidLogic` and `SBFluidLogic` repeat the following pieces nearly verbatim:

- compatible block capability discovery (`BBFluidLogic.java:56-63`,
  `SBFluidLogic.java:53-67`);
- the player/null-context adapter around take and place (`BBFluidLogic.java:43-47`, `:149-153`,
  `SBFluidLogic.java:47-51`, `:143-147`);
- the simulate/authorize/execute transaction for draining one bucket from a block
  (`BBFluidLogic.java:176-197`, `SBFluidLogic.java:183-204`);
- Forge fluid sound lookup and vanilla fallback (`BBFluidLogic.java:223-235`,
  `SBFluidLogic.java:227-237`);
- player pickup criterion handling (`BBFluidLogic.java:336-340`,
  `SBFluidLogic.java:333-338`).

The finite/infinite distinction belongs in the item-side handler and in whether placement drains the
item; it does not justify duplicate capability discovery, protection, feedback, and pickup-award code.
There is already one observable drift: Source explicitly rejects a simulated block drain below 1,000
mB at `SBFluidLogic.java:187-188`, whereas Big relies on the subsequent item-fill simulation to reject
it at `BBFluidLogic.java:183-184`. The outcome should normally match, but the two paths now express the
same transaction differently.

Recommendation: extract a shared block-fluid transfer helper that owns capability discovery,
simulation, exact-position authorization, execution, and fluid-specific sound selection. Supply the
finite/infinite behavior through the existing `IFluidHandlerItem` contract or a small caller policy.
At minimum, centralize capability discovery and sound resolution so those rules have one definition.

### 3. Low: Source Bucket placement fetches and passes an unused item capability

`SBFluidLogic.tryPlace` obtains `itemHandler` at `SBFluidLogic.java:161` and passes it to
`tryPlaceToBlock` at `:162-163`. The callee declares the parameter at `:206-209` but never reads it.
`compatibleBlockCapability` has already verified that the stack exposes the item capability, so the
second lookup has no effect.

Recommendation: remove the local lookup, argument, and parameter. If finding 2 is addressed first,
this disappears naturally.

### 4. Low: finite fluid draining has two implementations, followed by redundant normalization

`NBTUtil.drainFluid` (`NBTUtil.java:244-264`) is the central finite-content drain operation and clears
the bucket itself when the remaining amount is nonpositive. `BBFluidHandler.performDrain`
(`BBFluidHandler.java:46-63`) independently reimplements the same read/min/subtract/clear-or-store
algorithm.

In addition, five call sites immediately invoke `NBTUtil.normalizeEmptyState` after
`NBTUtil.drainFluid`, even though `drainFluid` has already either cleared the bucket or stored a
positive amount:

- `BBFluidLogic.java:242-243`;
- `Cauldrons.java:60-61` and `:71-72`;
- `Dispensers.java:114-115` and `:122-123`.

Recommendation: have `BBFluidHandler.performDrain` compute the returned `FluidStack` and delegate the
mutation to `NBTUtil.drainFluid`. Remove the five post-drain normalization calls, or change the
contract explicitly if callers are intended to own normalization. The current Javadoc already says
that `drainFluid` handles empty-state normalization.

### 5. Low: Trash Bucket repeats its merge/replace operation in three interaction shapes

The inventory callbacks in `TBItem.java:38-68` and `:72-105` contain parallel full-fit-merge and
replace branches, differing mainly in whether the incoming stack is held by a `Slot` or
`SlotAccess`. The world-entity path at `:176-214` implements the same semantic operation again. Within
that method, the empty and replacement cases repeat the same copy, clamp, store, shrink, and
discard/update block at `:178-190` and `:203-214`.

Recommendation: define one private Trash storage operation over a stored stack and incoming stack
that returns both the new stored value and the unconsumed incoming value. Adapt the slot, cursor, and
entity owners around that result. This would keep the deliberately unusual “merge only if the whole
stack fits; otherwise replace” rule in one place.

### 6. Low: a one-line Source Bucket presentation wrapper is unnecessary

`SBItem.getContentProperty` at `SBItem.java:212-214` only forwards to
`BBItem.getContentProperty`. Its sole production caller is Source Bucket property registration in
`SomeBuckets.java:96-97`; that caller can use `BBItem.getContentProperty` directly, as the comment in
`SomeBuckets` already says it is reusing the Big Bucket mapping.

Recommendation: register the shared function directly and remove the forwarding method.

### 7. Low: three GameTest support helpers are unused

These methods have no callers anywhere in the Java tree:

- `GameTestSupport.big64()` at `GameTestSupport.java:44-46`;
- `GameTestSupport.snapshotTag()` at `:187-189`;
- `GameTestSupport.assertTagEquals()` at `:191-194`.

Removing the last two also removes the otherwise unnecessary `CompoundTag` import. These are the only
clear unused methods found after accounting for annotation-discovered event subscribers and GameTests.


## Suggested order

1. Remove the dead parameter/lookup, forwarding method, unused test helpers, and redundant
   normalization calls.
2. Make `BBFluidHandler` delegate finite draining to `NBTUtil`.
3. Consolidate block-capability fluid transactions.
4. Extract cauldron transaction primitives while retaining distinct player/dispenser dispatch.
5. Consolidate Trash Bucket's merge-or-replace operation.
