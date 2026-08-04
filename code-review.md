# Some Buckets Code Review

A static review of the source tree. Nothing was built or run;
findings come from reading the code and reasoning about Minecraft 1.20.1 / Forge 47.x behavior.

Open findings are ordered by severity. Numbering is stable.

## Hygiene

- **`mods.toml` is the unedited MDK template**, comments and all.

## Resolved since this review

- Findings 15 and 18 were replaced by Forge's runtime fluid-container model. Big, Huge, and Source Buckets now use the
  assigned fluid's still sprite and tint without hand-maintained per-fluid models or colors.
- Generic modded-fluid item names now interpolate the fluid's translated component instead of showing an untranslated
  Some Buckets key.
