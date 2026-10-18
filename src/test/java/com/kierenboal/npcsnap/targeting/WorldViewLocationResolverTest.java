package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;
import java.util.Collections;
import net.runelite.api.Actor;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Projection;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

public class WorldViewLocationResolverTest
{
	@Test
	public void leavesTopLevelActorLocationUnchanged()
	{
		WorldView top = TestProxies.proxy(WorldView.class, method("isTopLevel", true));
		LocalPoint point = new LocalPoint(128, 256, top);
		Actor actor = TestProxies.proxy(Actor.class, method("getWorldView", top), method("getLocalLocation", point));

		assertSame(point, WorldViewLocationResolver.toMainWorld(top, actor));
	}

	@Test
	public void transformsNestedActorThroughOwningWorldEntity()
	{
		Projection projection = TestProxies.proxy(Projection.class);
		WorldView nested = TestProxies.proxy(WorldView.class,
			method("isTopLevel", false), method("getMainWorldProjection", projection));
		LocalPoint nestedPoint = new LocalPoint(64, 96, nested);
		LocalPoint projected = new LocalPoint(512, 640);
		WorldEntity entity = TestProxies.proxy(WorldEntity.class,
			method("getWorldView", nested), method("transformToMainWorld", projected));
		IndexedObjectSet<WorldEntity> entities = indexed(entity);
		WorldView top = TestProxies.proxy(WorldView.class, method("isTopLevel", true), method("worldEntities", entities));

		assertSame(projected, WorldViewLocationResolver.toMainWorld(top, nested, nestedPoint));
	}

	@Test
	public void addsOwningBoatOrientationToNestedActorOrientation()
	{
		WorldView nested = TestProxies.proxy(WorldView.class, method("isTopLevel", false));
		WorldEntity entity = TestProxies.proxy(WorldEntity.class,
			method("getWorldView", nested), method("getOrientation", 512));
		WorldView top = TestProxies.proxy(WorldView.class,
			method("isTopLevel", true), method("worldEntities", indexed(entity)));
		Actor actor = TestProxies.proxy(Actor.class,
			method("getWorldView", nested), method("getCurrentOrientation", 256));

		assertEquals(768, WorldViewLocationResolver.toMainWorldOrientation(top, actor));
	}

	@Test
	public void resolvesRotatedDeckHeightAndRejectsHiddenOrUnprojectedBoats()
	{
		java.util.concurrent.atomic.AtomicBoolean hidden = new java.util.concurrent.atomic.AtomicBoolean();
		net.runelite.api.Projection projection = TestProxies.projection(512, 10, 1024, 512);
		WorldView nested = TestProxies.proxy(WorldView.class, method("getMainWorldProjection", projection), method("getTileHeight", -40));
		WorldEntity owner = TestProxies.proxy(WorldEntity.class, method("getWorldView", nested), method("getLocalLocation", new LocalPoint(64, 64)),
			TestProxies.methodSupplier("isHiddenForOverlap", hidden::get));
		WorldView top = TestProxies.proxy(WorldView.class, method("isTopLevel", true), method("getTileHeight", 100), method("worldEntities", indexed(owner)));
		net.runelite.api.Client client = TestProxies.proxy(net.runelite.api.Client.class, method("getTopLevelWorldView", top));
		Actor actor = TestProxies.proxy(Actor.class, method("getWorldView", nested), method("getLocalLocation", new LocalPoint(64, 96, nested)));
		WorldViewLocationResolver.ResolvedLocation resolved = WorldViewLocationResolver.resolve(client, actor);
		assertEquals(608, resolved.mainPoint.getX());
		assertEquals(960, resolved.mainPoint.getY());
		assertEquals(70d, resolved.height, 0d);
		assertSame(nested, resolved.source);
		hidden.set(true);
		org.junit.Assert.assertNull(WorldViewLocationResolver.resolve(client, actor));
		hidden.set(false);
		WorldView missing = TestProxies.proxy(WorldView.class);
		org.junit.Assert.assertNull(WorldViewLocationResolver.resolve(client, missing, new LocalPoint(64, 64), 0, 0));
	}

	@Test
	public void sourceTileKeysStayDistinctAcrossBoatsAndWorldViewReplacement()
	{
		WorldView first = TestProxies.proxy(WorldView.class, method("getId", 7));
		WorldView second = TestProxies.proxy(WorldView.class, method("getId", 7));
		OccupiedTileKey key = OccupiedTileKey.of(first, new LocalPoint(128, 128), 0);
		assertEquals(key, OccupiedTileKey.of(first, new LocalPoint(160, 160), 0));
		org.junit.Assert.assertNotEquals(key, OccupiedTileKey.of(second, new LocalPoint(128, 128), 0));
		org.junit.Assert.assertNotEquals(key, OccupiedTileKey.of(first, new LocalPoint(128, 128), 1));
	}

	@SuppressWarnings("unchecked")
	private static <T> IndexedObjectSet<T> indexed(T value)
	{
		return TestProxies.proxy(IndexedObjectSet.class,
			method("iterator", Collections.singletonList(value).iterator()));
	}
}
