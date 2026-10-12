<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — player-facing behavior

Some Buckets adds six items in one creative tab. Each stacks like a vanilla bucket: up to 16 while empty, one once it holds any content. Using a stack of empties fills one and leaves the rest. Contents stay with the item when moved, dropped, or carried through death. Malformed saved contents are removed (and logged) when the item loads.

Names are colored by rarity: Junk white, Big and Huge yellow, Source, Trash and Mob aqua.

## Crafting

| Item | Recipe |
| --- | --- |
| **Big Bucket** | 8 vanilla buckets in a ring |
| **Huge Bucket** | 8 empty Big Buckets in a ring |
| **Junk Bucket** | Chest with iron on the left, right, and below |
| **Trash Bucket** | Junk Bucket + Enderman Spawn Egg + Ender Eye, shapeless |
| **Source Bucket** | Trash Bucket + Netherite Block, shapeless |
| **Mob Bucket** | Empty Source Bucket + any spawn egg, shapeless |

Some Buckets ingredients must be empty. Spawn eggs do not configure the result.

## Structure loot

Vanilla structure containers get independent bucket rolls; several can succeed.

| Item | Chance | Locations |
| --- | --- | --- |
| **Junk Bucket** | 2% | All village chests |
| **Big Bucket** | 5% | Every vanilla structure container except village chests |
| **Source Bucket** | 10% | All bastion chests |
| **Source Bucket** | 5% | Buried treasure, all shipwreck chests, and both underwater-ruin chests |
| **Trash Bucket** | 5% | End City treasure and all stronghold chests |
| **Mob Bucket** | 5% | End City treasure and all stronghold chests |
| **Huge Powder Snow Bucket** | 5% | Igloo chests and Ancient City ice boxes |

Big Bucket locations include the jungle-temple dispenser; bonus chests, archaeology, fishing and entity drops are excluded. Structure-loot buckets are empty except for the Huge Bucket, which contains all 64 blocks of powder snow. Rolls survive a data pack replacing the target table.

## Big and Huge Buckets

The Big Bucket holds 8 units and the Huge Bucket holds 64. One unit is 1,000 mB of fluid, one block of powder snow, or one milking. A bucket holds only one content type at a time.

They can collect and place:

- Fluid source blocks, including water from waterlogged blocks
- Powder snow blocks
- Water, lava, and powder snow in cauldrons
- Fluids in blocks that expose Forge fluid capabilities or Fabric Transfer API storage
- Milk from adult cows

World, cauldron and tank operations move one unit (1,000 mB) per use; a tank is credited only for what it actually accepts or gives. Flowing fluids cannot be collected. Fluids that have no placeable world block can still be carried between tanks but cannot be poured into the world. Modded fluids use their loader-provided placement behavior.

An empty bucket tries to collect. A full bucket tries to place. A partially filled bucket first tries to collect compatible content and otherwise places one unit. Placement follows vanilla behavior for waterlogging, replaceable blocks, and water evaporation in ultra-warm dimensions.

Powder snow follows the same take-then-place order, except that sneaking while targeting an existing powder-snow block places another block instead of collecting it, so a partially filled bucket can build outward instead of vacuuming the wall it is standing next to.

Milk is consumed one unit at a time by using the bucket on air and clears potion effects. Sneak-use on air empties the entire bucket without confirmation.

A lava-filled Big or Huge Bucket burns for 20,000 ticks in a furnace and returns with one unit removed. Its name, tooltip, and colored durability-style bar show its contents and fill level.

In a dispenser, the bucket remains in the dispenser and operates on the block directly in front. Fluid and cauldron operations move one unit per pulse. An empty Big or Huge Bucket can collect a powder-snow block, but a powder-snow-filled one places instead of collecting additional blocks. Powder snow fills or empties cauldrons like water and lava.

## Source Bucket

The Source Bucket is an infinite source and sink for one server-allowed fluid or for milk. The default allowlist is water, lava, and milk; server configuration can remove these or add registered modded fluids.

An empty Source Bucket is assigned by collecting an allowed source block, draining a compatible tank or cauldron, receiving a held fluid transfer, or milking a cow. Once assigned, it can place, supply, or accept that content indefinitely. Infinite milk can be drunk repeatedly, and allowed lava provides permanent furnace fuel.

Normal right-click on a non-air target with an assigned fluid Source Bucket tries to place its assigned fluid. Sneak-right-click instead removes one collectible source unit of the assigned fluid; other fluids are not taken. The same gestures apply to supported cauldrons and blocks exposing a fluid tank. The bucket's assignment never changes when it takes or places fluid.

Machines transfer up to one bucket unit per operation through Forge fluid capabilities or Fabric Transfer API storage. Direct held-item transfers from a Source Bucket can fill the receiving container to capacity in one use.

Sneak-using an assigned fluid Source Bucket on air resets it to empty when no held-container transfer occurs. Normal use on air leaves the assignment unchanged. If the server removes its assigned content from the allowlist, the bucket retains its identity but becomes inert until reset.

In a dispenser, an assigned Source Bucket removes matching collectible fluid directly in front. If it cannot take a matching unit, it tries to place instead, including into a different world fluid; normal fluid reactions therefore occur, such as lava placed into water producing obsidian. This also applies to supported cauldrons and exposed fluid tanks. An empty Source Bucket can milk an adult cow standing in front of it.

## Junk Bucket

The Junk Bucket is a portable FIFO container for nine item stacks.

- Use on air to collect eligible dropped items within about 1.5 blocks.
- Sneak-use on a block to eject the oldest stored stack beside that block.
- Sneak-use on air to throw the oldest stored stack from the player.
- In an inventory, right-click between the bucket, cursor, and slots to insert or remove stacks.
- Use on an animal to feed it suitable stored food.
- Hold it in either hand to tempt mobs that follow a player holding any of its stored items, as if that item were held.

Compatible stacks merge before using another entry. Freshly dropped items remain unavailable until their normal pickup delay expires. The tooltip and bar show the number of occupied stack entries. Collecting and ejecting each play a sound.

Stored items protrude from the bucket opening, oldest in front, in a layout randomized on each insertion, with their own models, tint, and glint.

Junk Buckets cannot store Junk or Trash Buckets or items carrying an inventory (bundles, filled shulker boxes, loot-table containers, and on Forge modded backpacks). Big, Huge, Source, and Mob Buckets keep their contents. Items dropped for a specific other player are left alone; collected items count toward pickup statistics. Stored entries that cannot be loaded (an uninstalled mod's item, or excess beyond capacity) are set aside: kept on the bucket, shown in the tooltip, and restored once loadable.

In a dispenser, the Junk Bucket first tries to feed one animal in front (not untamed tamable animals), then collects eligible item entities, and otherwise ejects its oldest stack. An animal or collectable item that cannot currently be processed prevents ejection.

## Trash Bucket

The Trash Bucket is a one-stack variant of the Junk Bucket. If incoming items fit the stored stack, they merge. Otherwise the stored stack is destroyed and replaced by the incoming item, up to that item's stack limit. Excess incoming items remain where they were.

World use processes one nearby eligible item entity at a time. Inventory gestures, ejection, animal feeding, tempting, and storage restrictions match the Junk Bucket. Its tooltip reads `Stacks: n / 1`, and its item art shows a black void inside the bucket.
In a dispenser, it follows the Junk Bucket's feed, collect, and eject priorities but processes only one dropped item entity per pulse.

## Mob Bucket

The Mob Bucket holds up to eight mobs of one exact entity type.

Use it on an eligible mob to capture it with its state intact (health, name, age, inventory, UUID); aquatic mobs use their own bucket sounds. Capture takes priority over the mob's own right-click reaction, such as villager trading. After the first capture, the bucket accepts only the same entity type until emptied. Sneak-use on a block releases the oldest mob into the adjacent space. If the mob's hitbox would touch a solid block there, nothing is released and the action bar reads "No room to release".

Players, non-mob entities, passengers, vehicles carrying passengers, leashed mobs, mobs owned by someone else, and entity types in the `somebuckets:uncapturable` tag cannot be captured. The shipped tag contains the Ender Dragon, the Wither, Shiny Mobs' shiny Wither, and the optional `#forge:bosses` and `#c:bosses` tags.

Aquatic mobs require water at the release position. The bucket waterlogs a suitable block or places a water source where possible. Release fails if the mob does not fit or the destination cannot support the required water. In an ultra-warm dimension, the water evaporates, but the mob can still be released. A mob remains stored until it successfully enters the world. If its saved UUID is already in use by a loaded entity, it receives a new one.

Capturing an aquatic mob removes the water source it occupies, so recapture yields no free water.

Using the Mob Bucket without targeting a mob or sneak-releasing exchanges one mob with a mob bucket in the other hand. A water-based vanilla or modded mob bucket, such as a Bucket of Cod or Bucket of Axolotl, empties its mob into a compatible Mob Bucket and becomes a Water Bucket. A Water Bucket receives the Mob Bucket's oldest mob when that mob has its own water-based bucket. Only what the vanilla bucket format records is kept, with a new UUID; mobs without their own bucket, such as squid, stay. Filling a vanilla mob bucket counts toward bucket advancements such as Tactical Fishing.

The tooltip shows type and count; the bucket is tinted with the spawn-egg colors. Egg-less mobs use another type's egg colors or explicit colors listed in the client resource `somebuckets:mob_bucket_colors.json` (shipped for Shiny Mobs, Sushi Go Crafting, Bumblezone and Wilder Nature), or are gray.

In a dispenser, the bucket first tries to capture an eligible unowned mob in front. Any mob remaining in the target space prevents release. If the space contains no mob, the bucket releases its oldest stored mob.

## Held-container transfers

Using a Big, Huge, or Source Bucket on air while holding a fluid container in the other hand transfers between them, as does using the other container on air while the bucket is in the off hand. A targeted block takes precedence. The other container may be a vanilla bucket, modded bucket, or tank item that exposes its loader's fluid storage API. Milk transfers only to or from a vanilla milk bucket.

Big and Huge Buckets transfer as much as the receiving container accepts. A Source Bucket can fill a compatible container without losing content, fill a Big or Huge Bucket to capacity, or accept compatible fluid without changing. An empty Source Bucket can be assigned by a transfer.

When a held stack contains multiple containers, the operation processes as many as possible. One legal result stack remains in the hand; other results go to the inventory, then drop at the player's feet. Source Bucket transfers are subject to the Source Bucket allowlist; Big and Huge Bucket transfers are not.

Containers in the `somebuckets:held_transfer_excluded` item tag (shipped with Meadow's wooden and Caverns & Chasms' golden buckets) are ignored.

## Land claims

Before changing the world, Some Buckets posts the loader event a player doing the same by hand would: block break for world fluid and powder-snow pickup (including the water an aquatic capture removes), block place for world fluid and powder-snow placement (including aquatic-release water), and item pickup for Junk and Trash intake. A player's cauldron and tank use and capture, milking, and feeding pass the block- and entity-interaction events through vanilla dispatch. Placement is checked against the block actually placed; a refusal restores the world without drops. On Fabric, place and pickup are posted through Architectury's events, and Common Protection API is consulted, when those mods are installed. Claim mods such as FTB Chunks and Open Parties and Claims therefore apply without an adapter. Any denial leaves bucket and target unchanged.

Dispensers post the same events, plus block use for cauldrons, tanks, mob release and item ejection and entity interaction for capture, milking, and feeding, as a stable fake player named `[SomeBuckets]` standing at the dispenser. Claim mods' fake-player settings therefore govern dispensers.

Player block edits also obey spawn protection, the world border, and adventure mode; entity interactions and dispensers obey the world border.

## Configuration and data packs

Forge worlds use `serverconfig/somebuckets-server.toml`; Fabric uses `config/somebuckets-server.json` (same key, as JSON). The Source Bucket allowlist defaults to:

```toml
allowedContents = ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
```

Registered fluid ids may be added. `somebuckets:milk` represents milk, which is not a loader fluid. An empty list disables all Source Bucket contents. Unknown fluid ids are ignored and logged. Fabric rereads the file on `/reload` and sends the list to connected clients.

Data packs can replace the recipes, tune or disable each `somebuckets:inject/<reward>` loot table, retarget Forge's `somebuckets:add_table` modifiers, and extend the `somebuckets:uncapturable` and `somebuckets:held_transfer_excluded` tags. The mod also exposes `somebuckets:empty_bucket` and `somebuckets:spawn_egg` custom recipe ingredients. `docs/datapacks.md` documents all of it.

Resource packs can replace the item models and textures. Stored fluids render with their animated still texture and runtime color, clipped to the content mask. The mod ships no advancements or JEI integration. Client commands `/sb fluids` and `/sb eggs` report fluid colors and Mob Bucket egg colors for diagnosis.

## Current limitations (observed, not planned work)

- Empty Junk and Mob Buckets use the same plain bucket texture.
- A Big Bucket of powder snow uses the vanilla-sized powder-snow bucket texture.
- In creative mode, some modded tanks may intercept a normal use and drain themselves without filling a Big Bucket; survival use and creative sneak-use work normally in the observed case.
