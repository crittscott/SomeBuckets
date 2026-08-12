# Some Buckets — Merged Code Review

Merged from three independent reviews (code-review-1.md, code-review-2.md, code-review-3.md), each a
full read of `common/`, `forge/`, `fabric/`, and the Gradle build, cross-checked against
`player-view.md` and `as-built.md`. Per instructions, doc-vs-code mismatches are documentation
problems, not code defects, and are only raised where the underlying *behavior* is itself a concern.
Duplicate findings from the three reviews have been merged; this file is a consolidation, not a
re-verification.

## 1. Standard code quality: correctness, safety, redundancy, dead code, bugs

### Junk Bucket and Mob Bucket are stackable to 64 on Fabric, but their content is mutable per-stack NBT (real bug — found independently by all three reviews)

- `fabric/src/main/java/com/github/crittscott/somebuckets/register/FabricItems.java:18-19` registers
  `JUNK_BUCKET` and `MOB_BUCKET` with plain `new Item.Properties()` — no `.stacksTo(1)`.
- `forge/src/main/java/com/github/crittscott/somebuckets/register/ModItems.java:25-28` registers the
  same two items with `.stacksTo(1)` explicitly.
- `common/.../item/JBItem.java:47-51` and `common/.../item/MBItem.java:64-66` do **not** force
  `stacksTo(1)` in their own constructors — they trust the `Properties` passed in by the registration
  call site.

Compare with the mod's other four items, which all self-enforce unstackability in their own
constructor regardless of what the caller passes, making them loader-independent:
- `BBItem.java:48-51`: `super(properties.stacksTo(1))`
- `SBItem.java:42-44`: `super(props.stacksTo(1))`
- `TBItem.java:40-42`: `super(properties.stacksTo(1), 1)`

`JBItem`/`MBItem` are the odd ones out, and only Forge's registration call happens to compensate. On
Fabric, two empty Junk (or Mob) Buckets will merge into one `ItemStack` of count 2+ in the same slot,
because vanilla stack-merging only checks item identity and NBT equality, and two empty buckets have
no NBT to differ on. All of this mod's storage/capture logic (`JBItem.use`,
`MBItem.interactLivingEntity`, `MBItem.capture`, `MBItem.releaseOldest`, etc.) fetches
`player.getItemInHand(hand)` and mutates that stack's tag compound directly, with no "split one off
the stack first" step. Using one bucket out of a merged stack therefore applies the mutation (stored
items, captured mob) to the *entire* stack at once — e.g. capturing a cow with one of two stacked Mob
Buckets leaves both showing that same captured cow, and splitting the stack in an inventory
(shift-click half into another slot) duplicates the capture. This is a genuine dupe-shaped bug
specific to the Fabric build, not a cosmetic difference; capacity math (`getBarWidth`, `canAccept`,
`MBItem.MAX_MOBS`) is also computed against one bucket's worth of state while the player may be
holding several.

`as-built.md` documents this stacking difference as current behavior, so it is not a doc/code
mismatch — but the behavior itself looks unintended, since it defeats the purpose of the `stacksTo(1)`
pattern the other five items already use to prevent exactly this class of bug. Fix: move `.stacksTo(1)`
into `JBItem`'s and `MBItem`'s own constructors the same way `TBItem` already does, so it is enforced
regardless of which loader's registration code calls it, making the loader-specific `.stacksTo(1)`
calls redundant (harmless) rather than load-bearing.

### `big_bucket_64.json` is likely missing Fabric's custom-ingredient discriminator (high severity, unverified — found by two of three reviews)

`common/src/main/resources/data/somebuckets/recipes/big_bucket_64.json:9`:

```json
"B": { "type": "somebuckets:empty_bucket", "item": "somebuckets:big_bucket_8" }
```

Every other custom-ingredient use in the repo — e.g.
`common/.../recipes/mob_bucket.json:4-5` — carries **both** `"type"` and `"fabric:type"`:

```json
{ "type": "somebuckets:empty_bucket", "fabric:type": "somebuckets:empty_bucket", "item": "somebuckets:source_bucket" }
```

`as-built.md` records the omission as current fact but doesn't say what it does at runtime. Fabric's
custom-ingredient deserialization is keyed off `"fabric:type"`; without it, Fabric's ingredient parser
likely has no reason to route to the custom serializer and falls back to a plain item-id match
(ignoring fill state). If so, the Huge Bucket recipe's "input must be empty" requirement
(`player-view.md`) would silently not apply on Fabric, letting a filled Big Bucket 8 craft into a Huge
Bucket, destroying its contents. This was not run/verified in-game (out of scope for this review), but
given every other instance in the repo pairs the two keys, this looks like a real gap worth a quick
in-game check on Fabric. Fix, if confirmed: add `"fabric:type": "somebuckets:empty_bucket"` to the `B`
key in `big_bucket_64.json`.

### Minor: inconsistent thread-safety pattern in `AutomationPlayers`

`common/.../protection/AutomationPlayers.java:11-23` writes its `provider` field under a
`synchronized install()` but reads it in `get()` without synchronization and without `volatile`.
The sibling class `common/.../platform/BucketOperations.java:18-33` gets this right: `Holder.instance`
is `volatile`, `install` is `synchronized`, `get()` does a plain volatile read — the standard
safe-publication idiom. `AutomationPlayers` should mirror that pattern. Risk is low in practice since
installation happens once during single-threaded mod bootstrap before the server thread processes
dispensers, but the inconsistency between two structurally identical "install once, read many times"
classes is worth fixing.

### Everything else in this category is clean

All three reviews independently concluded the rest of the codebase is correct: `NBTUtil`,
`StoredFluid`, `FluidPlacement`, `Protections`/`ClaimProtections`, the fluid handler hierarchy
(`AbstractFluidHandler`/`BBFluidHandler`/`SBFluidHandler` on Forge, `FabricBucketStorage` on Fabric),
`Transfers`, `Cauldrons`, and the dispenser behaviors consistently simulate before authorizing and
authorize before mutating, normalize empty state through one shared path (`NBTUtil.removeTagIfEmpty`),
and correctly separate read-only preview methods from mutating ones. `MBItem.releaseOldest` removes
the stored snapshot only after `addFreshEntity` succeeds. No dead code, no leftover `TODO`/`FIXME`
markers, no dangling references or off-by-one/unit-conversion bugs were found. GameTest sources
(Forge-only, ~5,000 lines) were spot-checked and are excluded from the shipped jar
(`forge/build.gradle:77-78`), so their quality doesn't affect players.

## 2. The Forge/Fabric/Minecraft way

Both loaders follow their platform's idioms closely:

- Forge: `IFluidHandlerItem`/`ICapabilityProvider` via `LazyOptional` (`FluidProvider.java`),
  `CauldronInteraction` maps (`Cauldrons.java`), `DispenserBlock.registerBehavior` with
  `DefaultDispenseItemBehavior`, `FillBucketEvent`, `FurnaceFuelBurnTimeEvent`, `DeferredRegister`/
  `RegistryObject`, `DistExecutor` for server-safe client access, and a shared
  `BlockEntityWithoutLevelRenderer` exposed through `IClientItemExtensions` for rendering. Notably,
  `BBFluidLogic.placePowderBlock` reimplements vanilla's own block-snapshot/event transaction
  (`captureBlockSnapshots`, `ForgeEventFactory.onBlockPlace`) to keep powder-snow placement compatible
  with other mods listening for Forge's block-place events — a faithful, idiomatic use of Forge's
  public contracts.
- Fabric: `Storage<FluidVariant>`/Transfer API with `Transaction.openOuter()` simulate-then-commit
  patterns throughout `FabricBucketOperations`, `CustomIngredient`/`CustomIngredientSerializer` for
  crafting, `UseItemCallback` for off-hand transfer priority, `BuiltinItemRendererRegistry`/
  `FabricBakedModel` for rendering, and a Mixin (`AbstractFurnaceBlockEntityMixin`) used only where
  Fabric has no event hook equivalent to Forge's `FurnaceFuelBurnTimeEvent`. This matches the expected
  Fabric approach.
- Both loaders register a genuine loader-native fake player for dispenser actions
  (`FakePlayerFactory`/Fabric's `FakePlayer`) rather than inventing an ad hoc actor, which is what
  claim mods (FTB Chunks) expect to see, and both route claim-mod checks through the shared
  `Protections`/`ClaimProtectionProvider` seam rather than duplicating logic.
- Item-side loader shells (`ForgeBBItem`, `ForgeJBItem`, `FabricBBItem`, ...) are a clean, minimal
  pattern for attaching loader-only extension points onto shared behavior.

No Forge-only or Fabric-only API leaks into `common/` were found (verified across all three reviews:
no `net.minecraftforge`/`net.fabricmc`/`dev.architectury` imports anywhere under
`common/src/main/java`).

### Architectury API is declared mandatory but is essentially unused, and a build comment is now wrong (found by all three reviews)

`forge/src/main/resources/META-INF/mods.toml:68-75` declares `architectury` as a `mandatory=true`
runtime dependency, with the comment:

```
# Common code calls Architectury's Platform API directly, so this is a real runtime dependency,
# not just a compile-time one.
```

That statement is false for the current source tree — `common/src/main/java` has zero Architectury
usage. The only direct Java use of Architectury in the entire codebase is one call,
`Platform.isModLoaded("ftbchunks")` in `forge/.../SomeBucketsForge.java:72`. Fabric's equivalent check
(`SomeBucketsFabric.java:46`) uses `FabricLoader.getInstance().isModLoaded(...)` directly and doesn't
use Architectury at all, even though Architectury is also declared mandatory in `fabric.mod.json:30`.
`as-built.md` itself is accurate about this, but the `mods.toml` comment predates that reality and
should be corrected or the dependency reconsidered. As it stands: `common/build.gradle:19`'s
`modCompileOnly "dev.architectury:architectury:${architectury_api_version}"` pulls in a dependency
`common` never uses; every Forge and Fabric player is required to install a whole extra mod
(multi-hundred-KB runtime library) for one boolean check that Forge's own `ModList.get().isLoaded(...)`
could answer without it, and that Fabric doesn't even use Architectury for. Worth confirming this isn't
secretly required transitively (e.g. by FTB Chunks, which is itself Architectury-based) before treating
it as removable — but if not, either lean into it (use `Platform.isModLoaded` on Fabric too, for
consistency) or drop the mandatory runtime dependency.

## 3. Over-guarding

No meaningful over-guarding was found, across all three reviews. The codebase consistently trusts its
own code and lets Forge/Fabric/Minecraft crash on their own misbehavior:

- `Transfers.tryTakeFromBlock`/`tryPlaceIntoBlock` (Forge) and the `Cauldrons` counterparts do throw
  `IllegalStateException` if a simulated fill/drain and the subsequent executed fill/drain disagree —
  but this guards against a *third-party* block's fluid handler violating its own simulate/execute
  contract (a real, known-unreliable interface — any mod's tile entity), not the mod's own code. That
  is an appropriate place to guard, not over-guarding.
- `Transfers.requireBucketHandler` and Fabric equivalents assume the mod's own item capability is
  present and throw if not — correctly treating "our own capability provider is missing" as an
  invariant violation rather than something to silently tolerate.
- `NBTUtil.requireNonNegative` guards public setters against negative amounts — reasonable input
  validation on a widely-reused persistence utility, not defensive noise.
- `MBItem.releaseOldest` dereferences `NBTUtil.getCurrentEntityType(stack)` without a null check,
  relying on the invariant that `capture()` always sets the entity-type header first — consistent with
  the project's policy of trusting its own prior calls rather than re-validating them.
- `JBItem.canFitInsideContainerItems()`/`canStore` delegate to vanilla's own
  `Item.canFitInsideContainerItems` flag rather than reimplementing a bundle/shulker-box exclusion
  list — the minimal correct amount of checking.
- `SBPolicy.resolve` logging and skipping unknown fluid ids is a server-admin-facing feature (bad
  config entries), not internal mistrust.

## 4. Simplicity

The code is generally direct and free of speculative abstraction, per all three reviews:

- `BucketOperations` is a plain interface + static holder (service locator) — the right amount of
  indirection for a project that deliberately doesn't use Architectury Loom's `@ExpectPlatform`
  bytecode injection.
- `ProtectionContext`/`ProtectionAction`/`ClaimProtectionProvider` are a small, closed set of types
  covering exactly the mod's interaction categories — no generic "rule engine" the problem didn't call
  for.
- The elaboration that does exist (`BucketOperations`/`AutomationPlayers` as the seam between `common`
  and two genuinely different loader fluid backends) buys real value and isn't speculative.
- Client fluid/Junk rendering (`NbtFluidContainerModel`, `FabricFluidContainerModel`,
  `JBRenderer`/`FabricJunkBucketRenderer`) is justified by the problem itself (per-stack NBT-driven
  tinting and dynamic geometry), is entirely client-side, and caches appropriately
  (`StackTintOverrides`'s bounded cache, `TintedFluidLayer`'s per-quad-key cache) rather than
  prematurely.
- No unused "flexibility" — no dead config knobs, speculative interface parameters, or generic
  collection types where a concrete one would do.

### Duplication worth noting (not necessarily worth fixing)

- `forge/.../compat/ftbchunks/FtbChunksProtection.java` (85 lines) and
  `fabric/.../compat/ftbchunks/FabricFtbChunksProtection.java` (69 lines) are line-for-line identical
  apart from one call (`DispenserFakePlayer.get(level)` directly on Forge vs. going through
  `AutomationPlayers.get(level)` on Fabric — see build section) and one cast. Every type referenced is
  already loader-neutral (FTB Chunks is itself Architectury-based). If FTB Chunks ships a common API
  artifact usable as `compileOnly` in `common/`, this adapter could likely move there as one class
  registered from both entrypoints. Not verified against FTB Chunks' actual artifact layout.
- `Transfers` (Forge) and `FabricBucketOperations`'s milk-transfer methods
  (`fabric/.../platform/FabricBucketOperations.java:505-615`) independently reimplement the same
  "settle a stack-wide transfer, keep one useful result in hand, drop the rest" logic (~25 lines each).
  This can't move to `common/` without pulling loader fluid APIs there, so the duplication is a
  reasonable consequence of the architecture — flagged only because a future change to the settlement
  rule has to be made twice.
- The client-side "average texture color" and "junk bucket opening mask" computations are duplicated
  essentially verbatim between loaders: `forge/.../client/ClientFluidColors.java` vs
  `fabric/.../client/FabricClientFluidColors.java`, and `forge/.../client/BucketMouth.java` vs
  `fabric/.../client/FabricBucketMouth.java` (near character-for-character identical), and likewise
  `JunkIconLayout.java` vs `FabricJunkIconLayout.java`. Unlike the fluid-transfer duplication, this
  logic touches only vanilla Mojang client classes (`NativeImage`, `Resource`, `Minecraft`) — no Forge
  or Fabric API — so it's a genuine sharing candidate. The catch: the project's `buildSrc` convention
  plugins wire up one common Java source set compiled into every loader's *main* compilation, including
  the dedicated server, so putting `Minecraft`/`NativeImage`-touching code there risks class-loading
  trouble on a headless server unless a "common client" source set were introduced — a real build
  change, not a trivial move. A "nice to have if revisited" item, not something to fix reflexively.

### Minor cosmetic nit

`forge/.../register/ModCreativeTabs.java:50-61` packs two Java statements onto several single lines,
inconsistent with the one-statement-per-line style used elsewhere. Not a functional issue.

## 5. Server-friendliness

No per-tick hot paths were found, across all three reviews. All of the mod's logic is
interaction-driven (right-click, dispenser pulse, cauldron interaction) rather than ticking:

- No `BlockEntity`/`Entity` tick handlers are registered at all, consistent with `as-built.md` ("no
  blocks, block entities, ... or saved-world objects").
- World queries (`level.getEntitiesOfClass` for item/animal/mob pickup and dispenser targeting) are
  bounded to small `AABB`s (1.5–2.25 block radius, or a single dispenser-front block), only run on an
  actual interaction, and `TBItem.findFirstNearby` explicitly caps its query to one result
  (`level.getEntities(..., result, 1)`) rather than collecting and taking the first element — a
  deliberate invariant documented in `as-built.md`.
- `SBPolicy` resolves its allowlist into an immutable snapshot on config load/reload rather than
  re-parsing config or re-resolving fluid registry lookups per check; `allows()` is an O(allowlist
  size) loop over a small in-memory `Set<Fluid>`, called only from interaction paths.
- The bounded retry loops in `Transfers.fillFrom`/`drainInto` (Forge) and
  `FabricBucketOperations.moveHeld`/`moveInfiniteHeld` (capped at 64 passes) run once, synchronously,
  per interaction or dispenser pulse — not a recurring cost.
- Client-side rendering costs (per-frame NBT re-parsing/layout in `JBRenderer`/
  `FabricJunkBucketRenderer`, color-averaging) are real but explicitly out of scope per the stated
  priority that client-side cost matters much less, and are bounded by how many buckets are on screen.

## 6. Server-admin configuration

All three reviews agree: `sourceBucket.allowedContents`/`allowedContents` (Forge
`serverconfig/somebuckets-server.toml`, Fabric `config/somebuckets-server.json`) is exactly the kind of
knob a server admin would want, implemented consistently on both loaders including unknown-id logging
(`SBPolicy.refresh`) and reload support (Forge's `ModConfigEvent.Loading`/`Reloading`; Fabric's
`ServerLifecycleEvents.SERVER_STARTING` re-read, a reasonable substitute given Fabric has no built-in
config-reload event). Both config paths validate entries and log-and-ignore unknown fluid ids rather
than failing the whole config or crashing — the right failure mode for a server admin who fat-fingers
an id. `Cauldrons`/`FluidPlacement`/`NonFluidDispensers` route every automated action through the same
protection seam a real player would use, so claim-mod admins get consistent behavior between manual and
dispenser use.

No other config surface is implied by the design documents but missing. Junk/Trash/Mob Bucket
capacities (9, 1, 8) and Big/Huge Bucket unit counts (8, 64) are compile-time constants; nothing in
`player-view.md`/`as-built.md` frames these as admin-configurable, so their absence isn't a gap against
the spec — just worth flagging as a possible future ask if server admins request it.

## 7. Build environment (common/forge/fabric split, Architectury/Gradle setup)

All three reviews independently confirm this project does **not** use the Architectury Loom multiloader
template most Architectury tutorials assume — worth stating plainly since that's the setup most likely
to surprise someone unfamiliar with it. Instead:

- `settings.gradle` includes three subprojects (`common`, `forge`, `fabric`); Forge uses ModDevGradle
  LegacyForge (`net.neoforged.moddev.legacyforge`), Fabric uses Fabric Loom — two independent Gradle
  plugins, not one shared multiloader plugin. No stray modules; `functional-gradle-files/` (a reference
  build used only to confirm toolchain versions) is correctly excluded from both `settings.gradle` and
  git tracking.
- `buildSrc/src/main/groovy/multiloader-common.gradle` and `multiloader-loader.gradle` are small,
  hand-written convention plugins. `common`'s Java sources and resources are exposed as Gradle
  `commonJava`/`commonResources` consumable configurations (`canBeResolved = false`), and each loader's
  `compileJava`/`processResources` tasks pull those files in directly. `common` is compiled twice — once
  per loader — and never exists as a standalone jar or runtime dependency. This is a legitimate, known
  community pattern (the "MultiLoader Template" approach, an alternative to Architectury Loom's
  `@ExpectPlatform` bytecode rewriting), and it's documented both in code comments and in `as-built.md`.
- **Sharp edge for newcomers**: common's own dependency declarations do not travel with its source
  (`common/build.gradle`'s `mixin`/`architectury` deps are compile-only conveniences for `common`'s own
  compilation only) — every loader module must independently redeclare any library `common`'s source
  imports. This is correctly documented today (e.g. the `jsr305` comment in `fabric/build.gradle:16-21`
  explaining why Fabric needs an explicit `compileOnly` that Forge gets for free transitively) and was
  verified consistent across both loaders for everything `common/` currently imports — but a future
  contributor could easily add an import to `common/`, forget the matching `compileOnly`/
  `modCompileOnly` on one loader, and get either a compile failure or (worse, if the class happens to
  already be on that loader's classpath transitively) a silent behavior difference between loaders.
  A convention plugin that must never be replaced with `implementation project(":common")` — the two
  loaders share source, not a compiled artifact.
- Module boundaries are clean and independently verified by all three reviews: `common/` imports no
  `net.minecraftforge.*`/`net.fabricmc.*`/`dev.architectury.*` classes anywhere.
- Repository blocks across the module `build.gradle` files are commented with *why* each repo is needed
  (Sponge's Maven for Forge's mixin annotation processor, `maven.architectury.dev`, `maven.ftb.dev`)
  rather than an undifferentiated repo dump. `gradle.properties` documents every pinned toolchain
  version with a comment, and the pinned values match what the loader `build.gradle` files actually
  consume and what `as-built.md` claims — no drift found.
- Both loaders correctly re-derive Forge/Fabric metadata placeholders from `gradle.properties` in their
  own `processResources` blocks with no drift between the two loaders' variable-substitution lists.
- See §2 for the Architectury-mandatory-but-barely-used finding, which is also a build-environment
  concern for anyone seeing it required in both `mods.toml` and `fabric.mod.json`.

### Minor build observations

- `gradle.properties:3-4` sets `org.gradle.daemon=false` globally, so every Gradle invocation (for
  either loader) pays full JVM/plugin-classloading startup cost with no warm daemon reuse — plausibly
  deliberate (e.g. to dodge stale-daemon problems switching between ModDevGradle and Loom caches) but
  non-obvious, and will make the two-loader build feel slower than a typical single-loader Forge
  project. Worth a deliberate yes/no from whoever owns the build rather than assuming it's a leftover
  template default.
- `org.gradle.jvmargs=-Xmx3G` is shared by both loader toolchains; if a full build ever runs Forge's
  ModDevGradle machinery and Fabric Loom's remapping in the same Gradle process back to back, 3G may be
  tighter than a single-loader project would need. Not diagnosed as an actual failure, just worth
  revisiting if OOM issues show up during a full multi-module build.
- `forge/build.gradle` and `fabric/build.gradle` both carry forward-looking comments about mixin wiring
  that doesn't exist yet on Forge (`# No mixin config exists yet...`) — harmless, and the file already
  flags itself as provisional, but worth pruning once/if that gap closes, since project convention is
  for comments to describe current code, not future hypotheticals.
- Minor consistency nit (found by all three reviews): `forge/.../compat/ftbchunks/FtbChunksProtection.java:57`
  calls `DispenserFakePlayer.get(level)` directly, whereas every other piece of Forge automation code and
  the Fabric mirror of this exact class (`FabricFtbChunksProtection.java:42`) go through the
  `AutomationPlayers` indirection that `as-built.md` documents as "the stable loader-native fake player."
  Today `AutomationPlayers.install` is wired to `DispenserFakePlayer::get` in `SomeBucketsForge.java:36`,
  so behavior is identical either way — but this is the one place in the codebase that reaches around an
  abstraction it otherwise relies on. Low severity, worth aligning for consistency.

### Build output committed to the repository (found by one review; not cross-checked by the other two)

`git ls-files` shows 41 files under `common/build/` and 231 files under `forge/build/` are currently
tracked in git (compiled `.class` files), despite `.gitignore` covering `**/build/` for exactly this
purpose. These were added in the original multi-loader migration commit and never removed. The most
recent commit on this branch, "Stop tracking Fabric build output," fixed this exact problem for
`fabric/build/` but not for `common/build/` or `forge/build/` — those two modules' stale compiled
output are still checked in and will keep silently accumulating diffs (or going stale relative to
source) on every future build. Recommend `git rm -r --cached common/build forge/build` as a follow-up,
mirroring what was already done for Fabric.

## Overall assessment

All three reviews converge on the same picture: the core interaction logic (fluid transactions,
protection, storage, mob capture/release) is careful, idiomatic per-loader, free of over-guarding and
speculative abstraction, and server-friendly with no per-tick costs. The one must-fix is the missing
`stacksTo(1)` on Fabric's Junk and Mob Buckets (§1) — a genuine dupe-shaped data-integrity bug. The
`big_bucket_64.json` discriminator gap (§1) is a close second, pending in-game verification. Everything
else is build/dependency hygiene (Architectury's near-total disuse, stale tracked build output, the
`daemon=false` setting) or minor consistency nits, none of which are urgent.
