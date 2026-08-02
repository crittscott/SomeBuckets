# Some Buckets As-Built Description

This is an as-built record: it describes what the code currently does, not what it ought to do. It does not drive the implementation. It covers the mod's architecture, state model, and observable behavior as an orientation and maintenance guide, not a prose transcription of the Java. Where this document and the implementation disagree, the implementation is authoritative and this document is what gets corrected. Divergence recorded here is information about the code, not a defect list.

## Scope and platform

Some Buckets is an unreleased Forge mod for Minecraft 1.20.1, Forge 47.x, and Java 17. The mod id and root package are `somebuckets` and `com.github.crittscott.somebuckets`.

The mod adds six unstackable items:

| Registry name | Role |
| --- | --- |
| `big_bucket_8` | Finite container for eight bucket/block units |
| `big_bucket_64` | Finite container for 64 bucket/block units |
| `source_bucket` | Infinite source/sink once assigned a fluid or milk |
| `junk_bucket` | Portable storage for up to nine item stacks |
| `trash_bucket` | One-stack replacement container; replacing its contents destroys the old stack |
| `mob_bucket` | Portable storage for up to eight living entities of one entity type |

There are no blocks, block entities, menus, packets, saved-world objects, commands, or configuration files. Persistent state belongs entirely to the individual bucket `ItemStack`.

The project is intentionally not maintaining legacy formats while unreleased. If the item NBT schema changes, existing development data should be migrated or replaced rather than supported through compatibility branches.

## Architecture

The implementation is divided by responsibility:

| Area | Responsibility |
| --- | --- |
| `SomeBuckets` and `register/` | Forge entry point, deferred item/tab registration, lifecycle integrations, and model predicates |
| `item/` | Player-facing behavior of each bucket family |
| `util/NBTUtil` | Shared item-state schema, serialization, normalization, and crafting remainders |
| `util/Protections` | Permission checks and the bucket-use event applied before a player changes the world |
| `crafting/` | Custom ingredient types for empty buckets and standard spawn eggs |
| `fluid/*FluidHandler` | Forge `IFluidHandlerItem` capabilities for Big and Source Buckets |
| `fluid/*FluidLogic` | World, block-capability, powder-snow, and special fluid operations |
| `fluid/FluidPlacement` | Shared vanilla-style world placement of one fluid unit, used by both fluid logic classes |
| `interaction/` | Cross-hand transfers, cauldrons, dispensers, and furnace fuel |
| `client/` | Fluid tint lookup, Big Bucket overlay coloring, and Mob Bucket spawn-egg coloring |
| `resources/` | Recipes, tags, translations, item models, and textures |

The main separation is between storage and operations. `NBTUtil` owns the serialized representation; item classes choose an operation from player input; fluid logic performs world or capability transactions; integration classes adapt those operations to Forge and vanilla hooks.

Most mutations are server-side. Client-side calls generally return a matching success result and play presentation effects so vanilla hand animation and prediction remain responsive.

## Item state model

### Content modes

Content-bearing bucket state is discriminated by a serialized `Mode` string. Big and Source Buckets use the fluid-related modes, while Mob Buckets use `entity`. `NBTUtil` maps the stored value to the closed set below; a missing, empty, or unrecognized value is interpreted as `none`.

| Mode | Associated state | Meaning |
| --- | --- | --- |
| `none` | none | Empty/unassigned bucket |
| `fluid` | `FluidStack` compound | Forge fluid, including its optional NBT payload and amount in mB |
| `milk` | `Amount` integer | Milk amount in mB; milk is deliberately not exposed as a Forge fluid |
| `powder_snow` | `Powder` integer | Count of powder-snow blocks |
| `entity` | `EntityType` plus `Entities` | Entity type id and serialized entity snapshots |

`entity` is part of the shared utility schema for Mob Buckets. Big Buckets do not support entity content.

Zero-valued fluid, milk, powder, and entity states are normally collapsed back to `none`. Callers are responsible for invoking normalization after operations that can remove the final unit. Reading bucket state does not create NBT. When content removal leaves the root compound empty, the compound itself is discarded; unrelated item NBT is preserved.

### Other storage

Junk and Trash Buckets store a `JunkItems` list of serialized `ItemStack` compounds. Their capacity is measured in stack entries, not individual items. Compatible items merge up to their normal maximum stack size before another entry is allocated. When no stacks remain, the `JunkItems` key is absent.

Mob Buckets store each entity with `saveWithoutId`. The common entity type is stored once as a registry id, and each captured entity contributes one compound to `Entities`. UUIDs are removed when an entity is recreated to avoid identity conflicts.

### Fluid capability contract

Big and Source Buckets always expose `ForgeCapabilities.FLUID_HANDLER_ITEM`, but the handler reports contents only while the bucket is in `fluid` mode. Milk, powder snow, and entities are outside the Forge fluid capability.

Both handlers present one tank and accept any non-empty Forge fluid. An assigned tank only accepts the same fluid, including Forge's fluid-stack compatibility rules.

- A Big Bucket is a conventional finite tank with capacity 8,000 or 64,000 mB. Capability fills and drains may be smaller than a world bucket.
- A Source Bucket reports a 1,000 mB tank. Its first fill assigns the fluid and stores a representative 1,000 mB. Later same-fluid fills are accepted without changing it, and drains return fluid without reducing it.

Player and dispenser world operations use whole units: 1,000 mB for fluids or milk and one block for powder snow.

## Protection and permissions

Player-driven world changes are authorized before they happen, as a vanilla bucket does. `util/Protections.mayModify` combines `Level.mayInteract` — spawn protection and the world border — with `Player.mayUseItemAt`, which covers `mayBuild` and the adventure-mode placement rules. A null player is an automated source such as a dispenser and is not subject to these checks, matching vanilla dispenser buckets.

The check is applied per modified position inside the fluid logic classes and `fluid/FluidPlacement` rather than at the item entry points: those classes already carry the nullable player and know which block each operation actually changes. `FluidPlacement` authorizes every candidate position, so the neighbor reached by a fall-through is checked in its own right instead of on the strength of the clicked block. A refused position returns false and the item falls through to `PASS`, as though there had been nothing to do there.

The block-use packet path is gated on `Level.mayInteract` by the server before an item sees it, and `ItemStack.useOn` applies the adventure-mode rules, so the cauldron interactions and the clicked position of every `useOn` need nothing further. The Mob Bucket is the exception: it releases into the neighbor of the clicked block, which that gate does not cover, so `MBItem.useOn` checks that position itself.

`Item.use` has no such gate, since the server receives it without a target position. That is the path the Big and Source Buckets use for fluid and powder work, and both fire `FillBucketEvent` there once a block is targeted, after the shift-discard and cross-hand transfer branches. A cancelling listener fails the interaction. An allowing listener is told it handled the interaction, but the bucket is deliberately not exchanged for the event's filled bucket the way `ForgeEventFactory.onBucketUse` would — these buckets hold many units and are not interchangeable with a one-unit vanilla bucket.

These checks resolve to "permitted" on the client, since only `ServerLevel` overrides `mayInteract`. A refused interaction is predicted as successful and then corrected by the server, exactly as a vanilla bucket is.

## Big Buckets

Big Buckets are the finite general-purpose containers. The two tiers share behavior and differ only in capacity and presentation.

### Fluid behavior

- An empty bucket can collect a source fluid block, drain exactly 1,000 mB from a block fluid capability, or collect a powder-snow block.
- A partially filled fluid bucket first tries to collect a matching source at the target. If collection does not apply, it tries to place 1,000 mB.
- A full fluid bucket only tries to place.
- Different fluids cannot be mixed.
- World placement consumes 1,000 mB and follows the vanilla bucket rules through `fluid/FluidPlacement`: a block that can hold the liquid takes it in place, a replaceable block is broken with its drops, water evaporates in ultra-warm dimensions, and a target that refuses the fluid falls through to the neighbor along the clicked face.
- A compatible block fluid capability is preferred over direct world pickup or placement. The block transaction proceeds only if a simulated full 1,000 mB transfer succeeds.
- Collection, placement, and both powder-snow operations are refused at a position the player may not modify.
- Shift-right-clicking air discards all contents.

### Milk and powder snow

Right-clicking an adult cow adds one milk unit until capacity. Drinking consumes one unit, removes all status effects, and leaves the same bucket item. Powder snow is collected and placed one block at a time.

The item name, tooltip, and durability-style bar expose content type and fullness. The bar is scaled by mB for fluid/milk and block count for powder snow.

### Cauldrons

Big Buckets are registered directly in the vanilla cauldron interaction maps.

- A full water, lava, or powder-snow cauldron contributes one unit and becomes empty.
- An empty cauldron accepts one water, lava, or powder-snow unit and becomes the corresponding full cauldron.
- Other fluids and milk do not use vanilla cauldrons.

## Source Bucket

The Source Bucket is an infinite source/sink keyed to one content type.

- An empty Source Bucket can be assigned by collecting a source fluid block, draining 1,000 mB from a fluid-capable block, right-clicking an adult cow, or receiving a transfer.
- Once assigned a Forge fluid, world placement and capability drains do not reduce or clear it.
- Same-fluid capability fills are accepted without changing its state, making it an infinite sink as well as a source.
- A milk Source Bucket can be drunk repeatedly without consuming milk.
- It does not support powder snow or entities.
- Shift-right-clicking in air clears the assignment. When a block is targeted, the block-use path may perform its fluid operation first.

Player interactions explicitly handle water and lava cauldrons. An empty Source Bucket can consume a full matching cauldron to acquire its type, and an assigned bucket can fill an empty cauldron indefinitely.

Source Bucket world placement shares `fluid/FluidPlacement` with the Big Bucket, so the two agree on target selection, liquid containers, replaceable-block drops, ultra-warm evaporation, and per-position permission. The buckets differ only afterward: the Big Bucket drains a unit and normalizes, while the Source Bucket charges nothing.

## Cross-hand bucket transfers

`Transfers` centralizes intended 1,000 mB transfers among Big Buckets, Source Buckets, and vanilla empty/water/lava/milk buckets. Transfers are attempted only while right-clicking air, with the active bucket normally in the main hand and its partner in the off hand; a targeted block deliberately routes to that block's interaction instead, since that is what a player aiming at a block expects. The air check uses the player's block reach. A Forge player-interaction subscriber supplies the corresponding path when the main-hand item is a vanilla bucket.

The important behavior is:

- A vanilla filled bucket (water, lava, or milk) adds one unit to a compatible Big Bucket and becomes empty.
- A Big Bucket can fill an empty vanilla bucket with water, lava, or milk and loses one unit.
- A vanilla filled bucket can assign a Source Bucket and becomes empty.
- A Source Bucket fills or tops off a compatible Big Bucket to its full capacity without being consumed.
- Sending a Big Bucket unit into a compatible Source Bucket consumes one unit from the Big Bucket.
- Only water, lava, and milk have vanilla bucket item representations; arbitrary modded fluids cannot be transferred into a vanilla bucket.
- An already-filled vanilla bucket, including a milk bucket, is not a valid destination, since it is a fixed 1,000 mB container with no room to top off.

Milk is not a Forge fluid, so `Transfers` cannot carry it as a `FluidStack` the way generic fluids are carried. Internally it represents transferable content as either a real Forge fluid or milk, keeping the two distinct rather than approximating milk as an empty fluid.

## Junk Bucket

The Junk Bucket holds up to nine ordinary item stacks.

- Right-clicking in air absorbs nearby item entities within the player's expanded bounding box, merging compatible stacks and continuing until no candidate or stack slot remains. Items still under their pickup delay are skipped, so a fresh drop or death pile stays with its owner.
- In an inventory, secondary-click gestures insert from a slot or cursor. Secondary-clicking the bucket with an empty cursor extracts the oldest stored stack.
- Shift-right-clicking a block ejects the oldest stored stack into the adjacent space.
- Right-clicking an animal uses the first stored stack that the animal accepts as food. Babies are aged up and eligible adults enter love mode; one food item is consumed outside creative mode.

The tooltip and bar report occupied stack entries, not total item count.

## Trash Bucket

The Trash Bucket reuses Junk Bucket storage and extraction behavior but has a one-stack capacity and destructive replacement semantics.

- If empty, it accepts one legal stack.
- If the incoming stack is compatible and the complete stack fits, it merges.
- Otherwise, the stored stack is deleted and replaced by the incoming stack.
- World right-click considers at most one eligible item entity per use, within a 2.25-block inflated player bounds. Replacement can therefore be used to destroy the previous contents deliberately.

It inherits extraction/ejection and animal-feeding behavior from the Junk Bucket, although there is only one stored entry to choose from.

## Mob Bucket

The Mob Bucket stores up to eight living entities, but all stored entries must have exactly the same `EntityType`.

### Player behavior

- Right-clicking an eligible mob captures its serialized state and removes the entity from the world.
- Eligibility requires a `Mob` whose `EntityType` can be serialized and is not blacklisted, and that is neither riding nor being ridden, since only the clicked entity is captured. Players, armor stands, and other non-`Mob` living entities are never eligible.
- The datapack tag `somebuckets:mb_blacklist` excludes the Ender Dragon and Wither by default and can be extended by datapacks.
- `Bucketable` mobs are eligible. Storage is a full entity snapshot rather than the vanilla bucket tag, so a modded `Bucketable` mob keeps its variant data as long as that data is written in the normal entity save.
- Shift-right-clicking a block releases the oldest stored snapshot into the adjacent block-center position, provided the player may modify that position.
- Release recreates the entity, restores its saved data without its previous UUID, and succeeds only if its collision box fits.
- A released mob that needs water is given water first: the target is waterlogged if it accepts water, otherwise replaced by a water source. If the position cannot hold water, the mob stays in the bucket. Water is required for `Bucketable` mobs and for any mob whose `MobType` is `WATER`.

The tooltip names the stored entity type and shows count out of eight. The bar shows fullness. A filled model uses two spawn-egg-colored overlay layers. The client asks Forge for the standard spawn egg associated with the stored entity type, supporting both Forge and vanilla eggs, and uses gray when none exists.

### Dispenser behavior

On each activation, a Mob Bucket first inspects the block directly in front for a random eligible mob that its current contents can accept. An empty bucket can accept any eligible type; a nonempty bucket can accumulate the same exact type up to its capacity of eight. If no capture is possible, the presence of any other nonremoved `Mob` in that block prevents release, including an incompatible or uncapturable mob and a compatible mob when the bucket is full. Only a mob-vacant front allows a nonempty bucket to release its oldest snapshot at the block center, subject to the same block-collision and water requirements as the player path.

Player and dispenser paths share capture, transactional FIFO release, eligibility, and water-placement helpers on `MBItem`. A failed release does not remove, mutate, or reorder the stored snapshot. The front-block occupancy rule belongs specifically to the dispenser adapter; player release retains its own interaction semantics.

The front block therefore supplies the operation context: compatible mobs make the dispenser an intake, while a vacant block makes a nonempty bucket an outlet. Automation must avoid pulsing a partially filled bucket while the front is accidentally vacant, since that activation releases rather than waits for another input mob.

## Dispensers and automation

Custom dispenser behavior is registered for both Big Buckets, the Source Bucket, and the Mob Bucket. Junk and Trash Buckets use vanilla item dispensing.

- Big Buckets collect/place fluid or powder snow one unit at a time and interact directly with full/empty vanilla cauldrons.
- Source Buckets collect or place fluid without later consumption. An empty Source Bucket first tries to milk an adult cow occupying the block in front. A filled water/lava Source Bucket also empties a full same-fluid cauldron while retaining its assignment.
- Mob Bucket dispenser behavior is described above.

Dispenser and player paths share the fluid-logic classes where practical but contain separate cauldron and mob adapters, so parity between those paths must be maintained explicitly. Dispensers pass a null player, so the permission checks do not apply to them.

## Furnace fuel and crafting remainders

A Big or Source Bucket containing at least 1,000 mB of lava advertises a 20,000-tick furnace burn time, equivalent to a vanilla lava bucket.

Only a bucket holding content provides a crafting remainder:

- A Big Bucket returns itself with one unit consumed: 1,000 mB of fluid or milk, or one powder-snow block. If that was the final unit, the returned bucket is empty.
- A Source Bucket returns itself unchanged, because it is an infinite source. A lava Source Bucket is therefore permanent furnace fuel.
- An empty bucket has no remainder, so a recipe listing it as an ingredient consumes it.

This policy applies to ordinary crafting as well as the container behavior used by lava fuel.

## Client presentation

The `somebuckets:bb_content` item property selects content models for both Big Bucket tiers and the Source Bucket. Model choice depends on content type, not fullness; fullness is shown by the bar.

Big Bucket generic-fluid models use a tinted overlay. The client first asks the fluid's `IClientFluidTypeExtensions` for a tint and falls back to `FluidData`. `FluidData` provides stable predicate/color entries for water, lava, and a set of Mekanism fluids, but Mekanism is not a declared code dependency. Any Forge fluid is still accepted; unlisted fluids use generic predicate and color fallbacks.

The Source Bucket has explicit water, lava, and milk model overrides and falls back to a tinted generic-fluid model, `source_bucket_fluid`, sharing the Big Bucket's tint handler. That model's overlay texture `somebucket_full` has not been drawn, so generic fluids currently render with a missing-texture layer.

The creative tab contains empty Big Bucket tiers, fully filled water/lava/milk/powder variants, empty and assigned Source Bucket variants, and the three specialized storage buckets.

## Recipes and data

The shipped recipes form this progression:

| Result | Recipe |
| --- | --- |
| Big Bucket (8) | Ring of eight vanilla buckets |
| Huge Bucket (64) | Ring of eight empty Big Buckets (8) |
| Junk Bucket | Chest with three iron ingots in a bucket shape |
| Trash Bucket | Junk Bucket plus `forge:heads/enderman` |
| Source Bucket | Trash Bucket plus a netherite block |
| Mob Bucket | Empty Source Bucket plus any standard `SpawnEggItem` |

Recipes that consume a bucket as material use the `somebuckets:empty_bucket` ingredient type, registered during common setup from `crafting/EmptyBucketIngredient`. It matches a named bucket item only while that bucket holds nothing, keeping a filled bucket — which would return itself as a crafting remainder — out of those recipes.

The Mob Bucket recipe uses the `somebuckets:spawn_egg` custom ingredient from `crafting/SpawnEggIngredient`. It accepts every loaded item that extends Minecraft's `SpawnEggItem`, including Forge's standard modded spawn eggs, without maintaining per-mod item tags.

`work/` contains retained art/source-reference material. It is not loaded by Forge. Only files under `src/main/resources` (plus any future generated-resources source set) are runtime assets.

## Current boundaries and maintenance notes

- The code contains no configuration, networking, JEI integration, or loot tables. Forge GameTests cover the principal bucket operations. JEI and broader tag/loot-table work are listed in `src/TODO.txt`.
- Several standalone Mekanism bucket models/textures are present but are not selected by the active Big Bucket generic-overlay model path.
- Empty-state normalization is call-site driven. Any new operation that removes content must normalize the final zero state or deliberately clear the bucket.
- Permission checks are likewise call-site driven. Any new player-driven world mutation must call `Protections.mayModify` on the position it changes, not on the position that was clicked.
- The dispenser implementations contain behavior not delegated to player item methods. Changes to cauldron or Source Bucket semantics should check both paths. Mob Bucket capture, release, eligibility, and water placement are shared on `MBItem`; the dispenser adapter additionally owns its capture-first and mob-vacancy policy.
- `src/TODO.txt` is a work list and includes stale or exploratory entries; this document and the live runtime tree should be kept aligned with actual behavior.
