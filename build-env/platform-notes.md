# Platform notes

Facts for Minecraft 1.20.1 and its configured loaders. A **[project source]** tag identifies what the checked-in code or build wiring shows. Runtime behavior established by an actual build or game run is marked **[tested]**. Scope evidence to the version and loader where it was observed.

## Forge 47.4.0

- The GameTest support mod has its own Loom source set, `mods.toml`, `@Mod` entry point, and `pack.mcmeta`. The run enables the production mod's GameTest namespace. Tests that read production resources anchor the lookup to a production class. **[project source]**
- Dispenser automation gets a Forge fake player through `FakePlayerFactory`. Later Forge targets have different automation-player wiring. **[project source]**
- Data generation selects Forge's `data` userdev template. The build filters the unused `dataClient` template from the generated launcher configuration and uses an isolated launcher to exit after the data entry point returns. **[project source]**
- Common joins the development mod through `loom.mods` and the production artifact through Architectury's transformed common configuration. Forge uses its checked-in access transformer. **[project source]**

## Fabric

- The GameTest run uses the Fabric API GameTest flag and writes `fabric/build/gametest-report.xml`. Discovery uses the test source set's `fabric-gametest` entry points. **[project source]**
- The Fabric build declares its own access widener and uses direct Mixin remapping. The Fabric manifest lists the shared and Fabric-specific Mixin configurations. **[project source]**
- Architectury API and Common Protection API are compile-only dependencies for optional protection hooks. Fabric's production manifest declares Fabric Loader and Fabric API dependencies. **[project source]**

## Architectury and Loom

- Both loaders build their release artifacts by shading transformed common and remapping the resulting JAR. A production launch exercises the remapped Mixins and access rules. **[project source]**
- Both GameTest runs use the default Loom run directory. The shared task setup clears `run/world` before either loader's GameTest server starts. **[project source]**
