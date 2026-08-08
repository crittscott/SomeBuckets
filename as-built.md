# Some Buckets As-Built Description

This is an orientation and maintenance guide to the code as it exists. It records the architecture,
persistent state, important contracts, and observable behavior that a maintainer must preserve. It is
not a specification and does not drive the implementation: where this document disagrees with the
code, the code is authoritative and this document should be corrected. See `player-view.md` for the
full player-facing guide.

## Project shape

Some Buckets is an unreleased Minecraft 1.20.1 mod for Forge 47.x and Java 17. Its mod id is
`somebuckets`; its root package is `com.github.crittscott.somebuckets`.

It adds six unstackable items and one creative-mode tab, but no blocks, block entities, menus,
packets, commands, or saved-world objects. All persistent contents are stored on the bucket
`ItemStack`. One Forge server configuration controls which contents Source Buckets may use.

The `gametest/` package and its generated empty structure support development verification. The JAR
task explicitly excludes both the GameTest classes and that structure from the released mod.

| Registry name | Purpose |
| --- | --- |
| `big_bucket_8` | Finite container for 8 bucket/block units |
| `big_bucket_64` | Finite container for 64 bucket/block units |
| `source_bucket` | Infinite source and sink for one allowed content |
| `junk_bucket` | FIFO storage for up to 9 item stacks |
| `trash_bucket` | One-stack storage with destructive replacement |
| `mob_bucket` | FIFO storage for up to 8 mobs of one entity type |

The project does not preserve legacy item formats while unreleased. Schema changes should migrate or
replace development data rather than add compatibility branches.

## Architecture

| Area | Responsibility |
| --- | --- |
| `SomeBuckets`, `register/` | Common entry point, configuration/common setup, deferred item registration, and the creative-mode tab |
| `config/` | Server configuration and Source Bucket policy |
| `item/` | Player interaction and item presentation contracts |
| `util/NBTUtil` | Shared NBT schema, serialization, deserialization, and normalization |
| `fluid/*FluidHandler` | Forge item fluid capabilities |
| `fluid/*FluidLogic` | World, tank, powder-snow, and special fluid operations |
| `fluid/FluidPickup`, `fluid/FluidPlacement` | Shared vanilla-style world pickup and placement for Big/Source Buckets, plus required-water placement for Mob Bucket release |
| `interaction/Cauldrons` | All physical cauldron transitions, bucket mutation, protection, feedback, and player accounting |
| `interaction/Transfers` | Held-item and block-capability transactions, pumping, settlement, and shared fluid feedback |
| `interaction/Dispensers` | Registration and family-specific selection for every dispenser behavior |
| `interaction/FuelHandler` | Big/Huge and allowed Source Bucket lava fuel |
| `protection/` | Permission checks, claim-provider dispatch, bucket-use event, stable dispenser fake player |
| `compat/ftbchunks/` | Optional FTB Chunks provider |
| `crafting/` | Empty-bucket and spawn-egg custom ingredients |
| `client/ClientSetup` | Single client lifecycle owner for predicates, colors, model wiring, and reload listeners |
| Other `client/` classes | Fluid models/colors, Junk rendering, and Mob tinting |
| `gametest/` | Development-only behavioral verification; excluded from the release JAR |
| `resources/` | Recipes, tags, translations, models, and textures |

Storage and operations are deliberately separate: `NBTUtil` owns representation, item classes own
admission and crafting policy and choose an operation, fluid/storage helpers perform it, and
integration classes adapt it to Forge and vanilla entry points. Mutations are server-side; client
calls generally return matching interaction results for hand animation and prediction.

## Persistent item state

### Content modes

`Mode` is a string discriminator. Missing, empty, or unknown values read as `none`.

| Mode | Keys | Used by |
| --- | --- | --- |
| `none` | none | Empty or unassigned content-bearing buckets |
| `fluid` | `FluidStack` compound | Big, Huge, Source |
| `milk` | `Amount` in mB | Big, Huge, Source |
| `powder_snow` | `Powder` block count | Big, Huge |
| `entity` | `EntityType`, `Entities` list | Mob |

Fluid NBT, including payload data, is retained. `NBTUtil.drainFiniteContent` is the authoritative
finite fluid/milk debit operation and normalizes an exhausted bucket to `none`. Other content-removal
paths are likewise responsible for normalization. Public amount and powder writers reject negative
values instead of silently clamping them. Empty Some Buckets keys and then an empty root tag are
removed without disturbing unrelated item NBT.

Junk and Trash Buckets use `JunkItems`, a list of serialized `ItemStack` compounds. Capacity counts
list entries, not individual items. Compatible stacks merge before allocating another entry. Intake
must pass `Item.canFitInsideContainerItems` through `JBItem.canStore`. Junk and Trash Buckets opt out
of container storage; Big, Huge, Source, and Mob Buckets do not.

Mob Buckets store one `EntityType` and entity snapshots made with `saveWithoutId`. On release, saved
state and UUID are restored. If the UUID is already used by a loaded entity in any server level, a new
UUID is assigned.

### Fluid capability

Big, Huge, and Source Buckets always expose one `IFluidHandlerItem` tank. It reports contents only in
`fluid` mode; milk and powder snow are outside Forge's fluid system.

- Big and Huge Buckets are ordinary finite tanks of 8,000 and 64,000 mB. Capability transfers may be
  smaller than a bucket; world operations use exactly 1,000 mB.
- Source Buckets report a 1,000 mB tank. The first allowed fill assigns it. Compatible later fills
  succeed without changing it, and drains supply fluid without reducing it.
- Assigned tanks accept only compatible `FluidStack`s. Source Bucket operations additionally require
  the current content to be allowed by `SBPolicy`.
- Every handler reports exactly one tank (`getTanks() == 1`). Any index other than `0` returns empty,
  `false`, or `0` rather than aliasing tank 0's real contents.

## Cross-cutting contracts

### Source Bucket policy

`serverconfig/somebuckets-server.toml` contains `sourceBucket.allowedContents`. The default is
`minecraft:water`, `minecraft:lava`, and the special non-fluid id `somebuckets:milk`.

Policy is checked at every Source Bucket input and output boundary, not only at assignment. Removing
an assigned content from the allowlist leaves its NBT and name intact but makes the bucket inert until
reset. The policy does not restrict Big or Huge Buckets. Config load and reload events resolve the
list into an immutable snapshot, so boundary checks do not repeatedly parse ids or query the fluid
registry. Unknown ids are ignored and logged once per load or reload with the config filename.

### Protection

Protected mutations use a `ProtectionContext`, exact target, and action: fluid edit, block edit,
block interaction, entity interaction, or entity release. Player block changes and release targets
also pass vanilla `mayInteract`/`mayUseItemAt`; all registered claim providers must allow an action.
Checks occur after feasibility is established and before mutation.

A player context requires the real player and the caller's actual interaction hand. Dispenser
contexts carry the dispenser source position; deliberately unattributed automation must use an
explicit unowned-automation context. A missing player is never implicitly converted into automation.

FTB Chunks support is registered only when that mod is loaded. Dispensers use a stable fake player
named `[SomeBuckets]` at the dispenser; they do not impersonate its owner. `protection/DispenserFakePlayer`
is the single canonical source of that identity, used unconditionally (not just when FTB Chunks is
loaded): `FtbChunksProtection` uses it for its own permission check, and `JBItem.feedAnimal` uses it to
actually drive automated animal feeding (see "Item behavior summary" below), so both see the same
player. There is no dedicated Open Parties and Claims adapter in this repository; any compatibility
with it comes from its ordinary Forge hooks and dispenser wrapper. A denial from either that external
integration or a registered Some Buckets provider wins.

Player Junk/Trash collection, feeding, and ejection call Some Buckets' claim-provider layer through
`Protections.mayAct`, the same as the corresponding dispenser paths, and Forge's ordinary interaction
events can still cancel them independently. Vanilla spawn protection is not applied to dispensers.
Overflow stacks produced while settling a hand-to-hand fluid transfer are ordinary player inventory
drops through `Player.drop`, not a Some Buckets `ENTITY_RELEASE`. They therefore remain outside the
mod's internal claim-provider layer; unlike Junk/Trash ejection, creating a world item is incidental
to rebuilding legal held stacks rather than the purpose of the gesture.

When the foreign fluid container is in the main hand and a Big/Huge/Source Bucket is in the offhand,
`NBEvents` supplies the otherwise-unreachable held-transfer route. It listens at `LOWEST`, does not
receive canceled right-click-item events, rechecks cancellation before mutation, and cancels only
after an air-click transfer succeeds. Earlier Forge listeners therefore retain their veto.

The ray-traced Big/Source world-fluid paths and Big powder-snow pickup post `FillBucketEvent` exactly
once per supported block-hit interaction,
at the position that will actually be mutated — resolved the same way the item's own take/place
dispatch resolves it (`BBFluidLogic.canAttemptTakeAt`/`canAttemptTakePowderAt`/`resolvePlaceTarget`,
`SBFluidLogic.resolvePlaceTarget`, `FluidPlacement.resolveTarget`), so a partial Big Bucket's
take-vs-place choice and any fall-through-to-neighbor placement are both reflected in the posted
target rather than guessed from capacity alone. Cancellation fails the operation. `DEFAULT` and a
non-canceling `DENY` continue the bucket's own dispatch, matching Forge's bucket helper. `ALLOW`
short-circuits because the listener has handled the operation. In survival, its supplied result is
accepted only when it is one instance of the exact input item, preserving the multi-unit bucket and
its tier; an incompatible result fails without running the bucket's mutation logic. Creative-mode
`ALLOW` retains the original stack, as Forge does. Big Bucket powder-snow output does not post this
bucket event: it runs the powder-snow block item's placement checks inside Forge's captured
`EntityPlaceEvent` transaction instead. Forge block-capability transfers likewise do not post it;
they use their shared simulate/authorize/execute transaction and `BLOCK_INTERACT` protection check.
Cauldron-map interactions (Big/Huge only; Source has no `CauldronInteraction` registration) use their
normal block interaction path and don't post this event, matching vanilla's own cauldron interactions.
The BB map adapters, Source Bucket dispatch, and dispenser selection all delegate the physical
transition to `Cauldrons`. `SBItem` has no `useOn` override — every Source Bucket block interaction
routes through `use()`, while capability presence is recognized there so the capability transaction
is not misreported as a vanilla-style bucket event.

### Game events

A successful fluid pickup/placement, powder-snow pickup, or cauldron transfer posts
`GameEvent.FLUID_PICKUP` or `GameEvent.FLUID_PLACE` at the position actually changed. Powder-snow
output instead posts `GameEvent.BLOCK_PLACE` with the placed powder-snow state, matching block-item
placement. World paths attribute the event to the acting player and cauldron paths attribute it to no
entity, matching `BucketItem`, `BlockItem`, and `CauldronInteraction` respectively. Capability paths
attribute it to the `ProtectionContext` player, or to no entity for dispenser/automation calls.
Ultra-warm evaporation posts nothing, as in vanilla.

Mob Bucket release delegates required water to `FluidPlacement` at an explicit hit with face
fall-through disabled. It therefore posts `GameEvent.FLUID_PLACE` and plays the water-placement sound
only when it actually creates or waterlogs water, not when the target already holds water.
`MBItem.releaseOldest` posts `GameEvent.ENTITY_PLACE` once `addFreshEntity` succeeds. Both attribute
to the acting `ProtectionContext`'s player, `null` for dispenser releases. In an ultra-warm dimension,
the delegated water unit evaporates with vanilla feedback; the aquatic mob is still inserted and its
snapshot removed if insertion succeeds, matching bucket-of-fish ordering without persistent water.

### Vanilla criteria and statistics

Big, Huge, and Source Bucket interactions award the same `Stats` vanilla buckets and cauldrons would:
`Stats.ITEM_USED` on every successful fluid/powder-snow pickup or placement (world, cauldron, or tank),
and `Stats.USE_CAULDRON` alongside it on every cauldron interaction, in both directions
(`interaction/Cauldrons.java` owns the physical transition and accounting for Big, Huge, and Source
callers). `CriteriaTriggers.FILLED_BUCKET` fires on genuine pickups only — world fluid, cauldron, and
powder-snow — mirroring where vanilla's own `BucketItem.use()` fires it; it is deliberately not fired
on the Forge-capability tank-drain path (`Transfers.tryTakeFromBlock`), since vanilla
buckets have no equivalent capability-mediated interaction to mirror there.
`CriteriaTriggers.PLACED_BLOCK` fires for successful player powder-snow output through the native
block-item placement primitive; dispenser output has no player and therefore does not fire it.
Successful player Mob Bucket capture fires `CriteriaTriggers.FILLED_BUCKET` after the snapshot is
stored and the live mob is discarded; automation and failed captures do not. Successful player Mob
Bucket release awards one `Stats.ITEM_USED` only after entity insertion succeeds; dispenser release
and every rejected release path award none.
`CriteriaTriggers.CONSUME_ITEM` fires at actual milk consumption (`finishUsingItem`, both Big/Huge and
Source), not at the moment milk is acquired from a cow, matching `MilkBucketItem`'s own timing. Both
items trigger the criterion and award the stat against the still-milk-filled stack before any NBT
mutation, matching vanilla's own ordering; Big/Huge Buckets drain and normalize the stack only
afterward, inside the server-only branch, so a final-unit sip is never reported against an
already-emptied bucket.

## Item behavior summary

### Big and Huge Buckets

They hold one content type at a time. Empty buckets take; full buckets place; partial buckets try to
take compatible content before placing. Supported inputs and outputs are source fluids, powder-snow
blocks, full/empty vanilla cauldrons, Forge block fluid tanks, and adult cows for milk. Tank transfers
are all-or-nothing for player/dispenser world clicks. A fluid without a placeable world block can
still be transported between capabilities but cannot be poured into the world. Tank-transfer fill and
drain feedback plays the fluid's own registered `BUCKET_FILL`/`BUCKET_EMPTY` sound when it defines
one, falling back to vanilla water/lava sounds only when it does not — the same resolution the world
pickup/placement and held-item transfer paths use. Source Bucket tank transfers follow the same sound
resolution. Successful dispenser milking uses `COW_MILK`, matching the player milking path.

World fluid pickup goes through the block that owns the fluid: Forge's `IFluidBlock` wrapper, or
otherwise the `BucketPickup` contract. A waterlogged block therefore keeps itself and gives up only
its water, and a block that refuses pickup keeps its fluid. `FluidPickup` excludes flowing fluid
itself, because a `BucketPickup` block reports its whole fluid state when asked what it holds.
What one unit would yield is established before the bucket, protection, or the world is touched, and
that exact content is then requested, so a block handing back something else is refused rather than
stored.

World fluid placement follows vanilla target selection, waterlogging, replaceable-block drops, and
ultra-warm evaporation. Player placement may fall through to the neighboring block along the clicked
face; dispenser placement is restricted to the block directly in front.

Milk is drunk one unit at a time and clears effects. Sneak-right-clicking air clears all contents.
A lava-filled bucket supplies 20,000 ticks of furnace fuel per unit. Names, tooltips, and a tinted bar
show content and fullness.

### Source Bucket

An empty Source Bucket is assigned by an allowed fluid source, compatible tank, water/lava cauldron,
cow, or held-item transfer. It then supplies and accepts that content indefinitely. Milk can be drunk
indefinitely; powder snow is unsupported. Sneak-right-clicking air resets the assignment.

When a clicked block exposes a fluid handler on that face, `SBFluidLogic` transfers with it directly
(`SBItem.use` has no separate capability pre-check to defer to); a block whose own right-click
behavior claims the interaction first — opening a GUI, for instance — still gets that first chance,
since block interaction is resolved before the item's `use` is reached. Otherwise the bucket's own
logic may perform a 1,000 mB transfer. Allowed lava is permanent 20,000-tick furnace fuel.

### Junk and Trash Buckets

Junk holds nine FIFO stack entries. It absorbs nearby eligible dropped items, supports inventory
secondary-click insertion/extraction, ejects the oldest stack on sneak-use against a block or on
sneak-use against air, and feeds animals from stored food. Fresh drops still under pickup delay are
ignored.

`JBItem.trySneakEject`, shared by Junk and Trash, throws the oldest stack via `Player.drop(stack,
false, true)` — the same call vanilla's drop-item key makes — so a sneaking player can eject without
a block to target. It checks `ProtectionAction.ENTITY_RELEASE` against the player's own block
position with a throwaway `ItemEntity` probe, the same shape `JBItem.useOn`'s block-targeted eject
uses. `use()` on both items checks `isShiftKeyDown()` first and routes to this helper before any
vacuum/absorb logic runs, so a sneaking click never also vacuums.

Feeding (`JBItem.feedAnimal`) does not compute breeding or growth itself: it hands the animal a
one-count copy of the stored food through a real `Mob.interact(Player, InteractionHand)` call, so
vanilla — including any species-specific growth-rate override — decides the outcome and applies its own
rate. A real feeding player drives this directly; automated (dispenser) feeding uses the shared
`protection/DispenserFakePlayer` identity instead. Whether the food is actually consumed is read back
from vanilla's own result rather than tracked separately, so a creative-mode feeder's stored food is
preserved for the same reason a creative player's held food item would be.

Trash uses the same interaction model with one entry. A compatible incoming stack merges only if the
whole stack fits; otherwise the old contents are destroyed and replaced with at most one legal stack.
If the incoming stack is oversized, the excess remains in its original slot, cursor, or item entity.
Its world use processes one eligible item entity per click. Both the player and dispenser paths locate
that entity with
`TBItem.findFirstNearby`, a `Level.getEntities(..., maxResults=1)` query that aborts the world scan at
the first match rather than collecting every eligible entity in range and discarding all but one, so
repeatedly clearing a large pile one entity per interaction does not cost quadratic work.

### Mob Bucket

Mob Buckets capture eligible `Mob` instances and restrict later captures to the same exact
`EntityType`. Players, non-`Mob` entities, passengers/vehicles, and entities in
`somebuckets:mb_blacklist` are rejected; the shipped tag contains the Ender Dragon and Wither.

Release recreates the oldest snapshot at the adjacent position and removes it from the bucket only
after `addFreshEntity` succeeds. Collision failure leaves it stored. `Bucketable` or water-type mobs
delegate exact-target waterlogging, replacement, and ultra-warm evaporation to `FluidPlacement`;
water committed before a final spawn rejection is not rolled back. See "Game events" above for the
fluid- and entity-placement events and water-placement sound this emits.

## Transfers and automation

Right-clicking air transfers between a Big, Huge, or Source Bucket and a fluid-capable item in the
other hand. A targeted block takes precedence. Partners are discovered through
`ForgeCapabilities.FLUID_HANDLER_ITEM`; milk has a separate vanilla milk-bucket path.

Ordinary finite hand transfers use Forge's `FluidUtil.tryFluidTransfer`, which simulates source and
destination and then executes one transfer bounded by what the pair accepts. Source Buckets fill
finite containers without loss and act as infinite sinks when filled. An assigned Source Bucket acting
as the source fills the destination to its full reported capacity in a single simulate/execute round
(`Transfers.pumpUnlimited`), rather than stepping through the public capability's 1,000 mB-per-call
limit — that limit stays in place for machines/pipes talking to the item's `IFluidHandlerItem`
directly, since `pumpUnlimited` only special-cases the hand-to-hand path and never calls the public
`drain` for more than a 1-unit liveness/policy probe. Results are rebuilt into legal stacks: the hand
keeps one useful stack and other results are dropped at the player's feet. Single-item bucket stacks
are mutated in place because their handlers retain that stack reference.

All six items have custom dispenser behavior and remain in the dispenser:

- Big/Source Buckets operate on the front block; dispenser selection is separate from player maps,
  but both delegate physical cauldron transitions to `Cauldrons`.
  The synthetic hit built for that block targets its face adjacent to the dispenser — the opposite
  of the dispenser's own facing — so sided block-entity fluid-handler lookups and the protection
  checks built from that hit see the same face a player standing at the dispenser would contact.
- Big Buckets collect world powder snow only while empty and do not fill empty cauldrons with powder
  snow from a dispenser.
- Source Buckets can milk a cow in front when empty and milk is allowed.
- Junk/Trash first feed one animal, then collect items, then eject only when no input target blocks
  output. Junk collects everything it can fit; `Dispensers.StorageBehavior` gives Trash the same
  single-result `TBItem.findFirstNearby` lookup the player path uses, rather than the full-list scan
  it still performs for Junk.
- Mob Buckets capture a compatible mob first. Any remaining `Mob` in front blocks release; an empty
  front lets the bucket release its oldest mob.

## Crafting and data

| Result | Recipe |
| --- | --- |
| Big | Eight vanilla buckets in a ring |
| Huge | Eight empty Big Buckets in a ring |
| Junk | Chest with iron on the left, right, and below |
| Trash | Junk Bucket, enderman spawn egg, ender eye |
| Source | Trash Bucket, netherite block |
| Mob | Empty Source Bucket, any `SpawnEggItem` |

`somebuckets:empty_bucket` rejects filled Big/Source ingredients whose crafting remainder would
otherwise preserve them. `somebuckets:spawn_egg` accepts vanilla and standard Forge modded spawn eggs.
Junk, Trash, and Mob Buckets have no crafting remainder. Big/Huge return themselves with one unit
removed; Source returns itself unchanged. Empty Big/Source Buckets have no remainder.

`work/` is retained source/reference material and is not a runtime resource root. Runtime resources
come from `src/main/resources` and configured generated-resource roots. Development resource
processing also materializes the GameTest structure under `build/`; the release JAR excludes that
structure and the `gametest/` classes.

## Client presentation

`somebuckets:bb_content` reports empty (`0`), fluid (`0.1`), milk (`0.2`), or powder snow (`0.3`) for
Big, Huge, and Source Buckets. Empty and fluid both use the base dynamic fluid-container model; the
shipped Big/Huge JSON adds overrides for milk and powder snow, while Source adds only milk. Forge-fluid
models delegate sprite selection to Forge's dynamic fluid-container model, then resolve the stack's
own still texture and apply the stack-aware fluid tint, so NBT-defined fluid variants — texture and
color alike — render distinctly. Big/Huge bars derive their base color from the still texture and
multiply it by the runtime tint.

`somebuckets:filled` reports `0` for an empty Mob Bucket and `1` once it stores any snapshot, selecting
the spawn-egg-tinted filled model. Big, Huge, and Source dynamic names derive their translation-key
base from the registered item's description ID and append only the applicable content suffix.

The Junk Bucket uses a custom renderer but delegates every stored stack to Minecraft's `ItemRenderer`,
preserving layers, tint, glint, render passes, overrides, and custom renderers. Stable transforms place
the oldest stack in front, and a mask derived from `junk_bucket_opening.png` redraws the vessel outside
the mouth. Trash uses a conventional layered model tinted black. Mob uses its entity's spawn-egg
colors, falling back to gray.

## Maintenance invariants and boundaries

- Every new content-removal path must normalize an empty state.
- Finite fluid and milk debits must use `NBTUtil.drainFiniteContent`; amount and powder writers must
  never be called with negative values.
- World blocks give up and receive fluid through their own contracts; never remove a fluid by
  setting its block to air, which destroys waterlogged blocks and ignores blocks that refuse pickup.
- Every new world or cauldron mutation must post the matching fluid game event at the changed
  position.
- Every new fluid/powder-snow pickup or cauldron interaction should award the matching vanilla `Stats`
  entry, and a genuine pickup (not a Forge-capability tank drain) should fire
  `CriteriaTriggers.FILLED_BUCKET`; milk-drinking fires `CONSUME_ITEM` at actual consumption, not at
  acquisition.
- Every new Source Bucket boundary must consult `SBPolicy`.
- Every new mutation must check its actual target with the correct protection action and context.
- Player protection contexts must carry the known real hand. Automation must be represented by an
  explicit dispenser or unowned-automation context, never inferred from a missing player.
- `ClaimProtections.PROVIDERS` is a plain `List`, not a concurrent collection: registration happens
  once at mod setup (or for the duration of a GameTest, via the `Registration` token) and reads
  happen from server-thread interaction handling, all single-threaded. Introducing concurrent
  registration or lookup would need to revisit that.
- A new player world-use branch must resolve its `FillBucketEvent` target the same way it resolves
  its own take/place action (reusing the same peek methods, not a second hand-derived guess), so the
  event never names a different block than the one that changes.
- `BBFluidLogic.tryTakeWithContext` does not call `canAttemptTakeAt` as an internal preflight: the
  shared block-capability transaction and world-pickup branch already perform that same simulate/
  mode/capacity check immediately before mutating, per the Forge transaction pattern. Don't
  reintroduce that call inside `tryTakeWithContext` as a guard; `canAttemptTakeAt` remains a read-only
  peek used solely for world `FillBucketEvent` target resolution after capability dispatch is excluded.
- Dispenser synthetic hits must target the block face adjacent to the dispenser, not the dispenser's
  own firing direction, so sided fluid-handler capability lookups and protection checks see the
  correct face.
- BB/SB fluid dispatch must obtain the mod bucket's own item capability through
  `Transfers.requireBucketHandler`. Its absence is an invariant violation, not a signal to fall back
  from a block-capability path to world-fluid handling.
- Single-tank fluid handlers must reject any tank index other than 0 (empty, `false`, or `0`) rather
  than aliasing tank 0.
- Big-Bucket-only paths (`BBFluidLogic`, `Cauldrons`, `BBFluidHandler`) cast their stack straight to
  `BBItem` rather than falling back to a guessed capacity. The private dispenser family behaviors
  require the matching BB/SB/MB/JB item type and throw on a mismatched registration rather than
  falling through to another family or substituting a guessed value. The mod's own dispenser and
  `CauldronInteraction` registrations guarantee the item at those call sites. The same trust applies to
  `MBItem.capture`/`releaseOldest` (a live mob's registered `EntityType`, a stored snapshot's resolved
  type, and that type's entity factory are all trusted rather than defensively null-checked): let a
  violation throw instead of silently failing the capture or release. `MBItem.releaseOldest` takes
  `ServerLevel` directly rather than checking `instanceof ServerLevel`, since Mob Bucket release only
  ever happens from an already-server-side call site; a new release call site must resolve or cast to
  `ServerLevel` itself before calling it.
- Tank-transfer fill/drain feedback must resolve the fluid's own `BUCKET_FILL`/`BUCKET_EMPTY` sound
  action, falling back to vanilla water/lava sounds only when the fluid type defines none.
- Every new Junk/Trash intake path must call `JBItem.canStore`, including direct replacement paths.
- Trash replacement must preserve any incoming amount beyond one legal stored stack at the original
  slot, cursor, or item entity.
- Player, Source Bucket, and dispenser cauldron selection remain separate, while every physical
  transition must stay in `Cauldrons`.
- Mob capture/release primitives are shared, but dispenser capture-first and vacancy rules are local.
- Any new Trash Bucket entity-lookup path (player or dispenser) must use `TBItem.findFirstNearby`
  rather than a full `getEntitiesOfClass` scan: Trash only ever consumes one entity per interaction,
  and collecting every eligible entity in range just to discard all but one reintroduces quadratic
  cost against a large pile.
- Any new Mob Bucket release path must delegate required water to `FluidPlacement` with an explicit
  hit and the caller's exact-target fall-through policy, then post `ENTITY_PLACE` only after the mob
  is accepted. An already-wet target must not produce redundant water feedback.
- Fluid model recoloring depends on Forge's delegate using the still sprite for its fluid layer and
  honoring nested overrides, and must resolve that still texture from the actual stack (not just the
  fluid type) so NBT-dependent fluids recolor their real variant.
- Junk rendering must continue to delegate child stacks to `ItemRenderer`; sprite approximations lose
  custom, layered, and multi-pass rendering.
- `ClientModelLoaders`/`JBRenderer` trust that the mod's own `junk_bucket` model always bakes
  (every registered item gets a baked-model entry) and no longer guard against a missing vessel model;
  a genuinely broken bake now fails at render time instead of silently drawing nothing.
- Client resource reads (`BucketMouth`, `ClientFluidColors`) catch only `IOException`, the genuinely
  external failure a missing or corrupt resource-pack texture can produce. Don't widen that to
  `RuntimeException` or `Throwable`; doing so hides real bugs in the surrounding code behind a silent
  blank render instead of a broken pack.
- Quad- or behavior-changing `BakedModelWrapper`s must override both quad forms as needed and return
  themselves from transforms so inherited behavior does not discard the wrapper.
- Transfer settlement must preserve in-place mutation for single-item container stacks.
- The foreign-main-hand/offhand-Some-Buckets transfer bridge must remain a non-canceled `LOWEST`
  right-click-item listener and cancel only after a successful air transfer.
- `Transfers.pumpUnlimited`'s capability bypass is scoped to the hand-to-hand path only; the public
  `IFluidHandlerItem` a Source Bucket exposes to machines must keep its 1,000 mB-per-call limit.
- `Transfers.pump` delegates ordinary finite transfer to Forge's `FluidUtil.tryFluidTransfer`, using
  one simulate/execute round rather than looping call-by-call against a foreign handler.
- `ClientSetup` remains the single client lifecycle subscriber. The Java property identifiers and
  values must stay synchronized with the shipped `bb_content` and `filled` model predicates.
- The implementation has one server config and no networking, JEI integration, or loot tables.
- `src/TODO.txt` is exploratory and may be stale; runtime code is authoritative.
