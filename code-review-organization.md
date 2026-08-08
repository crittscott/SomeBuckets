# Organization Review — Some Buckets

Scope: `src/main/java/com/github/crittscott/somebuckets/**`. This looks at scattered
responsibilities, duplicated constants/helpers, and package structure vs. feature boundaries.
The codebase is generally well-factored (the `protection/` package in particular is a clean,
single-purpose group of collaborators), so this report focuses on the places where it isn't.

## 1. BBItem and SBItem duplicate several members verbatim instead of sharing a home

`BBItem` (`src/main/java/.../item/BBItem.java`) and `SBItem` (`src/main/java/.../item/SBItem.java`)
are siblings implementing the same "single-fluid-container bucket" concept (finite vs. infinite),
but nothing expresses that relationship in code — they each extend `Item` directly and re-implement
the same behavior independently. Concretely:

- **Identical shift-click-clear + cross-bucket-transfer preamble.** `BBItem.use()` lines 203–229 and
  `SBItem.use()` lines 65–87 are the same ~18-line block (raytrace air, clear on shift-click with the
  `BUCKET_EMPTY` sound, then try `Transfers.tryTransferEither`) copied with only cosmetic
  differences (`if (!level.isClientSide) NBTUtil.clearBucket(stack);` on one line vs. wrapped in
  braces on the other).
- **Identical `withPos` helper.** `BBItem.java:361-365` and `SBItem.java:127-131` are character-for-character
  the same private static method that re-targets a `BlockHitResult` at a resolved position.
- **Identical `getName()` fluid-naming branch.** `BBItem.java:122-146` and `SBItem.java:185-207` both
  branch on `Fluids.WATER` / `Fluids.LAVA` / generic fluid / milk to pick a translation-key suffix;
  the fluid-naming logic (lines 127-138 in BBItem, 190-201 in SBItem) is identical.
- **Duplicated `DRINK_DURATION_TICKS = 32` constant** — `BBItem.java:64` and `SBItem.java:48` — same
  name, same value, same purpose (milk drink duration), declared independently.
- **`SBItem`'s item model borrows `BBItem`'s constants rather than owning its own.** `SBItem`'s class
  doc (`SBItem.java:45`) says the model "uses `BBItem#CONTENT_PROPERTY`... for the shared
  content-state protocol", and `ClientSetup.onClientSetup` (`client/ClientSetup.java:33-34`)
  registers `SOURCE_BUCKET`'s item property using `BBItem.CONTENT_PROPERTY` /
  `BBItem.getContentProperty(stack)`. To understand what drives the Source Bucket's model state, a
  reader has to go read an unrelated sibling class.

**Suggestion:** Give BB and SB a shared home for what they actually share — either a small common
abstract base (e.g. `AbstractFluidBucketItem extends Item`) or a package-private static helper class
in `item/` that both delegate to for `withPos`, the shift-clear/transfer preamble, and the fluid-name
suffix lookup. Move `CONTENT_PROPERTY`/`CONTENT_EMPTY`/`CONTENT_FLUID`/`CONTENT_MILK`/
`CONTENT_POWDER_SNOW` and `DRINK_DURATION_TICKS` onto that shared type so `SBItem` isn't reaching into
`BBItem` for its own model protocol.

## 2. "Is this stack one of our fluid buckets" is reimplemented three different ways

The concept "this ItemStack is a Big/Huge Bucket or a Source Bucket" (i.e. a Some Buckets
fluid-container item, as opposed to Junk/Trash/Mob buckets) recurs at several call sites, each
written differently:

- `interaction/Transfers.java:498-500` — private `isOurs(ItemStack)`:
  `stack.getItem() instanceof BBItem || stack.getItem() instanceof SBItem`. Private, so nothing
  outside `Transfers` can reuse it.
- `event/NBEvents.java:34-35` — the same two-way `instanceof` check inlined again, verbatim, because
  `Transfers.isOurs` isn't visible from the `event` package.
- `interaction/FuelHandler.java:25-27` — a third, differently-written version of the same idea:
  `stack.is(ModItems.BIG_BUCKET_8.get()) || stack.is(ModItems.BIG_BUCKET_64.get()) ||
  stack.getItem() instanceof SBItem` (registry-object comparison for BB, `instanceof` for SB).
- `client/ClientColorHandlers.registerItemColors` (`client/ClientColorHandlers.java:24-25`) expresses
  the same grouping a fourth way: an explicit list of `RegistryObject`s passed to `event.register(...)`.

None of these four call sites can see or reuse another's definition, so the "which items are fluid
buckets" rule has to be kept in sync by hand across four places whenever a new fluid-bucket tier is
added.

**Suggestion:** Introduce a marker (a small `FluidBucketItem` interface implemented by `BBItem` and
`SBItem`, or a public static predicate alongside `NBTUtil`/`ModItems`) and have `Transfers`,
`NBEvents`, and `FuelHandler` all call it instead of re-deriving the check.

## 3. Item-bar constants triplicated across item classes

The vanilla durability-bar width (13px) and this mod's default bar tint are each declared
independently in three item classes instead of once:

- `ITEM_BAR_WIDTH = 13` is declared privately in `item/BBItem.java:59`, `item/JBItem.java:46`, and
  `item/MBItem.java:59`.
- `DEFAULT_BUCKET_BAR_COLOR = 0x3F76E4` is declared privately, with the identical value, in both
  `item/JBItem.java:47` and `item/MBItem.java:59-60` (`item/MBItem.java:60`).

`TBItem` inherits `JBItem`'s copies, so the true count of independent declarations is three
(`BBItem`, `JBItem`, `MBItem`), one of which (`DEFAULT_BUCKET_BAR_COLOR`) is bit-for-bit duplicated
between two of them.

**Suggestion:** Move `ITEM_BAR_WIDTH` (and the shared default bar color, since two of the three items
already agree on it) into one shared location — e.g. a small constants holder next to `NBTUtil`, or
public constants on a common item base if Finding 1's refactor introduces one.

## 4. Forge event subscribers are split across packages with no organizing rule

There is an `event/` package, but it holds exactly one class (`event/NBEvents.java`). Two other
`@Mod.EventBusSubscriber` classes exist elsewhere:

- `interaction/FuelHandler.java:16-20` — subscribes to `FurnaceFuelBurnTimeEvent` on the Forge bus.
- `client/ClientSetup.java:21-58` — subscribes to five different client-side Forge/FML events.

`ClientSetup` living in `client/` is defensible (it's client-only bootstrap, consistent with how the
rest of `client/` is organized). `FuelHandler`, though, is a general Forge-bus event handler exactly
like `NBEvents`, just for a different event type — there's no visible reason one lives in `event/`
and the other in `interaction/`. A reader looking for "what Forge events does this mod hook into"
has to already know to check both packages.

**Suggestion:** Either move `FuelHandler` into `event/` alongside `NBEvents`, or fold `event/` into
`interaction/` (renaming `NBEvents` to something like `CrossHandTransferEvents`) so there is one
place that owns "things this mod does in response to Forge events outside its own item classes."

## 5. Minor: duplicated random-pitch formula for the evaporation/eat sound

`fluid/FluidPlacement.java:150-151` (water evaporation in ultra-warm dimensions) and
`item/TBItem.java:137-138` and `item/TBItem.java:145-146` (Trash Bucket intake/eject sounds) all use
the identical literal pitch expression
`2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F` together with
`SoundEvents.FIRE_EXTINGUISH`. `TBItem`'s comment ("That same evaporation sound, reversed...")
shows the reuse is deliberate, but the actual formula is copied as a magic-number literal three
times rather than expressed once. Low priority, but if a fourth sound ever wants this "raspy hiss"
pitch treatment, it's worth a shared constant/helper (e.g. next to `Transfers.resolveFillSound` /
`resolveEmptySound`) rather than a fifth copy-paste.

## Areas checked with no significant findings

- **`protection/` package** — `Protections`, `ClaimProtections`, `ClaimProtectionProvider`,
  `ProtectionContext`, `ProtectionAction`, `DispenserFakePlayer` each have one clear job and compose
  cleanly; `compat/ftbchunks/FtbChunksProtection` plugs into the same seam without leaking FTB Chunks
  types elsewhere. No scattering found here.
- **`client/` package** — model loading (`ClientModelLoaders`), color handling
  (`ClientColorHandlers`/`ClientFluidColors`/`SidedFluidColors`), and Junk Bucket rendering
  (`JBRenderer`/`JBModel`/`JunkIconLayout`/`BucketMouth`) are each a focused, single-purpose class;
  `ClientSetup` is a reasonable single subscription point for all of them.
- **`fluid/` package** — `FluidPickup`/`FluidPlacement`/`FluidProvider`/`AbstractFluidHandler` are
  well-separated shared primitives; `BBFluidLogic`/`SBFluidLogic` do duplicate some structural shape
  (both have a `tryTake`/`tryPlace`/`resolvePlaceTarget` trio), but the duplication tracks a genuine
  behavioral difference (finite debit vs. infinite source, cauldron special-casing on SB only) rather
  than accidental copy-paste, so it reads more as a simplification opportunity than a scattering
  problem.
- **`gametest/` package** — one file per feature area, consistent naming, no cross-file duplication
  of production-code concepts beyond normal test setup.
