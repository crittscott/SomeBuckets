# Some Buckets release preparation

The release process has three distinct layers:

1. `mods.toml` tells Forge what the JAR is.
2. `build.gradle` tells Gradle how to produce the JAR.
3. CurseForge, Modrinth, or GitHub describes and distributes that finished JAR.

The project is already close to producing a usable artifact, but it still treats development tests
as production code and retains a lot of untouched MDK template material.

## What `mods.toml` does

[`mods.toml`](src/main/resources/META-INF/mods.toml) is the mod's runtime manifest. Forge reads it
from `META-INF/mods.toml` inside the finished JAR before loading the mod.

It answers:

- Which loader understands this JAR?
- Which mod or mods does it contain?
- What are their IDs and versions?
- Which Minecraft and Forge versions are compatible?
- Which other mods are required or optional?
- What should Forge display in the Mods screen?

It does not compile the project, publish anything, or control Java dependencies.

The file uses placeholders such as:

```toml
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
```

During `processResources`, [`build.gradle`](build.gradle) replaces those with values from
[`gradle.properties`](gradle.properties). Thus the finished JAR contains literal values, not
`${mod_id}`.

### What it should contain

At minimum, keep:

- `modLoader="javafml"`
- The loader range
- The license
- `[[mods]]` with ID, version, name, authors, and description
- The mandatory Forge dependency
- The mandatory Minecraft dependency
- The optional FTB Chunks dependency

The optional FTB declaration is meaningful. It tells Forge that FTB Chunks is recognized but not
required:

```toml
[[dependencies.somebuckets]]
    modId="ftbchunks"
    mandatory=false
    versionRange="[2001,2002)"
    ordering="AFTER"
    side="BOTH"
```

The `compileOnly` declaration in `build.gradle` is the build-side counterpart: the project compiles
against FTB Chunks but does not bundle it.

### What should be improved

- Replace `Get you some buckets!` with a useful description.
- Narrow Minecraft compatibility to 1.20.1.
- Restrict Forge compatibility to the 47.x line.
- Add a real project URL and issue tracker if those pages exist.
- Add a logo if desired.
- Remove the template tutorial comments.

The chosen compatibility policy is Minecraft 1.20.1 only and any Forge 47.x release:

```properties
minecraft_version_range=[1.20.1]
forge_version_range=[47,48)
loader_version_range=[47,48)
```

The main principle is that these ranges are claims to users. Do not claim compatibility with
versions that have not been designed or tested for.

The default `MATCH_VERSION` display behavior is appropriate. Some Buckets adds registered items and
therefore belongs on both client and server. It should not be marked client-only or configured to
ignore client/server version mismatches.

Leave `updateJSONURL` absent unless a Forge update-check JSON file will deliberately be maintained.
It is optional infrastructure, not a release requirement.

## What `build.gradle` does

[`build.gradle`](build.gradle) is the build recipe. It controls:

- Plugins and their versions
- Java 17
- Minecraft and Forge dependencies
- Mappings
- Development run configurations
- Resource processing
- Data-generated resources
- JAR construction and reobfuscation
- Optional Maven publication

A release build eventually produces something like:

```text
build/libs/somebuckets-1.0.0.jar
```

That JAR is what players install and what gets uploaded to a mod platform.

### Parts that are needed

These are substantive and should remain:

- The ForgeGradle plugin
- The Parchment plugin, since the project uses Parchment mappings
- The Java 17 toolchain
- The `minecraft` configuration and run definitions
- Forge and FTB dependencies
- `processResources` placeholder expansion
- Generated-resource configuration
- UTF-8 compilation
- JAR reobfuscation

The manifest block is reasonable. Fields such as `Implementation-Version` can be useful when
inspecting a JAR.

### Parts that probably are not needed

- `id 'eclipse'` if only IntelliJ is used
- `id 'maven-publish'` and the `publishing` block unless a Maven repository is wanted
- The local `mcmodsrepo` publication example
- Commented JEI, flat-directory, access-transformer, and multi-project examples
- Most explanatory MDK comments
- The startup Java-version `println`, unless it is useful

The existing `publishing` block does not upload to CurseForge or Modrinth. It merely defines a local
Maven publication under `mcmodsrepo`. Removing it will not prevent ordinary release JAR creation.

The build should also replace plugin version ranges:

```groovy
id 'net.minecraftforge.gradle' version '[6.0,6.2)'
id 'org.parchmentmc.librarian.forgegradle' version '1.+'
```

with exact versions. Otherwise identical source checkouts can resolve different plugin releases over
time.

One subtle point: this attribute prevents bit-for-bit reproducible JARs:

```groovy
'Implementation-Timestamp': new Date().format(...)
```

That is harmless for ordinary releases, but should be removed if reproducible builds are a goal.

## L6: GameTests in the production JAR

This is the more concrete release defect.

All GameTests are in:

```text
src/main/java/com/github/crittscott/somebuckets/gametest/
```

Everything under `src/main/java` belongs to Gradle's `main` source set, so those classes are compiled
into the production JAR. Furthermore, `build.gradle` generates the GameTest structure and explicitly
adds it to `main.resources`.

That means the release JAR likely contains:

- All eleven GameTest and support classes
- Forge GameTest annotations
- The `empty_9x6x9.nbt` test structure

This probably does not break ordinary play, but it is still undesirable:

- It enlarges and clutters the artifact.
- Test-only classes become part of the distributed code.
- Forge may discover the annotated test classes.
- Production packaging is no longer an honest description of runtime requirements.

There are two reasonable fixes.

### Preferred: dedicated GameTest source set

Move the classes to something like:

```text
src/gametest/java/
src/gametest/resources/
```

Create a `gameTest` source set whose compile and runtime classpaths include `main`, and configure only
the GameTest development run to load both source sets.

The generated NBT fixture should feed `gameTest.resources`, not `main.resources`.

This gives a clean boundary:

```text
main     -> production code and resources
gameTest -> tests, support code, and structures
```

Given how extensive the test suite is, this is the architecturally proper solution.

### Simpler: exclude tests from the release JAR

The classes could remain in `main` for development while the package and fixture are excluded from
the JAR task. That produces a clean distributable with less Gradle reconfiguration.

It is an acceptable interim measure, but less clean because the tests still compile as production
sources and the separation depends on remembering exclusion patterns.

A dedicated source set is preferable before the first public release.

## What a practical release entails

For this mod, use the following release gate.

### Project identity

- Choose a real version. If this is the first public and still exploratory release, `0.1.0` may
  communicate reality better than `1.0.0`.
- Choose and include a license.
- Write a useful one- or two-paragraph description.
- Add project and issue URLs if available.
- Optionally add a Mods-screen logo.
- Prepare a player-facing README and release changelog.

`All Rights Reserved` is valid if that is the intention, but an actual `LICENSE` file still makes
the terms unambiguous.

### Compatibility

- Declare Minecraft 1.20.1 only.
- Declare the tested Forge 47.x range.
- Confirm Java 17.
- Preserve FTB Chunks as optional.
- Test without FTB Chunks as well as with it.
- Test on a dedicated server, not only an integrated client world.

### Artifact hygiene

The finished JAR should contain:

- `META-INF/mods.toml`
- Compiled production classes
- `assets/somebuckets/...`
- `data/somebuckets/...`
- `pack.mcmeta`

It should not contain:

- GameTest classes or fixtures
- Source files
- Development-only configuration
- Unrelated dependency JARs
- Template example assets
- Secrets or local paths

### Release verification

When making the release, build and inspect the actual JAR:

```powershell
.\gradlew.bat clean build
jar tf .\build\libs\somebuckets-<version>.jar
```

Then test that exact JAR in fresh client and dedicated-server instances. Testing the development run
is not quite the same thing as testing the packaged, reobfuscated artifact.

Finally, upload that one JAR to the chosen platform with:

- Minecraft 1.20.1
- Forge
- The correct release status: alpha, beta, or release
- FTB Chunks listed as an optional dependency
- Installation instructions
- A changelog
- The license

## Recommended order

Treat L6 as technical cleanup and most of L7 as release preparation:

1. Separate GameTests from production.
2. Pin build-plugin versions.
3. Replace the manifest description and add any real URLs.
4. Remove unused Maven, Eclipse, and template scaffolding.
5. Decide whether the first public version is honestly `1.0.0` or a prerelease.
