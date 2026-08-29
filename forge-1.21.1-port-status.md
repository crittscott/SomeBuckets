# Forge 1.21.1 Port Status

This is the compact execution snapshot. It is overwritten in place whenever the position changes and
is never appended to. Per-command history, the running file-change list, and completed-stage notes
live in the append-only `forge-1.21.1-port-log.md`, which is not read during execution.

`forge-1.21.1-port-process.md` governs how this snapshot is maintained.

## Current position

| Field | Value |
| --- | --- |
| Overall state | Port in progress |
| Current stage | Stage 8 — runtime GameTest stabilization |
| Current work unit | Stage 8D — synthetic-player GameTest fixtures |
| Work-unit state | In progress |
| Failed verification attempts used | 0 of 3 |
| Stable documents read this session | Yes |
| Last updated | 2026-08-28 |

## Stable controlling documents

Read once per session, then not again this session: `CLAUDE.md`, `forge-1.21.1-port-plan.md`,
`forge-1.21.1-port-process.md`, and this snapshot. `forge-1.21.1-port-assessment.md` is reference
material, consulted by section when a stage needs it. The log is never read during execution.

## Current work-unit definition — Stage 8D

### Scope

- Give the two milk-consumption scenarios a minimally connected synthetic `ServerPlayer` so
  1.21.1 effect synchronization can send its removal packet.
- Make the two air gestures prove a `MISS` through cleared fixture columns, and make the two
  placement gestures ray-trace a known side of a known target block.
- Preserve all production behavior and assertions; only the GameTest player fixtures change.

### Intended files

- `common/src/gametest/java/com/github/crittscott/somebuckets/gametest/SharedGameTestSupport.java`
- `common/src/gametest/java/com/github/crittscott/somebuckets/gametest/BBScenarios.java`
- `common/src/gametest/java/com/github/crittscott/somebuckets/gametest/SBScenarios.java`
- `forge/src/gametest/java/com/github/crittscott/somebuckets/gametest/FillBucketEventGameTests.java`
- This snapshot; one appended line per verification in the log.

### Verification command

```powershell
.\gradlew.bat :forge:runGameTestServer --console=plain
```

### Completion condition

- Both milk-consumption tests remove their effects and retain their finite/infinite content rules.
- Both air-clearing tests empty their assigned bucket, and both placement tests mutate and report
  the exact intended neighbor.
- No previously passing test regresses.

## Last command and result

`.\gradlew.bat :forge:runGameTestServer --console=plain` (Stage 8C verification, run by the user):
all 185 tests ran, 179 passed, and 6 failed. All six targeted positive vanilla-bucket transfer tests
passed, including stacked settlement and event-bus dispatch, with no new failures.

## Remaining known failure classes

- Two milk-consumption tests use a synthetic `ServerPlayer` whose null connection cannot receive
  the 1.21.1 effect-removal packet.
- Four ray-traced `use()` tests require actual-hit diagnosis: two air-clearing cases, one Source
  Bucket placement, and one FillBucketEvent target assertion.
- `:forge:build` not yet run.
- Forge production compile passes with four warnings from the common Fabric client annotation.

## Established technical decisions

- Target Minecraft 1.21.1 and Forge 52.1.16 on Java 21.
- Forge-first: common changes required by Forge are in scope. Temporary Fabric breakage is accepted
  and not repaired during this port. NeoForge runtime work is deferred.
- Forge FTB Chunks support is dropped; no 1.21.1 Forge artifact exists.
- No backward compatibility with unreleased 1.20.1 item data or worlds.
- Bucket state stays in the built-in `minecraft:custom_data` component with `NBTUtil` retaining
  aggregate schema ownership; nested stacks use explicit registry-aware encoding.
- Variable bucket stack limits use the vanilla `MAX_STACK_SIZE` data component, refreshed at the
  `NBTUtil` write boundary.
- Forge 52.1 custom ingredients use `net.minecraftforge.common.crafting.ingredients`:
  `AbstractIngredient#serializer`, an `IIngredientSerializer` supplying a `MapCodec` plus
  `RegistryFriendlyByteBuf` read/write, registered in `ForgeRegistries.Keys.INGREDIENT_SERIALIZERS`.
- Forge 52.1 removed `FakePlayer` / `FakePlayerFactory` with no replacement. Forge dispenser feeding
  applies the vanilla baby-growth or adult-love outcome directly after authorization and consumes
  one stored food item; player-driven feeding stays on the animal interaction path. Fabric may still
  install its automation player for claim adapters.
- Minecraft 1.21.1 cauldron interactions return `ItemInteractionResult`, registered through
  `InteractionMap#map`.
- Existing shared and Forge GameTests are the primary unattended runtime verification system.
- Forge 52.1 leaves `FluidUtil.getFluidHandler(ItemStack)` temporarily disabled. Held-container
  lookup therefore prefers an exposed capability and otherwise uses Forge's public
  `FluidBucketWrapper` for `BucketItem`s.
- No Git or GitHub operations are authorized.

## Blockers

None.

## Exact next action

The user runs `.\gradlew.bat :forge:runGameTestServer --console=plain` and reports the result; the
agent performs no Gradle actions.

## Cumulative gate record

| Gate | Status | Last result |
| --- | --- | --- |
| `:common:compileJava` | Passed | Successful on Stage 2B |
| `:forge:compileJava` | Passed | Successful on Stage 6C; 4 annotation warnings |
| `:forge:processResources` | Passed | Successful after Stage 5D resource migration and loot generation |
| `:forge:compileGametestJava` | Passed | Successful on Stage 7F |
| `:forge:runGameTestServer` | Failing, classified | Stage 8C: 185 tests ran; 179 passed and 6 failed |
| `:forge:build` | Not run | — |
