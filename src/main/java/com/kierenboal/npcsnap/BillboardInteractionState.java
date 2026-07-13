package com.kierenboal.npcsnap;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;

final class BillboardInteractionState
{
	private Actor clickedActor;
	private int clickedActorTick = Integer.MIN_VALUE;
	private Actor frameHoveredActor;
	private Actor frameInteractionActor;
	private Actor lastHoveredActor;
	private Actor lastInteractionActor;

	void noteClick(Actor actor, int tickCount)
	{
		clickedActor = actor;
		clickedActorTick = actor != null ? tickCount : Integer.MIN_VALUE;
	}

	void clearIfStale(Player localPlayer, int tickCount)
	{
		if (clickedActor == null || tickCount <= clickedActorTick)
		{
			return;
		}

		Actor currentTarget = localPlayer != null ? localPlayer.getInteracting() : null;
		if (currentTarget != clickedActor)
		{
			clear();
		}
	}

	void onLocalPlayerInteractionChanged(Actor target, int tickCount)
	{
		if (clickedActor != null && target != clickedActor && tickCount > clickedActorTick)
		{
			clear();
		}
	}

	void clearIfMatches(Actor actor)
	{
		if (actor == clickedActor)
		{
			clear();
		}
	}

	void clear()
	{
		clickedActor = null;
		clickedActorTick = Integer.MIN_VALUE;
		frameInteractionActor = null;
		lastInteractionActor = null;
	}

	Change update(Client client)
	{
		Actor hoveredActor = BillboardHoverInteractionResolver.hoveredActor(client);
		Actor interactionActor = BillboardHoverInteractionResolver.interactedActor(client, clickedActor);
		frameHoveredActor = hoveredActor;
		frameInteractionActor = interactionActor;
		if (hoveredActor == lastHoveredActor && interactionActor == lastInteractionActor)
		{
			return Change.unchanged();
		}

		Change change = new Change(lastHoveredActor, hoveredActor, lastInteractionActor, interactionActor);
		lastHoveredActor = hoveredActor;
		lastInteractionActor = interactionActor;
		return change;
	}

	Actor frameHoveredActor()
	{
		return frameHoveredActor;
	}

	Actor frameInteractionActor()
	{
		return frameInteractionActor;
	}

	Actor priorityActor()
	{
		return frameInteractionActor != null ? frameInteractionActor : frameHoveredActor;
	}

	Actor clickedActor()
	{
		return clickedActor;
	}

	static final class Change
	{
		private static final Change UNCHANGED = new Change(null, null, null, null, false);
		final Actor previousHoveredActor;
		final Actor hoveredActor;
		final Actor previousInteractionActor;
		final Actor interactionActor;
		final boolean changed;

		private Change(Actor previousHoveredActor, Actor hoveredActor, Actor previousInteractionActor, Actor interactionActor)
		{
			this(previousHoveredActor, hoveredActor, previousInteractionActor, interactionActor, true);
		}

		private Change(Actor previousHoveredActor, Actor hoveredActor, Actor previousInteractionActor, Actor interactionActor, boolean changed)
		{
			this.previousHoveredActor = previousHoveredActor;
			this.hoveredActor = hoveredActor;
			this.previousInteractionActor = previousInteractionActor;
			this.interactionActor = interactionActor;
			this.changed = changed;
		}

		private static Change unchanged()
		{
			return UNCHANGED;
		}
	}
}
