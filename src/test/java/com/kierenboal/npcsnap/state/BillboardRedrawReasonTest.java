package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import net.runelite.api.Model;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class BillboardRedrawReasonTest
{
	private final Rectangle bounds = new Rectangle(0, 0, 100, 100);
	private final NpcSnapConfig config = new NpcSnapConfig() {};

	@Test
	public void distinguishesFrameAdvancementFromAnimationChanges()
	{
		CachedBillboard displayed = cached(key(1, 5, 2, 6, false, 100, 0, 0));
		assertEquals(EnumSet.of(BillboardRedrawReason.FRAME_COUNT),
			BillboardRedrawReason.cacheChanges(displayed, key(1, 6, 2, 6, false, 100, 0, 0), bounds, 20));
		assertEquals(EnumSet.of(BillboardRedrawReason.ANIMATION_CHANGE),
			BillboardRedrawReason.cacheChanges(displayed, key(3, 5, 2, 6, false, 100, 0, 0), bounds, 20));
		assertEquals(EnumSet.of(BillboardRedrawReason.FRAME_COUNT),
			BillboardRedrawReason.cacheChanges(displayed, key(1, 5, 2, 7, false, 100, 0, 0), bounds, 20));
	}

	@Test
	public void combinesForcedAndCacheReasonsWithoutDuplicatesAndFormatsWithCommas()
	{
		FrameUpdatePlan plan = new FrameUpdatePlan(1, true, true, null, null, 10, 1, 1, 1);
		CachedBillboard displayed = cached(key(1, 5, 2, 6, false, 100, 0, 0));
		plan.redrawReasons.addAll(BillboardRedrawReason.cacheChanges(displayed,
			key(3, 6, 2, 6, true, 100, 0, 0), bounds, 20));
		assertEquals(4, plan.redrawReasons.size());
		assertEquals("Frame count, Clickbox/hull size change, Animation change, Hover/interaction change",
			BillboardRedrawReason.format(plan.redrawReasons));
	}

	@Test
	public void includesTextureQualityBoundsAndExpiryCauses()
	{
		CachedBillboard displayed = cached(key(1, 5, 2, 6, false, 100, 7, 8));
		assertEquals(EnumSet.of(BillboardRedrawReason.TEXTURE_CHANGE, BillboardRedrawReason.QUALITY_CHANGE,
			BillboardRedrawReason.BOUNDS_CHANGE, BillboardRedrawReason.CACHE_EXPIRY),
			BillboardRedrawReason.cacheChanges(displayed, key(1, 5, 2, 6, false, 50, 9, 10),
				new Rectangle(0, 0, 120, 100), BillboardConstants.CACHE_TTL_MILLIS + 11));
	}

	@Test
	public void initialDrawAndUnchangedCacheHaveDifferentReasons()
	{
		BillboardCacheKey key = key(1, 5, 2, 6, false, 100, 0, 0);
		assertEquals(EnumSet.of(BillboardRedrawReason.INITIAL_DRAW),
			BillboardRedrawReason.cacheChanges(null, key, bounds, 20));
		assertTrue(BillboardRedrawReason.cacheChanges(cached(key), key, bounds, 20).isEmpty());
	}

	@Test
	public void successfulSpriteCapturesAnIndependentConsumableReasonList()
	{
		CachedBillboard sprite = cached(key(1, 5, 2, 6, false, 100, 0, 0));
		EnumSet<BillboardRedrawReason> reasons = EnumSet.of(BillboardRedrawReason.FRAME_COUNT, BillboardRedrawReason.ACTOR_RESIZE);
		sprite.markDebugFrameRedrawn(reasons);
		reasons.clear();
		assertTrue(sprite.consumeDebugFrameRedrawn());
		assertEquals(EnumSet.of(BillboardRedrawReason.FRAME_COUNT, BillboardRedrawReason.ACTOR_RESIZE),
			sprite.consumeDebugRedrawReasons());
		assertFalse(sprite.consumeDebugFrameRedrawn());
		assertTrue(sprite.consumeDebugRedrawReasons().isEmpty());
	}

	private CachedBillboard cached(BillboardCacheKey key)
	{
		return new CachedBillboard(key, bounds, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 10);
	}

	private BillboardCacheKey key(int action, int frame, int pose, int poseFrame, boolean hover, int quality,
		int textureState, int animatedTextureState)
	{
		BillboardRenderRequest request = new BillboardRenderRequest(null, proxy(Model.class, method("getVerticesCount", 4)),
			null, 0, 0, 0, 0, action, frame, pose, poseFrame, -1, hover, false, VerticalAnchor.BOTTOM, null);
		return BillboardCacheKey.create(request, config, 1, 8, quality, textureState, animatedTextureState);
	}
}
