# Some Buckets — Build Environment Re-Assessment

Re-assessment of `code-review-merged.md` §7 (Build environment) after all other findings in that
review were fixed and the mod's one Architectury API call (`Platform.isModLoaded("ftbchunks")`) was
removed. Verified directly against the current source tree, not against the prior review's text.

## Resolved since the original review

### The §2 "Architectury mandatory but unused" finding is fully resolved

- `forge/src/main/resources/META-INF/mods.toml` no longer declares an `architectury` dependency
  block. The stale comment claiming it was "a real runtime dependency, not just a compile-time one"
  is gone with it.
- `fabric/src/main/resources/fabric.mod.json`'s `depends` block no longer lists `architectury`.
- No `build.gradle` (`common`, `forge`, or `fabric`) declares a `dev.architectury:architectury`
  artifact, and `gradle.properties` no longer defines `architectury_api_version` — a clean removal,
  no orphaned property left behind.
- `dev.architectury` no longer appears in any `.java` file in the project.
- Forge's FTB Chunks presence check now reads `ModList.get().isLoaded("ftbchunks")`
  (`forge/.../SomeBucketsForge.java:72`) — the same idiom Fabric's `SomeBucketsFabric.java` already
  used. The old asymmetry ("Fabric doesn't even use Architectury for this") is gone; both loaders now
  use their own native mod-loaded check.

### Build output tracked in git is resolved

`git ls-files` shows zero files under `common/build/`, `forge/build/`, or any `/build/`/`.gradle/`
path. The "Stop tracking Fabric build output," "Stop tracking common and Forge build output," and
"Stop tracking Fabric Loom cache output" commits closed this out. No `git rm --cached` follow-up
remains outstanding.

## New finding, caused by the Architectury removal

### `maven.architectury.dev` is now a vestigial repository declaration

Still present in both:
- `buildSrc/src/main/groovy/multiloader-common.gradle:19`
- `buildSrc/src/main/groovy/multiloader-loader.gradle:18`

Nothing in the dependency graph declares an artifact from that host anymore — the one thing that
justified it is gone. Caveat, same shape as the original review's own caveat on the dependency
itself: FTB Chunks is itself Architectury-based, so `dev.ftb.mods:ftb-chunks-forge`/`-fabric` (still
`modCompileOnly` in both loader `build.gradle` files) may transitively resolve an artifact only
hosted on that repo. Worth a quick removal-and-build-attempt rather than assuming it's safe to
delete outright — but it's no longer justified by anything declared explicitly, so it's dead weight
pending that check, not a live dependency.

## Unchanged, re-verified accurate

- **Not using the Architectury Loom multiloader template.** Still true; `settings.gradle` only
  includes `common`/`forge`/`fabric`. Forge still on ModDevGradle LegacyForge
  (`net.neoforged.moddev.legacyforge`), Fabric still on Fabric Loom.
- **The `buildSrc` convention-plugin split.** `commonJava`/`commonResources` consumable
  configurations, common compiled twice (once per loader), never a standalone jar or runtime
  dependency — unchanged.
- **Sharp edge for newcomers.** Common's own dependency declarations don't travel with its source;
  every loader module must independently redeclare any library common's source imports. Still
  correctly documented via the `jsr305` comment in `fabric/build.gradle:15-19`.
- **Module boundaries.** Still clean, and now with a stronger guarantee than before: zero
  `dev.architectury` imports anywhere in the tree, not just in `common/`.
- **Repository comments.** Still accurate for every repo except the now-dead
  `maven.architectury.dev` entry above (Sponge's maven for Forge's mixin annotation processor,
  `maven.ftb.dev` for FTB Chunks).
- **`gradle.properties` pinned toolchain versions.** Still match what the `build.gradle` files
  consume; no drift found.
- **`processResources` metadata placeholder expansion.** Unchanged on both loaders, no drift between
  their variable-substitution lists.
- **`org.gradle.daemon=false` and `org.gradle.jvmargs=-Xmx3G`.** Both still present in
  `gradle.properties`, still worth a deliberate yes/no from whoever owns the build. Not addressed by
  this change.
- **`forge/build.gradle:6`'s "No mixin config exists yet" comment.** Still present. One correction to
  the original review's phrasing: it's only in `forge/build.gradle`, not `fabric/build.gradle` (which
  has no such comment). Minor, cosmetic.
- **`FtbChunksProtection.java:57` vs. `FabricFtbChunksProtection.java:42` inconsistency.** Forge's
  adapter still calls `DispenserFakePlayer.get(level)` directly instead of going through
  `AutomationPlayers`, while the Fabric mirror still goes through `AutomationPlayers.get(level)`.
  Untouched by this change; stands exactly as originally described.

## Net effect

The Architectury removal closes out the biggest §7/§2 cross-reference cleanly and symmetrically, and
the build-output tracking issue is already fixed separately. §7's only live items now are the newly
dead `maven.architectury.dev` repository line (pending a build check before deletion) and the
pre-existing minor nits: `daemon=false`, the `-Xmx3G` heap setting, the Forge-only mixin comment, and
the `FtbChunksProtection` indirection inconsistency.
