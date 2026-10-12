# Backport tasks: 1.21.4 architecture and behavior into 1.20.1

Scope: the 1.20.1 repo only; the 1.21.4 repo is reference. Background and rationale are in `backport-report.md`.

Legend: `[x]` done, `[~]` done differently or partly (see note), `[ ]` not done. Nothing has been committed, and new files are untracked. Both loaders build. GameTests pass on Forge; on Fabric one test failed and its fix has not been rerun.

## Phase 1: Empty-stack duplication

- [x] `HeldTransfers.fillFromHand`: works on a one-count copy and uses `ItemUtils.createFilledResult` (inventory or player's feet; creative keeps the empties).
- [x] Route every player intake through it: Big, Huge and Source world take, powder snow, milking, tank drain, Mob Bucket capture, Junk/Trash world intake, cauldrons.
- [x] Dispensers work on a one-item copy and insert or eject extra results.
- [~] Junk/Trash inventory gestures with a stack of empty buckets: the stack refuses insertion, instead of being split to one.
- [x] Stack scenarios per intake path (BB, MB, Junk, automation).

## Phase 2: One bucket-state authority, checked on load

- [x] Rename `NBTUtil` to `BucketState`; it is the only reader and writer.
- [x] Setters validate capacity, content type and nesting before writing.
- [x] Common `SomeBucketItem` base absorbs `VariableStackItem`; `verifyTagAfterLoad` removes malformed state and logs it.
- [x] Junk set-aside (`JunkSetAside`): logged once, shown in the tooltip, not counted, restored when loadable and there is room; lang key added.
- [x] No migration layer (NBT keys unchanged since 0.8.0).
- [x] Scenarios: malformed content removed, unloadable entry set aside.

## Phase 3: `FluidBucketItem` owns the transfer rules

- [x] `acceptable`/`insert`/`extractable`/`extract` on `BBItem` and `SBItem`.
- [x] Forge `ForgeBucketFluidHandler` and Fabric `FabricBucketStorage` reduced to unit conversion around them.
- [x] `AbstractFluidHandler`, `BBFluidHandler`, `SBFluidHandler` deleted.

## Phase 4: Loader seam and common world-fluid logic

- [x] Common `fluid/FluidTransactions` (replaces `FluidPlacement` and the aquatic water helpers).
- [x] `BucketOperations` reduced to primitives (`blockFluidStore`, `moveHeldFluid`, `placeChecked`, arbitrary-fluid placement, sounds, `fluidDisplayName`, `mobBucketContents`, `permits*`, `beforeWorldBucketUse`).
- [x] Deleted Forge `BBFluidLogic`, `SBFluidLogic`, `FluidPickup`, `Transfers`; rewrote `ForgeFluidPlacement`. Deleted Fabric `FabricFluidPlacement`, `FabricFluidStorages`.
- [x] Failed or refused operations never debit the bucket.
- [x] Placement checked against the block actually placed; Forge restores without drops on refusal.
- [ ] Sculk sensors trigger once on aquatic release: not verified.
- [x] Tanks credited with what actually moved.
- [x] Pickup and placement fire vanilla stats and triggers (cauldron fill stat added).
- [x] Kept: dispensers posting protection events; Forge `FillBucketEvent` (now cancel-only, `ALLOW` ignored).
- [~] Player tank and cauldron use moved to `useOn`, so the block-use event comes from vanilla dispatch; only automation posts block- and entity-interaction events.

## Phase 5: Held transfers

- [x] `HeldTransfers` absorbs `HeldTransferSettlement` and `MilkTransfers`; loaders expose `moveHeldFluid` and `holdsFluid`.
- [x] Overflow goes to the inventory first, then drops.
- [x] `tryOffHandPriority` on both loaders; `ForgeHeldTransferEvents`/`FabricHeldTransferEvents` are thin hooks.
- [x] Kept: `held_transfer_excluded` tag, Mob Bucket exchange with vanilla mob buckets.
- [x] Scenarios: settlement overflow, off-hand priority.

## Phase 6: Common dispensers and cauldrons

- [x] Common `interaction/Dispensers` and `interaction/Cauldrons`, registered from `SomeBuckets.registerBehaviors`.
- [x] Deleted Forge `Dispensers`/`Cauldrons`, Fabric `FabricFluidDispensers`/`FabricCauldronInteractions`, `NonFluidDispensers`, `PowderSnowCauldrons`, `DispenserTarget`.
- [x] `BucketOperations.automationPlayer()` replaces `AutomationPlayers`.
- [x] Kept: dispensers acting as `[SomeBuckets]`, powder-snow cauldron filling.
- [x] Shared `CauldronScenarios` for both loaders.

## Phase 7: Mob and Junk/Trash rules

- [x] Mob Bucket refuses leashed mobs and other players' owned mobs; dispensers capture no owned mob.
- [x] Eligibility checked again at release.
- [x] Capture/release use Bucketable sounds; released mobs get `setFromBucket`.
- [x] Dispensers never feed untamed tamable animals.
- [~] `ItemEntity.target` reached through a common accessor mixin (`ItemEntityAccessor`) instead of an access widener; no Forge AT mirror and no `verifyCommonAccessMirror` task needed.
- [x] Seam methods `allowsItemPickup`, `afterItemPickup`, `tossFromPlayer`.
- [x] Pickups count toward the picked-up statistic; items dropped for another player are left; dispensers ignore the intended recipient.
- [~] `carriesItemContainer`: Forge checks the item-handler capability; Fabric returns `false` (Fabric API 0.92 has no `ItemStorage.ITEM`).
- [x] Dispenser capture fix: capture-priority listeners skip `Protections.isAutomationActor`, so the protection probe no longer captures into the probe copy.

## Phase 8: Fabric allowlist reload and client sync

- [x] Fabric rereads `config/somebuckets-server.json` on `/reload`.
- [x] `FabricSBPolicyNetworking` sends the allowlist on join and on change (1.20.1 channel API); client applies on the client thread and resets on disconnect.
- [x] `SBPolicyNetworkGameTests` (round trip, oversized payload).
- [ ] Confirm Forge's config sync refreshes `SBPolicy` on the client: not verified.

## Phase 9: Client sharing and diagnostics

- [x] Common `client/ClientPlatform` seam (fluid facts, spawn-egg lookup, bar color); caches cleared on resource reload.
- [~] Color preloading on reload: caches are cleared, not preloaded.
- [x] Forge model loaders/BEWLR and Fabric baked-model wrappers stay loader-owned.
- [x] `/sb fluids` and `/sb eggs` client commands, with lang keys.
- [x] Bumblezone entries merged into `mob_bucket_colors.json`.
- [~] Wilder Nature entries added under `wildernature`; entity IDs not verified against a 1.20.1 build.

## Phase 10: Naming, tests and docs

- [x] Loader class prefixes (`ForgeItems`, `ForgeServerConfig`, `ForgeAutomationPlayer`, etc.).
- [x] `every_shared_scenario_has_a_loader_wrapper` on both loaders (Fabric lists its exclusions).
- [x] `orientation-code.md`, `orientation-player.md` updated and within limits.
- [x] `change-list.md` entries added.
- [x] `docs/datapacks.md`: no change needed.
- [x] `build-env.md`: no change needed (no build edits).

## Verification

- [x] Both loaders compile (`gradlew clean build`).
- [x] GameTests pass on Forge.
- [ ] GameTests pass on Fabric: `settlement_overflow_fills_inventory_before_dropping` failed because the mock player's `Player#drop` never adds the item entity on vanilla/Fabric (Forge's toss hook does). The scenario now uses a server player; rerun to confirm.
- [ ] Identify the deprecated API `FluidTransactions` uses (compiler note).
- [ ] Commit (user's call).

## Not ported

- Milk as a loader fluid (declined).
- 1.21.4-only: data components and codecs, item definitions, `FuelValuesMixin`, NeoForge, the 1.21.1 save migration.
