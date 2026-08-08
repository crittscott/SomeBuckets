# Targeted code review: protection and claims

## Scope

I reviewed the production paths that mutate blocks, fluids, block-entity tanks, mobs, animals, and
item entities, together with the generic claim-provider layer, the FTB Chunks adapter, dispenser
attribution, and the Forge interaction hooks on which other claim mods depend. I also inspected the
protection GameTests to distinguish implemented behavior from tested behavior. I did not read any
other code-review report and made no source changes.

## Findings

### Medium: the offhand-bucket transfer can mutate before later protection listeners veto the click

`NBEvents.onRightClickItem` performs the complete transfer while handling
`PlayerInteractEvent.RightClickItem` at the default `NORMAL` priority
(`event/NBEvents.java:20-46`). This is the special route used when the main hand contains a foreign
fluid container and the offhand contains a Some Buckets container. The handler mutates both held
stacks and can spawn overflow item entities through `Transfers.settle` before event dispatch has
finished (`interaction/Transfers.java:304-338`). It then cancels the event itself.

This ordering does not reliably honor a protection mod that handles the same event later at
`NORMAL`, `LOW`, or `LOWEST`: the transfer has already happened, and cancellation cannot roll it
back. Moreover, once this handler cancels the event, later subscribers that do not opt into
receiving canceled events may not run at all. The normal `BBItem.use` and `SBItem.use` routes do not
have this problem because item use occurs only after Forge finishes posting the cancellable
interaction event.

The immediate claim impact is narrower than a block-edit bypass because the transfer is primarily
inventory-local, but stacked-container settlement can drop item entities into the world at the
player's position. It also contradicts the general claim that ordinary Forge interaction hooks get
an opportunity to veto player operations.

Recommendation: do not finalize this transfer until higher-priority/default protection listeners
have had their chance. If the event remains the necessary interception point, register this handler
at `LOWEST`, leave `receiveCanceled` false, and check that the event is not already canceled before
mutating. Add an ordering test with a later-priority veto listener proving that neither hand nor the
world changes on denial.

### Low: transfer overflow drops bypass the mod's own entity-release permission gate

`Transfers.settle` calls `player.drop(...)` for every result that cannot remain in the hand
(`interaction/Transfers.java:334-338`) without calling `Protections.mayAct`. These are genuine world
entity creations; common examples include using a Source Bucket against a stack of vanilla buckets
or transferring with stacked fluid-container items.

The omission is inconsistent with the project's treatment of equivalent custom drops. Junk/Trash
air ejection checks `ENTITY_RELEASE` at the player's position (`item/JBItem.java:141-150`),
block-targeted ejection checks the resolved adjacent position (`item/JBItem.java:170-182`), and
dispenser ejection checks the front block (`interaction/StorageBucketDispenser.java:66-75`). A
registered provider can therefore forbid those releases but cannot forbid transfer settlement
drops.

This is low severity because the drop is at the acting player's feet and many claim policies allow
ordinary item dropping. It is nevertheless a gap in the internal protection contract, especially
for custom providers or policies that intentionally deny entity release.

Recommendation: before performing any transfer that requires settlement drops, authorize an
`ENTITY_RELEASE` at `player.blockPosition()` using a representative `ItemEntity`, or explicitly
define transfer overflow as ordinary player inventory dropping and make the Junk/Trash policy
consistent with that decision. The authorization must happen before either held stack is mutated.

## What is handled correctly

- `Protections.mayAct` combines vanilla `Level.mayInteract`/`Player.mayUseItemAt` restrictions with
  every registered provider for player block/fluid/release actions (`util/Protections.java:34-44`).
- World fluid pickup checks the source position as `FLUID_EDIT` before invoking the block's pickup
  contract (`fluid/BBFluidLogic.java:120-145`, `fluid/SBFluidLogic.java:126-139`).
- Fluid placement resolves fall-through first and checks the position that will actually change,
  rather than only the clicked block (`fluid/FluidPlacement.java:71-106`). Powder-snow pickup and
  placement do the analogous `BLOCK_EDIT` checks (`fluid/BBFluidLogic.java:269-287`, `310-331`).
- Block-entity tank transfers use `BLOCK_INTERACT` and authorize before executing either handler
  (`fluid/BBFluidLogic.java:176-220`, `fluid/SBFluidLogic.java:183-224`).
- Player and dispenser cauldron mutations are gated before changing the cauldron. Source Bucket,
  Big/Huge Bucket, and dispenser-specific cauldron paths all do this despite their separate
  implementations (`interaction/Cauldrons.java`, `fluid/SBFluidLogic.java`, and
  `interaction/Dispensers.java`).
- Mob capture checks the actual mob as `ENTITY_INTERACT` before snapshotting or discarding it
  (`item/MBItem.java:67-82`). Release checks the resolved destination and reconstructed entity as
  `ENTITY_RELEASE`; aquatic release separately checks `FLUID_EDIT` before placing or waterlogging
  water (`item/MBItem.java:95-121`, `132-159`).
- Junk/Trash absorption and feeding authorize each actual target entity immediately before mutation
  (`item/JBItem.java:240-259`, `285-323`; `item/TBItem.java:166-214`). Player and dispenser paths use
  the same primitives.
- Dispensers carry their source position in `ProtectionContext`, and FTB Chunks sees a stable fake
  player with the bucket copied into its hand (`protection/ProtectionContext.java`,
  `protection/DispenserFakePlayer.java`, `compat/ftbchunks/FtbChunksProtection.java`). The target face
  for dispenser block/tank operations is the face adjacent to the dispenser, not the firing
  direction (`interaction/Dispensers.java:40-45`).
- FTB Chunks denials compose correctly: every registered provider must allow the operation
  (`protection/ClaimProtections.java:29-36`), and the adapter maps fluid edits, block edits, block
  interactions, and entity operations to the corresponding FTB protection categories
  (`compat/ftbchunks/FtbChunksProtection.java:51-56`).
- Player world-fluid paths post `FillBucketEvent` against the same resolved target their mutation
  logic uses, allowing ordinary Forge protection listeners to cancel before mutation
  (`item/BBItem.java:215-226`, `item/SBItem.java:87-110`).

## Test coverage gaps

`ProtectionGameTests` gives good transaction-level coverage for generic-provider denial of a fluid
edit, mob capture, storage absorption, feeding, cauldron interaction, entity release, aquatic water
placement, adventure-mode restrictions, and fall-through target resolution. The suite does not,
however, exercise:

- the actual `FtbChunksProtection` adapter or fake-player policy end to end;
- cancellation ordering around `NBEvents.onRightClickItem`;
- transfer settlement drops and `ENTITY_RELEASE` denial;
- player denial on Big/Source milking, world fluid placement/pickup, or Mob Bucket release (the
  underlying shared gates are covered through other callers, but the public player entry points are
  not);
- denial of Big/Huge player cauldron interactions through the registered `CauldronInteraction` map;
- coexistence with Open Parties and Claims. That integration is intentionally event-based, but no
  test fixture establishes that its player/dispenser hooks surround every custom behavior.

These are integration-risk gaps rather than evidence that the shared checks themselves are wrong.

## Overall assessment

The protection architecture is deliberate and, for direct block/fluid/entity operations, unusually
consistent: checks use the real mutation target, dispenser actions have a stable actor, and compound
aquatic releases require both entity and fluid permission. I found no direct unguarded block, fluid,
mob, feeding, or vacuum mutation. The principal defect is Forge event ordering in the foreign-main-
hand transfer route; the remaining concrete inconsistency is its unchecked overflow drops.
