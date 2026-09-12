package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.TestProxies;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Model;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BillboardCacheModelsTest
{
	@Test
	public void fullAndPreviewFactoriesStayInSync()
	{
		NpcSnapConfig config = config();
		BillboardRenderRequest request = request(true, false);
		BillboardCacheKey key = BillboardCacheKey.create(request, config, 2, 12, 75, 99, 101);
		BillboardCachePreviewKey preview = BillboardCachePreviewKey.create(request, config, 2, 75, 101);
		CachedBillboard cached = new CachedBillboard(
			key, new Rectangle(1, 2, 3, 4), new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 10L);

		assertTrue(cached.matchesPreviewKey(preview));
		assertEquals(12, key.colorBands);
		assertEquals(99, key.textureStateHash);
	}

	@Test
	public void previewRejectsChangedRequestOrAnimatedTextureState()
	{
		NpcSnapConfig config = config();
		BillboardCacheKey key = BillboardCacheKey.create(request(true, false), config, 2, 12, 75, 99, 101);
		CachedBillboard cached = new CachedBillboard(
			key, new Rectangle(1, 2, 3, 4), new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 10L);

		assertFalse(cached.matchesPreviewKey(null));
		assertFalse(cached.matchesPreviewKey(BillboardCachePreviewKey.create(request(false, false), config, 2, 75, 101)));
		assertFalse(cached.matchesPreviewKey(BillboardCachePreviewKey.create(request(true, false), config, 2, 75, 102)));
	}

	@Test
	public void fullKeyEqualityIncludesTextureAndColorInputs()
	{
		NpcSnapConfig config = config();
		BillboardRenderRequest request = request(false, true);
		BillboardCacheKey base = BillboardCacheKey.create(request, config, 1, 8, 50, 7, 9);
		BillboardCacheKey same = BillboardCacheKey.create(request, config, 1, 8, 50, 7, 9);
		BillboardCacheKey otherBands = BillboardCacheKey.create(request, config, 1, 9, 50, 7, 9);
		BillboardCacheKey otherTexture = BillboardCacheKey.create(request, config, 1, 8, 50, 8, 9);

		assertEquals(base, same);
		assertEquals(base.hashCode(), same.hashCode());
		assertNotEquals(base, otherBands);
		assertNotEquals(base, otherTexture);
		assertNotEquals(base, null);
	}

	@Test
	public void cachedBillboardStateIsConsumableAndBoundsAreDefensivelyCopied()
	{
		Rectangle sourceBounds = new Rectangle(1, 2, 3, 4);
		Rectangle contentBounds = new Rectangle(2, 3, 1, 2);
		CachedBillboard cached = new CachedBillboard(
			BillboardCacheKey.create(request(false, false), config(), 1, 8, 50, 0, 0),
			sourceBounds,
			contentBounds,
			new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
			10L);
		sourceBounds.x = 100;
		contentBounds.x = 100;

		assertEquals(1, cached.bounds.x);
		assertEquals(2, cached.contentBounds.x);
		assertTrue(cached.consumeDirty());
		assertFalse(cached.consumeDirty());
		assertTrue(cached.consumeDebugFrameRedrawn());
		assertFalse(cached.consumeDebugFrameRedrawn());
		cached.touch(20L);
		assertEquals(20L, cached.lastUsedMillis());
	}

	private static NpcSnapConfig config()
	{
		return TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("billboardColorBands", 12),
			TestProxies.method("billboardLightBoostPercent", 180),
			TestProxies.method("enableBillboardHighlightOutline", true),
			TestProxies.method("enableBillboardShadowOutline", false),
			TestProxies.method("enableBillboardSpriteOutline", true),
			TestProxies.method("enableBillboardSpriteShadows", true),
			TestProxies.method("enableBillboardHighlightInline", false),
			TestProxies.method("enableBillboardShadowInline", true),
			TestProxies.method("enableBillboardSpriteInline", false),
			TestProxies.method("billboardSpriteOutlineColor", Color.BLUE),
			TestProxies.method("billboardHoverOutlineColor", Color.YELLOW),
			TestProxies.method("billboardInteractionOutlineColor", Color.RED));
	}

	private static BillboardRenderRequest request(boolean hover, boolean interact)
	{
		Model model = TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesCount", 0),
			TestProxies.method("getModelHeight", 10));
		return new BillboardRenderRequest(
			null, model, null, 0, 0, 10, 20, 30, 40, 50, 60, -1,
			hover, interact, VerticalAnchor.BOTTOM, null);
	}
}
