# Code review decisions

This file records accepted SomeBuckets choices for review. Specific decisions take precedence over general conventions. Revisit one when a new demonstrated defect changes the evidence.

## Review scope

- Distinguish a demonstrated defect, a maintenance risk, and an implementation preference. State what the project source establishes and which platform fact a consequence depends on.
- Preserve original numbering when recording verdicts on a delivered review. Record the resolution and its verification status. A later verdict supersedes a rejected or outdated proposed action.
- Report compilation, GameTests, manual launches, and multiplayer checks according to the evidence each provides.

## Development and build

- Keep build and documentation structure aligned where releases can share it. Keep each checkout's dependency versions, loader layout, and metadata compatibility declarations.
- Manual play-testing uses MultiMC. GameTest worlds under the loader's `run/` directory are disposable. `cleanDev` is an explicit reset of local build and loader run contents.
- Common owns production `pack.mcmeta`. Forge and NeoForge development runs group common with their main source sets through `loom.mods`. Production bundling includes transformed common. Keep the loader's access rules, Mixins, and common classpath arrangement together when considering changes.
- Forge and NeoForge use native loot generation with loader-specific data templates and the shared isolated launcher. Archive tasks use fresh generated modifiers, and resource processing provides the baseline needed to launch datagen. Keep that dependency direction.

## Loader integration

- Fabric adds structure-loot pools through its runtime callback. Forge and NeoForge register native global loot modifiers. The common reward-to-target mapping supplies both paths.
- Common Protection API is an optional Fabric protection integration. It is available to development runs and is excluded from the production artifact.
- Keep loader-native storage and protection adapters at their respective boundaries. Common owns transaction rules, authorization orchestration, and bucket state.
