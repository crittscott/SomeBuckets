# Some Buckets Build Environment

This document is an orientation to the repository's build environment: its entry points, module layout, version authorities, dependency baselines, and packaging flow. It describes the setup as it exists. It is not build history, a troubleshooting log, release documentation, or a conversation.

`build-env/` is a reference snapshot of every checked-in Gradle build input, including the wrapper. It preserves repository-relative paths so that files can be compared or restored without guessing where they belong. The active files at the repository root and under `common/`, `fabric/`, `forge/`, and `gradle/` remain authoritative; Gradle does not read the copies. Keep the snapshot and the version tables here synchronized whenever an active build file changes.

The snapshot should contain only manually maintained files that define or launch this Gradle build: Gradle scripts, Gradle properties, development launchers, wrapper launchers, and wrapper files. It should not contain caches, generated output, IDE state, run directories, resolved dependency JARs, mod source or resources, loader manifests, or release notes. The wrapper JAR is the sole binary because it is itself a checked-in build launcher.

## Snapshot contents

| Active path | Reference copy |
| --- | --- |
| `settings.gradle` | `build-env/settings.gradle` |
| `build.gradle` | `build-env/build.gradle` |
| `gradle.properties` | `build-env/gradle.properties` |
| `common/build.gradle` | `build-env/common/build.gradle` |
| `fabric/build.gradle` | `build-env/fabric/build.gradle` |
| `fabric/gradle.properties` | `build-env/fabric/gradle.properties` |
| `forge/build.gradle` | `build-env/forge/build.gradle` |
| `forge/gradle.properties` | `build-env/forge/gradle.properties` |
| `forge/src/datagenLauncher/java/com/github/crittscott/somebuckets/datagen/DataRunLauncher.java` | `build-env/forge/src/datagenLauncher/java/com/github/crittscott/somebuckets/datagen/DataRunLauncher.java` |
| `gradlew` | `build-env/gradlew` |
| `gradlew.bat` | `build-env/gradlew.bat` |
| `gradle/wrapper/gradle-wrapper.properties` | `build-env/gradle/wrapper/gradle-wrapper.properties` |
| `gradle/wrapper/gradle-wrapper.jar` | `build-env/gradle/wrapper/gradle-wrapper.jar` |

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, and `forge` subprojects. The root build applies Architectury Loom and the Architectury plugin to each subproject and establishes shared Minecraft mappings and Java settings. `settings.gradle` includes `common` and the loaders named by `enabled_platforms`, currently Fabric and Forge. The build declares no Maven publications. `common` is transformed for each loader; each loader module bundles its transformed common output with Shadow and then remaps the resulting production JAR.

Fabric places the `common` dependency on its compile, runtime, and development classpaths. Forge uses it on the compile classpath and loads common in development through `loom.mods` source sets. Each loader declares its Loom platform in its module's `gradle.properties`. Fabric uses the legacy Mixin annotation processor with the fixed `somebuckets.refmap.json` refmap, referenced by `somebuckets.fabric.mixins.json`.

Production loader JARs are named `somebuckets-fabric-1.20.1-0.8.3.jar` and `somebuckets-forge-1.20.1-0.8.3.jar`. Source and development JARs use the same module, Minecraft-version, and mod-version naming with their respective classifiers. Shared loader metadata placeholders are defined by `sharedModProperties` in the root build; loader scripts add their own dependency and compatibility properties.

Both loader modules call the root `configureGameTests` helper, which creates their dedicated `gametest` source sets over shared scenarios and resources, decodes the shared GameTest structure into generated loader resources, and registers `gametestJavadoc`. That task documents loader tests and package-private shared scenarios under each loader's `build/docs/gametestJavadoc` directory. Forge GameTest metadata is expanded from the same compatibility properties as production metadata. Forge's data run uses `ForgeBucketLootProvider`, a `GlobalLootModifierProvider`, to generate checked-in loot-modifier JSON under `forge/src/generated/resources` from the common loot manifest. Production resource processing reads that directory; it does not run data generation. After changing the manifest, run `:forge:runData` and include the generated resources with the change. Generated data-cache files are ignored. Fabric clears only its development GameTest world before a GameTest server run.

Forge's `runData` task includes an isolated `datagenLauncher` source set. Its `DataRunLauncher` delegates to the task's configured launch chain and explicitly exits after that chain returns, because Architectury's runtime remapper leaves non-daemon workers alive. Launch exceptions produce a nonzero exit status. This launcher is excluded from production and source JARs.

The root `generateDocs` task derives its loader list from `enabled_platforms` and runs each module's production `javadoc` task and each loader's `gametestJavadoc` task. It synchronizes their HTML output into the committed `docs/javadoc/common/`, `docs/javadoc/fabric/`, `docs/javadoc/forge/`, `docs/javadoc/fabric-gametest/`, and `docs/javadoc/forge-gametest/` directories. Separate sections keep overlapping loader class names apart. `docs/index.html` is the hand-maintained landing page; `docs/.nojekyll` allows GitHub Pages to serve generated files unchanged. Documentation is excluded from `build-env/`, and the build does not produce Javadoc JARs.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java configuration. The root build declares a Java 17 toolchain, which Gradle applies by convention to compilation, Javadoc, and Gradle-launched Java executions.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom | `1.17.493` | Minecraft development, mappings, runs, transforms, and remapping |
| Architectury Gradle plugin | `3.5.170` | Common/Fabric/Forge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java toolchain level | `17` | Compilation, Javadoc, and Java execution |
| Minecraft | `1.20.1` | Compile and runtime target |
| Mojang mappings | Official mappings for `1.20.1` | Base mapping layer; no separate mapping version is declared |
| Parchment mappings | `org.parchmentmc.data:parchment-1.20.1:2023.09.03@zip` | Layer over the official mappings |
| Forge | `net.minecraftforge:forge:1.20.1-47.4.0` | Exact Forge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.3` | Fabric loader dependency; also supplies the common annotation dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.92.11+1.20.1` | Fabric runtime and development API |
| FTB Chunks for Forge | `dev.ftb.mods:ftb-chunks-forge:2001.3.8` | Optional, compile-only claim-integration API |
| FTB Chunks for Fabric | `dev.ftb.mods:ftb-chunks-fabric:2001.3.8` | Optional, compile-only claim-integration API |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.3` |
| Fabric and Forge GameTest support mods | `1.0.0` |
| Minecraft compatibility | exactly `1.20.1`; Forge syntax `[1.20.1]`, Fabric syntax `=1.20.1` |
| Forge compatibility | `[47.4.0,48)` |
| JavaFML loader compatibility | `[47,48)` |
| Fabric Loader compatibility | `>=0.19.3` |
| Fabric Java compatibility | `>=17` |
| Fabric API runtime declaration | required, with version `*`; compilation uses `0.92.11+1.20.1` |
| Forge FTB Chunks compatibility | optional `[2001,2002)`; compilation uses `2001.3.8` |
| Fabric FTB Chunks compatibility | suggested with version `*`; compilation uses `2001.3.8` |

## Resolution and version authorities

`gradle.properties` is the authority for the Minecraft, mapping, loader, API, compatibility, integration, and mod versions. Forge's JavaFML compatibility property is `forge_loader_version_range`. The root `build.gradle` pins the three external Gradle plugins and JSR 305, and `gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. The loader scripts consume the root properties rather than restating dependency versions.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal. Explicit project dependency repositories are FTB Maven and Parchment Maven; Loom supplies its standard Minecraft repositories. There is no Gradle version catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the repository. Consequently, the table above records every exact version deliberately declared by the build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
