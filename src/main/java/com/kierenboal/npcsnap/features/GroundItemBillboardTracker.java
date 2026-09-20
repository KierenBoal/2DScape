package com.kierenboal.npcsnap.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.ItemLayer;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

public final class GroundItemBillboardTracker
{
	private final Map<TileItem, GroundItemBillboard> groundItems = new HashMap<>();

	public void track(TileItem item, Tile tile)
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

		ItemLayer itemLayer = tile.getItemLayer();
		int verticalOffset = itemLayer != null ? itemLayer.getHeight() : 0;
		groundItems.put(item, new GroundItemBillboard(tile.getPlane(), localPoint, verticalOffset));
	}

	public void untrack(TileItem item)
	{
		if (item == null)
		{
			return;
		}

		groundItems.remove(item);
	}

	public void clear()
	{
		groundItems.clear();
	}

	public GroundItemBillboard get(TileItem item)
	{
		return groundItems.get(item);
	}

	public Set<Map.Entry<TileItem, GroundItemBillboard>> entries()
	{
		return groundItems.entrySet();
	}

	public Set<TileItem> items()
	{
		return groundItems.keySet();
	}

	public void seed(WorldView worldView)
	{
		forEachSceneGroundItem(worldView, this::track);
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
