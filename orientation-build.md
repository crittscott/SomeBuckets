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
| Agent and review guidance | `CLAUDE.md`, `AGENTS.md`, `code-review.md`, `code-conventions.md`, `code-review-decisions.md` |
| Platform facts | `platform-notes.md` |
| Environment script and Git rules | `gradlews.ps1`, `.gitignore`, `.gitattributes` |
| Orientation outlines | Fixed header and Markdown headings only from `orientation-player.md`, `orientation-code.md`, `orientation-build.md` |

The snapshot excludes caches, generated output, IDE/run state, dependency JARs, mod source and resources (including data providers, access wideners, and GameTest fixtures), loader manifests, and release notes. The wrapper JAR is its sole binary. Full orientation bodies remain only in the active files.

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, `forge`, and `neoforge` subprojects. The root build applies Architectury Loom and the Architectury plugin to each subproject and establishes shared Minecraft mappings and Java settings. The build declares no Maven publication; the modules expose no stable public API and nothing consumes them through Maven. `common` is transformed for Fabric, Forge, and NeoForge; each loader module bundles its transformed common output with Shadow and then remaps the resulting production JAR. The Fabric JAR is also the Quilt artifact; there is no separate Quilt subproject or production JAR.

`common` declares the access widener `common/src/main/resources/somebuckets.accesswidener` through `loom.accessWidenerPath`; Fabric and NeoForge point their own `loom.accessWidenerPath` at common's; NeoForge, which reads access transformers rather than wideners, also names that widener in `atAccessWideners` so its development runs apply it. Forge keeps its one common entry in `accesstransformer.cfg` beside three Forge-only client entries because Loom cannot convert into a JAR that already contains an access transformer; `verifyCommonAccessMirror`, wired into `check`, rejects drift between the common widener and that mirror.

Fabric and NeoForge use loader-supplied production pack metadata. Forge bundles `pack.mcmeta` declaring server data format 88 so its mod pack loads. Forge and NeoForge GameTest packs also declare format 88.

Shared GameTest players explicitly select Survival mode. Forge and NeoForge dispenser actors use Survival abilities. The Forge test mod leaves Forge's optional milk fluid disabled; ordinary milk-bucket behavior is covered, while native milk-fluid exchange runs only when the loader supplies that fluid. NeoForge enables its milk fluid in the test mod.

Common and Fabric disable the legacy Mixin annotation processor; Loom remaps Mixin targets in place without refmaps. `somebuckets.common.mixins.json` installs shared stack admission on every loader: Fabric declares it in `fabric.mod.json`, NeoForge in its mod manifest, and Forge through Loom's Mixin configuration and the production Shadow archive's `MixinConfigs` manifest attribute. Forge and NeoForge otherwise use their loader-specific Loom setup. Forge's production and GameTest compilation use the EventBus validator annotation processor, and its development runs enable `eventbus.api.strictRuntimeChecks`.

Each loader module compiles against common through the `common` configuration. Fabric also places it on its runtime and development classpaths; Forge and NeoForge runs receive common only through their `loom.mods` source sets, because a second copy would split its packages across two modules. Every module has Fabric Loader on its compile-only classpath so shared `@Environment` annotations resolve during compilation and Javadoc; only Fabric declares it as a runtime loader dependency. Fabric additionally compiles against the Common Protection API (`modCompileOnly`) so claim mods that ship it can be consulted when present, and puts it on its development runs (`modLocalRuntime`) so that integration is exercised there; both declarations are non-transitive, and the API is neither bundled nor required at runtime. Fabric, Forge, and NeoForge each call the root `configureGameTests` helper, which creates a `gametest` source set over the shared scenarios and structure resources and registers `gametestJavadoc`; each loader wires that source set into a `runGameTestServer` run. Forge and NeoForge also include `common/src/gametest/registered-tests`, which holds their shared vanilla test-instance and environment definitions. Fabric discovers its own annotated wrappers. Its Happy Ghast fixtures enable sky access so the adult ghast and rider have clearance above the structure, matching the native test definitions. Forge's GameTest run selects the native `gametestserver` environment and `gameTestServer` template. Forge's data run explicitly selects the `data` environment and Forge `data` template. After project evaluation, the Forge script removes the unused `dataClient` userdev template from Loom's template collection: Loom serializes every template into the shared Dev Launch Injector configuration, and the bundled injector rejects the `dataclient` section even when another run is selected. The remaining templates supply the ordinary client/server, GameTest, and data launch arguments. NeoForge uses `clientData()`. Forge and NeoForge have data runs and loader-native `GlobalLootModifierProvider` implementations that derive checked-in add-table modifier resources under `src/generated/resources` from the common loot manifest. Native `runData` depends on `gametestClasses` because Loom advertises the GameTest dev mod to the loader even during data runs; this prepares test classes and metadata without running tests. It uses the checked-in resource baseline to launch; `jar`, `shadowJar`, and `sourcesJar` depend on it, so ordinary builds regenerate automatically. Production archive tasks omit the baseline processed loot modifiers and include the fresh generated files directly. This avoids making `processResources` depend on a data run that itself needs processed metadata and compiled classes. Datagen `.cache/` is excluded from resources and archives. Forge and NeoForge each compile an isolated `datagenLauncher` source set from `common/src/datagenLauncher/java`. Its JDK-only `DataRunLauncher` delegates to the configured launch main and exits after it returns, releasing lingering runtime workers; launch exceptions exit nonzero. This source set is not registered as a mod and is excluded from production and source archives. Its Java source is excluded from `build-env/` like other source code. All three loader modules are implemented runtime mods; `common` is transformed for each and bundled into its production JAR.

`collectJars` copies already-built production loader JARs named `<mod>-<loader>-<minecraft>-<version>.jar` into the shared `../jars/`, skips missing artifacts, and leaves other versions alone. It does not build. `gradlews.ps1` runs `clean build`, `runGameTestServer`, `generateDocs`, and `collectJars` in sequence, prints each exit code as a pass/fail result, and exits nonzero if any step failed.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java configuration. The root build declares a Java 21 toolchain, which Gradle applies by convention to compilation, Javadoc, and Gradle-launched Java executions.

Each subproject has the Java plugin's standard production-source `javadoc` task. Each loader also has a `gametestJavadoc` task over its complete GameTest source set, including the shared package-private scenarios under `common/src/gametest/java`. The root `generateDocs` task derives its loader list from `enabled_platforms`, depends on every module's `javadoc` and each loader's `gametestJavadoc`, and synchronizes their HTML output into the committed `docs/javadoc/<module>/` and `docs/javadoc/<loader>-gametest/` trees; separate sections are required because loader modules contain classes with overlapping fully qualified names. `docs/index.html` is the hand-maintained landing page and `docs/.nojekyll` makes GitHub Pages serve the generated files unchanged. Neither file is a Gradle build input, and generated documentation is intentionally excluded from `build-env/`. The build does not produce Javadoc JARs.

Fabric GameTests use `run/world`. Forge and NeoForge use `run/gametestserver/gametestworld` and load shared test instance and environment definitions. The shared GameTest task setup clears the applicable world before launch. Compilation and Javadoc use UTF-8. Loader source JARs include common's main sources and resources.

`clean` empties build directories while preserving their Dropbox ignore marks. `cleanDev` also empties all loader run directories, including local worlds and configs. `collectJars` disables Gradle state tracking for the shared `../jars/` directory because it can contain Dropbox online-only files.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom | `1.17.493` | Minecraft development, mappings, runs, transforms, and remapping |
| Architectury Gradle plugin | `3.5.170` | Common/Fabric/Forge/NeoForge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java toolchain level | `21` | Compilation, Javadoc, and Java execution |
| Minecraft | `1.21.11` | Compile and runtime target |
| Mojang mappings | Official mappings for `1.21.11` | Base mapping layer; no separate mapping version is declared |
| Parchment mappings | `org.parchmentmc.data:parchment-1.21.11:2025.12.20@zip` | Layer over the official mappings |
| Forge | `net.minecraftforge:forge:1.21.11-61.1.5` | Exact Forge compile and development-run baseline |
| NeoForge | `net.neoforged:neoforge:21.11.48` | Exact NeoForge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.20.1` | Fabric loader dependency; also supplies the common annotation dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11` | Fabric runtime and development API; used by the Fabric artifact on Quilt |
| Common Protection API | `eu.pb4:common-protection-api:1.0.0` | Fabric compile-only optional claim-mod integration, also on Fabric development runs |
| EventBus validator | `net.minecraftforge:eventbus-validator:7.0.1` | Forge production and GameTest annotation processing |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.3` |
| Fabric, Forge, and NeoForge GameTest support mods | `1.0.0` |
| Minecraft compatibility | exactly `1.21.11`; Forge and NeoForge syntax `[1.21.11]`, Fabric syntax `=1.21.11` |
| Forge compatibility | `[61.1.5,62)` |
| Forge JavaFML loader compatibility | `[61,62)` |
| NeoForge compatibility | `[21.11.48,21.12)` |
| NeoForge JavaFML loader compatibility | `[1,)` |
| Fabric Loader compatibility | `>=0.20.1` |
| Fabric Java compatibility | `>=21` |
| Fabric API runtime declaration | `>=0.141.6+1.21.11`; compilation uses `0.141.6+1.21.11` |
| Quilt compatibility | the Fabric artifact is expected to run through Quilt's Fabric compatibility |

## Resolution and version authorities

`gradle.properties` owns the exact toolchain, Gradle plugin, dependency, and Minecraft versions. `settings.gradle` resolves the external plugins through `pluginManagement`, and the wrapper properties pin Gradle. The root build derives the Minecraft, Forge, and Forge JavaFML ranges. NeoForge compatibility ranges are declared in `gradle.properties`. Fabric's Java requirement uses the toolchain version. `sharedModProperties` derives the source branch and issue URLs from `mod_homepage`; all production loader manifests expand the shared homepage and issue properties, and Fabric also expands the source URL. Optional protection dependencies vary by Minecraft/loader availability; this version retains non-transitive Common Protection API compile-only and development-runtime declarations on Fabric, with no required or bundled protection API.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal. Every module declares Parchment Maven; the Fabric module also declares Nucleoid Maven (for the Common Protection API), and the NeoForge module also declares NeoForge Maven; Loom supplies its standard Minecraft repositories. There is no Gradle version catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the repository. Consequently, the table above records every exact version deliberately declared by the build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
