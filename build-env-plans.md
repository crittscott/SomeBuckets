# Some Buckets — Build Environment: Retry Plan

Everything is committed, so this is a safe point to attempt the "right" multiloader build environment
again, now with a working fallback to return to if it doesn't pan out. This documents why the first
Architectury Loom attempt likely failed, what currently exists, and a bounded, low-risk plan for
retrying it.

## Why 1.20.1 was uniquely bad timing

NeoForge forked from Forge in mid-2023, and its first supported version was 1.20.1. For a while after
that, 1.20.1 was the one Minecraft version where two "Forge-family" loaders existed simultaneously with
overlapping-but-diverging APIs, and Architectury Loom had to scramble to support both at once. That's a
real, documented rough patch, not an imagined one. It's now been about three years since that fork, so
Architectury Loom's 1.20.1 support has had a long time to settle.

## What actually exists today

**The official Architectury Template Generator** ([generate.architectury.dev](https://generate.architectury.dev/))
is a live, maintained tool built to solve exactly the "X needs Y but Y needs Z and Z doesn't work"
problem. You pick a Minecraft version, pick mappings (Mojang official, which this project already
uses), and independently check which loaders you want — Fabric, Forge, NeoForge, Quilt — as well as
whether to include Architectury API. It generates a project with a version set it knows is compatible,
rather than assembling one by hand. This is the tool that didn't exist (or wasn't used) the first time
around, and it's the natural way to retry.

Separately, the community's other major multi-loader template — **jaredlll08/MultiLoader-Template** —
uses the *exact same pattern this project already built by hand*: a shared `common` source set compiled
independently into separate Forge and Fabric Gradle builds, no Architectury Loom, no Architectury API
at all. So the instinct to avoid the bytecode-rewriting machinery wasn't a fringe idea — it's a second,
legitimate, actively-maintained community path. Its `1.20.1` branch supports Forge + Fabric, matching
what's needed now.

The catch for the eventual 1.21.1 port: that template's `1.21.1` branch drops classic Forge entirely and
only ships Fabric + NeoForge — most of the community stopped bothering with classic Forge once NeoForge
matured, so nobody maintains a 3-loader (Forge+Fabric+NeoForge) version of it. Adding a third loader
module would be a manual extension, doable once the pattern is understood but not off-the-shelf. The
Architectury generator, by contrast, treats loader selection as independent checkboxes per Minecraft
version, so a Forge+Fabric+NeoForge 1.21.1 project is something it's actually designed to produce
directly.

## Recommended plan

Treat this as a cheap, bounded experiment rather than a committed rewrite:

1. Use the generator to produce a **throwaway** scaffold for 1.20.1 + Forge + Fabric + Architectury API,
   outside this repo.
2. Get a bare sync (`./gradlew help`) working on that scaffold alone — no game code moved yet. This is
   the same "prove the empty skeleton compiles before touching anything" discipline the existing
   migration doc (`multi-loader-transition.md`) already used, and it's the cheapest possible go/no-go
   gate.
3. If that succeeds cleanly: port `common`/`forge`/`fabric` source into the generated structure. This
   gets `@ExpectPlatform`, Architectury's fluid/menu/etc. abstractions, and a setup that extends
   straightforwardly to 1.21.1's three-loader case later via the same generator.
4. If it degenerates into the same version maze as before: fall back to adopting jaredlll08's template
   as the new foundation instead — a much smaller, lower-risk move since it's structurally almost
   identical to what already exists here, just professionally maintained. Handle 1.21.1's third loader
   (Forge) by hand later, following the pattern the template already establishes for the other two.

Generating the scaffold and running the sync involves downloading external tooling and invoking
Gradle, so that part is a job to run directly rather than delegate — the builds are yours to run. The
exact click-by-click generator steps or the exact `gradle.properties`/`build.gradle` targets to aim for
can be written out on request.

## Sources

- [Architectury Template Generator](https://generate.architectury.dev/)
- [architectury/architectury-loom](https://github.com/architectury/architectury-loom)
- [jaredlll08/MultiLoader-Template (1.20.1 branch)](https://github.com/jaredlll08/MultiLoader-Template/tree/1.20.1)
- [jaredlll08/MultiLoader-Template (1.21.1 branch)](https://github.com/jaredlll08/MultiLoader-Template/tree/1.21.1)
- [Architectury Loom documentation](http://docs.architectury.dev/loom/introduction/)
