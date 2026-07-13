package com.kierenboal.npcsnap;

final class NpcSnapConfigChangeHandler
{
	private static final String CONFIG_GROUP = "npc-snap";

	private final Runnable markTextureBandingDirty;
	private final Runnable clearTextureCache;
	private final Runnable markUiTexturesDirty;
	private final Runnable clearBillboardCache;

	NpcSnapConfigChangeHandler(
		Runnable markTextureBandingDirty,
		Runnable clearTextureCache,
		Runnable markUiTexturesDirty,
		Runnable clearBillboardCache)
	{
		this.markTextureBandingDirty = markTextureBandingDirty;
		this.clearTextureCache = clearTextureCache;
		this.markUiTexturesDirty = markUiTexturesDirty;
		this.clearBillboardCache = clearBillboardCache;
	}

	void handle(String group, String key)
	{
		if (!CONFIG_GROUP.equals(group))
		{
			return;
		}

		if ("enableGlobalTextureBanding".equals(key) || "globalTextureColorBands".equals(key))
		{
			markTextureBandingDirty.run();
			clearTextureCache.run();
		}

		if ("enableUiTextureBanding".equals(key)
			|| "uiTextureColorBands".equals(key)
			|| "uiSpriteQuality".equals(key))
		{
			markUiTexturesDirty.run();
		}

		if ("useInventorySpritesForGroundItems".equals(key))
		{
			clearBillboardCache.run();
		}
	}
}
