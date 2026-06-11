package com.kierenboal.npcspan;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NpcSpanPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NpcSpanPlugin.class);
		RuneLite.main(args);
	}
}
