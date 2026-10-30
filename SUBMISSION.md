# RuneLite Plugin Hub submission

Candidate: 0.9.4-rc1. Source: https://github.com/juskyg/lobbykin-runelite

## Submission decision

The owner has chosen to submit for review without a live multiplayer raid test.
Raid reporting remains optional and experimental. Do not claim verified
cross-client completion deduplication or official kill-count accuracy.

The successful compilation and automated tests are documented in VALIDATION.md.
A live multiplayer test is recommended follow-up validation, not a prerequisite
imposed by this project for opening the review request. RuneLite reviewers may
request changes or additional evidence and decide whether to accept the plugin.

## Submit

1. Fork https://github.com/runelite/plugin-hub into the GitHub account that will
   maintain the plugin.
2. Create an `add-lobbykin` branch in the fork.
3. Create `plugins/lobbykin` without an extension. Include exactly two fields:
   `repository=` with this public plugin repository's HTTPS .git URL, and
   `commit=` with the actual full 40-character plugin commit SHA.
4. Use `tools/make_hub_manifest.py --repository URL --commit SHA` to print the
   marker. Check that the referenced commit exists and its CI has passed.
5. Open a pull request from that fork branch into `runelite/plugin-hub`.
   Use PLUGIN_HUB_PR.md as the English description.
6. Check official Hub CI and address reviewer feedback in the same PR.
   Changes to plugin code require updating `commit=` to the new commit.
7. After acceptance and distribution, verify installation in a clean RuneLite
   client. Public source availability is not Plugin Hub approval.

Use the current official Hub `runelite.version` at submission time. This
candidate targets Java 11 bytecode and uses `build=standard`. Local development
uses JDK 17 and Gradle 8.10. No extra main-runtime dependency is introduced.

## Known validation limits

No live multiplayer test was performed for this candidate. The remote service's
matching of multiple completion reports is a heuristic outside this repository
and has not been verified here against a real multiplayer raid. Different raid
modes and start/exit signals may behave differently. No report is proof of
official Jagex KC, account ownership or every member's participation.

PRIVACY.md describes client payloads and local storage; it does not audit the
remote service's retention/deletion policy. Disclose these boundaries to
reviewers rather than inventing backend guarantees.

## Recommended follow-up tests

- Browser approval, cancel, reconnect and revoke.
- Optional permissions and Ready synchronization.
- Explicit world hop and game restrictions.
- Each supported raid/mode: start, local death, exit, interruption and completion.
- Two clients reporting one completion, retries and delayed events.
- Two consecutive raids in one session; stopping tracking and disabling plugin.

## Official references

- https://github.com/runelite/plugin-hub/blob/master/README.md
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
