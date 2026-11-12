# Platform notes

Facts for Minecraft 1.21.1 and its configured loaders. A **[project source]** tag identifies what the checked-in code or build wiring shows. Scope runtime evidence to the checkout and loader where it was observed.

## GameTests

- All three GameTest servers use the disposable `run/world`. The shared task setup clears the applicable world before launching the GameTest server. **[project source]**
- Fabric discovers its GameTest wrappers through the Fabric API and writes `fabric/build/gametest-report.xml`. Forge and NeoForge use their native `gameTestServer` launch templates. **[project source]**

## Forge and NeoForge

- Forge selects the native `data` userdev template and removes the unused `dataClient` template before Loom writes its launch configuration. NeoForge selects the native `data` userdev template. Both loaders use the isolated datagen launcher. **[project source]**
- The loader builds package their own access rules for this target. **[project source]**

## Packaging and optional integrations

- The loader builds bundle transformed common with Shadow and use `remapJar` for the final artifact. **[project source]**
- FTB Chunks is an optional compile-only protection integration on Fabric and NeoForge. The adapter source set is shared by those two loaders. **[project source]**
