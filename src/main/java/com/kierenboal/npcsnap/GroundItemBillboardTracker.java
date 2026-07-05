package com.kierenboal.npcsnap;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

final class GroundItemBillboardTracker
{
	private final Map<TileItem, GroundItemBillboard> groundItems = new HashMap<>();

	void track(TileItem item, Tile tile)
	{
		if (item == null || tile == null)
		{
			return;
		}

		LocalPoint localPoint = tile.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		groundItems.put(item, new GroundItemBillboard(tile.getPlane(), localPoint));
	}

	void untrack(TileItem item)
	{
		if (item == null)
		{
			return;
		}

		groundItems.remove(item);
	}

	void clear()
	{
		groundItems.clear();
	}

	GroundItemBillboard get(TileItem item)
	{
		return groundItems.get(item);
	}

	Set<Map.Entry<TileItem, GroundItemBillboard>> entries()
	{
		return groundItems.entrySet();
	}

	Set<TileItem> items()
	{
		return groundItems.keySet();
	}

	void seed(WorldView worldView)
	{
		forEachSceneGroundItem(worldView, this::track);
	}

	void sync(WorldView worldView, Consumer<TileItem> staleItemConsumer)
	{
		if (worldView == null || worldView.getScene() == null)
		{
			clearTrackedItems(staleItemConsumer);
			return;
		}

		Set<TileItem> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
		forEachSceneGroundItem(worldView, (item, tile) ->
		{
			seenItems.add(item);
			track(item, tile);
		});

		groundItems.keySet().removeIf(item ->
		{
			boolean stale = !seenItems.contains(item);
			if (stale)
			{
				staleItemConsumer.accept(item);
			}
			return stale;
		});
	}

	private void clearTrackedItems(Consumer<TileItem> itemConsumer)
	{
		for (TileItem item : groundItems.keySet())
		{
			itemConsumer.accept(item);
		}

		groundItems.clear();
	}

	private static void forEachSceneGroundItem(WorldView worldView, GroundItemConsumer groundItemConsumer)
	{
		if (worldView == null)
		{
			return;
		}

		Scene scene = worldView.getScene();
		if (scene == null || scene.getTiles() == null)
		{
			return;
		}

		for (Tile[][] planeTiles : scene.getTiles())
		{
			if (planeTiles == null)
			{
				continue;
			}

			for (Tile[] row : planeTiles)
			{
				trackGroundItems(row, groundItemConsumer);
			}
		}
	}

	private static void trackGroundItems(Tile[] row, GroundItemConsumer groundItemConsumer)
	{
		if (row == null)
		{
			return;
		}

		for (Tile tile : row)
		{
			if (tile == null || tile.getGroundItems() == null)
			{
				continue;
			}

			for (TileItem item : tile.getGroundItems())
			{
				if (item != null)
				{
					groundItemConsumer.accept(item, tile);
				}
			}
		}
	}

	private interface GroundItemConsumer
	{
		void accept(TileItem item, Tile tile);
	}
}
