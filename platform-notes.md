# Platform notes

Facts about how specific Minecraft versions, loaders, and libraries behave, learned the hard way. General rules belong in `CLAUDE.md` and `code-conventions.md`; this file holds what is true only of a particular platform. Nothing here is about one mod.

Every fact is scoped to the version it was learned on unless it says otherwise. Do not assume it holds on another version; check it when porting.

Each fact ends with how it is known:

- **[tested]**: a build, gametest, or in-game check depended on it.
- **[source]**: read from published source (Fabric API, a library, a JVM spec).
- **[recalled]**: from memory, unconfirmed. Treat as a hypothesis.

Upgrade the mark when a build or test settles a fact, and correct or delete a fact that turns out wrong.

## Minecraft

### Rendering

- 1.20.1 and 1.21.1 draw items immediately and have no use for item render state code; 1.21.4 and later use it. [tested on 1.20.1]
- 1.20.1: the render pose holds the view rotation `V = Rx(pitch) Ry(yaw + 180)`, and `cameraOrientation` is `Ry(-yaw) Rx(pitch)`. The true inverse of `V` is `cameraOrientation * Ry(180)`; without the 180 degree turn, pitch doubles instead of canceling. [tested in game; the matrices themselves are recalled]
- The view convention differs by version, so derive what each version's pose holds instead of copying a fix. 1.21.1, 1.21.4, and 1.21.8 pass `cameraOrientation`; 1.21.11 and later have been treated as world-space poses. [recalled]
- `PoseStack.mulPose(Quaternionf)` multiplies the pose by the quaternion without modifying it, so one quaternion can be shared across draws. [recalled]
- `ItemColor` is asked once per tinted quad, and `item/generated` makes a quad for each edge pixel run, so a tint callback can run dozens of times per item per frame. [recalled]
- 1.21.11: item and feature rendering submits nodes to a `SubmitNodeCollector`, and a `FeatureRenderDispatcher` draws them later; rendering an item outside the normal frame needs its own submit-node storage and feature dispatcher, followed by a flush of the item buffers. [source: [NeoForge rendering migration reference](https://docs.neoforged.net/docs/1.21.11/rendering/feature/) and published API documentation; signatures only, client behavior not verified on this target]
- 1.21.11: copying a GPU texture into a buffer for CPU readback completes asynchronously through a callback; `RenderSystem.queueFencedTask` callbacks run between frames, and `Minecraft.schedule` always enqueues, unlike `execute` called on the render thread. [recalled]

### Interaction and items

- 1.21.4: `MobBucketItem.playEmptySound(Player, LevelAccessor, BlockPos)` is protected; the access widener opens it. [compiled] 1.21.8: its first parameter is `LivingEntity`. [compiled]
- 1.20.1: `Player.mayUseItemAt(pos, face, stack)` tests the block at `pos.relative(face.getOpposite())`. Callers pass the position next to the clicked block: `clicked.relative(face)`. [tested]
- 1.20.1: `ServerPlayer.canHarmPlayer` already includes the server's PvP setting. [tested]
- 1.20.1: the server swings the arm when the interaction result is `SUCCESS`, not `CONSUME`. Server paths return `CONSUME`. [recalled]
- `ItemStack.useOn` awards `Stats.ITEM_USED`, and `ServerPlayerGameMode.useItemOn` fires `ITEM_USED_ON_BLOCK`. A cancelled click event returns before both, so a handler that takes over must award them itself. [recalled]
- `LivingEntity.heal` is called without a health check by some vanilla code, for example horse regeneration. [recalled]
- `Player` overrides `LivingEntity.actuallyHurt` without calling super, so an injection on `LivingEntity` misses players. [tested]
- `dropCustomDeathLoot` and the loot table drop run only inside the `shouldDropLoot() && doMobLoot` gate in `dropAllDeathLoot`. A drop rolled by mod code is not covered by that gate. [tested]
- `LivingEntity.hurt` returns false on the client before `actuallyHurt`, and the client's trade menu uses `ClientSideMerchant`, not an `AbstractVillager`. [recalled]
- `ItemStack.isSameItemSameTags` is the 1.20.1 name; later versions need the component equivalent. [tested on 1.20.1]
- The name of `DamageSource.isDirect` appears to differ between release lines. [recalled]

### Data and block entities

- 1.21.x and later: the client ignores a block-entity data packet whose tag is empty (vanilla's handler and NeoForge's `onDataPacket` both check `isEmpty`), so state that clears to "save nothing" must still put something in the update tag or clients keep the old state. 1.20.1 behaves the same: an empty update tag reaches its client as no tag, which the client skips. [recalled; a fix depending on it tested in game on 1.21.1/1.21.4]
- 1.20.1: `BlockItem` merges a stack's `BlockEntityTag` into the block entity and calls `load`, and heads are not an op-only block entity. A creative player needs no operator rights to supply that NBT, so a `load` that keeps a whole compound stores and rebroadcasts whatever was sent. [recalled]
- 1.21.11: `PackMetadataSection` is `(Component description, InclusiveRange<PackFormat> supportedFormats)`, `SharedConstants.getCurrentVersion().packVersion(PackType)` returns a `PackFormat` with `major()`/`minor()`, and `PackMetadataSection.SERVER_TYPE` is a `MetadataSectionType` record carrying the section name (`pack`) and the data-pack codec, which writes `min_format`/`max_format`. [recalled; `packVersion(...).major()/minor()` compiled on 1.21.11]
- A non-special recipe needs an unlock advancement, or `doLimitedCrafting` blocks it and the recipe book never offers it. The path is `data/<ns>/advancements/recipes/...` on 1.20.1 and uses the `advancement` directory on later versions. [tested on 1.20.1]

### Commands

- 1.20.1: a distant command spawn can return true from `tryAddFreshEntityWithPassengers` while an immediate `getEntity(UUID)` does not return that entity. Treating this lookup as an insertion failure discards accepted distant spawns. [tested in game: fixed-distance cutoff in Minecraft and Animania authoring grids; diagnostic confirmed this branch]
- Brigadier evaluates `requires` at parse time for each execution, but the client's copy of the tree is only resent on login, reload, and permission change, not on `/gamemode`. Mutable state in `requires` gives stale tab completion. [recalled]
- `StringArgumentType.word()` stops at `:`, and `FloatArgumentType` accepts a dot only, whatever the locale. [recalled]

### Text

- `Component.translatable` sends only the key, and the receiver resolves it. A client without the mod sees the raw key; use `translatableWithFallback`. [tested]
- Lang files may not be available on a dedicated server under Forge, so resolving a lang key at reload time can yield the raw key. [recalled]

## Forge

- 1.20.1: `LivingDropsEvent` fires after the loot gate in `dropAllDeathLoot`, whatever `doMobLoot` says, because it also carries equipment drops. A listener must check the game rule itself. [tested]
- 1.20.1: `LivingDamageEvent` fires from both `actuallyHurt` implementations. `LivingHealEvent` fires before the heal. [tested]
- 1.20.1: `ForgeEventFactory.onLivingConvert` is patched into the hand-built lightning conversions, so `LivingConversionEvent` covers them. [tested]
- 1.20.1: `TradeWithVillagerEvent` is posted from `AbstractVillager.notifyTrade`. [tested]
- 1.20.1: `onPlayerDestroyItem` is called from `Player.interactOn` and similar callers, not `hurtAndBreak`. A cancelled interaction that breaks a tool must call it itself. [tested]
- 1.20.1: `RightClickBlock` fires before vanilla's spectator check; so does `EntityInteractSpecific`. [recalled]
- 1.21.4: Forge `ForgeHooks.onPlaceItemIntoWorld` and NeoForge `CommonHooks.onPlaceItemIntoWorld` arm block-snapshot capture around `useOn` for any item but `BucketItem`. After a consuming result they post place events for the captured snapshots; on refusal they restore only the blocks and the held stack, not inventory changes made meanwhile. A `useOn` that uses a block without building must suspend that capture. [recalled]
- Forge and NeoForge `FluidType` have a stack-aware `getSound(FluidStack, SoundAction)` beside `getSound(SoundAction)`. [recalled]
- 1.20.1: `EntityInteractSpecific` fires before an armor stand's own `interactAt`. [tested]
- 1.20.1: `getPersistentData` has no non-creating read. [recalled]
- 1.20.1: `SimpleChannel` prepends a one-byte handler index to the encoded body, which counts against the vanilla packet limit. `IndexedMessageCodec.consume` validates packet direction before decoding. [settled during review; not re-verified]
- 1.20.1: a loot modifier is the normal way to change existing loot, because Forge has no way to add a function to an existing pool. [recalled]
- 1.20.1: `AddLayers.getRenderer` casts generically and throws `ClassCastException` for non-living renderers. Walk `EntityRenderDispatcher.renderers`, widened by an access widener, instead. [tested]
- There is no Forge `FakePlayer` after 1.20.1.
- 1.21.11: Forge fires `onPlayerDestroyItem` from `hurtAndBreak`, so a tool worn out by mod code needs no separate report, while a stack emptied by `ItemStack.consume` does; report that one through `ForgeEventFactory.onPlayerDestroyItem(Player, ItemStack, InteractionHand)`. [recalled; Forge gametests in a sibling mod depend on it]

## Fabric

- `ServerLivingEntityEvents.MOB_CONVERSION` fires only from `Mob.convertTo`. On 1.20.1 villager-to-witch and pig-to-zombified-piglin lightning conversions, and the mooshroom shear, build the new mob by hand and need their own hook. The villager path ends in `addFreshEntityWithPassengers`, the pig path in `addFreshEntity`. [tested]
- `ClientPacketListener`'s send-command methods are injected and run through the client dispatcher, so calling `sendCommand` from a client command recurses without end. To reach the server from a client command, throw one of the two exception types Fabric forwards, such as `BUILT_IN_EXCEPTIONS.dispatcherParseException()`. [source]
- Fabric API's client-command merge (`ClientCommandInternals.copyChildren`, all versions through 26.x) adds each copied node to the received server tree before copying its children. When the server also sends that root literal, Brigadier's same-name merge keeps the server node and the client subtree is attached to a discarded copy, so chat parsing and completion lack it while execution through Fabric's own dispatcher is unaffected. Add whole client trees to `ClientPacketListener.getCommands()` after `handleCommands` instead. [source]
- `UseEntityCallback` fires for both the positioned and the plain interaction packet. [source]
- The use callbacks fire before vanilla's spectator check. [recalled]
- Fabric API loads mod language files on the server, so `Language.getInstance()` can supply fallback text there. [source]
- `ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS` is the counterpart of Forge's `OnDatapackSyncEvent` and fires per player on reload; `END_DATA_PACK_RELOAD` is the once-per-reload signal. [tested]
- `getAttachedOrCreate` on a persistent attachment makes a read write to the save. Use `getAttached` for reads. [tested]
- For a tracking broadcast, build one packet with `ServerPlayNetworking.createS2CPacket` and send it with `ServerChunkCache.broadcastAndSend`, the mechanism Forge's `TRACKING_ENTITY_AND_SELF` uses. [tested on earlier versions]
- 1.21.11: the tracking-and-self send is `ServerChunkCache.sendToTrackingPlayersAndSelf(Entity, Packet)`; 26.1.2 has no `broadcastAndSend`, and the 1.21.11 name is carried from there. [recalled]
- 1.21.11: `EntityTrackingEvents.START_TRACKING` fires at the tail of `ServerEntity.addPairing` (fabric-networking-api-v1 `ServerEntityMixin`, `@At("TAIL")`), after the entity's spawn packet is sent, so a packet about the entity sent from it reaches a client that already has the entity. `STOP_TRACKING` fires at the head of `removePairing`. [source]
- 1.21.11: Fabric API's `LootTableEvents.MODIFY_DROPS` runs after any loot table rolls its drops, with the table, the `LootContext`, and the mutable drop list, so one listener can change chosen stacks of a finished roll instead of adding a function to every pool. [recalled]
- `ClientPlayConnectionEvents.JOIN` passes the live `ClientPacketListener`; use it instead of looking one up. [tested]
- A login query reply can carry a version, which distinguishes a version mismatch from a missing mod. [tested]
- 1.21.11: Fabric API's GameTest run flag `fabric-api.gametest.report-file` writes a JUnit-style report to the named file. [tested]

## Mixin

- In a multi-target `@Mixin({A.class, B.class})`, every injection must exist on every target. A method only `A` declares fails at launch, so split the class. [reasoned, not run]
- An injector with a method list counts matches across the list, so a name absent on one loader does not fail `defaultRequire: 1` while another name matches. [tested]
- `@Pseudo` tolerates a missing target class only, not a missing method in a class that exists. [recalled]
- A Mixin config's package may hold only Mixins. [tested]
- `require`, not `@Pseudo`, decides what a miss does. A config with `required: false` and `defaultRequire: 0` lets an optional integration degrade to nothing. [tested]

## Architectury and Loom

- 1.21.11: with the legacy Mixin annotation processor, common Mixins compile in `common/src/main/java` and need their own refmap, named by `loom.mixin.defaultRefmapName` in common and by the `refmap` key in the common Mixin config: Fabric resolves them against intermediary names, and the Fabric module's refmap covers only Mixins compiled there. Forge and NeoForge run on Mojang names, so their jars exclude that refmap and their dev runs set `mixin.env.disableRefMap`. With `loom.mixin.useLegacyMixinAp = false`, no refmap is generated and Loom remaps Mixin targets in place when it remaps the jar. Either way a mistake surfaces only at runtime, so check a production-jar launch after changing this. [recalled; builds on both setups compile, production-jar launch not checked here]
- `@ExpectPlatform` needs a per-loader class, so its one-line forwarding is forced. [tested]
- Forge and NeoForge receive the Fabric access widener converted to an access transformer at remap time; one file serves every loader. [tested] Loom's `forge { convertAccessWideners = true; extraAccessWideners.add <widener name> }` does the conversion by filling `remapJar`'s `atAccessWideners`; NeoForge sets `atAccessWideners` directly because whether its extension has the same switches is unknown. [recalled] Loom cannot convert into a jar that already contains an access transformer, so a Forge module with its own transformer entries mirrors the widener's entries by hand instead. [recalled]
