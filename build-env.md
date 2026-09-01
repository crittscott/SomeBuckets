# Some Buckets Build Environment

This document is an orientation to the repository's build environment: its entry points, module
layout, version authorities, dependency baselines, and packaging flow. It describes the setup as it
exists. It is not build history, a troubleshooting log, release documentation, or a conversation.

`build-env/` is a reference snapshot of every checked-in Gradle build input, including the wrapper.
It preserves repository-relative paths so that files can be compared or restored without guessing
where they belong. The active files at the repository root and under `common/`, `fabric/`, `forge/`,
`neoforge/`, and `gradle/` remain authoritative; Gradle does not read the copies. Keep the snapshot
and the version tables here synchronized whenever an active build file changes.

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
| `fabric/gradle.properties` | `build-env/fabric/gradle.properties` |
| `forge/build.gradle` | `build-env/forge/build.gradle` |
| `forge/gradle.properties` | `build-env/forge/gradle.properties` |
| `neoforge/build.gradle` | `build-env/neoforge/build.gradle` |
| `neoforge/gradle.properties` | `build-env/neoforge/gradle.properties` |
| `gradlew` | `build-env/gradlew` |
| `gradlew.bat` | `build-env/gradlew.bat` |
| `gradle/wrapper/gradle-wrapper.properties` | `build-env/gradle/wrapper/gradle-wrapper.properties` |
| `gradle/wrapper/gradle-wrapper.jar` | `build-env/gradle/wrapper/gradle-wrapper.jar` |

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, `forge`, and `neoforge`
subprojects. The root build applies Architectury Loom and the Architectury plugin to each subproject
and establishes shared Minecraft mappings and Java settings. The build declares no Maven publication;
the modules expose no stable public API and nothing consumes them through Maven. `common` is
transformed for Fabric, Forge, and NeoForge; each loader module bundles its
transformed common output with Shadow and then remaps the resulting production JAR. The Fabric JAR
is also the Quilt artifact; there is no separate Quilt subproject or production JAR.

Fabric, Forge, and NeoForge each have a dedicated `gametest` source set wired into a
`runGameTestServer` run. The root build decodes the shared GameTest structure into their generated
loader resources. Forge and NeoForge additionally generate global loot-modifier JSON from the common
loot manifest during resource processing (`forge:` and `neoforge:` namespaces respectively). Fabric
clears only its development GameTest world before a GameTest server run. All three loader modules are
implemented runtime mods; `common` is transformed for each and bundled into its production JAR.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper
selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java
configuration. Compilation and Gradle-launched Java executions explicitly request a Java 21
toolchain and use Java 21 source, target, and `--release` levels.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom | `1.17.491` | Minecraft development, mappings, runs, transforms, and remapping |
| Architectury Gradle plugin | `3.5.169` | Common/Fabric/Forge/NeoForge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java language and toolchain level | `21` | Compilation, source compatibility, target compatibility, and Java execution |
| Minecraft | `1.21.1` | Compile and runtime target |
| Mojang mappings | Official mappings for `1.21.1` | Base mapping layer; no separate mapping version is declared |
| Parchment mappings | `org.parchmentmc.data:parchment-1.21.1:2024.11.17@zip` | Layer over the official mappings |
| Forge | `net.minecraftforge:forge:1.21.1-52.1.16` | Exact Forge compile and development-run baseline |
| NeoForge | `net.neoforged:neoforge:21.1.248` | Exact NeoForge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.3` | Fabric loader dependency; also supplies the common annotation dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.116.15+1.21.1` | Fabric runtime and development API; used by the Fabric artifact on Quilt |
| FTB Chunks for Fabric | `dev.ftb.mods:ftb-chunks-fabric:2101.1.21` | Optional, compile-only claim-integration API |
| FTB Chunks for NeoForge | `dev.ftb.mods:ftb-chunks-neoforge:2101.1.21` | Optional, compile-only claim-integration API |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a
JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle
core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared
version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion
and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.0` |
| Fabric, Forge, and NeoForge GameTest support mods | `1.0.0` |
| Minecraft compatibility | exactly `1.21.1`; Forge and NeoForge syntax `[1.21.1]`, Fabric syntax `=1.21.1` |
| Forge compatibility | `[52.1.16,53)` |
| Forge JavaFML loader compatibility | `[52,53)` |
| NeoForge compatibility | `[21.1.248,22)` |
| NeoForge JavaFML loader compatibility | `[1,)` |
| Fabric Loader compatibility | `>=0.19.3` |
| Fabric Java compatibility | `>=21` |
| Fabric API runtime declaration | required, with version `*`; compilation uses `0.116.15+1.21.1` |
| Fabric FTB Chunks compatibility | suggested with version `*`; compilation uses `2101.1.21` |
| NeoForge FTB Chunks compatibility | optional `[2101,2102)`; compilation uses `2101.1.21` |
| Forge FTB Chunks compatibility | none; no Forge 1.21.1 artifact is available |
| Quilt compatibility | the Fabric artifact is expected to run through Quilt's Fabric compatibility |

## Resolution and version authorities

`gradle.properties` is the authority for the Minecraft, mapping, loader, API, compatibility,
integration, and mod versions. The root `build.gradle` pins the three external Gradle plugins and
JSR 305, and `gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. The loader scripts
consume the root properties rather than restating dependency versions.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, NeoForge Maven, and the Gradle
Plugin Portal. Explicit project dependency repositories are FTB Maven, Parchment Maven, and
NeoForge Maven; Loom supplies its standard Minecraft repositories. There is no Gradle version
catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or
wrapper-distribution checksum in the repository. Consequently, the table above records every exact
version deliberately declared by the build, but it is not a lock of every transitive artifact
selected by Gradle and Loom.
