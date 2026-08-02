# Some Buckets GameTests

The tests are ordinary Forge GameTests in the `somebuckets` namespace. They use the shared
`somebuckets:empty_9x6x9` structure, generated from the reviewable base64 fixture in
`src/gametestFixtures` during resource processing.

## Run the complete suite

On Windows:

```powershell
.\gradlew.bat runGameTestServer
```

The dedicated GameTest server exits with a nonzero status when a required test fails. A failing
behavior test is expected to remain red until the corresponding production behavior is deliberately
fixed; tests must not be weakened merely to describe an accidental implementation detail.

## Run interactively

Start the development client or server and use Minecraft's test commands:

```text
/test runall
/test run somebuckets:<test-name>
/test runfailed
```

Test methods are grouped by subsystem under
`src/main/java/com/github/crittscott/somebuckets/gametest`. The common support class owns only test
setup and assertions. It must not duplicate the production transition logic being tested.

## Coverage boundary

The dedicated-server suite covers serialized state, Forge fluid capabilities, player-independent
world operations, transfers, cauldrons, item/entity storage, mob capture and release, crafting
remainders, and dispenser behavior. It does not cover client models, textures, colors, tooltips as
rendered, hand-animation prediction, or other client-only presentation.
