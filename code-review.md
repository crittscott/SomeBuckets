# Some Buckets Code Review

A static review of the full source tree (26 Java files, plus resources). Nothing was built or run;
findings come from reading the code and reasoning about Minecraft 1.20.1 / Forge 47.x behavior.

Open findings are ordered by severity. Numbering is stable, so resolved entries keep their original numbers.

## Resolved

### 1. Source Bucket placement did not check the target block

`SBFluidLogic.tryPlaceInWorld` picked a placement position and wrote it with no test on the block already there,
unlike the Big Bucket. Both now call
[FluidPlacement.emptyContents](src/main/java/com/github/crittscott/somebuckets/fluid/FluidPlacement.java#L37),
which follows the vanilla bucket rules — liquid containers fill in place, replaceable blocks break with their drops,
water evaporates in ultra-warm dimensions, and a refusing target falls through to the neighbor along the clicked face.
The buckets now differ only in whether they charge a unit afterward.

The Critical rating rested on a griefing scenario that does not hold up: hitting a block face requires the ray to
approach from that side, so an occluding neighbor such as a chest stops it first. With a vanilla client the reachable
targets were non-solid blocks like torches and rails, which the Source Bucket deleted without drops. The real defects
were that dropless deletion, a clicked face taken from the client packet without validation on the `useOn` path, and
one this entry missed: no ultra-warm check, so a water Source Bucket left a permanent water source in the Nether.

The shared method also moved the Big Bucket off its hand-rolled `WATERLOGGED` manipulation onto
`canPlaceLiquid`/`placeLiquid`, and off the strict `canBeReplaced()` onto the fluid-aware overload, so both buckets
flood non-solid blocks the way vanilla does.

### 3. Client-only event type registered from the common mod class

Mob Bucket color registration moved into
[ClientColorHandlers](src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java#L26), which is
already `@Mod.EventBusSubscriber(value = Dist.CLIENT)`. `SomeBuckets` no longer names `RegisterColorHandlersEvent` or
`ItemColors`. `clientSetup` stays in the main class deliberately: `FMLClientSetupEvent` ships on both distributions and
never fires on a server, so there is nothing client-only for `addListener` to resolve.

## Critical

### 2. No block-protection checks anywhere

None of the world mutations check `level.mayInteract(player, pos)` or
`player.mayUseItemAt(pos, direction, stack)`; vanilla `BucketItem.use` does both. This affects
[BBFluidLogic.tryTake / tryPlaceInWorld](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L41-L169),
[SBFluidLogic](src/main/java/com/github/crittscott/somebuckets/fluid/SBFluidLogic.java#L39-L213), and the
powder-snow paths. On a server with spawn protection, these buckets ignore it. Given the project's "server friendly"
principle this belongs on the must-fix list.

Neither `BBItem.use` nor `SBItem.use` fires `ForgeEventFactory.onBucketUse` the way Forge's patched `BucketItem`
does, so protection and automation mods cannot see these buckets at all.

The check belongs at the item entry points rather than inside `FluidPlacement`, since the dispenser paths have no
player. Finding 1's shared placement method gives it one obvious place to sit.

## Behavior bugs

### 4. Milk cross-hand transfer is unreachable

[Transfers.fluidOf](src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java#L314) represents milk
as `new FluidStack(Fluids.EMPTY, 1000)`, for which `isEmpty()` returns true;
[Transfers.java:42](src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java#L42) rejects it
immediately. The destination side fails too, since `getNormalBucketFluidStack(MILK_BUCKET)` returns
`FluidStack.EMPTY`. Every milk branch in this file — roughly 40 lines across four methods — is dead. Either give milk
a real sentinel (a private marker fluid, or carry `Kind` plus mode rather than a `FluidStack`) or delete the branches.

### 5. Transferring into an already-full vanilla bucket voids 1000 mB

[Transfers.java:201-214](src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java#L201-L214): if the
target vanilla bucket already holds the same fluid, the code reports success and drains the Big Bucket, but the vanilla
bucket cannot hold more. It should return `false`.

### 6. Capacity overflow on partial contents

[BBFluidLogic.java:66-73](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L66-L73) guards with
`current.getAmount() < capMb` and then adds a flat 1000. A pipe can leave a bucket at 7500/8000; the next world pickup
makes it 8500. The same pattern appears in
[Cauldrons.java:94](src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java#L94),
[Cauldrons.java:121](src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java#L121), and
[Dispensers.java:146-159](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L146-L159). The
guard should be `amount + 1000 <= capMb`.

### 7. Baby cows can be milked

[BBItem.java:333](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L333),
[SBItem.java:128](src/main/java/com/github/crittscott/somebuckets/item/SBItem.java#L128), and
[SBFluidLogic.tryMilkDispenser](src/main/java/com/github/crittscott/somebuckets/fluid/SBFluidLogic.java#L241-L252)
check `instanceof Cow` but not `!isBaby()`. `Cow.mobInteract` runs first and returns PASS for these items, so vanilla's
own baby check never applies.

### 8. Cauldron powder-snow deposit skips normalization

[Cauldrons.java:67-73](src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java#L67-L73) decrements
units without calling `normalizeEmptyState`, so a bucket that deposited its last snow block stays in `powder_snow` mode
at zero — wrong name, wrong model, empty bar — until some later operation happens to normalize it. Every sibling branch
in that file normalizes.

### 9. Junk Bucket absorbs items it should not

[JBItem.java:72](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L72) filters only on
`!isEmpty() && isAlive()` — no `hasPickUpDelay()` check — so it vacuums items another player just dropped or died with,
before that player can retrieve them. `TBItem` does filter on `!e.hasPickUpDelay()`
([TBItem.java:127](src/main/java/com/github/crittscott/somebuckets/item/TBItem.java#L127)); the two should agree.

### 10. Random extraction forces a client/server divergence

[JBItem.overrideOtherStackedOnMe](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L203) returns
`false` on the client and `true` on the server, so the client falls through to vanilla's slot swap while the server
extracts a random stack. The root cause is `player.getRandom()` inside a method that `AbstractContainerMenu.doClick`
runs on both sides. Vanilla's Bundle is deterministic for exactly this reason. Deterministic LIFO/FIFO extraction would
let both sides agree and remove the special-casing here and at
[JBItem.java:110](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L110).

### 11. `BBFluidLogic.releaseOneEntity` is unsound and unreachable

[BBFluidLogic.java:214-262](src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java#L214-L262) pops the
snapshot before validating the entity type, then returns false having already discarded it —
the stored entity is gone. It also runs on the client (mutating client NBT), places a water block unconditionally
whatever the mob is, and computes `placedWater` without ever using it. Its only caller is
[Dispensers.java:120](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L120) for `entity`
mode on a Big Bucket, which no Big Bucket code path can produce.

Delete the method and the dispenser branch rather than repairing them. `MBItem.useOn` already handles release
correctly, and `MBItem.placeWaterFor` now covers the water placement this method was reaching for, conditioned on the
mob actually needing it.

### 12. `new Random()` per dispense

[Dispensers.java:268](src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java#L268) allocates a
fresh `java.util.Random` on every dispenser activation. Use `level.random`.

## Client and presentation

### 13. Twelve missing model files

Both Big Bucket models reference `cod_bucket`, `salmon_bucket`, …, `tadpole_bucket` and the `…64` variants at
predicate 0.51–0.56
([big_bucket_8.json:33-38](src/main/resources/assets/somebuckets/models/item/big_bucket_8.json#L33-L38)). None exist.
The model loader resolves override targets at load time, so this produces log spam on every client start for a code
path that cannot be reached. Delete the override entries.

### 14. Source Bucket predicate thresholds do not match the code

[source_bucket.json](src/main/resources/assets/somebuckets/models/item/source_bucket.json) uses `0.3` for milk, but
`getContentProperty` returns `0.39`. It happens to work, since overrides match on ≥ and are scanned from the end, but
the Mekanism values 0.21–0.38 all fall between the lava threshold (0.2) and milk (0.3) — so a Source Bucket of brine
renders as a **lava** bucket, and 0.30–0.38 render as milk. Either give the Source Bucket its own thresholds matching
`FluidData`, or a generic fluid model.

### 15. Generic fluid tint falls back to white instead of `FluidData`

[FluidTintHelper.java:35](src/main/java/com/github/crittscott/somebuckets/client/FluidTintHelper.java#L35) returns
`getTintColor(stack) & 0x00FFFFFF`. The default `IClientFluidTypeExtensions` returns `0xFFFFFFFF`, so any fluid without
a custom tint yields pure white and the curated `FluidData` fallback at
[BBItem.java:177](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L177) and
[ClientColorHandlers.java:75](src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java#L75) is
never used. Treat an opaque-white result as "no tint" and use the fallback.

### 16. Interaction results do not follow the sided convention

[BBItem.java:259-298](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L259-L298) returns bare
`InteractionResultHolder.success(stack)` on both sides; on the server that makes `ServerGamePacketListenerImpl`
broadcast an extra swing, where vanilla returns `CONSUME` server-side via `sidedSuccess`. Conversely
[JBItem.java:66](src/main/java/com/github/crittscott/somebuckets/item/JBItem.java#L66) returns `PASS` on the client
while the server returns success, so absorbing items produces no arm swing at all. The `Transfers` block at
[BBItem.java:230](src/main/java/com/github/crittscott/somebuckets/item/BBItem.java#L230) has the same asymmetry.

Related: the cross-hand transfer in `BBItem.use` only runs when the point-of-view raytrace returns `MISS`, so it
engages only while aiming at open air with nothing within five blocks. Aiming at the ground routes to placement
instead. Whatever else changes here, that makes the feature close to undiscoverable.

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
  `@Mod.EventBusSubscriber` without a `modid`.

## Suggested order of work

Finding 2 is what remains of the server-correctness work and is now the top item; the shared placement method from
finding 1 is in place for it to build on. Everything below it is fixable at leisure. Findings 11 and 13 are deletions
rather than repairs, so they are cheap.
