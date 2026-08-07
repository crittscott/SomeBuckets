# Merged code review

This report merges the three independent static reviews. Repeated findings have been consolidated;
the underlying claims were not rechecked against the source. No reviewer built or ran the project.
The implementation is treated as authoritative when it differs from the descriptive documents.

No critical defect was reported.

## Findings

### High h1 — Player Junk/Trash world operations bypass exact-target claim protection

`JBItem.use` passes a null protection context into item-entity absorption
(`src/main/java/com/github/crittscott/somebuckets/item/JBItem.java:101`, `:117`, `:205-212`), and
`TBItem` does the same (`src/main/java/com/github/crittscott/somebuckets/item/TBItem.java:135`). A
right-click-in-air event has no target entity for a claim mod to authorize, so ordinary Forge
right-click hooks do not replace the missing per-target check. A player near a claim boundary can
therefore absorb, or Trash-delete, an `ItemEntity` across that boundary without Some Buckets asking
its registered claim provider about the entity.

The inverse path has the same target mismatch: `JBItem.useOn` removes the oldest stack and spawns it
at the adjacent `dropPos` without authorizing that position (`JBItem.java:128-150`). Minecraft's
normal block-use gate applies to the clicked block, which may be in a different claim from the
neighbor where the entity is created.

The dispenser implementation already authorizes each absorbed entity and the ejection target. Pass
`ProtectionContext.player(player, hand)` into player absorption and authorize the actual `dropPos`
before removing the stored stack. Feeding is less exposed because it begins as a real targeted
entity interaction, though using the same context there would make the protection contract uniform.

### Medium m1 — Mob Bucket release omits vanilla game events and fluid feedback

Aquatic release directly waterlogs or writes a water block in `MBItem.placeWaterFor`
(`src/main/java/com/github/crittscott/somebuckets/item/MBItem.java:95-117`), and all releases add the
restored entity in `releaseOldest` (`MBItem.java:128-158`). These mutations do not emit the
corresponding `GameEvent.FLUID_PLACE` and `GameEvent.ENTITY_PLACE`. Sculk sensors and other listeners
therefore cannot observe the water placement or entity release. The aquatic path also omits normal
water-placement sound feedback.

Emit `FLUID_PLACE` only when water is actually created or waterlogged, play the appropriate placement
sound, and emit `ENTITY_PLACE` after `addFreshEntity` succeeds. Any shared placement primitive must
preserve the Mob Bucket requirement that real water remain at the destination, including in
ultra-warm dimensions.

### Medium m2 — Finite milk reports consumption after changing the consumed stack

`BBItem.finishUsingItem` drains and normalizes the bucket before calling
`CriteriaTriggers.CONSUME_ITEM` (`src/main/java/com/github/crittscott/somebuckets/item/BBItem.java:325-334`).
Vanilla triggers the criterion against the item being consumed before decrementing it. On the final
unit, this implementation presents the advancement system with an empty Big/Huge Bucket rather than
the milk-filled bucket that was consumed, so a `consume_item` advancement with a milk NBT predicate
can miss the event.

Trigger `CONSUME_ITEM` before mutating NBT. Award the statistic in the server-side branch as well,
matching `SBItem.finishUsingItem` and vanilla ordering.

### Medium m3 — Trash Bucket item searches do quadratic work on large piles

`StorageBucketDispenser` materializes every eligible `ItemEntity` in the front block
(`src/main/java/com/github/crittscott/somebuckets/interaction/StorageBucketDispenser.java:46-50`),
then `TBItem.absorbItemEntities` consumes only `entities.get(0)`
(`src/main/java/com/github/crittscott/somebuckets/item/TBItem.java:138-146`). Clearing `n` entities over
successive pulses therefore builds lists of roughly `n`, `n-1`, and so on, producing quadratic
traversal and allocation for an operation intentionally limited to one entity per pulse.

The player path likewise asks for all results (`TBItem.java:129-135`). If none are found, it calls
`JBItem.use` (`TBItem.java:120-121`), which immediately repeats the same search over a smaller,
contained radius even though there is no other fallback behavior to preserve.

Use a one-result lookup for Trash Buckets in both paths and return `PASS` directly after an empty
Trash query. A direct Trash branch is simpler than a generalized collection abstraction.

### Low l1 — General hand-to-hand pumping makes too many foreign capability calls

`Transfers.pump` permits 512 iterations
(`src/main/java/com/github/crittscott/somebuckets/interaction/Transfers.java:38-39`, `:163-188`), with
up to four capability calls per iteration. A valid foreign handler that moves only a small amount per
call can cause roughly two thousand calls during one player interaction. The Source Bucket fast path
already handles this mod's intentional per-call-limited infinite source, while finite Big/Huge
Buckets answer ordinary requests directly.

Use one Forge-style simulate/execute transfer round for the general path and trust the capability
result. This is simpler and places a tighter bound on server work.

### Low l2 — Big Bucket pickup repeats its preflight

`BBFluidLogic.tryTakeWithContext` calls `canAttemptTakeAt`, which discovers the capability or fluid,
simulates a drain, simulates the bucket fill, and checks type and capacity
(`src/main/java/com/github/crittscott/somebuckets/fluid/BBFluidLogic.java:110`). It then rediscovers
the capability and repeats the simulations in `tryTransferFromBlock`, or calls
`FluidPickup.available` and repeats the same state/capacity checks (`BBFluidLogic.java:123-133`). A
partial player-operated bucket may run the preflight once more while resolving the `FillBucketEvent`
target.

Keep the branch-local simulation immediately before execution, as required by the Forge transaction
pattern, and remove the preliminary guard inside `tryTakeWithContext`. The existing branches already
reject impossible transfers; no transaction-plan abstraction is needed.

### Low l3 — Internal-invariant guards silently conceal failures owned by the mod

Several guards defend states established entirely by Some Buckets and turn programming or packaging
errors into normal-looking gameplay failures:

- `StorageBucketDispenser.execute` quietly returns if the registered item is not a `JBItem`
  (`src/main/java/com/github/crittscott/somebuckets/interaction/StorageBucketDispenser.java:26-27`),
  although the behavior is registered only for Junk and Trash Buckets.
- `ClientModelLoaders` skips installation if its own baked Junk Bucket model is absent, and
  `JunkBucketRenderer` draws nothing if that setup failed
  (`src/main/java/com/github/crittscott/somebuckets/client/ClientModelLoaders.java:26-31` and
  `JunkBucketRenderer.java:58-63`).
- Mob capture quietly refuses a live mob with no registry key, while release quietly refuses a
  non-server call, an unresolved stored type, or a type factory returning null
  (`src/main/java/com/github/crittscott/somebuckets/item/MBItem.java:66-72`, `:128-139`).

Use a direct cast where registration guarantees the item type, make server-only contracts explicit
(for example, accept `ServerLevel`), and let impossible internal states fail visibly. Retain guards
for genuinely external input such as corrupt resource-pack files, invalid config ids, unavailable or
denied capabilities, obstructed spawn space, and rejected world/entity insertion.

### Low l4 — Packaged metadata is not useful to players or administrators

The description is still `Get you some buckets!` (`gradle.properties:60`), and `mods.toml` remains
mostly the commented MDK template without a homepage or issue tracker. Replace the description and
add the appropriate support URL before release so administrators can identify the mod's purpose,
compatibility, configuration surface, and support channel.

## Forge and Minecraft fit

Aside from the findings above, the implementation was consistently judged recognizable and
conventional Forge 1.20.1 code:

- Items and the creative tab use deferred registration; common setup registers dispenser,
  cauldron, and custom-ingredient hooks through queued setup work.
- Big, Huge, and Source Buckets expose `IFluidHandlerItem`; world pickup delegates to
  `BucketPickup`/`IFluidBlock`, while placement follows vanilla targeting, waterlogging,
  replaceability, ultra-warm evaporation, sounds, statistics, criteria, and game events.
- Custom ingredients are appropriate for NBT-sensitive empty buckets and the registry-wide spawn-egg
  match.
- Source policy is a server config resolved to an immutable snapshot. Unknown optional-mod fluid ids
  are logged on configuration load/reload rather than repeatedly resolved at interaction boundaries.
- Optional FTB Chunks support is isolated behind a mod-load check. Automation uses a stable fake
  player, and protected mutations generally establish feasibility, authorize the actual target, and
  then mutate.
- Dispenser behavior uses `DispenserBlock`, retains the container, limits scans to the front block,
  and uses the face adjacent to the dispenser for sided capabilities.
- Persistent state lives on the `ItemStack`; the mod introduces no tick-driven world object, packet
  protocol, global saved data, or broad entity scan.

Tank-index validation, capability simulation at the execution boundary, Source policy checks at
input/output boundaries, and client/server mutation splits were judged necessary API contracts, not
over-guarding.

The helper boundaries between NBT representation, world pickup/placement, item behavior, and Forge
entry points reflect distinct responsibilities rather than abstraction for its own sake. Separate
Big/Source and player/dispenser branches encode materially different behavior; forcing them through
a generic bucket framework would be less direct. Stateless `BBFluidLogic` and `SBFluidLogic`
singletons add some ceremony and could be static utilities, but this was considered cleanup rather
than a priority defect. Player and dispenser cauldron duplication is justified by their different
Minecraft entry points and targeting rules.

## Server friendliness

The mod is server-friendly overall. Work is triggered by clicks, crafting, furnace queries, or
dispenser pulses rather than periodic polling. Nearby-entity searches use small AABBs; item and mob
lists are capped at nine and eight entries; UUID collision checks use per-level UUID lookup; Source
policy checks use a cached snapshot; and client rendering does not burden a dedicated server. There
are no tick handlers, chunk loaders, networking, or persistent world indexes.

The Trash Bucket search and hand-transfer loop findings are the reported avoidable server-side
amplifications. Big Bucket's repeated preflight is a smaller source of unnecessary calls into
third-party handlers.

## Administrator surface

The expected controls are otherwise present: a per-world Source Bucket content allowlist, a datapack
entity blacklist for Mob Buckets, replaceable or removable recipe data, and claim behavior governed
through the claim mods' player/fake-player settings. Removing lava from the Source allowlist also
removes infinite Source-Bucket furnace fuel. Capacities and interaction radii do not require settings
merely because they are constants, and the mod does not need a command subsystem.

The player Junk/Trash protection gap is the substantive missing admin control. Packaged project and
support metadata should also be completed before release.

## Test coverage assessment

The reviewed GameTests cover state normalization, finite and infinite capabilities, crafting
remainders, recipes, fuel eligibility, world fluid behavior, cauldrons, transfers, protection
denials, automation, and mob state/UUID restoration.

Reported gaps are player Junk/Trash claim-boundary operations, Mob Bucket game events, real
block-entity fluid-capability transfers including sided handlers, actual FTB Chunks integration,
client model/resource reload behavior, and a dedicated-server startup smoke test. These gaps do not
establish defects, but they cover integration seams likely to regress.

## Overall

The merged assessment is that the code is mostly simple, bounded, and idiomatic Forge code. It is not
generally over-abstracted or over-defensive. The principal release work identified is closing the
player Junk/Trash protection gap, restoring vanilla feedback/event parity for Mob Bucket release,
and removing the limited set of guards that distrust Some Buckets' own registrations and call paths.
