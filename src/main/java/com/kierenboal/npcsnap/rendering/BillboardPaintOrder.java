package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BillboardPaintOrder
{
	public static final long NO_PRIORITY_GROUP = Long.MIN_VALUE;

	private BillboardPaintOrder()
	{
	}

	public static <T> List<T> sort(List<Entry<T>> entries)
	{
		if (entries.isEmpty())
		{
			return new ArrayList<>();
		}

		int size = entries.size();
		int[] parent = new int[size];
		for (int i = 0; i < size; i++)
		{
			parent[i] = i;
		}

		for (int i = 0; i < size; i++)
		{
			Rectangle left = entries.get(i).bounds;
			if (left == null)
			{
				continue;
			}

			for (int j = i + 1; j < size; j++)
			{
				Rectangle right = entries.get(j).bounds;
				if (right != null && left.intersects(right))
				{
					union(parent, i, j);
				}
			}
		}

		List<Group<T>> groups = new ArrayList<>();
		for (int i = 0; i < size; i++)
		{
			if (find(parent, i) != i)
			{
				continue;
			}

			List<Entry<T>> groupEntries = new ArrayList<>();
			for (int j = 0; j < size; j++)
			{
				if (find(parent, j) == i)
				{
					groupEntries.add(entries.get(j));
				}
			}

			groupEntries.sort(BillboardPaintOrder::compareWithinGroup);
			groups.add(Group.of(groupEntries));
		}

		groups.sort(Comparator
			.comparingDouble(Group<T>::getDepth).reversed()
			.thenComparingInt(Group::getTopY)
			.thenComparingLong(Group::getStableOrder));

		List<T> ordered = new ArrayList<>(size);
		for (Group<T> group : groups)
		{
			for (Entry<T> entry : group.entries)
			{
				ordered.add(entry.value);
			}
		}

		return ordered;
	}

	private static <T> int compareWithinGroup(Entry<T> left, Entry<T> right)
	{
		if (left.priorityGroup != NO_PRIORITY_GROUP && left.priorityGroup == right.priorityGroup)
		{
			int byPriority = Integer.compare(left.renderPriority, right.renderPriority);
			if (byPriority != 0)
			{
				return byPriority;
			}
		}

		if (left.depthFirst || right.depthFirst)
		{
			int byDepth = Double.compare(right.depth, left.depth);
			if (byDepth != 0)
			{
				return byDepth;
			}
		}

		int byBottom = Integer.compare(left.screenBottomY, right.screenBottomY);
		if (byBottom != 0)
		{
			return byBottom;
		}

		int byDepth = Double.compare(right.depth, left.depth);
		if (byDepth != 0)
		{
			return byDepth;
		}

		return Long.compare(left.stableOrder, right.stableOrder);
	}

	private static int find(int[] parent, int index)
	{
		int root = index;
		while (parent[root] != root)
		{
			root = parent[root];
		}

		while (parent[index] != index)
		{
			int next = parent[index];
			parent[index] = root;
			index = next;
		}

		return root;
	}

	private static void union(int[] parent, int left, int right)
	{
		int leftRoot = find(parent, left);
		int rightRoot = find(parent, right);
		if (leftRoot != rightRoot)
		{
			parent[rightRoot] = leftRoot;
		}
	}

	public static final class Entry<T>
	{
		private final T value;
		private final Rectangle bounds;
		private final int renderPriority;
		private final long priorityGroup;
		private final int screenBottomY;
		private final double depth;
		private final boolean depthFirst;
		private final long stableOrder;

		public Entry(T value, Rectangle bounds, int renderPriority, long priorityGroup, int screenBottomY, double depth, long stableOrder)
		{
			this(value, bounds, renderPriority, priorityGroup, screenBottomY, depth, false, stableOrder);
		}

		public Entry(T value, Rectangle bounds, int renderPriority, long priorityGroup, int screenBottomY, double depth, boolean depthFirst, long stableOrder)
		{
			this.value = value;
			this.bounds = bounds;
			this.renderPriority = renderPriority;
			this.priorityGroup = priorityGroup;
			this.screenBottomY = screenBottomY;
			this.depth = depth;
			this.depthFirst = depthFirst;
			this.stableOrder = stableOrder;
		}
	}

	private static final class Group<T>
	{
		private final List<Entry<T>> entries;
		private final double depth;
		private final int topY;
		private final long stableOrder;

		private Group(List<Entry<T>> entries, double depth, int topY, long stableOrder)
		{
			this.entries = entries;
			this.depth = depth;
			this.topY = topY;
			this.stableOrder = stableOrder;
		}

		private static <T> Group<T> of(List<Entry<T>> entries)
		{
			double maxDepth = Double.NEGATIVE_INFINITY;
			int minTopY = Integer.MAX_VALUE;
			long minStableOrder = Long.MAX_VALUE;
			for (Entry<T> entry : entries)
			{
				maxDepth = Math.max(maxDepth, entry.depth);
				minTopY = Math.min(minTopY, entry.bounds != null ? entry.bounds.y : entry.screenBottomY);
				minStableOrder = Math.min(minStableOrder, entry.stableOrder);
			}

			return new Group<>(entries, maxDepth, minTopY, minStableOrder);
		}

		private double getDepth()
		{
			return depth;
		}

		private int getTopY()
		{
			return topY;
		}

		private long getStableOrder()
		{
			return stableOrder;
		}
	}
}
