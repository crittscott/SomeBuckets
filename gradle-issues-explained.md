# Some Buckets — Build Setup, Explained for an Outsider

This is not a bug report. It's an answer to: if an experienced Forge+Fabric multi-loader modder opened
this repo cold, what would surprise them, and why? Written for someone who hasn't worked with a
multi-loader Gradle setup before. Pulled from the actual build files (`settings.gradle`,
`gradle.properties`, the three `build.gradle`s, `buildSrc/`, `mods.toml`, `fabric.mod.json`) and the
migration brief (`multi-loader-transition.md`) that documents why these choices were made.

## The one thing to understand first

For "one mod, two loaders" (Forge + Fabric), there is a dominant, default path almost everyone uses:
**Architectury Loom**. It's a single Gradle plugin that handles both loaders, gives you
`@ExpectPlatform` (write one method signature in shared code, implement it separately per loader, the
tool wires the call), and pairs with **Architectury API** for ready-made cross-loader abstractions
(fluids, menus, networking, etc.). Every tutorial, the official template repo, and the majority of
published multi-loader mods use this. If an experienced multi-loader modder opened this repo expecting
that, the first thing they'd notice is: **it's not here at all.** No Architectury Loom, and — as of the
latest change — no Architectury API either.

That's a legitimate choice (`multi-loader-transition.md` argues for it explicitly, and it isn't wrong),
but it means the project is off the well-worn path, and everything below is a consequence of that.

## Where the eyebrows go up, roughly in order of size

### 1. ModDevGradle "LegacyForge" instead of ForgeGradle, for a genuinely old Forge (47.4.0 / MC 1.20.1)

ModDevGradle is NeoForge's own build tool. "LegacyForge" is a compatibility mode NeoForge's team
bolted on so ModDevGradle can *also* target pre-NeoForge Forge. It's new, far less battle-tested for
actual Forge than the tool basically everyone uses for Forge 1.20.1 — ForgeGradle 6.x, which is what
every Forge MDK, tutorial, and template ships with. The migration doc has to stop and clarify:
*"`net.neoforged.moddev.legacyforge`... its package name does not mean the project targets NeoForge."*
Needing that disclaimer for future readers is a tell — an expert's first question would be "why not
plain ForgeGradle here?"

### 2. No standard template — the multi-module wiring is hand-built from scratch

Sharing one `common` source folder across two independent Gradle builds (compiling it twice, once per
loader, with no shared jar) is itself a known, legitimate pattern with its own community templates —
just a much less popular one than Architectury Loom. This project doesn't use one of those either; the
`buildSrc/multiloader-common.gradle` / `multiloader-loader.gradle` convention plugins were built by
hand for this repo. The tell here is `multi-loader-transition.md`'s "Failure modes encountered" table —
a list of plugin-ordering footguns, missing-repository errors, and resolution failures that a
maintained template would have already discovered and fixed. That debugging was paid for from scratch.

### 3. Architectury API was added for one call, made mandatory for every player, then removed

The doc's own rationale was "a small cross-loader runtime API" used for `Platform.isModLoaded(...)`.
That already made an expert raise an eyebrow: it required *every player on both loaders* to separately
install a whole extra library just so the mod could ask "is FTB Chunks present?" — something Forge's
`ModList.isLoaded(...)` and Fabric's `FabricLoader.isModLoaded(...)` already answer natively, for free.
Dropping it now matches what an expert would've suggested from day one — but it leaves an odd middle
state in the build files: repository entries and infrastructure that assumed Architectury would be
there, now serving nothing (the `maven.architectury.dev` line in both `buildSrc` convention plugins —
nothing resolves against it anymore).

### 4. GameTest code lives inside `src/main/java`, not its own source set

Forge's own official templates put GameTest code in a dedicated `gameTest` source set (or `src/test`).
Here, ~5,000 lines of test code sit directly in the main production source tree and get stripped out of
the shipped jar afterward via a string-pattern `exclude(...)` in the `jar` task. That means the tests
compile into every dev run and get IDE-indexed as if they were production code, and "don't ship the
tests" is enforced by remembering to exclude the right path rather than by where the files physically
live. An expert would ask "why isn't this just a separate source set?"

### 5. No data generation — recipes and tags are hand-typed JSON

Both Forge and Fabric ship a datagen API specifically so you don't hand-author recipe/tag/lang JSON,
because it's exactly the kind of thing that silently drifts between similar files. This project has
none configured (`as-built.md` confirms it directly: "No Gradle GameTest or data run is configured").
This isn't hypothetical risk — it's the actual root cause of the `big_bucket_64.json` bug from the
earlier code review, where the Fabric-specific `"fabric:type"` discriminator key was missing from one
recipe file because it has to be typed correctly, by hand, in parallel with every other recipe. An
expert would say generated recipes are the standard fix for exactly this failure mode.

### 6. `org.gradle.daemon=false`, committed globally

This is unusual on its own merits, independent of the multiloader question. The Gradle daemon (a
background process that keeps the JVM warm between builds) is on by default in essentially every
Gradle project, including every Forge/Fabric template. Turning it off means *every single Gradle
command* — even `./gradlew help` — pays full JVM and plugin-classloading startup cost. If this was a
workaround for ModDevGradle and Fabric Loom fighting over cache state, the normal fix is running
`--stop` on the daemon when switching tools, not disabling it permanently for everyone who clones the
repo.

### 7. Some of the hand-rolled plumbing is fragile in ways a template wouldn't be

Small thing, but real: `common/build.gradle` exposes its source with
`sourceSets.main.java.sourceDirectories.singleFile` — that call throws if `common` ever ends up with a
second source directory (e.g., someone adds a generated-sources folder later). And the two `buildSrc`
convention plugins each redeclare the same three Maven repositories independently rather than sharing
one list. Neither is a bug today, but both are the kind of thing a maintained template gets right once,
that here has to be noticed by hand.

## The honest framing

None of this is "the build is broken." It's "the build takes a less-traveled, hand-maintained path
where the well-traveled one (Architectury Loom) would have given most of this for free, battle-tested,
with far more Stack Overflow/Discord coverage when something goes wrong." The migration doc shows this
was a deliberate, reasoned choice, not an accident — but it does mean every future "how do I do X in a
Forge+Fabric mod" search will assume tooling this project doesn't have, and every rough edge (plugin
ordering, missing repos, the recipe discriminator bug) has to be found by hand instead of inherited as
already-fixed.
