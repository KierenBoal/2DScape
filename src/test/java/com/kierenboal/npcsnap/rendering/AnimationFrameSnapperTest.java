package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AnimationFrameSnapperTest
{
	@Test
	public void snapsFramesIntoEvenlySpacedBucketsAndClampsOutOfRangeFrames()
	{
		Animation animation = animation(42, 10);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);

		assertEquals(0, snapper.snapAnimationFrame(42, 0, 3));
		assertEquals(0, snapper.snapAnimationFrame(42, 2, 3));
		assertEquals(3, snapper.snapAnimationFrame(42, 4, 3));
		assertEquals(6, snapper.snapAnimationFrame(42, 9, 3));
		assertEquals(6, snapper.snapAnimationFrame(42, 100, 3));
	}

	@Test
	public void returnsOriginalFrameForInvalidOrUnnecessarySnapping()
	{
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation(7, 4)));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);

		assertEquals(3, snapper.snapAnimationFrame(-1, 3, 2));
		assertEquals(-1, snapper.snapAnimationFrame(7, -1, 2));
		assertEquals(3, snapper.snapAnimationFrame(7, 3, 0));
		assertEquals(3, snapper.snapAnimationFrame(7, 3, 4));
		assertEquals(3, snapper.snapFrame(null, 3, true, 2));
		assertEquals(3, snapper.snapFrame(animation(7, 4), 3, false, 2));
	}

	@Test
	public void cachesFrameCountsUntilCleared()
	{
		AtomicInteger loads = new AtomicInteger();
		Animation animation = animation(5, 8);
		Client client = TestProxies.proxy(Client.class,
			TestProxies.methodSupplier("loadAnimation", () ->
			{
				loads.incrementAndGet();
				return animation;
			}));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);

		snapper.snapAnimationFrame(5, 1, 2);
		snapper.snapAnimationFrame(5, 2, 2);
		assertEquals(1, loads.get());
		snapper.clear();
		snapper.snapAnimationFrame(5, 2, 2);
		assertEquals(2, loads.get());
	}

	@Test
	public void toleratesMissingAnimation()
	{
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(TestProxies.proxy(Client.class));

		assertEquals(4, snapper.snapAnimationFrame(123, 4, 2));
	}

	private static Animation animation(int id, int frameCount)
	{
		return TestProxies.proxy(Animation.class,
			TestProxies.method("getId", id),
			TestProxies.method("getNumFrames", frameCount));
	}
}
