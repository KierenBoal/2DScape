package com.kierenboal.npcsnap.export;

import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import com.kierenboal.npcsnap.targeting.ObservedTileObjectBuilder;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.MenuEntry;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class BillboardObjectExportResolverTest
{
	private static final int OBJECT_ID = 1234;
	private final Tile[][][] tiles = new Tile[4][10][10];
	private final Scene scene = proxy(Scene.class, method("getTiles", tiles));
	private final WorldView view = proxy(WorldView.class, method("getId", WorldView.TOPLEVEL),
		method("getScene", scene), method("getPlane", 0));
	private final Client client = proxy(Client.class, method("getWorldView", view));

	@Test
	public void multiTileObjectExportsFromEveryOccupiedTileInsteadOfOnlyItsCenter()
	{
		GameObject object = gameObject(view, 2, 3, 5, 6);
		Tile tile = proxy(Tile.class, method("getGameObjects", new GameObject[] { object }));
		for (int x = 2; x <= 5; x++)
		{
			for (int y = 3; y <= 6; y++)
			{
				tiles[0][x][y] = tile;
				assertSame(object, BillboardObjectExportResolver.resolve(client, target(x, y)));
			}
		}
		ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
		assertNotNull(observed);
		assertEquals(1, observed.parts.size());
		assertNull(BillboardObjectExportResolver.resolve(client, target(1, 3)));
	}

	@Test
	public void offsetDecorationsResolveAtTheirOwningTileAndRetainBothParts()
	{
		DecorativeObject object = proxy(DecorativeObject.class,
			method("getId", OBJECT_ID), method("getWorldView", view),
			method("getLocalLocation", LocalPoint.fromScene(2, 3, view)),
			method("getRenderable", proxy(Renderable.class)), method("getRenderable2", proxy(Renderable.class)),
			method("getXOffset", 256), method("getYOffset2", -256));
		tiles[0][2][3] = proxy(Tile.class, method("getDecorativeObject", object));
		assertSame(object, BillboardObjectExportResolver.resolve(client, target(2, 3)));
		ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
		assertEquals(2, observed.parts.size());
		assertEquals(4, observed.parts.get(0).localPoint.getSceneX());
		assertEquals(1, observed.parts.get(1).localPoint.getSceneY());
	}

	@Test
	public void wallsAndGroundObjectsResolveWithoutBillboardSelectionOrClassification()
	{
		WallObject wall = proxy(WallObject.class, method("getId", OBJECT_ID), method("getWorldView", view),
			method("getLocalLocation", LocalPoint.fromScene(2, 3, view)),
			method("getRenderable1", proxy(Renderable.class)), method("getRenderable2", proxy(Renderable.class)));
		tiles[0][2][3] = proxy(Tile.class, method("getWallObject", wall));
		assertSame(wall, BillboardObjectExportResolver.resolve(client, target(2, 3)));
		assertEquals(2, ObservedTileObjectBuilder.build(wall).parts.size());
		GroundObject ground = proxy(GroundObject.class, method("getId", OBJECT_ID), method("getWorldView", view),
			method("getLocalLocation", LocalPoint.fromScene(2, 3, view)));
		tiles[0][2][3] = proxy(Tile.class, method("getGroundObject", ground));
		assertSame(ground, BillboardObjectExportResolver.resolve(client, target(2, 3)));
	}

	@Test
	public void duplicateIdsMustMatchFootprintPlaneAndWorldView()
	{
		WorldView otherView = proxy(WorldView.class, method("getId", 1));
		GameObject elsewhere = gameObject(view, 6, 6, 7, 7);
		GameObject otherWorld = gameObject(otherView, 2, 3, 5, 6);
		GameObject correct = gameObject(view, 2, 3, 5, 6);
		GameObject otherPlane = proxy(GameObject.class, method("getId", OBJECT_ID), method("getWorldView", view),
			method("getPlane", 1), method("getSceneMinLocation", new Point(2, 3)),
			method("getSceneMaxLocation", new Point(5, 6)));
		tiles[0][2][3] = proxy(Tile.class,
			method("getGameObjects", new GameObject[] { elsewhere, otherWorld, otherPlane, correct }));
		assertSame(correct, BillboardObjectExportResolver.resolve(client, target(2, 3)));
		assertNull(BillboardObjectExportResolver.resolve(client,
			new BillboardObjectExportTarget(OBJECT_ID, 2, 3, 1, 0)));
		assertNull(BillboardObjectExportResolver.resolve(client,
			new BillboardObjectExportTarget(OBJECT_ID, 2, 3, WorldView.TOPLEVEL, 1)));
	}

	@Test
	public void snapshotsSurviveMenuEntryReuseButNotTargetDespawn()
	{
		AtomicInteger id = new AtomicInteger(OBJECT_ID);
		AtomicInteger x = new AtomicInteger(2);
		MenuEntry entry = proxy(MenuEntry.class, methodSupplier("getIdentifier", id::get),
			methodSupplier("getParam0", x::get), method("getParam1", 3), method("getWorldViewId", WorldView.TOPLEVEL));
		BillboardObjectExportTarget snapshot = BillboardObjectExportTarget.snapshot(client, entry);
		GameObject object = gameObject(view, 2, 3, 5, 6);
		tiles[0][2][3] = proxy(Tile.class, method("getGameObjects", new GameObject[] { object }));
		id.set(99);
		x.set(8);
		assertSame(object, BillboardObjectExportResolver.resolve(client, snapshot));
		tiles[0][2][3] = null;
		assertNull(BillboardObjectExportResolver.resolve(client, snapshot));
	}

	@Test
	public void examineCanResolveActiveObjectTransform()
	{
		GameObject object = gameObject(view, 2, 3, 5, 6);
		tiles[0][2][3] = proxy(Tile.class, method("getGameObjects", new GameObject[] { object }));
		ObjectComposition definition = proxy(ObjectComposition.class, method("getImpostorIds", new int[] { 4321 }),
			method("getImpostor", proxy(ObjectComposition.class, method("getId", 4321))));
		Client transformedClient = proxy(Client.class, method("getWorldView", view), method("getObjectDefinition", definition));
		assertSame(object, BillboardObjectExportResolver.resolve(transformedClient,
			new BillboardObjectExportTarget(4321, 2, 3, WorldView.TOPLEVEL, 0)));
	}

	@Test
	public void invalidCoordinatesAndMissingSceneReturnUnavailable()
	{
		assertNull(BillboardObjectExportResolver.resolve(client, target(-1, 3)));
		assertNull(BillboardObjectExportResolver.resolve(client, target(2, 10)));
		assertNull(BillboardObjectExportResolver.resolve(proxy(Client.class), target(2, 3)));
		assertNull(BillboardObjectExportResolver.resolve(proxy(Client.class,
			method("getWorldView", proxy(WorldView.class, method("getId", WorldView.TOPLEVEL)))), target(2, 3)));
	}

	private static GameObject gameObject(WorldView view, int minX, int minY, int maxX, int maxY)
	{
		return proxy(GameObject.class, method("getId", OBJECT_ID), method("getWorldView", view),
			method("getSceneMinLocation", new Point(minX, minY)), method("getSceneMaxLocation", new Point(maxX, maxY)),
			method("getLocalLocation", new LocalPoint((minX + maxX + 1) * 64, (minY + maxY + 1) * 64)),
			method("getRenderable", proxy(Renderable.class)));
	}

	private static BillboardObjectExportTarget target(int x, int y)
	{
		return new BillboardObjectExportTarget(OBJECT_ID, x, y, WorldView.TOPLEVEL, 0);
	}
}
