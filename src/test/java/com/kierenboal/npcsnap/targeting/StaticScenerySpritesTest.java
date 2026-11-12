package com.kierenboal.npcsnap.targeting;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.runelite.api.Constants;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.hooks.DrawCallbacks;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class StaticScenerySpritesTest
{
	@Test
	public void swapsOnlyReadyPlacementsAfterUploadAndCoalescesZoneRestoration()
	{
		GameObject first = tree(1);
		GameObject second = tree(2);
		Scene scene = scene(first, second);
		StaticScenerySprites scenery = new StaticScenerySprites();
		scenery.useScene(scene);
		var candidates = new IdentityHashMap<TileObject, ObservedTileObject>();
		scenery.addCandidates(candidates, "second only", object -> object == second);
		assertEquals("Only wanted placements become candidates", Set.of(second), candidates.keySet());
		scenery.addCandidates(candidates, "everything", object -> true);
		assertEquals(2, candidates.size());

		List<Point> rebuilds = new ArrayList<>();
		DrawCallbacks renderer = new DrawCallbacks()
		{
			@Override public void draw(int overlayColor) {}
			@Override public void swapScene(Scene scene) {}
			@Override public void invalidateZone(Scene owner, int x, int z)
			{
				assertSame(scene, owner);
				rebuilds.add(new Point(x, z));
			}
		};
		scenery.update(Set.of(first), renderer);
		int offset = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
		assertEquals(List.of(new Point((offset + 1) / 8, offset / 8)), rebuilds);
		assertFalse(scenery.isReplaced(first));
		assertEquals(Boolean.FALSE, scenery.drawObject(scene, first));
		assertEquals(Boolean.TRUE, scenery.drawObject(scene, second));
		assertTrue(scenery.isReplaced(first));
		assertFalse(scenery.isReplaced(second));

		scenery.update(Set.of(first, second), renderer);
		assertEquals(Boolean.FALSE, scenery.drawObject(scene, second));
		rebuilds.clear();
		scenery.update(Set.of(), renderer);
		assertEquals(1, rebuilds.size());
		assertTrue(scenery.isReplaced(first));
		assertEquals(Boolean.TRUE, scenery.drawObject(scene, first));
		assertEquals(Boolean.TRUE, scenery.drawObject(scene, second));
		assertFalse(scenery.isReplaced(first));
		assertFalse(scenery.isReplaced(second));
	}

	@Test
	public void sceneChangeAndDespawnDiscardOldPlacements()
	{
		GameObject first = tree(1);
		Scene oldScene = scene(first);
		GameObject second = tree(2);
		Scene newScene = scene(second);
		StaticScenerySprites scenery = new StaticScenerySprites();
		scenery.useScene(oldScene);
		scenery.useScene(newScene);
		assertNull(scenery.drawObject(oldScene, first));
		assertFalse(scenery.manages(first));
		assertTrue(scenery.manages(second));
		scenery.remove(second);
		assertNull(scenery.drawObject(newScene, second));
	}

	@Test
	public void filterOnlyRunsAgainWhenPlacementsOrItsStateChange()
	{
		GameObject first = tree(1);
		GameObject second = tree(2);
		Scene scene = scene(first, second);
		StaticScenerySprites scenery = new StaticScenerySprites();
		scenery.useScene(scene);
		AtomicInteger filtered = new AtomicInteger();
		Predicate<TileObject> notFirst = object ->
		{
			filtered.incrementAndGet();
			return object != first;
		};

		assertEquals(Set.of(second), candidates(scenery, "a", notFirst));
		assertEquals(2, filtered.get());
		assertEquals("An unchanged scene reuses the last answer", Set.of(second), candidates(scenery, "a", notFirst));
		assertEquals(2, filtered.get());

		assertEquals(Set.of(second), candidates(scenery, "b", notFirst));
		assertEquals("A new filter state must be asked again", 4, filtered.get());

		GameObject third = tree(3);
		scenery.add(scene, third);
		assertEquals(Set.of(second, third), candidates(scenery, "b", notFirst));
		assertEquals("A spawn must be filtered in", 7, filtered.get());

		scenery.remove(second);
		assertEquals(Set.of(third), candidates(scenery, "b", notFirst));
		assertEquals("A despawn must be dropped", 9, filtered.get());
		assertEquals(Set.of(third), candidates(scenery, "b", notFirst));
		assertEquals(9, filtered.get());
	}

	@Test
	public void sceneChangeForgetsTheLastFilteredPlacements()
	{
		GameObject first = tree(1);
		GameObject second = tree(2);
		StaticScenerySprites scenery = new StaticScenerySprites();
		scenery.useScene(scene(first));
		assertEquals(Set.of(first), candidates(scenery, "a", object -> true));

		scenery.useScene(scene(second));
		assertEquals(Set.of(second), candidates(scenery, "a", object -> true));
		scenery.useScene(null);
		assertEquals(Set.of(), candidates(scenery, "a", object -> true));
	}

	private static Set<TileObject> candidates(StaticScenerySprites scenery, Object filterState, Predicate<TileObject> filter)
	{
		var candidates = new IdentityHashMap<TileObject, ObservedTileObject>();
		scenery.addCandidates(candidates, filterState, filter);
		return candidates.keySet();
	}

	@Test
	public void diagnosticsCountEachSwapStageSinceTheSceneWasSeeded()
	{
		GameObject model = tree(1);
		GameObject baked = proxy(GameObject.class, method("getSceneMinLocation", new Point(2, 0)),
			method("getLocalLocation", new LocalPoint(2 * 128 + 64, 64)),
			method("getRenderable", proxy(DynamicObject.class, method("getModelZbuf", proxy(Model.class)))));
		GameObject animated = proxy(GameObject.class, method("getSceneMinLocation", new Point(3, 0)),
			method("getLocalLocation", new LocalPoint(3 * 128 + 64, 64)),
			method("getRenderable", proxy(DynamicObject.class)));
		Scene scene = scene(model, baked, animated);
		StaticScenerySprites scenery = new StaticScenerySprites();
		scenery.useScene(scene);
		DrawCallbacks renderer = new DrawCallbacks()
		{
			@Override public void draw(int overlayColor) {}
			@Override public void swapScene(Scene swapped) {}
		};

		scenery.update(Set.of(model), renderer);
		scenery.drawObject(scene, model);
		scenery.drawObject(scene, baked);
		StaticScenerySprites.Diagnostics counts = scenery.diagnostics();
		assertEquals(1, counts.tracked);
		assertEquals(1, counts.seenModels);
		assertEquals(1, counts.seenBakedDynamics);
		assertEquals(1, counts.seenOthers);
		assertEquals(1, counts.zonesInvalidated);
		assertEquals(1, counts.drawHidden);
		assertEquals(0, counts.drawShown);
		assertEquals(1, counts.drawUntracked);
		assertEquals(1, counts.replaced);

		scenery.useScene(scene(tree(4)));
		assertEquals(0, scenery.diagnostics().zonesInvalidated);
		assertEquals(0, scenery.diagnostics().drawHidden);
	}

	private static GameObject tree(int x)
	{
		return proxy(GameObject.class, method("getId", ObjectID.TREE),
			method("getSceneMinLocation", new Point(x, 0)), method("getLocalLocation", new LocalPoint(x * 128 + 64, 64)),
			method("getRenderable", proxy(Model.class)));
	}

	private static Scene scene(GameObject... objects)
	{
		Tile[][][] tiles = new Tile[1][8][1];
		for (GameObject object : objects)
		{
			Point location = object.getSceneMinLocation();
			tiles[0][location.getX()][0] = proxy(Tile.class, method("getSceneLocation", location),
				method("getGameObjects", new GameObject[] {object}));
		}
		return proxy(Scene.class, method("getTiles", tiles));
	}
}
