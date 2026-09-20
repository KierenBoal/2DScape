package com.kierenboal.npcsnap;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NpcSnapConfigChangeHandlerTest
{
	@Test
	public void ignoresOtherGroupsAndUnrelatedKeys()
	{
		Counters counters = new Counters();
		NpcSnapConfigChangeHandler handler = counters.handler();

		handler.handle("other", "enableUiTextureBanding");
		handler.handle("npc-snap", "enableGlobalTextureBanding");
		handler.handle("npc-snap", "globalTextureSpriteQuality");
		handler.handle("npc-snap", "globalTextureColorBands");
		handler.handle("npc-snap", "unrelated");

		counters.assertCounts(0, 0);
	}

	@Test
	public void retiredGlobalBandingChangesAreIgnored()
	{
		for (String key : new String[] {"enableGlobalTextureBanding", "globalTextureSpriteQuality", "globalTextureColorBands"})
		{
			Counters counters = new Counters();
			counters.handler().handle("npc-snap", key);
			counters.assertCounts(0, 0);
		}
	}

	@Test
	public void uiChangesOnlyDirtyUiTextures()
	{
		for (String key : new String[] {"enableUiTextureBanding", "applyToCustomUiTextures",
			"uiTextureColorBands", "uiSpriteQuality"})
		{
			Counters counters = new Counters();
			counters.handler().handle("npc-snap", key);
			counters.assertCounts(1, 0);
		}
	}

	@Test
	public void inventorySpriteModeClearsBillboardCache()
	{
		Counters counters = new Counters();

		counters.handler().handle("npc-snap", "useInventorySpritesForGroundItems");

		counters.assertCounts(0, 1);
	}

	private static final class Counters
	{
		private final AtomicInteger uiDirty = new AtomicInteger();
		private final AtomicInteger billboardCache = new AtomicInteger();

		private NpcSnapConfigChangeHandler handler()
		{
			return new NpcSnapConfigChangeHandler(
				uiDirty::incrementAndGet,
				billboardCache::incrementAndGet);
		}

		private void assertCounts(int uiDirty, int billboardCache)
		{
			assertEquals(uiDirty, this.uiDirty.get());
			assertEquals(billboardCache, this.billboardCache.get());
		}
	}
}
