# Some Buckets As-Built Orientation

This is an orientation and maintenance guide to the code as it exists. It identifies the major
subsystems, persistent data, and design boundaries a maintainer should understand before making a
change. It is deliberately incomplete: `player-view.md` describes current player-facing behavior,
and the code is authoritative when either document is wrong.

## Project at a glance

Some Buckets is an unreleased Minecraft 1.20.1 mod for Forge 47.x and Java 17. Its mod id is
`somebuckets`; its root package is `com.github.crittscott.somebuckets`.

It adds six unstackable items and one creative tab. It adds no blocks, block entities, menus,
packets, commands, or saved-world objects. Bucket contents live entirely on the item stack. One
server configuration controls the Source Bucket allowlist.

| Registry name | Role |
| --- | --- |
| `big_bucket_8` | Finite eight-unit fluid, milk, or powder-snow container |
| `big_bucket_64` | Finite sixty-four-unit version |
| `source_bucket` | Infinite source and sink for one allowed content |
| `junk_bucket` | FIFO storage for nine item stacks |
| `trash_bucket` | One-stack storage with destructive replacement |
| `mob_bucket` | FIFO storage for eight mobs of one entity type |

The project does not preserve old development data formats. If an item schema changes before
release, migrate or replace the data instead of adding compatibility branches.

## Code map

| Area | Owns |
| --- | --- |
| `SomeBuckets`, `register/` | Mod setup, registrations, and the creative tab |
| `config/` | Server configuration and Source Bucket policy |
| `item/` | Item interactions, admission rules, crafting policy, names, bars, and tooltips |
| `util/NBTUtil` | Item NBT representation, serialization, and normalization |
| `fluid/*FluidHandler` | Forge item fluid capabilities |
| `fluid/*FluidLogic` | Big/Source Bucket fluid operations |
| `fluid/FluidPickup`, `fluid/FluidPlacement` | Shared vanilla-style world pickup and placement |
| `interaction/Cauldrons` | Physical cauldron transitions and their accounting |
| `interaction/Transfers` | Held-item and block-capability fluid transactions |
| `interaction/Dispensers` | All dispenser behavior registration and selection |
| `interaction/FuelHandler` | Lava fuel behavior |
| `protection/` | Permission contexts, actions, provider dispatch, and dispenser identity |
| `compat/ftbchunks/` | Optional FTB Chunks integration |
| `crafting/` | Custom recipe ingredients |
| `client/` | Models, colors, predicates, and Junk/Mob rendering |
| `gametest/` | Development verification, excluded from the release JAR |
| `resources/` | Recipes, tags, translations, models, and textures |

The intended layering is simple: `NBTUtil` owns representation; item classes choose and authorize an
operation; fluid, storage, and interaction helpers perform it; integration classes adapt those
operations to Forge and vanilla entry points. Mutations are server-side.

## Persistent item state

Content-bearing buckets use a string `Mode` discriminator. Missing or unknown modes read as empty.

| Mode | Additional keys | Items |
| --- | --- | --- |
| `none` | none | Empty or unassigned content buckets |
| `fluid` | `FluidStack` | Big, Huge, Source |
| `milk` | `Amount` | Big, Huge, Source |
| `powder_snow` | `Powder` | Big, Huge |
| `entity` | `EntityType`, `Entities` | Mob |

Fluid stack NBT is preserved, including mod-defined payload data. Finite fluid and milk removal goes
through `NBTUtil.drainFiniteContent`. Empty content must normalize back to `none`, and empty
Some Buckets tags are removed without disturbing unrelated item NBT.

Junk and Trash Buckets store serialized item stacks in `JunkItems`. Capacity counts list entries,
not individual items. Compatible stacks merge before consuming another entry. Every intake route
must enforce `Item.canFitInsideContainerItems` through `JBItem.canStore`.

Mob Buckets store an entity type and snapshots created without the entity id. Release restores the
saved state and UUID, assigning a new UUID only if the old one is already in use by a loaded entity.

Big, Huge, and Source Buckets expose one Forge fluid tank. Big and Huge are finite tanks of 8,000 and
64,000 mB. Source reports a 1,000 mB tank but does not lose its assigned content. Milk and powder
snow are not exposed as Forge fluids. Invalid tank indices are rejected rather than treated as tank
zero.

## Important system boundaries

### Source Bucket policy

`serverconfig/somebuckets-server.toml` contains `sourceBucket.allowedContents`. The default allows
water, lava, and the special non-fluid id `somebuckets:milk`.

The allowlist is checked whenever a Source Bucket acquires, supplies, places, consumes, or destroys
content. Removing an assigned content leaves its NBT and name intact but makes the bucket inert until
reset. The policy never restricts Big or Huge Buckets. Config loading resolves ids into an immutable
snapshot and logs unknown ids.

### Protection

Protected operations carry a `ProtectionContext`, an exact target, and an action such as block edit,
fluid edit, entity interaction, or entity release. Feasibility is established first; permission is
checked before mutation. Player contexts contain the real player and actual hand. Dispensers use the
stable fake player `[SomeBuckets]` at the dispenser rather than impersonating the dispenser's owner.

FTB Chunks has a dedicated optional provider. Open Parties and Claims compatibility comes from its
ordinary Forge hooks and dispenser wrapper. Player Junk/Trash operations also pass through the mod's
protection layer. A denial must leave the intended bucket, block, fluid, cauldron, or entity mutation
undone.

**Known limitation:** `ClaimProtections.initialize()` only registers a `ClaimProtectionProvider` when
FTB Chunks is loaded (`protection/ClaimProtections.java`). No other claim mod has a bundled adapter.
Player actions are still covered by `level.mayInteract`/`player.mayUseItemAt` and vanilla's own
`FillBucketEvent`/`BlockEvent.EntityPlaceEvent`/`PlayerInteractEvent.EntityInteract`, which most claim
mods already hook. But automation-driven `ENTITY_INTERACT`/`ENTITY_RELEASE` — the dispenser paths that
feed animals, capture/release mobs, and vacuum/eject item entities — have no such vanilla event to fall
back on, so under any claim mod other than FTB Chunks those dispenser behaviors are **not deniable**.
Adding coverage for another claim mod means writing and registering another `ClaimProtectionProvider`.

### Vanilla and Forge integration

World fluid pickup uses the block's own `IFluidBlock` or `BucketPickup` contract; it does not replace
the block with air. Placement follows vanilla target selection, waterlogging, replacement, and
ultra-warm evaporation rules. Player placement may fall through to the neighboring block, while
dispenser placement is restricted to the block directly in front.

Player world-bucket paths post `FillBucketEvent` against the block the operation will actually
change. Successful operations emit the corresponding game event and award vanilla-style statistics
and criteria where vanilla has an equivalent. Cauldron transitions are physically implemented in
`Cauldrons`, even though player, Source Bucket, and dispenser selection paths remain separate.

Block and held-item fluid transfers use Forge capabilities and simulate before executing. Big and
Huge Buckets are ordinary finite handlers. Source Buckets have special hand-to-hand pumping so they
can fill a destination in one gesture; machines still see the public 1,000 mB-per-call capability.

## Behavior landmarks

The full observable behavior belongs in `player-view.md`. These points explain the main internal
families:

- Big and Huge Buckets share finite-content logic for fluids, milk, and powder snow. Empty buckets
  take, full buckets place, and partial buckets try to take compatible content before placing.
- Source Buckets reuse much of the fluid machinery but represent an allowed, permanent assignment.
  They are infinite both as a source and as a compatible sink.
- Junk and Trash Buckets share inventory gestures, animal feeding, ejection, dispenser structure, and
  overridable intake/eject sound hooks. Junk is a nine-entry FIFO; Trash is a one-entry destructive
  replacement container.
- Mob Buckets store full entity snapshots, restrict a load to one exact entity type, and remove a
  snapshot only after the entity successfully enters the world. Aquatic release delegates required
  water placement to `FluidPlacement`.
- Every item has custom dispenser behavior and remains in the dispenser. Dispenser selection rules
  are family-specific, but the underlying mutations reuse the player-operation primitives where
  practical.

Crafting uses two custom ingredients: `somebuckets:empty_bucket` rejects filled Big or Source
Buckets, and `somebuckets:spawn_egg` accepts spawn eggs. Runtime resources come from
`src/main/resources` and configured generated-resource roots; `work/` is reference material only.

## Client presentation

`ClientSetup` owns client lifecycle registration. Big, Huge, and Source Buckets use the
`somebuckets:bb_content` predicate to select empty, fluid, milk, or powder-snow presentation. Fluid
models and bars use the actual stack's fluid texture and tint so NBT-defined fluid variants can look
different.

Mob Buckets use `somebuckets:filled` and spawn-egg colors. Junk Bucket rendering delegates each stored
stack to Minecraft's `ItemRenderer`, then masks the result to the bucket opening; this preserves
custom models, tint, render passes, and glint.

## Maintenance checklist

Before adding a new mutation or transfer path, check the relevant items below:

- Normalize exhausted content and use `NBTUtil` rather than editing content NBT ad hoc.
- Enforce `SBPolicy` at every new Source Bucket input or output boundary.
- Simulate capability transactions before authorization and execution.
- Check the actual mutation target with the correct protection context and action.
- Use block-owned fluid pickup and placement contracts.
- Emit the matching game event and vanilla-style statistic or criterion where applicable.
- Play sound feedback unconditionally on both sides, not only `if (!level.isClientSide)`; a
  server-only call is silently excluded from the acting player's own broadcast.
- Keep physical cauldron changes in `Cauldrons`.
- Keep dispenser hits and sided capabilities aimed at the face adjacent to the dispenser.
- Route every Junk/Trash intake through `JBItem.canStore`; use `TBItem.findFirstNearby` for Trash
  world lookup so a one-item operation does not scan an entire pile.
- Remove a Mob Bucket snapshot only after successful insertion, and delegate aquatic water placement
  to `FluidPlacement`.
- Preserve legal item stacks and in-place mutation when settling transfers.
- Preserve the public Source Bucket capability's 1,000 mB-per-call limit.
- Keep `ClientSetup` as the single client lifecycle owner, and keep Java predicate values synchronized
  with resource models.
- Continue delegating stored Junk items to `ItemRenderer` rather than approximating their sprites.

The implementation intentionally has one server config and no networking, JEI integration, loot
tables, blocks, or block entities. `src/TODO.txt` is exploratory and may be stale.
