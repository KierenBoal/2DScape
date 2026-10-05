package com.kierenboal.npcsnap.export;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

public final class BillboardObjectExportResolver
{
	private BillboardObjectExportResolver()
	{
	}

	public static TileObject resolve(Client client, BillboardObjectExportTarget target)
	{
		WorldView view = client.getWorldView(target.worldViewId);
		if (view == null || view.getId() != target.worldViewId || view.getPlane() != target.plane
			|| view.getScene() == null)
		{
			return null;
		}
		Tile[][][] tiles = view.getScene().getTiles();
		if (tiles == null || target.plane < 0 || target.plane >= tiles.length
			|| tiles[target.plane] == null || target.sceneX < 0 || target.sceneX >= tiles[target.plane].length
			|| tiles[target.plane][target.sceneX] == null || target.sceneY < 0
			|| target.sceneY >= tiles[target.plane][target.sceneX].length)
		{
			return null;
		}
		Tile tile = tiles[target.plane][target.sceneX][target.sceneY];
		if (tile == null)
		{
			return null;
		}
		GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject object : objects)
			{
				if (matches(client, object, target))
				{
					return object;
				}
			}
		}
		if (matches(client, tile.getWallObject(), target))
		{
			return tile.getWallObject();
		}
		if (matches(client, tile.getDecorativeObject(), target))
		{
			return tile.getDecorativeObject();
		}
		return matches(client, tile.getGroundObject(), target) ? tile.getGroundObject() : null;
	}

	private static boolean matches(Client client, TileObject object, BillboardObjectExportTarget target)
	{
		if (object == null || object.getWorldView() == null
			|| object.getWorldView().getId() != target.worldViewId || object.getPlane() != target.plane)
		{
			return false;
		}
		if (object instanceof GameObject)
		{
			GameObject gameObject = (GameObject) object;
			Point min = gameObject.getSceneMinLocation();
			Point max = gameObject.getSceneMaxLocation();
			if (min == null || max == null || target.sceneX < min.getX() || target.sceneX > max.getX()
				|| target.sceneY < min.getY() || target.sceneY > max.getY())
			{
				return false;
			}
		}
		else
		{
			LocalPoint location = object.getLocalLocation();
			if (location == null || location.getSceneX() != target.sceneX || location.getSceneY() != target.sceneY)
			{
				return false;
			}
		}
		if (object.getId() == target.identifier)
		{
			return true;
		}
		// Examine menus can identify the active transform rather than the base object.
		ObjectComposition definition = client.getObjectDefinition(object.getId());
		ObjectComposition impostor = definition != null && definition.getImpostorIds() != null
			? definition.getImpostor() : null;
		return impostor != null && impostor.getId() == target.identifier;
	}
}
