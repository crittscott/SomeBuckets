# Some Buckets Build Environment

This document is an orientation to the repository's build environment: its entry points, module
layout, version authorities, dependency baselines, and packaging flow. It describes the setup as it
exists. It is not build history, a troubleshooting log, release documentation, or a conversation.

`build-env/` is a reference snapshot of every checked-in Gradle build input, including the wrapper.
It preserves repository-relative paths so that files can be compared or restored without guessing
where they belong. The active files at the repository root and under `common/`, `fabric/`, `forge/`,
and `gradle/` remain authoritative; Gradle does not read the copies. Keep the snapshot and the version
tables here synchronized whenever an active build file changes.

The snapshot should contain only manually maintained files that define or launch this Gradle build:
Gradle scripts, Gradle properties, wrapper launchers, and wrapper files. It should not contain caches,
generated output, IDE state, run directories, resolved dependency JARs, mod source or resources,
loader manifests, or release notes. The wrapper JAR is the sole binary because it is itself a
checked-in build launcher.

## Snapshot contents

| Active path | Reference copy |
| --- | --- |
| `settings.gradle` | `build-env/settings.gradle` |
| `build.gradle` | `build-env/build.gradle` |
| `gradle.properties` | `build-env/gradle.properties` |
| `common/build.gradle` | `build-env/common/build.gradle` |
| `fabric/build.gradle` | `build-env/fabric/build.gradle` |
| `forge/build.gradle` | `build-env/forge/build.gradle` |
| `forge/gradle.properties` | `build-env/forge/gradle.properties` |
| `gradlew` | `build-env/gradlew` |
| `gradlew.bat` | `build-env/gradlew.bat` |
| `gradle/wrapper/gradle-wrapper.properties` | `build-env/gradle/wrapper/gradle-wrapper.properties` |
| `gradle/wrapper/gradle-wrapper.jar` | `build-env/gradle/wrapper/gradle-wrapper.jar` |

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, and `forge` subprojects. The root
build applies Architectury Loom and the Architectury plugin to each subproject, establishes shared
Minecraft mappings and Java settings, and configures Maven publications without a publication
repository. `common` is transformed for each loader; each loader module bundles its transformed
common output with Shadow and then remaps the resulting production JAR.

Both loader modules have dedicated `gametest` source sets. The root build decodes the shared GameTest
structure into generated loader resources. Forge additionally generates global loot-modifier JSON
from the common loot manifest during resource processing. Fabric clears only its development
GameTest world before a GameTest server run.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper
selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java
configuration. Compilation and Gradle-launched Java executions explicitly request a Java 17
toolchain and use Java 17 source, target, and `--release` levels.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom | `1.17.491` | Minecraft development, mappings, runs, transforms, and remapping |
| Architectury Gradle plugin | `3.5.169` | Common/Fabric/Forge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java language and toolchain level | `17` | Compilation, source compatibility, target compatibility, and Java execution |
| Minecraft | `1.20.1` | Compile and runtime target |
| Mojang mappings | Official mappings for `1.20.1` | Base mapping layer; no separate mapping version is declared |
| Parchment mappings | `org.parchmentmc.data:parchment-1.20.1:2023.09.03@zip` | Layer over the official mappings |
| Forge | `net.minecraftforge:forge:1.20.1-47.4.0` | Exact Forge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.3` | Fabric loader dependency; also supplies the common annotation dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.92.11+1.20.1` | Fabric runtime and development API |
| FTB Chunks for Forge | `dev.ftb.mods:ftb-chunks-forge:2001.3.8` | Optional, compile-only claim-integration API |
| FTB Chunks for Fabric | `dev.ftb.mods:ftb-chunks-fabric:2001.3.8` | Optional, compile-only claim-integration API |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations in common and Fabric |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a
JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle
core plugins such as `maven-publish` use Gradle `9.5.1` and therefore have no separate declared
version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion
and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.0` |
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

`gradle.properties` is the authority for the Minecraft, mapping, loader, API, compatibility,
integration, and mod versions. The root `build.gradle` pins the three external Gradle plugins, and
`gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. `common/build.gradle` pins JSR 305;
the loader scripts consume the root properties rather than restating dependency versions.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal.
Explicit project dependency repositories are FTB Maven and Parchment Maven; Loom supplies its
standard Minecraft repositories. There is no Gradle version catalog, dependency-lock state,
dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the
repository. Consequently, the table above records every exact version deliberately declared by the
build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
