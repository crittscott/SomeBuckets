# Targeted code review: magic numbers and magic strings

## Scope

This review examines production Java and runtime resources for unexplained literals, repeated semantic values, identifiers that should have one owner, and player-visible English text that should be localized. No build or test run was performed, in accordance with `CLAUDE.md`.

## Findings

### 1. High priority: one bucketful is hard-coded as `1000` throughout the fluid system

The codebase already uses Forge's named `FluidType.BUCKET_VOLUME` in `FluidPickup.java:65-66`, but the equivalent literal `1000` appears throughout eleven other production classes. Major concentrations include:

- `BBFluidLogic.java:76-215` for simulation, capacity tests, construction, execution, and draining;
- `SBFluidLogic.java:94-327` and `SBFluidHandler.java:17-45`;
- `Cauldrons.java:55-151` and `Dispensers.java:109-171`;
- `BBItem.java:51-363` for capacity conversion, display, drinking, and milking;
- `Transfers.java:220-351` for milk unit arithmetic;
- `NBTUtil.java:234`, `FuelHandler.java:33`, and `ModCreativeTabs.java:65-73`.

Replace these with `FluidType.BUCKET_VOLUME`. This is not merely cosmetic: `1000` currently means a capacity conversion, a minimum transaction size, a requested transfer, a stored Source Bucket amount, and a milk unit. Using Forge's constant makes every occurrence state the common contract and removes the largest literal cluster in the project.

Where arithmetic is expressed in units, use helpers such as `unitsToMb(int)`/`wholeBucketUnits(int)` if that reads better than repeated multiplication and division by `FluidType.BUCKET_VOLUME`.

### 2. High priority: Mob Bucket capacity has three independent literals

The Mob Bucket's capacity is encoded as `8` in three behaviorally significant places:

- admission rejects the ninth mob in `NBTUtil.canAcceptEntity` (`NBTUtil.java:168-172`);
- the tooltip reports `/8` in `MBItem.java:163-171`;
- the item bar divides by `8.0f` in `MBItem.java:211-213`.

A change to only one site would make the item admit, report, and draw different capacities. Define one `MAX_MOBS` constant on the Mob Bucket domain owner and use it for admission, tooltip, and bar calculation. This is a direct drift hazard, not just a preference for named numbers.

The Big/Huge and storage capacities deserve names too (`64`, `8`, and `9` in `ModItems.java:20-25`), but those are passed into item instances and thereafter mostly flow through accessors. They are therefore less risky than the Mob Bucket's independently repeated capacity.

### 3. High priority: the literal `3` means two different cauldron concepts on the same lines

`Cauldrons`, `Dispensers`, and `SBFluidLogic` repeatedly use `3` both as the full value of `LayeredCauldronBlock.LEVEL` and as the flags argument to `Level.setBlock`. For example:

```java
level.setBlock(pos,
        Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3),
        3);
```

This pattern occurs across `Cauldrons.java:59-174`, `Dispensers.java:65-159`, and `SBFluidLogic.java:88-300`. The two threes happen to have the same value but have no semantic relationship.

Use `LayeredCauldronBlock.MAX_FILL_LEVEL` for level comparisons/assignments and `Block.UPDATE_ALL` for the update flags. If the mapped Minecraft max-level constant is not accessible, introduce a project constant named `FULL_CAULDRON_LEVEL`; do not leave the two meanings visually indistinguishable.

### 4. Medium priority: Big Bucket identity is inferred from a magic capacity and hard-coded translation fragments

`BBItem.getName` decides the registered item identity with:

```java
String bucketType = (getCapacityUnits() == 8) ? "big_bucket_8" : "big_bucket_64";
```

It then constructs keys from the hard-coded prefix `"item.somebuckets."` and suffixes such as `".water"` (`BBItem.java:94-116`). Any non-8 tier would silently be named as the 64-unit Huge Bucket, even if it had another registry name. `SBItem.java:176-195` similarly repeats its complete translation-key stem.

Derive the base key from the item's description ID and append only the content suffix, or pass an explicit translation stem as item metadata. Capacity should determine capacity, not identity. This removes the magic `8`, the duplicated namespace/item IDs, and the assumption that there can only ever be two finite tiers.

### 5. Medium priority: model-property identifiers and values are a cross-file implicit protocol

`SomeBuckets.java:89-101` registers the property names `"bb_content"` and `"filled"`. `BBItem.getContentProperty` returns `0.0f`, `0.1f`, `0.2f`, and `0.3f` for content modes (`BBItem.java:56-69`), while the item model JSON files repeat the property identifier and threshold values, for example `big_bucket_8.json:10-11`, `big_bucket_64.json:10-11`, and `source_bucket.json:10`. The Mob Bucket model similarly repeats `somebuckets:filled` and `1.0`.

These values form a protocol between Java and resource JSON, but nothing names or verifies that protocol. At minimum, define Java constants for the property `ResourceLocation`s and predicate values and give the constants names such as `CONTENT_FLUID`, `CONTENT_MILK`, and `CONTENT_POWDER_SNOW`. Better, generate these model definitions or add a focused resource test that asserts that the shipped JSON predicates match the registered property IDs and returned values. Java constants alone cannot prevent the JSON half from drifting.

The `0.1` spacing is not itself meaningful; named mode values make it clear that callers must not perform arithmetic with them.

### 6. Medium priority: the mod namespace is repeated instead of using `SomeBuckets.MODID`

`SomeBuckets.MODID` exists, but the literal `"somebuckets"` is repeated in Java at:

- `EmptyBucketIngredient.java:31`;
- `SpawnEggIngredient.java:21`;
- `MBItem.java:47`;
- `BucketMouth.java:31`;
- `ClientColorHandlers.java:22`;
- `ModCreativeTabs.java:20`.

Use `SomeBuckets.MODID` for Java-side `ResourceLocation`s and the event-subscriber annotation. The creative-tab registry path may remain a separate named path, but it should not masquerade as a second declaration of the namespace. Namespace strings in JSON resources necessarily remain literal because those files cannot reference the Java constant.

### 7. Medium priority: shared item-bar geometry and color are unnamed and duplicated

Big, Junk, and Mob Buckets each multiply fill fraction by `13.0f`/`13.0F` (`BBItem.java:124-131`, `JBItem.java:80-86`, `MBItem.java:211-213`). Junk and Mob Buckets also return the same raw blue color `0x3F76E4` (`JBItem.java:89-91`, `MBItem.java:216-218`). The Big Bucket contains four more unexplained raw colors at `BBItem.java:142-153`.

Introduce named presentation constants such as `ITEM_BAR_WIDTH`, `DEFAULT_BUCKET_BAR_COLOR`, `EMPTY_BAR_COLOR`, `MILK_BAR_COLOR`, and `POWDER_SNOW_BAR_COLOR`, preferably in a small common item-presentation helper. This makes intentional visual sharing explicit and keeps future palette changes synchronized.

### 8. Low priority: several isolated semantic literals should be named

These values are not dangerous enough to justify a framework, but each benefits from a local constant:

- `32` ticks for milk drinking in both `BBItem.java:316-321` and `SBItem.java:147-154` (`DRINK_DURATION_TICKS`);
- `20000` ticks of lava fuel in `FuelHandler.java:34-35` (`LAVA_BUCKET_BURN_TIME_TICKS`);
- `1.5D` on all three axes for Junk Bucket pickup in `JBItem.java:106` (`PICKUP_RADIUS`), matching the already named Trash radius in `TBItem.java:26`;
- `6` passed as dispenser ejection speed in `StorageBucketDispenser.java:75` (`DISPENSER_EJECTION_SPEED`, or an explicitly named vanilla-parity constant);
- `8` smoke particles in `FluidPlacement.java:138` (`EVAPORATION_PARTICLE_COUNT`);
- `10` as the NBT list element type in `NBTUtil.java:181`, which should simply be `Tag.TAG_COMPOUND` as it already is at `NBTUtil.java:122`.

The common `1.0F` sound volume/pitch arguments, zero/one counts, tank index zero, channel masks, shifts, and ordinary loop bounds are sufficiently conventional or local; naming all of them would add noise rather than clarity.

### 9. Low priority: the custom-rendering coordinate scale is repeated across classes

The Junk renderer uses Minecraft's 0-to-16 item-model coordinate space in `BucketMouth`, `JunkIconLayout`, and `JBRenderer`. The literal `16.0F` appears repeatedly in image scaling, placement constraints, pose transforms, quad construction, and UV conversion (`BucketMouth.java:59-77`, `JunkIconLayout.java:65`, `JBRenderer.java:74-75,171-222`).

Define one package-level `ITEM_MODEL_SIZE = 16.0F` (and, if useful, its reciprocal) for this rendering subsystem. The existing comments explain the coordinate system well; a shared name would make the mathematical relationship between the three classes executable rather than documentary only.

## Localization review

No player-visible English literals are missing from `en_us.json`. Item names, dynamic bucket names, tooltips, and the creative-tab title all use `Component.translatable`, and every referenced key is present in `src/main/resources/assets/somebuckets/lang/en_us.json`.

The remaining English strings in production Java are server-config comments (`ServerConfig.java:19-22`), a log warning (`SBPolicy.java:39-43`), and a JSON parse exception (`EmptyBucketIngredient.java:102`). They are diagnostics or configuration-file documentation, not player-facing components, and do not belong in `en_us.json`. The stable fake-player name `[SomeBuckets]` likewise must remain a literal identity rather than localized text.

## Suggested order

1. Replace fluid-unit literals with `FluidType.BUCKET_VOLUME`.
2. Centralize Mob Bucket capacity and remove capacity-based name selection.
3. Replace both meanings of cauldron `3` with their named constants.
4. Name and verify the Java/resource model-property protocol.
5. Consolidate namespace, item-bar, and smaller behavior constants.

These changes are mechanical except for the translation-key and model-protocol cleanup; those two should be accompanied by focused resource assertions because they cross Java/resource boundaries.
