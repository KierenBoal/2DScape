package com.kierenboal.npcsnap.state;

import java.util.concurrent.CompletableFuture;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FrameRenderableTrackerTest
{
	@Test
	public void concurrentRecordingAndFrameRolloverRemainSafe()
	{
		FrameRenderableTracker<EqualValue> tracker = new FrameRenderableTracker<>();
		EqualValue first = new EqualValue(1);
		EqualValue second = new EqualValue(1);

		CompletableFuture.allOf(
			CompletableFuture.runAsync(() ->
			{
				for (int i = 0; i < 1000; i++)
				{
					tracker.record(first);
				}
			}),
			CompletableFuture.runAsync(() ->
			{
				for (int i = 0; i < 1000; i++)
				{
					tracker.record(second);
				}
			}),
			CompletableFuture.runAsync(() ->
			{
				for (int i = 0; i < 100; i++)
				{
					tracker.advanceFrame();
				}
			})
		).join();

		EqualValue marker = new EqualValue(2);
		tracker.record(marker);
		tracker.advanceFrame();

		assertTrue(tracker.containsPrevious(marker));
		assertFalse(tracker.containsPrevious(new EqualValue(2)));
		assertFalse(tracker.containsCurrent(marker));
	}

	private static final class EqualValue
	{
		private final int value;

		private EqualValue(int value)
		{
			this.value = value;
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof EqualValue && value == ((EqualValue) other).value;
		}

		@Override
		public int hashCode()
		{
			return value;
		}
	}
}
