# Some Buckets — as-built player's guide

A description of what the mod does today, read from the source. This is behavior as implemented,
not intent: where the code and the apparent design disagree, the code is what is recorded here.

Six items, all unstackable, all in one creative tab called **Some Buckets**. Everything is stored in
item NBT, so a bucket's contents ride along with the item and drop on death like anything else.

## The crafting tree

| Item | Recipe |
|---|---|
| **Big Bucket** | 8 vanilla buckets in a ring (24 iron) |
| **Huge Bucket** | 8 *empty* Big Buckets in a ring (192 iron) |
| **Junk Bucket** | Chest with iron on left, right, and below |
| **Trash Bucket** | Junk Bucket + Enderman Spawn Egg + Ender Eye (shapeless) |
| **Source Bucket** | Trash Bucket + Netherite Block (shapeless) |
| **Mob Bucket** | *empty* Source Bucket + any spawn egg (shapeless) |

Two things worth knowing:

- The Huge Bucket and Mob Bucket recipes use a custom "empty bucket" ingredient: a Big Bucket or
  Source Bucket that has contents will **not** match. This is deliberate — a filled bucket returns
  itself as a crafting remainder, so it would survive the craft. The Big Bucket recipe needs no such
  guard, since `minecraft:bucket` is the empty bucket and a filled one is a different item.
- The Trash and Source Bucket recipes do **not** require an empty storage bucket. A filled Junk
  Bucket can be crafted into an empty Trash Bucket, and a filled Trash Bucket can be crafted into an
  empty Source Bucket. Junk and Trash Buckets have no crafting remainder, so anything stored inside
  is destroyed by the craft.
- The spawn egg in the Mob Bucket recipe is just a reagent. It is consumed and does **not** set what
  the Mob Bucket can hold; the result is empty. The same goes for the enderman spawn egg in the
  Trash Bucket recipe.

## Big Bucket / Huge Bucket

The workhorses. Big Bucket holds **8 units**, Huge Bucket holds **64 units**, where a unit is one
vanilla bucketful (1000 mB) or one block of powder snow. A bucket holds exactly one thing at a time —
no mixing water and lava, or fluid and snow.

**Filling** (right-click), one unit per click:

- Any fluid **source block** in the world, vanilla or modded. A plain source block is removed.
  A **waterlogged** block — a slab, stair, or fence with water in it — keeps the block and gives up
  only the water, exactly as a vanilla bucket does. Flowing water and lava cannot be collected, and
  a block that refuses bucket pickup keeps its fluid.
- **Powder snow** blocks.
- **Cauldrons**: full water cauldron, lava cauldron, full powder-snow cauldron. Empties the cauldron,
  takes one unit.
- **Any block with a Forge fluid tank** (a modded tank, a machine's fluid port) — drains 1000 mB per
  click, all-or-nothing. Some modded fluids exist only inside pipes and machines and have no form as
  a world block — Immersive Engineering's potion fluid, for one. A bucket will hold and carry these
  and hand them back to any tank, but clicking the ground with one does nothing at all; it does not
  quietly cost you a unit.
- **Cows**: right-click an adult cow to milk one unit, repeatedly, up to capacity. Milk is a separate
  mode from fluids, so no Forge machine can see it.

**Emptying** (right-click), one unit per click:

- Places a source block in the world following vanilla's rules: waterloggable blocks get waterlogged,
  replaceable blocks are broken and drop, water evaporates in the Nether with the usual hiss and
  smoke, and a click on a solid face falls through to the neighboring block.
- Fills an empty cauldron with water, lava, or powder snow.
- Pushes 1000 mB into a tank block.

**Which way a click goes:** an empty bucket always tries to take. A full bucket always tries to place.
A **partially filled** bucket tries to take first and only places if there is nothing to take — so
clicking a water source tops you up, clicking dirt pours one out.

**Drinking:** a bucket in milk mode is drunk with a normal right-click on air. Consumes one unit and
clears all potion effects, like a vanilla milk bucket. Repeat until empty.

**Sneak-right-click on air voids the entire bucket.** No confirmation, no recovery — 64 lava, gone.
It plays the empty-bucket sound and that is it.

**As furnace fuel:** a Big/Huge Bucket holding lava burns for 20 000 ticks (100 items, same as a
vanilla lava bucket) and is returned to the fuel slot with one unit drained. Drop a Huge Bucket of
lava in a furnace and it burns 64 lava buckets' worth without further attention.

**In a dispenser:** the bucket stays in the dispenser and acts only on the block in front. Fluid
pickup and placement work one unit at a time; full water or lava cauldrons can be emptied into the
bucket, and an empty cauldron can be filled from it. Full powder-snow cauldrons can also be collected.
Powder-snow blocks are asymmetric: an empty bucket can collect one, but once the bucket contains
powder snow it tries to place instead of collecting more, and it cannot fill an empty cauldron with
powder snow. A solid front block does not make any placement move one block farther away. Vanilla
spawn protection does not govern dispensers, but supported land-claim mods do (see below).

**Reading it:** the item name changes with contents ("Big Water Bucket", "Huge Milk Bucket"), a
durability-style bar shows the fill level tinted to the fluid's color, and the tooltip reads
`3/8 buckets` or `2/8 blocks`.

## Source Bucket

An **infinite** bucket of one server-allowed fluid, or of milk when milk is allowed. The default
allowlist is water, lava, and milk; a server can remove any of them or add registered modded fluids.

Right-click an allowed fluid source block (which takes it, on the same terms as a Big Bucket) or a
matching lava/water cauldron and the bucket is now permanently that fluid. From then on, right-click to place that fluid forever. It
never runs down. Machines that drain it through the Forge fluid capability drain up to 1000 mB at a
time and it never empties; machines that fill into it can send up to 1000 mB at a time and it never
fills.

Milk a cow with an empty one and you get infinite milk you can drink forever, effect-clearing each
time, provided `somebuckets:milk` is allowed.

**Sneak-right-click on air resets it** to empty so it can be reassigned, the same gate the Big
Bucket uses. Sneak-clicking a block does not wipe the assignment; it places or picks up fluid as an
ordinary click would.

If you right-click a block that has a Forge fluid tank, the block's own interaction gets priority, so
machine GUIs still open and machines that handle held fluid containers can operate on the bucket. If
the block passes the click onward, the Source Bucket can perform its own all-or-nothing 1000 mB tank
transfer; an empty one is assigned by a successful drain.

If a server removes an assigned content from the allowlist, existing Source Buckets keep their NBT
and still identify what they contain, but become inert: they cannot fill, drain, place, drink, fuel a
furnace, or transfer that content. Sneak-right-clicking air still resets them.

As a crafting ingredient an assigned Source Bucket returns itself unchanged; an empty one has no
remainder and is consumed. While lava is allowed, that also means **infinite furnace fuel**:
20 000 ticks per burn, forever.

In a dispenser it fills and empties cauldrons, places or picks up world fluids, and can milk a cow
standing in front of it. World placement is limited to the block directly in front.

## Junk Bucket

A portable 9-stack container.

- **Right-click air**: vacuums up every dropped item within about 1.5 blocks, merging
  into existing stacks first, then into new slots up to 9.
- **Sneak-right-click a block**: drops the **oldest** stored stack into the world next to that block.
- **In your inventory**: hold the bucket on the cursor and right-click a slot to suck that slot in; or
  right-click the bucket in its slot with an empty cursor to pop the oldest stack onto your cursor.
  Right-click it with items on the cursor to insert them.
- **Right-click an animal**: feeds it from the bucket's contents if any stored item is that animal's
  food — breeding adults, growing babies at the same rate real hand-feeding would — without the food
  in hand.
- **In a dispenser**: acts only on the block directly in front. It first feeds one animal that can
  currently benefit from stored food; otherwise it absorbs every eligible dropped item it can fit.
  If an animal or collectable item is present but cannot be processed, the bucket waits. With no
  input target present, it ejects the oldest stored stack. The bucket itself stays in the dispenser.

Bar and tooltip show `Stacks: n / 9`.

**Reading it:** the bucket's mouth shows what is inside. Each stored stack is drawn as its own
inventory icon, tilted and scattered, cropped to the opening so it reads as junk sticking up out of
the bucket. The **oldest** stack is drawn in front, so the one on top is the one that comes out next.
Nine stacks in a twelve-pixel-wide opening overlap heavily — that is the point. The arrangement is
fixed for a given set of contents rather than reshuffling as you look at it. Each stack goes through
Minecraft's normal inventory-item renderer, so layered and tinted items retain their complete art,
enchanted items retain their glint, and custom-rendered items such as beds, shields, and tridents
appear as themselves rather than as substitutes.

**Storage nesting is only partly blocked.** Junk and Trash Buckets, bundles, and shulker boxes opt out
of container storage, so a Junk or Trash Bucket cannot absorb those items and cannot itself be put in
one of them. Big, Huge, Source, and Mob Buckets do **not** opt out: they can be stored in a Junk or
Trash Bucket with their contents intact, and dropped ones are eligible for the vacuum. Other modded
containers are accepted unless that item explicitly opts out of container storage.

## Trash Bucket

Despite the name, it is a **one-stack Junk Bucket that destroys what it is holding whenever you feed
it something different.**

Its item art shows a pure-black void inside the bucket.

**Right-click** near dropped items: grabs the **first** item entity in a ~2.25-block radius. If the
bucket is empty, it takes up to a full stack. If it already holds the same item and everything fits in
one stack, it merges. **Otherwise it deletes what it was holding and takes the new item instead.**

In practice it is a void-anything trash can: click through a pile of cobble and it all vanishes except
the last stack. That last stack is retrievable — sneak-right-click a block to drop it, or pull it out
in the inventory. All the Junk Bucket inventory gestures work, and the same "replace what is stored"
rule applies there.

It also inherits animal feeding and the same partial storage-nesting rule as the Junk Bucket.
Tooltip reads `Stacks: n / 1`. In a dispenser it follows the Junk Bucket priorities, but processes
only one dropped item per pulse and applies its usual merge-or-replace rule.

## Mob Bucket

Holds up to **8 mobs of a single species**.

- **Right-click a mob** to capture it. Its full state is saved — health, name, age, inventory. It
  vanishes from the world with a slime-splat sound. Once it holds a cow, it only takes cows until it
  is emptied.
- **Sneak-right-click a block** to release the **oldest** captured mob into the space next to that
  block. It keeps its UUID, so UUID-based systems can recognize it as the same mob. If another loaded
  entity anywhere on the server already has that UUID, the released mob gets a new one instead.
- Fish, axolotls, and other water mobs are given water on release: the target block is waterlogged if
  it can be, otherwise replaced with a water source. If the spot cannot hold water or the mob does not
  fit, the release fails and the mob stays in the bucket. The mob is not removed from the bucket until
  it has entered the world; if another mod rejects that final spawn after water was placed, the mob
  stays in the bucket but the new water is not rolled back.
- **Cannot capture**: players, armor stands, item frames, boats, minecarts (anything that is not a
  `Mob`), anything currently riding or being ridden, and anything in the `somebuckets:mb_blacklist`
  entity tag — which ships containing the **Ender Dragon** and the **Wither**. Everything else,
  including the Warden and Elder Guardians, is fair game.
- **In a dispenser**: if there is an eligible mob in the block in front, it captures one at random.
  If no capture is possible, **any** mob still occupying that block prevents release — including an
  incompatible or uncapturable mob, or a compatible mob when the bucket is full. It releases the
  oldest stored mob only when the space is free of mobs.

Tooltip reads e.g. `Cow 3/8`. The item art is tinted with the captured mob's spawn-egg colors, so a
bucket of creepers looks green and a bucket of pigs looks pink.

Capturing and releasing both obey supported land protection. Releasing an aquatic mob can require
two permissions at the destination: permission to release the entity and permission to place or
waterlog water.

## Land claims

Some Buckets integrates with **FTB Chunks** when it is installed. Player fluid, cauldron, milking,
capture, and release operations that enter the mod's protection layer are checked as that player.
Dispensers use a stable fake player named `[SomeBuckets]`, so the FTB Chunks server settings for fake
players and allies determine whether an automated bucket may act in a claim. A dispenser is not
attributed to the player who placed it. Automated Junk and Trash Bucket collection, feeding, and
ejection use this fake-player check as well.

Player-operated Junk and Trash Bucket vacuuming, feeding, and ejection do **not** enter Some Buckets'
claim-provider layer. They still pass through Minecraft Forge's ordinary right-click item, entity, or
block hooks, so a claim mod may stop them there, but Some Buckets itself makes no FTB Chunks query for
those three player paths.

**Open Parties and Claims** needs no Some Buckets add-on: its normal player interaction hooks and its
dispenser wrapper see these operations. Where both claim mods check an operation, a denial from either
one wins. A protection denial before mutation leaves the bucket, block, fluid, cauldron, and mob
unchanged.

## Cross-bucket transfers

Right-click **air** with a Big, Huge, or Source Bucket in one hand and a fluid container in the other
and they exchange contents. It works whichever hand things are in. One side must be one of those
three Some Buckets; two unrelated fluid containers do not transfer through this feature. A block you
are aiming at within your normal reach takes precedence over the hand-to-hand transfer.

The partner can be **anything that exposes fluid storage to Forge** — a vanilla bucket, a modded
bucket, or a tank item from another mod. Any fluid that defines a bucket item can be handed to an
empty vanilla bucket, not just water and lava. Milk is separate from Forge fluids and transfers only
to or from a vanilla milk bucket.

- Filled container → Big/Huge Bucket: moves as much as the destination can accept.
- Big/Huge Bucket → compatible container with room: fills it as far as it can and loses what was
  transferred.
- Source Bucket → empty bucket: fills it, the Source Bucket is unchanged.
- Source Bucket → Big Bucket: fills the Big Bucket **to capacity** in one click.
- Big Bucket → Source Bucket: assigns an unassigned one. Pouring into an **already assigned** Source
  Bucket costs the giver a unit and the Source Bucket keeps nothing — it is an unlimited sink as
  well as an unlimited supply.
- Source Bucket → compatible Source Bucket: with the same allowed Forge fluid, the transfer reports
  success and plays its feedback even though neither bucket changes. Two milk Source Buckets simply
  do nothing.
- Big Bucket → tank: fills the tank as far as it goes, so a full Huge Bucket fills a 16-bucket tank
  completely.
- Source Bucket → tank: fills it to its full reported capacity in one click, however large — the
  source never runs dry, so there is no multi-click wait even for a very large modded tank.

**A held stack is worked through item by item and as much moves as the pair allows.** What ends up
where:

- A Huge Bucket holding 17 units against two 8-bucket tanks fills both and keeps 1 unit.
- A full Huge Bucket against five 16-bucket tanks fills four; those four stay in hand and the fifth
  is dropped at your feet.
- A Source Bucket against a stack of 16 empty buckets fills **one**, because filled buckets do not
  stack, and drops the other fifteen at your feet.

The rule behind all three: the hand keeps one stack, preferring one that still holds something, and
whatever cannot share that slot is dropped rather than destroyed.

Source Bucket transfers work only for contents on the Source Bucket allowlist. Big and Huge Buckets
remain general-purpose fluid containers and are not restricted by that list.

## Configuration

Each world has `serverconfig/somebuckets-server.toml`. Its `sourceBucket.allowedContents` list
controls which contents a Source Bucket may acquire, supply, or destroy as an infinite sink. The
default is:

```toml
allowedContents = ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
```

Use a registered fluid id for modded fluids. Milk is not a Forge fluid, so it uses the special id
`somebuckets:milk`. Removing `minecraft:lava`, for example, disables lava Source Buckets without
restricting Big or Huge Buckets. An empty list disables every Source Bucket content. Existing buckets
whose content is removed become inert but remain resettable. Unknown fluid ids are ignored and logged
when the server configuration loads or reloads, so removing an optional fluid mod does not prevent the
world from loading.

There are no config options for capacities, disabling the other buckets, or changing protection
policy. Claim behavior is configured in FTB Chunks or Open Parties and Claims itself.

What is adjustable, all through normal datapack and resource-pack means:

**Datapack**

- `somebuckets:mb_blacklist` — an entity-type tag. Add types here to stop the Mob Bucket capturing
  them. Ships with `minecraft:ender_dragon` and `minecraft:wither`.
- All six recipes are ordinary recipe JSONs and can be overridden or removed.
- Two custom ingredient types are available to datapack authors:
  `{"type": "somebuckets:empty_bucket", "item": "..."}` checks whether the named item's `Mode` is
  empty (the shipped recipes use it for Big and Source Buckets),
  and `{"type": "somebuckets:spawn_egg"}` matches any spawn egg.

**Resource pack**

- Fluid Big, Huge, and Source Buckets wrap Forge's dynamic fluid-container model, which samples the
  fluid's own still texture, and color it from the bucket's actual contents rather than from the
  fluid alone. Fluids that keep their identity in item data instead of their name — Immersive
  Engineering's potion fluid is the one you will meet — therefore show one color per potion instead
  of one color for all of them. The `somebuckets:bb_content` predicate distinguishes empty (`0`),
  Forge fluid (`0.1`), milk (`0.2`), and powder snow (`0.3`).
- `somebuckets:filled` is `0` or `1` for the Mob Bucket.

There are **no** loot tables, advancements, JEI integration, item tags, or ore-dictionary-style tags of
any kind. Item-use and cauldron-use statistics are tracked the same as any vanilla bucket, though, and
a datapack advancement built around vanilla's own filled-bucket or consume-item criteria will fire
correctly for these items even though none ship by default.

## Rough edges a player will actually see

1. **An empty Junk Bucket and an empty Mob Bucket are visually identical** — both use the same plain
   bucket texture with no distinguishing art. They diverge once either one holds something.
2. **A Big Bucket of powder snow uses the ordinary vanilla-sized powder snow bucket texture**, so it
   does not look big.
3. **The mod's metadata is the unedited MDK template**, comments and all, so the Mods list shows only
   "Get you some buckets!".
4. **Sneak-right-clicking air with a Big Bucket silently destroys everything in it** — no
   confirmation, and a full Huge Bucket goes the same way as an almost-empty one.
5. **A creative-mode, non-sneak right-click on some modded tanks (observed with Mekanism) can drain the
   tank without filling the Big Bucket** — no sound, bucket stays empty, tank loses what it gave up.
   Sneak-right-clicking the same tank, or doing either in survival, works correctly. This points at the
   tank's own creative-mode handling rather than anything this mod does: our fluid-pickup code only runs
   at all when the tank's block doesn't intercept the click first, and every other path (cauldrons,
   world fluids, survival-mode tanks) behaves correctly.
