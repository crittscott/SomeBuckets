# Some Buckets — Merged Code Review

This document merges three independent static reviews. Repeated findings have been consolidated;
the underlying claims were not independently verified during the merge. No build or test run was
performed.

## Findings

### Low l6 — GameTests and their fixture are included in production source sets

GameTest classes live under `src/main/java`, and the generated test structure is added to
`sourceSets.main.resources` (`build.gradle:117-132`). Unless separately excluded during packaging,
the release jar includes the test suite and fixture.

Use a dedicated GameTest source set or exclude test classes and structures from the production jar
while retaining the development run configuration.

### Low l7 — Release metadata and build files retain MDK scaffolding

`mods.toml` remains mostly Forge template text, the displayed description is only "Get you some
buckets!", and `build.gradle` retains unused template guidance and setup. ForgeGradle and Librarian
also use the nonreproducible version selectors `[6.0,6.2)` and `1.+`. This makes the unreleased
project visibly unfinished and adds avoidable build noise.

Before release, replace the metadata with a useful description and appropriate support/home links,
remove unused template commentary and publishing/Eclipse setup, pin both build plugins, and add a
logo if desired.

## Overall assessment

The core architecture was judged broadly conventional for a Forge 1.20.1 item-only mod: deferred
registration, item fluid capabilities, cauldron interaction maps, registered dispenser behaviors,
server-side mutations, datapack recipes and tags, resource-pack models, server configuration, and a
stable dispenser fake player are expected Minecraft/Forge mechanisms. Persistent state is bounded
and centralized; capability transfers are normally simulated before execution; Mob Bucket state is
removed only after successful entity insertion; and unrelated item NBT is preserved.

The principal Forge integration issue is the nonstandard `FillBucketEvent` behavior. The dispenser
face error affects sided machine compatibility. No normal-path item duplication problem was reported in transfer settlement or
crafting remainders.

The design is generally server-friendly: it adds no tick handlers, custom networking, block
entities, or saved-world scans; collection searches are local and use is action-triggered; and
stored entry counts are bounded. The main efficiency concern is Source transfer iteration.

The existing Source allowlist, Mob blacklist tag, datapack recipes, resource-pack models, and named
fake player provide the admin controls expected for this scope. A command layer or additional saved
admin state was not considered warranted. Standard advancement/stat hooks are the notable
admin-facing interoperability gap.

## Suggested order of work

1. Define and implement valid `FillBucketEvent` semantics and exact targeting.
2. Correct dispenser sided-face selection.
3. Fix baby aging and criteria/statistics.
4. Reduce Source transfer work.
5. Address capability indices, modded-fluid APIs, test packaging, and release metadata.
