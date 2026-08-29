# Fabric 1.21.1 Port Status

This is the compact execution snapshot. It is overwritten in place whenever the position changes and
is never appended to. Per-command history, the running file-change list, and completed-stage notes
live in the append-only `fabric-1.21.1-port-log.md`, which is not read during execution.

`fabric-1.21.1-port-process.md` governs how this snapshot is maintained.

## Current position

| Field | Value |
| --- | --- |
| Overall state | Not started |
| Current stage | Stage 0 — Baseline diagnostics |
| Current work unit | Stage 0 baseline diagnostic |
| Work-unit state | Not started |
| Failed verification attempts used | 0 of 3 |
| Stable documents read this session | No — a new session must read them first |
| Forge compile state | Passing (completed Forge 1.21.1 port); must stay green |
| Last updated | 2026-08-27 |

## Stable controlling documents

Read once per session, then not again this session: `CLAUDE.md`, `fabric-1.21.1-port-plan.md`,
`fabric-1.21.1-port-process.md`, and this snapshot. `fabric-1.21.1-port-assessment.md` and the
completed `forge-1.21.1-port-status.md` are reference material, consulted by section when a stage
needs it. The log is never read during execution.

## Current work-unit definition — Stage 0 baseline diagnostic

### Scope

- Run one baseline `:fabric:compileJava` diagnostic in the current build environment.
- Classify every compiler failure against Stages 1–5 of `fabric-1.21.1-port-plan.md`.
- Do not edit code in response to unclassified errors.

### Intended files

- `fabric-1.21.1-port-status.md` (this snapshot)
- `fabric-1.21.1-port-log.md` (one appended baseline entry)

### Verification command

```powershell
.\gradlew.bat :fabric:compileJava --console=plain
```

### Completion condition

- The snapshot records a reproducible baseline error count and classification, and a finite Stage 1
  work unit as the next action.

## Last command and result

None yet.

## Remaining known failure classes

Not yet established — Stage 0 produces the first classification. Expected groups from the assessment:

- common reconciliation under the Fabric transform (Stage 1);
- Fabric bootstrap, identifiers, metadata (Stage 2);
- Transfer API stack copies and `FluidVariant` payload conversion (Stage 3);
- custom ingredients, loot events, cauldrons, dispensers, mixins, config (Stage 4);
- client model, color, and renderer APIs (Stage 5).

## Established technical decisions

- Target Minecraft 1.21.1, Fabric Loader 0.19.3, Fabric API 0.116.15+1.21.1, Java 21.
- Fabric-first. Common may change where Fabric requires it, but `:forge:compileJava` must stay green;
  every common change is followed by a Forge re-compile before the stage closes.
- NeoForge runtime work is deferred. The Fabric artifact is also the Quilt artifact.
- The completed Forge 1.21.1 port already carried `common/src/main` and `common/src/gametest` to
  1.21.1 (component-backed `NBTUtil` state via `minecraft:custom_data`, vanilla `MAX_STACK_SIZE`
  component, registry-aware nested stack codecs, shared GameTest scenario bodies).
- Fabric keeps its FTB Chunks integration (`ftb-chunks-fabric:2101.1.21` exists) and its
  `fabric-api` fake player, unlike the Forge port which dropped both.
- Default plan for `StoredFluid` to `FluidVariant`: a Fabric-module-only `CompoundTag <->
  DataComponentPatch` conversion helper, leaving `StoredFluid`'s common shape unchanged. Revisit only
  if the Fabric compile proves the boundary-only approach cannot preserve variant data.
- Likely `ItemStackMixin` removal: the common `MAX_STACK_SIZE` component should make the Fabric
  max-stack-size mixin redundant. Confirm with a GameTest before deleting.
- No Git or GitHub operations are authorized.

## Blockers

None.

## Exact next action

Run `.\gradlew.bat :fabric:compileJava --console=plain`, reduce the output to an error count and the
major error groups, classify each group against Stages 1–5, append one baseline entry to
`fabric-1.21.1-port-log.md`, and record the first Stage 1 work unit as the next action in this
snapshot.

## Cumulative gate record

| Gate | Status | Last result |
| --- | --- | --- |
| `:common:compileJava` | Passing (from Forge port) | Not re-run for Fabric |
| `:forge:compileJava` | Passing (regression guard) | Not re-run this port |
| `:fabric:compileJava` | Not run | — |
| `:fabric:processResources` | Not run | — |
| `:fabric:compileGametestJava` | Not run | — |
| `:fabric:runGameTestServer` | Not run | — |
| `:fabric:build` | Not run | — |
