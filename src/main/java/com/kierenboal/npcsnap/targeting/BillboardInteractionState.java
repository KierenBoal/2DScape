package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import com.kierenboal.npcsnap.features.GroundItemBillboard;
import java.util.Map;

public final class BillboardInteractionState
{
	private Actor clickedActor;
	private TileItem clickedGroundItem;
	private int clickedActorTick = Integer.MIN_VALUE;
	private Renderable frameHoveredTarget;
	private Renderable frameInteractionTarget;
	private Renderable lastHoveredTarget;
	private Renderable lastInteractionTarget;

	public void noteClick(Actor actor, int tickCount)
	{
		clickedGroundItem = null;
		clickedActor = actor;
		clickedActorTick = actor != null ? tickCount : Integer.MIN_VALUE;
	}

	public void noteGroundItemClick(TileItem item)
	{
		clickedActor = null;
		clickedActorTick = Integer.MIN_VALUE;
		clickedGroundItem = item;
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
		clickedGroundItem = null;
		clickedActorTick = Integer.MIN_VALUE;
		frameInteractionTarget = null;
		lastInteractionTarget = null;
	}

	public Change update(Client client, Iterable<Map.Entry<TileItem, GroundItemBillboard>> groundItems)
	{
		Renderable hoveredTarget = BillboardHoverInteractionResolver.hoveredTarget(client, groundItems);
		Renderable interactionTarget = clickedGroundItem != null
			? clickedGroundItem
			: BillboardHoverInteractionResolver.interactedActor(client, clickedActor);
		frameHoveredTarget = hoveredTarget;
		frameInteractionTarget = interactionTarget;
		if (hoveredTarget == lastHoveredTarget && interactionTarget == lastInteractionTarget)
		{
			return Change.unchanged();
		}

		Change change = new Change(lastHoveredTarget, hoveredTarget, lastInteractionTarget, interactionTarget);
		lastHoveredTarget = hoveredTarget;
		lastInteractionTarget = interactionTarget;
		return change;
	}

	public Renderable frameHoveredTarget()
	{
		return frameHoveredTarget;
	}

	public Renderable frameInteractionTarget()
	{
		return frameInteractionTarget;
	}

	public Renderable priorityTarget()
	{
		return frameInteractionTarget != null ? frameInteractionTarget : frameHoveredTarget;
	}

	public Actor clickedActor()
	{
		return clickedActor;
	}

	public TileItem clickedGroundItem()
	{
		return clickedGroundItem;
	}

	public void clearIfMatches(TileItem item)
	{
		if (item == clickedGroundItem)
		{
			clear();
		}
	}

	public static final class Change
	{
		private static final Change UNCHANGED = new Change(null, null, null, null, false);
		public final Renderable previousHoveredTarget;
		public final Renderable hoveredTarget;
		public final Renderable previousInteractionTarget;
		public final Renderable interactionTarget;
		public final boolean changed;

		private Change(Renderable previousHoveredTarget, Renderable hoveredTarget, Renderable previousInteractionTarget, Renderable interactionTarget)
		{
			this(previousHoveredTarget, hoveredTarget, previousInteractionTarget, interactionTarget, true);
		}

		private Change(Renderable previousHoveredTarget, Renderable hoveredTarget, Renderable previousInteractionTarget, Renderable interactionTarget, boolean changed)
		{
			this.previousHoveredTarget = previousHoveredTarget;
			this.hoveredTarget = hoveredTarget;
			this.previousInteractionTarget = previousInteractionTarget;
			this.interactionTarget = interactionTarget;
			this.changed = changed;
		}

		private static Change unchanged()
		{
			return UNCHANGED;
		}
	}
}
