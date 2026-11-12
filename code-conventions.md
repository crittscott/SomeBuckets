# Code conventions

Apply these conventions when changing code. Read `code-review-decisions.md` for specific decisions and `platform-notes.md` for facts about the target loader.

## Adding a variant

- Read the existing variants first. Find the rule's owner and its consumers, including previews, predictions, inverse actions, loader adapters, and diagnostics.
- Put shared decisions in one place. Repeated loader wiring is fine when it makes no independent decision. Prefer an existing coherent class when sharing a rule.
- Let one declaration drive related lists, defaults, limits, and derived values. Give unrelated values their own names even when they happen to be equal.

## Hooking the game

- Prefer an existing method on the mod's own class, then a loader event or API, then a Mixin. A Mixin's Javadoc explains why it is needed.
- Hook the stage where the result is known. When substituting for a loader event, cover the same triggers and cancellation paths.
- Preserve vanilla permission checks, events, stats, criteria, and interaction results when taking over a vanilla path. Use the actor, hand, and target supplied by the callback.
- Keep shared decisions in common. Loader adapters translate between shared operations and loader events, storage, or data types.
- Follow the loader's result contract. Use `PASS` when no action applies, `FAIL` for deliberate refusal, and the appropriate success result for a completed action or its matching client prediction.

## Guards and failures

- Identify the input and the boundary before adding a check. Check each fact where every relevant path passes through it, using `@Nullable` only where null actually flows.
- Trust game and loader contracts unless there is evidence of an unreliable interface. Validate external data and network input. Catch failures where they can be handled, using the narrow exception or failure result the API provides.
- Remove dead branches, unused parameters, and obsolete paths. Keep required platform bridges and small adapters. Shared build machinery may cover more than one supported Minecraft version.

## Values, text, and logs

- Name tunable, shared, and nonobvious values. Use stable identifiers for serialized choices and imports for Java type references.
- Keep player-visible text in lang files as complete messages. Use the format's serializer for structured data.
- Log startup milestones and effective settings when they load or change. Name the input or object on failure and describe the resulting state. Keep repeated draw and tick paths quiet.

## Saved and runtime state

- Reading a bucket must leave it unchanged. Route persisted changes through `BucketState`, preserve unrelated NBT, and canonicalize empty contents.
- Validate the full proposed change before mutation. Authorize the exact action and target, then preserve state and item costs on refusal. Keep rollback scoped to the operation's unit of work.
- Give retained state an owner and a lifetime. Handle the applicable load, reload, replacement, disconnect, and shutdown transitions. Update game-owned state on its owning thread.
- In hot paths, reject inapplicable work before allocation or decoding. Refresh derived state on change. Keep cache complexity in proportion to the work it saves.

## Networking

- Validate packet structure and intrinsic bounds on decode. Check permissions and conditions that depend on current game state at execution.
- Identify the authoritative endpoint, recipients, and the point at which initial state can be applied. Send updates on change and reset client state when its connection ends.
- Change the readers, writers, and affected tests together when changing a wire format. Preserve stable IDs and account for loader framing when setting packet size limits.

## Tests and documentation

- Drive the real trigger on each loader where the test can reach it. Derive the expected result independently. For permission checks, exercise refusal and an allowed control, and check state and item costs on refusal.
- Set up the preconditions and show the trigger occurred. Restore changed settings, game rules, and temporary listeners on success and failure.
- Keep test helpers in test sources. A GameTest Javadoc gives the setup, action, and expected in-game observation. For automation-only checks, explain what the test guards.
- Document current behavior. Explain the practical reason for surprising wiring where it appears. Update the affected orientation and build snapshot when their inputs change.

## Organization and member order

- Group related functionality. Keep client-only code in client packages and loader-specific adapters in their loader modules. Use matching names for corresponding files where practical.
- Group Java members by kind: constants, other static fields, instance fields, initialization blocks, constructors, nested enums, nested records, other nested types, and methods. Sort names within each group where initialization dependencies and deliberate field groupings permit. Order constructor and method overloads by arity, then parameter types.
- Keep field initialization in dependency order. Explain a surprising dependency where it forces an exception. Order registration calls by their functional dependencies and related concerns.
