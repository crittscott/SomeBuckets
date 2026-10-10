![Some Buckets](https://raw.githubusercontent.com/crittscott/SomeBuckets/1.21.1/images/somebuckets-splash.png)

![Loaders: Fabric Forge Neoforge Quilt](https://img.shields.io/badge/Loaders-Fabric%20Forge%20Neoforge%20Quilt-5c7c8a?style=for-the-badge) ![MC: 1.21.4 1.21.1 1.20.1](https://img.shields.io/badge/MC-1.21.4%201.21.1%201.20.1-8a5a9b?style=for-the-badge)

# Some Buckets

An expansion of [the_will_bl's](https://www.curseforge.com/members/the_will_bl/projects) [Big Buckets](https://www.curseforge.com/minecraft/mc-mods/bigbuckets). For 1.21.4, 1.21.1, and 1.20.1, Fabric, Forge, Neoforge, and Quilt.

Need more than buckets that are big? How about buckets to hold mobs? Buckets to hold a random assortment of junk? Bottomless, item destroying buckets to help you clean up the place? Endless source buckets? You've come to the right place!

## The buckets

| Item | What it does |
| --- | --- |
| **Big Bucket** | Holds 8 units of one fluid, milk, or powder snow |
| **Huge Bucket** | Same as a Big Bucket, but holds 64 units |
| **Source Bucket** | Infinite supply and sink for one allowed fluid or milk |
| **Junk Bucket** | Stores up to 9 stacks of items, ejects one stack at a time, feeds animals |
| **Trash Bucket** | Stores 1 stack of items, destroying it when incoming items do not fit |
| **Mob Bucket** | Stores 8 mobs of any one type |

All six are in one creative tab. Each bucket stacks to 16 while empty and to 1 once it holds any content. Using a stack of empties fills one, which goes to your inventory or drops at your feet; in creative mode the empties are kept, as in vanilla. Contents stay attached when the bucket is moved, dropped, stored, or carried through death.

## Crafting

**Big/Huge Bucket** — 8 vanilla/empty Big Buckets in a ring.

```
B B B
B   B
B B B
```

**Junk Bucket** — a chest with iron on both sides and below:

```
. . .
I C I
. I .
```

**Trash Bucket** — shapeless: Junk Bucket + Enderman Spawn Egg + Ender Eye

**Source Bucket** — shapeless: Trash Bucket + Netherite Block

**Mob Bucket** — shapeless: empty Source Bucket + any spawn egg

Recipes that consume a Some Buckets item require it to be empty. Spawn eggs are crafting ingredients only; they do not configure the resulting bucket.

## Structure loot

Each roll is independent, so one container can receive several buckets.

| Item | Chance | Locations |
| --- | --- | --- |
| **Junk Bucket** | 2% | All village chests |
| **Big Bucket** | 5% | Every vanilla structure container except village chests, including the jungle-temple dispenser |
| **Source Bucket** | 10% | All bastion chests |
| **Source Bucket** | 5% | Buried treasure, all shipwreck chests, and both underwater-ruin chests |
| **Trash Bucket** | 5% | End City treasure and all stronghold chests |
| **Mob Bucket** | 5% | End City treasure and all stronghold chests |
| **Huge Bucket of powder snow** | 5% | Igloo chests and Ancient City ice boxes |

Loot buckets are empty except the Huge Bucket, which holds 64 powder snow. Bonus chests, archaeology, fishing, entity drops, and non-structure loot are not affected.

## Using the buckets

### Big Bucket / Huge Bucket

The Big Bucket holds 8 units and the Huge Bucket holds 64. One unit is 1,000 mB of fluid, one powder-snow block, or one milking. A bucket holds only one content type at a time.

They can collect and place:

- Fluid source blocks, including water from waterlogged blocks
- Powder-snow blocks
- Water, lava, and powder snow in cauldrons
- Fluids in blocks that expose a Neoforge/Forge fluid tank or Fabric Transfer API storage
- Milk from adult cows

World, cauldron, and tank operations move one unit per use.

| Action | Result |
| --- | --- |
| Use a source block, cauldron, tank, or adult cow | Collect one unit |
| Use a placeable block, cauldron, or tank | Place one unit |
| Use air while holding milk | Drink one unit and clear potion effects |
| Sneak-use air | Empty the whole bucket without confirmation |

An empty bucket tries to collect. A full bucket tries to place. A partially filled bucket first tries to collect compatible content and otherwise places one unit. Placement follows vanilla behavior for waterlogging, replaceable blocks, and water evaporation in ultra-warm dimensions.

Sneak-use on air empties the bucket (be careful not to lose your 64 buckets of lava!).

Powder snow follows the same order, except that sneaking while targeting an existing powder-snow block places another block instead of collecting it.

On Forge and Neoforge, when another mod enables the loader's milk fluid, tanks, pipes, and machines exchange milk with Big, Huge, and Source Buckets as that fluid.

A lava-filled Big or Huge Bucket burns in a furnace like a vanilla lava bucket and comes back with one unit removed.

The name, tooltip, and a colored durability-style bar show the contents and fill level.

In a dispenser, the bucket stays in the dispenser and operates on the block directly in front, one unit per pulse.

### Source Bucket

The Source Bucket is an infinite source and sink for one server-config-allowed fluid/milk. The default allowlist is water, lava, and milk.

| Action | Result |
| --- | --- |
| Use a block, cauldron, or tank | Place the assigned fluid |
| Sneak-use a block, cauldron, or tank holding the assigned fluid | Take one unit |
| Sneak-use air | Reset the bucket to empty |

Machines transfer up to one bucket unit per operation through Neoforge/Forge fluid capabilities or Fabric Transfer API storage. Hand-filling with a Source Bucket fills the receiving container to capacity in one use.

In a dispenser, an assigned bucket takes matching fluid in front; otherwise it places, even into a different world fluid, so normal reactions happen (lava into water makes obsidian). The same applies to cauldrons and tanks.

### Junk Bucket

The Junk Bucket is a portable, first-in-first-out item container with nine stack slots. If you want the thing in the middle, you have to dump the older stacks first.

| Action | Result |
| --- | --- |
| Use air | Collect eligible nearby dropped items within 1.5 blocks |
| Sneak-use a block | Eject the oldest stack beside that block |
| Sneak-use air | Throw the oldest stack from the player; like Q |
| Use an animal | Offer it suitable stored food; the animal decides, as with food in hand (breeding, growth, healing) |
| In an inventory screen | Right-click between the bucket, cursor, and slots to insert or remove stacks |

Stored items are rendered protruding from the bucket opening, with the oldest stack in front. Their layout is randomized whenever items are inserted, while their normal models, tint, and enchantment glint are preserved.

No recursive Junk Buckets: Junk Buckets cannot store Junk Buckets, Trash Buckets, bundles, shulker boxes, other items that opt out of container storage, or modded item containers such as backpacks. They can store Big, Huge, Source, and Mob Buckets with their contents intact.

In a dispenser, the Junk Bucket first tries to feed an animal in front, then collects eligible item entities, and otherwise ejects its oldest stack. An animal or item that cannot currently be processed prevents ejection.

### Trash Bucket - Danger!

The Trash Bucket is a one-stack variant of the Junk Bucket. If incoming items fit the stored stack, they merge. *Otherwise the stored stack is destroyed and replaced by the incoming item, up to that item's stack limit*. Excess incoming items remain where they were.

### Mob Bucket

The Mob Bucket holds up to eight mobs of one entity type.

| Action | Result |
| --- | --- |
| Use an eligible mob | Capture it with its state intact, including health, name, age, inventory, and UUID |
| Sneak-use a block | Release the oldest stored mob into the adjacent space |

After the first capture, the bucket accepts only the same entity type until emptied. Cannot capture players, non-mob entities, passengers, vehicles carrying passengers, leashed mobs, mobs owned by another player, and entity types in the `somebuckets:mb_blacklist` tag.The Ender Dragon and Wither are blocked by default.

Aquatic mobs require water at the release position. The bucket waterlogs a suitable block or places a water source where possible. In an ultra-warm dimension, the water evaporates, but the mob is still released. The mob remains stored until it successfully enters the world.

The tooltip shows the stored type and count, and the bucket is tinted with that entity's spawn-egg colors. A bundled color table, which resource packs can extend, covers entities whose eggs report no usable colors.

In a dispenser, the bucket first tries to capture an eligible mob in front. Any mob remaining in the target space prevents release. If the space contains no mob, the bucket releases its oldest stored mob.

## Held-container transfers

Using a Big, Huge, or Source Bucket on air while holding a fluid container in the other hand transfers between them. A targeted block takes precedence. The other container may be a vanilla bucket, modded bucket, or tank item that exposes its loader's fluid storage API. Milk moves between Some Buckets containers and vanilla milk buckets, and, where the loader's milk fluid is enabled, to and from any other fluid container.

Big and Huge Buckets transfer as much as the receiving container accepts. A Source Bucket can fill a compatible container without losing content, fill a Big or Huge Bucket to capacity, or accept compatible fluid without changing. An unassigned Source Bucket can be assigned by a transfer.

When a held stack contains multiple containers, the operation processes as many as possible. One result stack stays in your hand, the rest go to your inventory, and only what does not fit drops at your feet.

## Land claims

Some Buckets acts as the player and follows vanilla protection:

- Player block edits respect spawn protection, the world border, and adventure-mode rules.
- Player entity interactions respect the world border.
- Dispensers act as a stable automation player named `[SomeBuckets]` and, like vanilla dispensers, answer only to the world border.

Claim mods need no Some Buckets add-on; they are consulted through the loader events they already watch:

| Player action | Event |
| --- | --- |
| Cauldron and tank use | Block interaction (`RightClickBlock` on Forge/Neoforge, `UseBlockCallback` on Fabric) |
| Collecting fluid or powder snow from the world, including the water removed when capturing an aquatic mob | Block break (`BlockEvent.BreakEvent` on Forge/Neoforge, `PlayerBlockBreakEvents.BEFORE` on Fabric) |
| Placing fluid or powder snow, including the water placed when releasing an aquatic mob | Block place on Forge/Neoforge, reporting the fluid or powder snow placed |
| World fluid use (Forge only) | `FillBucketEvent` |
| Capturing, milking, and feeding | Entity interaction |
| Junk/Trash item collection | Item pickup (Forge/Neoforge) |

On Fabric, when Patbox's Common Protection API is installed (claim mods that support it ship it), it also checks player fluid pickup and fluid and powder-snow placement.

Any denial prevents the operation. A refused placement is undone: the bucket and any stored mob are unchanged, and a replaced plant drops nothing.

Owned mobs can be captured only by their owner, and never by dispensers. Dispensers never feed an untamed tamable animal, so they cannot tame one.

## Configuration

Forge and Neoforge keep the Source Bucket allowlist in the world save's `serverconfig/somebuckets-server.toml`, with the loader's usual per-world override behavior, and sync it to clients:

```toml
allowedContents = ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
```

Fabric has no per-world config facility, so it uses a global, server-owned `config/somebuckets-server.json` and syncs the result to clients on join and reload:

```json
{
  "allowedContents": ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
}
```

Registered fluid IDs may be added. `somebuckets:milk` represents milk, which is not a loader fluid. An empty list disables all Source Bucket contents. Unknown fluid IDs are ignored and logged. `/reload` applies changes without a restart on every loader. The allowlist does not affect Big or Huge Buckets.

## Diagnostics

`/sb eggs` and `/sb fluids` are client commands that write plain-text reports to that client's `config/somebuckets/`, overwriting the previous run. On a dedicated server, run them from a connected client. Each report starts with a `PROBLEMS` section, then lists every entry; problems also appear in the command output, and nothing is logged.

- `/sb eggs` lists the two Mob Bucket tint colors for every entity type, flagging capturable types with no spawn egg and eggs with missing, identical, or colorless tints.
- `/sb fluids` follows the Big/Source Bucket bar-color path for every source fluid, flagging fluids that fall back to the default color or come out near-black, along with their world-pickup and bucket-item facts.

## Known issues

- In creative mode, some modded tanks may intercept a normal use and drain themselves without filling a Big Bucket. Survival use and creative sneak-use worked in the case observed.

## Credits and license

A complete rewrite of [the_will_bl's](https://www.curseforge.com/members/the_will_bl/projects) [Big Buckets](https://www.curseforge.com/minecraft/mc-mods/bigbuckets), with new kinds of buckets, for 1.21.4, 1.21.1, and 1.20.1, Fabric, Forge, Neoforge, and Quilt.

License: [**GPL-3.0**](https://github.com/crittscott/SomeBuckets/blob/main/LICENSE)
