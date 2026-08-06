# Some Buckets As-Built Description

This is an orientation and maintenance guide to the code as it exists. It records the architecture,
persistent state, important contracts, and observable behavior that a maintainer must preserve. It is
not a specification and does not drive the implementation: where this document disagrees with the
code, the code is authoritative and this document should be corrected. See `player-view.md` for the
full player-facing guide.

## Project shape

Some Buckets is an unreleased Minecraft 1.20.1 mod for Forge 47.x and Java 17. Its mod id is
`somebuckets`; its root package is `com.github.crittscott.somebuckets`.

It adds six unstackable items and no blocks, block entities, menus, packets, commands, or saved-world
objects. All persistent contents are stored on the bucket `ItemStack`. One Forge server configuration
controls which contents Source Buckets may use.

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
| `SomeBuckets`, `register/` | Entry point, deferred registration, lifecycle integration, model predicates |
| `config/` | Server configuration and Source Bucket policy |
| `item/` | Player interaction and item presentation contracts |
| `util/NBTUtil` | Shared NBT schema, serialization, normalization, crafting remainders |
| `fluid/*FluidHandler` | Forge item fluid capabilities |
| `fluid/*FluidLogic` | World, tank, powder-snow, and special fluid operations |
| `fluid/FluidPickup`, `fluid/FluidPlacement` | Shared vanilla-style world pickup and placement for Big and Source Buckets |
| `interaction/` | Transfers, cauldrons, dispensers, and furnace fuel |
| `util/Protections`, `protection/` | Permission checks, claim-provider dispatch, bucket-use event, stable dispenser fake player |
| `compat/ftbchunks/` | Optional FTB Chunks provider |
| `crafting/` | Empty-bucket and spawn-egg custom ingredients |
| `client/` | Fluid models/colors, Junk rendering, Mob tinting |
| `resources/` | Recipes, tags, translations, models, and textures |

Storage and operations are deliberately separate: `NBTUtil` owns representation, item classes choose
an operation, fluid/storage helpers perform it, and integration classes adapt it to Forge and vanilla
entry points. Mutations are server-side; client calls generally return matching interaction results
for hand animation and prediction.

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

Fluid NBT, including payload data, is retained. Zero-valued content is normally normalized to
`none`; callers that remove content are responsible for normalization. Empty Some Buckets keys and
then an empty root tag are removed without disturbing unrelated item NBT.

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
  the current content to be allowed by `SourceBucketPolicy`.
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

FTB Chunks support is registered only when that mod is loaded. Dispensers use a stable fake player
named `[SomeBuckets]` at the dispenser; they do not impersonate its owner. `protection/DispenserFakePlayer`
is the single canonical source of that identity, used unconditionally (not just when FTB Chunks is
loaded): `FtbChunksProtection` uses it for its own permission check, and `JBItem.feedAnimal` uses it to
actually drive automated animal feeding (see "Item behavior summary" below), so both see the same
player. Open Parties and Claims is handled by its ordinary Forge hooks and dispenser wrapper. A denial
from either system wins.

Player Junk/Trash collection, feeding, and ejection do not call Some Buckets' claim-provider layer,
though Forge's ordinary interaction events can still be cancelled. The corresponding dispenser paths
do use the protection layer. Vanilla spawn protection is not applied to dispensers.

The ray-traced Big/Source world paths post `FillBucketEvent`. Cancellation fails the operation;
`ALLOW` ends it as a successful no-op rather than exchanging the stack for the event's result.
Cauldron-map interactions and `SBItem.useOn` use their normal block interaction paths instead.

### Game events

A successful fluid, powder-snow, or cauldron transfer posts `GameEvent.FLUID_PICKUP` or
`GameEvent.FLUID_PLACE` at the position actually changed, so sculk sensors and other vibration
listeners observe these buckets as they do vanilla ones. World paths attribute the event to the
acting player and cauldron paths attribute it to no entity, matching `BucketItem` and
`CauldronInteraction` respectively. Ultra-warm evaporation posts nothing, as in vanilla.

### Vanilla criteria and statistics

Big, Huge, and Source Bucket interactions award the same `Stats` vanilla buckets and cauldrons would:
`Stats.ITEM_USED` on every successful fluid/powder-snow pickup or placement (world, cauldron, or tank),
and `Stats.USE_CAULDRON` alongside it on every cauldron interaction, in both directions
(`interaction/Cauldrons.java` for Big/Huge, the cauldron branches of `fluid/SBFluidLogic.java` for
Source). `CriteriaTriggers.FILLED_BUCKET` fires on genuine pickups only — world fluid, cauldron, and
powder-snow — mirroring where vanilla's own `BucketItem.use()` fires it; it is deliberately not fired
on the Forge-capability tank-drain paths (`tryTransferFromBlock`/`tryTakeFromBlock`), since vanilla
buckets have no equivalent capability-mediated interaction to mirror there.
`CriteriaTriggers.CONSUME_ITEM` fires at actual milk consumption (`finishUsingItem`, both Big/Huge and
Source), not at the moment milk is acquired from a cow, matching `MilkBucketItem`'s own timing.

## Item behavior summary

### Big and Huge Buckets

They hold one content type at a time. Empty buckets take; full buckets place; partial buckets try to
take compatible content before placing. Supported inputs and outputs are source fluids, powder-snow
blocks, full/empty vanilla cauldrons, Forge block fluid tanks, and adult cows for milk. Tank transfers
are all-or-nothing for player/dispenser world clicks. A fluid without a placeable world block can
still be transported between capabilities but cannot be poured into the world. Tank-transfer fill and
drain feedback plays the fluid's own registered `BUCKET_FILL`/`BUCKET_EMPTY` sound when it defines
one, falling back to vanilla water/lava sounds only when it does not — the same resolution the world
pickup/placement paths use. Source Bucket tank transfers follow the same sound resolution.

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

When a clicked block exposes a fluid handler on that face, `SBItem.useOn` returns `PASS` so the block
can open its GUI or operate on the held capability. If the block passes the interaction onward, the
bucket's own logic may perform a 1,000 mB transfer. Allowed lava is permanent 20,000-tick furnace fuel.

### Junk and Trash Buckets

Junk holds nine FIFO stack entries. It absorbs nearby eligible dropped items, supports inventory
secondary-click insertion/extraction, ejects the oldest stack on sneak-use against a block, and feeds
animals from stored food. Fresh drops still under pickup delay are ignored.

Feeding (`JBItem.feedAnimal`) does not compute breeding or growth itself: it hands the animal a
one-count copy of the stored food through a real `Mob.interact(Player, InteractionHand)` call, so
vanilla — including any species-specific growth-rate override — decides the outcome and applies its own
rate. A real feeding player drives this directly; automated (dispenser) feeding uses the shared
`protection/DispenserFakePlayer` identity instead. Whether the food is actually consumed is read back
from vanilla's own result rather than tracked separately, so a creative-mode feeder's stored food is
preserved for the same reason a creative player's held food item would be.

Trash uses the same interaction model with one entry. A compatible incoming stack merges only if the
whole stack fits; otherwise the old contents are destroyed and replaced. Its world use processes one
eligible item entity per click.

### Mob Bucket

Mob Buckets capture eligible `Mob` instances and restrict later captures to the same exact
`EntityType`. Players, non-`Mob` entities, passengers/vehicles, and entities in
`somebuckets:mb_blacklist` are rejected; the shipped tag contains the Ender Dragon and Wither.

Release recreates the oldest snapshot at the adjacent position and removes it from the bucket only
after `addFreshEntity` succeeds. Collision failure leaves it stored. `Bucketable` or water-type mobs
require a waterloggable target or water source; water placed before a final spawn rejection is not
rolled back.

## Transfers and automation

Right-clicking air transfers between a Big, Huge, or Source Bucket and a fluid-capable item in the
other hand. A targeted block takes precedence. Partners are discovered through
`ForgeCapabilities.FLUID_HANDLER_ITEM`; milk has a separate vanilla milk-bucket path.

Transfers simulate before executing and move as much as the pair permits. Source Buckets fill finite
containers without loss and act as infinite sinks when filled. An assigned Source Bucket acting as the
source is filled to the destination's full reported capacity in a single simulate/execute round
(`Transfers.pumpUnlimited`), rather than stepping through the public capability's 1,000 mB-per-call
limit — that limit stays in place for machines/pipes talking to the item's `IFluidHandlerItem`
directly, since `pumpUnlimited` only special-cases the hand-to-hand path and never calls the public
`drain` for more than a 1-unit liveness/policy probe. Results are rebuilt into legal stacks: the hand
keeps one useful stack and other results are dropped at the player's feet. Single-item bucket stacks
are mutated in place because their handlers retain that stack reference.

All six items have custom dispenser behavior and remain in the dispenser:

- Big/Source Buckets operate on the front block; their cauldron logic is separate from player maps.
  The synthetic hit built for that block targets its face adjacent to the dispenser — the opposite
  of the dispenser's own facing — so sided block-entity fluid-handler lookups and the protection
  checks built from that hit see the same face a player standing at the dispenser would contact.
- Big Buckets collect world powder snow only while empty and do not fill empty cauldrons with powder
  snow from a dispenser.
- Source Buckets can milk a cow in front when empty and milk is allowed.
- Junk/Trash first feed one animal, then collect items, then eject only when no input target blocks
  output. Junk collects everything it can fit; Trash processes one entity.
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
come from `src/main/resources`, configured generated resources, and the processed GameTest structure.

## Client presentation

`somebuckets:bb_content` selects empty (`0`), fluid (`0.1`), milk (`0.2`), or powder snow (`0.3`)
models. Forge-fluid models delegate sprite selection to Forge's dynamic fluid-container model, then
resolve the stack's own still texture and apply the stack-aware fluid tint, so NBT-defined fluid
variants — texture and color alike — render distinctly. Big/Huge bars derive their base color from
the still texture and multiply it by the runtime tint.

The Junk Bucket uses a custom renderer but delegates every stored stack to Minecraft's `ItemRenderer`,
preserving layers, tint, glint, render passes, overrides, and custom renderers. Stable transforms place
the oldest stack in front, and a mask derived from `junk_bucket_opening.png` redraws the vessel outside
the mouth. Trash uses a conventional layered model tinted black. Mob uses its entity's spawn-egg
colors, falling back to gray.

## Maintenance invariants and boundaries

- Every new content-removal path must normalize an empty state.
- World blocks give up and receive fluid through their own contracts; never remove a fluid by
  setting its block to air, which destroys waterlogged blocks and ignores blocks that refuse pickup.
- Every new world or cauldron mutation must post the matching fluid game event at the changed
  position.
- Every new fluid/powder-snow pickup or cauldron interaction should award the matching vanilla `Stats`
  entry, and a genuine pickup (not a Forge-capability tank drain) should fire
  `CriteriaTriggers.FILLED_BUCKET`; milk-drinking fires `CONSUME_ITEM` at actual consumption, not at
  acquisition.
- Every new Source Bucket boundary must consult `SourceBucketPolicy`.
- Every new mutation must check its actual target with the correct protection action and context.
- Dispenser synthetic hits must target the block face adjacent to the dispenser, not the dispenser's
  own firing direction, so sided fluid-handler capability lookups and protection checks see the
  correct face.
- Single-tank fluid handlers must reject any tank index other than 0 (empty, `false`, or `0`) rather
  than aliasing tank 0.
- Tank-transfer fill/drain feedback must resolve the fluid's own `BUCKET_FILL`/`BUCKET_EMPTY` sound
  action, falling back to vanilla water/lava sounds only when the fluid type defines none.
- Every new Junk/Trash intake path must call `JBItem.canStore`, including direct replacement paths.
- Player and dispenser cauldron/selection logic are separate; changes must inspect both paths.
- Mob capture/release primitives are shared, but dispenser capture-first and vacancy rules are local.
- Fluid model recoloring depends on Forge's delegate using the still sprite for its fluid layer and
  honoring nested overrides, and must resolve that still texture from the actual stack (not just the
  fluid type) so NBT-dependent fluids recolor their real variant.
- Junk rendering must continue to delegate child stacks to `ItemRenderer`; sprite approximations lose
  custom, layered, and multi-pass rendering.
- Quad- or behavior-changing `BakedModelWrapper`s must override both quad forms as needed and return
  themselves from transforms so inherited behavior does not discard the wrapper.
- Transfer settlement must preserve in-place mutation for single-item container stacks.
- `Transfers.pumpUnlimited`'s capability bypass is scoped to the hand-to-hand path only; the public
  `IFluidHandlerItem` a Source Bucket exposes to machines must keep its 1,000 mB-per-call limit.
- The implementation has one server config and no networking, JEI integration, or loot tables.
- `src/TODO.txt` is exploratory and may be stale; runtime code is authoritative.
