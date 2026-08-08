# Targeted code review: organization

## Scope

This review looks only for functionality that has become scattered across multiple owners and could be consolidated. It is based on the current production sources under `src/main/java`; no build or test run was performed, in accordance with `CLAUDE.md`.

## Findings

### 1. High priority: cauldron behavior has three independent authorities

Big Bucket player interactions live in `interaction/Cauldrons.java` (`register` and the four `on*Cauldron` methods, lines 31-197). Source Bucket cauldron behavior is embedded in `fluid/SBFluidLogic.java` in both the take path (lines 83-124) and placement path (lines 245-305). Dispenser cauldron behavior is implemented a third time in `interaction/Dispensers.java`, separately for Source Buckets (lines 52-92) and Big Buckets (lines 108-164).

These sites all own some combination of the same responsibilities:

- recognizing full and empty cauldrons;
- deciding whether the held content is compatible;
- checking protection;
- changing the cauldron and bucket state;
- choosing sounds;
- emitting `FLUID_PICKUP` or `FLUID_PLACE`;
- awarding player stats and criteria.

The player and dispenser *selection rules* are intentionally different, and Source Buckets are infinite, so they should not be collapsed into one universal interaction method. The physical cauldron transitions nevertheless need one owner. A `CauldronTransfers` service could expose explicit operations such as take-full-water, take-lava, take-powder, place-water, and place-lava. Thin player, Source Bucket, and dispenser adapters would retain their own ordering and eligibility policy while delegating protection, mutation, sound, game event, and optional player advancement/stat bookkeeping to those operations.

This is the most consequential organizational issue because every cauldron-related maintenance change currently requires finding and reasoning about all three implementations. The maintenance note that player and dispenser cauldron logic must both be inspected is evidence of this structural cost.

### 2. High priority: block fluid-capability transactions are duplicated between Big and Source logic

`BBFluidLogic` and `SBFluidLogic` each define their own `compatibleBlockCapability` helper (`BBFluidLogic.java:56-64`, `SBFluidLogic.java:60-67`) and nearly identical tank-drain/tank-fill transactions (`BBFluidLogic.java:176-220`, `SBFluidLogic.java:183-225`). Each implementation independently performs capability lookup, simulation, the all-or-nothing 1,000 mB check, protection, execution, stat award, and feedback.

The finite/infinite difference is already represented by `BBFluidHandler` versus `SBFluidHandler`; it does not require duplicating the surrounding transaction. A shared `BlockFluidTransfers` primitive should perform the one-unit transaction through the supplied item handler. Executing a drain against `SBFluidHandler` is already non-consuming, so the handler remains the natural owner of source-bucket infinity. `BBFluidLogic` and `SBFluidLogic` would keep their mode/policy dispatch and call the shared primitive.

The duplicated `fillSound`/`emptySound` methods in both classes (`BBFluidLogic.java:223-233`, `SBFluidLogic.java:227-237`) should move with that primitive or to a small `FluidSounds` helper. The same fallback rule also appears in `FluidPickup` and `FluidPlacement`, so one resolver would give registered fluid sounds a single owner without combining the otherwise well-separated pickup and placement transactions.

### 3. Medium priority: `Dispensers` is three unrelated behaviors behind a type cascade

One `Dispensers` instance is registered for Big, Source, and Mob Buckets in `SomeBuckets.java:71-79`. Its `execute` method then dispatches by item type: Mob Bucket at `Dispensers.java:47-50`, Source Bucket at lines 52-93, and an implicit "everything else is a Big Bucket" branch at lines 95-174. Mob-specific capture/release selection is another separate method at lines 177-204.

This makes the class name and type misleading: it is not shared dispenser infrastructure but a container for three feature implementations. It also means the Big Bucket branch depends on registration discipline for the unchecked cast at line 98. That cast is valid under the current registrations, but the dependency is not expressed by the type.

Follow the existing `StorageBucketDispenser` pattern and create `BigBucketDispenser`, `SourceBucketDispenser`, and `MobBucketDispenser`. A small shared value/helper can construct the front position, adjacent hit face, hit result, and `ProtectionContext` once. Each registered behavior would then have one reason to change, while shared world-fluid and cauldron primitives would still prevent duplication.

### 4. Medium priority: `NBTUtil` combines three storage schemas with domain policy

`NBTUtil` is not one cohesive utility. It owns:

- finite/source fluid, milk, and powder state (`NBTUtil.java:20-118`);
- Mob Bucket entity type and FIFO snapshot storage (`NBTUtil.java:120-173`);
- Junk/Trash item-stack storage (`NBTUtil.java:175-206`);
- finite-bucket crafting behavior (`NBTUtil.java:223-264`);
- normalization for several unrelated bucket families (`NBTUtil.java:266-304`).

It also crosses the persistence/policy boundary. For example, `canAcceptEntity` embeds the Mob Bucket capacity and species rule at lines 168-172, while `getCraftingRemainder` owns item behavior rather than merely NBT encoding. Conversely, Junk/Trash state does not participate in `Mode` or `clearBucket`, despite residing in the same class.

Split this into domain-specific state owners, for example `FluidBucketContents`, `MobBucketContents`, and `StorageBucketContents`. Keep serialization, normalization, and the invariants for each schema together; keep item behavior such as crafting remainder in the item or a finite-bucket behavior component. A tiny private/shared tag-cleanup function is enough common infrastructure. This would make it much easier to determine which invariants apply when changing one bucket family.

### 5. Medium priority: the common Big/Source item-use prelude is maintained twice

`BBItem.use` and `SBItem.use` independently implement the same two preliminary gestures: sneak-use on air clears content (`BBItem.java:173-187`, `SBItem.java:54-66`), and ordinary use on air attempts the other-hand transfer (`BBItem.java:189-199`, `SBItem.java:68-78`). Both classes also contain the same hit-result retargeting helper (`BBItem.java:310-314`, `SBItem.java:118-122`). Milking, drinking, dynamic naming, and capability exposure form another shared family of behavior, though their finite/source policies differ.

Consolidate the identical input-routing prelude and hit retargeting first, preferably through a composition helper rather than a large inheritance hierarchy. If milk behavior is consolidated later, make the finite/infinite consumption and `SBPolicy` checks explicit strategy inputs; those real semantic differences should remain visible. The goal is one owner for gesture ordering, not a generic base class full of boolean switches.

### 6. Low priority: client registration and presentation ownership are split across common and client packages

Item-property registration is performed by the common mod entry point in `SomeBuckets.clientSetup` (`SomeBuckets.java:86-102`), while tint and resource-reload registration use `ClientColorHandlers`, model-loader registration uses `ClientModelLoaders`, and item classes reach into the client package for the Junk renderer and fluid bar color (`JBItem.java:3,50-56`; `BBItem.java:3,136-155`). `FluidColorHelper` is a deliberate sided bridge, so this is not a claim that the current code is unsafe; the issue is that there is no single place to discover the mod's client lifecycle wiring.

Move item-property setup into a client-only subscriber/bootstrap beside the color and model registrations. Keep Forge's `initializeClient` item hook where required, but let it delegate to a clearly named client extension factory. If `FluidColorHelper` remains callable from common item code, place or name it as an explicit sided bridge rather than as though it were ordinary client implementation. This gives client setup a coherent boundary and keeps `SomeBuckets` focused on common registration.

### 7. Low priority: the protection facade is outside the protection package

The public facade used throughout the mod is `util/Protections.java`, while its context, actions, provider registry, fake player, and provider interface are all under `protection`. Eleven production classes therefore import a protection concern from `util` while also importing protection types from `protection`.

Move `Protections` into the `protection` package (and consider a more specific name such as `ProtectionChecks`). This is a mechanical consolidation, but it makes the package boundary truthful: callers should not need to know that the main protection entry point is categorized as a generic utility.

## Existing organization worth retaining

- `FluidPickup` and `FluidPlacement` are good examples of consolidation at the correct level: they own world transactions while callers retain bucket-specific policy and accounting.
- `AbstractFluidHandler` correctly centralizes the common Forge capability contract while leaving finite and infinite behavior in separate handlers.
- `SBPolicy` gives allowlist interpretation one owner.
- `JBItem`/`TBItem` share behavior through a meaningful subtype relationship, and `StorageBucketDispenser` delegates storage and feeding operations back to that item behavior rather than reimplementing them.

## Suggested order

1. Extract shared cauldron transition primitives and add equivalence coverage for player/dispenser and finite/infinite callers.
2. Extract block-capability transfer and fluid-sound primitives.
3. Split `Dispensers` by bucket family.
4. Split `NBTUtil` by persisted domain.
5. Consolidate the Big/Source input prelude.
6. Tidy the client and protection package boundaries.

The first two changes reduce the largest drift risk. The remaining changes can then be mostly structural because their core behavior will already have narrower owners.
