package com.kierenboal.npcsnap;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardTargetEligibilityTest
{
	private static final Rectangle VIEWPORT = new Rectangle(0, 0, 100, 100);

	@Test
	public void actorRequiresPlayerAndActorLocations()
	{
		Actor actor = actor(null, 0, visibleTile());
		BillboardTargetEligibility eligibility = eligibility(0, false, 10);

		assertFalse(eligibility.actor(null, null, actor, VIEWPORT));
	}

	@Test
	public void actorRejectsOtherPlaneUnlessAllPlanesEnabled()
	{
		LocalPoint player = point(0, 0, 0);
		LocalPoint target = point(128, 0, 1);
		Actor actor = actor(target, 1, visibleTile());

		assertFalse(eligibility(0, false, 10).actor(player, target, actor, VIEWPORT));
		assertTrue(eligibility(0, true, 10).actor(player, target, actor, VIEWPORT));
	}

	@Test
	public void actorUsesCircularRadiusRatherThanSquareBounds()
	{
		LocalPoint player = point(0, 0, 0);
		LocalPoint diagonal = point(128, 128, 0);
		Actor actor = actor(diagonal, 0, visibleTile());

		assertFalse(eligibility(0, false, 1).actor(player, diagonal, actor, VIEWPORT));
	}

	@Test
	public void visibleActorTilePolygonIsEligibleWithoutProjectionFallback()
	{
		LocalPoint player = point(0, 0, 0);
		LocalPoint target = point(128, 0, 0);

		assertTrue(eligibility(0, false, 10).actor(player, target, actor(target, 0, visibleTile()), VIEWPORT));
	}

	@Test
	public void projectileWithoutModelIsRejectedBeforeProjection()
	{
		LocalPoint player = point(0, 0, 0);
		Projectile projectile = TestProxies.proxy(Projectile.class,
			TestProxies.method("getX", 128d),
			TestProxies.method("getY", 0d),
			TestProxies.method("getFloor", 0),
			TestProxies.method("getModel", null));

		assertFalse(eligibility(0, false, 10).projectile(player, projectile, VIEWPORT));
	}

	private static BillboardTargetEligibility eligibility(int plane, boolean allPlanes, int radius)
	{
		Client client = TestProxies.proxy(Client.class, TestProxies.method("getPlane", plane));
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("renderBillboardsOnAllPlanes", allPlanes),
			TestProxies.method("billboardRadiusTiles", radius));
		return new BillboardTargetEligibility(client, config, null, request -> null, (renderable, request) -> false);
	}

	private static Actor actor(LocalPoint location, int plane, Polygon tilePolygon)
	{
		WorldView worldView = worldView(plane);
		return TestProxies.proxy(Actor.class,
			TestProxies.method("getLocalLocation", location),
			TestProxies.method("getWorldView", worldView),
			TestProxies.method("getCanvasTilePoly", tilePolygon));
	}

	private static Polygon visibleTile()
	{
		return new Polygon(new int[] {10, 20, 20}, new int[] {10, 10, 20}, 3);
	}

	private static LocalPoint point(int x, int y, int plane)
	{
		return new LocalPoint(x, y, worldView(plane));
	}

	private static WorldView worldView(int plane)
	{
		return TestProxies.proxy(WorldView.class, TestProxies.method("getPlane", plane));
	}
}
