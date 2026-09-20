package com.kierenboal.npcsnap.features;

import com.kierenboal.npcsnap.TestProxies;

import java.util.Arrays;
import java.util.Collection;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.ItemLayer;
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
		Tile tile = tile(2, localPoint, Arrays.asList(item), 96);

		tracker.track(item, tile);

		GroundItemBillboard billboard = tracker.get(item);
		Assert.assertNotNull(billboard);
		Assert.assertEquals(2, billboard.plane);
		Assert.assertEquals(localPoint, billboard.localPoint);
		Assert.assertEquals(96, billboard.verticalOffset);
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
	public void seedTracksItemsInScene()
	{
		GroundItemBillboardTracker tracker = new GroundItemBillboardTracker();
		TileItem item = proxy(TileItem.class);
		Tile currentTile = tile(0, new LocalPoint(256, 256), Arrays.asList(item));

		tracker.seed(worldView(currentTile));

		Assert.assertNotNull(tracker.get(item));
	}

	@Test
	public void clearAllowsASeedForTheNextScene()
	{
		GroundItemBillboardTracker tracker = new GroundItemBillboardTracker();
		TileItem firstItem = proxy(TileItem.class);
		TileItem secondItem = proxy(TileItem.class);

		tracker.seed(worldView(tile(0, new LocalPoint(128, 128), Arrays.asList(firstItem))));
		tracker.clear();
		tracker.seed(worldView(tile(0, new LocalPoint(256, 256), Arrays.asList(secondItem))));

		Assert.assertNull(tracker.get(firstItem));
		Assert.assertNotNull(tracker.get(secondItem));
	}

	private static WorldView worldView(Tile... tiles)
	{
		Tile[][][] sceneTiles = new Tile[][][] { { tiles } };
		Scene scene = proxy(Scene.class, method("getTiles", sceneTiles));
		return proxy(WorldView.class, method("getScene", scene));
	}

	private static Tile tile(int plane, LocalPoint localPoint, Collection<TileItem> items)
	{
		return tile(plane, localPoint, items, 0);
	}

	private static Tile tile(int plane, LocalPoint localPoint, Collection<TileItem> items, int itemLayerHeight)
	{
		ItemLayer itemLayer = proxy(ItemLayer.class, method("getHeight", itemLayerHeight));
		return proxy(
			Tile.class,
			method("getPlane", plane),
			method("getLocalLocation", localPoint),
			method("getGroundItems", items),
			method("getItemLayer", itemLayer)
		);
	}
}
