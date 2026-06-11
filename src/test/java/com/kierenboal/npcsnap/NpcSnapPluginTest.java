package com.kierenboal.npcsnap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NpcSnapPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NpcSnapPlugin.class);
		RuneLite.main(args);
	}
}
