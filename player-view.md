# Some Buckets — as-built player-facing description

This describes the mod's current observable behavior. It is not a design specification; where it
disagrees with the code, the code is authoritative.

Some Buckets adds six items in one creative tab. Each stacks like a vanilla bucket: up to 16 while
empty, one once it holds any content. Their contents remain attached to the item when it is moved,
dropped, or carried through death.

Tooltip names are colored by rarity: the Junk Bucket is common (white), the Big and Huge Buckets are
uncommon (yellow), and the Source, Trash, and Mob Buckets are rare (aqua).

## Crafting

| Item | Recipe |
| --- | --- |
| **Big Bucket** | 8 vanilla buckets in a ring |
| **Huge Bucket** | 8 empty Big Buckets in a ring |
| **Junk Bucket** | Chest with iron on the left, right, and below |
| **Trash Bucket** | Junk Bucket + Enderman Spawn Egg + Ender Eye, shapeless |
| **Source Bucket** | Trash Bucket + Netherite Block, shapeless |
| **Mob Bucket** | Empty Source Bucket + any spawn egg, shapeless |

Every recipe that consumes another Some Buckets item as an ingredient requires that item to be empty.
Spawn eggs are crafting ingredients only; they do not configure the resulting bucket.

## Structure loot

Some Buckets adds independent bucket rolls to vanilla structure containers. A container can receive
more than one kind of bucket when several rolls succeed.

| Item | Chance | Locations |
| --- | --- | --- |
| **Junk Bucket** | 2% | All village chests |
| **Big Bucket** | 5% | Every vanilla structure container except village chests |
| **Source Bucket** | 10% | All bastion chests |
| **Source Bucket** | 5% | Buried treasure, all shipwreck chests, and both underwater-ruin chests |
| **Trash Bucket** | 5% | End City treasure and all stronghold chests |
| **Mob Bucket** | 5% | End City treasure and all stronghold chests |
| **Huge Powder Snow Bucket** | 5% | Igloo chests and Ancient City ice boxes |

The Big Bucket locations include the jungle-temple dispenser. Bonus chests, archaeology, fishing,
entity drops, and non-structure loot are excluded. Structure-loot buckets are empty except for the
Huge Bucket, which contains all 64 blocks of powder snow.

## Big and Huge Buckets

The Big Bucket holds 8 units and the Huge Bucket holds 64. One unit is 1,000 mB of fluid, one block
of powder snow, or one milking. A bucket holds only one content type at a time.

They can collect and place:

- Fluid source blocks, including water from waterlogged blocks
- Powder snow blocks
- Water, lava, and powder snow in cauldrons
- Fluids in blocks that expose a loader fluid tank (Forge or NeoForge fluid capability, or Fabric Transfer API storage)
- Milk from adult cows

World and cauldron operations move one unit per use. Tank operations also move 1,000 mB per use.
Flowing fluids cannot be collected. Fluids that have no placeable world block can still be carried
between tanks but cannot be poured into the world. Modded fluids use their loader-registered world
placement, vaporization, block-state, and empty-sound behavior where the loader provides it.

An empty bucket tries to collect. A full bucket tries to place. A partially filled bucket first tries
to collect compatible content and otherwise places one unit. Placement follows vanilla behavior for
waterlogging, replaceable blocks, and water evaporation in ultra-warm dimensions.

Powder snow follows the same take-then-place order, except that sneaking while targeting an
existing powder-snow block places another block instead of collecting it, so a partially filled
bucket can build outward instead of vacuuming the wall it is standing next to.

Milk is consumed one unit at a time by using the bucket on air and clears potion effects. Sneak-use
on air empties the entire bucket without confirmation.

A lava-filled Big or Huge Bucket burns for 20,000 ticks in a furnace and returns with one unit
removed. Its name, tooltip, and colored durability-style bar show its contents and fill level.

In a dispenser, the bucket remains in the dispenser and operates on the block directly in front.
Fluid and cauldron operations move one unit per pulse. An empty Big or Huge Bucket can collect a
powder-snow block, but a powder-snow-filled one places instead of collecting additional blocks and
does not fill an empty cauldron from a dispenser.

## Source Bucket

The Source Bucket is an infinite source and sink for one server-allowed fluid or for milk. The default
allowlist is water, lava, and milk; server configuration can remove these or add registered modded
fluids.

An empty Source Bucket is assigned by collecting an allowed source block, draining a compatible
tank or cauldron, receiving a held fluid transfer, or milking a cow. Once assigned, it can place,
supply, or accept that content indefinitely. Infinite milk can be drunk repeatedly, and allowed lava
provides permanent furnace fuel.

Normal right-click on a non-air target with an assigned fluid Source Bucket tries to place its
assigned fluid. Sneak-right-click instead removes one collectible source unit when the target
contains the assigned fluid. A different fluid, or fluid that cannot be collected as one source
unit, is not taken. The same gestures apply to supported cauldrons and blocks exposing a fluid tank.
The bucket's assignment never changes when it takes or places fluid.

Machines transfer up to one bucket unit per operation through Forge or NeoForge fluid capabilities or
Fabric Transfer API storage. Direct held-item transfers from a Source Bucket can fill the receiving
container to capacity in one use.

Sneak-using an assigned fluid Source Bucket on air resets it to empty when no held-container
transfer occurs. Normal use on air leaves the assignment unchanged. If the server removes its
assigned content from the allowlist, the bucket retains its identity but becomes inert until reset.

In a dispenser, an assigned Source Bucket removes matching collectible fluid directly in front. If
it cannot take a matching unit, it tries to place instead, including into a different world fluid;
normal fluid reactions therefore occur, such as lava placed into water producing obsidian. This also
applies to supported cauldrons and exposed fluid tanks. An empty Source Bucket can milk an adult cow
standing in front of it.

## Junk Bucket

The Junk Bucket is a portable FIFO container for nine item stacks.

- Use on air to collect eligible dropped items within about 1.5 blocks.
- Sneak-use on a block to eject the oldest stored stack beside that block.
- Sneak-use on air to throw the oldest stored stack from the player.
- In an inventory, right-click between the bucket, cursor, and slots to insert or remove stacks.
- Use on an animal to feed it suitable stored food.

Compatible stacks merge before using another entry. Freshly dropped items remain unavailable until
their normal pickup delay expires. The tooltip and bar show the number of occupied stack entries.
Collecting and ejecting each play a sound.

Stored items are rendered protruding from the bucket opening, with the oldest stack in front. Their
layout is randomized whenever items are inserted. Their normal item models, tint, and enchantment
glint are preserved.

Junk Buckets cannot store Junk Buckets, Trash Buckets, bundles, shulker boxes, other items that opt
out of container storage, or modded containers such as backpacks that expose an item inventory. Big,
Huge, Source, and Mob Buckets can be stored with their contents intact.

In a dispenser, the Junk Bucket first tries to feed one animal in front, then collects eligible item
entities, and otherwise ejects its oldest stack. An animal or collectable item that cannot currently
be processed prevents ejection.

## Trash Bucket

The Trash Bucket is a one-stack variant of the Junk Bucket. If incoming items fit the stored stack,
they merge. Otherwise the stored stack is destroyed and replaced by the incoming item, up to that
item's stack limit. Excess incoming items remain where they were.

World use processes one nearby eligible item entity at a time. Inventory gestures, ejection, animal
feeding, and storage restrictions match the Junk Bucket. Its tooltip reads `Stacks: n / 1`, and its
item art shows a black void inside the bucket. Collecting plays a water-evaporating sound; ejecting
plays that sound reversed.

In a dispenser, it follows the Junk Bucket's feed, collect, and eject priorities but processes only
one dropped item entity per pulse.

## Mob Bucket

The Mob Bucket holds up to eight mobs of one exact entity type.

Use it on an eligible mob to capture that mob with its state intact, including health, name, age,
inventory, and UUID. After the first capture, the bucket accepts only the same entity type until
emptied. Sneak-use on a block releases the oldest mob into the adjacent space.

Players, non-mob entities, passengers, vehicles carrying passengers, and entity types in the
`somebuckets:mb_blacklist` tag cannot be captured. The shipped blacklist contains the Ender Dragon
and Wither.

Aquatic mobs require water at the release position. The bucket waterlogs a suitable block or places
a water source where possible. Release fails if the mob does not fit or the destination cannot
support the required water. In an ultra-warm dimension, the water evaporates, but the mob can still
be released. A mob remains stored until it successfully enters the world. If its saved UUID is
already in use by a loaded entity, it receives a new one.

Capturing an aquatic mob also removes the water source block it occupies, so releasing a mob and
immediately recapturing it does not leave a free water block behind. That removal uses the normal
fluid-pickup sound and game event and fails the capture if the block refuses pickup.

The tooltip shows the stored type and count, and the bucket is tinted with that entity's spawn-egg
colors. A bundled override table supplies colors for a few entities whose spawn eggs do not report
usable ones; it currently covers The Bumblezone's bee queen and variant bee.

In a dispenser, the bucket first tries to capture an eligible mob in front. Any mob remaining in the
target space prevents release. If the space contains no mob, the bucket releases its oldest stored
mob.

## Held-container transfers

Using a Big, Huge, or Source Bucket on air while holding a fluid container in the other hand transfers
between them. A targeted block takes precedence. The other container may be a vanilla bucket, modded
bucket, or tank item that exposes its loader's fluid storage API (Forge or NeoForge fluid capability,
or Fabric Transfer API storage). Milk transfers only to or from a vanilla milk bucket.

Big and Huge Buckets transfer as much as the receiving container accepts. A Source Bucket can fill a
compatible container without losing content, fill a Big or Huge Bucket to capacity, or accept
compatible fluid without changing. An empty Source Bucket can be assigned by a transfer.

When a held stack contains multiple containers, the operation processes as many as possible. One
legal result stack remains in the hand and incompatible overflow is dropped at the player's feet.
Source Bucket transfers are subject to the Source Bucket allowlist; Big and Huge Bucket transfers are
not.

## Land claims

Some Buckets has direct FTB Chunks integration on Fabric and NeoForge; there is no FTB Chunks build
for Forge on this version. Player fluid, cauldron, milking, storage, and mob operations are checked
as the acting player, including vanilla spawn protection and the world border, so a player cannot
capture mobs, feed animals, vacuum items, or milk a cow inside the spawn-protection radius. Dispensers
act as a stable fake player named `[SomeBuckets]`, so the claim mod's fake-player and ally settings
control automation.

Open Parties and Claims applies its normal interaction hooks and dispenser wrapper without a Some
Buckets add-on. When more than one protection system checks an action, a denial from either prevents
the operation.

**Known limitation:** FTB Chunks is the only claim or protection mod this mod has a dedicated adapter
for. Some Buckets performs its fluid, block, cauldron, and mob edits with direct world calls and a
single internal permission check; it does **not** post the generic block-break and block-place events
that other protection mods (GriefPrevention, region protections, PvP/griefing addons, and similar)
hook. So with any non-FTB protection mod, these buckets can place or pick up fluids, break replaceable
blocks, manipulate cauldrons, and capture, release, feed, or vacuum inside someone else's claim
without that mod being consulted — for player use as well as dispenser automation.

Protection paths that **do** still apply everywhere: vanilla spawn protection, the world border, and
adventure-mode restrictions for player use; the targeted block's own bucket-pickup rules; the
block-place event on powder-snow output; Forge's `FillBucketEvent` on player world fluid fill and
place (which many Forge protection mods listen to); and Forge and NeoForge's ordinary interaction
events for gestures that route through vanilla dispatch.

## Configuration and data packs

Forge and NeoForge worlds use `serverconfig/somebuckets-server.toml`; Fabric uses
`config/somebuckets-server.json`. Their Source Bucket allowlists default to:

```toml
allowedContents = ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
```

The Fabric file expresses the same list as JSON:

```json
{
  "allowedContents": ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
}
```

Registered fluid ids may be added. `somebuckets:milk` represents milk, which is not a loader fluid.
An empty list disables all Source Bucket contents. Unknown fluid ids are ignored and logged.

Editing the allowlist and running `/reload` applies the change without a server restart on every
loader.

Data packs can replace or remove all six recipes, adjust Forge's and NeoForge's structure-loot
modifiers, and extend the `somebuckets:mb_blacklist` entity tag. On Fabric, replacing a target
vanilla loot table with an external data pack suppresses the bundled bucket injection for that table. The mod also
exposes `somebuckets:empty_bucket` and `somebuckets:spawn_egg` custom recipe ingredients.

Resource packs can replace the item models and textures. Every loader clips the stored fluid's
animated still texture to the bucket's content mask and applies its runtime color. NBT-dependent
variant colors are preserved. The mod ships no advancements or JEI integration.

## Diagnostics

Two operator commands write plain-text reports to `config/somebuckets/`, overwriting the previous
run. Findings appear both in the command feedback and in the report file; nothing is written to the
log. Each report leads with a `PROBLEMS` section and then lists every entry.

`/sb eggs` walks every registered entity type and records the two spawn-egg colors the Mob Bucket
would tint its overlays with, flagging capturable types that have no spawn egg and eggs whose two
colors are identical or have no hue. Types covered by the bundled override table report those
override colors instead and are not flagged. Blacklisted types are annotated. On a dedicated server
it needs permission level 2; a client runs it locally and writes to that client's config, like
`/sb fluids`.

`/sb fluids` runs on the client that types it. It walks every registered source fluid and mirrors the
Big and Source Bucket bar-color path: the still texture it resolves, the averaged base color, the
loader tint, the final bar color, and whether the fluid can be collected from the world plus its
vanilla bucket item. It flags fluids that fall back to the default bar color (no still texture, or a
sprite with no readable source image) and fluids whose tint collapses the color to near-black.
Flowing and aliased fluids are skipped and counted. On a dedicated server an operator runs it from a
connected client, and the report lands in that client's `config/somebuckets/`.

## Visible limitations

- Empty Junk and Mob Buckets use the same plain bucket texture.
- A Big Bucket of powder snow uses the vanilla-sized powder-snow bucket texture.
- The Mods screen metadata is still the unedited MDK template.
- Sneak-use on air empties a Big or Huge Bucket without confirmation.
- In creative mode, some modded tanks may intercept a normal use and drain themselves without filling
  a Big Bucket; survival use and creative sneak-use work normally in the observed case.
