# Platform notes

Facts for Minecraft 1.21.11 and its configured loaders. A **[project source]** tag identifies what the checked-in code or build wiring shows. Scope runtime evidence to the checkout and loader where it was observed.

## GameTests

- Fabric GameTests use `run/world`. Forge and NeoForge use `run/gametestserver/gametestworld` and load shared test instance and environment definitions. The shared task setup clears the applicable world before launching the GameTest server. **[project source]**
- Fabric discovers its GameTest wrappers through the Fabric API and writes `fabric/build/gametest-report.xml`. Forge and NeoForge use their native `gameTestServer` launch templates. **[project source]**

## Forge and NeoForge

- Forge selects the native `data` userdev template and removes the unused `dataClient` template before Loom writes its launch configuration. NeoForge declares its data run with `clientData()`. Both loaders use the isolated datagen launcher. **[project source]**
- Forge's checked-in access transformer mirrors common's access widener. `verifyCommonAccessMirror` checks the shared rules. NeoForge converts the widener at remap time. **[project source]**

## Packaging and optional integrations

- The loader builds bundle transformed common with Shadow and use `remapJar` for the final artifact. **[project source]**
- Common Protection API is an optional Fabric protection integration. It is available to development runs and is excluded from the production artifact. **[project source]**
