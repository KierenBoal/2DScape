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
	private final LocalPoint[] firstLocation = {new LocalPoint(128, 128)};
	private final LocalPoint[] secondLocation = {new LocalPoint(128, 128)};
	private final LocalPoint[] replacementLocation = {new LocalPoint(128, 128)};
	private final Actor first = actor(firstLocation);
	private final Actor second = actor(secondLocation);
	private final Actor replacement = actor(replacementLocation);
	private final OccupiedTileKey tile = OccupiedTileKey.of(new LocalPoint(128, 128), 0);

	@Test
	public void overlapMustPersistForCompleteTickIntervalBeforeConfirmation()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		Map<OccupiedTileKey, List<Actor>> actorsByTile = actors(first, second);

		assertFalse(tracker.confirmedStackedTiles(actorsByTile, top(first), 10).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actorsByTile, top(first), 10).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actorsByTile, top(first), 11).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actorsByTile, top(first), 12).contains(tile));
	}

	@Test
	public void changedActorMembershipRestartsConfirmationDelay()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 20).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 21).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(first), 22).contains(tile));

		assertFalse(tracker.confirmedStackedTiles(actors(first, replacement), top(first), 22).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, replacement), top(first), 23).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, replacement), top(first), 24).contains(tile));
	}

	@Test
	public void topActorMovementWithinTileReleasesImmediately()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 30).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 31).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(first), 32).contains(tile));

		firstLocation[0] = new LocalPoint(160, 128);
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 32).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 33).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(first), 34).contains(tile));
	}

	@Test
	public void topActorMovementBeforeConfirmationRestartsDelay()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 40).contains(tile));

		firstLocation[0] = new LocalPoint(160, 128);
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 40).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 41).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(first), 42).contains(tile));
	}

	@Test
	public void changedTopActorRestartsConfirmationDelay()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 50).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 51).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(first), 52).contains(tile));

		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(second), 52).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(second), 53).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(second), 54).contains(tile));
	}

	@Test
	public void leavingAndReenteringTileRestartsConfirmationDelay()
	{
		ActorStackTracker tracker = new ActorStackTracker();
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 60).contains(tile));
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 61).contains(tile));
		assertTrue(tracker.confirmedStackedTiles(actors(first, second), top(first), 62).contains(tile));

		Set<OccupiedTileKey> separated = tracker.confirmedStackedTiles(Collections.emptyMap(), Collections.emptyMap(), 62);
		assertTrue(separated.isEmpty());
		assertFalse(tracker.confirmedStackedTiles(actors(first, second), top(first), 63).contains(tile));
	}

	private Map<OccupiedTileKey, List<Actor>> actors(Actor... actors)
	{
		Map<OccupiedTileKey, List<Actor>> result = new HashMap<>();
		result.put(tile, Arrays.asList(actors));
		return result;
	}

	private Map<OccupiedTileKey, Actor> top(Actor actor)
	{
		Map<OccupiedTileKey, Actor> result = new HashMap<>();
		result.put(tile, actor);
		return result;
	}

	private static Actor actor(LocalPoint[] location)
	{
		return TestProxies.proxy(Actor.class,
			TestProxies.methodSupplier("getLocalLocation", () -> location[0]));
	}
}
