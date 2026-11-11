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

The project is a Groovy-DSL Gradle build with `common`, `fabric`, `forge`, and `neoforge` subprojects. The root applies Architectury's `dev.architectury.loom-no-remap` and `architectury-plugin` to each subproject. Minecraft 26.2 is unobfuscated and compilation uses Mojang's names directly. Each loader packages its transformed common output in a production Shadow JAR with no classifier. The plain JAR has the `dev` classifier, `assemble` builds the production JAR, and the loader sources JAR includes common's sources. The Fabric JAR is the artifact intended for Quilt. The build declares no Maven publication.

`common` declares `somebuckets.accesswidener` in the `official` namespace through `loom.accessWidenerPath`. Fabric injects it into its Shadow JAR. NeoForge uses it for development and converts it to an access transformer on `shadowJar`. Forge mirrors the rules in `accesstransformer.cfg`, and `check` runs `verifyCommonAccessMirror` to check the shared entries.

The common `pack.mcmeta` spans resource format 88.0 and data format 107.1. Forge and NeoForge expand the same template at their loader resource roots for development and include one copy in each Shadow JAR. Their GameTest packs declare data format 107.1.

`somebuckets.common.mixins.json` installs shared stack admission and bucket fuel consumption. Fabric and NeoForge declare it in their mod manifests. Forge declares it through Loom's Mixin configuration and the Shadow JAR's `MixinConfigs` manifest attribute. The shared Mixin config declares `JAVA_21` for the Forge-family runtimes, and Fabric's own config declares `JAVA_25`. Forge's production and GameTest compilation use the EventBus validator annotation processor, and its development runs enable `eventbus.api.strictRuntimeChecks`.

Each loader compiles against common through the `common` configuration. Fabric also places it on its runtime and development classpaths. Forge and NeoForge group common's main source set with their own in `loom.mods` and depend on common's compiled classes and processed resources for client, server, GameTest, and data runs. Common uses Fabric Loader as a compile-only dependency for shared `@Environment` annotations. Fabric declares its own loader dependency and uses non-transitive `compileOnly` and `runtimeOnly` declarations for optional Common Protection API integration in development.

Each loader calls `configureGameTests` to add the shared scenarios and structures to its `gametest` source set and register `gametestJavadoc`. Fabric discovers its annotated wrappers, clears its GameTest world before launch, and writes an XML report. Forge and NeoForge use the native `gameTestServer` launch template and load shared test instances and environments from `common/src/gametest/registered-tests`. NeoForge sets `neoforge.enableGameTest`. Shared GameTest players explicitly select Survival, and the Forge-family dispenser actors use Survival abilities. NeoForge enables its milk fluid in the test mod. Forge tests native milk exchange only when that fluid is available.

Forge and NeoForge generate add-table loot modifiers under `src/generated/resources` from the common loot manifest. Forge selects the `data` template and filters the unused `dataClient` template out of the Dev Launch Injector configuration. NeoForge uses `clientData()`. Both data runs use common's processed resources and depend on the GameTest classes because Loom advertises the support mod during launch. An isolated `datagenLauncher` source set provides the JDK-only `DataRunLauncher`, which exits when the launch chain returns and reports launch exceptions with a nonzero exit code. `jar`, `shadowJar`, and `sourcesJar` depend on datagen. The binary archives use the fresh loot output and exclude the processed baseline and datagen cache. This keeps `processResources` available as a launch prerequisite.

`collectJars` copies built production JARs named `<mod>-<loader>-<minecraft>-<version>.jar` to the shared `../jars/` directory. `gradlews.ps1` runs `clean build`, `runGameTestServer`, `generateDocs`, and `collectJars` in order and reports each exit code.

On Windows, `gradlew.bat` is the normal entry point. The build uses a Java 25 toolchain for compilation, Javadoc, and Gradle-launched Java executions. The Gradle JVM and IDE also use JDK 25.

Each subproject has the Java plugin's standard production-source `javadoc` task. Each loader also has a `gametestJavadoc` task over its complete GameTest source set, including the shared package-private scenarios under `common/src/gametest/java`. The root `generateDocs` task derives its loader list from `enabled_platforms`, depends on every module's `javadoc` and each loader's `gametestJavadoc`, and synchronizes their HTML output into the committed `docs/javadoc/<module>/` and `docs/javadoc/<loader>-gametest/` trees; separate sections are required because loader modules contain classes with overlapping fully qualified names. `docs/index.html` is the hand-maintained landing page and `docs/.nojekyll` makes GitHub Pages serve the generated files unchanged. Neither file is a Gradle build input, and generated documentation is intentionally excluded from `build-env/`. The build does not produce Javadoc JARs.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom (no-remap) | `1.17.493` | Minecraft development, runs, and platform transforms |
| Architectury Gradle plugin | `3.5.170` | Common/Fabric/Forge/NeoForge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java toolchain level | `25` | Compilation, Javadoc, and Java execution |
| Minecraft | `com.mojang:minecraft:26.2` | Compile and runtime target |
| Forge | `net.minecraftforge:forge:26.2-65.1.5` | Exact Forge compile and development-run baseline |
| NeoForge | `net.neoforged:neoforge:26.2.0.93` | Exact NeoForge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.5` | Fabric loader dependency; also supplies the common annotation dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.161.0+26.2` | Fabric runtime and development API |
| Common Protection API | `eu.pb4:common-protection-api:2.0.0` | Fabric compile-only optional claim-mod integration, also on Fabric development runs |
| EventBus validator | `net.minecraftforge:eventbus-validator:7.0.1` | Forge production and GameTest annotation processing |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.3` |
| Fabric, Forge, and NeoForge GameTest support mods | `1.0.0` |
| Minecraft compatibility | exactly `26.2` (`[26.2]` on Forge and NeoForge, `=26.2` on Fabric) |
| Forge compatibility | `[65.1.5,66)` |
| Forge JavaFML loader compatibility | `[65,66)` |
| NeoForge compatibility | `[26.2.0.93]` |
| NeoForge JavaFML loader compatibility | `[1,)` |
| Fabric Loader compatibility | `>=0.19.5` |
| Fabric Java compatibility | `>=25` |
| Fabric API runtime declaration | `>=0.161.0+26.2` |
| Quilt compatibility | Unverified on `26.2` |

## Resolution and version authorities

`gradle.properties` is the authority for the Minecraft, Java, pack formats, loader and API dependencies, and mod versions. The root `build.gradle` pins the three external Gradle plugins and JSR 305, and `gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. The loader scripts consume the root properties and use the exact Minecraft release to derive the Forge and NeoForge Minecraft dependency range. `sharedModProperties` derives the source branch and issue URLs from `mod_homepage`; all production loader manifests expand the shared homepage and issue properties, and Fabric also expands the source URL. Common Protection API is an optional Fabric integration.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal. Subprojects declare Maven Central. Fabric adds Nucleoid Maven for Common Protection API, NeoForge adds NeoForge Maven, and Loom supplies the Minecraft repositories. There is no Gradle version catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the repository. Consequently, the table above records every exact version deliberately declared by the build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
