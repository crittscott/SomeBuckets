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
| `util/Protections` and `protection/` | Vanilla permission checks, action-aware claim-provider dispatch, and the bucket-use event |
| `compat/ftbchunks/` | Optional FTB Chunks adapter and stable dispenser fake-player identity |
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

Storage does not nest. `JBItem.canStore` gates every intake path on `Item.canFitInsideContainerItems`, the same flag vanilla bundles and shulker boxes use to exclude one another, and `JBItem` returns false for it. Both buckets therefore refuse to store any container and are themselves refused by bundles, shulker boxes, and each other.

Mob Buckets store each entity with `saveWithoutId`. The common entity type is stored once as a registry id, and each captured entity contributes one compound to `Entities`. Release preserves the saved UUID unless a nonremoved entity with that UUID is already loaded in any server level, in which case the released entity receives a new UUID.

### Fluid capability contract

Big and Source Buckets always expose `ForgeCapabilities.FLUID_HANDLER_ITEM`, but the handler reports contents only while the bucket is in `fluid` mode. Milk, powder snow, and entities are outside the Forge fluid capability.

Both handlers present one tank and accept any non-empty Forge fluid. An assigned tank only accepts the same fluid, including Forge's fluid-stack compatibility rules.

- A Big Bucket is a conventional finite tank with capacity 8,000 or 64,000 mB. Capability fills and drains may be smaller than a world bucket.
- A Source Bucket reports a 1,000 mB tank. Its first fill assigns the fluid and stores a representative 1,000 mB. Later same-fluid fills are accepted without changing it, and drains return fluid without reducing it.

Player and dispenser world operations use whole units: 1,000 mB for fluids or milk and one block for powder snow.

## Protection and permissions

Every protected fluid, cauldron, powder-snow, milking, and Mob Bucket mutation is described by a `ProtectionContext`, an exact target, and one of five actions: fluid edit, block edit, block interaction, entity interaction, or entity release. Player contexts carry the player and hand; dispenser contexts carry the dispenser position. `util/Protections.mayAct` first applies `Level.mayInteract` and `Player.mayUseItemAt` to player-driven block changes and entity-release destinations, then requires every registered claim provider to allow the operation. Entity interactions use the claim providers and the ordinary Forge player-interaction event path. Denial is fail-closed across providers: any false result prevents the transaction.

Checks live at the mutation boundary, after the code has established that the operation can otherwise succeed. World fluid edits authorize the source or destination actually changed; capability transfers and cauldrons authorize a block interaction; powder snow authorizes a block edit; milking and capture authorize the target entity; release authorizes the recreated entity at the destination. Aquatic release additionally authorizes the waterlog or water-source block edit. Failure leaves item state, world state, and the Mob Bucket FIFO unchanged.

Player fluid use still posts `FillBucketEvent`, allowing protection and automation mods that already understand vanilla buckets to veto the interaction. A cancelling listener fails the interaction. An allowing listener is told it handled the interaction, but the bucket is deliberately not exchanged for the event's filled bucket the way `ForgeEventFactory.onBucketUse` would — these buckets hold many units and are not interchangeable with a one-unit vanilla bucket.

FTB Chunks support is an optional compile-time integration against its 1.20.1 API. At common setup, the adapter registers only when `ftbchunks` is loaded. It maps actions to `EDIT_FLUID`, `EDIT_BLOCK`, `INTERACT_BLOCK`, or `INTERACT_ENTITY` and delegates to `ClaimedChunkManager.shouldPreventInteraction`. Real server players retain their identity and hand. Automation uses a stable Forge fake player named `[SomeBuckets]` with a name-derived UUID, positioned at the dispenser and temporarily holding a copy of the bucket for the check; it deliberately does not impersonate or persist the dispenser owner's identity, so FTB Chunks' fake-player and ally settings remain authoritative.

Open Parties and Claims requires no direct dependency or adapter. Its Forge interaction listeners cover player bucket/entity actions, and its dispenser mixin wraps the registered custom dispense behavior before `execute` runs. Its own fluid-flow protection continues to govern propagation after a source is placed. When OpenPAC and FTB Chunks are both present, their independent vetoes compose: either can stop the action.

Vanilla spawn protection is not applied to dispenser contexts. Claim-provider checks are: direct cauldron edits, fluid/powder edits, milking, capture, entity release, and aquatic water placement all carry the dispenser context. Dispenser fluid and powder placement never use player-style face fall-through; only the block directly in front may be changed. Player placement retains vanilla face fall-through, with the final destination checked in its own right.

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
- Shift-right-clicking clears the assignment only when the ray trace misses, matching the Big Bucket. A targeted block falls through to the ordinary take or place operation instead.

Player interactions explicitly handle water and lava cauldrons. An empty Source Bucket can consume a full matching cauldron to acquire its type, and an assigned bucket can fill an empty cauldron indefinitely.

Source Bucket world placement shares `fluid/FluidPlacement` with the Big Bucket, so the two agree on target selection, liquid containers, replaceable-block drops, ultra-warm evaporation, and per-position permission. The buckets differ only afterward: the Big Bucket drains a unit and normalizes, while the Source Bucket charges nothing.

## Cross-hand bucket transfers

`Transfers` moves content between one of this mod's buckets and whatever fluid container the other hand holds. Transfers are attempted only while right-clicking air, with the active bucket normally in the main hand and its partner in the off hand; a targeted block deliberately routes to that block's interaction instead, since that is what a player aiming at a block expects. The air check uses the player's block reach. A Forge player-interaction subscriber supplies the corresponding path when one of these buckets is in the off hand and the main-hand item is anything else.

Partners are identified by `ForgeCapabilities.FLUID_HANDLER_ITEM` rather than by item identity, so vanilla buckets, modded buckets, and tanks all travel one path, and any fluid that defines a bucket item can be handed to an empty vanilla bucket. One side must be one of this mod's buckets; two foreign containers are ignored.

Each transfer works the partner stack one item at a time. Per item, `pump` moves as much as the pair allows, bounded by the capacity the destination declares and following the simulate-then-execute order Forge's own transfer helper uses, so nothing is drained that the destination will not take. A per-item step ceiling terminates against a container advertising an effectively unbounded capacity. Filling stops once the number of results would exceed the result item's maximum stack size, which is what makes vanilla buckets fill one at a time while stackable tanks fill several.

`settle` then reassembles the outcome: results are merged into stacks, any untouched remainder is appended, the hand keeps the first entry that still holds something, and every other entry is dropped at the player's feet on the server. A stack of empty buckets filled from a Source Bucket therefore yields one filled bucket in hand and the remaining empties on the ground, rather than the whole held stack being replaced by a single item.

A single-item stack is worked in place rather than copied. These buckets never stack and their handlers edit the held `ItemStack` in place, so copying would strand callers on a stale reference.

The important behavior is:

- A filled container adds units to a compatible Big Bucket until the Big Bucket is full or the container is empty.
- A Big Bucket fills empty containers and loses what it transfers; a full Huge Bucket fills a sixteen-bucket tank completely.
- A filled container can assign an unassigned Source Bucket.
- A Source Bucket fills or tops off a compatible Big Bucket to its full capacity without being consumed.
- Sending content into an already-assigned compatible Source Bucket consumes it from the giver and leaves the Source Bucket unchanged, since it is an unlimited sink as well as an unlimited source. Two unlimited supplies exchange nothing.
- An already-filled fixed-capacity container, such as a vanilla water bucket, is not a valid destination, since it has no room to top off.

Milk is not a Forge fluid, so no capability carries it. It keeps its own branch in both directions, keyed on `Items.MILK_BUCKET` and the `milk` mode, and reuses the same stack settlement. An assigned milk Source Bucket sinks a unit the way an assigned fluid one does.

## Junk Bucket

The Junk Bucket holds up to nine ordinary item stacks.

- Right-clicking in air absorbs nearby item entities within the player's expanded bounding box, merging compatible stacks and continuing until no candidate or stack slot remains. Items still under their pickup delay are skipped, so a fresh drop or death pile stays with its owner. Containers are skipped as well, so a dropped bucket, bundle, or shulker box is left on the ground.
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

It inherits extraction/ejection and animal-feeding behavior from the Junk Bucket, although there is only one stored entry to choose from. Its replacement paths write storage directly rather than going through the shared insertion helper, so each of them applies the no-nesting gate in its own right.

## Mob Bucket

The Mob Bucket stores up to eight living entities, but all stored entries must have exactly the same `EntityType`.

### Player behavior

- Right-clicking an eligible mob captures its serialized state and removes the entity from the world.
- Eligibility requires a `Mob` whose `EntityType` can be serialized and is not blacklisted, and that is neither riding nor being ridden, since only the clicked entity is captured. Players, armor stands, and other non-`Mob` living entities are never eligible.
- The datapack tag `somebuckets:mb_blacklist` excludes the Ender Dragon and Wither by default and can be extended by datapacks.
- `Bucketable` mobs are eligible. Storage is a full entity snapshot rather than the vanilla bucket tag, so a modded `Bucketable` mob keeps its variant data as long as that data is written in the normal entity save.
- Shift-right-clicking a block releases the oldest stored snapshot into the adjacent block-center position, provided entity release is allowed there and any required water edit is also allowed.
- Release recreates the entity, restores its saved data and UUID, and succeeds only if its collision box fits. If that UUID already belongs to a loaded entity in any server level, the released mob receives a new UUID instead.
- A released mob that needs water is given water first: the target is waterlogged if it accepts water, otherwise replaced by a water source. If the position cannot hold water, the mob stays in the bucket. Water is required for `Bucketable` mobs and for any mob whose `MobType` is `WATER`.

The tooltip names the stored entity type and shows count out of eight. The bar shows fullness. A filled model uses two spawn-egg-colored overlay layers. The client asks Forge for the standard spawn egg associated with the stored entity type, supporting both Forge and vanilla eggs, and uses gray when none exists.

### Dispenser behavior

On each activation, a Mob Bucket first inspects the block directly in front for a random eligible mob that its current contents can accept. An empty bucket can accept any eligible type; a nonempty bucket can accumulate the same exact type up to its capacity of eight. If no capture is possible, the presence of any other nonremoved `Mob` in that block prevents release, including an incompatible or uncapturable mob and a compatible mob when the bucket is full. Only a mob-vacant front allows a nonempty bucket to release its oldest snapshot at the block center, subject to the same collision, water, and claim checks as the player path.

Player and dispenser paths share capture, transactional FIFO release, eligibility, and water-placement helpers on `MBItem`. A failed release does not remove, mutate, or reorder the stored snapshot. The front-block occupancy rule belongs specifically to the dispenser adapter; player release retains its own interaction semantics.

The front block therefore supplies the operation context: compatible mobs make the dispenser an intake, while a vacant block makes a nonempty bucket an outlet. Automation must avoid pulsing a partially filled bucket while the front is accidentally vacant, since that activation releases rather than waits for another input mob.

## Dispensers and automation

Custom dispenser behavior is registered for both Big Buckets, the Source Bucket, and the Mob Bucket. Junk and Trash Buckets use vanilla item dispensing.

- Big Buckets collect/place fluid or powder snow one unit at a time and interact directly with full/empty vanilla cauldrons.
- Source Buckets collect or place fluid without later consumption. An empty Source Bucket first tries to milk an adult cow occupying the block in front. A filled water/lava Source Bucket also empties a full same-fluid cauldron while retaining its assignment.
- Mob Bucket dispenser behavior is described above.

World fluid and powder placement is front-only for dispensers: a blocked front cell does not fall through to the next block. Dispenser and player paths share the fluid-logic classes where practical but contain separate cauldron and mob adapters, so parity between those paths must be maintained explicitly. Every dispenser operation passes the same source-aware automation context into the protection layer.

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
| Trash Bucket | Junk Bucket plus an enderman spawn egg and an ender eye |
| Source Bucket | Trash Bucket plus a netherite block |
| Mob Bucket | Empty Source Bucket plus any standard `SpawnEggItem` |

Recipes that consume one of this mod's buckets as material use the `somebuckets:empty_bucket` ingredient type, registered during common setup from `crafting/EmptyBucketIngredient`. It matches a named bucket item only while that bucket holds nothing, keeping a filled bucket — which would return itself as a crafting remainder — out of those recipes. The Big Bucket (8) recipe needs no such guard, since `minecraft:bucket` is itself the empty item and a filled vanilla bucket is a different one.

The Mob Bucket recipe uses the `somebuckets:spawn_egg` custom ingredient from `crafting/SpawnEggIngredient`. It accepts every loaded item that extends Minecraft's `SpawnEggItem`, including Forge's standard modded spawn eggs, without maintaining per-mod item tags. The Trash Bucket recipe names `minecraft:enderman_spawn_egg` directly and does not use that ingredient; both eggs are reagents and neither sets what the resulting bucket may hold.

`work/` contains retained art/source-reference material. It is not loaded by Forge. Only files under `src/main/resources` (plus any future generated-resources source set) are runtime assets.

## Current boundaries and maintenance notes

- The code contains no configuration, networking, JEI integration, or loot tables. Forge GameTests cover the principal bucket operations. JEI and broader tag/loot-table work are listed in `src/TODO.txt`.
- Several standalone Mekanism bucket models/textures are present but are not selected by the active Big Bucket generic-overlay model path.
- Empty-state normalization is call-site driven. Any new operation that removes content must normalize the final zero state or deliberately clear the bucket.
- Permission checks are likewise call-site driven. Any new mutation must call `Protections.mayAct` with its precise action, actor context, and actual target before changing item or world state. Automation must preserve its source position rather than falling back to an unowned context.
- The no-nesting rule is likewise call-site driven. Any new storage intake path must go through `JBItem.canStore`; the shared insertion helper covers most of them, but paths that write `JunkItems` directly do not inherit it.
- `Transfers` knows only the fluid-item capability and this mod's own bucket classes. A new container from any mod is supported without changes there, but an item handler that mutates the stack it was handed relies on single-item stacks being worked in place; that behavior must be preserved if the settlement logic is reworked.
- The dispenser implementations contain behavior not delegated to player item methods. Changes to cauldron or Source Bucket semantics should check both paths. Mob Bucket capture, release, eligibility, and water placement are shared on `MBItem`; the dispenser adapter additionally owns its capture-first and mob-vacancy policy.
- `src/TODO.txt` is a work list and includes stale or exploratory entries; this document and the live runtime tree should be kept aligned with actual behavior.
