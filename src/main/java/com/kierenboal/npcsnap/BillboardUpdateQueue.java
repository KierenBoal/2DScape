package com.kierenboal.npcsnap;

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

final class BillboardUpdateQueue implements Iterable<BillboardTargetKey>
{
	private final Deque<BillboardTargetKey> entries = new ArrayDeque<>();
	private final Set<BillboardTargetKey> membership = new HashSet<>();
	private final Map<BillboardTargetKey, Integer> debugPositions = new HashMap<>();
	private final Map<BillboardTargetKey, Double> debugScores = new HashMap<>();

	void clear()
	{
		entries.clear();
		membership.clear();
		debugPositions.clear();
		debugScores.clear();
	}

	int size()
	{
		return entries.size();
	}

	boolean isEmpty()
	{
		return entries.isEmpty();
	}

	boolean contains(BillboardTargetKey key)
	{
		return membership.contains(key);
	}

	BillboardTargetKey pollFirst()
	{
		BillboardTargetKey key = entries.pollFirst();
		if (key != null)
		{
			membership.remove(key);
		}
		return key;
	}

	void addLast(BillboardTargetKey key)
	{
		if (membership.add(key))
		{
			entries.addLast(key);
		}
	}

	void addFirst(BillboardTargetKey key)
	{
		if (membership.add(key))
		{
			entries.addFirst(key);
		}
	}

	List<BillboardTargetKey> prune(Set<BillboardTargetKey> validKeys)
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

	void replaceWith(List<QueuedBillboardTarget> prioritizedTargets)
	{
		entries.clear();
		membership.clear();
		for (QueuedBillboardTarget target : prioritizedTargets)
		{
			addLast(target.key);
		}
		refreshDebugPositions();
	}

	int debugPosition(BillboardTargetKey key)
	{
		return debugPositions.getOrDefault(key, -1);
	}

	void putDebugScore(BillboardTargetKey key, double score)
	{
		debugScores.put(key, score);
	}

	Double debugScore(BillboardTargetKey key)
	{
		return debugScores.get(key);
	}

	Collection<Double> debugScores()
	{
		return debugScores.values();
	}

	boolean hasDebugScores()
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
