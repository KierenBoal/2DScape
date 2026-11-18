package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.IdentitySets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
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
	private final Set<TileObject> uploadedAsSprite = IdentitySets.create();
	private Set<TileObject> desired = Collections.emptySet();
	private Scene scene;
	// Moves whenever a placement is added or removed.
	private int revision;
	// The placements the candidate filter last accepted, and what that answer was based on.
	private List<Map.Entry<TileObject, ObservedTileObject>> wanted = Collections.emptyList();
	private int wantedRevision = -1;
	private Object wantedFilterState;
	public synchronized void useScene(Scene current)
	{
		if (scene == current)
		{
			return;
		}
		scene = current;
		objects.clear();
		revision++;
		// Dropped now, not at the next filter run, so an unused scene is not kept alive.
		wanted = Collections.emptyList();
		wantedFilterState = null;
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
		if (scene == null || scene != owner)
		{
			return;
		}
		Renderable renderable = object.getRenderable();
		if (renderable instanceof Model)
		{
			ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
			if (observed != null)
			{
				objects.put(object, observed);
				revision++;
			}
		}
	}

	public synchronized void remove(GameObject object)
	{
		if (objects.remove(object) != null)
		{
			revision++;
		}
		uploadedAsSprite.remove(object);
	}

	/**
	 * Adds the tracked placements the filter accepts. Static scenery rarely changes, so the
	 * filter only runs again once a placement is added or removed or filterState stops being
	 * equal to the one last passed; the caller must change it whenever the filter's answers may.
	 */
	public void addCandidates(Map<TileObject, ObservedTileObject> candidates, Object filterState,
		Predicate<TileObject> filter)
	{
		List<Map.Entry<TileObject, ObservedTileObject>> accepted;
		List<Map.Entry<TileObject, ObservedTileObject>> tracked = null;
		int trackedRevision;
		synchronized (this)
		{
			accepted = wanted;
			trackedRevision = revision;
			if (wantedRevision != revision || !filterState.equals(wantedFilterState))
			{
				// Copies each entry: the map's own are views into a table that keeps changing.
				tracked = new ArrayList<>(objects.entrySet());
			}
		}
		if (tracked != null)
		{
			// The filter may load object definitions; keep that off the lock the uploader waits on.
			accepted = new ArrayList<>();
			for (Map.Entry<TileObject, ObservedTileObject> entry : tracked)
			{
				if (filter.test(entry.getKey())) accepted.add(entry);
			}
			synchronized (this)
			{
				if (revision == trackedRevision)
				{
					wanted = accepted;
					wantedRevision = trackedRevision;
					wantedFilterState = filterState;
				}
			}
		}
		for (Map.Entry<TileObject, ObservedTileObject> entry : accepted)
		{
			candidates.putIfAbsent(entry.getKey(), entry.getValue());
		}
	}

	public synchronized boolean manages(TileObject object)
	{
		return objects.containsKey(object);
	}

	/** Null leaves the ordinary draw callback in charge. */
	public synchronized Boolean drawObject(Scene owner, TileObject object)
	{
		if (scene == null || scene != owner)
		{
			return null;
		}
		if (!objects.containsKey(object))
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
			Set<TileObject> next = IdentitySets.create();
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
