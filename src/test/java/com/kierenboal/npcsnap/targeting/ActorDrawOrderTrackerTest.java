package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;
import net.runelite.api.Actor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActorDrawOrderTrackerTest
{
	@Test
	public void laterSceneDrawWinsAfterCompletedFrame()
	{
		Actor first = TestProxies.proxy(Actor.class);
		Actor second = TestProxies.proxy(Actor.class);
		ActorDrawOrderTracker tracker = new ActorDrawOrderTracker();
		tracker.record(first);
		tracker.record(second);
		tracker.advanceFrame();

		assertTrue(tracker.drawnAfter(second, first));
		assertFalse(tracker.drawnAfter(first, second));
		assertTrue(tracker.preferred(first, second, null) == second);
		assertTrue(tracker.preferred(second, first, null) == second);
	}

	@Test
	public void currentFrameCannotChangeCompletedSceneOrder()
	{
		Actor first = TestProxies.proxy(Actor.class);
		Actor second = TestProxies.proxy(Actor.class);
		ActorDrawOrderTracker tracker = new ActorDrawOrderTracker();
		tracker.record(first);
		tracker.record(second);
		tracker.advanceFrame();
		tracker.record(first);

		assertTrue(tracker.drawnAfter(second, first));
		tracker.advanceFrame();
		assertTrue(tracker.drawnAfter(first, second));
	}

	@Test
	public void localPlayerWinsEvenWhenDrawnEarlier()
	{
		Actor localPlayer = TestProxies.proxy(Actor.class);
		Actor other = TestProxies.proxy(Actor.class);
		ActorDrawOrderTracker tracker = new ActorDrawOrderTracker();
		tracker.record(localPlayer);
		tracker.record(other);
		tracker.advanceFrame();

		assertTrue(tracker.preferred(localPlayer, other, localPlayer) == localPlayer);
		assertTrue(tracker.preferred(other, localPlayer, localPlayer) == localPlayer);
	}
}
