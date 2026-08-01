package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class BoatSceneCollectorTest
{
	@Test
	public void collectsBoatObjectModelsAtProjectedMainWorldLocations()
	{
		LocalPoint nestedPoint = new LocalPoint(128, 256);
		LocalPoint mainPoint = new LocalPoint(640, 768);
		Model model = TestProxies.proxy(Model.class);
		Renderable renderable = TestProxies.proxy(Renderable.class, method("getModel", model));
		GameObject gameObject = TestProxies.proxy(GameObject.class,
			method("getRenderable", renderable), method("getLocalLocation", nestedPoint), method("getPlane", 0));
		Tile tile = TestProxies.proxy(Tile.class, method("getGameObjects", new GameObject[] {gameObject}));
		Scene scene = TestProxies.proxy(Scene.class, method("getTiles", new Tile[][][] {{{tile}}}));
		WorldView nested = TestProxies.proxy(WorldView.class, method("getScene", scene));
		WorldEntity entity = TestProxies.proxy(WorldEntity.class,
			method("getWorldView", nested), method("transformToMainWorld", mainPoint));

		ObservedTileObject boat = BoatSceneCollector.collect(entity);

		assertNotNull(boat);
		assertEquals(ClassifiedObjectType.BOAT, boat.classifiedType);
		assertEquals(1, boat.parts.size());
		assertSame(renderable, boat.parts.get(0).renderable);
		assertSame(nestedPoint, boat.parts.get(0).localPoint);
	}

	@Test
	public void convertsUntexturedTilePaintIntoBoatFaces()
	{
		WorldView nested = TestProxies.proxy(WorldView.class,
			method("getTileHeight", 0));
		LocalPoint tilePoint = new LocalPoint(128, 128, nested);
		SceneTilePaint paint = TestProxies.proxy(SceneTilePaint.class,
			method("getTexture", -1), method("getSwColor", 1), method("getSeColor", 2),
			method("getNeColor", 3), method("getNwColor", 4));
		Tile tile = TestProxies.proxy(Tile.class, method("getLocalLocation", tilePoint),
			method("getPlane", 0), method("getSceneTilePaint", paint));
		Scene scene = TestProxies.proxy(Scene.class, method("getTiles", new Tile[][][] {{{tile}}}));
		nested = TestProxies.proxy(WorldView.class, method("getScene", scene), method("getTileHeight", 0));
		WorldEntity entity = TestProxies.proxy(WorldEntity.class,
			method("getWorldView", nested), method("getLocalLocation", new LocalPoint(0, 0)),
			method("transformToMainWorld", new LocalPoint(64, 64)));

		assertEquals(2, BoatSceneCollector.collectTileFaces(entity, tilePoint).size());
	}

	@Test
	public void convertsTexturedTilePaintIntoBoatFaces()
	{
		WorldView nested = TestProxies.proxy(WorldView.class, method("getTileHeight", 0));
		LocalPoint tilePoint = new LocalPoint(128, 128, nested);
		SceneTilePaint paint = TestProxies.proxy(SceneTilePaint.class,
			method("getTexture", 12), method("getSwColor", 1), method("getSeColor", 2),
			method("getNeColor", 3), method("getNwColor", 4));
		Tile tile = TestProxies.proxy(Tile.class, method("getLocalLocation", tilePoint),
			method("getPlane", 0), method("getSceneTilePaint", paint));
		Scene scene = TestProxies.proxy(Scene.class, method("getTiles", new Tile[][][] {{{tile}}}));
		nested = TestProxies.proxy(WorldView.class, method("getScene", scene), method("getTileHeight", 0));
		WorldEntity entity = TestProxies.proxy(WorldEntity.class,
			method("getWorldView", nested), method("getLocalLocation", new LocalPoint(0, 0)));

		assertEquals(2, BoatSceneCollector.collectTileFaces(entity, tilePoint).size());
	}
}
