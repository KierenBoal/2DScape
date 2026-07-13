package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Actor;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActorStackTrackerTest
{
	private final Actor first = TestProxies.proxy(Actor.class);
	private final Actor second = TestProxies.proxy(Actor.class);
	private final Actor replacement = TestProxies.proxy(Actor.class);
	private final OccupiedTileKey tile = OccupiedTileKey.of(new LocalPoint(128, 128), 0);

	@Test
	public void overlapMustPersistForCompleteTickIntervalBeforeConfirmation()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		Map<OccupiedTileKey, List<Actor>> actorsByTile = actors(first, second);

		assertFalse(tracker.confirmedStackedTiles(actorsByTile, 10).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actorsByTile, 10).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actorsByTile, 11).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actorsByTile, 12).contains(tile));
	}

	@Test
	public void changedActorMembershipRestartsConfirmationDelay()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), 20).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), 21).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), 22).contains(tile));

		assertFalse(tracker.confirmedStackedTiles(actors(first, replacement), 22).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, replacement), 23).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, replacement), 24).contains(tile));
	}

	@Test
	public void leavingAndReenteringTileRestartsConfirmationDelay()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), 30).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), 31).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), 32).contains(tile));

		Set<OccupiedTileKey> separated = tracker.confirmedStackedTiles(Collections.emptyMap(), 32);
		assertTrue(separated.isEmpty());
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), 33).contains(tile));
	}

	private Map<OccupiedTileKey, List<Actor>> actors(Actor... actors)
	{
		Map<OccupiedTileKey, List<Actor>> result = new HashMap<>();
		result.put(tile, Arrays.asList(actors));
		return result;
	}
}
