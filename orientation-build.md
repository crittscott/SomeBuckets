<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — build orientation

## Snapshot contents

`build-env/` is a reference snapshot of the extended build environment: Gradle inputs and wrapper, agent instructions, review guidance, launcher script, Git text/ignore rules, and orientation outlines. It preserves repository-relative paths. Active files are authoritative; Gradle never reads the copies. Keep the snapshot and this document synchronized whenever the environment changes.

| Group | Active paths mirrored under `build-env/` |
| --- | --- |
| Root Gradle configuration | `settings.gradle`, `build.gradle`, `gradle.properties` |
| Module Gradle configuration | `common/build.gradle`; each enabled loader's `build.gradle` and `gradle.properties` |
| Wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` |
| Agent and review guidance | `CLAUDE.md`, `AGENTS.md`, `code-review.md` |
| Environment script and Git rules | `gradlews.ps1`, `.gitignore`, `.gitattributes` |
| Orientation outlines | Fixed header and Markdown headings only from `orientation-player.md`, `orientation-code.md`, `orientation-build.md` |

The snapshot excludes caches, generated output, IDE/run state, dependency JARs, mod source and resources (including data providers, access wideners, and GameTest fixtures), loader manifests, and release notes. The wrapper JAR is its sole binary. The orientation files share the exemplar's fixed header and purpose-specific section structure; player and code headings describe Some Buckets' items and transactions rather than SomeStacks' blocks. Full orientation bodies remain only in the active files.

## Build shape

The Groovy Gradle build contains `common`, `fabric`, `forge` modules. `settings.gradle` includes loaders from `enabled_platforms`. Architectury is build-time only: each loader shades its transformed common output, then remaps the production JAR. `common` is not a runtime mod, and no Maven publication is declared. The Fabric artifact also serves Quilt through its Fabric compatibility.

Fabric uses its own access widener and direct Mixin remapping (`useLegacyMixinAp = false`); Forge names `somebuckets-common.mixins.json` in its Loom configuration.

Fabric places common on its compile, runtime, and development classpaths. Forge and NeoForge, where enabled, receive common in development through `loom.mods` rather than adding a second runtime module. Fabric compiles against Architectury API and Common Protection API only for optional protection hooks. They are not bundled or required. Fabric declares Architectury and Nucleoid Maven for these APIs; Forge has no protection-mod dependency.

`configureGameTests` creates each loader's `gametest` source set from shared Java and resources. It includes the checked-in `common/src/gametest/resources/data/somebuckets/structures/empty_9x6x9.nbt` fixture directly and registers `gametestJavadoc` including package-private shared scenarios. Loader manifests and wrappers belong to the loader GameTest source trees. Fabric clears only its development GameTest world before `runGameTestServer`.

Forge uses `ForgeBucketLootProvider` and `somebuckets:add_table` modifiers pointing at shared inject tables. Native data runs write checked-in resources under `<loader>/src/generated/resources`. Fabric injects loot at runtime and has no data run. Forge explicitly selects the `data` environment and template; its unused `dataClient` template is excluded before Loom writes the shared injector config. 

`configureNativeDataGeneration` makes `jar`, `shadowJar`, and `sourcesJar` depend on `runData`. The launch uses the baseline copied by `processResources`; production archive tasks exclude those processed loot modifiers and take fresh generated modifiers directly. This avoids a cycle through `classes` and `processResources`. Datagen caches are excluded from resources and archives. `runData` first prepares `gametestClasses`, since Loom advertises the GameTest dev mod during data runs, and compiles an isolated JDK-only `datagenLauncher` source set from `common/src/datagenLauncher/java`. Its launcher delegates to the configured launch main, then exits after it returns; launch exceptions exit nonzero. Launcher classes and sources are absent from production and source archives.

`generateDocs` synchronizes production and GameTest Javadoc to `docs/javadoc/<module>/` and `docs/javadoc/<loader>-gametest/`. It does not make Javadoc JARs. `collectJars` copies already-built production loader JARs named `<mod>-<loader>-<minecraft>-<version>.jar` into shared `../jars/`, skips missing artifacts, and leaves other versions alone; it does not build.

On Windows, `gradlew.bat` is the wrapper entry point. `gradlews.ps1` runs `clean build`, `runGameTestServer`, `generateDocs`, and `collectJars`, reports each result, and exits nonzero if any step failed. The wrapper pins Gradle; the root build selects Java 17 toolchains for compilation, Javadoc, and Java execution. JDK vendor and patch version and the JVM hosting Gradle are not pinned.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` | Wrapper |
| Architectury Loom | `1.17.493` | Minecraft development and remapping |
| Architectury Gradle plugin | `3.5.170` | Common/loader organization |
| GradleUp Shadow plugin | `9.4.3` | Bundle transformed common code |
| Java toolchain level | `17` | Compilation, Javadoc, and execution |
| Minecraft | `1.20.1` | Compile and runtime target |
| Parchment mappings | `org.parchmentmc.data:parchment-1.20.1:2023.09.03@zip` | Layer over official Mojang mappings |
| Forge | `net.minecraftforge:forge:1.20.1-47.4.0` | Compile and development baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.20.1` | Loader and common annotations |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.92.12+1.20.1` | Fabric API |
| JSR 305 | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only annotations |
| dev.architectury:architectury-fabric | `dev.architectury:architectury-fabric:9.2.14` | Optional compile-only protection API |
| eu.pb4:common-protection-api | `eu.pb4:common-protection-api:1.0.0` | Optional compile-only protection API |

## Artifact and runtime version declarations

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.3` |
| GameTest support mods | `1.0.0` |
| Minecraft compatibility | `[1.20.1] (Forge/NeoForge); =1.20.1 (Fabric)` |
| Forge compatibility | `[47.4.0,48)` |
| Forge JavaFML loader compatibility | `[47,48)` |
| Fabric Loader compatibility | `>=0.20.1` |
| Fabric Java compatibility | `>=17` |
| Fabric API runtime declaration | `*` |

## Resolution and version authorities

`gradle.properties` owns Minecraft, mappings, loader/API versions, compatibility ranges, optional integration versions, and mod metadata. The root `build.gradle` pins Loom, Architectury, Shadow, and JSR 305; `gradle/wrapper/gradle-wrapper.properties` pins Gradle. Loader scripts consume these properties. `sharedModProperties` derives issue and source-branch URLs from `mod_homepage`; production manifests expand the shared project links.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal. All modules declare Parchment Maven; enabled NeoForge modules declare NeoForge Maven. Optional protection repositories are described above. Loom supplies its standard Minecraft repositories. No version catalog, dependency locks, verification metadata, wrapper checksum, exact JDK distribution, or complete transitive dependency lock is maintained.
