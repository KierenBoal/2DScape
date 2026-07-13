package com.kierenboal.npcsnap.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.Test;

public class BillboardPerformanceMetricsTest
{
	@Test
	public void disabledMetricsDoNotRetainFrameData()
	{
		TestClock clock = new TestClock();
		BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::nanos);

		metrics.beginFrame(false);
		metrics.addElapsed("Hidden", 10_000_000L);
		metrics.finishFrame();

		assertTrue(metrics.rows(10).isEmpty());
	}

	@Test
	public void doesNotPublishSnapshotBeforeFiveHundredMilliseconds()
	{
		TestClock clock = new TestClock();
		BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::nanos);

		metrics.beginFrame(true);
		metrics.addElapsed("Stage", 10_000_000L);
		clock.advanceMillis(499);
		metrics.finishFrame();

		assertTrue(metrics.rows(10).isEmpty());
	}

	@Test
	public void publishesMinAvgAndMaxAfterFiveHundredMilliseconds()
	{
		TestClock clock = new TestClock();
		BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::nanos);

		recordFrame(metrics, clock, "Stage", 10, 100);
		recordFrame(metrics, clock, "Stage", 20, 400);

		List<BillboardPerformanceMetrics.MetricRow> rows = metrics.rows(10);
		BillboardPerformanceMetrics.MetricRow stage = rows.get(1);
		assertEquals("Stage", stage.name);
		assertEquals(1, stage.depth);
		assertEquals(10.0d, stage.minMillis, 0.0001d);
		assertEquals(15.0d, stage.avgMillis, 0.0001d);
		assertEquals(20.0d, stage.maxMillis, 0.0001d);
		assertEquals(6.0d, stage.percentOfOverall, 0.0001d);
		assertEquals(100.0d, rows.get(0).percentOfOverall, 0.0001d);
	}

	@Test
	public void rowsUseDepthInsteadOfDashPrefixes()
	{
		TestClock clock = new TestClock();
		BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::nanos);

		metrics.beginFrame(true);
		try (BillboardPerformanceMetrics.Timer ignored = metrics.time("Rasterization"))
		{
			metrics.addElapsed("Painting triangles", 12_000_000L);
		}
		clock.advanceMillis(500);
		metrics.finishFrame();

		List<BillboardPerformanceMetrics.MetricRow> rows = metrics.rows(10);
		assertEquals("Painting triangles", rows.get(2).name);
		assertEquals(2, rows.get(2).depth);
		assertFalse(rows.get(2).name.startsWith("-"));
	}

	@Test
	public void sortsSiblingMetricsByIntervalAverageDescending()
	{
		TestClock clock = new TestClock();
		BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::nanos);

		metrics.beginFrame(true);
		metrics.addElapsed("Fast", 1_000_000L);
		metrics.addElapsed("Slow", 5_000_000L);
		clock.advanceMillis(500);
		metrics.finishFrame();

		List<BillboardPerformanceMetrics.MetricRow> rows = metrics.rows(10);
		assertEquals("Slow", rows.get(1).name);
		assertEquals("Fast", rows.get(2).name);
		assertEquals(0.2d, rows.get(2).percentOfOverall, 0.0001d);
	}

	@Test
	public void colorsAreStableAndBright()
	{
		Color first = BillboardPerformanceMetrics.colorForName("Rasterization");
		Color second = BillboardPerformanceMetrics.colorForName("Rasterization");

		assertEquals(first, second);
		assertBright(first.getRed());
		assertBright(first.getGreen());
		assertBright(first.getBlue());
	}

	private static void recordFrame(BillboardPerformanceMetrics metrics, TestClock clock, String name, int elapsedMillis, int frameMillis)
	{
		metrics.beginFrame(true);
		metrics.addElapsed(name, elapsedMillis * 1_000_000L);
		clock.advanceMillis(frameMillis);
		metrics.finishFrame();
	}

	private static void assertBright(int channel)
	{
		assertTrue(channel >= 140);
		assertTrue(channel <= 255);
	}

	private static final class TestClock
	{
		private long nanos;

		private long nanos()
		{
			return nanos;
		}

		private void advanceMillis(int millis)
		{
			nanos += millis * 1_000_000L;
		}
	}
}
