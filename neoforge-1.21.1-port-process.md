# NeoForge 1.21.1 Unattended Port Process

## Purpose

This process governs autonomous execution of `neoforge-1.21.1-port-plan.md`. It permits sustained
code, test, compile, runtime-test, and fix work while preventing endless iteration, unjustified
redesign, environment rewrites, and loss of state across turns or context compaction. It also keeps
each session's context bounded to a single stage so execution does not become dominated by
re-reading accumulated history.

`CLAUDE.md` remains controlling. This process grants no permission to ignore its source-inspection,
Git, cache, environment, or project-scope restrictions. In particular, NeoForge's own published API
documentation and sources may be consulted when the local project and compiler diagnostics are
insufficient; decompiled or remapped Minecraft and Forge sources may not.

## This is construction, not migration

The Forge and Fabric 1.21.1 ports repaired existing 1.20.1 loader code by classifying compiler
errors. The `neoforge` module has build and metadata scaffolding only and no loader Java. There is
no error list to classify. `forge/src/main` is the structural template, `fabric/src/main` and the
`fabric-1.21.1-port-*.md` documents are the precedent for the 1.21.1 boundary conversions, and the
Stage 0 per-file **disposition inventory** in `neoforge-1.21.1-port-status.md` plays the role the
error classification played in the Fabric port: it assigns each target file to a stage.

Until Stage 5 a diagnostic `:neoforge:compileJava` is expected to fail because it references classes
in files not yet written. That is not an error to repair. A pre-Stage-5 stage is done when its own
files exist and every remaining compiler error names a symbol owned by a later stage, with no
regression in an earlier stage's files or in `common`, Forge, or Fabric.

## Authorized work

Within the current NeoForge port, unattended execution may:

- create and edit NeoForge production source under `neoforge/src/main`;
- create and edit NeoForge GameTest source under `neoforge/src/gametest`;
- create and edit `neoforge/src/main/resources` and `neoforge/src/gametest/resources`, including
  `neoforge.mods.toml` variants, `pack.mcmeta`, mixin configuration if a stage proves one is
  needed, and item model JSON;
- edit `common/src/main` and `common/src/gametest` **only** where NeoForge production or test code
  genuinely requires it, each such change followed by the dual regression guard below;
- make the specific `neoforge/build.gradle` construction the plan names: a `gametest` source set,
  the Loom `mods.somebuckets_gametest` entry, the Loom `runs.gameTestServer` run, the
  `rootProject.configureGameTestStructures(project)` call, a `generateBucketLootModifiers` NeoForge
  loot-modifier generator with its `processResources` and resource-srcDir wiring, and any
  `processResources` `replaceProperties` additions those require;
- run the Gradle compile, resource, GameTest, and build commands named in the plan, including the
  `:forge:compileJava` and `:fabric:compileJava` regression guards;
- use read-only project searches and inspect project-generated text logs and reports;
- consult NeoForge's published API documentation and sources, and the live `forge/` and `fabric/`
  modules, when the local project and compiler diagnostics are insufficient.

This authorization does **not** include Git operations, publishing, network service changes, IDE
automation, operating-system changes, cache manipulation, dependency or tool-version changes,
`neoforge_compile_version` changes, any `neoforge/build.gradle` change beyond the construction
listed above, or any change to `common/`, `fabric/`, or `forge/` build scripts.

## Do not regress Forge or Fabric

Both the Forge 1.21.1 and Fabric 1.21.1 ports are complete. Any change under `common/src/main` or
`common/src/gametest` must be followed, before the current stage closes, by:

- `:forge:compileJava --console=plain`, and
- `:fabric:compileJava --console=plain`, and
- `:fabric:compileGametestJava --console=plain` if shared GameTest code changed.

A Forge or Fabric regression is a failed verification for the work unit that caused it. If a NeoForge
requirement and a Forge or Fabric invariant genuinely conflict in common code, stop and report the
conflict; do not weaken any loader unilaterally. The strong expectation, matching the Fabric port's
zero-`common`-change outcome, is that no `common` change is needed at all.

## Persistent execution state

Execution state lives in two files with opposite disciplines:

- `neoforge-1.21.1-port-status.md` is a small bounded snapshot of the current position. It is
  **overwritten in place** whenever it changes and is never appended to. It is the only state file
  read during the work loop.
- `neoforge-1.21.1-port-log.md` is an **append-only** history. It is written to but never read back
  during execution; it exists as the audit trail for a human reviewer.

The snapshot stays short. It contains only:

- current stage and work unit;
- work-unit state: not started, in progress, passed, blocked, or complete;
- verification attempt count for the current work unit;
- whether the stable controlling documents have been read this session;
- the current work-unit definition: scope, intended files, verification command, completion
  condition;
- the Stage 0 per-file disposition inventory (target path, disposition, key API facts), maintained
  as files are completed;
- last command and one-line result;
- remaining dispositions assigned to later stages;
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
for the rest of the session, and it does not read the log. `neoforge-1.21.1-port-assessment.md` and
the `fabric-1.21.1-port-*.md` set are reference material: consult the relevant section when a stage
needs it, not wholesale. Continue from the recorded next action rather than restarting the port or
repeating completed verification.

## Definition of a bounded work unit

A work unit is one coherent behavioral group, implemented and checked together. It is not the
smallest possible edit. A stage's worth of parallel construction — the registration classes, the
item shells, a family of renamed-event subscriptions — is a single work unit with a single
verification at the end, not one unit per file. Reserve finer granularity for units that carry real
risk: the item-capability attach, the `StoredFluid` to `FluidStack` conversion, the handler
simulate/execute parity, the custom ingredients, the client geometry model, the GLM data
generator, and any change to a documented invariant or to common code.

Within a stage, orient from the disposition inventory, write everything the inventory and the
reference modules already make visible, and run one verification at the end. Intermediate file
inspection and `rg` searches over `forge/`, `fabric/`, and `common/` are cheap and unrestricted; do
not spend a verbose compile to confirm a partial set of files.

Examples of a single work unit:

- all Stage 2 bootstrap, registration, config, fake-player, and optional-mod wiring;
- the item-capability attach plus `requireBucketHandler`;
- the `NeoForgeFluidStacks` conversion and its application across every `FluidStack` site;
- both custom ingredients and their `IngredientType` registration together;
- the GLM modifier class, its registration, and the `neoforge/build.gradle` generator together;
- the geometry loader and the fluid container model together;
- one demonstrated shared cause of several failing GameTests.

A work unit must name its files, intended behavior, verification command, and completion condition
in the snapshot before writing begins.

Do not expand a work unit because a compile exposes a symbol owned by a later stage. That is
expected; record it against its stage.

## Standard work loop

For each work unit:

1. **Orient.** Read the snapshot. Read the stable controlling documents only if this session has not
   yet loaded them. Read the reference files in `forge/`, `fabric/`, and `common/` that the work
   unit mirrors.
2. **Bound.** Record scope, invariants, intended files, and the narrowest useful verification in the
   snapshot.
3. **Diagnose.** Use the disposition inventory and the reference modules. Run a diagnostic command
   only when a specific NeoForge API fact cannot be settled from the reference code plus NeoForge's
   published docs.
4. **Implement.** Write the full coherent set of files for the work unit using current NeoForge
   conventions and the existing project seams, keeping player behavior and documented invariants
   fixed.
5. **Inspect.** Re-read the new files and search for stale Forge API use copied by mistake
   (`ForgeRegistries`, `LazyOptional`, `AttachCapabilitiesEvent`, `net.minecraftforge.*`,
   `FMLJavaModLoadingContext`). Do not use Git to review changes.
6. **Verify.** Run the narrowest plan gate that can evaluate the work, once. Add `:forge:compileJava`
   and `:fabric:compileJava` if the work unit changed common code.
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

The first failed post-implementation verification is attempt 1. After attempt 3 fails, stop. Do not
make a fourth correction, change the gate, enlarge the work unit, weaken a test, or reset the
counter under a new failure label.

Rules for counting:

- A pre-implementation diagnostic command is not an attempt because no correction has yet been
  claimed.
- Every failed verification after an edit counts, including a different compiler or test failure
  exposed by the preceding fix within the same work unit, and including a Forge or Fabric
  regression.
- An error that only names a symbol owned by a later, not-yet-written stage does **not** count as a
  failure for a pre-Stage-5 diagnostic gate; such a compile is a success for that stage if its own
  files compile and nothing earlier regressed.
- A successful verification closes the work unit. The counter resets only when the next separately
  bounded work unit begins.
- An infrastructure or dependency-resolution failure may be retried once with the exact same command
  and no environment changes; the failed run still counts. A repeated infrastructure failure stops
  the work unit.
- A command that hangs or never reaches a result is a failed attempt. Do not manipulate Gradle
  daemons, caches, processes, or the operating system to force progress.

When the limit is reached, the log must record all three hypotheses, edits, commands, and results,
and the snapshot must record the blocked state and the smallest decision or information needed from
the user.

## Verification ladder

Always use the narrowest sufficient level and then the cumulative gates named by the plan:

1. focused file inspection and `rg` searches over `neoforge/`, `forge/`, `fabric/`, `common/`;
2. deterministic JSON/resource inspection;
3. `:common:compileJava` for common production code;
4. `:forge:compileJava` **and** `:fabric:compileJava` as regression guards after any common change;
5. `:neoforge:compileJava` for NeoForge production code;
6. `:neoforge:processResources` for generated and copied production data;
7. `:neoforge:compileGametestJava` for automated test source;
8. `:neoforge:runGameTestServer` for runtime behavior;
9. `:neoforge:build` for final packaging.

Do not run a broad gate repeatedly when a narrower gate can prove the current correction. Do not use
`clean` as a routine precursor. Do not use `--refresh-dependencies`, delete run directories, alter
Gradle user settings, or touch caches to address a code failure.

Verification output is transient. Read it in the turn it is produced, reduce it to an exit status,
an error count, and the delta from the previous run, and record only that. Do not quote the error
list back in a later turn or copy it into the snapshot or log; re-run the command if the detail is
needed again.

For a compile that is expected to remain red because later stages are not written, current-stage
success means:

- no compiler errors remain in the current stage's own files;
- every remaining error names a symbol owned by a later stage and is classified in the snapshot; and
- the current work did not increase unrelated failures or regress `common`, Forge, or Fabric.

The explicitly passing gates in the plan remain mandatory.

## Test discipline

- The Forge and Fabric `*GameTests` classes and the shared `**Scenarios` bodies are the coverage
  specification. Assertions are specifications unless they conflict with `player-view.md`, the
  code's documented invariants, or an explicitly accepted 1.21.1 or NeoForge semantic change.
- Never delete, disable, ignore, or weaken a test merely to make a gate pass.
- Never convert an assertion into logging or catch an exception that should fail the test.
- Port assertions faithfully; adjust only for renamed APIs, new context parameters, current fixture
  formats, and intentional behavior changes.
- When several tests fail, group them into one work unit only when evidence identifies one shared
  production cause.
- A GameTest run with no discovered tests is a failure.
- A dedicated-server classloading failure is a production failure, even if client compilation
  succeeds.

New NeoForge-specific tests should exist where a NeoForge boundary has no shared coverage — in
particular the item-capability attach, the `StoredFluid` to `FluidStack` component round trip, and
block-capability dispatch ownership — mirroring the Forge `Forge*` / `ForgeOnly*` classes. Avoid
adding a new testing framework when the existing GameTest system can express the requirement.

## Decision rules

Unattended work may decide between implementation details when all of the following are true:

- player-visible behavior remains unchanged;
- the choice stays inside the current work unit and existing subsystem ownership;
- it uses a documented Minecraft or NeoForge facility;
- it adds no dependency or plugin;
- it does not require Forge or Fabric to be changed, or if it changes common it keeps both
  compiling;
- it is reversible without data migration or broad redesign;
- one option is clearly smaller or more consistent with the `forge`/`fabric` precedent and existing
  project principles.

When two approaches remain plausible, prefer in this order:

1. current vanilla or NeoForge API facility;
2. the shape the `forge` or `fabric` module already uses for the same concern on 1.21.1;
3. existing Some Buckets abstraction seam;
4. smallest explicit context plumbing;
5. contained duplication over a new cross-cutting abstraction;
6. a documented stop for user choice.

Compiler errors establish that an API use is wrong; they do not by themselves establish the correct
behavioral replacement.

## Immediate stop conditions

Stop without using the remaining attempt budget when progress would require any of the following:

- changing Minecraft, NeoForge, NeoForge loader, Gradle, Loom, Architectury, Shadow, plugin,
  mapping, or JDK versions, or `neoforge_compile_version`;
- any `neoforge/build.gradle` change beyond the authorized gametest and loot-modifier-generator
  construction, or any change to `common/`, `fabric/`, or `forge/` build scripts;
- adding or replacing a dependency, repository, plugin, loader, or test framework;
- changing the OS, IDE, global Java setup, environment variables, network configuration, Gradle user
  home, daemon state, or caches;
- decompiling, unarchiving, inspecting bytecode, or reading prohibited cached/remapped Minecraft or
  Forge sources;
- a Git or GitHub operation;
- destructive deletion or broad movement outside an explicitly named resource-directory action;
- changing documented player behavior, capacities, gesture priorities, protection rules, fuel
  values, transfer settlement, or persistence ownership;
- redesigning `StoredFluid`'s common shape, or any other persistence-ownership change, without
  explicit user confirmation;
- the NeoForge capability model genuinely cannot express a documented BB/SB fluid behavior;
- a needed `common` change cannot keep both Forge and Fabric compiling;
- deciding to support legacy 1.20.1 data after all;
- weakening the Forge or Fabric build to make a NeoForge gate pass;
- deleting a feature or test because its current API is inconvenient;
- introducing a loader API or global registry singleton into common production code without an
  established project seam;
- a conflict with unrelated user changes that cannot be preserved;
- contradictory evidence about the current NeoForge API that cannot be resolved from NeoForge's
  published documentation, the `forge`/`fabric` reference code, and compiler diagnostics;
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

- `neoforge/src/main` (new);
- `neoforge/src/gametest` (new);
- `neoforge/src/main/resources` and `neoforge/src/gametest/resources` (new);
- the authorized `neoforge/build.gradle` gametest and loot-modifier-generator construction;
- `common/src/main` and `common/src/gametest` only as NeoForge genuinely requires, each with the
  dual Forge + Fabric regression compile;
- root port documents and final orientation updates.

### Out of scope

- Forge or Fabric production or test changes beyond preserving their compiles;
- any build-environment change other than the authorized `neoforge/build.gradle` construction;
- release publishing or Git history;
- unrelated refactoring, formatting, or documentation cleanup;
- performance redesign without a demonstrated regression;
- manual client interaction.

## Session and turn boundaries

Run exactly one stage per session, and stop at the stage boundary without exception. When the
current stage's primary gate is satisfied — the gate passes, or for a pre-Stage-5 stage the stage's
own files compile and every remaining compiler failure is classified against a later stage — stop
immediately and hand off to the user. In the same session, do not begin the next stage, read its
scope, bound its first work unit, or run any further verification, even when tokens and wall-clock
time remain. The next stage starts only in a new session that loads the snapshot and the stable
documents once. This keeps each session's context proportional to one stage rather than the whole
port; carrying a second stage into the session is the specific failure this rule exists to prevent.

Token exhaustion is a handoff boundary, not a reason to rush or enlarge a work unit.

Before ending an unfinished session:

1. finish the current safe atomic edit if possible;
2. do not start a verification command that cannot be monitored to completion;
3. overwrite the snapshot with the complete current position;
4. name the exact next file or command;
5. report whether the current files are expected to compile, and whether Forge and Fabric still
   compile;
6. leave the attempt count unchanged and explicit.

The next session resumes from that exact action. It does not repeat a passing gate unless later
edits could have invalidated it.

## Blocked handoff format

When stopping for the user, report:

- stage and bounded work unit;
- intended invariant;
- files changed or created;
- exact verification command;
- concise result of each failed attempt;
- current best diagnosis;
- smallest user decision, API information, or external-state change required;
- whether the workspace is compile-ready, intentionally incomplete, or has a running command, and
  the Forge and Fabric compile state.

The snapshot must already carry the current position, diagnosis, and required decision, and the log
must already carry the per-attempt results, so the next session does not depend on the chat
transcript.

## Completion handoff

When every gate passes:

- mark the snapshot `complete`;
- record every final passing command in the log;
- summarize behavior-affecting decisions in the snapshot;
- reconcile `as-built.md`, `player-view.md`, and `build-env.md` to the three-loader 1.21.1 state;
- list recommended manual client checks;
- do not commit, publish, or modify Git.
