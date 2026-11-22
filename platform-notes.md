# Platform notes

Facts for Minecraft 1.21.8 and its configured loaders. A **[project source]** tag identifies what the checked-in code or build wiring shows. Scope runtime evidence to the checkout and loader where it was observed.

## GameTests

- Fabric GameTests use `run/world`. Forge and NeoForge use `run/gametestserver/gametestworld` and load shared test instance and environment definitions. The shared task setup clears the applicable world before launching the GameTest server. **[project source]**
- Fabric discovers its GameTest wrappers through the Fabric API and writes `fabric/build/gametest-report.xml`. Forge and NeoForge use their native `gameTestServer` launch templates. **[project source]**

## Forge and NeoForge

- Forge selects the native `data` userdev template and removes the unused `dataClient` template before Loom writes its launch configuration. NeoForge declares its data run with `clientData()`. Both loaders use the isolated datagen launcher. **[project source]**
- Forge's checked-in access transformer mirrors common's access widener. `verifyCommonAccessMirror` checks the shared rules. NeoForge converts the widener at remap time. **[project source]**

## Packaging and optional integrations

- The loader builds bundle transformed common with Shadow and use `remapJar` for the final artifact. **[project source]**
- Common Protection API is an optional Fabric protection integration. It is available to development runs and is excluded from the production artifact. **[project source]**

## Client egg palettes

- `EggPaletteSampler` uses a `TextureTarget`, normal GUI item render state, and `CommandEncoder.copyTextureToBuffer` with its completion callback. GPU work is confined to the render thread and one readback is active per sampler. Output targets, projection, model-view transform, fog, lighting, texture transform, model offset, and scissor state are restored after sampling. An idle or retired sampler releases its target and buffers after any outstanding readback. **[project source]**
- Queue service uses `RenderSystem.queueFencedTask` and the client's deferred `schedule` method. No loader tick/render event or Mixin is required. Each loader registers `somebuckets:mob_bucket`, whose model identity includes the current palette and whose pending state requests redraw. **[project source]**
- API names and signatures were checked against [Mojang's published 1.21.8 mapping metadata](https://piston-data.mojang.com/v1/objects/bdeb624c3aefba11d9d40f34bc96176350b549b6/client.txt) and [Fabric's published 1.21.8 API documentation](https://maven.fabricmc.net/docs/yarn-1.21.8%2Bbuild.1/com/mojang/blaze3d/systems/CommandEncoder.html). This establishes the interface shape; compilation, transparency/readback appearance, model refresh, and client behavior require the user's build and manual runs.
