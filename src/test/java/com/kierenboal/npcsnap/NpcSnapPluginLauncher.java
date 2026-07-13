package com.kierenboal.npcsnap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development-client entry point used by the Gradle run task. */
public final class NpcSnapPluginLauncher
{
	private NpcSnapPluginLauncher()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NpcSnapPlugin.class);
		RuneLite.main(args);
	}
}
