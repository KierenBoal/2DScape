package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.TestProxies;

import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.GraphicsObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BillboardTargetModelsTest
{
	@Test
	public void priorityTileKeyRequiresPriorityLocationAndPlane()
	{
		LocalPoint point = point(130, 260);

		assertNull(PriorityTileKey.of(point, 0, BillboardConstants.RENDER_PRIORITY_NONE));
		assertNull(PriorityTileKey.of(null, 0, 1));
		assertNull(PriorityTileKey.of(point, -1, 1));
		assertEquals(PriorityTileKey.of(point, 2, 1), PriorityTileKey.of(point(255, 383), 2, 99));
		assertNotEquals(PriorityTileKey.of(point, 2, 1), PriorityTileKey.of(point, 1, 1));
		assertNotEquals(PriorityTileKey.of(point, 2, 1).sortKey(), PriorityTileKey.of(point, 1, 1).sortKey());
	}

	@Test
	public void occupiedTileKeyUsesTileCoordinatesRatherThanFineCoordinates()
	{
		assertNull(OccupiedTileKey.of(null, 0));
		OccupiedTileKey first = OccupiedTileKey.of(point(128, 128), 1);
		OccupiedTileKey sameTile = OccupiedTileKey.of(point(255, 255), 1);
		OccupiedTileKey nextTile = OccupiedTileKey.of(point(256, 128), 1);

		assertEquals(first, sameTile);
		assertEquals(first.hashCode(), sameTile.hashCode());
		assertNotEquals(first, nextTile);
		assertNotEquals(first, null);
	}

	@Test
	public void effectKeysDeduplicateActorAndGraphicsRepresentations()
	{
		WorldView worldView = worldView(2);
		LocalPoint point = new LocalPoint(256, 384, worldView);
		GraphicsObject graphics = TestProxies.proxy(GraphicsObject.class,
			TestProxies.method("getId", 77), TestProxies.method("getLevel", 2), TestProxies.method("getLocation", point));
		Actor actor = TestProxies.proxy(Actor.class,
			TestProxies.method("getLocalLocation", point), TestProxies.method("getWorldView", worldView));
		ActorSpotAnim spotAnim = TestProxies.proxy(ActorSpotAnim.class, TestProxies.method("getId", 77));

		assertEquals(EffectDedupKey.of(graphics), EffectDedupKey.of(spotAnim, actor));
		assertEquals(EffectDedupKey.of(graphics).hashCode(), EffectDedupKey.of(spotAnim, actor).hashCode());
		assertNull(EffectDedupKey.of(TestProxies.proxy(GraphicsObject.class)));
	}

	@Test
	public void tileObjectTargetUsesFirstAvailablePartLocation()
	{
		LocalPoint point = point(128, 256);
		ObservedTileObject observed = new ObservedTileObject(
			null,
			List.of(new ObjectRenderablePart(null, null, 2), new ObjectRenderablePart(null, point, 2)),
			ClassifiedObjectType.EFFECT);
		BillboardTarget target = BillboardTarget.forTileObject(observed, 5d);

		assertNotNull(target.priorityTileKey);
		assertEquals(PriorityTileKey.of(point, 2, target.getRenderPriority()), target.priorityTileKey);
	}

	private static LocalPoint point(int x, int y)
	{
		return new LocalPoint(x, y, worldView(0));
	}

	private static WorldView worldView(int plane)
	{
		return TestProxies.proxy(WorldView.class, TestProxies.method("getPlane", plane));
	}
}
