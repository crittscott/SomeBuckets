# Some Buckets release tasks

## Metadata

- [ ] Replace `mod_description` in `gradle.properties` (`Get you some buckets!`) with a real one- or two-sentence description.
- [ ] Set `mod_version` for the release.
- [ ] Update `README.md` for 1.21.4: splash image URL branch, loader and Minecraft badges, intro and footer version text, Quilt claim.
- [ ] Write the release changelog.

## Testing

- [ ] Load a world saved by the previous released version on each loader and confirm bucket contents migrate.
- [ ] Test each loader with the minimum declared loader version and the newest available one.
- [ ] Test on a dedicated server as well as single-player.
- [ ] Test the Fabric JAR on Quilt, or drop the Quilt claim.

## Artifacts

- [ ] `gradlew clean build`.
- [ ] Inspect each loader JAR (`jar tf`): no GameTest classes or fixtures, source files, dev config, bundled dependency JARs, secrets, or local paths.
- [ ] Test those exact JARs in fresh client and dedicated-server instances.

## Upload (CurseForge, Modrinth)

- [ ] One file per loader, tagged with Minecraft 1.21.4 and its loader.
- [ ] Fabric: Fabric API listed as a required dependency.
- [ ] Release status (alpha, beta, release), changelog, and license set.
