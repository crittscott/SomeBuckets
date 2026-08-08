# Targeted code review: Javadocs

## Scope and standard

The production code does not have a complete set of Javadocs. Coverage is strongest around the newer world-fluid and client-rendering code and weakest around the protection API, NBT state API, fluid-logic entry points, and item behavior classes.

"Complete" should not mean adding a comment to every public Forge override. Straightforward overrides already inherit the framework contract, and registry constants are generally self-explanatory. The useful target is:

- a concise class contract for each nontrivial public type;
- complete contracts for public/protected APIs owned by this mod, especially mutation and extension points;
- documentation of nullable values, client/server behavior, protection responsibility, mutation timing, and return meaning where they are not evident from the signature;
- ordinary comments, not Javadocs, for private algorithm narration and local implementation constraints.

No build or Javadoc tool was run, in accordance with `CLAUDE.md`.

## Findings

### 1. High priority: the claim-protection extension API is entirely undocumented

The mod's most important extension seam has no Javadocs:

- `ClaimProtectionProvider` and its `mayAct` method (`ClaimProtectionProvider.java:11-14`);
- `ClaimProtections.register` and `mayAct` (`ClaimProtections.java:26-37`);
- the `ClaimProtections.Registration` lifetime token (`ClaimProtections.java:40-44`);
- `ProtectionContext` and all four factories (`ProtectionContext.java:10-33`);
- `ProtectionAction` (`ProtectionAction.java:3-8`).

A provider author cannot learn from the API which values may be null, whether `false` means denial, how multiple providers combine, what `face` means for an entity operation, when `targetEntity` is supplied, or how player, dispenser, and unowned-automation contexts differ. `register` does not state that closing the returned token unregisters the provider. The single-threaded registration/use assumption recorded in `as-built.md` is also part of the extension contract and has no code-level owner.

Add class and method Javadocs here first. Document each `ProtectionAction` semantically, the nullable record components, the all-providers-must-allow rule, registration lifetime/threading, and the exact meaning of the target position and face. This is the one area where full `@param`/`@return` documentation is warranted despite the verbosity.

`Protections.java` is comparatively well documented, but it describes the facade used by callers, not the provider contract implementers need.

### 2. High priority: the protected fluid-handler extension contract is undocumented

`AbstractFluidHandler` has no class Javadoc and none of its protected hooks is documented (`AbstractFluidHandler.java:8-82`):

- `fillEmpty`;
- `fillExisting`;
- `performDrain`;
- `canAcceptFluid`.

These methods are a real subclass API used by both `BBFluidHandler` and `SBFluidHandler`. Their contract must state which container modes have already been checked, whether `current` is guaranteed nonempty and fluid-equal, how `FluidAction.SIMULATE` must be honored, whether the container may be mutated, and what the returned amount/stack means. None of that can be inferred safely from the declarations alone.

The concrete overrides do not need repetitive Javadocs once the base contract is complete; normal comments can explain only the finite-versus-infinite policy differences. The public `IFluidHandlerItem` overrides can inherit Forge's documentation.

### 3. High priority: public mutation entry points do not specify their transactional contract

Most public methods in `BBFluidLogic` and `SBFluidLogic` are undocumented even though their boolean result and side effects carry substantial meaning:

- Big Bucket `tryTake*`, `tryPlace*`, and their `ProtectionContext` overloads (`BBFluidLogic.java:43-332`);
- Source Bucket `tryTake*`, `tryPlace*`, and `tryMilkDispenser` (`SBFluidLogic.java:47-330`);
- `FluidPlacement.emptyContents` (`FluidPlacement.java:56-60`);
- storage mutation methods such as `JBItem.absorbItemEntities`, `feedAnimal`, and protected `addStack` (`JBItem.java:223-321,408-417`).

These contracts should answer:

- Is `true` prediction on the client or proof of a server mutation?
- Does the method itself perform protection checks, stats, criteria, sounds, and game events?
- Does failure guarantee that both world and item are unchanged?
- Does `allowFaceOffset` permit only target selection or alter placement validity too?
- Is a null storage `ProtectionContext` an intentional request to skip authorization, and for which callers is that valid?
- For collection/feeding, does `true` mean any entity/food was consumed or merely that a candidate existed?

The existing read-only resolver Javadocs are detailed, but the methods that actually mutate state are mostly bare. That is the reverse of the risk profile: mutation entry points need the strongest contracts.

### 4. High priority: several `NBTUtil` Javadocs are broader than the code and therefore false

`NBTUtil` has many undocumented public methods, but the more urgent problem is that some existing documentation misstates scope:

- `isEmptyBucket` says it is true when "the bucket holds nothing" (`NBTUtil.java:63-65`), but it checks only `Mode.NONE`. Junk and Trash Buckets can contain `JunkItems` while remaining in `Mode.NONE`, so the documented claim is false for two bucket types handled by this same utility.
- `clearBucket` says it clears "all bucket content" (`NBTUtil.java:210-220`), but it does not remove `JunkItems`; it clears only mode-based fluid/milk/powder/entity fields.
- `drainFluid` claims to drain fluid (`NBTUtil.java:243-264`), but its non-fluid branch reduces the generic `Amount` field and is used for milk as well.
- the class says it centralizes NBT manipulation "for all bucket types" (`NBTUtil.java:15-17`) without explaining that storage-bucket state is outside the `Mode`/normalization/clear model.

Narrow these contracts to the actual schemas, or split the utility by bucket domain. Then document the non-obvious guarantees on the currently bare API: getters do not attach tags; setters set a mode and clamp negative counts; `getStoredItems` returns a detached mutable list; `setStoredItems` skips empty entries and removes an empty storage tag; entity queue operations are FIFO; and unrelated NBT is preserved.

Because callers use these methods to maintain persistent state, inaccurate documentation is worse than absent documentation here.

### 5. Medium priority: Mob/Trash Javadocs omit or misstate observable edge contracts

Several concise comments hide behavior that callers need to preserve:

- `MBItem.needsWater` says it identifies a mob that "suffocates out of water" (`MBItem.java:85-89`). The actual predicate is `Bucketable` or a `LivingEntity` with `MobType.WATER`; the Javadoc should describe that classification directly rather than infer a biological consequence.
- `MBItem.releaseOldest` says only that it recreates the oldest mob after authorization (`MBItem.java:132-160`). It should state that the stored entry is removed only after `addFreshEntity` succeeds, UUID collisions are repaired across loaded server levels, collision/protection failures preserve the entry, and water placed before a final spawn rejection is not rolled back.
- `MBItem.capture` (`MBItem.java:66-82`) should state that `true` means the snapshot was appended and the live mob discarded, while `false` leaves both unchanged.
- `TBItem.findFirstNearby` calls its result "nearest" (`TBItem.java:139-150`), but the implementation requests the first matching entity from the world query and performs no distance sort. Document it as the first eligible entity in query iteration order, explicitly not a nearest-entity guarantee.

These details are stable behavioral contracts and maintenance invariants, not implementation trivia.

### 6. Medium priority: the hand-transfer public Javadocs are incomplete and one records current implementation history

`Transfers` has a useful class-level contract, but its two public methods are weakly documented:

- `tryTransferOne` uses quoted parameter names and "IFF" and says only that state/sounds/stats were applied (`Transfers.java:44-51`). It does not say that one side must be a Big/Source Bucket, which hand is settled, how client prediction differs from server mutation, or that `false` means no transfer was accepted.
- `tryTransferEither` says it "mirrors the 'either direction' behavior some call sites use today" (`Transfers.java:69-78`). That is narration about the current codebase, not a contract. The stable fact is simply the ordered attempt: main-to-off first, then off-to-main only if the first attempt fails.

Rewrite both around inputs, ordered behavior, side effects, and return meaning. Use `{@code fromHand}`/`{@code fromStack}` rather than quotation marks and avoid temporal phrases such as "use today."

### 7. Medium priority: class-level coverage is inconsistent on the core domain types

The five principal behavior classes have no class Javadocs: `BBItem`, `SBItem`, `JBItem`, `TBItem`, and `MBItem`. Neither do `BBFluidLogic`, `SBFluidLogic`, `AbstractFluidHandler`, `SBPolicy`, `ClaimProtections`, or `ProtectionContext`.

These classes do not need player-guide essays. Each needs a short statement of role and its central invariant, for example finite single-content storage, infinite policy-gated assignment, FIFO stack storage, replace-on-mismatch Trash semantics, or same-species FIFO entity snapshots. A reader should be able to tell why both an item class and a logic/handler class exist and which layer owns state, world mutation, and capability behavior.

By contrast, simple event subscribers, registry holders, and straightforward Forge overrides do not need boilerplate Javadocs merely because they are public.

### 8. Medium priority: private implementation explanations are frequently formatted as API Javadocs

The client-rendering code contains useful explanations, but many are attached as Javadocs to private implementation details and narrate algorithms rather than contracts. Examples include:

- mask scanning, row merging, and coordinate conversion in `BucketMouth.java:16-27`;
- nested-render translation mechanics in `JBRenderer.java:100-104`;
- delegate wrapping, render-pass traversal, cache keys, and quad-overload behavior throughout `NbtFluidContainerModel.java:91-242`.

Keep the information, but separate it by purpose. Public/package type Javadocs should describe the abstraction and guarantees: what a mouth span represents, what gets recolored, what remains untouched, and cache/lifecycle constraints callers can rely on. Step-by-step renderer mechanics belong in ordinary block or line comments beside the relevant implementation.

The same cleanup applies to hypothetical phrasing such as "would silently bypass" (`JBModel.java:21-24`) or "would otherwise cut holes" (`NbtFluidContainerModel.java:237-241`). State the required invariant directly (for example, "Return this wrapper so custom rendering remains selected") and use an implementation comment when the reason is local to the algorithm.

## Existing Javadocs worth retaining

- `FluidPickup` has a strong class contract and clear `available`/`take` separation, including mutation and client/server semantics.
- `FluidPlacement` clearly documents target resolution, fall-through, protection ownership, and the caller's responsibility to charge the bucket.
- `Protections.onBucketUse` explains the non-obvious `FillBucketEvent.ALLOW` treatment as a stable compatibility contract.
- `JBItem.canStore`, `isIntakeCandidate`, and `removeOldest` are concise and contract-focused.
- The read-only target-resolver docs in `BBFluidLogic`/`SBFluidLogic` correctly state that they do not check protection or mutate the world; they only need trimming where they narrate their current caller.

## Recommended documentation pass

1. Document the protection SPI and `ProtectionContext` completely.
2. Document `AbstractFluidHandler`'s protected hook contract.
3. Add contracts to public mutation entry points in fluid logic, storage behavior, and transfers.
4. Correct the overbroad `NBTUtil`, Mob Bucket, and Trash lookup statements.
5. Add concise class contracts to core domain types.
6. Convert private algorithm Javadocs to ordinary implementation comments and remove historical/hypothetical phrasing.

This should be a selective pass, not a documentation blanket: inherited framework behavior and obvious registry declarations should remain uncluttered.
