package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.state.CachedBillboard;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.AABB;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class ActorBillboardBoundsTest
{
	@Test
	public void eitherDimensionOfEitherBoxCanTriggerGrowthOrShrinkage()
	{
		ActorBillboardBounds displayed = bounds(100, 100, 100, 100);
		assertTrue(bounds(111, 100, 100, 100).exceedsResizeThreshold(displayed, 10));
		assertTrue(bounds(100, 89, 100, 100).exceedsResizeThreshold(displayed, 10));
		assertTrue(bounds(100, 100, 89, 100).exceedsResizeThreshold(displayed, 10));
		assertTrue(bounds(100, 100, 100, 111).exceedsResizeThreshold(displayed, 10));
		// Equal area must not hide opposing width/height changes.
		assertTrue(bounds(200, 50, 100, 100).exceedsResizeThreshold(displayed, 10));
	}

	@Test
	public void thresholdIsStrictAndZeroDisablesTheCheck()
	{
		ActorBillboardBounds displayed = bounds(100, 100, 100, 100);
		assertFalse(bounds(110, 90, 110, 90).exceedsResizeThreshold(displayed, 10));
		assertFalse(bounds(500, 500, 500, 500).exceedsResizeThreshold(displayed, 0));
		assertFalse(bounds(200, 200, 200, 200).exceedsResizeThreshold(displayed, 100));
		assertTrue(bounds(201, 100, 100, 100).exceedsResizeThreshold(displayed, 100));
	}

	@Test
	public void translationAndMissingOrDegenerateBoxesDoNotInvalidateSprites()
	{
		ActorBillboardBounds displayed = bounds(100, 100, 100, 100);
		assertFalse(new ActorBillboardBounds(new Rectangle(500, 600, 100, 100),
			new Rectangle(-100, -200, 100, 100)).exceedsResizeThreshold(displayed, 10));
		assertFalse(new ActorBillboardBounds(null, new Rectangle(0, 0, 0, 100))
			.exceedsResizeThreshold(displayed, 10));
		assertTrue(new ActorBillboardBounds(null, new Rectangle(0, 0, 100, 111))
			.exceedsResizeThreshold(displayed, 10));
		assertFalse(displayed.exceedsResizeThreshold(null, 10));
		assertFalse(displayed.exceedsResizeThreshold(new ActorBillboardBounds(null, null), 10));
	}

	@Test
	public void observationsAccumulateAgainstTheSpriteBaselineWithoutChangingIt()
	{
		ActorBillboardBounds displayed = bounds(100, 100, 100, 100);
		CachedBillboard sprite = new CachedBillboard(null, new Rectangle(100, 100), new Rectangle(100, 100),
			new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0L, displayed);
		assertFalse(bounds(106, 100, 100, 100).exceedsResizeThreshold(sprite.actorBounds, 10));
		sprite.touch(10L); // Reusing a cached sprite does not advance its geometry baseline.
		assertTrue(bounds(112, 100, 100, 100).exceedsResizeThreshold(sprite.actorBounds, 10));
		assertSame(displayed, sprite.actorBounds);
	}

	@Test
	public void samplesModelBoxAndHullIndependentlyWithPublicProjection()
	{
		WorldView view = proxy(WorldView.class, method("isTopLevel", true), method("contains", true),
			method("getTileSettings", new byte[4][16][16]));
		Client client = proxy(Client.class, method("getWorldView", view), method("getScale", 512),
			method("getViewportWidth", 800), method("getViewportHeight", 600));
		Actor actor = proxy(Actor.class, method("getWorldView", view),
			method("getLocalLocation", new LocalPoint(512, 1024)));
		Model initial = model(50, 50, true);
		ActorBillboardBounds.Sampler sampler = new ActorBillboardBounds.Sampler(client);
		ActorBillboardBounds displayed = sampler.sample(actor, initial);
		assertNotNull(displayed);
		assertFalse(sampler.sample(actor, initial).exceedsResizeThreshold(displayed, 10));
		assertTrue(sampler.sample(actor, model(70, 50, true)).exceedsResizeThreshold(displayed, 10));
		assertTrue(sampler.sample(actor, model(50, 70, true)).exceedsResizeThreshold(displayed, 10));
		// Missing AABB does not prevent the independent hull check.
		assertTrue(sampler.sample(actor, model(70, 50, false)).exceedsResizeThreshold(displayed, 10));
		assertNull(sampler.sample(actor, null));
		assertNull(sampler.sample(proxy(Actor.class), initial));
	}

	private static Model model(int hullHalfWidth, int boxHalfWidth, boolean withBox)
	{
		AABB box = proxy(AABB.class, method("getExtremeX", boxHalfWidth), method("getExtremeY", 50),
			method("getExtremeZ", 10), method("getCenterY", -50));
		return proxy(Model.class, method("getVerticesCount", 4),
			method("getVerticesX", new float[] { -hullHalfWidth, hullHalfWidth, hullHalfWidth, -hullHalfWidth }),
			method("getVerticesY", new float[] { -100, -100, 0, 0 }),
			method("getVerticesZ", new float[4]), method("getAABB", withBox ? box : null));
	}

	private static ActorBillboardBounds bounds(int modelWidth, int modelHeight, int hullWidth, int hullHeight)
	{
		return new ActorBillboardBounds(new Rectangle(0, 0, modelWidth, modelHeight),
			new Rectangle(0, 0, hullWidth, hullHeight));
	}
}
