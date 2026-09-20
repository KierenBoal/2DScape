package com.kierenboal.npcsnap.targeting;

import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Actor;

/** Records the order in which the scene actually submits actor models. */
public final class ActorDrawOrderTracker
{
	private final Map<Actor, Long> current = new IdentityHashMap<>();
	private final Map<Actor, Long> previous = new IdentityHashMap<>();
	private long sequence;

	public synchronized void record(Actor actor)
	{
		if (actor != null)
		{
			current.put(actor, ++sequence);
		}
	}

	public synchronized void advanceFrame()
	{
		previous.clear();
		previous.putAll(current);
		current.clear();
		sequence = 0;
	}

	public synchronized boolean drawnAfter(Actor candidate, Actor selected)
	{
		Long candidateOrder = previous.get(candidate);
		Long selectedOrder = previous.get(selected);
		return candidateOrder != null && (selectedOrder == null || candidateOrder > selectedOrder);
	}

	public synchronized Actor preferred(Actor selected, Actor candidate, Actor localPlayer)
	{
		if (selected == null || candidate == localPlayer)
		{
			return candidate;
		}
		if (selected == localPlayer)
		{
			return selected;
		}
		return drawnAfter(candidate, selected) ? candidate : selected;
	}

	public synchronized void clear()
	{
		current.clear();
		previous.clear();
		sequence = 0;
	}
}
