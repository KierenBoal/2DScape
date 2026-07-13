package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;

public final class BillboardInteractionState
{
	private Actor clickedActor;
	private int clickedActorTick = Integer.MIN_VALUE;
	private Actor frameHoveredActor;
	private Actor frameInteractionActor;
	private Actor lastHoveredActor;
	private Actor lastInteractionActor;

	public void noteClick(Actor actor, int tickCount)
	{
		clickedActor = actor;
		clickedActorTick = actor != null ? tickCount : Integer.MIN_VALUE;
	}

	public void clearIfStale(Player localPlayer, int tickCount)
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

	public void onLocalPlayerInteractionChanged(Actor target, int tickCount)
	{
		if (clickedActor != null && target != clickedActor && tickCount > clickedActorTick)
		{
			clear();
		}
	}

	public void clearIfMatches(Actor actor)
	{
		if (actor == clickedActor)
		{
			clear();
		}
	}

	public void clear()
	{
		clickedActor = null;
		clickedActorTick = Integer.MIN_VALUE;
		frameInteractionActor = null;
		lastInteractionActor = null;
	}

	public Change update(Client client)
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

	public Actor frameHoveredActor()
	{
		return frameHoveredActor;
	}

	public Actor frameInteractionActor()
	{
		return frameInteractionActor;
	}

	public Actor priorityActor()
	{
		return frameInteractionActor != null ? frameInteractionActor : frameHoveredActor;
	}

	public Actor clickedActor()
	{
		return clickedActor;
	}

	public static final class Change
	{
		private static final Change UNCHANGED = new Change(null, null, null, null, false);
		public final Actor previousHoveredActor;
		public final Actor hoveredActor;
		public final Actor previousInteractionActor;
		public final Actor interactionActor;
		public final boolean changed;

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
