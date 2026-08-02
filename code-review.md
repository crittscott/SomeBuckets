# Some Buckets Code Review

A static review of the full source tree (27 Java files, plus resources). Nothing was built or run;
findings come from reading the code and reasoning about Minecraft 1.20.1 / Forge 47.x behavior.

Open findings are ordered by severity. Numbering is stable.

## Behavior bugs

### 11. `BBFluidLogic.releaseOneEntity` is unsound and unreachable

[BBFluidLogic.java:214-262](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L214-L262) pops the
snapshot before validating the entity type, then returns false having already discarded it —
the stored entity is gone. It also runs on the client (mutating client NBT), places a water block unconditionally
whatever the mob is, and computes `placedWater` without ever using it. Its only caller is
[Dispensers.java:120](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L120) for `entity`
mode on a Big Bucket, which no Big Bucket code path can produce.

Delete the method and the dispenser branch rather than repairing them. `MBItem.useOn` already handles release
correctly, and `MBItem.placeWaterFor` covers the water placement this method was reaching for, conditioned on the
mob actually needing it. The permission guard it now carries is inert, since its only caller passes a null player.

## Client and presentation

### 13. Twelve missing model files

Both Big Bucket models reference `cod_bucket`, `salmon_bucket`, …, `tadpole_bucket` and the `…64` variants at
predicate 0.51–0.56
([big_bucket_8.json:33-38](src/main/resources/assets/somebuckets/models/item/big_bucket_8.json#L33-L38)). None exist.
The model loader resolves override targets at load time, so this produces log spam on every client start for a code
path that cannot be reached. Delete the override entries.

### 15. Generic fluid tint falls back to white instead of `FluidData`

[FluidTintHelper.java:35](src/main/java/com/github/crittscott/somebuckets/client/FluidTintHelper.java#L35) returns
`getTintColor(stack) & 0x00FFFFFF`. The default `IClientFluidTypeExtensions` returns `0xFFFFFFFF`, so any fluid without
a custom tint yields pure white and the curated `FluidData` fallback at
[BBItem.java:177](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L177) and
[ClientColorHandlers.java:75](src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java#L75) is
never used. Treat an opaque-white result as "no tint" and use the fallback.

This now gates the Source Bucket as well: its generic-fluid model is tinted by the same `bucketTint`, so until this is
fixed a Source Bucket of any fluid without a custom `IClientFluidTypeExtensions` renders white rather than its
`FluidData` color.

### 18. `somebucket_full` texture does not exist

[source_bucket_fluid.json](src/main/resources/assets/somebuckets/models/item/source_bucket_fluid.json) is the Source
Bucket's generic-fluid model and expects a fill-shaped overlay at `somebuckets:item/somebucket_full`, matching what
`big_bucket_full` does for the Big Bucket silhouette. The texture has not been drawn, so any Source Bucket holding a
fluid other than water, lava, or milk renders with a missing-texture layer.

The 18 Mekanism per-fluid models are still unreferenced by any override list, and their `layer0` paths omit the
`mekanism/` directory the textures actually live in — `ethene_bucket` and `hydrofluoric_acid_bucket` additionally
disagree with the texture names `ethylene_bucket.png` and `hydro_fluoric_acid_bucket.png`. They are candidates for
deletion now that the generic overlay covers those fluids.

## Hygiene

- **`getOrCreateTag()` on every read.**
  [NBTUtil.getMode](src/main/java/com/github/crittscott/somebuckets/util/NBTUtil.java#L37-L40) and its neighbors attach
  an empty `CompoundTag` to any stack they inspect. These run from `getName`, `getBarWidth`, tooltips, item property
  functions, and the furnace fuel event — every rendered frame and every hopper probe. Read accessors should use
  `stack.getTag()` and handle null, as `NBTUtil.isEmptyBucket` does. This is why every empty bucket ends up carrying
  `{}` NBT to disk.
- **Dead code**: `NBTUtil.setFluid` (labeled legacy, yet the only thing `ModCreativeTabs` uses),
  `NBTUtil.getFluidTypeString`, `NBTUtil.hasFluid`, `FuelHandler.register` (a no-op; the class is
  annotation-registered), the commented-out `getBarColor` at
  [BBItem.java:141-161](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L141-L161), and
  `FluidData.getResourceLocation` / `FluidData.getByResourceLocation`.
- **Mode as bare strings.** `"fluid"`, `"milk"`, `"powder_snow"`, and `"entity"` are compared by literal across ten
  files. An enum with `fromNbt`/`toNbt` would make each switch's exhaustiveness checkable; several findings above are
  missing-branch bugs.
- **`somebuckets:spawn_eggs` does not exist**, so the Mob Bucket recipe's ingredient resolves to nothing and the item
  is unobtainable in survival.
- **`mods.toml` is the unedited MDK template**, comments and all.
- **`NBEvents`** uses a hardcoded `player.pick(5.0, …)` instead of the player's reach attribute, and
  `@Mod.EventBusSubscriber` without a `modid`. It also returns early on the client, so the vanilla-bucket-in-main-hand
  transfer path cancels only server-side and gets no hand swing — the asymmetry the `BBItem`/`SBItem`/`JBItem` entry
  points no longer have.

## Suggested order of work

Findings 15 and 18 pair up: between them they are all that stands between the Source Bucket and correct generic-fluid
rendering, and are the top item now that findings 4, 10, and 17 are fixed. Findings 11 and 13 are deletions rather
than repairs, so they are cheap. Everything else is fixable at leisure.
