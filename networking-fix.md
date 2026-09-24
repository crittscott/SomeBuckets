# Networking Fix Plan

This file is the durable task record for the networking and state-hardening work identified in
`review-networking.md`. Work through the sections in order. After completing each section, mark its
tasks, fill in that section's **Completion update**, and save this file before starting the next
section. Do not build or run the project unless the user explicitly asks; verify changes by source
inspection and record any tests the user should run.

## 1. Centralize component and stack validation

- [x] Define the structural invariants for every Some Buckets data component in one shared authority.
- [x] Make persistent codecs return validation errors instead of allowing invalid values or throwing
      from record constructors.
- [x] Bound `FluidContent.amount` to a positive value no greater than 64,000 mB and reject an empty or
      unregistered fluid.
- [x] Bound `MILK_AMOUNT` to 1–64,000 mB.
- [x] Bound `POWDER_UNITS` to 1–64.
- [x] Bound `CapturedMobs.entities` to 1–8 snapshots while preserving unresolved entity-type IDs on
      disk so temporarily removed mods do not destroy stored mobs.
- [x] Bound `JunkContents.items` to 1–9 nonempty, legal-sized stacks.
- [x] Add enclosing-item validation for Big, Huge, Source, Junk, Trash, and Mob Buckets; component
      codecs alone cannot distinguish their different capacities.
- [x] Apply `JBItem.canStore` during server admission so nested Junk/Trash Buckets, vanilla container
      components, and loader-native item inventories cannot enter through crafted data.
- [x] Before Mob Bucket release, require the resolved type to create a serializable `Mob` and reject
      blacklisted types. Continue treating an unresolved type as inert, not destructive.
- [x] Replace overflow-prone `current + amount <= capacity` calculations with subtraction/room checks.
- [x] Make invalid network input fail closed. Handle invalid persisted data without crashing the
      containing inventory or chunk, preferably by dropping the invalid component or entry once and
      issuing one warning.
- [x] Add or extend source-level/GameTest coverage for every lower bound, upper bound, wrong-item
      capacity, nested-container, blacklist, and overflow case.

**Completion update:** Complete. Persistent and stream codecs now reject invalid structural bounds;
`BucketState` owns item-specific capacity, exclusivity, and nested-container checks; invalid admitted
state has a one-warning fail-closed discard path; fill arithmetic is overflow-safe; and Mob release
rechecks live eligibility after loading the snapshot. Existing state and Mob scenarios cover the
blacklist, while the shared invalid-setter scenario now covers bounds, wrong items, nesting, capacity,
and integer overflow. Source inspected; tests not run per `CLAUDE.md`.

## 2. Send only a Mob Bucket summary over the network

- [x] Keep complete entity snapshots in the persistent `CapturedMobs` representation; do not remove
      `Motion`, inventory, brain, ownership, health, age, UUID, or other release-relevant state.
- [x] Add a persistent opaque content identifier that changes whenever the stored mob list changes.
- [x] Define a compact wire representation containing only the opaque identifier, entity type, and
      count.
- [x] Add a server-session registry mapping issued identifiers to authoritative full
      `CapturedMobs` values.
- [x] When the server encodes a full component, register it and send only the compact summary.
- [x] When a client returns a summarized stack, resolve a known identifier to the authoritative full
      component before accepting it.
- [x] Treat transmitted type and count as display hints and verify them against the resolved server
      value.
- [x] Reject unknown, stale, or forged identifiers and correct the client from server state; never
      persist a summary-only component.
- [x] Scope the registry to one running server, clear it at shutdown, and prevent unbounded stale
      identifier growth without evicting identifiers that clients may still legitimately return.
- [x] Preserve the existing intentional release exceptions: position changes, a replacement UUID on
      collision, and `Bucketable#setFromBucket(true)`.
- [x] Verify all client consumers use only type and count.
- [x] Add a server-to-client-to-server codec test proving that no entity snapshot NBT appears on the
      wire and that the returned server component retains all snapshots exactly.
- [x] Add creative inventory move/copy tests proving that summarized client stacks neither erase nor
      replace authoritative snapshots.
- [x] Add tests for forged identifiers, mismatched hints, disconnect/reconnect, and server restart.

**Completion update:** Complete. `CapturedMobs` now persists a fresh UUID with every changed snapshot
list, while its stream codec sends only UUID/type/count. A server-session registry restores exact full
values only when both hints match; unresolved values remain non-persistable summaries for admission to
reject. The registry is cleared at server start/stop and capped at 65,536 issued values. The shared
codec scenario now checks that marker NBT is absent, a client summary contains only display data, the
return trip restores byte-for-byte snapshot values, and forged hints fail resolution. Source inspected;
tests not run per `CLAUDE.md`.

## 3. Defend server stack-admission and rendering boundaries

- [x] Add one shared server-side validator/normalizer for externally constructed Some Buckets item
      stacks.
- [x] Invoke it at creative inventory admission, using loader-supported hooks where available and the
      smallest cross-loader interception necessary where no hook exists.
- [x] Invoke it before Mob capture/release, Junk/Trash insertion/ejection, fluid/milk/powder
      interaction, and any other path that could consume crafted component state.
- [x] Invoke it for legacy migration and data-driven stack creation where applicable.
- [x] Reject or remove invalid content rather than clamping it into additional items, fluid, powder
      snow, milk, or mobs.
- [x] Cap Junk Bucket render-data preparation to the legal visible capacity as a final client safety
      boundary.
- [x] Refuse nested Junk/Trash render recursion even if malformed data somehow reaches a client.
- [x] Verify that ordinary survival container clicks remain server-authoritative and preserve hidden
      Mob Bucket snapshot state.
- [x] Add malicious-stack regression cases for creative packets, oversized Junk lists, nested storage
      buckets, invalid counts, invalid amounts, and non-Mob entity headers.

**Completion update:** Complete. The shared validator is applied on the first server inventory tick
after creative admission and immediately before every common player interaction, capture/release, and
storage click, plus the shared dispenser base before automation, so a crafted payload cannot turn
malformed state into resources before correction.
Invalid owned components are removed rather than clamped and logged once. Client Junk render-data is
independently limited to nine non-storage-bucket entries. The shared invalid-state scenario now injects
crafted nested Junk and unresolved Mob summary components through the inventory-tick admission path;
the shared automation scenario also covers malformed dispenser state.
Source inspected; tests not run per `CLAUDE.md`.

## 4. Synchronize Fabric Source Bucket policy

- [x] Define a small Fabric S2C custom payload containing the resolved allowed fluid IDs and milk
      permission.
- [x] Register its payload type and client receiver through Fabric API.
- [x] Send the current resolved policy after a player joins and configuration is complete.
- [x] Broadcast the current policy to connected players after `FabricServerConfig.load(true)` on
      reload.
- [x] Apply received policy changes to `SBPolicy` on the client thread.
- [x] Reset the client policy to shipped defaults on disconnect so policy cannot leak between
      singleplayer worlds or remote servers.
- [x] Keep `somebuckets-server.json` server-owned; do not use a client's local file as multiplayer
      policy.
- [x] Preserve Forge and NeoForge's existing `ModConfig.Type.SERVER` synchronization rather than
      adding redundant packets there.
- [x] Add payload round-trip and client-policy replacement tests.
- [x] Record manual checks for join, reconnect, empty allowlist, custom fluid allowlist, milk removal,
      and `/reload` while players are connected.

**Completion update:** Complete. Fabric now sends a bounded resolved-policy payload containing only
registered fluid IDs and the milk flag, once on join and again to every connected player after config
reload. The client replaces `SBPolicy` directly from that snapshot on Fabric's client-thread receiver
and restores shipped defaults on disconnect; no client config file participates in multiplayer policy.
Forge and NeoForge networking remain unchanged. `SBPolicyNetworkGameTests` covers payload round-trip,
replacement, an empty fluid allowlist, independent milk permission, and default reset. Source inspected;
tests not run per `CLAUDE.md`. Manual checks to run on Fabric: join with defaults, reconnect between
servers with different policies, empty allowlist, a registered custom fluid, milk removed, and
`/reload` while players remain connected.

## 5. Make legacy migration atomic, validated, and one-shot

- [x] Detect recognized legacy keys without copying an unrelated `custom_data` compound every
      inventory tick.
- [x] Decode and data-fix all legacy values into detached candidates before mutating the stack.
- [x] Validate candidates through the shared rules from section 1.
- [x] Enforce Junk/Trash capacity and `JBItem.canStore` during migration.
- [x] Enforce Mob count, resolved-type eligibility, serialization, and blacklist rules when the type
      is currently registered.
- [x] Commit all migrated components only after every candidate has succeeded so migration cannot be
      partially applied.
- [x] On failure, remove the recognized legacy keys or move them beneath a non-recognized quarantine
      key, while preserving unrelated custom data.
- [x] Log a failed migration once and perform no DataFixer work on later ticks.
- [x] Remove the permanent retry marker when recognized keys are no longer present.
- [x] Add cases for invalid entity IDs, blacklisted mobs, too many entries, nested containers,
      negative and excessive amounts, DataFixer/codec failure, partial-failure rollback, and unrelated
      custom data.

**Completion update:** Complete. `LegacyBucketMigration` now checks key presence without copying
unrelated custom data, decodes and data-fixes detached content and junk candidates, previews their
combined result through `BucketState.validationError`, and commits only after every candidate passes.
Resolved mob types must create an eligible serializable Mob and pass the live blacklist; unresolved
well-formed ids remain inert and preserved. Failed payloads move beneath
`SomeBucketsLegacyMigrationQuarantine`, lose all recognized keys and the obsolete retry marker, log
once, and require no later DataFixer work. The shared state scenario covers success, unresolved and
invalid entity ids, blacklist and count rejection, Junk/Trash nesting and capacity, negative and
excessive amounts, item decode failure, rollback, unrelated data, marker cleanup, and a no-op second
attempt on quarantined data across all three loaders. Source inspected; tests not run per `CLAUDE.md`.

## 6. Make Junk Bucket inventory prediction deterministic

- [x] Replace `ThreadLocalRandom` layout rerolls with a pure deterministic mixer.
- [x] Derive the next seed from state shared by client and server, such as the previous seed, incoming
      item registry ID, amount moved, and resulting entry count.
- [x] Do not use object identity or an identity-based Java hash.
- [x] Preserve the observable rule that a successful insertion changes the layout.
- [x] Run empty-cursor FIFO extraction and cursor assignment on both logical sides instead of returning
      client success without mutation.
- [x] Keep server authority and ordinary menu correction for genuinely stale or rejected predictions.
- [x] Cover both bucket-on-slot and cursor-on-bucket insertions, compatible merges, new entries,
      partial moves, and FIFO extraction.
- [x] Verify identical initial client/server states produce identical bucket, slot, cursor, and layout
      seed results.

**Completion update:** Complete. Junk/Trash insertion seeds now advance through a pure 64-bit mixer of
the previous seed, incoming registry-id characters, amount moved, and resulting entry count; the
transition explicitly cannot return the previous seed. Inventory extraction now removes the FIFO
entry and assigns the cursor on both logical sides, while server-only admission remains authoritative
for malformed or stale state. The shared storage scenario runs identical prediction/authority
sequences for bucket-on-slot and cursor-on-bucket gestures, compatible merges, new entries, partial
moves, and FIFO extraction, then compares full bucket, slot, cursor, and seed state on all loaders.
Source inspected; tests not run per `CLAUDE.md`.

## 7. Correct sound and particle inconsistencies

- [x] Preserve the working ordinary Forge and NeoForge vaporization prediction paths.
- [x] Change shared `FluidPlacement.evaporate` to a null-source server broadcast so the acting player
      hears aquatic Mob Bucket evaporation on every loader.
- [x] Replace eight one-particle sends with one eight-particle send while keeping approximately the
      same block-sized visual spread.
- [x] Replace Forge and NeoForge `BucketSounds.playBucketSound`'s split broadcast plus actor
      notification with one null-source broadcast at the actual interaction position.
- [x] Retain `notifyActor` only where a loader fluid utility already broadcasts to everyone except the
      actor and client prediction does not supply the actor's sound.
- [x] Check ordinary Big/Huge/Source evaporation separately from aquatic Mob Bucket release; they use
      different paths.
- [x] Record manual checks for actor and nearby-player sound count, sound position, dispensers, and
      ultra-warm evaporation on all three loaders.

**Completion update:** Complete. Shared aquatic-placement evaporation now broadcasts the hiss once
with a null source and emits one eight-particle burst centered over the block with block-sized spread.
Forge and NeoForge block-store and cauldron sounds likewise use one null-source broadcast at the
interaction position. Their ordinary arbitrary-fluid placement paths retain client vaporization
prediction and retain `notifyActor` only after non-vaporizing `FluidUtil.tryPlaceFluid`, whose loader
utility supplies the other listeners. Fabric's loader-owned ordinary path remains distinct. Source
inspected; tests not run per `CLAUDE.md`. Manual checks to run on Forge, NeoForge, and Fabric: one
sound for actor and nearby player at the target position, dispenser sound position/count, ordinary
Big/Huge/Source ultra-warm evaporation, and aquatic Mob Bucket ultra-warm release with one hiss and
an eight-particle smoke burst.

## 8. Reconcile documentation and perform the final review

- [x] Update `as-built.md` in place and keep it within its 150-line/12k-character budget.
- [x] Correct the NeoForge configuration description: the registered `SERVER` configuration is
      synchronized and supports the documented per-world override behavior for NeoForge 1.21.3.
- [x] Document the Mob Bucket contract: full persistent snapshots, compact network summary, and safe
      creative round-trip.
- [x] Document component-validation and server-admission ownership without restating implementation
      branches.
- [x] Update `review-networking.md` to reflect the completed fixes rather than leaving resolved
      findings presented as current defects.
- [x] Correct the review's obsolete legacy retry/log-flood claim.
- [x] Correct the statement that Mob release overwrites `Motion`; it overwrites position only.
- [x] Distinguish working ordinary-fluid evaporation from the former aquatic Mob Bucket sound gap.
- [x] Replace “nothing is driven by ticks” with the accurate statement that there is no recurring
      steady-state network traffic.
- [x] Update `player-view.md` only if malformed-data handling or another fix becomes player-visible.
- [x] Re-read every changed file and record source-level verification here.
- [x] List the exact loader GameTests and manual multiplayer checks for the user to run; do not run a
      build or game test without explicit authorization.

**Completion update:** Complete. `as-built.md` is 131 lines and 8,838 characters; it now describes
the current loader seams, validation/admission ownership, Mob persistence/wire split, Fabric policy,
atomic migration, deterministic prediction, and synchronized NeoForge `SERVER` configuration.
`review-networking.md` is a post-fix review, and `player-view.md` now gives the player-visible
per-loader config locations and synchronization behavior. A final source inspection re-read the
changed component, registry, validation, admission, item, migration, networking, sound/particle,
GameTest-wrapper, and documentation files. It found and closed one remaining malformed-state path in
`BucketDispenseBehavior`, with shared cross-loader coverage.

Exact focused GameTests to run on Fabric, Forge, and NeoForge are
`StateGameTests.negative_content_setters_fail_without_mutation`,
`StateGameTests.entity_snapshot_network_sync_preserves_payloads`,
`StateGameTests.legacy_migration_is_atomic_validated_and_one_shot`,
`StorageBucketGameTests.junk_bucket_inventory_prediction_is_deterministic`,
`AutomationGameTests.dispenser_malformed_state_is_discarded_before_automation`,
`MBGameTests.blacklisted_boss_is_not_capturable`, and
`MBGameTests.release_restores_state_and_uuid_and_normalizes`. Fabric additionally runs
`SBPolicyNetworkGameTests.source_policy_payload_round_trip_and_client_replacement`.

Manual multiplayer checks: on Fabric, join with defaults; reconnect between servers with different
policies; test an empty allowlist, one registered custom fluid, and milk removed; then `/reload` with
players connected. On every loader, move and copy a filled Mob Bucket in creative and release it to
verify saved state survives; with actor and nearby observer, check exactly one sound at the target for
block-store, cauldron, and dispenser operations; and separately check ordinary Big/Huge/Source and
aquatic Mob Bucket ultra-warm evaporation, including one hiss and the aquatic eight-particle burst.
Source inspected; no build, game launch, or GameTest was run per `CLAUDE.md`.

## Final acceptance checklist

- [x] Clients never receive captured entity snapshot NBT or capture coordinates.
- [x] Creative Mob Bucket movement preserves authoritative snapshots.
- [x] Malformed or malicious component payloads cannot exceed capacities, nest forbidden containers,
      overflow arithmetic, release forbidden entities, or drive unbounded rendering.
- [x] Fabric clients use the connected server's Source Bucket policy after join and reload.
- [x] Legacy migration performs at most one validated, atomic attempt per recognized payload.
- [x] Junk Bucket inventory interactions predict the same state on client and server.
- [x] Evaporation and bucket sounds reach the correct listeners once, from the correct position.
- [x] Documentation describes the resulting implementation accurately.
