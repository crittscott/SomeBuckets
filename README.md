# Some Buckets

An expansion of [the_will_bl's](https://www.curseforge.com/members/the_will_bl/projects) [Big Buckets](https://www.curseforge.com/minecraft/mc-mods/bigbuckets).

Need more that big buckets? Buckets to hold mobs? Buckets to hold a random assortment of junk? Bottomless buckets to help you clean up the place? Tired of carrying water everywhere?

## The buckets

| Item | What it does |
| --- | --- |
| **Big Bucket** | Holds 8 units of one fluid, milk, or powder snow |
| **Huge Bucket** | Same as Big, but holds 64 units |
| **Source Bucket** | Infinite supply and sink for one allowed fluid or milk |
| **Junk Bucket** | Stores up to 9 stacks of items |
| **Trash Bucket** | Stores 1 stack of items, overwriting whatever's already in it |
| **Mob Bucket** | Stores up to 8 mobs of one type |

Everything stays with it through drops, storage, and death.

## Crafting

**Big/Huge Bucket** — 8 vanilla/big buckets in a ring:

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

**Source Bucket** — shapeless: a Trash Bucket + Netherite Block

**Mob Bucket** — shapeless: a Source Bucket + any spawn egg (the egg is only an ingredient — it doesn't set what the bucket captures)

## Using the buckets

### Big Bucket / Huge Bucket

Collects and places fluid source blocks, powder snow, cauldron contents, fluids from Forge fluid tanks, and milk from cows.

| Action | Result |
| --- | --- |
| Right-click a source block, cauldron, tank, or cow | Collect one unit |
| Right-click a placeable block, cauldron, or tank | Place one unit |
| Right-click air, holding milk | Drink one unit; clears potion effects |
| Sneak + right-click air | Empty the whole bucket instantly |

An empty bucket always tries to collect; a full bucket always tries to place; a partially filled bucket tries to collect matching content first, then falls back to placing. A lava-filled bucket acts like so many buckets of lava. Big Buckets are dispenser-aware.

### Source Bucket

Fill like a Big Bucket, does not empty on normal use. Sneak + right-click air to empty.

Set allowed fluids in server config. Dispenser-aware.

### Junk Bucket

A portable, first-in-first-out item container with 9 stack slots. It's a junk bucket; you want the thing in the middle? You gotta dump a bunch of stuff on the ground.

| Action | Result |
| --- | --- |
| Right-click air | Vacuum up nearby dropped items (~1.5 blocks) |
| Sneak + right-click a block | Eject the oldest stack |
| Sneak + right-click air | Throw the oldest stack |
| Right-click an animal | Feed it a suitable food item |
| In an inventory screen | Right-click with the bucket to add stacks |

Matching stacks merge into an existing entry before a new one is used, so you won't burn through slots picking up the same item repeatedly. It can't hold Junk or Trash Buckets, bundles, shulker boxes, or other items that opt out of container storage — but it *can* hold Big, Huge, Source, and Mob Buckets, contents and all. In a dispenser, it feeds an animal in front if it can, otherwise collects a nearby item, otherwise ejects its oldest stack.

### Trash Bucket

One-slot version of the Junk Bucket, **which overwrites the contents when picking up anything that won't fit in the current stack**.

| Action | Result |
| --- | --- |
| Right-click nearby items | Pick up one nearby eligible dropped item |
| Sneak + right-click a block | Eject the stored stack next to that block |
| Sneak + right-click air | Throw the stored stack |
| Right-click an animal | Feed it, if the stored item is suitable food |
| In an inventory screen | Right-click with the bucket to add |

If an incoming item matches what's already stored, it merges in. If it doesn't match, **the stored stack is destroyed** and replaced by the new item. Sneak-right-click a block (or air) the moment you're done using it if you want to get your item back before storing something else.

### Mob Bucket

Captures up to 8 mobs, but only of one type at a time.

| Action | Result |
| --- | --- |
| Right-click an eligible mob | Capture it, with health, name, age, inventory, and identity intact |
| Sneak + right-click a block | Release the oldest stored mob into the adjacent space |

Once it holds a mob, it only accepts that same type until it's fully emptied. Players, non-mob entities, ridden/riding entities, and anything in the `somebuckets:mb_blacklist` tag (Ender Dragon and Wither, by default) can't be captured. Will place water with aquatic mobs need water at the release point. Dispenser-aware.

## Bucket-to-bucket transfer by hand

Right-click air with a Big, Huge, or Source Bucket while holding another fluid container (a vanilla bucket, a modded bucket, or a tank item) in your other hand to transfer between them. Big and Huge Buckets move as much as the receiving container can take; a Source Bucket can fill another container without losing its own content, or get assigned by draining one. If your held stack has several containers in it, the bucket works through as many as it can, leaves one legal result in your hand, and drops anything that doesn't fit at your feet.

## Land claims

Some Buckets checks player actions against FTB Chunks claims, and dispensers act as a stable fake player. Open Parties and Claims is covered through its own standard hooks, with no add-on needed.

**With any other claim mod:** player-driven use is still protected by vanilla's own hooks, but a dispenser that feeds animals, captures/releases mobs, or vacuums/ejects items inside someone else's claim is **not** stopped, because there's no vanilla event for those actions to hook into.

## Server configuration

`serverconfig/somebuckets-server.toml` controls what the Source Bucket is allowed to hold:

```toml
allowedContents = ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
```

Unknown IDs are ignored and logged. This setting has no effect on the Big or Huge Bucket.

## License

## Credits and license

A ground-up rewrite of the_will_bl's **Big Buckets**, rebuilt for 1.20.1 with new kinds of buckets.

License: [**GPL-3.0**](https://github.com/crittscott/SomeBuckets/blob/main/LICENSE)
