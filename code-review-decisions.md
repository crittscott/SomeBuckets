# Code review decisions

Settled review policy and accepted behavior for this project. Read this before reviewing or implementing a finding. These decisions override general preferences in the review prompt, CLAUDE.md, and code-conventions.md where they conflict. A new, demonstrated defect may justify revisiting a decision; preference, speculation, and a different architecture do not.

These decisions apply across release checkouts where the relevant behavior exists. API names and implementation observations below come from the 1.21.8 checkout unless explicitly scoped otherwise; they do not establish another checkout's implementation or verification status. Preserve that checkout's working APIs, target versions, and optional-mod settings, and consult its platform notes. An accepted integration placement does not require adding an integration to versions where it is absent.

## Review scope

- Preserve original numbering when recording verdicts on a delivered review. Record the resolution and its verification status. A later verdict supersedes a rejected or outdated proposed action; the original passages are not instructions to implement rejected work.
- Trust our code, Minecraft, loaders, and other mods unless a relevant behavior is known to be unreliable or unguaranteed. Filesystem operations and reflective integration boundaries can need failure handling. A client or server across the network remains untrusted.
- Keep effort proportional to a Minecraft mod. A theoretical edge case without a realistic player, server, or maintenance consequence is not an actionable defect.
- A working deliberate choice is not a finding merely because another implementation is more conventional. General principles do not override the specific decisions below.
- Distinguish a demonstrated behavioral defect, a maintenance risk, and an implementation preference. State what the project code establishes and which additional platform fact the claimed consequence depends on. A confirmed code shape does not make a recalled runtime consequence confirmed.
- Keep implementation status separate from verification status. A fix may be implemented while its runtime consequence remains unverified. Compilation, GameTests, manual launches, and multiplayer checks establish different things.

## Development and build

- Manual play-testing uses MultiMC, not the development runServer environment. GameTest worlds under the loader's `run/` directory are disposable. `cleanDev` is an explicit reset of local build and loader run contents.
- Keep build environments and documentation structure as close across Minecraft versions as reasonable. Keep each checkout's dependency versions, loader layout, and metadata compatibility declarations.
- Common owns production `pack.mcmeta`. Forge and NeoForge development runs group common with their main source sets through `loom.mods`. Production bundling includes transformed common. Keep the loader's access rules, Mixins, and common classpath arrangement together when considering changes.
- Forge and NeoForge use native loot generation with loader-specific data templates and the shared isolated launcher. Archive tasks use fresh generated modifiers, and resource processing provides the baseline needed to launch datagen. Keep that dependency direction.

## Loader integration

- Fabric adds structure-loot pools through its runtime callback. Forge and NeoForge register native global loot modifiers. The common reward-to-target mapping supplies both paths.
- Common Protection API is an optional Fabric protection integration. It is available to development runs and is excluded from the production artifact.
- Keep loader-native storage and protection adapters at their respective boundaries. Common owns transaction rules, authorization orchestration, and bucket state.
