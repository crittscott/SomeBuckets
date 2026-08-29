# Fabric 1.21.1 Unattended Port Process

## Purpose

This process governs autonomous execution of `fabric-1.21.1-port-plan.md`. It permits sustained
code, test, compile, runtime-test, and fix work while preventing endless iteration, unjustified
redesign, environment rewrites, and loss of state across turns or context compaction. It also keeps
each session's context bounded to a single stage so execution does not become dominated by re-reading
accumulated history.

`CLAUDE.md` remains controlling. This process grants no permission to ignore its source-inspection,
Git, cache, environment, or project-scope restrictions.

## Authorized work

Within the current Fabric port, unattended execution may:

- edit Fabric production source and the common production or test source Fabric depends on, as
  required by the staged plan;
- edit shared and Fabric GameTests required to preserve their existing coverage;
- edit checked-in resources and Fabric metadata required for Minecraft 1.21.1;
- make narrowly necessary Gradle source-set or test-task adjustments within the existing build
  architecture;
- run the Gradle compile, resource, GameTest, and build commands named in the plan, including the
  `:forge:compileJava` regression guard;
- use read-only project searches and inspect project-generated text logs and reports;
- consult official published Fabric API sources and documentation when the local project and
  compiler diagnostics are insufficient.

This authorization does not include Git operations, publishing, network service changes, IDE
automation, operating-system changes, cache manipulation, dependency upgrades, `fabric/build.gradle`
structural changes, or NeoForge runtime work.

## Do not regress Forge

The Forge 1.21.1 port is complete. Any change under `common/src/main` or `common/src/gametest` must
be followed by `:forge:compileJava --console=plain` before the current stage closes. A Forge
regression is a failed verification for the work unit that caused it. If a Fabric requirement and a
Forge invariant genuinely conflict in common code, stop and report the conflict; do not weaken
either loader unilaterally.

## Persistent execution state

Execution state lives in two files with opposite disciplines:

- `fabric-1.21.1-port-status.md` is a small bounded snapshot of the current position. It is
  **overwritten in place** whenever it changes and is never appended to. It is the only state file
  read during the work loop.
- `fabric-1.21.1-port-log.md` is an **append-only** history. It is written to but never read back
  during execution; it exists as the audit trail for a human reviewer.

The snapshot stays short. It contains only:

- current stage and work unit;
- work-unit state: not started, in progress, passed, blocked, or complete;
- verification attempt count for the current work unit;
- whether the stable controlling documents have been read this session;
- the current work-unit definition: scope, intended files, verification command, completion condition;
- last command and one-line result;
- remaining known failure classes assigned to later stages;
- established technical decisions and their evidence;
- blockers;
- exact next action;
- the cumulative gate record.

The snapshot is overwritten:

- when a work unit is bounded, passes, or blocks;
- after a material implementation decision;
- before ending a session with unfinished work.

The log receives one appended entry:

- after every verification command — command, exit status, error count, and the delta from the
  previous run, not the raw compiler output;
- when a stage completes;
- when a work unit reaches the attempt limit (all three hypotheses, edits, commands, and results).

Do not copy raw Gradle output into either file. Reduce it to a count and a delta first.

A resumed agent reads `CLAUDE.md`, the plan, this process, and the snapshot before acting, then sets
the snapshot's "stable documents read this session" field. It does not re-read those documents again
for the rest of the session, and it does not read the log. `fabric-1.21.1-port-assessment.md` and the
completed `forge-1.21.1-port-status.md` are reference material: consult the relevant section when a
stage needs it, not wholesale. Continue from the recorded next action rather than restarting the
port or repeating completed verification.

## Definition of a bounded work unit

A work unit is one coherent behavioral group, implemented and checked together. It is not the
smallest possible edit. A stage's worth of mechanical migration — identifier factories, signature
changes, renamed APIs across many files — is a single work unit with a single verification at the
end, not one unit per file or per error cluster. Reserve finer granularity for units that carry real
risk: the `StoredFluid` to `FluidVariant` conversion, the Transaction-layer stack copies, the custom
ingredients, the client model port, and any change to a documented invariant or to common code.

Within a stage, run one diagnostic at the start, implement everything the evidence already makes
visible, and run one verification at the end. Intermediate file inspection and `rg` searches are
cheap and unrestricted; do not spend a verbose compile to confirm a partial edit.

Examples of a single work unit:

- all Fabric-module identifier, lifecycle, and metadata changes;
- the `FluidVariant` payload conversion and its application across the Fabric fluid paths;
- both custom ingredients together;
- the cauldron `ItemInteractionResult` migration;
- the Fabric fluid container model;
- one demonstrated shared cause of several failing GameTests.

A work unit must name its files, intended behavior, verification command, and completion condition in
the snapshot before editing begins.

Do not expand a work unit because a compile exposes an unrelated later-stage error. Record that error
for its planned stage.

## Standard work loop

For each work unit:

1. **Orient.** Read the snapshot. Read the stable controlling documents only if this session has not
   yet loaded them. Read the relevant production files and tests.
2. **Bound.** Record scope, invariants, intended files, and the narrowest useful verification in the
   snapshot.
3. **Diagnose.** Use existing compiler/test output, or run at most one diagnostic command before the
   first edit when evidence is missing.
4. **Implement.** Make the full coherent change for the work unit using current Minecraft/Fabric
   conventions and the existing project seams.
5. **Inspect.** Re-read edited files and search for stale API use. Do not use Git to review changes.
6. **Verify.** Run the narrowest plan gate that can evaluate the work, once. Add `:forge:compileJava`
   if the work unit changed common code.
7. **Record.** Overwrite the snapshot with the new position, attempt count, and next action. Append
   one line to the log: command, exit status, error count, and delta from the previous run. Do not
   paste raw output into either file.
8. **Advance or repair.** Mark the work unit passed and move on, or perform a bounded correction
   cycle under the three-attempt rule.

Passing a narrow gate does not excuse skipping later cumulative gates.

When the work unit that satisfies the current stage's primary gate passes, the session ends there.
Do not pick up the next stage's first work unit in the same session; record the handoff and stop.

## Three-attempt rule

Each bounded work unit may have no more than three failed verification runs after implementation
begins.

An attempt consists of:

1. a stated failure hypothesis;
2. a bounded edit intended to correct it; and
3. execution of the work unit's verification command.

The first failed post-edit verification is attempt 1. After attempt 3 fails, stop. Do not make a
fourth correction, change the gate, enlarge the work unit, weaken a test, or reset the counter under
a new failure label.

Rules for counting:

- The single pre-edit baseline diagnostic is not an attempt because no correction has yet been
  claimed.
- Every failed verification after an edit counts, including a different compiler or test failure
  exposed by the preceding fix within the same work unit, and including a Forge regression.
- A successful verification closes the work unit. The counter resets only when the next separately
  bounded work unit begins.
- An infrastructure or dependency-resolution failure may be retried once with the exact same command
  and no environment changes; the failed run still counts. A repeated infrastructure failure stops
  the work unit.
- A command that hangs or never reaches a test result is a failed attempt. Do not manipulate Gradle
  daemons, caches, processes, or the operating system to force progress.

When the limit is reached, the log must record all three hypotheses, edits, commands, and results,
and the snapshot must record the blocked state and the smallest decision or information needed from
the user.

## Verification ladder

Always use the narrowest sufficient level and then the cumulative gates named by the plan:

1. focused file inspection and `rg` searches;
2. deterministic JSON/resource inspection;
3. `:common:compileJava` for common production code;
4. `:forge:compileJava` as the regression guard after any common change;
5. `:fabric:compileJava` for Fabric production code;
6. `:fabric:processResources` for generated and copied production data;
7. `:fabric:compileGametestJava` for automated test source;
8. `:fabric:runGameTestServer` for runtime behavior;
9. `:fabric:build` for final packaging.

Do not run a broad gate repeatedly when a narrower gate can prove the current correction. Do not use
`clean` as a routine precursor. Do not use `--refresh-dependencies`, delete run directories, alter
Gradle user settings, or touch caches to address a code failure.

Verification output is transient. Read it in the turn it is produced, reduce it to an exit status, an
error count, and the delta from the previous run, and record only that. Do not quote the error list
back in a later turn or copy it into the snapshot or log; re-run the command if the detail is needed
again.

For a compile that is expected to remain red because later stages are not ported, current-stage
success means:

- no compiler errors remain in the current work unit;
- any remaining errors are classified in the snapshot against later stages; and
- the current edit did not increase unrelated failures or regress Forge.

The explicitly passing gates in the plan remain mandatory.

## Test discipline

- Existing behavior assertions are specifications unless they conflict with `player-view.md`, the
  code's documented invariants, or an explicitly accepted 1.21.1 or Fabric API semantic change.
- Never delete, disable, ignore, or weaken a test merely to make a gate pass.
- Never convert an assertion into logging or catch an exception that should fail the test.
- Update tests for renamed APIs, new context parameters, current fixture formats, and intentional
  behavior changes only.
- When several tests fail, group them into one work unit only when evidence identifies one shared
  production cause.
- A GameTest run with no discovered tests is a failure.
- A dedicated-server classloading failure is a production failure, even if client compilation
  succeeds.

New tests should be added when a port changes a central boundary and the existing suite does not
exercise its invariant — in particular the `StoredFluid` to `FluidVariant` variant round trip. Avoid
adding a new testing framework when the existing GameTest system can express the requirement.

## Decision rules

Unattended work may decide between implementation details when all of the following are true:

- player-visible behavior remains unchanged;
- the choice stays inside the current work unit and existing subsystem ownership;
- it uses a documented Minecraft or Fabric facility;
- it adds no dependency or plugin;
- it does not require Forge to be changed, or if it changes common it keeps Forge compiling;
- it is reversible without data migration or broad redesign;
- one option is clearly smaller or more consistent with existing project principles.

When two approaches remain plausible, prefer in this order:

1. current vanilla or Fabric API facility;
2. existing Some Buckets abstraction seam;
3. smallest explicit context plumbing;
4. contained duplication over a new cross-cutting abstraction;
5. a documented stop for user choice.

Compiler errors establish that an API use is wrong; they do not by themselves establish the correct
behavioral replacement.

## Immediate stop conditions

Stop without using the remaining attempt budget when progress would require any of the following:

- changing Minecraft, Fabric Loader, Fabric API, Gradle, Loom, Architectury, plugin, mapping, or JDK
  versions, or `fabric/build.gradle` structure;
- adding or replacing a dependency, repository, plugin, loader, or test framework;
- changing the OS, IDE, global Java setup, environment variables, network configuration, Gradle user
  home, daemon state, or caches;
- decompiling, unarchiving, inspecting bytecode, or reading prohibited cached/remapped Minecraft or
  Forge sources;
- a Git or GitHub operation;
- destructive deletion or broad movement outside an explicitly named resource-directory migration;
- changing documented player behavior, capacities, gesture priorities, protection rules, fuel
  values, transfer settlement, or persistence ownership;
- redesigning `StoredFluid`'s common shape, or any other persistence-ownership change, without
  explicit user confirmation;
- deciding to support legacy 1.20.1 data after all;
- weakening the Forge build or implementing NeoForge to make a Fabric gate pass;
- deleting a feature or test because its current API is inconvenient;
- introducing a loader API or global registry singleton into common production code without an
  established project seam;
- a conflict with unrelated user changes that cannot be preserved;
- contradictory evidence about the current API that cannot be resolved from permitted documentation
  and compiler diagnostics;
- three failed verification attempts for the current work unit;
- the current stage's primary gate is already satisfied — completing a stage is itself a hard stop,
  regardless of remaining token or time budget, and the next stage is a separate session (see
  "Session and turn boundaries").

At an immediate stop, make no speculative workaround.

## Runtime command handling

- Use plain console output so failures are recordable.
- Run compile, resource, and GameTest commands in the background and wait for the completion
  notification rather than polling. Inspect the output once, when it finishes.
- Poll manually only when a run cannot be backgrounded, and then no more than once per minute.
- A normal compile or build that has produced no terminal result after 10 minutes should be treated
  as an environmental blocker.
- A GameTest server that has not exited or produced a decisive suite result after 20 minutes should
  be treated as blocked.
- Do not kill unrelated processes, stop global daemons, or clear caches. Record any still-running
  command/session in the snapshot and stop further work.
- Do not launch an interactive client as part of unattended completion.

## Scope fences

### In scope

- `common/src/main` and `common/src/gametest` as required by Fabric, with a Forge re-compile;
- `fabric/src/main`;
- `fabric/src/gametest`;
- shared resources used by Fabric;
- Fabric metadata and mixin configuration;
- narrowly necessary existing Gradle test/run configuration;
- root port documents and final orientation updates.

### Out of scope

- Forge production or test changes beyond preserving its compile;
- NeoForge runtime implementation;
- release publishing or Git history;
- unrelated refactoring, formatting, or documentation cleanup;
- performance redesign without a demonstrated port regression;
- manual client interaction.

## Session and turn boundaries

Run exactly one stage per session, and stop at the stage boundary without exception. When the
current stage's primary gate is satisfied — the gate passes, or for a diagnostic stage the stage's
own failures are gone and every remaining compiler failure is classified against a later stage —
stop immediately and hand off to the user. In the same session, do not begin the next stage, read
its scope, bound its first work unit, or run any further verification, even when tokens and wall
clock time remain. The next stage starts only in a new session that loads the snapshot and the
stable documents once. This keeps each session's context proportional to one stage rather than the
whole port; carrying a second stage into the session is the specific failure this rule exists to
prevent.

Token exhaustion is a handoff boundary, not a reason to rush or enlarge a work unit.

Before ending an unfinished session:

1. finish the current safe atomic edit if possible;
2. do not start a verification command that cannot be monitored to completion;
3. overwrite the snapshot with the complete current position;
4. name the exact next file or command;
5. report whether the current files are expected to compile, and whether Forge still compiles;
6. leave the attempt count unchanged and explicit.

The next session resumes from that exact action. It does not repeat a passing gate unless later edits
could have invalidated it.

## Blocked handoff format

When stopping for the user, report:

- stage and bounded work unit;
- intended invariant;
- files changed;
- exact verification command;
- concise result of each failed attempt;
- current best diagnosis;
- smallest user decision, API information, or external-state change required;
- whether the workspace is compile-ready, intentionally incomplete, or has a running command, and
  the Forge compile state.

The snapshot must already carry the current position, diagnosis, and required decision, and the log
must already carry the per-attempt results, so the next session does not depend on the chat
transcript.

## Completion handoff

When every gate passes:

- mark the snapshot `complete`;
- record every final passing command in the log;
- summarize behavior-affecting decisions in the snapshot;
- list deferred NeoForge work and any Forge follow-up;
- list recommended manual client checks;
- do not commit, publish, or modify Git.
