package com.kierenboal.npcsnap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class GroundItemBillboardTrackerTest
{
	@Test
	public void trackStoresGroundItemPlaneAndLocation()
	{
		GroundItemBillboardTracker tracker = new GroundItemBillboardTracker();
		TileItem item = proxy(TileItem.class);
		LocalPoint localPoint = new LocalPoint(128, 256);
		Tile tile = tile(2, localPoint, Arrays.asList(item));

		tracker.track(item, tile);

		GroundItemBillboard billboard = tracker.get(item);
		Assert.assertNotNull(billboard);
		Assert.assertEquals(2, billboard.plane);
		Assert.assertEquals(localPoint, billboard.localPoint);
	}

	@Test
	public void trackIgnoresTilesWithoutLocalLocation()
	{
		GroundItemBillboardTracker tracker = new GroundItemBillboardTracker();
		TileItem item = proxy(TileItem.class);
		Tile tile = tile(0, null, Arrays.asList(item));

		tracker.track(item, tile);

		Assert.assertNull(tracker.get(item));
	}

	@Test
	public void syncRemovesItemsNoLongerPresentInScene()
	{
		GroundItemBillboardTracker tracker = new GroundItemBillboardTracker();
		TileItem staleItem = proxy(TileItem.class);
		TileItem currentItem = proxy(TileItem.class);
		Tile staleTile = tile(0, new LocalPoint(128, 128), Arrays.asList(staleItem));
		Tile currentTile = tile(0, new LocalPoint(256, 256), Arrays.asList(currentItem));
		List<TileItem> removedItems = new ArrayList<>();

		tracker.track(staleItem, staleTile);
		tracker.sync(worldView(currentTile), removedItems::add);

		Assert.assertNull(tracker.get(staleItem));
		Assert.assertNotNull(tracker.get(currentItem));
		Assert.assertEquals(Arrays.asList(staleItem), removedItems);
	}

	@Test
	public void syncClearsTrackedItemsWhenSceneIsUnavailable()
	{
		GroundItemBillboardTracker tracker = new GroundItemBillboardTracker();
		TileItem item = proxy(TileItem.class);
		Tile tile = tile(0, new LocalPoint(128, 128), Arrays.asList(item));
		List<TileItem> removedItems = new ArrayList<>();

		tracker.track(item, tile);
		tracker.sync(proxy(WorldView.class), removedItems::add);

		Assert.assertNull(tracker.get(item));
		Assert.assertEquals(Arrays.asList(item), removedItems);
	}

	private static WorldView worldView(Tile... tiles)
	{
		Tile[][][] sceneTiles = new Tile[][][] { { tiles } };
		Scene scene = proxy(Scene.class, method("getTiles", sceneTiles));
		return proxy(WorldView.class, method("getScene", scene));
	}

	private static Tile tile(int plane, LocalPoint localPoint, Collection<TileItem> items)
	{
		return proxy(
			Tile.class,
			method("getPlane", plane),
			method("getLocalLocation", localPoint),
			method("getGroundItems", items)
		);
	}
}
