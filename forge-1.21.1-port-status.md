# Forge 1.21.1 Port Status

## Current position

| Field | Value |
| --- | --- |
| Overall state | Not started |
| Current stage | Stage 0 — Baseline diagnostics |
| Current work unit | Establish the initial Forge production compile baseline |
| Work-unit state | Ready |
| Failed verification attempts used | 0 of 3 |
| Last updated | 2026-08-26 |

## Controlling documents read for the current position

- `CLAUDE.md`
- `player-view.md`
- `as-built.md`
- `build-env.md`
- `forge-1.21.1-port-assessment.md`
- `forge-1.21.1-port-plan.md`
- `forge-1.21.1-port-process.md`

## Established decisions

- Target Minecraft 1.21.1 and Forge 52.1.16 on Java 21.
- Port Forge first; common changes required by Forge are in scope.
- Temporary Fabric breakage is acceptable and will not be repaired during this port.
- NeoForge runtime work is deferred.
- Forge FTB Chunks support is dropped because no 1.21.1 Forge artifact is available.
- No backward compatibility with unreleased 1.20.1 item data or worlds is required.
- The initial state-port preference is the built-in custom-data component with `NBTUtil` retaining
  aggregate schema ownership.
- Existing shared and Forge GameTests are the primary unattended runtime verification system.
- No Git or GitHub operations are authorized.

## Current work-unit definition

### Scope

- Run one diagnostic Forge production compile.
- Classify errors against Stages 1–6 without editing opportunistically.
- Select and record the first bounded Stage 1 implementation unit.

### Intended files

- This status document only, unless the diagnostic reveals that the build cannot start for an
  environmental reason.

### Diagnostic command

```powershell
.\gradlew.bat :forge:compileJava --console=plain
```

### Completion condition

- Compiler failures are grouped by planned stage.
- The next bounded work unit names its files, invariant, and verification command.

## Verification history

No port verification commands have been run yet. Gradle sync was reported successful after the
build-environment conversion.

## Files changed by port execution

None. The assessment, plan, process, and initial status documents establish the execution framework;
production port execution has not begun.

## Known expected failure groups

- Raw `ItemStack` tag access and nested stack serialization in common state code.
- Minecraft 1.21.1 identifier and item method signatures in common code.
- Obsolete Forge FTB Chunks reference and static loading-context access.
- Forge item capability attachment.
- Forge custom ingredient serializer APIs.
- Forge global-loot-modifier codec types.
- Cauldron and other interaction signature changes.
- Forge client geometry and baked-model signatures.
- GameTest API and resource-format changes after production compiles.

These are expectations, not verified compiler output. Stage 0 will replace this list with observed
failure groups.

## Blockers

None.

## Exact next action

Run the Stage 0 diagnostic command, record its terminal result here, classify the failures, and
define Stage 1A as a bounded work unit before editing production code.

## Completed stages

None.

## Final gate record

| Gate | Status | Last result |
| --- | --- | --- |
| `:common:compileJava` | Not run | — |
| `:forge:compileJava` | Not run | — |
| `:forge:processResources` | Not run | — |
| `:forge:compileGametestJava` | Not run | — |
| `:forge:runGameTestServer` | Not run | — |
| `:forge:build` | Not run | — |
