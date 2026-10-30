# Validation — 0.9.4-rc1

Prepared 2026-10-30 from the saved Lobbykin-RuneLite-v0.9.3.zip source package.
This is a submission-preparation candidate, not a new live-tested game release.

## Completed in this task

- Compared plugin layout with the official runelite/plugin-hub README, including
  standard build, metadata, catalog icon, license and marker submission process.
- Read official Hub runelite.version through GitHub: 1.13.4.
- Inspected all eleven main Java source files for API boundaries and destinations.
  No native-code loading, runtime code downloading or Java reflection call was
  found in this source inspection. This is not a full security audit.
- Root catalog icon copied byte-for-byte from the existing 48x48 resource.
- Removed the prebuilt 0.9.3 release JAR from the candidate to avoid shipping a
  binary whose version differs from the new source.
- Updated metadata/User-Agent versions; no game detection, hopping, session or
  authorization logic intentionally changed. All test and resource bytes remain
  those of 0.9.3. Only BrowserPairing.java and SessionClient.java changed in src,
  for their User-Agent strings.
- Checked the available Java runtime: OpenJDK 17 is present, but `javac` and a
  full JDK are not installed here. A direct subset compile was attempted and
  could not run; no compilation success is claimed.
- Ran tools/preflight.py: required-file layout, version consistency, PNG size,
  source-only packaging, literal endpoint and embedded token checks pass.
- Checked the final ZIP member list and CRC integrity.

## Blocked / not established

The full Gradle build was attempted. Wrapper download failed because the
environment could not reach services.gradle.org; the configured HTTP proxy was
also unavailable. This environment also lacks a full JDK. No complete build or
JUnit pass is claimed for this candidate.
The included GitHub Actions workflow has not run yet. The final archive must be
compiled and tested in CI before submission.

No production write, database migration, authenticated API test, game-client
test, multiplayer deduplication test, GitHub publication or Plugin Hub submission
was performed. The backend cross-client matcher is outside this package. See
SUBMISSION_LT.md for the live release gates and PRIVACY.md for the remaining
service-side retention/deletion documentation.

Official sources checked:
- https://github.com/runelite/plugin-hub/blob/master/README.md
- https://github.com/runelite/plugin-hub/blob/master/runelite.version
- https://github.com/runelite/example-plugin/blob/master/build.gradle
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
