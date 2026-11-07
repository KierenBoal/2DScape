package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.OwnedModel;
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
	public void brightnessChangeInvalidatesPreview()
	{
		NpcSnapConfig config = config();
		BillboardRenderRequest request = request(false, false);
		BillboardCacheKey key = BillboardCacheKey.create(request, config, 2, 12, 75, 99, 101, 180);
		CachedBillboard cached = new CachedBillboard(key,
			new Rectangle(1, 2, 3, 4), new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 10L);

		assertTrue(cached.matchesPreviewKey(BillboardCachePreviewKey.create(request, config, 2, 75, 101, 180)));
		assertFalse(cached.matchesPreviewKey(BillboardCachePreviewKey.create(request, config, 2, 75, 101, 360)));
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
	public void brightnessSettingsInvalidateOnlyTheirModelSource()
	{
		NpcSnapConfig baseline = config(180, 64);
		NpcSnapConfig generalChanged = config(120, 64);
		NpcSnapConfig retroChanged = config(180, 80);
		BillboardRenderRequest ordinary = request(false, false);
		BillboardRenderRequest retro = request(false, false, new OwnedModel(model()));

		BillboardCacheKey ordinaryBase = key(ordinary, baseline);
		BillboardCacheKey retroBase = key(retro, baseline);
		CachedBillboard ordinaryCached = new CachedBillboard(ordinaryBase,
			new Rectangle(0, 0, 1, 1), new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0L);
		CachedBillboard retroCached = new CachedBillboard(retroBase,
			new Rectangle(0, 0, 1, 1), new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0L);
		assertNotEquals(ordinaryBase, key(ordinary, generalChanged));
		assertEquals(ordinaryBase, key(ordinary, retroChanged));
		assertEquals(retroBase, key(retro, generalChanged));
		assertNotEquals(retroBase, key(retro, retroChanged));
		assertFalse(ordinaryCached.matchesPreviewKey(BillboardCachePreviewKey.create(
			ordinary, generalChanged, 1, 50, 0)));
		assertTrue(ordinaryCached.matchesPreviewKey(BillboardCachePreviewKey.create(
			ordinary, retroChanged, 1, 50, 0)));
		assertTrue(retroCached.matchesPreviewKey(BillboardCachePreviewKey.create(
			retro, generalChanged, 1, 50, 0)));
		assertFalse(retroCached.matchesPreviewKey(BillboardCachePreviewKey.create(
			retro, retroChanged, 1, 50, 0)));

		assertEquals(180, ordinaryBase.lightBoost);
		assertEquals(64, retroBase.lightBoost);
	}

	@Test
	public void previewKeysUseTheSameSourceSpecificBrightness()
	{
		NpcSnapConfig config = config(180, 64);
		BillboardCachePreviewKey ordinary = BillboardCachePreviewKey.create(request(false, false), config, 1, 50, 0);
		BillboardCachePreviewKey retro = BillboardCachePreviewKey.create(
			request(false, false, new OwnedModel(model())), config, 1, 50, 0);

		assertEquals(180, ordinary.lightBoost);
		assertEquals(64, retro.lightBoost);
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
		assertEquals(3, cached.consumeDebugRedrawOutlineWidth());
		assertEquals(2, cached.consumeDebugRedrawOutlineWidth());
		assertEquals(1, cached.consumeDebugRedrawOutlineWidth());
		assertEquals(0, cached.consumeDebugRedrawOutlineWidth());
	}

	private static NpcSnapConfig config()
	{
		return config(180, 64);
	}

	private static BillboardCacheKey key(BillboardRenderRequest request, NpcSnapConfig config)
	{
		return BillboardCacheKey.create(request, config, 1, config.billboardColorBands(), 50, 0, 0);
	}

	private static NpcSnapConfig config(int lightBoost, int retroBrightness)
	{
		return TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("billboardColorBands", 12),
			TestProxies.method("billboardLightBoostPercent", lightBoost),
			TestProxies.method("retroNpcSwapperBrightness", retroBrightness),
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
		return request(hover, interact, model());
	}

	private static BillboardRenderRequest request(boolean hover, boolean interact, Model model)
	{
		return new BillboardRenderRequest(
			null, model, null, 0, 0, 10, 20, 30, 40, 50, 60, -1,
			hover, interact, VerticalAnchor.BOTTOM, null);
	}

	private static Model model()
	{
		return TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesCount", 0),
			TestProxies.method("getModelHeight", 10));
	}
}
