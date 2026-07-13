package com.kierenboal.npcsnap.targeting;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Actor;

public final class ActorStackTracker
{
	private static final int REQUIRED_TICK_ADVANCES = 2;
	private final Map<OccupiedTileKey, Overlap> overlaps = new HashMap<>();

	public Set<OccupiedTileKey> confirmedStackedTiles(Map<OccupiedTileKey, List<Actor>> actorsByTile, int tickCount)
	{
		Set<OccupiedTileKey> observedStackedTiles = new HashSet<>();
		Set<OccupiedTileKey> confirmed = new HashSet<>();
		if (actorsByTile != null)
		{
			for (Map.Entry<OccupiedTileKey, List<Actor>> entry : actorsByTile.entrySet())
			{
				OccupiedTileKey tile = entry.getKey();
				List<Actor> actors = entry.getValue();
				if (tile == null || actors == null || actors.size() < 2)
				{
					continue;
				}

				observedStackedTiles.add(tile);
				Overlap overlap = overlaps.get(tile);
				if (overlap == null || !overlap.matches(actors) || tickCount < overlap.firstObservedTick)
				{
					overlaps.put(tile, new Overlap(actors, tickCount));
					continue;
				}

				// Waiting for two tick-count advances guarantees at least one complete
				// game-tick interval elapsed after the overlap was first observed. A
				// one-tile walk-through therefore never briefly collapses the actors.
				if (tickCount - overlap.firstObservedTick >= REQUIRED_TICK_ADVANCES)
				{
					confirmed.add(tile);
				}
			}
		}

		Iterator<OccupiedTileKey> iterator = overlaps.keySet().iterator();
		while (iterator.hasNext())
		{
			if (!observedStackedTiles.contains(iterator.next()))
			{
				iterator.remove();
			}
		}
		return confirmed;
	}

	public void clear()
	{
		overlaps.clear();
	}

	private static final class Overlap
	{
		private final Set<Actor> actors = Collections.newSetFromMap(new IdentityHashMap<>());
		private final int firstObservedTick;

		private Overlap(List<Actor> actors, int firstObservedTick)
		{
			this.actors.addAll(actors);
			this.firstObservedTick = firstObservedTick;
		}

		private boolean matches(List<Actor> currentActors)
		{
			if (currentActors.size() != actors.size())
			{
				return false;
			}

			for (Actor actor : currentActors)
			{
				if (!actors.contains(actor))
				{
					return false;
				}
			}
			return true;
		}
	}
}
