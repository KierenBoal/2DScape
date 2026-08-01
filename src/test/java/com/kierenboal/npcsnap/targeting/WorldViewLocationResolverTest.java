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

	@SuppressWarnings("unchecked")
	private static <T> IndexedObjectSet<T> indexed(T value)
	{
		return TestProxies.proxy(IndexedObjectSet.class,
			method("iterator", Collections.singletonList(value).iterator()));
	}
}
