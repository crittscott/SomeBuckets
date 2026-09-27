## User

I have a minecraft mod that I wrote first for 1.20.1, now I has a 1.21.1 version and had to consider, belatedly, how to handle people migrating their worlds. I think it was done badly. The details do not matter. I'm interested in discussing the general approach. What is expected of me as a mod author for world migrations? Do most other mods not worry about that? Should the migration be done in the mod or as some sort of stand alone? One issue I have is that my mod creates items and blocks that serve as inventory and can themselves be stored in, say, refined storage.



## Agent

The general expectation is not “you must be able to migrate every historical world forever.” But if you publish a new version of the same mod for a newer Minecraft release, users will normally assume that opening an existing world with the new version will preserve their mod content unless you explicitly say otherwise.

There is no universal modding convention that requires this, and plenty of mods handle it imperfectly. Minecraft itself also makes this unusually awkward because mod state can exist in many different persistence domains. The 1.20.1 → 1.21.1 jump is particularly unpleasant because Minecraft replaced the old free-form ItemStack NBT model with data components in 1.20.5. Unconverted arbitrary item data winds up in minecraft:custom_data, while modern mod-specific item state is naturally represented by registered data components with codecs. 

My Dude, I would think about the problem in terms of ownership of the data rather than “upgrading the world.”

Your mod owns several persistent schemas. For example:

- block/entity state;
- item state;
- level-wide saved state;
- configuration or registries that affect interpretation of saved state.

Each of those should ideally have a format version. Then the current mod knows how to read older versions of its own data and convert them to its current in-memory representation.

That is substantially better than thinking in terms of one giant operation saying “convert a 1.20.1 world to 1.21.1.”

For example, conceptually:





```
disk representation    schema 0       ↓    schema 1       ↓    schema 2       ↓ current object model
```

The important property is that the reader accepts old forms. Once the object is subsequently saved, you write only the current form.

That gives you lazy migration.

For a block entity, this is straightforward. Minecraft loads the block entity, your deserializer sees schema_version = 1, converts it into the current representation, and later it gets written as schema_version = 2.

For an item, the same idea is even more valuable. Since 1.20.5, ItemStacks use data components, and custom persistent components are explicitly serialized through a Codec.  You can have your 1.21.1 code recognize whatever legacy representation survives the vanilla upgrade and turn it into your current component representation when the stack passes through code you control.

That brings us to Refined Storage, which is the reason I would strongly prefer migration inside the mod rather than a standalone world converter.

An ItemStack containing one of your items can be anywhere. Not merely:





```
player inventory chest block entity item entity
```

but potentially:





```
another mod's backpack a machine's internal inventory Refined Storage Applied Energistics an entity inventory an item containing another inventory some mod's SavedData
```

Minecraft inventories are deliberately abstract; even vanilla-compatible container state can be backed by arbitrary implementations. NeoForge's own documentation points out that item-containing systems need not be ordinary block inventories and specifically mentions modded items such as backpacks. 

A standalone converter therefore has a nasty architectural problem: it would need to understand Refined Storage's on-disk representation, AE's representation, backpack mods' representations, and potentially every other container your item could inhabit.

Your mod does not and should not know those things.

The much stronger boundary is:

> When an ItemStack of one of my items becomes visible to my mod, my mod must be capable of interpreting every persistent representation of that item that I claim to support.

Then Refined Storage can remain completely opaque. It merely stores an ItemStack. When it eventually reconstructs the stack and gives it back to Minecraft, your code can deal with it.

There is one complication. A codec normally has to decode the serialized representation before you have an ItemStack to work with. Therefore, for a transition as large as 1.20.1 → 1.21.1, you may need a small compatibility layer that looks for the old information in minecraft:custom_data, rather than expecting your new custom component to magically exist. Minecraft deliberately retained arbitrary data there during the component transition. 

I would consequently design future versions roughly like this:





```
Current item representation:    mymod:inventory_data = {        version: 3,        ...    } On use/access/load:    if current component exists:        decode it        migrate older component schema if necessary    else if recognizable legacy data exists in minecraft:custom_data:        parse legacy representation        create current component        remove legacy data that belongs to you    else:        use appropriate defaults / report corrupt data
```

There is another distinction worth making. I would separate compatibility migration from bulk migration.

Compatibility migration belongs in the mod. It should be safe, local, and preferably idempotent. Something like:





```
old representation → canonical current representation
```

running twice should not damage anything.

A bulk migrator can be useful as an optional tool when you really need every object rewritten immediately—for example because the old representation prevents the world from loading, because registry IDs have changed, or because you need to perform a global transformation with cross-object knowledge.

But I would regard that as the exceptional case, not the basic upgrade mechanism.

One especially important rule is: preserve registry identifiers whenever possible.

If





```
mymod:storage_box
```

still conceptually means the same thing, keep that identifier. Changing IDs turns a schema migration into a registry-remapping problem, which is much harder and more fragile.

Similarly, persistent fields should not implicitly mean “whatever the current Java class expects.” Treat the disk format as an API. Once released, it exists independently of your current implementation.

So if today you have:





```
energy: 123 items: [...]
```

and someday you want:





```
contents: {    energy: 123    inventory: [...] }
```

don't merely change the serializer. Give the structure a version and retain the ability to read the old one.

As for what is reasonable to promise users, I think a sensible policy for a modest mod is something like:

> Worlds from the immediately previous supported Minecraft version can be upgraded directly. Back up the world first. Older versions may require upgrading through intermediate releases.

That prevents the compatibility code from becoming archaeological sediment forever.

You can then deliberately support:





```
1.20.1 mod format       ↓ 1.21.1 mod format       ↓ future format
```

rather than requiring a 2029 release to contain direct knowledge of every format you have ever produced. The migration functions themselves can be chained.

I would also make one rule absolute: never silently discard state merely because you don't recognize its version.

If you encounter:





```
version = 17
```

and your code understands only through version 4, failing loudly or marking the object unreadable is much safer than interpreting it as version 0 and overwriting it.

So my basic architecture would be:

1. Stable registry IDs.
2. Explicit schema versions on persistent data you control.
3. Migration functions between schema versions.
4. Read old → convert in memory → write current.
5. Lazy item migration so items hidden inside other mods eventually migrate naturally.
6. A special compatibility reader for transitions where Minecraft itself changed the surrounding serialization format, such as the 1.20.5 item-component change.
7. Standalone/bulk migration only for cases that genuinely cannot be handled upon reading an object.
8. Published statement defining which upgrade paths you support.

And because of your “inventory item inside Refined Storage” example, I would emphatically avoid a migration strategy whose correctness depends upon enumerating every instance of your item in the world. That is essentially impossible to guarantee in a modded environment. The persistence boundary you can reliably own is the serialized state of your own object once it reaches you, not every place another mod might have squirreled that object away.

If you describe how your 1.20.1 inventory items stored their contents and what the 1.21.1 representation looks like, I can also discuss how I would structure that particular transition without getting bogged down in your existing implementation.