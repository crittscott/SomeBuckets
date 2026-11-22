# Platform notes

Facts for Minecraft 26.2 and its configured loaders. A **[project source]** tag identifies what the checked-in code or build wiring shows. Scope runtime evidence to the checkout and loader where it was observed.

## GameTests

- Fabric GameTests use `run/world`. Forge and NeoForge use `run/gametestserver/gametestworld` and load shared test instance and environment definitions. The shared task setup clears the applicable world before launching the GameTest server. **[project source]**
- Fabric discovers its GameTest wrappers through the Fabric API and writes `fabric/build/gametest-report.xml`. Forge and NeoForge use their native `gameTestServer` launch templates. **[project source]**

## Forge and NeoForge

- Forge selects the native `data` userdev template and removes the unused `dataClient` template before Loom writes its launch configuration. NeoForge declares its data run with `clientData()`. Both loaders use the isolated datagen launcher. **[project source]**
- Forge's checked-in access transformer mirrors common's access widener. `verifyCommonAccessMirror` checks the shared rules. NeoForge converts the widener on `shadowJar`. **[project source]**

## Packaging and optional integrations

- Loom no-remap builds the unobfuscated Minecraft target. The production artifact is the unclassified Shadow JAR. **[project source]**
- Common Protection API is an optional Fabric protection integration. It is available to development runs and is excluded from the production artifact. **[project source]**

## Client egg palettes

- `EggPaletteSampler` renders a default spawn egg using a private submit-node storage passed to the game feature dispatcher. GPU readback is asynchronous and one sample is active at a time. Fenced callbacks and deferred client tasks service the request queue. Reload cancels unfinished diagnostic sweeps and retires old GPU work after its readback; idle GPU resources are released. **[project source]**
- `MobBucketModel` includes the palette in GUI model identity and marks pending samples animated. All loaders register the model codec. `/sb eggs` samples every registered egg and writes its report and PNG contact sheet on the I/O executor; explicit resource-pack overrides remain authoritative for bucket colors. **[project source]**
- Rendering signatures were checked against published API documentation and the [rendering migration reference](https://docs.neoforged.net/primer/docs/26.2/). The 1.21.8 implementation was confirmed working by the user; this target requires its own build and client verification.
