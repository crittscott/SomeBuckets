# Guarding Against Impossible Errors — Review

Scope: all Java sources under `src/main/java`. Read-only review; no code changed. Findings are
organized from highest to lowest confidence that the guarded condition is actually unreachable
given Minecraft/Forge's documented contracts and this mod's own code paths.

## Confirmed: guards against conditions this mod's own code already rules out

### 1. `NBTUtil.normalizeEmptyState` — null-tag guard after mode already proves the tag exists

**File:** `src/main/java/com/github/crittscott/somebuckets/util/NBTUtil.java`, lines 267–296
(the throwing line is 273).

```java
public static void normalizeEmptyState(ItemStack stack) {
    Mode mode = getMode(stack);
    if (mode == Mode.NONE) return;

    CompoundTag tag = stack.getTag();
    if (tag == null) throw new IllegalStateException("Nonempty bucket mode requires NBT");
    ...
```

`getMode()` (lines 56–58) is defined as `tag == null ? Mode.NONE : Mode.fromNbt(...)`. So by the
time execution reaches line 270 and passes the `mode == Mode.NONE` early return, `getMode(stack)`
must have read a non-null tag on the immediately preceding line. Nothing runs between that read
and the `stack.getTag()` re-read on line 272 — this is synchronous, single-threaded world logic
with no intervening call that could clear the stack's NBT. The `tag == null` branch therefore
cannot execute; it duplicates a fact the method itself just established two lines earlier.

### 2. `Objects.requireNonNull(context.hand(), ...)` — re-checking an invariant `ProtectionContext` already guarantees

**Files:**
- `src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java`, line 313
- `src/main/java/com/github/crittscott/somebuckets/compat/ftbchunks/FtbChunksProtection.java`, line 39

Both do the same thing:
```java
hand = Objects.requireNonNull(context.hand(), "Player protection context requires a hand");
```
guarded by `if (context.player() != null)`.

`ProtectionContext` (`src/main/java/com/github/crittscott/somebuckets/protection/ProtectionContext.java`)
is a record with exactly three factories that are the *only* places `new ProtectionContext(...)` is
called anywhere in the codebase (verified via project-wide search — no other call site exists):

- `player(Player, InteractionHand)` (lines 26–29) — itself does
  `Objects.requireNonNull(player, "player")` and `Objects.requireNonNull(hand, "hand")` before
  constructing, so any context with a non-null `player()` was built here and already has a
  non-null `hand()`.
- `dispenser(BlockPos)` (lines 32–34) and `unownedAutomation()` (lines 37–38) both pass `null` for
  `player`.

So `context.player() != null` already logically entails `context.hand() != null` for every
`ProtectionContext` instance that can exist in this codebase — the record's own javadoc even states
this invariant ("hand … required exactly when player is non-null"). The two downstream
`requireNonNull` calls are re-verifying a condition that was already made true, and can never be
false, by construction.

### 3. `Dispensers.requireItem` — item-type check the mod's own dispenser registration already guarantees

**File:** `src/main/java/com/github/crittscott/somebuckets/interaction/Dispensers.java`, lines
60–65, used at lines 88, 150, 189, 226.

```java
private static <T> T requireItem(ItemStack stack, Class<T> expectedType, String family) {
    if (!expectedType.isInstance(stack.getItem())) {
        throw new IllegalStateException(family + " dispenser behavior received " + stack.getItem());
    }
    return expectedType.cast(stack.getItem());
}
```

`Dispensers.register()` (lines 51–57) registers each `DefaultDispenseItemBehavior` singleton
against specific `Item` instances only:
- `BB_BEHAVIOR` → `ModItems.BIG_BUCKET_8`, `BIG_BUCKET_64` (both always constructed as `BBItem`,
  see `ModItems.java` lines 20–23)
- `SB_BEHAVIOR` → `ModItems.SOURCE_BUCKET` (always `SBItem`)
- `MB_BEHAVIOR` → `ModItems.MOB_BUCKET` (always `MBItem`)
- `STORAGE_BEHAVIOR` → `ModItems.JUNK_BUCKET`, `TRASH_BUCKET` (`JBItem`/`TBItem`, and `TBItem
  extends JBItem`)

Vanilla's `DispenserBlock.registerBehavior`/dispatch mechanism looks up the behavior to run purely
by `stack.getItem()` identity against the map populated by `registerBehavior`. Since this mod is
the sole registrant for these items and registers exactly one behavior per item, `BBBehavior.execute`
can only ever be invoked with a stack whose item is one of the two `BBItem` instances it was
registered for (and likewise for the other three behaviors). The `instanceof`-failure branch in
`requireItem` guards against a dispatch that vanilla's own registration contract, combined with this
mod's own `register()` method in the same file, makes unreachable.

### 4. `JunkIconLayout.seedFor` — null registry key for an item that can only be a stored, registered item

**File:** `src/main/java/com/github/crittscott/somebuckets/client/JunkIconLayout.java`, lines
79–83.

```java
private static long seedFor(ItemStack stack, int index) {
    ResourceLocation id = Objects.requireNonNull(
            ForgeRegistries.ITEMS.getKey(stack.getItem()), "Stored item is not registered");
    return id.hashCode() * 31L + index;
}
```

This is only ever called on entries returned by `NBTUtil.getStoredItems()` (see
`JBRenderer.java` line 82, `JunkIconLayout.arrange` line 55). `NBTUtil.getStoredItems`
(`NBTUtil.java` lines 214–224) explicitly filters: `ItemStack s = ItemStack.of(tagList.getCompound(i)); if (!s.isEmpty()) result.add(s);`.
Minecraft's `ItemStack.of(CompoundTag)` resolves an unrecognized serialized item id to an empty/air
stack rather than throwing or producing a stack backed by an unregistered `Item`; only a stack whose
`getItem()` was actually returned by the item registry survives that `isEmpty()` filter. Every stack
reaching `seedFor` therefore has an item that is, by construction, in `ForgeRegistries.ITEMS`, so
`getKey()` cannot return null for it.

## Borderline: same "own handler must be self-consistent" pattern, weaker guarantee

### 5. SIMULATE/EXECUTE consistency checks on the mod's *own* fluid handler

**Files/lines:**
- `src/main/java/com/github/crittscott/somebuckets/interaction/Cauldrons.java`, lines 162–165 and
  183–186 ("Bucket fluid handler violated its simulated cauldron fill/drain")
- `src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java`, lines 167–170 and
  206–210 ("Bucket fluid handler violated its simulated fill/drain" — note these are distinct from
  the *block*-handler checks at lines 161–165 and 201–204 in the same methods, which legitimately
  guard a third-party mod's `IFluidHandler` and should stay)

Each throws `IllegalStateException` if a `SIMULATE` call on the mod's own `BBFluidHandler`/
`SBFluidHandler` (via `AbstractFluidHandler`, `src/main/java/.../fluid/AbstractFluidHandler.java`)
disagrees with the immediately following `EXECUTE` call on the same handler/resource. Both
subclasses' `fillEmpty`/`fillExisting`/`performDrain` hooks are pure, deterministic arithmetic over
the container's current NBT and the requested `FluidStack` amount — no randomness, no external
state. Nothing between the SIMULATE and EXECUTE calls in these methods mutates the bucket's NBT: the
only intervening step is a protection check (`Protections.mayAct` → `ClaimProtections.mayAct`),
and the sole currently-registered provider, `FtbChunksProtection`, only copies the stack into a fake
player's hand for automation checks and never mutates the real stack (see
`FtbChunksProtection.java` lines 61–67). Given the current codebase, SIMULATE/EXECUTE divergence on
the bucket's own handler cannot occur.

This is graded "borderline" rather than "confirmed" because `ClaimProtectionProvider`
(`src/main/java/.../protection/ClaimProtectionProvider.java`) is a documented extension point for
third-party claim mods — a hypothetical future provider could in principle mutate the passed stack.
That's a step removed from "provably impossible today," so treat this one as worth a second look
rather than a hard finding.

### 6. `Transfers.requireBucketHandler` — capability presence check on an item that always provides it

**File:** `src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java`, lines
119–123.

```java
/** The mod bucket's own capability is an invariant, not an optional dispatch signal. */
public static IFluidHandlerItem requireBucketHandler(ItemStack stack) {
    return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
            () -> new IllegalStateException("Some Buckets item is missing its fluid capability"));
}
```

`BBItem.initCapabilities` (`BBItem.java` lines 191–195) and `SBItem.initCapabilities`
(`SBItem.java` lines 54–57) unconditionally return a `FluidProvider` exposing
`FLUID_HANDLER_ITEM` for every stack of those items. Every call site passes a stack already known
to be a `BBItem`/`SBItem` instance (`Cauldrons`, `BBFluidLogic`, `SBFluidLogic`, `Dispensers`), so
the capability lookup cannot resolve empty in the current code. The method's own doc comment
already frames this as an intentional invariant assertion rather than protection against an
unreliable interface, so this is a deliberate fail-fast design choice more than an accidental
over-guard — flagged here only because it fits the same "provably true by this mod's own
registration" pattern as findings 3 and 4.

## Minor: constructor validation of a value only ever supplied as a hardcoded literal

**File:** `src/main/java/com/github/crittscott/somebuckets/item/JBItem.java`, lines 52–56.

```java
public JBItem(Properties properties, int capacity) {
    super(properties);
    if (capacity < 1) throw new IllegalArgumentException("Storage bucket capacity must be positive");
    this.capacity = capacity;
}
```

The only call sites are `ModItems.java` line 25 (`new JBItem(..., 9)`) and `TBItem.java` line 38
(`super(properties.stacksTo(1), 1)`), both hardcoded literals owned by this mod. The check can
never trigger given the current codebase. For comparison, `BBItem`'s analogous
`capacityUnits` constructor parameter (`BBItem.java` lines 68–71) has no such check — so this is
also a minor inconsistency between the two sibling item classes rather than a deliberate policy.
Low severity; a "fail fast" idiom is reasonable at a class boundary, but per this mod's own
principle (no need to defend against its own hardcoded configuration) it's not pulling weight here.

## Areas checked with nothing notable to report

- NBT read paths on the mod's own serialization format elsewhere in `NBTUtil.java` (`getMode`,
  `getAmount`, `getFluidStack`, `getPowderUnits`, `getEntityCount`, `getStoredItems`, etc.) use
  `tag == null` checks that are genuine defaults for an item stack that may or may not have any NBT
  yet — not over-guarding, since an `ItemStack`'s tag is legitimately absent for a freshly-crafted
  or vanilla-sourced stack.
- Client-side texture/resource-pack reading (`ClientFluidColors.java`, `BucketMouth.java`,
  `NbtFluidContainerModel.java`, `ClientColorHandlers.java`'s entity-type/spawn-egg lookups) all
  guard against genuinely variable external input (resource packs, other mods' entity types/fluids)
  and were left alone.
- `SBPolicy.java` / `ServerConfig.java` validate server-admin-supplied config values — legitimate
  external input, not mod-controlled data.
- Recipe ingredient deserialization (`EmptyBucketIngredient.java`, `SpawnEggIngredient.java`)
  validates datapack JSON — legitimate external input. The `@Nullable ItemStack input` null checks
  in `test()` match `Ingredient`'s own nullable contract from Forge/vanilla.
- `Transfers.java`'s block-handler-side SIMULATE/EXECUTE checks (lines 161–165, 201–204) guard a
  capability that can be backed by *any* other mod's `IFluidHandler` implementation — a genuinely
  unreliable interface, correctly guarded.
- No exhaustive-enum `switch` with an unreachable `default: throw` was found; the `Mode` switches in
  `NBTUtil.java` and `BBItem.java` either enumerate all cases without a `default`, or use `default`
  for legitimate business-logic fallthrough (e.g. `BBItem.getBarColor`), not error signaling.
- Gametest sources (`src/main/java/.../gametest/**`) contain many `null`/`IllegalStateException`
  assertions via `GameTestSupport.check` and `orElseThrow` — these are test assertions verifying
  behavior, not production defensive code, and are out of scope for this review.
