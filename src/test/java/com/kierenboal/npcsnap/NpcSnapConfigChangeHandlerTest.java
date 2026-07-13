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

		handler.handle("other", "enableGlobalTextureBanding");
		handler.handle("npc-snap", "unrelated");

		counters.assertCounts(0, 0, 0, 0);
	}

	@Test
	public void globalBandingChangesDirtyManagerAndTextureCache()
	{
		for (String key : new String[] {"enableGlobalTextureBanding", "globalTextureColorBands"})
		{
			Counters counters = new Counters();
			counters.handler().handle("npc-snap", key);
			counters.assertCounts(1, 1, 0, 0);
		}
	}

	@Test
	public void uiChangesOnlyDirtyUiTextures()
	{
		for (String key : new String[] {"enableUiTextureBanding", "uiTextureColorBands", "uiSpriteQuality"})
		{
			Counters counters = new Counters();
			counters.handler().handle("npc-snap", key);
			counters.assertCounts(0, 0, 1, 0);
		}
	}

	@Test
	public void inventorySpriteModeClearsBillboardCache()
	{
		Counters counters = new Counters();

		counters.handler().handle("npc-snap", "useInventorySpritesForGroundItems");

		counters.assertCounts(0, 0, 0, 1);
	}

	private static final class Counters
	{
		private final AtomicInteger textureDirty = new AtomicInteger();
		private final AtomicInteger textureCache = new AtomicInteger();
		private final AtomicInteger uiDirty = new AtomicInteger();
		private final AtomicInteger billboardCache = new AtomicInteger();

		private NpcSnapConfigChangeHandler handler()
		{
			return new NpcSnapConfigChangeHandler(
				textureDirty::incrementAndGet,
				textureCache::incrementAndGet,
				uiDirty::incrementAndGet,
				billboardCache::incrementAndGet);
		}

		private void assertCounts(int textureDirty, int textureCache, int uiDirty, int billboardCache)
		{
			assertEquals(textureDirty, this.textureDirty.get());
			assertEquals(textureCache, this.textureCache.get());
			assertEquals(uiDirty, this.uiDirty.get());
			assertEquals(billboardCache, this.billboardCache.get());
		}
	}
}
