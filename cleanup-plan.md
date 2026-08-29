# Some Buckets Post-Port Cleanup Plan

How the codebase gets put in order after the 1.20.1 → 1.21.1 port that also added NeoForge. Targets
are in `cleanup-register.md`; this is the method, the rules, and the order.

## Goal

Remove the jank the port introduced by freezing `common/` and copying each finished Forge file into
`neoforge/`. Specifically: share loader-neutral logic that is currently duplicated 2–3×, replace
1.20.1-era wrappers with the 1.21.1 / NeoForge-native way, and make shared code have no loader owner.

## Non-negotiable rules

- **Behavior is frozen.** `player-view.md` is the contract. A change that alters observable behavior
  stops and becomes a separate question.
- **`NBTUtil` / `custom_data` schema is untouchable.** No `Mode` payload change, no `MAX_STACK_SIZE`
  rewrite change, no persisted-byte change, no migration.
- **Forge stays co-equal.** Full support parity. NeoForge wins only ties about the shape of shared
  code.
- **NeoForge idiom is the reference for shared code:** component `FluidStack` semantics,
  `Registries.*` keys, `DeferredHolder`, `FMLEnvironment.dist == Dist.CLIENT`,
  `ModelResourceLocation.inventory()`, external client-extension registration.
- **No speculative abstraction.** No general `FluidStore` SPI, no adapter sprawl, unless a slice
  proves a specific file needs it. Some duplication is acceptable (project principle); 2,700
  mechanically copied lines is not.
- **Surgical commits.** One subsystem per commit. Every changed line traces to a register row. No
  drive-by cleanup.
- **I do not run builds.** After each slice I state it is ready for a build + `runGameTestServer` on
  Forge, NeoForge, and Fabric, and wait for the result before the next slice.

## The mechanism reality

A `.java` cannot be compiled into both `forge` and `neoforge` (divergent `net.minecraftforge.*` /
`net.neoforged.*` packages). `common/` cannot see either loader's symbols. `@ExpectPlatform` is
static-only. See `cleanup-register.md` → "The mechanism constraint" for the full reasoning. The
available tools:

| ID | Mechanism | Use for |
| --- | --- | --- |
| M1 | Extract vanilla-typed core to `common/src/main`; thin loader shell keeps the `extends`/registration part | Logic needing only `net.minecraft.*` + common types |
| M2 | `@ExpectPlatform` static hook in `common`, one impl per loader | Small loader-specific computation with a common call site |
| M3 | Accept the duplication | Loader-bound surface dominates; neutral core < ~15 lines |
| M4 | In-place idiom fix on Forge, no sharing | Worry-2 rows that are just "use the modern spelling" |
| M5 | Shared source set for identical-FQN-only files | Rare; usually belongs in `common` anyway |
| ~~M6~~ | ~~Build reconfig: NeoForge-base common~~ | **Ruled out — see Step 0** |

## Step 0 — M6 spike (done 2026-08-29, negative)

Researched to conclusion from Architectury's own documentation; no branch or build was needed.

- Architectury's cross-loader transform is **one-directional** (`TransformForgeLikeToNeoForge`).
  There is no mode where `common` compiles against NeoForge and is remapped to Forge.
- The only "large shared `common`" Architectury supports is **Forge-idiom** source auto-remapped
  *to* NeoForge. That contradicts the NeoForge-idiom decision.
- More decisively: the auto-remap renames classes but does not rewrite API-shape differences. Forge
  `FluidStack` (NBT-tag: `new FluidStack(fluid, amount, tag)`, `getTag()`, `isFluidEqual`) and
  NeoForge `FluidStack` (component-based, registry-dependent) cannot be bridged by a class rename.
  The repo's hand-written `ForgeFluidStacks` / `NeoForgeFluidStacks` converters exist precisely
  because of this. The entire fluid layer would stay duplicated either way.

**Conclusion: proceed with the incremental plan below. There is no Track B.**

---

## The plan — incremental (M1 / M2 / M4, no build-environment change)

Ordered low-risk → higher-risk. Each slice is one commit + a 3-loader build/GameTest checkpoint.

| # | Slice | Register rows | Mechanism | Risk |
| --- | --- | --- | --- | --- |
| ~~1~~ ✓ | Forge idiom fixes: `SidedFluidColors` `DistExecutor`→`FMLEnvironment` + `ClientHolder`; `ClientModelLoaders` `ModelResourceLocation.inventory()` | 2a, 2b | M4 | **Done — Forge build clean, 185/185 GameTests pass** |
| ~~2~~ ✓ | **Re-scoped to minimal.** New `common/fluid/WorldFluidPickup` (`sourceAt` + `take`, vanilla-typed, caller passes the loader-resolved fill sound); Fabric's inline `worldFluid`/`takeWorldFluid` delegate to it. Caveat 1w confirmed harmless: NeoForge already `BucketPickup`-only. | 1·`FluidPickup` (partial), Fabric asymmetry | M1 | **Done — 3-loader build clean** |
| ~~3~~ ✗ | **Dropped.** The premise (neutralize the spawn-egg lookup to vanilla `SpawnEggItem.byId`) would regress modded-entity Mob Bucket tints on Forge, where mods register via `DeferredSpawnEggItem` and `ForgeSpawnEggItem.fromEntityType` is needed to see them. NeoForge + Fabric already use `SpawnEggItem.byId`. The only safe shared surface is the ~4-line color formula — not worth a new class. `ClientColorHandlers` stays duplicated (M3). | 1·`ClientColorHandlers` | M3 | — |
| ~~4~~ ✓ | **Part A done:** Forge + NeoForge `FluidPickup` deleted; `BBFluidLogic` / `SBFluidLogic` / `*BucketOperations` world-pickup call sites on both loaders converted to `StoredFluid` via `WorldFluidPickup`; `completePlayerPickup` moved to `WorldFluidPickup`. Take-path code is now identical across Forge and NeoForge. One mechanism change: world-pickup fill sound goes from `Transfers.playBucketSound`+`notifyActor` to `WorldFluidPickup`'s `level.playSound(null, …)` — same as Fabric, same audible result (no client prediction on this path). **Part B (leaf-predicate extraction) split out → slice 4b.** | 1·`FluidPickup`, 2g, 1·`BBFluidLogic`, 1·`SBFluidLogic` | M1 | **Done — 3-loader build clean** |
| ~~4b~~ ✓ | **Done (BB only).** New `BBItem.canAcceptFluidUnit(ItemStack, StoredFluid)` in `common`; the finite-take admission predicate (mode + variant + capacity), previously inline ×2 in each of Forge/NeoForge `BBFluidLogic` and as Fabric's `acceptsFinite`, now calls it from all three. Killed a real drift risk — Fabric's `acceptsFinite` had different `&&`/`||` nesting (equivalent, but fragile). **SB not extracted:** Forge/NeoForge thread `FluidStack assigned` through `SBFluidLogic.tryTakeWithContext` for the capability path, so the shared world-source predicate surface is thin and awkward to factor without touching the capability logic — M3. | 1·`BBFluidLogic`, Fabric asymmetry | M1 | **Done — 3-loader build clean** |
| 5 | `Transfers` leaf extraction: settlement ordering / `isOurs` / milk branch already partly common — pull the remaining loader-neutral decision fragments across; capability round-trips stay per-loader | 1·`Transfers` | M1 | Med |
| 6 | Placement-class audit (`FluidPlacement` vs the 3 loader copies): share only genuinely vanilla-only logic; keep general placement loader-owned per the invariant | 2f | M1 audit | Med |
| ~~7~~ ⧗ | **Done.** New `neoforge/src/gametest/.../NeoForgeFluidStacksGameTests` — `custom_data_fluid_variant_survives_round_trip` asserts a registry-free `minecraft:custom_data` variant round-trips through `NeoForgeFluidStacks.of`/`variantTag` and the item-stack `set`/`get` path, and that a variantless fluid stays variantless. No production code change. | 2d | test | Low — **done, pending NeoForge build** |
| ~~8~~ ✓ | **Done (doc-only).** Read-through confirmed `beforeWorldBucketUse` is the sole Forge-shaped method in `BucketOperations`. Rewrote its javadoc + the interface class javadoc to mark it a Forge `FillBucketEvent` carve-out that NeoForge/Fabric no-op; added a matching comment to Fabric's bare `return null`. No behavior change. | 3b, 2e | doc | Low |
| ~~9~~ ✓ | **Done.** `as-built.md`: `forge`/`neoforge` reframed as parallel peers (no "reuses the forge module"); added `WorldFluidPickup` to the ownership table, the seams section, the `FluidPlacement` note, and a maintenance invariant; noted the new variant GameTest. `build-env.md` unchanged — no `build.gradle`, source-set, or dependency change in this cleanup. | 3c | doc | Low |

**Accepted as duplicate (M3), not scheduled:** `AbstractFluidHandler` + `BBFluidHandler` +
`SBFluidHandler`; `Cauldrons`; `Dispensers`; `JBModel`; `ClientFluidColors`; all `register/*`;
`config/ServerConfig`; `loot/AddBucketLootModifier`.

**Deferred (own slice, higher risk / low coverage):** `JBRenderer` vanilla-core extraction;
`StoredFluidContainerModel` mask/tint extraction. Not scheduled until rendering GameTest coverage
improves or the visual-regression risk is accepted.

---

## Out of scope

- Any `NBTUtil` / `custom_data` / data-component work.
- `crafting/*Ingredient` — Forge and NeoForge APIs differ enough that sharing buys nothing.
- `fluid/FluidProvider` (Forge) vs NeoForge capability registration — a real seam.
- `ForgeJBItem` / `ForgeMBItem` / `ForgeTBItem` — investigate once (loader-mandated shells most
  likely); otherwise M3.
- FTB Chunks / `common/src/compat` wiring — already correct.

## Definition of done

- `FluidPickup` and the shared leaf predicates live once in `common`, and Fabric no longer
  re-implements them.
- The 2a/2b idioms are modernized to the NeoForge / 1.21.1 spelling.
- `BucketOperations` Forge-only hooks (`beforeWorldBucketUse`, …) are labeled as Forge-specific.
- `as-built.md` no longer describes `neoforge` as tracking `forge`; the remaining `register/*` /
  handler / cauldron / dispenser duplication is documented as explicitly accepted, not accidental.
- Every loader's GameTest suite green, no `player-view.md` change.
