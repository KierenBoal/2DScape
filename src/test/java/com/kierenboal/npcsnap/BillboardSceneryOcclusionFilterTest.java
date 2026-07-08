package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Renderable;
import net.runelite.api.WallObject;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardSceneryOcclusionFilterTest
{
	@Test
	public void wallsAreAlwaysSceneryOccluders()
	{
		assertTrue(BillboardSceneryOcclusionFilter.isOccluder(proxy(WallObject.class)));
	}

	@Test
	public void tallGameObjectsAreSceneryOccluders()
	{
		Renderable renderable = proxy(Renderable.class, method("getModelHeight", BillboardSceneryOcclusionFilter.MIN_SCENERY_MODEL_HEIGHT));
		GameObject gameObject = proxy(GameObject.class, method("getRenderable", renderable));

		assertTrue(BillboardSceneryOcclusionFilter.isOccluder(gameObject));
	}

	@Test
	public void shortGameObjectsAreNotSceneryOccluders()
	{
		Renderable renderable = proxy(Renderable.class, method("getModelHeight", BillboardSceneryOcclusionFilter.MIN_SCENERY_MODEL_HEIGHT - 1));
		GameObject gameObject = proxy(GameObject.class, method("getRenderable", renderable));

		assertFalse(BillboardSceneryOcclusionFilter.isOccluder(gameObject));
	}

	@Test
	public void decorativeAndGroundObjectsAreNotSceneryOccluders()
	{
		assertFalse(BillboardSceneryOcclusionFilter.isOccluder(proxy(DecorativeObject.class)));
		assertFalse(BillboardSceneryOcclusionFilter.isOccluder(proxy(GroundObject.class)));
	}

	@Test
	public void gameObjectsUseConvexHullOrClickboxForInterest()
	{
		Rectangle interest = new Rectangle(0, 0, 8, 8);
		GameObject hullMatch = proxy(GameObject.class, method("getConvexHull", new Rectangle(4, 4, 4, 4)));
		GameObject clickboxMatch = proxy(GameObject.class, method("getClickbox", new Rectangle(4, 4, 4, 4)));
		GameObject outside = proxy(
			GameObject.class,
			method("getConvexHull", new Rectangle(32, 32, 4, 4)),
			method("getClickbox", new Rectangle(48, 48, 4, 4))
		);

		assertTrue(BillboardSceneryOcclusionFilter.intersectsInterest(hullMatch, interest));
		assertTrue(BillboardSceneryOcclusionFilter.intersectsInterest(clickboxMatch, interest));
		assertFalse(BillboardSceneryOcclusionFilter.intersectsInterest(outside, interest));
	}

	@Test
	public void wallObjectsUseEitherHullOrClickboxForInterest()
	{
		Rectangle interest = new Rectangle(0, 0, 8, 8);
		WallObject secondHullMatch = proxy(WallObject.class, method("getConvexHull2", new Rectangle(4, 4, 4, 4)));
		WallObject clickboxMatch = proxy(WallObject.class, method("getClickbox", new Rectangle(4, 4, 4, 4)));
		WallObject outside = proxy(
			WallObject.class,
			method("getConvexHull", new Rectangle(32, 32, 4, 4)),
			method("getConvexHull2", new Rectangle(40, 40, 4, 4)),
			method("getClickbox", new Rectangle(48, 48, 4, 4))
		);

		assertTrue(BillboardSceneryOcclusionFilter.intersectsInterest(secondHullMatch, interest));
		assertTrue(BillboardSceneryOcclusionFilter.intersectsInterest(clickboxMatch, interest));
		assertFalse(BillboardSceneryOcclusionFilter.intersectsInterest(outside, interest));
	}
}
