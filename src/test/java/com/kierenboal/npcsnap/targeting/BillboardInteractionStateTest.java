package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.Player;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class BillboardInteractionStateTest
{
	@Test
	public void keepsClickedActorThroughClickTickAndMatchingInteraction()
	{
		Actor actor = TestProxies.proxy(Actor.class);
		Player interactingPlayer = TestProxies.proxy(Player.class, TestProxies.method("getInteracting", actor));
		BillboardInteractionState state = new BillboardInteractionState();

		state.noteClick(actor, 10);
		state.clearIfStale(null, 10);
		assertSame(actor, state.clickedActor());
		state.clearIfStale(interactingPlayer, 11);
		assertSame(actor, state.clickedActor());
	}

	@Test
	public void clearsClickedActorWhenInteractionDoesNotMaterializeOnLaterTick()
	{
		Actor actor = TestProxies.proxy(Actor.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.noteClick(actor, 10);
		state.clearIfStale(null, 11);

		assertSame(null, state.clickedActor());
	}

	@Test
	public void ignoresInteractionChangeDuringClickTickButClearsItLater()
	{
		Actor actor = TestProxies.proxy(Actor.class);
		Actor other = TestProxies.proxy(Actor.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.noteClick(actor, 10);
		state.onLocalPlayerInteractionChanged(other, 10);
		assertSame(actor, state.clickedActor());
		state.onLocalPlayerInteractionChanged(other, 11);
		assertSame(null, state.clickedActor());
	}

	@Test
	public void onlyMatchingDespawnClearsClickedActor()
	{
		Actor actor = TestProxies.proxy(Actor.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.noteClick(actor, 10);
		state.clearIfMatches(TestProxies.proxy(Actor.class));
		assertSame(actor, state.clickedActor());
		state.clearIfMatches(actor);
		assertSame(null, state.clickedActor());
	}
}
