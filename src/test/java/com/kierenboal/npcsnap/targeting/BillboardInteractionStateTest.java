package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
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

	@Test
	public void groundItemInteractionPersistsUntilMatchingItemClears()
	{
		TileItem item = TestProxies.proxy(TileItem.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.noteGroundItemClick(item);
		state.clearIfMatches(TestProxies.proxy(TileItem.class));
		assertSame(item, state.clickedGroundItem());
		state.clearIfMatches(item);
		assertSame(null, state.clickedGroundItem());
	}
	@Test
	public void objectClickPersistsWhileWalkingAndClearsAfterArrivalOrReplacement()
	{
		net.runelite.api.TileObject object = TestProxies.proxy(net.runelite.api.TileObject.class);
		BillboardInteractionState state = new BillboardInteractionState();
		state.noteObjectClick(object, 10);
		net.runelite.api.Client sameTick = TestProxies.proxy(net.runelite.api.Client.class, TestProxies.method("getTickCount", 10));
		state.clearStaleObject(sameTick);
		assertSame(object, state.clickedObject());
		net.runelite.api.Client walking = TestProxies.proxy(net.runelite.api.Client.class,
			TestProxies.method("getTickCount", 11), TestProxies.method("getLocalDestinationLocation", new net.runelite.api.coords.LocalPoint(128, 128)));
		state.clearStaleObject(walking);
		assertSame(object, state.clickedObject());
		net.runelite.api.Client arrived = TestProxies.proxy(net.runelite.api.Client.class, TestProxies.method("getTickCount", 12));
		state.clearStaleObject(arrived);
		assertSame(null, state.clickedObject());
		state.noteObjectClick(object, 10);
		state.noteGroundItemClick(TestProxies.proxy(TileItem.class));
		assertSame(null, state.clickedObject());
		state.noteObjectClick(object, 10);
		state.noteClick(TestProxies.proxy(Actor.class), 11);
		assertSame(null, state.clickedObject());
		state.noteObjectClick(object, 10);
		state.clearIfMatches(TestProxies.proxy(net.runelite.api.TileObject.class));
		assertSame(object, state.clickedObject());
		state.clearIfMatches(object);
		assertSame(null, state.clickedObject());
		state.noteObjectClick(object, 10);
		state.clear();
		assertSame(null, state.clickedObject());
	}

	@Test
	public void unloadingOwningWorldViewClearsTheSelectedObject()
	{
		net.runelite.api.WorldView view = TestProxies.proxy(net.runelite.api.WorldView.class);
		net.runelite.api.TileObject object = TestProxies.proxy(net.runelite.api.TileObject.class,
			TestProxies.method("getWorldView", view));
		BillboardInteractionState state = new BillboardInteractionState();
		state.noteObjectClick(object, 10);
		state.unloadWorldView(TestProxies.proxy(net.runelite.api.WorldView.class));
		assertSame(object, state.clickedObject());
		state.unloadWorldView(view);
		assertSame(null, state.clickedObject());
	}

}
