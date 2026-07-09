package com.kierenboal.npcsnap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

final class BillboardPerformanceMetrics
{
	private static final long SNAPSHOT_INTERVAL_NANOS = 500_000_000L;
	private final MetricNode root = new MetricNode("Overall");
	private final List<MetricNode> stack = new ArrayList<>();
	private final LongSupplier nanoTime;
	private List<MetricRow> publishedRows = Collections.emptyList();
	private boolean enabled;
	private long rootStartNanos = -1L;
	private long intervalStartNanos = -1L;

	BillboardPerformanceMetrics()
	{
		this(System::nanoTime);
	}

	BillboardPerformanceMetrics(LongSupplier nanoTime)
	{
		this.nanoTime = nanoTime;
	}

	void beginFrame(boolean enabled)
	{
		long now = nanoTime.getAsLong();
		if (!enabled)
		{
			this.enabled = false;
			rootStartNanos = -1L;
			intervalStartNanos = -1L;
			publishedRows = Collections.emptyList();
			stack.clear();
			resetCurrent(root);
			resetInterval(root);
			return;
		}

		if (!this.enabled || intervalStartNanos < 0L)
		{
			intervalStartNanos = now;
			resetInterval(root);
			publishedRows = Collections.emptyList();
		}

		this.enabled = true;
		stack.clear();
		resetCurrent(root);
		root.touched = true;
		rootStartNanos = now;
		stack.add(root);
	}

	Timer time(String name)
	{
		if (!enabled || stack.isEmpty())
		{
			return Timer.NOOP;
		}

		MetricNode parent = stack.get(stack.size() - 1);
		MetricNode node = parent.child(name);
		node.touched = true;
		node.startNanos = nanoTime.getAsLong();
		stack.add(node);
		return new Timer(this, node);
	}

	void addElapsed(String name, long elapsedNanos)
	{
		if (!enabled || stack.isEmpty() || elapsedNanos <= 0L)
		{
			return;
		}

		MetricNode parent = stack.get(stack.size() - 1);
		MetricNode node = parent.child(name);
		node.touched = true;
		node.currentNanos += elapsedNanos;
	}

	void finishFrame()
	{
		if (!enabled || rootStartNanos < 0L)
		{
			stack.clear();
			return;
		}

		long now = nanoTime.getAsLong();
		root.currentNanos = Math.max(0L, now - rootStartNanos);
		recordFrame(root);
		if (now - intervalStartNanos >= SNAPSHOT_INTERVAL_NANOS)
		{
			publishedRows = buildRows(Integer.MAX_VALUE);
			resetInterval(root);
			intervalStartNanos = now;
		}
		stack.clear();
	}

	List<MetricRow> rows(int maxRows)
	{
		if (!enabled || maxRows <= 0 || publishedRows.isEmpty())
		{
			return Collections.emptyList();
		}

		if (publishedRows.size() <= maxRows)
		{
			return publishedRows;
		}

		return new ArrayList<>(publishedRows.subList(0, maxRows));
	}

	boolean isEnabled()
	{
		return enabled;
	}

	private void close(Timer timer)
	{
		if (!enabled || timer == null || timer.node == null || timer.closed)
		{
			return;
		}

		timer.closed = true;
		MetricNode node = timer.node;
		node.currentNanos += Math.max(0L, nanoTime.getAsLong() - node.startNanos);
		if (!stack.isEmpty() && stack.get(stack.size() - 1) == node)
		{
			stack.remove(stack.size() - 1);
			return;
		}

		stack.remove(node);
	}

	private void resetCurrent(MetricNode node)
	{
		node.currentNanos = 0L;
		node.startNanos = 0L;
		node.touched = false;
		for (MetricNode child : node.children)
		{
			resetCurrent(child);
		}
	}

	private void resetInterval(MetricNode node)
	{
		node.sampleCount = 0;
		node.sampleTotalNanos = 0L;
		node.sampleMinNanos = Long.MAX_VALUE;
		node.sampleMaxNanos = 0L;
		for (MetricNode child : node.children)
		{
			resetInterval(child);
		}
	}

	private void recordFrame(MetricNode node)
	{
		if (node.touched)
		{
			node.sampleCount++;
			node.sampleTotalNanos += node.currentNanos;
			node.sampleMinNanos = Math.min(node.sampleMinNanos, node.currentNanos);
			node.sampleMaxNanos = Math.max(node.sampleMaxNanos, node.currentNanos);
		}

		for (MetricNode child : node.children)
		{
			recordFrame(child);
		}
	}

	private List<MetricRow> buildRows(int maxRows)
	{
		List<MetricRow> rows = new ArrayList<>();
		appendRows(root, 0, maxRows, rows);
		return rows;
	}

	private void appendRows(MetricNode node, int depth, int maxRows, List<MetricRow> rows)
	{
		if (node.sampleCount <= 0 || rows.size() >= maxRows)
		{
			return;
		}

		rows.add(MetricRow.from(node, depth));
		if (rows.size() >= maxRows)
		{
			return;
		}

		List<MetricNode> children = new ArrayList<>();
		for (MetricNode child : node.children)
		{
			if (child.sampleCount > 0)
			{
				children.add(child);
			}
		}
		children.sort(Comparator
			.comparingDouble(MetricNode::sampleAverageNanos).reversed()
			.thenComparing(MetricNode::name));
		for (MetricNode child : children)
		{
			appendRows(child, depth + 1, maxRows, rows);
			if (rows.size() >= maxRows)
			{
				return;
			}
		}
	}

	static Color colorForName(String name)
	{
		int hash = name != null ? name.hashCode() : 0;
		int red = brightChannel(hash);
		int green = brightChannel(hash >> 8);
		int blue = brightChannel(hash >> 16);
		return new Color(red, green, blue, 255);
	}

	private static int brightChannel(int value)
	{
		return 140 + Math.floorMod(value, 116);
	}

	static final class MetricRow
	{
		final String name;
		final int depth;
		final double minMillis;
		final double avgMillis;
		final double maxMillis;
		final double percentOfOverall;
		final Color color;

		private MetricRow(String name, int depth, double minMillis, double avgMillis, double maxMillis, double percentOfOverall, Color color)
		{
			this.name = name;
			this.depth = depth;
			this.minMillis = minMillis;
			this.avgMillis = avgMillis;
			this.maxMillis = maxMillis;
			this.percentOfOverall = percentOfOverall;
			this.color = color;
		}

		private static MetricRow from(MetricNode node, int depth)
		{
			double overallAverage = node.root().sampleAverageNanos();
			double percentOfOverall = overallAverage > 0.0d ? (node.sampleAverageNanos() * 100.0d) / overallAverage : 0.0d;
			return new MetricRow(
				node.name,
				depth,
				node.sampleMinNanos / 1_000_000.0d,
				node.sampleAverageNanos() / 1_000_000.0d,
				node.sampleMaxNanos / 1_000_000.0d,
				percentOfOverall,
				colorForName(node.name)
			);
		}
	}

	static final class Timer implements AutoCloseable
	{
		private static final Timer NOOP = new Timer(null, null);
		private final BillboardPerformanceMetrics metrics;
		private final MetricNode node;
		private boolean closed;

		private Timer(BillboardPerformanceMetrics metrics, MetricNode node)
		{
			this.metrics = metrics;
			this.node = node;
		}

		@Override
		public void close()
		{
			if (metrics != null)
			{
				metrics.close(this);
			}
		}
	}

	private static final class MetricNode
	{
		private final String name;
		private final MetricNode parent;
		private final List<MetricNode> children = new ArrayList<>();
		private long currentNanos;
		private long startNanos;
		private int sampleCount;
		private long sampleTotalNanos;
		private long sampleMinNanos = Long.MAX_VALUE;
		private long sampleMaxNanos;
		private boolean touched;

		private MetricNode(String name)
		{
			this(name, null);
		}

		private MetricNode(String name, MetricNode parent)
		{
			this.name = name;
			this.parent = parent;
		}

		private MetricNode child(String name)
		{
			for (MetricNode child : children)
			{
				if (child.name.equals(name))
				{
					return child;
				}
			}

			MetricNode child = new MetricNode(name, this);
			children.add(child);
			return child;
		}

		private String name()
		{
			return name;
		}

		private double sampleAverageNanos()
		{
			return sampleCount > 0 ? sampleTotalNanos / (double) sampleCount : 0.0d;
		}

		private MetricNode root()
		{
			MetricNode node = this;
			while (node.parent != null)
			{
				node = node.parent;
			}
			return node;
		}
	}
}
