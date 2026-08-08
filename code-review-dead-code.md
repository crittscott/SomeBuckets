# Dead Code and Duplication Review

Scope: all 58 `.java` files under `src/main/java/com/github/crittscott/somebuckets` (main
mod source; no Forge/Minecraft decompilation, no build/run). Verified by reading each file and
grepping the whole `src` tree for references before calling anything unused. Overall the codebase
is unusually clean — most classes are tightly used, and a dedicated pass over every `private`
field/method and every import in all 58 files (cross-checked independently) turned up zero unused
private members and zero unused imports. The handful of real findings below are the exceptions.

## Dead code

### `GameTestSupport.absolute(GameTestHelper, int, int, int)` is never called
**File:** `src/main/java/com/github/crittscott/somebuckets/gametest/GameTestSupport.java:130-132`

```java
static BlockPos absolute(GameTestHelper helper, int x, int y, int z) {
    return helper.absolutePos(new BlockPos(x, y, z));
}
```

Checked: `grep`ed for `GameTestSupport.absolute(` and for a bare `absolute(` (in case of a static
import) across the entire `src` tree. The only match is the declaration itself. Every call site in
the test suite instead uses `helper.absolutePos(new BlockPos(...))` directly, or the sibling helper
`GameTestSupport.hit(...)`/`GameTestSupport.spawn(...)` which take a relative `BlockPos` rather than
raw coordinates. `absolute` is package-private, has no other callers, and can be removed.

## Duplicated code

### `withPos` is copy-pasted verbatim between `BBItem` and `SBItem`
**Files:**
- `src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:362-365`
- `src/main/java/com/github/crittscott/somebuckets/item/SBItem.java:128-131`

Both classes declare the identical private static helper:

```java
private static BlockHitResult withPos(BlockHitResult base, BlockPos pos) {
    return pos.equals(base.getBlockPos()) ? base
            : new BlockHitResult(base.getLocation(), base.getDirection(), pos, base.isInside());
}
```

Byte-for-byte identical logic and doc comment (the one-line comment above each is worded the same
way too: "`{@code base}` re-targeted at `pos`..."). Since both are `private`, neither can reference
the other's copy; this is exactly the kind of duplication that results from one class being copied
to create the other. A natural home is a shared utility (e.g. a `static` helper on `Transfers`,
which both `BBItem` and `SBItem` already depend on) or a small shared base/interface for the two
finite/infinite bucket items.

### `getName(ItemStack)` fluid-name resolution is duplicated between `BBItem` and `SBItem`
**Files:**
- `src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:122-146`
- `src/main/java/com/github/crittscott/somebuckets/item/SBItem.java:185-207`

Both override `getName` with the same water/lava/generic-fluid/milk branching over
`NBTUtil.getMode(stack)` and `NBTUtil.getFluidStack(stack)`, building `Component.translatable(baseKey
+ ".water")`, `".lava")`, `".fluid", fluidName)`, and `".milk")` the same way. The only difference is
that `BBItem` has an extra `POWDER_SNOW` branch (`SBItem` has no powder-snow mode). This is the same
logic maintained in two places; a shared helper (e.g. `static Component resolveBucketName(String
baseKey, ItemStack stack)` taking an optional powder-snow flag, or living on `NBTUtil`/`BBItem` and
called by both) would remove the duplication and the risk of the two drifting apart (e.g. one adding
a new fluid special-case like water/lava without the other).

### Bar-rendering constants copy-pasted across `BBItem`, `JBItem`, and `MBItem`
**Files:**
- `src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:59` — `private static final int ITEM_BAR_WIDTH = 13;`
- `src/main/java/com/github/crittscott/somebuckets/item/JBItem.java:46` — `private static final int ITEM_BAR_WIDTH = 13;`
- `src/main/java/com/github/crittscott/somebuckets/item/MBItem.java:59` — `private static final int ITEM_BAR_WIDTH = 13;`

All three items independently declare the identical constant name and value (13, vanilla's durability-bar
pixel width). In addition:

- `src/main/java/com/github/crittscott/somebuckets/item/JBItem.java:47` — `private static final int DEFAULT_BUCKET_BAR_COLOR = 0x3F76E4;`
- `src/main/java/com/github/crittscott/somebuckets/item/MBItem.java:60` — `private static final int DEFAULT_BUCKET_BAR_COLOR = 0x3F76E4;`

`JBItem` and `MBItem` declare the same constant with the same name and the same literal color value.
None of these are wrong today, but three (respectively two) independently maintained copies of the
same magic number is the kind of drift-prone duplication that tends to go stale — e.g. a future
change to the vanilla bar width, or a deliberate re-color of one bucket family's bar, only updates
one of the copies. Worth consolidating into one shared constant (e.g. on a common item base class,
or a small `BucketBar` constants holder) if these are meant to always match.

### `BBItem` and `SBItem` share an identical `DRINK_DURATION_TICKS` constant
**Files:**
- `src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:64` — `private static final int DRINK_DURATION_TICKS = 32;`
- `src/main/java/com/github/crittscott/somebuckets/item/SBItem.java:48` — `private static final int DRINK_DURATION_TICKS = 32;`

Same name, same value (matching vanilla's milk-bucket drink duration), declared independently in
both milk-capable bucket items. Minor, but the same drift risk as the bar constants above.

## Lower-confidence observation (architectural, not clear-cut duplication)

### `BBFluidLogic` and `SBFluidLogic` share a lot of structural shape
**Files:**
- `src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java`
- `src/main/java/com/github/crittscott/somebuckets/fluid/SBFluidLogic.java`

Both classes are singletons with parallel method families — `tryTake`/`tryTakeWithContext`,
`tryPlace`/`tryPlace(..., allowFaceOffset)`, `resolvePlaceTarget`, and a private
`tryPlaceInWorld` — following the same "sided block capability, then cauldron/world fallback"
dispatch order, using identical idioms (e.g. `Transfers.requireBucketHandler(stack)`,
`Transfers.tryTakeFromBlock`/`tryPlaceIntoBlock`, `context.player().awardStat(...)`). This reads as
parallel evolution rather than accidental copy-paste: the two classes encode genuinely different
policies (BB is a finite debit/credit container, SB is an infinite, allowlist-gated source/sink), and
the project's own recent history (see `git log`, "Refactor bucket interactions around explicit
transaction owners") indicates this shape was a deliberate design choice to keep each bucket family's
transaction ownership explicit rather than hidden behind a shared abstraction. Flagged here for
visibility, but forcing a shared base class over these two would likely reduce rather than improve
clarity, so this is not a recommended change — just worth the project's awareness in case the two
drift out of sync during future edits (e.g. `BBFluidLogic.tryTakeWithContext` merges into existing
finite content while `SBFluidLogic.tryTakeWithContext` always overwrites, which is correct today but
easy to lose track of if one file is edited without the other in mind).

## Areas checked with no significant findings

- **`util/NBTUtil.java`** — every schema key and every public method (including
  `copyFirstEntitySnapshot`, `removeFirstEntitySnapshot`, `getEntityCount`, etc.) has live callers
  in main code and/or gametests; no dead branches in `normalizeEmptyState`'s mode switch.
- **`fluid/AbstractFluidHandler.java`, `BBFluidHandler.java`, `SBFluidHandler.java`** — clean
  template-method design; the apparent similarity between `BBFluidHandler`/`SBFluidHandler` is the
  intended shape of the template pattern (each fills in finite vs. infinite policy), not
  copy-paste duplication.
- **`interaction/Transfers.java`, `Cauldrons.java`, `Dispensers.java`, `FuelHandler.java`** — all
  methods and constants are exercised; `Dispensers`' call to `spawnItem(...)` resolves to the
  inherited `DefaultDispenseItemBehavior.spawnItem`, not a missing declaration.
- **`protection/*`** — `ClaimProtectionProvider`, `ClaimProtections`, `Protections`,
  `ProtectionContext`, `ProtectionAction` (all five enum constants), and `DispenserFakePlayer` are
  all referenced from both main code and the FTB Chunks compat layer or gametests.
  `DispenserFakePlayer.NAME` is `public` but in practice only consumed inside its own file (building
  the fake player's `GameProfile`); it's used, just narrower in practice than its declared
  visibility — not flagged as dead since it is genuinely referenced.
- **`client/*`** — `ClientSetup`, `ClientColorHandlers`, `ClientFluidColors`, `SidedFluidColors`,
  `BucketMouth`, `JunkIconLayout`, `JBRenderer`, `JBModel`, `ClientModelLoaders`,
  `NbtFluidContainerModel` all wire together through `ClientSetup`'s event subscriptions with no
  orphaned classes.
- **`config/*`, `crafting/*`, `event/NBEvents.java`, `register/*`, `compat/ftbchunks/*`,
  `SomeBuckets.java`** — no dead code found; registration classes are minimal and each entry is used.
- **No commented-out code blocks, no `TODO`/`FIXME`/`@Deprecated` markers, and no obviously
  unreachable/always-false branches** were found anywhere in `src/main/java`.
- **Item classes (`JBItem`/`TBItem`, `MBItem`)** — `TBItem` overrides `JBItem` behavior rather than
  copying it (genuine specialization, not duplication); `MBItem`'s FIFO entity-snapshot handling is
  independent from `JBItem`/`TBItem`'s item-stack storage and does not duplicate it despite the
  superficial "bucket with a bar and a `useOn`" similarity.
