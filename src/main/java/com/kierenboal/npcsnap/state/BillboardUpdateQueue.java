package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.targeting.BillboardTargetKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BillboardUpdateQueue implements Iterable<BillboardTargetKey>
{
	private final Deque<BillboardTargetKey> entries = new ArrayDeque<>();
	private final Set<BillboardTargetKey> membership = new HashSet<>();
	private final Map<BillboardTargetKey, Integer> debugPositions = new HashMap<>();
	private final Map<BillboardTargetKey, Double> debugScores = new HashMap<>();

	public void clear()
	{
		entries.clear();
		membership.clear();
		debugPositions.clear();
		debugScores.clear();
	}

	public int size()
	{
		return entries.size();
	}

	public boolean isEmpty()
	{
		return entries.isEmpty();
	}

	public boolean contains(BillboardTargetKey key)
	{
		return membership.contains(key);
	}

	public BillboardTargetKey pollFirst()
	{
		BillboardTargetKey key = entries.pollFirst();
		if (key != null)
		{
			membership.remove(key);
		}
		return key;
	}

	public void addLast(BillboardTargetKey key)
	{
		if (membership.add(key))
		{
			entries.addLast(key);
		}
	}

	public void addFirst(BillboardTargetKey key)
	{
		if (membership.add(key))
		{
			entries.addFirst(key);
		}
	}

	public List<BillboardTargetKey> prune(Set<BillboardTargetKey> validKeys)
	{
		List<BillboardTargetKey> removed = new ArrayList<>();
		Iterator<BillboardTargetKey> iterator = entries.iterator();
		while (iterator.hasNext())
		{
			BillboardTargetKey key = iterator.next();
			if (!validKeys.contains(key))
			{
				iterator.remove();
				membership.remove(key);
				debugPositions.remove(key);
				debugScores.remove(key);
				removed.add(key);
			}
		}
		return removed;
	}

	public void replaceWith(List<QueuedBillboardTarget> prioritizedTargets)
	{
		entries.clear();
		membership.clear();
		for (QueuedBillboardTarget target : prioritizedTargets)
		{
			addLast(target.key);
		}
		refreshDebugPositions();
	}

	public int debugPosition(BillboardTargetKey key)
	{
		return debugPositions.getOrDefault(key, -1);
	}

	public void putDebugScore(BillboardTargetKey key, double score)
	{
		debugScores.put(key, score);
	}

	public Double debugScore(BillboardTargetKey key)
	{
		return debugScores.get(key);
	}

	public Collection<Double> debugScores()
	{
		return debugScores.values();
	}

	public boolean hasDebugScores()
	{
		return !debugScores.isEmpty();
	}

	private void refreshDebugPositions()
	{
		debugPositions.clear();
		int index = 0;
		for (BillboardTargetKey key : entries)
		{
			debugPositions.put(key, index++);
		}
	}

	@Override
	public Iterator<BillboardTargetKey> iterator()
	{
		return entries.iterator();
	}
}
