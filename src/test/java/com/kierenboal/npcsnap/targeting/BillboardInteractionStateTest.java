package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.TileItem;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class BillboardInteractionStateTest
{
	@Test
	public void preservesClickThenFollowsLocalPlayerInteraction()
	{
		Actor actor = TestProxies.proxy(Actor.class);
		Actor other = TestProxies.proxy(Actor.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.select(actor, 10, 0, false);
		state.onLocalPlayerInteractionChanged(other, 10);
		assertSame(actor, state.interacted);
		state.onLocalPlayerInteractionChanged(other, 11);
		assertSame(other, state.interacted);
	}

	@Test
	public void onlyMatchingDespawnClearsClickedActor()
	{
		Actor actor = TestProxies.proxy(Actor.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.select(actor, 10, 0, false);
		state.clearIfMatches(TestProxies.proxy(Actor.class));
		assertSame(actor, state.interacted);
		state.clearIfMatches(actor);
		assertSame(null, state.interacted);
	}

	@Test
	public void groundItemInteractionPersistsUntilMatchingItemClears()
	{
		TileItem item = TestProxies.proxy(TileItem.class);
		BillboardInteractionState state = new BillboardInteractionState();

		state.select(item, 10, 0, false);
		state.clearIfMatches(TestProxies.proxy(TileItem.class));
		assertSame(item, state.interacted);
		state.clearIfMatches(item);
		assertSame(null, state.interacted);
	}
	@Test
	public void objectClickPersistsWhileWalkingAndClearsAfterArrivalOrReplacement()
	{
		net.runelite.api.TileObject object = TestProxies.proxy(net.runelite.api.TileObject.class);
		BillboardInteractionState state = new BillboardInteractionState();
		state.select(object, 10, 0, false);
		net.runelite.api.Client sameTick = TestProxies.proxy(net.runelite.api.Client.class, TestProxies.method("getTickCount", 10));
		state.clearOnArrival(sameTick);
		assertSame(object, state.interacted);
		net.runelite.api.Client walking = TestProxies.proxy(net.runelite.api.Client.class,
			TestProxies.method("getTickCount", 11), TestProxies.method("getLocalDestinationLocation", new net.runelite.api.coords.LocalPoint(128, 128)));
		state.clearOnArrival(walking);
		assertSame(object, state.interacted);
		net.runelite.api.Client arrived = TestProxies.proxy(net.runelite.api.Client.class, TestProxies.method("getTickCount", 12));
		state.clearOnArrival(arrived);
		assertSame(null, state.interacted);
		state.select(object, 10, 0, false);
		state.select(TestProxies.proxy(TileItem.class), 10, 0, false);
		org.junit.Assert.assertTrue(state.interacted instanceof TileItem);
		state.select(object, 10, 0, false);
		state.select(TestProxies.proxy(Actor.class), 11, 0, false);
		org.junit.Assert.assertTrue(state.interacted instanceof Actor);
		state.select(object, 10, 0, false);
		state.clearIfMatches(TestProxies.proxy(net.runelite.api.TileObject.class));
		assertSame(object, state.interacted);
		state.clearIfMatches(object);
		assertSame(null, state.interacted);
		state.select(object, 10, 0, false);
		state.clear();
		assertSame(null, state.interacted);
	}

	@Test
	public void unloadingOwningWorldViewClearsTheSelectedObject()
	{
		net.runelite.api.WorldView view = TestProxies.proxy(net.runelite.api.WorldView.class);
		net.runelite.api.TileObject object = TestProxies.proxy(net.runelite.api.TileObject.class,
			TestProxies.method("getWorldView", view));
		BillboardInteractionState state = new BillboardInteractionState();
		state.select(object, 10, 0, false);
		state.unloadWorldView(TestProxies.proxy(net.runelite.api.WorldView.class));
		assertSame(object, state.interacted);
		state.unloadWorldView(view);
		assertSame(null, state.interacted);
	}

}
