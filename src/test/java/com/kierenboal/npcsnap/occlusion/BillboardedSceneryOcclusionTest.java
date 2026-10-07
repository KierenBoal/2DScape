package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class BillboardedSceneryOcclusionTest
{
	@Test
	public void excludesBillboardedObjectsBeforeReadingModelsAndKeepsOtherScenery()
	{
		Fixture fixture = new Fixture(true);
		int original = fixture.collect(Collections.emptySet());
		assertTrue("Both original models must contribute occlusion", original > 0);
		fixture.modelReads.set(0);
		assertEquals(original / 2, fixture.collect(Collections.singleton(fixture.first)));
		assertEquals("Only the remaining 3D object may query its model", 0, fixture.modelReads.get());
		assertEquals(0, fixture.collect(Set.of(fixture.first, fixture.second)));
		assertEquals("Disabling billboards must restore native scenery occlusion", original,
			fixture.collect(Collections.emptySet()));
	}

	@Test
	public void exclusionsAlsoSkipFallbackHullsWhenModelsAreUnavailable()
	{
		Fixture fixture = new Fixture(false);
		int original = fixture.collect(Collections.emptySet());
		assertTrue("Unavailable models must fall back to the object's hull", original > 0);
		fixture.modelReads.set(0);
		fixture.hullReads.set(0);
		assertEquals(0, fixture.collect(Set.of(fixture.first, fixture.second)));
		assertEquals(0, fixture.modelReads.get());
		assertEquals(0, fixture.hullReads.get());
	}

	private static final class Fixture
	{
		private final AtomicInteger modelReads = new AtomicInteger();
		private final AtomicInteger hullReads = new AtomicInteger();
		private final GameObject first;
		private final GameObject second;
		private final WorldView view;
		private final BillboardWorldOcclusionCollector collector;
		private final LocalPoint location = new LocalPoint(512, 1024);

		private Fixture(boolean availableModel)
		{
			Tile[][][] tiles = new Tile[4][16][16];
			Scene scene = proxy(Scene.class, method("getTiles", tiles));
			view = proxy(WorldView.class, method("getScene", scene),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]));
			Client client = proxy(Client.class, method("getWorldView", view), method("getTopLevelWorldView", view),
				method("getLocalPlayer", proxy(Player.class, method("getWorldView", view))),
				method("getCameraX", 512), method("getCameraFpX", 512.0f), method("getScale", 512),
				method("get3dZoom", 512), method("getViewportWidth", 800), method("getViewportHeight", 600));
			Model model = proxy(Model.class, method("getVerticesCount", 3), method("getFaceCount", 1),
				method("getVerticesX", new float[] {-100, 100, 0}),
				method("getVerticesY", new float[] {0, 0, -200}), method("getVerticesZ", new float[3]),
				method("getFaceIndices1", new int[] {0}), method("getFaceIndices2", new int[] {1}),
				method("getFaceIndices3", new int[] {2}), method("getFaceColors3", new int[] {5000}));
			first = object(proxy(Renderable.class, method("getModelHeight", 200),
				methodSupplier("getModel", () -> { modelReads.incrementAndGet(); return availableModel ? model : null; })));
			second = object(proxy(Renderable.class, method("getModelHeight", 200),
				method("getModel", availableModel ? model : null)));
			tiles[0][4][8] = proxy(Tile.class, method("getPlane", 0), method("getSceneLocation", new Point(4, 8)),
				method("getGameObjects", new GameObject[] {first, second}));
			collector = new BillboardWorldOcclusionCollector(client, new NpcSnapConfig() {},
				new BillboardDepthCalculator(client), new BillboardPerformanceMetrics(),
				org.slf4j.LoggerFactory.getLogger(getClass()));
		}

		private GameObject object(Renderable renderable)
		{
			return proxy(GameObject.class, method("getLocalLocation", location), method("getRenderable", renderable),
				method("getCanvasLocation", new Point(400, 300)),
				methodSupplier("getConvexHull", () -> { hullReads.incrementAndGet(); return new Rectangle(350, 200, 100, 100); }));
		}

		private int collect(Set<TileObject> billboardedObjects)
		{
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 800, 600), new Rectangle(0, 0, 800, 600));
			collector.collectTerrainOccluders(view, List.of(new BillboardWorldOcclusionCollector.TraceTarget(location, 0)),
				BillboardOcclusionQuality.HIGH, Collections.emptySet(), billboardedObjects);
			return collector.occluders().size();
		}
	}
}
