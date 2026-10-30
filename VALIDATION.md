# Validation — 0.9.4-rc1

Updated 2026-10-30. Source based on Lobbykin-RuneLite-v0.9.3.zip.

## GitHub CI: passed

The public repository import at commit
`7b06e8c25fb2656e3b013ca6e12c23354972f32e` passed **Build and test**:
https://github.com/juskyg/lobbykin-runelite/actions/runs/41095181537

Verified the job and its logs: the submission layout check passed;
`bash gradlew --no-daemon clean test jar` completed **BUILD SUCCESSFUL**.
The runner used Temurin JDK 17, Gradle 8.10 and RuneLite 1.13.4.
Main sources, test sources and resources compiled; the test task and JAR task
completed. No test count is inferred from the logs.

The original preparation environment lacked a full JDK and could not download
Gradle. GitHub CI now provides the missing build/test evidence. This does not
establish live game behavior, backend correctness or official Hub acceptance.

## Packaging and source checks

- Official Plugin Hub README, standard build, metadata, catalog icon, license
  and marker submission process reviewed. Official Hub runelite.version was
  1.13.4 when checked on 2026-10-30.
- Eleven main Java source files inspected for API boundaries and destinations.
  No native-code loading, runtime executable downloads or Java reflection call
  found in that source inspection; this is not a full security audit.
- Root icon copied byte-for-byte from the existing 48x48 resource.
- Old prebuilt 0.9.3 release JAR excluded to avoid a source/binary mismatch.
- Version metadata and API User-Agent strings synchronized. Game detection,
  hopping, session and authorization logic unchanged from 0.9.3; test/resource
  bytes unchanged. Only BrowserPairing.java and SessionClient.java changed in
  src, for their User-Agent strings.
- Offline preflight and ZIP member/CRC checks passed for the preparation package.

## Still required before public Plugin Hub submission

No authenticated production API test, live game-client test or multiplayer
completion-deduplication test was performed in this preparation task. The
backend matcher is outside this repository. Validate the scenarios in
SUBMISSION_LT.md and complete service-side retention/deletion information
referenced by PRIVACY.md. No production site or database changes were made.

Public source availability and successful CI are not Plugin Hub approval.
The official RuneLite Plugin Hub PR has not been submitted.

Official references:
- https://github.com/runelite/plugin-hub/blob/master/README.md
- https://github.com/runelite/plugin-hub/blob/master/runelite.version
- https://github.com/runelite/example-plugin/blob/master/build.gradle
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
