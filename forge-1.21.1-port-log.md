# Forge 1.21.1 Port Log

Append-only history of the Forge 1.21.1 port. Entries are added during execution and are never
edited or removed. Nothing in the standard work loop reads this file; it exists as the audit trail
for a human reviewer. The live execution position is in `forge-1.21.1-port-status.md`.

Entries predating 2026-08-27 were migrated verbatim from the status file when execution state was
split into an overwritten snapshot and this append-only log.

## Verification history

- Stage 0 sandboxed baseline: the wrapper could not acquire its user-cache lock because the
  workspace sandbox denied access. The identical command was rerun with approved access to the
  existing Gradle cache; no environment setting was changed.
- Stage 0 diagnostic: `.\gradlew.bat :forge:compileJava --console=plain` reached
  `:common:compileJava` and reported 56 source errors. Observed groups were raw stack-tag and nested
  stack serialization, `ResourceLocation` construction, component-aware stack equality, tooltip and
  use-duration overrides, dispenser source relocation, liquid-container player parameters, and
  removed aquatic MobType APIs. Forge source compilation was not reached.
- Stage 1A verification: `.\gradlew.bat :common:compileJava --console=plain` reported 34 errors,
  down from 56. No raw stack-tag or custom-data-boundary errors remained; the only `NBTUtil`
  failures were the two deliberately deferred nested-stack codec calls. Stage 1A therefore passed
  its classified completion condition on the first edit.
- Stage 1B verification: common compile reported 32 Stage 2 errors and no nested-stack or lookup
  plumbing errors. Explicit registry access compiled on the first edit.
- Stage 2A verification: common compile reported 11 errors, all classified for Stage 2B. Identifier
  factories, tooltip/use-duration signatures, and component-aware equality compiled on the first
  edit.
- Stage 2B attempt 1: common compile reported three stale accessor names on the relocated
  `BlockSource`; liquid-container player plumbing and the aquatic entity-type tag compiled.
- Stage 2B attempt 2: `.\gradlew.bat :common:compileJava --console=plain` succeeded.
- Stage 3A diagnostic: `.\gradlew.bat :forge:compileJava --console=plain` did not reach Forge
  compilation. Gradle stopped in `:common:processResources` with `Failed to clean up stale outputs`.
  This is classified as a build-filesystem failure rather than a source failure, so the unattended
  process stopped without running `clean`, deleting outputs, killing processes, or changing the
  environment.
- User rerun of the same Stage 3A diagnostic cleared `:common:processResources` without environment
  changes and reached Forge source compilation. It reported 66 errors and 6 warnings. Stage 3 owns
  the deprecated loading-context access and obsolete Forge FTB Chunks reference. Remaining groups
  classify into Stage 4 item capability overrides; Stage 5 ingredients, loot codecs, interactions,
  automation, and fake-player APIs; and Stage 6 model/rendering APIs.
- Stage 3 verification: Forge compile reported 64 errors and 4 warnings. Constructor-injected
  loading/config context compiled, both obsolete-context warnings disappeared, and all Forge FTB
  Chunks references were removed. Remaining failures belong to Stages 4–6.
- Stage 4A verification: Forge compile reported 62 errors and 4 warnings. Stack-bound Big/Huge and
  Source Bucket fluid handlers compiled through `AttachCapabilitiesEvent<ItemStack>`; the obsolete
  item `initCapabilities` overrides were removed.
- Stage 4 dynamic-stack verification: Forge compile reported 57 errors and 4 warnings. The removed
  Forge per-stack maximum-size overrides were replaced by the vanilla `MAX_STACK_SIZE` data
  component maintained at the central `NBTUtil` write boundary.
- Stage 4C verification: Forge compile reported 54 errors and 4 warnings. Current liquid-container
  player arguments and held-item interaction range compiled. No remaining failure was classified
  to Forge capabilities, fluid handlers, or fluid transfer logic, so Stage 4 passed.
- Stage 5A evidence check: the production compile shows that `AbstractIngredient`,
  `IIngredientSerializer`, and `CraftingHelper` are absent from Forge 52.1.16. Forge's published
  1.21 ingredient documentation still prescribes those unavailable types. No Stage 5A code was
  changed and no verification attempt was consumed.
- Stage 5A attempt 1: `.\gradlew.bat :forge:compileJava --console=plain` did not reach Gradle because
  the workspace sandbox denied access to the existing Gradle wrapper distribution lock under the
  user cache. The implementation was not evaluated. Per the infrastructure rule, the identical
  command will be retried once with approved access and no environment change.
- Stage 5A attempt 2: the approved identical command reached Gradle, confirmed
  `:common:compileJava UP-TO-DATE`, and then failed in `:common:processResources` with `Failed to
  clean up stale outputs`. Forge source compilation was not reached. The repeated infrastructure
  failure stops the work unit without `clean`, output deletion, process manipulation, or another
  unattended retry.
- Stage 5A external verification: the user's identical command passed common compilation and
  resource processing, reached Forge source compilation, and reported 21 errors and 4 warnings.
  No custom-ingredient error remained, so Stage 5A passed. The remaining errors are fully assigned
  to Stages 5B, 5C, and 6.
- Stage 5B verification: Forge compilation reported 17 errors and 4 warnings, with no remaining
  loot-modifier error. The four removed errors were exactly the `Codec`/`MapCodec` failures owned by
  Stage 5B, so the work unit passed its classified completion condition on the first edit.
- Stage 5C1 verification: Forge compilation reported 14 errors and 4 warnings, with no remaining
  `Dispensers` error. The three removed errors were exactly the stale `BlockSource` failures, so the
  work unit passed its classified completion condition on the first edit.
- Stage 5C2 verification: common production compilation succeeded and Forge compilation reported 12
  errors and 4 warnings, with no fake-player error. The authorized playerless dispenser-feeding path
  compiled on the first edit; remaining errors are cauldron registration and Stage 6 client code.
- Stage 5C3 attempt 1: the interaction-map accessor compiled, and Forge compilation exposed eight
  callback return-type errors because Minecraft 1.21.1 cauldron interactions require
  `ItemInteractionResult` rather than `InteractionResult`. The bounded correction is to port the four
  callback signatures and their result constants without changing dispatch or mutation logic.
- Stage 5C3 attempt 2: Forge compilation reported only four Stage 6 client errors and four warnings.
  The interaction-map accessor, `ItemInteractionResult` callbacks, success results, and pass-through
  results compiled, so Stage 5C3 and Stage 5C passed.
- Stage 5D verification: all production JSON and `pack.mcmeta` files parsed, no stale plural recipe
  or entity-type-tag directory remained, and `:forge:processResources --console=plain` succeeded.
  Forge generated the global modifier list and all seven manifest-derived modifier files; every
  generated JSON file parsed successfully. Stage 5D and Stage 5 passed.
- Stage 6A attempt 1: the Forge compile did not reach Forge source compilation because
  `:common:processResources` reported `Failed to clean up stale outputs`. The implementation was not
  evaluated. The identical command will receive the single permitted infrastructure retry without
  an environment change.
- Stage 6A retry: the identical Forge compile reached source compilation and reported one Stage 6C
  error plus four warnings. No `NbtFluidContainerModel` error remained, so Stage 6A passed; the
  custom model continues to delegate shape and sprite baking and apply stack-aware tint overrides.
- Stage 6C verification: `.\gradlew.bat :forge:compileJava --console=plain` succeeded. The Junk
  Bucket inventory model location now uses the Minecraft 1.21.1 two-argument
  `ModelResourceLocation` constructor. The four remaining diagnostics are warnings caused by the
  common Fabric client annotation being unavailable to the Forge compiler. Stage 6C and Stage 6
  passed.
- Stage 7A0 diagnostic: `.\gradlew.bat :forge:compileGametestJava --console=plain` reached GameTest
  compilation and stopped after 100 errors. The failures classify into Forge test registration,
  shared test support, shared scenarios, and Forge-specific tests. The first bounded implementation
  unit is removal of the deleted `PrefixGameTestTemplate` annotation, which causes 34 of the
  reported errors. As a pre-edit diagnostic, this consumed no verification attempt.
- Stage 7A1 verification: GameTest compilation still stopped at its 100-error cap, but no
  `PrefixGameTestTemplate` import or annotation error remained. The 17 test holders retain their
  namespace and explicit template declarations, so Stage 7A1 passed on the first edit.
- Stage 7B1 verification: GameTest compilation remained capped at 100 errors, but no raw-tag,
  component-equality, or missing-registry error remained in `SharedGameTestSupport`. Stored-stack
  assertions now decode against the test level registry and all their scenario call sites compile.
  Stage 7B1 passed on the first edit.
- Stage 7B2 verification: GameTest compilation remained capped at 100 errors, but
  `SharedGameTestSupport` no longer reported either the removed mock-player method or the missing
  `ClientInformation` constructor argument. Stage 7B2 passed on the first edit.
- Stage 7B3 verification: no `makeMockSurvivalPlayer` reference remained, and GameTest compilation
  reported no player-construction error. Both protection scenarios retain their intended survival
  or adventure mode through the shared helper. Stage 7B3 passed on the first edit.
- Stage 7B4 verification: GameTest compilation reported no missing registry argument for
  `NBTUtil.getStoredItems` or `setStoredItems`. All 31 direct scenario calls now use the test level
  provider. A later compile below the 100-error cap exposed that one private factory lacked `helper`
  scope; Stage 7B6 owns that bounded correction.
- Stage 7B5 verification: no removed `ResourceLocation` constructor error remained, and the compiler
  total fell from the 100-error cap to 97. Stage 7B5 passed on the first edit.
- Stage 7B6 verification: the private Trash Bucket test factory compiled with registry access, and
  the compiler total fell from 97 to 96. Stage 7B6 passed on the first edit.
- Stage 7B7 verification: the recipe lookup helper compiled after unwrapping `RecipeHolder#value`,
  and the compiler total fell from 96 to 95. Stage 7B7 passed on the first edit.
- Stage 7B8 verification: the Forge transfer test compiled with `blockInteractionRange`, and the
  compiler total fell from 95 to 94. Stage 7B8 passed on the first edit.
- Stage 7C1 verification: no cauldron interaction-map or `ItemInteractionResult` mismatch remained,
  and the compiler total fell from 94 to 78. Stage 7C1 passed on the first edit.
- Stage 7C2 verification: no raw-game-event listener, storage, comparison, or count error remained,
  and the compiler total fell from 78 to 61. Stage 7C2 passed on the first edit.
- Stage 7D1 attempt 1: `Criterion` and listener trigger-instance access compiled, but the filled
  bucket helper requires `ItemPredicate.Builder` rather than `Optional`, and
  `Advancement.Builder#build` already returns `AdvancementHolder`. The bounded correction uses an
  empty item-predicate builder and removes the redundant holder construction.

## Files changed by port execution

- `common/src/main/java/com/github/crittscott/somebuckets/util/NBTUtil.java` — raw stack NBT replaced
  by detached reads and replacement writes through `minecraft:custom_data`; nested stacks now use
  explicit registry-aware encoding and decoding.
- Common Junk/Trash gameplay, dispenser, identifier, tooltip, item-use, equality, liquid-container,
  and aquatic-classification call sites were ported to their Minecraft 1.21.1 APIs.
- `forge/src/main/java/com/github/crittscott/somebuckets/client/JBRenderer.java` now supplies client
  registry access when decoding displayed contents.
- `SomeBucketsForge` now uses its injected loading context for the mod bus and server config and no
  longer contains the dropped Forge FTB Chunks integration.
- Big/Huge and Source Bucket item stacks now receive stack-bound fluid capability providers through
  the Forge item-stack attachment event, with provider invalidation wired to the attachment
  lifecycle.
- Variable bucket stack limits are represented by the vanilla `MAX_STACK_SIZE` data component and
  refreshed whenever bucket custom data changes.
- Forge liquid placement and held-transfer call sites use their current Minecraft 1.21.1
  signatures.
- Forge custom ingredients use registered `MapCodec` serializers and registry-aware network buffers;
  the six recipes use the singular 1.21.1 recipe directory and current result-stack keys.
- Forge's global loot modifier and its serializer registry use the Forge 52 `MapCodec` contract
  without changing encoded fields or loot application.
- Forge fluid dispenser behaviors use Minecraft 1.21.1's relocated dispenser `BlockSource`.
- Forge dispenser animal feeding directly grows one eligible baby or sets one eligible adult in love
  after authorization, consuming one stored food item; Forge no longer installs or contains a fake
  player, while player feeding and Fabric's automation-player installation remain intact.
- Forge cauldron registrations use `InteractionMap#map`, and their callbacks use the 1.21.1
  item-specific interaction result while preserving success and pass-through semantics.
- Production recipes and entity tags use Minecraft 1.21.1's singular directories, production pack
  metadata uses Forge 52.1's format 34, and Forge loot resources remain manifest-generated.
- The custom Forge fluid geometry uses Forge 52's current five-argument bake method without changing
  its stack-aware tint pipeline.
- The Forge client model loader constructs the Junk Bucket inventory model key with the current
  two-argument `ModelResourceLocation` constructor.
- The assessment, plan, process, and status documents establish the execution framework.

## Completed stages

- Stage 0 — Baseline diagnostics.
- Stage 1A — Simple component-backed state boundary.
- Stage 1B — Registry-aware nested Junk/Trash stacks.
- Stage 2 — Minecraft 1.21.1 common API port.
- Stage 3 — Forge bootstrap and registration.
- Stage 4 — Forge capabilities and fluid transfer core.
- Stage 5A — Forge custom ingredients and 1.21.1 recipe resources.
- Stage 5B — Forge global loot modifier MapCodec.
- Stage 5C — Forge interactions and automation.
- Stage 5D — Forge production resources and metadata.
- Stage 5 — Forge server systems and data.
- Stage 6 — Forge client model and rendering compilation.

## Process changes

- 2026-08-27: Execution state split into the overwritten `forge-1.21.1-port-status.md` snapshot and
  this append-only log. Work units coarsened to one coherent behavioral group with a single
  end-of-stage verification. Stable controlling documents are now read once per session. Verification
  output is reduced to a count and delta before recording. Execution runs one stage per session.

- Stage 7D attempt 2: `.\gradlew.bat :forge:compileGametestJava --console=plain` exited before Gradle because the workspace sandbox denied the existing wrapper-distribution lock; the identical command was retried once with approved cache access and no environment change.
- Stage 7D attempt 3: the approved identical command reached GameTest compilation and reported 34 errors, down from 61. All migrated advancement uses compiled except for a missing `ItemPredicate` import in `SBScenarios`; the other 33 errors are assigned to later Stage 7 work units. The three-attempt limit blocks further correction pending user direction.

- Stage 7D resumed on 2026-08-28 after the user explicitly reset the failed-verification count to zero. The next correction is the missing `ItemPredicate` import identified by the preceding compile.

- Stage 7D fresh attempt 1 after the user-authorized reset: `.\gradlew.bat :forge:compileGametestJava --console=plain` reached 33 errors, down from 34. No advancement error remained, so Stage 7D passed its classified completion condition; remaining errors are 32 raw state-tag accesses and one tooltip serialization signature.

- Stage 7E attempt 1: `.\gradlew.bat :forge:compileGametestJava --console=plain` reached one error, down from 33. No raw item-tag/component error remained, so Stage 7E passed its classified completion condition; the sole remaining error is the tooltip JSON serializer's required registry provider.

- Stage 7F verification: `.\gradlew.bat :forge:compileGametestJava --console=plain` succeeded with no compiler errors. Explicit server registry context now supports tooltip JSON serialization.
- Stage 7 completed on 2026-08-28: Forge GameTest sources compile, intended tests remain enabled in their discovery classes, and the cumulative `:forge:compileGametestJava` gate passes.

- Stage 8A baseline: `.\gradlew.bat :forge:runGameTestServer --console=plain` discovered 185 tests, then exited 1 before test execution because `somebuckets:empty_9x6x9` was missing. The generated NBT exists only under the obsolete plural `data/somebuckets/structures` path; Stage 8B owns the singular-directory correction.

- Stage 8B attempt 1: `.\gradlew.bat :forge:runGameTestServer --console=plain` generated and processed the singular structure resource but again discovered 185 tests and crashed before execution on missing template `somebuckets.empty_9x6x9`. Forge 52 source shows the positional `GameTestHolder` value is a prefix, not a namespace; attempt 2 uses the exact `somebuckets:empty_9x6x9` template id.

- 2026-08-28 — Stage 8B counter reset to 0 of 3 at the user's direction; the existing runtime
  structure-lookup work unit resumed with a fresh verification window.
- 2026-08-28 — Stage 8B fresh attempt 1 of 3: changed the shared template constant to the exact
  `somebuckets:empty_9x6x9` identifier and ran `:forge:runGameTestServer`. Forge discovered all 185
  tests, but the server still crashed before execution with missing structure
  `somebuckets.empty_9x6x9`; the generated NBT was present under the processed singular
  `data/somebuckets/structure` resource path. Work unit remains in progress.
