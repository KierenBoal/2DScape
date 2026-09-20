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
import net.runelite.api.coords.LocalPoint;

public final class ActorStackTracker
{
	private static final int REQUIRED_TICK_ADVANCES = 2;
	private final Map<OccupiedTileKey, Overlap> overlaps = new HashMap<>();

	public Set<OccupiedTileKey> confirmedStackedTiles(
		Map<OccupiedTileKey, List<Actor>> actorsByTile,
		Map<OccupiedTileKey, Actor> topActorsByTile,
		int tickCount)
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
				Actor topActor = topActorsByTile != null ? topActorsByTile.get(tile) : null;
				Overlap overlap = overlaps.get(tile);
				if (overlap == null
					|| !overlap.matches(actors)
					|| !overlap.matchesTopActor(topActor)
					|| tickCount < overlap.firstObservedTick)
				{
					overlaps.put(tile, new Overlap(actors, topActor, tickCount));
					continue;
				}

				if (overlap.topActorMoved())
				{
					// The actor stack is no longer stable as soon as the selected top
					// actor's client-rendered position changes. Reset confirmation so
					// lower actors become eligible in this same frame and the stack
					// must settle again before it can be hidden.
					overlaps.put(tile, new Overlap(actors, topActor, tickCount));
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
		private final Actor topActor;
		private final int firstObservedTick;
		private final int lastTopX;
		private final int lastTopY;
		private final boolean hasTopLocation;

		private Overlap(List<Actor> actors, Actor topActor, int firstObservedTick)
		{
			this.actors.addAll(actors);
			this.topActor = topActor;
			this.firstObservedTick = firstObservedTick;

			LocalPoint topLocation = topActor != null ? topActor.getLocalLocation() : null;
			this.hasTopLocation = topLocation != null;
			this.lastTopX = topLocation != null ? topLocation.getX() : 0;
			this.lastTopY = topLocation != null ? topLocation.getY() : 0;
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

		private boolean matchesTopActor(Actor currentTopActor)
		{
			return topActor == currentTopActor;
		}

		private boolean topActorMoved()
		{
			if (!hasTopLocation || topActor == null)
			{
				return false;
			}

			LocalPoint currentLocation = topActor.getLocalLocation();
			return currentLocation != null
				&& (currentLocation.getX() != lastTopX || currentLocation.getY() != lastTopY);
		}
	}
}
