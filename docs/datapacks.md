# Some Buckets data packs (Minecraft 1.20.1)

Everything a data pack can change in Some Buckets, with examples. Files go in a data pack under `data/<namespace>/...` like any vanilla data; a file with the same namespace and path as one of the mod's replaces it. Run `/reload` after editing.

Some things are not data-driven: the Source Bucket allowlist is server configuration (see [Not controlled by data packs](#not-controlled-by-data-packs)), and bucket capacities are fixed.

## Recipes

All six recipes are ordinary crafting recipes and can be replaced or removed.

| Recipe id | Result |
| --- | --- |
| `somebuckets:big_bucket_8` | Big Bucket |
| `somebuckets:big_bucket_64` | Huge Bucket |
| `somebuckets:junk_bucket` | Junk Bucket |
| `somebuckets:trash_bucket` | Trash Bucket |
| `somebuckets:source_bucket` | Source Bucket |
| `somebuckets:mob_bucket` | Mob Bucket |

To replace a recipe, put your own recipe at the same path, for example `data/somebuckets/recipes/source_bucket.json`.

To remove a recipe, replace it with a recipe whose load condition is never met. One file works on both loaders:

```json
{
  "conditions": [ { "type": "forge:false" } ],
  "fabric:load_conditions": [ { "condition": "fabric:any_mods_loaded", "values": [] } ]
}
```

### Custom ingredients

Some Buckets adds two recipe ingredient types. Each needs both a `type` key (read by Forge) and a `fabric:type` key (read by Fabric).

`somebuckets:empty_bucket` matches the named Some Buckets item only while it holds nothing. Use it whenever a recipe consumes a bucket, because a filled bucket would otherwise survive the craft as its own remainder.

```json
{ "type": "somebuckets:empty_bucket", "fabric:type": "somebuckets:empty_bucket", "item": "somebuckets:junk_bucket" }
```

`somebuckets:spawn_egg` matches any spawn egg, including modded ones. The egg only counts as an ingredient; it does not decide which mob a Mob Bucket can hold.

```json
{ "type": "somebuckets:spawn_egg", "fabric:type": "somebuckets:spawn_egg" }
```

For example, a cheaper Source Bucket:

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "type": "somebuckets:empty_bucket", "fabric:type": "somebuckets:empty_bucket", "item": "somebuckets:big_bucket_64" },
    { "item": "minecraft:heart_of_the_sea" }
  ],
  "result": { "item": "somebuckets:source_bucket" }
}
```

## Mobs the Mob Bucket cannot capture

The entity-type tag `somebuckets:uncapturable` lists mobs the Mob Bucket refuses. The mod ships it with the Ender Dragon, the Wither, Shiny Mobs' shiny Wither (when that mod is installed), and the loader's boss tags (`#forge:bosses`, `#c:bosses`), so bosses that other mods tag are covered automatically.

Players, non-mob entities, and mobs that are riding or being ridden can never be captured, whatever the tag says.

To add mobs, create `data/somebuckets/tags/entity_types/uncapturable.json` in your pack. With `"replace": false` your entries are added to the shipped list:

```json
{
  "replace": false,
  "values": [
    "minecraft:warden",
    "minecraft:elder_guardian",
    { "id": "alexsmobs:void_worm", "required": false }
  ]
}
```

Mark entries from mods that might not be installed with `"required": false`; otherwise a missing entity makes the whole tag fail to load.

To replace the shipped list entirely, for example to allow capturing the Wither, use `"replace": true`:

```json
{
  "replace": true,
  "values": [ "minecraft:ender_dragon" ]
}
```

## Structure loot

Each bucket reward in structure chests is its own loot table, `somebuckets:inject/<reward>`. When a target chest is opened, that table is rolled once and its result is added to the chest's normal loot. Replacing an inject table changes the reward everywhere it appears.

| Inject table | Gives | Chance | Added to |
| --- | --- | --- | --- |
| `somebuckets:inject/junk_bucket` | Junk Bucket | 2% | All village chests |
| `somebuckets:inject/big_bucket` | Big Bucket | 5% | Every vanilla structure chest except village chests, plus the jungle-temple dispenser |
| `somebuckets:inject/source_bucket_bastion` | Source Bucket | 10% | All bastion chests |
| `somebuckets:inject/source_bucket_ocean` | Source Bucket | 5% | Buried treasure, shipwreck chests, underwater-ruin chests |
| `somebuckets:inject/trash_bucket` | Trash Bucket | 5% | End City treasure, stronghold chests |
| `somebuckets:inject/mob_bucket` | Mob Bucket | 5% | End City treasure, stronghold chests |
| `somebuckets:inject/huge_powder_snow_bucket` | Huge Bucket full of powder snow | 5% | Igloo chest, Ancient City ice box |

Each roll is independent, so one chest can receive several buckets. Rolls are added even when another data pack replaces the target chest's own loot table.

### Changing a chance

Copy the inject table to `data/somebuckets/loot_tables/inject/<reward>.json` in your pack and edit it. For example, to make Mob Buckets a 20% find:

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "conditions": [ { "condition": "minecraft:random_chance", "chance": 0.2 } ],
      "entries": [ { "type": "minecraft:item", "name": "somebuckets:mob_bucket" } ]
    }
  ]
}
```

The table can hold anything a vanilla loot table can: more rolls, different items, or functions.

### Disabling a reward

Replace the inject table with one that has no pools:

```json
{ "type": "minecraft:chest", "pools": [] }
```

### Filled buckets

Bucket contents are item NBT, so a loot table can produce a filled bucket with `minecraft:set_nbt`. The shipped powder-snow reward does this:

```json
{
  "type": "minecraft:item",
  "name": "somebuckets:big_bucket_64",
  "functions": [ { "function": "minecraft:set_nbt", "tag": "{Mode:\"powder_snow\",Powder:64}" } ]
}
```

Other contents use the same NBT the mod writes. A bucket of 3 units of water is `{Mode:"fluid",FluidStack:{FluidName:"minecraft:water",Amount:3000}}`. Amounts are in millibuckets (1,000 per unit), and a Big Bucket holds 8 units and a Huge Bucket 64. Milk is `{Mode:"milk",Amount:N}`, again 1,000 per unit. An assigned Source Bucket holds exactly one unit: `{Mode:"fluid",FluidStack:{FluidName:"minecraft:lava",Amount:1000}}`.

### Changing which chests get a reward (Forge only)

On Forge, each reward is attached to its target chests by a global loot modifier at `data/somebuckets/loot_modifiers/<reward>.json`. Replace that file to change the targets:

```json
{
  "type": "somebuckets:add_table",
  "conditions": [
    {
      "condition": "minecraft:any_of",
      "terms": [
        { "condition": "forge:loot_table_id", "loot_table_id": "minecraft:chests/end_city_treasure" },
        { "condition": "forge:loot_table_id", "loot_table_id": "minecraft:chests/woodland_mansion" }
      ]
    }
  ],
  "table": "somebuckets:inject/mob_bucket"
}
```

The `somebuckets:add_table` modifier rolls the named loot table into the chest whenever its conditions pass. You can use it in your own modifiers to add any loot table to any chest.

On Fabric, the target chests are fixed by the mod. Data packs there can change what each reward gives and how often, but not where it appears.

## Not controlled by data packs

- **Source Bucket allowlist.** Which fluids a Source Bucket accepts is server configuration: `serverconfig/somebuckets-server.toml` in the world folder on Forge, `config/somebuckets-server.json` on Fabric. See the README.
- **Capacities.** Big Bucket 8 units, Huge Bucket 64, Junk Bucket 9 stacks, Trash Bucket 1 stack, Mob Bucket 8 mobs.
- **Item appearance.** Models and textures belong to resource packs, not data packs. So does `assets/somebuckets/mob_bucket_colors.json`, which maps an entity type with no spawn egg to the type whose egg colors its Mob Bucket borrows (for example `"shiny:shiny_cod": "minecraft:cod"`). Each pack's entries are added to the shipped ones, later packs winning, and entries for mods that aren't installed are ignored.
