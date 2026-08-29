# Fabric 1.21.1 Port Status

This is the compact execution snapshot. It is overwritten in place whenever the position changes and
is never appended to. Per-command history and completed-stage notes live in the append-only
`fabric-1.21.1-port-log.md`, which is not read during execution.

`fabric-1.21.1-port-process.md` governs how this snapshot is maintained.

## Current position

| Field | Value |
| --- | --- |
| Overall state | **COMPLETE** — Fabric 1.21.1 port finished; every completion gate passes |
| Current stage | None — Stage 8 done |
| Current work unit | None |
| Failed verification attempts used | n/a |
| Stable documents read this session | Yes |
| Forge compile state | Passing; never re-compiled because no `common` change was made in the port |
| Last updated | 2026-08-29 |

## Outcome

The Fabric artifact for Minecraft 1.21.1 (Fabric Loader 0.19.3, Fabric API 0.116.15+1.21.1, Java 21)
compiles, packages, starts under the Fabric GameTest server, and passes the full ported automated
suite (167/167). The Quilt artifact is the same Fabric jar. Player-visible behavior in
`player-view.md` is unchanged by the port.

The user built the artifact and tested it in game. One minor client issue was observed and
deliberately deferred ("fix later"); it is not yet described in `player-view.md`'s visible-limitations
list — add it there once specified.

## Completion gate record

| Gate | Status | Evidence |
| --- | --- | --- |
| `:common:compileJava` | Passing | exit 0 (unchanged all port) |
| `:forge:compileJava` | Passing | regression guard; no `common` change was ever made, so not re-run |
| `:fabric:compileJava` | Passing | exit 0, 0 errors (Stage 5, 2026-08-29) |
| `:fabric:processResources` | Passing | exit 0 (Stage 4, 2026-08-28) |
| `:fabric:compileGametestJava` | Passing | exit 0, 0 errors (Stage 6, 2026-08-29) |
| `:fabric:runGameTestServer` | Passing | 167/167 tests, clean exit (Stage 7, 2026-08-29, user-run) |
| `:fabric:build` | Passing | BUILD SUCCESSFUL (Stage 8, 2026-08-29, user-run) |

## Documentation reconciliation (Stage 8)

- `as-built.md`: version bumped to Java 21 / MC 1.21.1; `neoforge` scaffolding-only subproject noted;
  `StoredFluid` conversion paragraph names `ForgeFluidStacks` / `FabricFluidVariants`; stale
  "Variable item stack size" loader-specific row removed (now the common `MAX_STACK_SIZE` component
  written by `NBTUtil`); furnace row cites `ForgeFuelEvents`; persistent-state section states the
  schema lives in `minecraft:custom_data` with `MAX_STACK_SIZE` maintained at the write boundary.
- `player-view.md`: one wording tweak ("Forge fluid tank" -> loader-neutral); behavior unchanged.
- `build-env.md`: already on the 1.21.1 baseline; no change.

## Behavior-affecting decisions made during the port

None. The port preserved every documented player behavior, capacity, gesture priority, protection
rule, fuel value, transfer-settlement rule, and persistence-ownership invariant. All changes were
API migrations. Key mechanism decisions (unchanged behavior):

- `FabricFluidVariants` converts `StoredFluid`'s optional variant `CompoundTag` <-> `FluidVariant`'s
  `DataComponentPatch` via `DataComponentPatch.CODEC` over plain `NbtOps`, degrading a
  registry-context-requiring component to a blank patch (user-approved "graceful degradation").
  Round-trip exercised by the Stage 7 suite.
- `ItemStackMixin` deleted; the vanilla `MAX_STACK_SIZE` data component (written by `NBTUtil` on
  every state mutation, propagated by `fabric/.../util/BucketStackState` across Transfer API
  snapshots) is the sole variable-stack mechanism on Fabric, matching the Forge port.
- `AbstractFurnaceBlockEntityMixin` retained (`compatibilityLevel` `JAVA_21`); its `isFuel` /
  `getBurnDuration` inject targets held at runtime (finite lava-bucket fuel tests pass).
- Cauldrons use `CauldronInteraction.InteractionMap` + `ItemInteractionResult`; loot injection uses
  `fabric.api.loot.v3` with `ResourceKey<LootTable>` identity and `SetCustomDataFunction`; custom
  ingredients use the 1.21 codec/stream-codec `CustomIngredientSerializer`; identifiers use
  `ResourceLocation.fromNamespaceAndPath` / `.parse`.
- Client: `ModelModifier.AfterBake.Context#resourceId()` (was `id()`) with a null guard;
  `FabricJunkBucketRenderer` reads client registry access for `NBTUtil.getStoredItems`.

## Deferred / out of scope

- **NeoForge runtime implementation.** The `neoforge` subproject has build, dependency, metadata,
  and packaging scaffolding only — no loader Java, no GameTest source. Not attempted; not regressed
  because it had no runtime code to begin with.
- **One minor client issue** from the manual in-game smoke test — deferred by the user. Needs a
  one-line description, then belongs in `player-view.md` "Visible limitations".

## Recommended manual client checks (largely satisfied by the user's in-game test)

Fluid tint incl. a variant-bearing modded fluid if available; milk and powder-snow overrides; Mob
Bucket empty/filled model and spawn-egg colors; Junk Bucket protruding item order, tint, and glint;
creative-tab contents and prefilled variants; tooltips, bars, use animations, and sounds. The user
reported these working apart from the one deferred minor issue.

## Blockers

None.

## Next action

None — port complete. If the user specifies the minor client issue, add it to `player-view.md`
"Visible limitations" and open a small bounded fix. No Git or GitHub action is authorized.
</content>
</invoke>
