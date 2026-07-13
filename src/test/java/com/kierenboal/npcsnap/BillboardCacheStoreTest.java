package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Renderable;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BillboardCacheStoreTest
{
	@Test
	public void putGetAndContainsUseRenderableIdentity()
	{
		BillboardCacheStore store = new BillboardCacheStore();
		Renderable renderable = TestProxies.proxy(Renderable.class);
		CachedBillboard cached = cached(new TrackingImage(), 10L);

		store.put(renderable, cached);

		assertSame(cached, store.get(renderable));
		assertTrue(store.contains(renderable));
		assertFalse(store.contains(TestProxies.proxy(Renderable.class)));
	}

	@Test
	public void replacementRemovalAndClearFlushOwnedImages()
	{
		BillboardCacheStore store = new BillboardCacheStore();
		Renderable firstKey = TestProxies.proxy(Renderable.class);
		Renderable secondKey = TestProxies.proxy(Renderable.class);
		TrackingImage replacedImage = new TrackingImage();
		TrackingImage removedImage = new TrackingImage();
		TrackingImage clearedImage = new TrackingImage();
		store.put(firstKey, cached(replacedImage, 10L));
		store.put(firstKey, cached(removedImage, 10L));
		store.put(secondKey, cached(clearedImage, 10L));

		assertTrue(replacedImage.flushed);
		store.remove(firstKey);
		assertTrue(removedImage.flushed);
		assertNull(store.get(firstKey));
		store.clear();
		assertTrue(clearedImage.flushed);
		assertFalse(store.contains(secondKey));
	}

	@Test
	public void expirationUsesIdleTtlAndFlushesExpiredEntries()
	{
		BillboardCacheStore store = new BillboardCacheStore();
		Renderable expiredKey = TestProxies.proxy(Renderable.class);
		Renderable liveKey = TestProxies.proxy(Renderable.class);
		TrackingImage expiredImage = new TrackingImage();
		store.put(expiredKey, cached(expiredImage, 0L));
		store.put(liveKey, cached(new TrackingImage(), BillboardConstants.CACHE_TTL_MILLIS));

		store.expire(BillboardConstants.CACHE_TTL_MILLIS + 1L);

		assertFalse(store.contains(expiredKey));
		assertTrue(expiredImage.flushed);
		assertTrue(store.contains(liveKey));
	}

	private static CachedBillboard cached(BufferedImage image, long lastUsed)
	{
		return new CachedBillboard(null, new Rectangle(0, 0, 1, 1), image, lastUsed);
	}

	private static final class TrackingImage extends BufferedImage
	{
		private boolean flushed;

		private TrackingImage()
		{
			super(1, 1, BufferedImage.TYPE_INT_ARGB);
		}

		@Override
		public void flush()
		{
			flushed = true;
			super.flush();
		}
	}
}
