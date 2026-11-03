# Lobbykin

Lobbykin is a RuneLite sidebar for finding and coordinating Old School RuneScape groups through [Lobbykin](https://lobbykin.com/).

## Install and connect

1. Open RuneLite's **Plugin Hub**, search for **Lobbykin**, and install it.
2. Open the Lobbykin sidebar and select **Connect with Lobbykin**.
3. Sign in to your Lobbykin account in your browser and choose which optional permissions to grant.

## Use the sidebar

- Host or join a group on the Lobbykin website. The sidebar shows your current group, its world, its roster, and upcoming sessions.
- Use **Mark ready** if you've granted that permission. Use the displayed world number to switch worlds manually.
- For optional Chambers of Xeric, Theatre of Blood, or Tombs of Amascut reporting, grant raid-report permission and select **Track this session** before entering the raid on the group's world. Reports can include starts, your deaths, exits, and completions.
- Stop tracking from the sidebar. To revoke access, use the [RuneLite access page](https://lobbykin.com/runelite.html). Reconnect after restarting RuneLite.

## Data and permissions

The plugin connects to Lobbykin over HTTPS. The service receives your IP address as connection metadata. Connecting and fetching your groups uses pairing and account access credentials. Optional actions send your group identifier and Ready status. Raid reporting sends your OSRS character name, world, and raid activity only after you grant permission and select a session to track. See the [full data and permissions details](https://github.com/juskyg/lobbykin-runelite/blob/plugin-hub-no-world-hop/PRIVACY.md).

## Support

Report bugs in [GitHub Issues](https://github.com/juskyg/lobbykin-runelite/issues). Include the plugin version and steps to reproduce the issue. Keep access codes and credentials private.
