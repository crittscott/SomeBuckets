# Targeted code review: Minecraft/Forge reinvention and observable hooks

## Scope

I reviewed the production code for places where it reimplements Minecraft or Forge behavior, where
a platform facility already exists, and where a custom action should reproduce the observable
contract of the vanilla action it resembles. That includes Forge events and capabilities, vanilla
game events, advancement criteria, statistics, sounds, dispenser behavior, cauldron integration,
entity creation/removal, and item-container interactions. I did not read any other code-review
report and made no production-code changes.

## Findings

### High: Big Bucket powder-snow placement bypasses the native block-item placement pipeline

World placement of stored powder snow is implemented as a ray-traced `Item.use` branch followed by
a direct `Level.setBlock` (`item/BBItem.java:228-236`, `fluid/BBFluidLogic.java:297-331`). Vanilla's
powder-snow bucket is a block-placement item. Its normal `ItemStack.useOn`/`BlockItem` route supplies
more than the final block write:

- Forge surrounds item-on-block placement with its block snapshot/place-event pipeline, including
  `BlockEvent.EntityPlaceEvent` compatibility.
- Minecraft fires `CriteriaTriggers.PLACED_BLOCK` for a server player.
- The placement emits `GameEvent.BLOCK_PLACE` with the placed state as context.

The custom path supplies none of the first two and emits `GameEvent.FLUID_PLACE` instead
(`BBFluidLogic.java:329-331`). It does post the mod's manually constructed `FillBucketEvent`, but that
is the fluid-bucket contract; it is not the block-placement contract used by the vanilla
powder-snow bucket. Consequently, block-placement listeners, advancements, and vibration listeners
can observe a Big Bucket differently from the standard item whose operation it is copying.

Recommendation: treat powder-snow placement as an item-on-block placement operation and preserve
the native block-placement hooks. If the multi-unit stack prevents delegating the whole transaction
to `PowderSnowBucketItem`, keep only the custom inventory debit and explicitly route the world write
through an equivalent placement-context/event contract. On success, fire `PLACED_BLOCK` and
`BLOCK_PLACE`, not the fluid-placement hook. Add tests for Forge place-event cancellation, the
placed-block criterion, and the exact game-event type.

### High: aquatic Mob Bucket release reimplements bucket placement and diverges from it

`MBItem.placeWaterFor` independently implements waterlogging, replaceable-block destruction, water
placement, sound, and game-event emission (`item/MBItem.java:91-121`). The project already has a
shared vanilla-style implementation in `FluidPlacement`, and Minecraft's native mob buckets use the
bucket placement path.

This duplication causes concrete incompatibilities:

- It does not implement ultra-warm evaporation. In an ultra-warm dimension it directly installs a
  water block at lines 117-118 instead of producing vanilla's hiss/smoke result.
- A player release does not post `FillBucketEvent` for the required water mutation. Ordinary Forge
  bucket listeners therefore cannot veto or replace that part of the operation; only the outer
  right-click-block event and this mod's private claim-provider check see it.
- Successful release never awards `Stats.ITEM_USED` for the Mob Bucket. Vanilla bucket placement
  does.

The path does correctly emit `FLUID_PLACE` only when it actually adds water and `ENTITY_PLACE` only
after `addFreshEntity` succeeds (`MBItem.java:110-120`, `154-155`). The problem is the larger native
bucket contract around those writes.

Recommendation: reuse the shared bucket-placement primitive for required water, including
ultra-warm behavior, and post the Forge bucket event at the resolved water target before mutation.
Award the Mob Bucket's item-used statistic once a player release succeeds. Add an ultra-warm aquatic
release test and a canceled-`FillBucketEvent` test.

### Medium: Mob capture omits the vanilla filled-bucket criterion

`MBItem.capture` snapshots and removes the mob but does not fire `CriteriaTriggers.FILLED_BUCKET`
(`item/MBItem.java:67-82`). Minecraft's `Bucketable.bucketMobPickup` fires that criterion after
successfully replacing an empty bucket with a filled mob bucket. The Some Buckets item is expressly
a multi-mob generalization of that operation, and datapack authors should be able to observe a
successful fill using the same criterion.

The outer entity-interaction Forge event still runs for a player, and dispenser capture correctly
has no player criterion. This omission concerns successful player capture only.

Recommendation: after the server-side bucket snapshot has been committed, trigger
`FILLED_BUCKET` for a `ServerPlayer` with the now-filled Mob Bucket stack. Add a criterion-listener
test for player capture and a negative test for dispenser capture.

### Medium: `FillBucketEvent` is posted manually but its `ALLOW` contract is deliberately ignored

`Protections.onBucketUse` constructs and posts `FillBucketEvent` directly rather than using Forge's
bucket-use helper (`util/Protections.java:49-66`). Cancellation is honored, but `ALLOW` is explicitly
treated as `DEFAULT`: the listener-supplied filled result is ignored and the mod attempts its own
operation. `FillBucketEventGameTests.allow_result_does_not_fake_success` locks this behavior in.

That is not the platform event contract. A listener uses `ALLOW` and `getFilledBucket()` to provide
the result of a bucket interaction that vanilla/Forge would otherwise not understand. With the
current code, an `ALLOW` listener cannot make an unsupported pickup succeed, and a listener's chosen
result can never win. Posting the standard event while implementing only its cancellation half gives
integrators a misleading signal.

The multi-unit NBT state explains why blindly replacing the held stack with a one-unit result is not
acceptable, but it does not make silently redefining `ALLOW` compatible.

Recommendation: either honor a compatible listener-supplied result through Forge's normal helper,
or define and document a separate cancellable permission hook for the multi-unit transaction while
reserving `FillBucketEvent` for cases where its result contract can be honored. At minimum, do not
describe the current `ALLOW` behavior as vanilla/Forge parity.

### Low: block-capability fluid transfers omit fluid game events

Big and Source Bucket transfers against a block entity correctly simulate, authorize, execute,
play the fluid's registered bucket sound, and award `Stats.ITEM_USED` to a player
(`fluid/BBFluidLogic.java:176-220`, `fluid/SBFluidLogic.java:183-224`). They do not emit
`GameEvent.FLUID_PICKUP` after draining a world tank or `GameEvent.FLUID_PLACE` after filling one.

The capability API does not emit these game events on the bucket's behalf, and arbitrary tank
implementations are not required to do so. The result is that sculk/vibration listeners observe the
same bucket taking from a fluid block or cauldron but not taking from a tank, even though this mod
provides equivalent sound and item-use feedback for all three.

Recommendation: emit the corresponding fluid game event at the block position after a successful
server-side execute, attributed to `context.player()` (null for automation). Test both directions
with a vibration listener. Do not add a filled-bucket criterion to capability drains; unlike the
game event, that omission already matches Forge container-transfer behavior rather than a vanilla
world pickup.

### Low: several custom transfer/milking sounds bypass the registered or vanilla sound

`Transfers` always plays generic `BUCKET_FILL` or `BUCKET_EMPTY` after a Forge-fluid transfer
(`interaction/Transfers.java:115-121`, `146-152`, `378-380`). A lava or modded fluid therefore loses
the `SoundActions.BUCKET_FILL`/`BUCKET_EMPTY` sound that the same bucket correctly resolves for
world and tank transfers. This duplicates and then undercuts the sound-resolution helpers already
present in `BBFluidLogic`, `SBFluidLogic`, `FluidPickup`, and `FluidPlacement`.

Separately, automated Source Bucket milking plays `BUCKET_FILL` rather than the vanilla cow-milking
sound (`fluid/SBFluidLogic.java:316-330`), while the player path correctly uses `COW_MILK`
(`item/SBItem.java:124-143`).

Recommendation: resolve transfer feedback from the actual moved fluid's `FluidType` sound action,
with the same lava/water fallback used elsewhere, and use `COW_MILK` for successful dispenser
milking. Centralize that sound resolution so the world, tank, hand-transfer, and dispenser paths do
not drift again.

### Low: finite hand-to-hand pumping duplicates Forge's transfer helper

`Transfers.pump` manually computes destination capacity and implements the standard
simulate-source, simulate-destination, execute-source, execute-destination transaction
(`interaction/Transfers.java:155-181`). Forge already supplies the finite handler-to-handler fluid
transfer facility for this pattern. Keeping the local copy makes foreign-handler compatibility
dependent on this mod continuing to match Forge's transaction semantics.

`pumpUnlimited` is a legitimate custom primitive: an infinite Source Bucket intentionally bypasses
its public one-bucket-per-call capability limit during direct hand transfer. Stack settlement is also
custom because one held stack can fan out into filled and untouched results. Neither requires the
ordinary finite pump itself to be duplicated.

Recommendation: use Forge's handler-transfer helper for `pump` and retain only `pumpUnlimited` and
the stack-settlement policy locally. The block-entity one-unit paths can be evaluated separately;
their all-or-nothing rule and protection boundary may justify a thin wrapper around the Forge
helper.

## Platform facilities used well

- Fluid block pickup delegates to Forge's `FluidBlockWrapper` and
  `BucketPickupHandlerWrapper`, preserving `IFluidBlock`/`BucketPickup` behavior, waterlogging, and
  refusal (`fluid/FluidPickup.java`).
- Big and Source Buckets expose `IFluidHandlerItem` through `ForgeCapabilities.FLUID_HANDLER_ITEM`
  rather than inventing a machine API (`fluid/AbstractFluidHandler.java`, `FluidProvider.java`).
- Player world-fluid interactions post `FillBucketEvent` once at the resolved mutation target, and
  cancellation happens before the world or item is changed. The target-resolution work in
  `BBItem`/`SBItem` is careful and necessary for partial and fall-through behavior.
- Big/Huge player cauldron behavior is registered in Minecraft's `CauldronInteraction` maps and
  reproduces `USE_CAULDRON`, `ITEM_USED`, filled-bucket criteria on pickup, fluid game events, and
  vanilla sounds (`interaction/Cauldrons.java`). Source Bucket's separately routed cauldron code also
  reproduces those observable hooks, although registering Source handlers in the same maps would be
  more idiomatic and reduce bespoke dispatch.
- Fluid placement uses `LiquidBlockContainer`, fluid replaceability, fluid-type sound actions, and
  vanilla ultra-warm behavior rather than assuming water/lava blocks (`fluid/FluidPlacement.java`).
- Junk/Trash feeding calls the animal's real `interact` method with a one-item hand, so vanilla
  species rules, growth amounts, breeding state, consumption, and downstream breeding behavior stay
  authoritative (`item/JBItem.java:279-323`).
- Dispenser item ejection uses `DefaultDispenseItemBehavior.spawnItem`; automated animal interaction
  uses Forge's `FakePlayerFactory`; custom dispenser behaviors are installed through
  `DispenserBlock.registerBehavior`.
- Furnace integration uses `FurnaceFuelBurnTimeEvent` and crafting remainders instead of modifying
  furnace internals (`interaction/FuelHandler.java`, `item/BBItem.java`, `item/SBItem.java`).
- Milk consumption fires `CONSUME_ITEM` at actual consumption, preserves the filled stack for the
  criterion, and awards `ITEM_USED` (`item/BBItem.java:324-340`, `item/SBItem.java:157-172`).
- Mob release uses `addFreshEntity`, so Forge's normal entity-join event can veto the spawn, and it
  avoids fresh-spawn initialization that would overwrite the stored mob state.

## Test coverage gaps

The GameTests cover state outcomes extensively and include FillBucket cancellation/target tests plus
sculk activation for Mob Bucket release. They do not assert the exact event, criterion, statistic, or
sound contract. In particular, there is no test for:

- Forge block-place cancellation or `PLACED_BLOCK` on Big Bucket powder snow;
- `BLOCK_PLACE` versus `FLUID_PLACE` for powder snow;
- ultra-warm aquatic Mob Bucket release;
- `FillBucketEvent` cancellation during aquatic release;
- `FILLED_BUCKET` on player Mob Bucket capture or `ITEM_USED` on release;
- fluid game events on block-capability transfers;
- fluid-specific hand-transfer sounds or dispenser cow-milking sound;
- a listener using `FillBucketEvent.ALLOW` to supply a result for a pickup the mod cannot perform.

The existing `ALLOW` test proves only that the mod continues its own successful water pickup; it
does not test the behavior for which `ALLOW` exists.

## Overall assessment

The ordinary fluid paths generally use Forge/Minecraft facilities well: capabilities, pickup
wrappers, cauldron maps, native protection events, standard stats/criteria, fluid-type sounds, and
game events are all present. Reinvention becomes problematic at the two type-changing edges:
powder snow is treated as a fluid placement instead of a block-item placement, and aquatic Mob
Bucket release implements its own reduced bucket algorithm. Those paths have observable parity
failures. The manual `FillBucketEvent` semantics are the other material compatibility issue; the
remaining findings are smaller missing hooks or maintainability opportunities.
