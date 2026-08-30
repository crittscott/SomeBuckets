# Test-Fix Plan

Reassessment of the gaps in `review-test-completeness.md` against the current code, plus a concrete
plan to close the confirmed ones. Branch `1.21.1`. No code changed yet.

## Reassessment

| # | Review claim | Verdict | Evidence |
| --- | --- | --- | --- |
| 1 | Fabric furnace-fuel path has no test | **Confirmed** | `fabric/src/gametest/.../RecipeAndFuelGameTests.java` forwards only the three recipe scenarios. `fabric/.../mixin/AbstractFurnaceBlockEntityMixin` (`isFuel` + `getBurnDuration` injectors) and the `FabricBBItem` / `FabricSBItem` `getRecipeRemainder` shells are unexercised. Forge and NeoForge each have three fuel tests. |
| 2 | Junk Bucket screen insert/extract untested | **Confirmed** | `StorageBucketScenarios` calls `overrideStackedOnOther` / `overrideOtherStackedOnMe` only through `TBItem` (`trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake`). `JBItem`'s FIFO extract-to-empty-cursor path (`JBItem.java` lines 421-448) and cursor-insert path have no test. The README lists screen right-click as a first-class interaction. |
| 3 | `canStore` eligibility rule untested | **Confirmed** | `JBItem.canStore` = `!empty && item.canFitInsideContainerItems()`; only `JBItem` overrides that flag to `false`. No test asserts JB/TB refuse each other, bundles, or shulker boxes, or that Big/Huge/Source/Mob Buckets are accepted with contents intact. A maintenance invariant names "the common storage eligibility rule." |
| 4 | Fabric held-transfer event ordering untested | **Partially confirmed** | Fabric `TransferGameTests` lacks `earlier_listener_can_veto_...` and `lowest_priority_..._still_succeeds`, but those test loader-native event-bus priority, which Fabric's `UseItemCallback` has no analog for. What is real and untested: the guard logic in `FabricHeldTransferEvents` — targeted block takes precedence (`hit != MISS` -> PASS), main-hand `FluidBucketItem` skipped, offhand-not-a-bucket skipped. Fix scope is one guard test, not two priority tests. |
| 5 | Client render math untested | **Confirmed but not worth fixing** | `client/*` (`BucketMouth`, `ClientTextureColors`, `JunkIconLayout`, `JunkForegroundGeometry`, `DelegatingBakedModel`) are all `@Environment(CLIENT)` and package-private. Server-side GameTest cannot load them. Covering them needs a new `common/src/test` JUnit source set with a client classpath — disproportionate infra for low-churn pure geometry. Leave it; raise as a separate infra decision if wanted. |
| 6a | Empty SB allowlist untested | **Confirmed (minor)** | `source_allow_list_blocks_...` uses `List.of("minecraft:water")` and a three-id list, never `List.of()`. `SBPolicy.resolve` has no empty special-case, so risk is low, but it is a one-scenario add. |
| 6b | `MAX_STACK_SIZE` component value untested | **Confirmed (minor)** | `NBTUtil.setData` (lines 89-91) writes it on every mutation; no test reads `stack.getMaxStackSize()` back (16 empty / 1 filled). |
| 6c | Recipe output identity untested | **Confirmed (minor)** | `RecipeScenarios.all_shipped_recipe_ids_load` only checks the recipe resolves, never `getResultItem`. |
| 6d | NeoForge cauldron-exclusion invariant | **Confirmed but adequately covered** | `neoforge/.../interaction/Transfers.java` line 286 returns `null` for `AbstractCauldronBlock`. `CauldronGameTests` already prove cauldrons award the stat, fire the criterion, and emit the events end-to-end, which is the observable consequence. A dedicated micro-test is optional. |
| 6e | Milk refusing a non-milk container in held transfer | **Confirmed (minor)** | `MilkTransfers.pourMilk` early-returns for a non-MILK/NONE destination mode and for a non-`Items.BUCKET` target; no negative test. Transfer suites are hand-written per loader, so this is a three-file add. |
| — | "Redundant / over-dense" clusters | **Out of scope** | Deleting tests is lower value and riskier than closing gaps. Only the two near-duplicate sculk tests (`MBScenarios.land_release_activates_sculk_sensor` + `aquatic_release_activates_sculk_sensor`) are worth collapsing. Leave the rest unless a trim pass is explicitly wanted. |

## Fix plan

All additions follow the existing pattern: a shared body in `common/src/gametest/.../*Scenarios.java`
plus a three-line `@GameTest` wrapper in each of `forge` / `neoforge` / `fabric` (`public static` with
`@GameTestHolder` and `@PrefixGameTestTemplate(false)` for Forge/NeoForge, `public void` for Fabric).
Loader-specific tests go straight in the loader suite with no shared body.

### 1. Fabric furnace fuel — loader-only, 3 tests

File: `fabric/src/gametest/java/.../RecipeAndFuelGameTests.java` (already registered in
`fabric.mod.json`). Mirror `ForgeFuelGameTests` one for one:

- `lava_big_bucket_is_furnace_fuel_at_one_unit_or_more` — `AbstractFurnaceBlockEntity.isFuel(stack)`
  true for `fluid(big8(), LAVA, 1000)` and `fluid(big8(), LAVA, 4000)`.
- `subunit_lava_and_nonlava_buckets_are_not_fuel` — `isFuel` false for `LAVA @ 999`,
  `WATER @ 8000`, `milk(big8(), 8000)`.
- `lava_source_bucket_is_furnace_fuel` — `isFuel` true for `fluid(source(), LAVA, 1000)`; and
  `((FabricSBItem) item).getRecipeRemainder(stack)` equals the input (permanent fuel), via
  `assertSameStack`.

`AbstractFurnaceBlockEntity.isFuel(ItemStack)` is `public static` (vanilla `AbstractFurnaceMenu`
calls it cross-package), so these are pure one-liners like the Forge `ForgeHooks.getBurnTime` checks —
no furnace block, no ticking.

- **Coverage note:** this covers the `isFuel` injector and the `getRecipeRemainder` shell. The
  `getBurnDuration` injector (a literal-constant return under the identical `BucketFuel.isLavaFuel`
  guard) stays uncovered. Options: (a) accept it as a near-zero-risk twin of `isFuel`; or (b) add a
  fourth test placing `Blocks.FURNACE` with `RAW_IRON` input and lava `big8(4000)` fuel,
  `runAfterDelay(4L)` asserting `BlockStateProperties.LIT` is true and the fuel slot is decremented
  to 3000 mB (this also nails the cross-loader "returns with one unit removed" guarantee from
  `player-view.md`). Recommend (b) if the mixin should be fully exercised; it is about 25 lines.
- **Fallback:** if `isFuel` turns out not reachable from the gametest module (JPMS), use the
  furnace-integration form for all three.

### 2. Junk Bucket screen interactions — shared, 1 scenario (+3 wrappers)

Add to `StorageBucketScenarios`: `junk_bucket_screen_insert_and_fifo_extract`. Model it on
`trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake` (same `SimpleContainer` / `Slot` /
`SlotAccess.forContainer` fixtures, already used there):

- **`overrideStackedOnOther`** (bucket on cursor, secondary-click a slot holding `APPLE x40`) ->
  returns true, slot emptied, stored `[APPLE x40]`.
- **`overrideOtherStackedOnMe` insert** (bucket in slot, cursor `DIAMOND x3`, secondary) -> returns
  true, cursor emptied, appended after existing entries.
- **`overrideOtherStackedOnMe` FIFO extract** (bucket in slot with stored `[A, B]`, empty cursor,
  secondary) -> returns true, cursor now holds `A` (oldest), stored `[B]`, `slot.setChanged` path
  taken.
- **Non-secondary click** (`ClickAction.PRIMARY`) -> returns false, no mutation.

Wrappers: `forge` / `neoforge` / `fabric` `StorageBucketGameTests`.

### 3. Storage eligibility rule — shared, 1 scenario (+3 wrappers)

Add to `StorageBucketScenarios`: `storage_eligibility_rule_accepts_buckets_and_refuses_containers`.
Pure `JBItem.canStore` assertions plus one round-trip:

- `canStore` **false**: `junk()`, `trash()`, `new ItemStack(Items.BUNDLE)`,
  `new ItemStack(Items.WHITE_SHULKER_BOX)`, `ItemStack.EMPTY`.
- `canStore` **true**: `big8()`, `big64()`, `source()`, `mob()`, and a filled
  `fluid(big8(), WATER, 4000)`.
- **Contents intact**: store the filled Big Bucket in a Junk Bucket via `addStack`, read back with
  `NBTUtil.getStoredItems`, assert `assertSameStack` (custom_data survives the nested round-trip).
- Optionally assert the world-intake gate too: `JBItem.isIntakeCandidate` on an `ItemEntity` wrapping
  a `junk()` stack -> false.

Wrappers: `forge` / `neoforge` / `fabric` `StorageBucketGameTests`.

### 4. Fabric held-transfer guard — loader-only, 1 test

File: `fabric/src/gametest/java/.../TransferGameTests.java`. Add
`offhand_held_transfer_yields_to_targeted_block`:

- Player aimed at a `STONE` block (reuse `survivalPlayerLookingAt`), main hand
  `new ItemStack(Items.BUCKET)`, off hand `fluid(big8(), WATER, 2000)`.
- Invoke `UseItemCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND)`.
- Assert the result is `PASS` and neither stack changed (targeted block wins; documented "A targeted
  block takes precedence").
- Positive control in the same test: repeat with `survivalPlayerLookingAtAir` -> result
  `SUCCESS` / `CONSUME`, main hand becomes `WATER_BUCKET`, off-hand Big Bucket down to 1000 mB.

This is the honest Fabric analog — it covers `FabricHeldTransferEvents`'s guard logic, which
currently has zero coverage. The Forge/NeoForge event-priority pair has no Fabric equivalent to port.

### 6. Minors

- **6a — empty SB allowlist.** Add shared
  `SBScenarios.empty_allow_list_disables_all_source_contents`: `SBPolicy.refresh(List.of(),
  "SBGameTests")`, assert `!allows(WATER) && !allows(LAVA) && !allowsMilk()`, restore with
  `SBPolicy.refresh(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS, ...)` in a `finally`. +3 wrappers in
  `SBGameTests`.
- **6b — MAX_STACK_SIZE.** Add shared `StateScenarios.variable_stack_size_tracks_fill_state`: fresh
  `big8()` -> `getMaxStackSize() == 16`; after `fluid(..., 1000)` -> `== 1`; after
  `NBTUtil.clearBucket` + `normalizeEmptyState` -> back to `16`. Repeat for `junk()` (empty 16) vs a
  `junk()` with one stored stack (1). +3 wrappers in `StateGameTests`.
- **6c — recipe output identity.** Extend `RecipeScenarios.all_shipped_recipe_ids_load` to assert
  `recipe.getResultItem(helper.getLevel().registryAccess())` is the expected item per id and
  `NBTUtil.isEmptyBucket(result)`. No new wrapper.
- **6e — milk refuses non-milk container.** Add `milk_big_bucket_refuses_incompatible_destination` to
  all three `TransferGameTests` (hand-written per loader): `milk(big8(), 8000)` main hand +
  `new ItemStack(Items.WATER_BUCKET)` off hand -> `Transfers.tryTransferEither` returns false, both
  stacks unchanged.
- **6d — NeoForge cauldron exclusion.** Optional. Add
  `neoforge BlockCapabilityGameTests.vanilla_cauldron_is_excluded_from_generic_block_lookup` asserting
  the `Transfers` fluid-storage lookup returns null over a `WATER_CAULDRON`. Skip unless it should be
  explicit.

### Not doing

- **#5 client math** — needs a new JUnit source set and client classpath; out of proportion. Separate
  decision.
- **Redundant-test trim** — leave, except optionally fold
  `MBScenarios.land_release_activates_sculk_sensor` and `aquatic_release_activates_sculk_sensor` into
  one (the `ENTITY_PLACE` event is already asserted directly in
  `aquatic_release_into_existing_water_emits_no_fluid_event`).

### Net new tests

About 7 shared scenarios x 3 wrappers plus about 5 loader-only methods, roughly 26 `@GameTest`
methods, with no new source sets and no new fixtures. Suggested order: 3, 2, 1, 4, 6a/6b/6c, 6e, 6d.
