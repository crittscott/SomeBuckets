# Some Buckets Code Review

A static review of the full source tree (24 Java files, plus resources). Nothing was built or run;
findings come from reading the code and reasoning about Minecraft 1.20.1 / Forge 47.x behavior.

Findings are ordered by severity.

## Critical

### 1. Crafting remainders duplicate buckets infinitely

[BBItem.java:356-361](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L356-L361) and
[SBItem.java:196-203](src/main/java/com/github/crittscott/somebuckets/item/SBItem.java#L196-L203) return
`hasCraftingRemainingItem() == true` unconditionally, and
[NBTUtil.getCraftingRemainder](src/main/java/com/github/crittscott/somebuckets/util/NBTUtil.java#L193-L215)
returns *the same bucket item*, cleared.

The Huge Bucket recipe is a ring of eight Big Buckets
([big_bucket_64.json](src/main/resources/data/somebuckets/recipes/big_bucket_64.json)). `ResultSlot.onTake` →
`Recipe.getRemainingItems` hands all eight Big Buckets back to the player along with the Huge Bucket. One craft,
eight buckets returned, infinite Huge Buckets. The same applies to `mob_bucket` ← `source_bucket`.

Vanilla avoids this because `Items.BUCKET` has no remainder and only *filled* buckets leave one behind. The fix is
to gate on content: `hasCraftingRemainingItem(stack)` returns `!"none".equals(NBTUtil.getMode(stack))`. That
preserves the lava-fuel and milk-in-cake behavior while letting empty buckets be consumed as materials.

Gating alone is not sufficient, though: a player can fill eight Big Buckets with free water, craft the ring, and
get eight emptied Big Buckets back. Recipes that consume a bucket as *material* also need an NBT-aware ingredient
that rejects filled buckets.

This is the unresolved item at the top of `src/TODO.txt`, but the duplication consequence is worth making explicit.

### 2. Mob Bucket captures players and any other `LivingEntity`

[MBItem.java:49-89](src/main/java/com/github/crittscott/somebuckets/item/MBItem.java#L49-L89) accepts any
`LivingEntity` target and calls `target.discard()`. `Player.interactOn` routes to `interactLivingEntity` for players
too, so right-clicking another player with an empty Mob Bucket removes them from the level.
[Dispensers.java:261-290](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L261-L290) has
the same hole via `getEntitiesOfClass(LivingEntity.class, …)` — a player standing in front of the dispenser is a
candidate.

Armor stands are also `LivingEntity` and would be captured and recreated. Restrict to
`target instanceof Mob mob && mob.getType().canSerialize()`, which is the check vanilla bucketing uses. Ridden and
riding mobs should also be refused, since `saveWithoutId` does not serialize the other half of the pair.

### 3. Source Bucket placement overwrites arbitrary blocks

[SBFluidLogic.java:207-211](src/main/java/com/github/crittscott/somebuckets/fluid/SBFluidLogic.java#L207-L211)
computes `placePos` and then calls `level.setBlock` with no `canBeReplaced()` check, unlike
[BBFluidLogic.java:201](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L201), which does
check. Any block reachable at `clicked.relative(direction)` is replaced by a fluid block. Clicking through a
non-full-cube block (rails, carpet, buttons, signs) at a face whose neighbor is a chest, a spawner, or part of a build
destroys it silently, contents included. With an *infinite* fluid source this is a serious griefing primitive.

### 4. No block-protection checks anywhere

None of the world mutations check `level.mayInteract(player, pos)` or
`player.mayUseItemAt(pos, direction, stack)`; vanilla `BucketItem.use` does both. This affects
[BBFluidLogic.tryTake / tryPlaceInWorld](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L63-L235),
[SBFluidLogic](src/main/java/com/github/crittscott/somebuckets/fluid/SBFluidLogic.java#L86-L219), and the
powder-snow paths. On a server with spawn protection, these buckets ignore it. Given the project's "server friendly"
principle this belongs on the must-fix list.

Neither `BBItem.use` nor `SBItem.use` fires `ForgeEventFactory.onBucketUse` the way Forge's patched `BucketItem`
does, so protection and automation mods cannot see these buckets at all.

### 5. Client-only event type registered from the common mod class

[SomeBuckets.java:43](src/main/java/com/github/crittscott/somebuckets/SomeBuckets.java#L43) calls
`bus.addListener(this::registerItemColors)`, whose parameter is `RegisterColorHandlersEvent.Item`, and the class
imports `net.minecraft.client.color.item.ItemColors`
([SomeBuckets.java:12](src/main/java/com/github/crittscott/somebuckets/SomeBuckets.java#L12)). Forge resolves the
listener's parameter type reflectively at registration, so a dedicated server loads a client event class whose fields
reference classes absent from the server distribution. Whether that hard-crashes depends on JVM lazy resolution, but
it is exactly the pattern Forge's sided-subscriber convention exists to prevent — and the mod already does it
correctly next door in
[ClientColorHandlers.java:22](src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java#L22).

Move the Mob Bucket color registration into `ClientColorHandlers` and delete `registerItemColors` from the main class.

## Behavior bugs

### 6. Milk cross-hand transfer is unreachable

[Transfers.fluidOf](src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java#L314) represents milk
as `new FluidStack(Fluids.EMPTY, 1000)`, for which `isEmpty()` returns true;
[Transfers.java:42](src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java#L42) rejects it
immediately. The destination side fails too, since `getNormalBucketFluidStack(MILK_BUCKET)` returns
`FluidStack.EMPTY`. Every milk branch in this file — roughly 40 lines across four methods — is dead. Either give milk
a real sentinel (a private marker fluid, or carry `Kind` plus mode rather than a `FluidStack`) or delete the branches.

### 7. Transferring into an already-full vanilla bucket voids 1000 mB

[Transfers.java:201-214](src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java#L201-L214): if the
target vanilla bucket already holds the same fluid, the code reports success and drains the Big Bucket, but the vanilla
bucket cannot hold more. It should return `false`.

### 8. Capacity overflow on partial contents

[BBFluidLogic.java:68-75](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L68-L75) guards with
`current.getAmount() < capMb` and then adds a flat 1000. A pipe can leave a bucket at 7500/8000; the next world pickup
makes it 8500. The same pattern appears in
[Cauldrons.java:94](src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java#L94),
[Cauldrons.java:121](src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java#L121), and
[Dispensers.java:152-165](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L152-L165). The
guard should be `amount + 1000 <= capMb`.

### 9. Baby cows can be milked

[BBItem.java:333](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L333),
[SBItem.java:128](src/main/java/com/github/crittscott/somebuckets/item/SBItem.java#L128), and
[SBFluidLogic.tryMilkDispenser](src/main/java/com/github/crittscott/somebuckets/fluid/SBFluidLogic.java#L241-L252)
check `instanceof Cow` but not `!isBaby()`. `Cow.mobInteract` runs first and returns PASS for these items, so vanilla's
own baby check never applies.

### 10. Cauldron powder-snow deposit skips normalization

[Cauldrons.java:67-73](src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java#L67-L73) decrements
units without calling `normalizeEmptyState`, so a bucket that deposited its last snow block stays in `powder_snow` mode
at zero — wrong name, wrong model, empty bar — until some later operation happens to normalize it. Every sibling branch
in that file normalizes.

### 11. Junk Bucket absorbs items it should not

[JBItem.java:72](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L72) filters only on
`!isEmpty() && isAlive()` — no `hasPickUpDelay()` check — so it vacuums items another player just dropped or died with,
before that player can retrieve them. `TBItem` does filter on `!e.hasPickUpDelay()`
([TBItem.java:127](src/main/java/com/github/crittscott/somebuckets/item/TBItem.java#L127)); the two should agree.

### 12. Random extraction forces a client/server divergence

[JBItem.overrideOtherStackedOnMe](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L203) returns
`false` on the client and `true` on the server, so the client falls through to vanilla's slot swap while the server
extracts a random stack. The root cause is `player.getRandom()` inside a method that `AbstractContainerMenu.doClick`
runs on both sides. Vanilla's Bundle is deterministic for exactly this reason. Deterministic LIFO/FIFO extraction would
let both sides agree and remove the special-casing here and at
[JBItem.java:110](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L110).

### 13. `BBFluidLogic.releaseOneEntity` is unsound and unreachable

[BBFluidLogic.java:280-328](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L280-L328) pops the
snapshot at line 302 *before* validating the entity type, then returns false at line 305 having already discarded it —
the stored entity is gone. It also runs on the client (mutating client NBT), places a water block for any mob type, and
computes `placedWater` without ever using it. Its only caller is
[Dispensers.java:126](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L126) for `entity`
mode on a Big Bucket, which no Big Bucket code path can produce. Deleting the method and the dispenser branch is
preferable to repairing them; `MBItem.useOn` already implements this correctly.

### 14. `new Random()` per dispense

[Dispensers.java:278](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L278) allocates a
fresh `java.util.Random` on every dispenser activation. Use `level.random`.

## Client and presentation

### 15. Twelve missing model files

Both Big Bucket models reference `cod_bucket`, `salmon_bucket`, …, `tadpole_bucket` and the `…64` variants at
predicate 0.51–0.56
([big_bucket_8.json:33-38](src/main/resources/assets/somebuckets/models/item/big_bucket_8.json#L33-L38)). None exist.
The model loader resolves override targets at load time, so this produces log spam on every client start for a code
path that cannot be reached. Delete the override entries.

### 16. Source Bucket predicate thresholds do not match the code

[source_bucket.json](src/main/resources/assets/somebuckets/models/item/source_bucket.json) uses `0.3` for milk, but
`getContentProperty` returns `0.39`. It happens to work, since overrides match on ≥ and are scanned from the end, but
the Mekanism values 0.21–0.38 all fall between the lava threshold (0.2) and milk (0.3) — so a Source Bucket of brine
renders as a **lava** bucket, and 0.30–0.38 render as milk. Either give the Source Bucket its own thresholds matching
`FluidData`, or a generic fluid model.

### 17. Generic fluid tint falls back to white instead of `FluidData`

[FluidTintHelper.java:35](src/main/java/com/github/crittscott/somebuckets/client/FluidTintHelper.java#L35) returns
`getTintColor(stack) & 0x00FFFFFF`. The default `IClientFluidTypeExtensions` returns `0xFFFFFFFF`, so any fluid without
a custom tint yields pure white and the curated `FluidData` fallback at
[BBItem.java:177](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L177) and
[ClientColorHandlers.java:52](src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java#L52) is
never used. Treat an opaque-white result as "no tint" and use the fallback.

### 18. Interaction results do not follow the sided convention

[BBItem.java:259-298](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L259-L298) returns bare
`InteractionResultHolder.success(stack)` on both sides; on the server that makes `ServerGamePacketListenerImpl`
broadcast an extra swing, where vanilla returns `CONSUME` server-side via `sidedSuccess`. Conversely
[JBItem.java:66](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L66) returns `PASS` on the client
while the server returns success, so absorbing items produces no arm swing at all. The `Transfers` block at
[BBItem.java:230](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L230) has the same asymmetry.

## Hygiene

- **`getOrCreateTag()` on every read.**
  [NBTUtil.getMode](src/main/java/com/github/crittscott/somebuckets/util/NBTUtil.java#L37-L40) and its neighbors attach
  an empty `CompoundTag` to any stack they inspect. These run from `getName`, `getBarWidth`, tooltips, item property
  functions, and the furnace fuel event — every rendered frame and every hopper probe. Read accessors should use
  `stack.getTag()` and handle null. This is why every empty bucket ends up carrying `{}` NBT to disk.
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
  `@Mod.EventBusSubscriber` without a `modid`.

## Suggested order of work

Findings 1 (duplication), 2 (player capture), and 3 (block overwrite) are the ones that damage a world. 4 and 5 are
the server-correctness pair. Everything below those is fixable at leisure.
