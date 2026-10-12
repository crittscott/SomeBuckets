# 1.21.4 → 1.20.1: what to bring back

**Bottom line:** the biggest gap is structural. 1.21.4 keeps behavior in `common`, and each loader there has about 1,200–1,400 lines of main code. In 1.20.1, Forge has 3,638 lines and Fabric 2,482. A lot of the player-facing differences come from that split, so porting the architecture fixes most of them. The build tooling already matches (Gradle 9.5.1, Loom 1.17.493, Shadow 9.4.3), so there's nothing to bring back there.

I worked from the orientation files and 1.21.4's changelogs, then checked the key points in the 1.20.1 source. I built and ran nothing.

## Player-facing gaps (most important first)

1. **Probable duplication bug: stacks of empty buckets fill all at once.** In 1.20.1, `BBItem.use` and the loader take paths write the contents onto the held stack as-is. I found no code that splits off one bucket, so 16 empty Big Buckets used on one water source most likely become 16 filled buckets. 1.21.4 fills one bucket, puts it in the inventory or at the player's feet, and lets creative mode keep the empties (`HeldTransfers.fillFromHand`, which uses `ItemUtils.createFilledResult`). This covers world fluid, powder snow, milk, mobs, items, cauldrons and tanks. Confirmed in-game.
2. **Junk items from a removed mod are silently lost.** 1.20.1's `NBTUtil` calls `ItemStack.of` on each stored entry, and an unknown item comes back as air. That breaks your "never silently discard player data" rule. 1.21.4 sets such entries aside, shows them in the tooltip, and restores them later when they load again. 1.20.1 has the hook for this: `Item#verifyTagAfterLoad`.
3. **Leashed and owned mobs can be captured.** 1.20.1 has no leash or owner check. 1.21.4 refuses leashed mobs and other players' pets, and dispensers capture no owned mob.
4. **Dispensers can tame animals.** 1.21.4 dispensers never feed an untamed tamable animal. Feeding in 1.20.1 already defers to the animal's own interaction (javadoc at `JBItem.java:331`), so this is a small add.
5. **Junk/Trash intake ignores who an item was dropped for.** 1.21.4 leaves items dropped for another player and counts pickups toward the picked-up statistic, using a common access widener on `ItemEntity.target`. 1.20.1 already fires the pickup event.
6. **Held-transfer overflow drops on the ground.** `HeldTransferSettlement.java:54` drops it at the player's feet; 1.21.4 tries the inventory first.
7. **Off-hand transfer direction varies.** 1.21.4 always has the Some Buckets container give before it takes, on every loader. In 1.20.1, Forge and Fabric each have their own off-hand event code, so this needs checking.
8. **Junk Buckets accept backpacks.** 1.21.4 also rejects modded items that expose an item inventory. 1.20.1 only checks `canFitInsideContainerItems`.
9. **Fabric allowlist changes need a restart, and clients never get them.** Fabric loads the config only at `SERVER_STARTING`. 1.21.4 reloads on `/reload` and sends the resolved allowlist to clients (`FabricSBPolicyPayload`).
10. **Placement and transfer fixes** from 1.21.4's "Fixed" list. Each needs checking against 1.20.1:
    - Placement is checked against the block actually placed.
    - A cancelled placement restores replaced plants without drops.
    - An aquatic release no longer triggers sculk sensors twice.
    - Failed operations never debit the bucket.
    - Tanks are credited with what actually moved. 1.20.1 Forge instead logs a contract violation and keeps the partial state.
11. **The `/sb eggs` and `/sb fluids` operator diagnostics.** These are in 1.21.1, so the 1.20.1 release is the only one missing them.
12. **Forge milk fluid:** when a mod like Create enables Forge's milk fluid, 1.21.4 exchanges milk with tanks as that fluid. Declined; not ported.
13. **Color-override data:** 1.21.4 ships entries for Bumblezone and Wilder Nature; 1.20.1 ships Shiny Mobs and Sushi Go Crafting. Merge the entries, for mods that have 1.20.1 builds, into 1.20.1's `mob_bucket_colors.json`.

## Architecture to port

- **`fluid/FluidTransactions` (common).** It owns world pickup and placement, protection, and accounting for both loaders. It replaces Forge's `BBFluidLogic`, `SBFluidLogic`, `FluidPickup`, `ForgeFluidPlacement` and much of `Transfers`, and Fabric's `FabricFluidPlacement`. Loaders keep only the basic operations: a sided fluid store, placing an arbitrary fluid, and the checked placement with snapshot-and-restore.
- **`interaction/HeldTransfers` (common).** It replaces `HeldTransferSettlement` and `MilkTransfers`, plus the per-loader transfer logic. Gaps 1, 6 and 7 fall out of this.
- **Common `Dispensers` and `Cauldrons`**, registered once by `SomeBuckets.registerBehaviors`. They replace Forge's `interaction/Dispensers` and `Cauldrons`, Fabric's `FabricFluidDispensers` and `FabricCauldronInteractions`, and `NonFluidDispensers` and `PowderSnowCauldrons`.
- **Make `FluidBucketItem` the owner of transfer rules.** 1.21.4's `acceptable`, `insert`, `extractable` and `extract` let the loader item storages (`BBFluidHandler`, `SBFluidHandler`, `FabricBucketStorage`) just convert units around them.
- **A single bucket-state class with load-time validation.** Turn `NBTUtil` into the equivalent of `BucketState`: the only reader and writer, enforcing capacity, exclusivity and no nesting on every write, and validating on load through `verifyTagAfterLoad` (this also carries gap 2). The NBT keys are unchanged since 0.8.0, so 1.20.1 needs no migration layer.
- **A common `SomeBucketItem` base** for stack size and load-time checks, folding in `VariableStackItem`. Fabric's `ItemStackMixin` stays, because 1.20.1 has no stack-size component.
- **A common `ClientPlatform` seam** for fluid appearance and bar color. Share whatever rendering logic doesn't depend on 1.21.4's item definitions. Forge's model loaders and BEWLR and Fabric's baked-model wrappers stay loader-owned.
- **Smaller conventions:**
  - Prefix loader class names (`ModItems` → `ForgeItems`, `ServerConfig` → `ForgeServerConfig`, `DispenserFakePlayer` → `ForgeAutomationPlayer`).
  - Add the `every_shared_scenario_has_a_loader_wrapper` GameTest.
  - Add the `verifyCommonAccessMirror` check once there's a common access widener.

## Don't port

These are 1.21.4-only, or 1.20.1 is already ahead:
- Data components and their codecs, `MAX_STACK_SIZE` and `CONSUMABLE` components, `items/*.json` item definitions, `FuelValuesMixin`.
- The NeoForge module.
- `BucketStateMigration`, which converts 1.21.1 saves.
- 1.21.4's protection model, where dispensers post no events. 1.20.1's dispensers post events as `[SomeBuckets]`.

Also flowing the other way: the 0.8.3 changes to 1.20.1 aren't in 1.21.4 yet. That includes the Mob Bucket exchange with vanilla mob buckets, the `uncapturable` tag, Junk/Trash tempting, the `held_transfer_excluded` tag, dispensers posting events, and powder-snow cauldron filling by dispensers. Those need porting forward.

**Suggested order:** confirm and fix gap 1, port `HeldTransfers`, then `FluidTransactions` (this does most of the work), then common `Dispensers` and `Cauldrons`, then bucket state and load-time validation. The smaller player-facing items fit in along the way.

## Outcome

All plan phases (see `backport-plan.md`) were implemented in the 1.20.1 repo; player-visible results are recorded in `change-list.md`, and both orientation files describe the new structure.

Build fixes after the first compile:
- `BlockPlaceContext(Level, …)` is protected in vanilla; common code uses the public `(Player, InteractionHand, ItemStack, BlockHitResult)` constructor. Loader code keeps the five-argument form, which the loaders expose.
- Fabric API 0.92 lacks `ItemStorage.ITEM`; Fabric's `carriesItemContainer` returns `false`.

GameTest fix after the first run: dispenser Mob Bucket capture failed on both loaders (`dispenser_empty_mob_bucket_captures_one_entity`, `dispenser_nonempty_mob_bucket_captures_matching_entity`, `player_capture_fires_filled_bucket_criterion_but_automation_does_not`). The automation entity-interaction protection check posts the loader event while the automation player holds a copy of the bucket, and the mod's own capture-priority listeners (`ForgeMobCaptureEvents`, `FabricMobCaptureEvents`) captured into that copy. Both listeners now skip `Protections.isAutomationActor`.

Open items:
- Rerun GameTests on both loaders to confirm the capture fix; the Fabric run stopped during batch 2, so its full failure list is unknown.
- Wilder Nature color entries were added under the `wildernature` namespace; their entity IDs are unverified against a 1.20.1 build.
- `FluidTransactions` uses a deprecated API (compiler note); not yet identified.
- Nothing is committed; new files are untracked.
