# Magic Numbers / Magic Strings Review

Scope: all Java sources under `src/main/java`, compared against
`src/main/resources/assets/somebuckets/lang/en_us.json`. Read-only assessment, no code changes.

Overall the codebase is disciplined about this: NBT keys are centralized in `NBTUtil`, translation
keys are routed through `en_us.json` via `Component.translatable`, and most numeric tuning values
already have named constants with explanatory comments (see `JunkIconLayout`, `JBRenderer`,
`FluidPlacement`, etc.). The findings below are the concrete exceptions to that pattern.

## 1. Duplicated named constants across classes (same value re-typed instead of shared)

These are cases described directly by CLAUDE.md's "already-existing constants but were re-typed as
literals instead" — except here the same *named* constant is independently redeclared with the same
value in multiple classes. A future change to one copy (e.g., adjusting the vanilla-matching item-bar
width) silently leaves the others stale.

- **`ITEM_BAR_WIDTH = 13`** (vanilla item durability-bar pixel width), declared separately in:
  - `src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:59`
  - `src/main/java/com/github/crittscott/somebuckets/item/JBItem.java:46`
  - `src/main/java/com/github/crittscott/somebuckets/item/MBItem.java:59`

  Suggestion: hoist to one shared constant (e.g. a small `ItemBars` utility or a constant on a common
  interface) that all three item classes reference.

- **`DRINK_DURATION_TICKS = 32`** (vanilla `MilkBucketItem` drink duration), declared separately in:
  - `src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:64`
  - `src/main/java/com/github/crittscott/somebuckets/item/SBItem.java:48`

  Suggestion: shared constant, since both exist purely to match vanilla milk-drinking timing.

- **`DEFAULT_BUCKET_BAR_COLOR = 0x3F76E4`**, declared separately in:
  - `src/main/java/com/github/crittscott/somebuckets/item/JBItem.java:47`
  - `src/main/java/com/github/crittscott/somebuckets/item/MBItem.java:60`

  Suggestion: same fix as above — one shared constant for the default storage-bucket bar color.

## 2. Storage capacity has no single accessible source of truth

`src/main/java/com/github/crittscott/somebuckets/register/ModItems.java:20-25` registers bucket
capacities as bare literals:

```java
() -> new BBItem(new Item.Properties().stacksTo(1), 64));   // line 21
() -> new BBItem(new Item.Properties().stacksTo(1), 8));    // line 23
() -> new JBItem(new Item.Properties().stacksTo(1), 9));    // line 25
```

`BBItem` exposes `getCapacityUnits()`/`getCapacityMb()` for its `8`/`64` tiers, so those two are at
least readable elsewhere post-construction. `JBItem`, however, stores `capacity` as a `private final
int` (`JBItem.java:50`) with **no accessor**. As a result, test code has to re-derive/hardcode the
Junk Bucket's capacity as a bare literal instead of reading it from one place:

- `src/main/java/com/github/crittscott/somebuckets/gametest/StorageBucketGameTests.java:117` —
  `GameTestSupport.check(actual.size() == 9, "Merge changed occupied entry count");`

  The `9` here is only correct because it happens to match `ModItems.java:25`'s literal `9`; nothing
  enforces that relationship. Suggestion: add a package-visible (or public) `getCapacity()` to
  `JBItem` and have the test reference `((JBItem) bucket.getItem()).getCapacity()` instead of the
  bare `9`.

## 3. Unnamed recursion-depth literal mirroring a vanilla internal default

`src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java:379`:

```java
level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
        oldState, newState, snapshot.getFlag(), 512);
```

The trailing `512` is `Level#markAndNotifyBlock`'s `recursionLeft` parameter, mirroring vanilla's own
internal default from `Level#setBlock`. There is no public Forge/Minecraft constant to reference (it's
an internal vanilla magic number itself), so this can't be eliminated outright, but it should at least
be a locally named constant (e.g. `private static final int MAX_UPDATE_RECURSION = 512;`) so a reader
doesn't have to know vanilla internals to understand why `512` appears here.

## 4. Duplicated color literal within one class

`src/main/java/com/github/crittscott/somebuckets/client/ClientColorHandlers.java`, `mobBucketTint`:

```java
48:        if (entityType == null) return 0x808080; // Gray fallback
...
51:        if (spawnEgg == null) return 0x808080;
```

Same fallback color re-typed twice four lines apart in the same method. Minor, but a one-line
`private static final int MISSING_EGG_COLOR = 0x808080;` would remove the duplication and the risk of
the two falling out of sync if the fallback is ever tuned.

## 5. Tooltip translation keys have no shared constant between production and tests

The four tooltip translation keys are inlined as string literals at their `Component.translatable`
call sites and separately re-typed in game tests that assert on them:

- `"tooltip.somebuckets.big_bucket.fluid"` — `item/BBItem.java:112` and
  `gametest/StateGameTests.java:173`
- `"tooltip.somebuckets.big_bucket.powder_snow"` — `item/BBItem.java:117` (no test cross-check found)
- `"tooltip.somebuckets.storage_bucket.stacks"` — `item/JBItem.java:99` and
  `gametest/StateGameTests.java:175`
- `"tooltip.somebuckets.mob_bucket.contents"` — `item/MBItem.java:199` and
  `gametest/StateGameTests.java:177`

Unlike the item-name keys (which `gametest/PresentationGameTests.java` cross-checks against the actual
`en_us.json` values, a good pattern), these tooltip-key checks in `StateGameTests` only assert that the
literal key string shows up in the serialized tooltip JSON — a typo made identically in both the
production call site and the test would still pass. Low risk in practice (a typo'd key would just
silently fail to translate at runtime and be visually obvious), but since `NBTUtil` already
centralizes NBT key strings for exactly this reason, the same pattern (a small constants holder, or
constants on the owning item classes) could be applied to these tooltip keys for consistency.

## Areas checked with no significant findings

- **NBT keys**: all centralized in `NBTUtil` (`MODE`, `AMOUNT`, `FLUID_STACK`, `POWDER_UNITS`,
  `ENTITY_TYPE`, `ENTITIES`, `STORED_ITEMS`); no call site re-types these strings directly.
- **User-facing text**: no `Component.literal(...)` calls in production code (only in
  `gametest/MBGameTests.java` for internal test-entity naming, which is not player-facing). All item
  names and tooltips route through `en_us.json` via `Component.translatable`.
- **Item/tooltip lang keys vs. `en_us.json`**: every key referenced in code
  (`item.somebuckets.*`, `tooltip.somebuckets.*`, `itemGroup.somebuckets`) has a matching entry in
  `en_us.json`, and vice versa — no orphaned or unused lang entries found.
- **Fluid/mB constants**: consistently use `FluidType.BUCKET_VOLUME` rather than a re-typed `1000`.
- **Client rendering math** (`JunkIconLayout`, `JBRenderer`, `BucketMouth`,
  `NbtFluidContainerModel`): every tuning literal is already a named, commented constant.
- **Config**: `ServerConfig`/`SBPolicy` have no stray literals; the allowed-fluid default list and the
  `somebuckets:milk` sentinel ID are defined once and referenced consistently.
