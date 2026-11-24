package com.kierenboal.npcsnap;

final class NpcSnapConfigChangeHandler
{
	private static final String CONFIG_GROUP = "npc-snap";

	private final Runnable clearBillboardCache;

	NpcSnapConfigChangeHandler(Runnable clearBillboardCache)
	{
		this.clearBillboardCache = clearBillboardCache;
	}

	void handle(String group, String key)
	{
		if (!CONFIG_GROUP.equals(group))
		{
			return;
		}

		if ("shadowType".equals(key)
			|| "enableAnimationFrameSnapping".equals(key)
			|| "animationFrameCount".equals(key)
			|| "enableActorWorldPlaneProjection".equals(key))
		{
			clearBillboardCache.run();
		}
	}
}
