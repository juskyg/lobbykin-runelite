# Add Lobbykin

Lobbykin is an OSRS group-coordination sidebar. It shows one current party,
Ready/Waiting roster states, collapsible scheduled groups and an explicit
Hop to world action. Connected features require a free Lobbykin account.

Browser approval grants a scoped, revocable credential held only in memory.
Ready changes, account notifications and experimental raid reports have separate
optional permissions. No Jagex password or website-session credential reaches
the plugin. Restarting the client requires reconnecting.

Raid reports require explicitly selecting a CoX, ToB or ToA session.
Game-system messages and client state are interpreted locally; raw chat text
is not uploaded. Death is separate from completion. No combat automation,
native code, Java reflection or dynamic executable downloads are used by the
main plugin source. Plugin API traffic goes to lobbykin.com.

PRIVACY.md and SECURITY.md document data fields, destinations and boundaries.
The plugin uses build=standard, targets Java 11 bytecode, and adds no main-runtime
dependency beyond RuneLite's transitive dependencies.

## Validation

The source candidate passed GitHub CI: submission layout plus
`bash gradlew --no-daemon clean test jar`, with JDK17 and RuneLite 1.13.4:
https://github.com/juskyg/lobbykin-runelite/actions/runs/41095181537
Current CI is available in the repository's Actions tab.

A live multiplayer raid test has NOT been performed for this candidate.
Cross-client completion deduplication happens on the remote service using a
session/raid/world/time heuristic; it is not a unique game-instance proof,
official KC or character-ownership verification. Different raid modes and
start/exit signals still need live validation. Reports may be incomplete or
inaccurate. Screenshots use demonstration data.

The submission is intentionally presented for review with these limitations,
without claiming live multiplayer verification. Service-wide retention/deletion
policy is outside the client-side privacy audit.
