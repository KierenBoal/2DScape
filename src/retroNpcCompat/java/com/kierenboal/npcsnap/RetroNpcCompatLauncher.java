package com.kierenboal.npcsnap;

import com.retronpcswapper.RetroNpcSwapperPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Loads both local builds, including their resources, instead of their Hub copies. */
public final class RetroNpcCompatLauncher
{
    private RetroNpcCompatLauncher() {}
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(NpcSnapPlugin.class, RetroNpcSwapperPlugin.class);
        RuneLite.main(args);
    }
}
