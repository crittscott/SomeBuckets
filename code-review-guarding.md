# Targeted code review: guarding impossible errors

## Scope and standard

This review examined the current production Java and relevant GameTest call sites for null guards,
fallbacks, clamping, exception handling, and recovery behavior. It did not read any other code-review
reports, modify implementation code, or build/run the project.

The standard used here is the project's stated one: trust Minecraft and Forge contracts and fail
visibly when an internal invariant is broken; protect interfaces that are genuinely optional or
external, such as resource-pack files, server configuration text, optional-mod availability, and
foreign capabilities that are allowed to be absent.

## Summary

The project does not have a broad over-catching problem. Its two `IOException` catches are narrowly
placed around resource-pack reads, and it does not catch runtime exceptions from Minecraft, Forge,
claim providers, rendering, or entity creation.

The main overguarding is instead silent fallback at internal boundaries. A missing protection context
can disable claim checks, a missing player can become unattributed automation, an impossible hand can
be guessed as the main hand, and a missing capability on the mod's own bucket can quietly change
dispatch. There are also several smaller null checks and clamps around values Minecraft or the mod's
own call graph already guarantees.

## Findings

### 1. Medium: nullable protection contexts turn an impossible caller error into a permission bypass

The storage mutation API declares its protection context nullable:

- `JBItem.absorbItemEntities` and `absorbItemEntity` at `JBItem.java:227-246`;
- `JBItem.feedAnimal` at `JBItem.java:285-292`;
- the corresponding Trash overrides at `TBItem.java:153-173`.

Each method conditionally authorizes the mutation only when the context is non-null. Every current
production caller supplies a context: player paths use `ProtectionContext.player`, and dispenser
paths use `ProtectionContext.dispenser`. The protection GameTests also supply an explicit context.
There is no legitimate current call shape in which these mutations should bypass the provider layer.

This is more serious than harmless defensive coding: if a future call site accidentally passes null,
the operation succeeds without a claim check. That violates the maintenance invariant that every
mutation check its real target.

Recommendation: make `ProtectionContext` non-null in these APIs and call `Protections.mayAct`
unconditionally. Keep `feeder` nullable in `feedAnimal`; null there deliberately selects the dispenser
fake player and is independent of whether authorization occurs.

### 2. Medium: nullable-player convenience overloads silently invent unattributed automation

The public Big and Source fluid methods accept a nullable `Player` and convert null to
`ProtectionContext.unownedAutomation()`:

- `BBFluidLogic.java:43-47`, `:149-153`, `:249-253`, and `:291-295`;
- `SBFluidLogic.java:47-51` and `:143-147`.

Runtime player paths always pass a real player. Runtime dispensers do not use null; they call the
context overloads with `ProtectionContext.dispenser(sourcePos)`, which is necessary for a stable fake
player position and claim attribution. Null is used by GameTests as a convenience.

Consequently, an accidental null in production is not a supported state. Turning it into source-less
automation hides the caller bug and can cause FTB Chunks to reuse the fake player's previous position,
because an unowned context has no `automationSource` from which to reposition it.

Recommendation: make the player overloads require a `Player`. Have GameTests call the existing
context overloads with an explicit `ProtectionContext.unownedAutomation()` when that is what the test
means. This leaves unowned automation available, but makes choosing it deliberate.

### 3. Medium: player-hand and server-player fallbacks hide invalid protection contexts

`ProtectionContext.player(Player, ItemStack)` infers the hand by identity and falls back to
`MAIN_HAND` when the stack is in neither hand (`ProtectionContext.java:16-20`). At the current call
sites, the stack came directly from one of that player's hands, so “neither” is an internal contract
violation, not a reason to guess.

`FtbChunksProtection` adds two more fallbacks at `FtbChunksProtection.java:36-45`:

- a non-null context player that is not a `ServerPlayer` is treated as automation;
- a player context with a null hand is treated as main-hand use.

`ClaimProtections.mayAct` only invokes providers for a `ServerLevel`. A real player associated with
that level is a `ServerPlayer`, and both `ProtectionContext.player` factories produce a hand. These
fallbacks therefore convert malformed internal contexts into a different actor or hand, potentially
asking the claim mod the wrong question.

Recommendation: pass the known `InteractionHand` through player item-use paths rather than inferring
it from the stack. In the FTB provider, branch on `context.player() != null`, cast to `ServerPlayer`,
and use the required hand directly. A broken context should fail at its source rather than be
reclassified as automation.

### 4. Low: the mod treats absence of its own fluid capability as an ordinary dispatch result

Both `BBFluidLogic.compatibleBlockCapability` and `SBFluidLogic.compatibleBlockCapability` return a
block's handler only if the Some Buckets stack also reports `FLUID_HANDLER_ITEM`
(`BBFluidLogic.java:56-63`, `SBFluidLogic.java:60-67`). Callers then query that same item capability
again with `orElse(null)` (`BBFluidLogic.java:75`, `:115`, `:167`; `SBFluidLogic.java:78`, `:161`).

These methods are Big/Source-only paths, and those items unconditionally install their handlers in
`initCapabilities`. If their capability is absent, the mod is broken. Quietly returning null makes a
tank interaction fall through to world-fluid logic or become a no-op, hiding the registration fault.
The later helpers dereference the handler without checking it anyway, so the repeated nullable lookup
does not establish a coherent recovery strategy.

Recommendation: discover the block capability as optional, but trust the mod bucket's own item
capability. Retrieve it once with a fail-fast expectation (or pass it from a helper that guarantees
it) rather than using its absence to select another behavior.

### 5. Low: Minecraft raytrace results are checked for null despite a non-null contract

`Item.getPlayerPOVHitResult` and `Entity.pick` return a hit result, using a `MISS` result when nothing
is targeted. Nevertheless, null is accepted in:

- Big Bucket sneak-clear and transfer selection (`BBItem.java:175-176`, `:191-192`);
- Source Bucket sneak-clear and transfer selection (`SBItem.java:57-58`, `:70-71`);
- the off-hand interaction subscriber (`NBEvents.java:36-37`).

The same classes correctly use later `getPlayerPOVHitResult` calls without null checks. The mixed
style implies uncertainty where Minecraft's API supplies none.

Recommendation: test only `getType() == HitResult.Type.MISS`.

### 6. Low: normalization rechecks tag nullability after proving a content mode

`NBTUtil.normalizeEmptyState` first computes a non-`NONE` mode from the stack tag. Inside each
mode-specific branch it retrieves the tag again and returns if it is null
(`NBTUtil.java:269-302`, specifically `:273-274`, `:281-282`, `:289-290`, and `:297-298`).

On Minecraft's single-threaded item mutation path, a stack cannot report `MILK`, `POWDER_SNOW`,
`FLUID`, or `ENTITY` and then lose its tag between those statements. These guards silently leave the
invalid state untouched rather than protecting any real interface.

Recommendation: retrieve the tag once after determining that normalization is needed and mutate it
directly. Do not add synchronization or recovery for concurrent `ItemStack` mutation; that is not a
supported Minecraft execution model.

### 7. Low: several clamps and defaults conceal invalid values guaranteed by internal contracts

The following fallbacks protect values that current constructors, arithmetic, or Minecraft/Forge
contracts already guarantee:

- `NBTUtil.setAmount` and `setPowderUnits` clamp negative values to zero
  (`NBTUtil.java:81-82`, `:115-117`). All current callers begin with sufficient stored content or add
  nonnegative amounts. A future subtraction error would silently become an empty-mode transition.
- `JBItem.getBarWidth` returns zero for a nonpositive capacity (`JBItem.java:80-87`), although the only
  constructed capacities are 9 and 1. A bad item registration should fail at construction, not only
  hide its bar.
- transfer settlement replaces an impossible item max-stack size below 1 with 1
  (`Transfers.java:110`, `:141`). Registered Minecraft/Forge items have a positive maximum stack size.
- transfer pumping clamps a foreign handler's negative tank capacity to zero
  (`Transfers.java:165`, `:197`). A negative capacity violates `IFluidHandler`; the rest of the same
  transaction already trusts that handler's simulation and execution results.
- `JunkIconLayout.seedFor` substitutes zero when a live stored item's registry key is null
  (`JunkIconLayout.java:77-80`). A usable `ItemStack` contains a registered item.

Recommendation: remove silent clamps where the type/API contract is sufficient. If a constructor or
internal arithmetic boundary merits an explicit check, reject the invalid value immediately with a
clear exception instead of converting it into plausible-looking state.

### 8. Low: dispenser-only code retains client-side recovery branches

`DefaultDispenseItemBehavior.execute` runs from the server dispenser path, but
`Dispensers.handleMobBucket` accepts a generic `Level` and returns early on the client
(`Dispensers.java:177-180`) before later casting that same level to `ServerLevel` at `:200`. The direct
Source Bucket dispenser cauldron branches likewise wrap mutations in `!level.isClientSide` at
`:67-70` and `:76-79`, while the adjacent Big Bucket dispenser branches correctly mutate directly.
`SBFluidLogic.tryMilkDispenser` also guards its mutation with `!level.isClientSide` at
`SBFluidLogic.java:316-330`.

Recommendation: carry `ServerLevel` through dispenser-only helpers and remove client recovery paths.
Keep side checks in shared item-use helpers that genuinely execute on both sides.

### 9. Low: Source config resolution repeats guarantees already established by Forge

The config spec accepts only strings that parse as `ResourceLocation`
(`ServerConfig.java:23-28`). `SBPolicy.resolve` parses them again with a null branch at
`SBPolicy.java:64-70`. After `ForgeRegistries.FLUIDS.containsKey(id)` succeeds, it also
checks whether `getValue(id)` returned null at `:74-82`.

Handling an unknown but syntactically valid registry name is intentional and should remain: server
admins may remove an optional fluid mod. Revalidating syntax after Forge has accepted the config
value, and treating a contained registry key as possibly value-less, are guards against violations of
the config and registry contracts.

Recommendation: retain the unknown-id warning, but rely on the config validator for syntax and on
the Forge registry for a value after `containsKey` succeeds.

### 10. Low: shared fluid placement accepts a null hit that no caller supplies

`FluidPlacement.emptyContents` accepts a nullable `BlockHitResult`, substitutes `Direction.UP`, and
disables fall-through when it is null (`FluidPlacement.java:56-61`). Both call sites pass the real,
non-null hit (`BBFluidLogic.java:238-239`, `SBFluidLogic.java:308`). A future null would also make the
protection check use an invented face instead of the interaction's real face.

Recommendation: require the hit result and derive the face/fall-through decision from it directly.
If a future non-player placement has no hit, that caller should supply an explicit face through a
separate, deliberately shaped API rather than receive `UP` by fallback.

## Guards and fallbacks that should remain

The following are responses to real, supported absence or failure and are not overguarding:

- missing block entities and absent foreign block/item capabilities;
- simulate/execute refusal by foreign fluid handlers;
- null Forge fluid fill/empty sounds, with vanilla sound fallback;
- resource-pack resources that are missing or fail with `IOException` in `BucketMouth` and
  `ClientFluidColors`;
- nullable `ItemOverrides.resolve` results and nullable stack-aware still textures in client model
  code;
- syntactically valid but unknown fluid IDs in server config, especially after an optional mod is
  removed;
- optional FTB Chunks manager availability and ordinary claim denial;
- normal world-operation failures such as collision, rejected fluid placement/pickup, or
  `addFreshEntity` returning false;
- API-declared nullable players in `UseOnContext`, nullable target entities for block protection
  actions, and nullable sound/event players for automation.

## Suggested order

1. Make storage protection contexts mandatory.
2. Remove nullable-player automation conversion and pass explicit hands/contexts.
3. Make own-bucket capability lookup fail fast.
4. Remove the raytrace, nullable-hit, NBT-tag, dispenser-side, and numeric/registry overguards.
5. Simplify Source config resolution while preserving unknown-ID handling.
