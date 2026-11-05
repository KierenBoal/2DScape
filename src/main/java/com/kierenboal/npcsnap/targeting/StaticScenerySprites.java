package com.kierenboal.npcsnap.targeting;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.hooks.DrawCallbacks;

/** Tracks the static placements that the GPU uploader has agreed to suppress. */
public final class StaticScenerySprites
{
	private static final int ZONE_SIZE = 8;
	private static final int SCENE_OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
	private final Map<TileObject, ObservedTileObject> objects = new IdentityHashMap<>();
	private final Set<TileObject> uploadedAsSprite = Collections.newSetFromMap(new IdentityHashMap<>());
	private Set<TileObject> desired = Collections.emptySet();
	private Scene scene;

	public synchronized void useScene(Scene current)
	{
		if (scene == current)
		{
			return;
		}
		scene = current;
		objects.clear();
		uploadedAsSprite.clear();
		desired = Collections.emptySet();
		if (scene == null)
		{
			return;
		}
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] row : plane)
			{
				for (Tile tile : row)
				{
					seed(tile);
					if (tile != null)
					{
						seed(tile.getBridge());
					}
				}
			}
		}
	}

	private void seed(Tile tile)
	{
		if (tile == null || tile.getGameObjects() == null)
		{
			return;
		}
		for (GameObject object : tile.getGameObjects())
		{
			if (object != null && tile.getSceneLocation().equals(object.getSceneMinLocation()))
			{
				add(scene, object);
			}
		}
	}

	public synchronized void add(Scene owner, GameObject object)
	{
		if (scene != null && scene == owner && object.getRenderable() instanceof Model)
		{
			ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
			if (observed != null)
			{
				objects.put(object, observed);
			}
		}
	}

	public synchronized void remove(GameObject object)
	{
		objects.remove(object);
		uploadedAsSprite.remove(object);
	}

	public synchronized void addCandidates(Map<TileObject, ObservedTileObject> candidates)
	{
		objects.forEach(candidates::putIfAbsent);
	}

	public synchronized boolean manages(TileObject object)
	{
		return objects.containsKey(object);
	}

	/** Null leaves the ordinary draw callback in charge. */
	public synchronized Boolean drawObject(Scene owner, TileObject object)
	{
		if (scene == null || scene != owner || !objects.containsKey(object))
		{
			return null;
		}
		if (desired.contains(object))
		{
			uploadedAsSprite.add(object);
			return false;
		}
		uploadedAsSprite.remove(object);
		return true;
	}

	public synchronized boolean isReplaced(TileObject object)
	{
		return uploadedAsSprite.contains(object);
	}

	public void update(Set<TileObject> drawable, DrawCallbacks renderer)
	{
		Set<Point> zones = new HashSet<>();
		Scene current;
		synchronized (this)
		{
			current = scene;
			if (current == null || renderer == null)
			{
				return;
			}
			Set<TileObject> next = Collections.newSetFromMap(new IdentityHashMap<>());
			for (TileObject object : drawable)
			{
				if (objects.containsKey(object))
				{
					next.add(object);
				}
			}
			for (TileObject object : next)
			{
				if (!uploadedAsSprite.contains(object)) zones.add(zone((GameObject) object));
			}
			for (TileObject object : uploadedAsSprite)
			{
				if (!next.contains(object)) zones.add(zone((GameObject) object));
			}
			desired = next;
		}
		for (Point zone : zones)
		{
			renderer.invalidateZone(current, zone.getX(), zone.getY());
		}
	}

	private static Point zone(GameObject object)
	{
		Point tile = object.getSceneMinLocation();
		return new Point((tile.getX() + SCENE_OFFSET) / ZONE_SIZE, (tile.getY() + SCENE_OFFSET) / ZONE_SIZE);
	}
}
