# Forge 1.21.1 Unattended Port Process

## Purpose

This process governs autonomous execution of `forge-1.21.1-port-plan.md`. It permits sustained code,
test, compile, runtime-test, and fix work while preventing endless iteration, unjustified redesign,
environment rewrites, and loss of state across turns or context compaction.

`CLAUDE.md` remains controlling. This process grants no permission to ignore its source-inspection,
Git, cache, environment, or project-scope restrictions.

## Authorized work

Within the current Forge port, unattended execution may:

- edit common and Forge production source required by the staged plan;
- edit shared and Forge GameTests required to preserve their existing coverage;
- edit checked-in resources and loader metadata required for Minecraft 1.21.1;
- make narrowly necessary Gradle source-set or test-task adjustments within the existing build
  architecture;
- run the Gradle compile, resource, GameTest, and build commands named in the plan;
- use read-only project searches and inspect project-generated text logs and reports;
- consult official published documentation when the local project and compiler diagnostics are
  insufficient.

This authorization does not include Git operations, publishing, network service changes, IDE
automation, operating-system changes, cache manipulation, dependency upgrades, or work on other
loaders.

## Persistent execution state

`forge-1.21.1-port-status.md` is the authority for execution state. It must be updated:

- before beginning a new stage or bounded work unit;
- after every verification command;
- after a material implementation decision;
- before ending a turn with unfinished work;
- immediately when work stops on a blocker or attempt limit.

The status entry must contain:

- current stage and work unit;
- state: not started, in progress, passed, blocked, or complete;
- verification attempt count for the current work unit;
- files changed in the work unit;
- last command and concise result;
- remaining known failures assigned to later stages;
- decisions and their evidence;
- exact next action.

A resumed agent must read `CLAUDE.md`, the assessment, plan, process, and status before acting. It
must continue from the recorded next action rather than restarting the port or repeating completed
verification.

## Definition of a bounded work unit

A work unit is the smallest coherent change that can be implemented and checked without mixing
independent subsystems. Examples include:

- replacing simple raw stack-tag access in `NBTUtil`;
- threading registry context through Junk Bucket storage;
- porting the two custom ingredients;
- adapting Forge item capability attachment;
- porting cauldron results;
- porting the Junk Bucket renderer;
- fixing one demonstrated shared cause of several failing GameTests.

A work unit must name its files, intended behavior, verification command, and completion condition
in the status file before editing begins.

Do not expand a work unit because a compile exposes an unrelated later-stage error. Record that
error for its planned stage.

## Standard work loop

For each work unit:

1. **Orient.** Read the controlling documents, current status, relevant production files, and
   relevant tests.
2. **Bound.** Record scope, invariants, intended files, and the narrowest useful verification.
3. **Diagnose.** Use existing compiler/test output or run at most one diagnostic command before the
   first edit when evidence is missing.
4. **Implement.** Make one coherent change using current Minecraft/Forge conventions and the
   existing project seams.
5. **Inspect.** Re-read edited files and search for stale API use. Do not use Git to review changes.
6. **Verify.** Run the narrowest plan gate that can evaluate the work.
7. **Record.** Update status with the command, result, attempt count, files, and next action.
8. **Advance or repair.** Mark the work unit passed and move on, or perform a bounded correction
   cycle under the three-attempt rule.

Passing a narrow gate does not excuse skipping later cumulative gates.

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
  exposed by the preceding fix within the same work unit.
- A successful verification closes the work unit. The counter resets only when the next separately
  bounded work unit begins.
- An infrastructure or dependency-resolution failure may be retried once with the exact same
  command and no environment changes; the failed run still counts. A repeated infrastructure
  failure stops the work unit.
- A command that hangs or never reaches a test result is a failed attempt. Do not manipulate Gradle
  daemons, caches, processes, or the operating system to force progress.

When the limit is reached, the status file must record all three hypotheses, edits, commands, and
results, followed by the smallest decision or information needed from the user.

## Verification ladder

Always use the narrowest sufficient level and then the cumulative gates named by the plan:

1. focused file inspection and `rg` searches;
2. deterministic JSON/resource inspection;
3. `:common:compileJava` for common production code;
4. `:forge:compileJava` for Forge production code;
5. `:forge:processResources` for generated and copied production data;
6. `:forge:compileGametestJava` for automated test source;
7. `:forge:runGameTestServer` for runtime behavior;
8. `:forge:build` for final packaging.

Do not run a broad gate repeatedly when a narrower gate can prove the current correction. Do not use
`clean` as a routine precursor. Do not use `--refresh-dependencies`, delete run directories, alter
Gradle user settings, or touch caches to address a code failure.

For a compile that is expected to remain red because later stages are not ported, current-stage
success means:

- no compiler errors remain in the current work unit;
- any remaining errors are classified in the status file against later stages; and
- the current edit did not increase unrelated failures.

The explicitly passing gates in the plan remain mandatory.

## Test discipline

- Existing behavior assertions are specifications unless they conflict with `player-view.md`, the
  code's documented invariants, or an explicitly accepted 1.21.1 semantic change.
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
exercise its invariant. Avoid adding a new testing framework when the existing GameTest system can
express the requirement.

## Decision rules

Unattended work may decide between implementation details when all of the following are true:

- player-visible behavior remains unchanged;
- the choice stays inside the current work unit and existing subsystem ownership;
- it uses a documented Minecraft or Forge facility;
- it adds no dependency or plugin;
- it does not require another loader to be repaired;
- it is reversible without data migration or broad redesign;
- one option is clearly smaller or more consistent with existing project principles.

When two approaches remain plausible, prefer in this order:

1. current vanilla or Forge facility;
2. existing Some Buckets abstraction seam;
3. smallest explicit context plumbing;
4. contained duplication over a new cross-cutting abstraction;
5. a documented stop for user choice.

Compiler errors establish that an API use is wrong; they do not by themselves establish the correct
behavioral replacement.

## Immediate stop conditions

Stop without using the remaining attempt budget when progress would require any of the following:

- changing Minecraft, Forge, Gradle, Loom, Architectury, plugin, mapping, or JDK versions;
- adding or replacing a dependency, repository, plugin, loader, or test framework;
- changing the OS, IDE, global Java setup, environment variables, network configuration, Gradle
  user home, daemon state, or caches;
- decompiling, unarchiving, inspecting bytecode, or reading prohibited cached/remapped Minecraft or
  Forge sources;
- a Git or GitHub operation;
- destructive deletion or broad movement outside an explicitly named resource-directory migration;
- changing documented player behavior, capacities, gesture priorities, protection rules, fuel
  values, transfer settlement, or persistence ownership;
- deciding to support legacy 1.20.1 data after all;
- repairing Fabric/Quilt or implementing NeoForge to make a Forge gate pass;
- deleting a feature or test because its current API is inconvenient;
- introducing a global registry singleton or loader API into common production code without an
  established project seam;
- a conflict with unrelated user changes that cannot be preserved;
- contradictory evidence about the current API that cannot be resolved from permitted documentation
  and compiler diagnostics;
- three failed verification attempts for the current work unit.

At an immediate stop, make no speculative workaround.

## Runtime command handling

- Use plain console output so failures are recordable.
- Monitor running Gradle commands at least once per minute when the tool yields control.
- A normal compile or build that has produced no terminal result after 10 minutes should be treated
  as an environmental blocker.
- A GameTest server that has not exited or produced a decisive suite result after 20 minutes should
  be treated as blocked.
- Do not kill unrelated processes, stop global daemons, or clear caches. Record any still-running
  command/session in the status file and stop further work.
- Do not launch an interactive client as part of unattended completion.

## Scope fences

### In scope

- `common/src/main` as required by Forge;
- `forge/src/main`;
- `common/src/gametest` and `forge/src/gametest`;
- shared resources used by Forge;
- Forge metadata;
- narrowly necessary existing Gradle test/run configuration;
- root port documents and final orientation updates.

### Out of scope

- Fabric/Quilt production or test repairs;
- NeoForge runtime implementation;
- Forge FTB Chunks support;
- release publishing or Git history;
- unrelated refactoring, formatting, or documentation cleanup;
- performance redesign without a demonstrated port regression;
- manual client interaction.

## Token and turn boundaries

Token exhaustion is a handoff boundary, not a reason to rush or enlarge a work unit.

Before ending an unfinished turn:

1. finish the current safe atomic edit if possible;
2. do not start a verification command that cannot be monitored;
3. update the status file completely;
4. name the exact next file or command;
5. report whether the current files are expected to compile;
6. leave the attempt count unchanged and explicit.

The next turn resumes from that exact action. It does not repeat a passing gate unless later edits
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
- whether the workspace is compile-ready, intentionally incomplete, or has a running command.

The same information must already be present in `forge-1.21.1-port-status.md` so the next session does
not depend on the chat transcript.

## Completion handoff

When every gate passes:

- mark the status `complete`;
- record every final passing command;
- summarize behavior-affecting decisions;
- list deferred Fabric/Quilt and NeoForge work;
- list recommended manual client checks;
- do not commit, publish, or modify Git.
