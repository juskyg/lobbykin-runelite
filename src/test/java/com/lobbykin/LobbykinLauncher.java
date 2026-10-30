package com.lobbykin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development launcher; not an in-game automation or installer. */
public final class LobbykinLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(LobbykinPlugin.class);
        RuneLite.main(args);
    }
}
