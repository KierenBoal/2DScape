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
		handler.handle("npc-snap", "unrelated");
		handler.handle("npc-snap", "maxShadows");

		counters.assertCounts(0);
	}

	@Test
	public void billboardChangesOnlyClearBillboardCache()
	{
		for (String key : new String[] {"shadowType", "enableAnimationFrameSnapping", "animationFrameCount",
			"enableActorWorldPlaneProjection"})
		{
			Counters counters = new Counters();
			NpcSnapConfigChangeHandler handler = counters.handler();
			handler.handle("other", key);
			counters.assertCounts(0);
			handler.handle("npc-snap", key);
			counters.assertCounts(1);
			handler.handle("npc-snap", key);
			counters.assertCounts(2);
		}
	}

	private static final class Counters
	{
		private final AtomicInteger billboardCache = new AtomicInteger();

		private NpcSnapConfigChangeHandler handler()
		{
			return new NpcSnapConfigChangeHandler(billboardCache::incrementAndGet);
		}

		private void assertCounts(int billboardCache)
		{
			assertEquals(billboardCache, this.billboardCache.get());
		}
	}
}
