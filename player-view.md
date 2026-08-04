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
- The spawn egg in the Mob Bucket recipe is just a reagent. It is consumed and does **not** set what
  the Mob Bucket can hold; the result is empty. The same goes for the enderman spawn egg in the
  Trash Bucket recipe.

## Big Bucket / Huge Bucket

The workhorses. Big Bucket holds **8 units**, Huge Bucket holds **64 units**, where a unit is one
vanilla bucketful (1000 mB) or one block of powder snow. A bucket holds exactly one thing at a time —
no mixing water and lava, or fluid and snow.

**Filling** (right-click), one unit per click:

- Any fluid **source block** in the world, vanilla or modded. The source block is removed.
- **Powder snow** blocks.
- **Cauldrons**: full water cauldron, lava cauldron, full powder-snow cauldron. Empties the cauldron,
  takes one unit.
- **Any block with a Forge fluid tank** (a modded tank, a machine's fluid port) — drains 1000 mB per
  click, all-or-nothing.
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

**In a dispenser:** the bucket stays in the dispenser and acts on the block in front — filling from or
emptying into cauldrons, picking up or placing world fluids, taking or placing powder snow. Placement
is limited to that front block; a solid block does not make the bucket place one block farther away.
Vanilla spawn protection does not govern dispensers, but supported land-claim mods do (see below).

**Reading it:** the item name changes with contents ("Big Water Bucket", "Huge Milk Bucket"), a
durability-style bar shows the fill level tinted to the fluid's color, and the tooltip reads
`3/8 buckets` or `2/8 blocks`.

## Source Bucket

An **infinite** bucket of one server-allowed fluid, or of milk when milk is allowed. The default
allowlist is water, lava, and milk; a server can remove any of them or add registered modded fluids.

Right-click an allowed fluid source block (which consumes it), a matching lava/water cauldron, or a
tank — the bucket is now permanently that fluid. From then on, right-click anywhere to place that
fluid, forever. It never runs down. Machines that drain it through the Forge fluid capability drain
up to 1000 mB at a time and it never empties; machines that fill into it can send up to 1000 mB at a
time and it never fills.

Milk a cow with an empty one and you get infinite milk you can drink forever, effect-clearing each
time, provided `somebuckets:milk` is allowed.

**Sneak-right-click on air resets it** to empty so it can be reassigned, the same gate the Big
Bucket uses. Sneak-clicking a block does not wipe the assignment; it places or picks up fluid as an
ordinary click would.

If you right-click a block that has a Forge fluid tank, the Source Bucket steps aside and lets that
block's own interaction run, so machine GUIs still open.

If a server removes an assigned content from the allowlist, existing Source Buckets keep their NBT
and still identify what they contain, but become inert: they cannot fill, drain, place, drink, fuel a
furnace, or transfer that content. Sneak-right-clicking air still resets them.

As a crafting ingredient a Source Bucket returns itself unchanged. While lava is allowed, that also
means **infinite furnace fuel**: 20 000 ticks per burn, forever.

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
  food — breeding adults, growing babies — without the food in hand.

Bar and tooltip show `Stacks: n / 9`.

**Storage does not nest.** A Junk or Trash Bucket refuses to store any container — another of these
buckets, a bundle, or a shulker box — and refuses to be stored in one in turn. A dropped bucket or
shulker box on the ground is skipped by the vacuum rather than absorbed.

## Trash Bucket

Despite the name, it is a **one-stack Junk Bucket that destroys what it is holding whenever you feed
it something different.**

**Right-click** near dropped items: grabs the **first** item entity in a ~2.25-block radius. If the
bucket is empty, it takes up to a full stack. If it already holds the same item and everything fits in
one stack, it merges. **Otherwise it deletes what it was holding and takes the new item instead.**

In practice it is a void-anything trash can: click through a pile of cobble and it all vanishes except
the last stack. That last stack is retrievable — sneak-right-click a block to drop it, or pull it out
in the inventory. All the Junk Bucket inventory gestures work, and the same "replace what is stored"
rule applies there.

It also inherits animal feeding and the no-nesting rule from the Junk Bucket. Tooltip reads
`Stacks: n / 1`.

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
  fit, the release fails and the mob stays in the bucket.
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

Some Buckets integrates with **FTB Chunks** when it is installed. Player actions are checked as that
player; dispensers use a stable fake player named `[SomeBuckets]`, so the FTB Chunks server settings
for fake players and allies determine whether an automated bucket may act in a claim. A dispenser is
not attributed to the player who placed it.

**Open Parties and Claims** needs no Some Buckets add-on: its normal player interaction hooks and its
dispenser wrapper see these operations. If both claim mods are installed, a denial from either one
wins. A denied action leaves the bucket, block, fluid, cauldron, and mob unchanged.

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
- Big/Huge Bucket → empty container: fills it as far as it can and loses what was transferred.
- Source Bucket → empty bucket: fills it, the Source Bucket is unchanged.
- Source Bucket → Big Bucket: fills the Big Bucket **to capacity** in one click.
- Big Bucket → Source Bucket: assigns an unassigned one. Pouring into an **already assigned** Source
  Bucket costs the giver a unit and the Source Bucket keeps nothing — it is an unlimited sink as
  well as an unlimited supply.
- Big Bucket → tank: fills the tank as far as it goes, so a full Huge Bucket fills a 16-bucket tank
  completely.

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
whose content is removed become inert but remain resettable.

There are no config options for capacities, disabling the other buckets, or changing protection
policy. Claim behavior is configured in FTB Chunks or Open Parties and Claims itself.

What is adjustable, all through normal datapack and resource-pack means:

**Datapack**

- `somebuckets:mb_blacklist` — an entity-type tag. Add types here to stop the Mob Bucket capturing
  them. Ships with `minecraft:ender_dragon` and `minecraft:wither`.
- All six recipes are ordinary recipe JSONs and can be overridden or removed.
- Two custom ingredient types are available to datapack authors:
  `{"type": "somebuckets:empty_bucket", "item": "..."}` matches one of these buckets only while empty,
  and `{"type": "somebuckets:spawn_egg"}` matches any spawn egg.

**Resource pack**

- Fluid Big, Huge, and Source Buckets use Forge's dynamic fluid-container model, which samples the
  fluid's own still texture and runtime tint. The `somebuckets:bb_content` predicate distinguishes
  empty (`0`), Forge fluid (`0.1`), milk (`0.2`), and powder snow (`0.3`).
- `somebuckets:filled` is `0` or `1` for the Mob Bucket.

There are **no** loot tables, advancements, JEI integration, item tags, or ore-dictionary-style tags of
any kind.

## Rough edges a player will actually see

1. **Empty Junk, Trash, and Mob Buckets are visually identical** — all three use the same plain bucket
   texture with no distinguishing art.
2. **A Big Bucket of powder snow uses the ordinary vanilla-sized powder snow bucket texture**, so it
   does not look big.
3. **The mod's metadata is the unedited MDK template**, comments and all, so the Mods list shows only
   "Get you some buckets!".
4. **Sneak-right-clicking air with a Big Bucket silently destroys everything in it** — no
   confirmation, and a full Huge Bucket goes the same way as an almost-empty one.
