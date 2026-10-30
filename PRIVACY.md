# Lobbykin plugin data use

This describes this plugin's client behavior, not a service-wide retention policy.

## Connection and consent

No Lobbykin API request occurs until you choose to connect. Browser approval
requires a signed-in Lobbykin account and lets you review optional Ready,
notification and raid-report permissions. Manual scoped codes remain a fallback.

Approval sends a random request identifier and SHA-256 challenges to
`https://lobbykin.com/api/runelite/pair/start`; polling sends the request ID and
private verifier to `/pair/poll`. Browser navigation uses
`https://lobbykin.com/runelite.html#pair=<request-id>`, without an access credential.
Subsequent session requests carry a scoped bearer credential.

## Transfers

| Feature | Request data | When |
| --- | --- | --- |
| Own sessions | Scoped credential | Connect, refresh, approximately every 30 seconds while connected |
| Ready | Group ID, desired Ready boolean | Explicit action, with permission |
| Raid reports | Group ID, random attempt ID, raid, event, character name, world, observation time, optional duration and signal source | After explicitly arming a session, with permission |
| Heartbeat | Same bounded raid-event format | Approximately every 30 seconds during tracking |
| Legacy place reply | Group ID, confirm boolean | Compatibility path; current host-direct acceptance does not issue offers |

Responses contain account display name, own sessions, allowed roster and Ready
states, optional notifications and raid summaries. No raw chat text, Jagex or
Discord password, inventory or unrelated player activity is uploaded. System
messages are matched locally; their text is not sent.

## Storage and disconnect

Credentials, pairing secrets and pending reports stay in memory, not plugin
settings or files. Restarting requires reconnecting. Disconnect clears local
access and queued reports. Revoke server access at
`https://lobbykin.com/runelite.html`. Local disconnect does not erase uploaded
reports. The service owner must document actual server retention and deletion
arrangements before public release.

Plugin API traffic goes to `https://lobbykin.com/api/runelite/`; explicit browser
links stay on `https://lobbykin.com/`. API redirects are disabled. RuneLite's
WorldService obtains its standard world list for explicit Hop actions. RuneLite
and browser operation generate their own normal network traffic. Hosting
services receive connection metadata such as IP addresses; their logging is
outside the plugin's control.

Raid reporting is experimental. Cross-client matching occurs on the service;
it is not official KC or Jagex ownership verification. A missing report is not
evidence of misconduct.
