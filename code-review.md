# Some Buckets Code Review

A static review of the full source tree (39 Java files, plus resources). Nothing was built or run;
findings come from reading the code and reasoning about Minecraft 1.20.1 / Forge 47.x behavior.

Open findings are ordered by severity. Numbering is stable.

## Client and presentation

### 15. Generic fluid tint falls back to white instead of `FluidData`

[FluidTintHelper.java:35](src/main/java/com/github/crittscott/somebuckets/client/FluidTintHelper.java#L35) returns
`getTintColor(stack) & 0x00FFFFFF`. The default `IClientFluidTypeExtensions` returns `0xFFFFFFFF`, so any fluid without
a custom tint yields pure white and the curated `FluidData` fallback at
[BBItem.java:155](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L155) and
[ClientColorHandlers.java:74](src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java#L74) is
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

- **`mods.toml` is the unedited MDK template**, comments and all.

## Suggested order of work

Findings 15 and 18 pair up: between them they are all that stands between the Source Bucket and correct generic-fluid
rendering, and are the top item now that findings 4, 10, 11, 13, and 17 are fixed. Everything else is fixable at
leisure.
