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
	public void wrapsFullAnimationsWhenDeterministicLoopingIsDisabled()
	{
		Animation animation = animation(42, 10);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);

		assertEquals(1, snapper.snapAnimationFrame(42, 41, 10, false));
	}

	@Test
	public void followsPartialAnimationLoopMetadata()
	{
		Animation animation = animation(42, 17, 12);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);

		assertEquals(5, snapper.snapAnimationFrame(42, 17, 17, false));
		assertEquals(6, snapper.snapAnimationFrame(42, 18, 17, false));
	}

	@Test
	public void preservesPartialLoopAfterAnActorFrameResets()
	{
		Animation animation = animation(42, 36, 24);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);

		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 35, 36, false, false));
		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 0, 36, false, false));
		assertEquals(13, snapper.snapActorAnimationFrame(actor, 42, 1, 36, false, false));
		assertEquals(24, snapper.snapActorAnimationFrame(actor, 42, 12, 36, false, false));
		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 23, 36, false, false));
		assertEquals(12, snapper.snapActorAnimationFrame(actor, 42, 24, 36, false, false));
	}

	@Test
	public void keepsTheFrameRateCadenceAcrossNativePartialLoops()
	{
		Animation animation = animation(42, 37, 31);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);

		assertEquals(0, snapper.snapActorAnimationFrame(actor, 42, 0, 3, false, false));
		assertEquals(16, snapper.snapActorAnimationFrame(actor, 42, 16, 3, false, false));
		assertEquals(32, snapper.snapActorAnimationFrame(actor, 42, 32, 3, false, false));
		assertEquals(32, snapper.snapActorAnimationFrame(actor, 42, 6, 3, false, false));
		assertEquals(18, snapper.snapActorAnimationFrame(actor, 42, 18, 3, false, false));
		assertEquals(34, snapper.snapActorAnimationFrame(actor, 42, 34, 3, false, false));
	}

	@Test
	public void doesNotApplyPreviousLoopOffsetWhenTheSameAnimationRestarts()
	{
		Animation animation = animation(42, 36, 24);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);

		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 35, 36, false, false));
		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 0, 36, false, false));
		assertEquals(-1, snapper.snapActorAnimationFrame(actor, -1, -1, 36, false, false));
		assertEquals(0, snapper.snapActorAnimationFrame(actor, 42, 0, 36, false, false));
	}

	@Test
	public void leavesNonLoopingAnimationsOnTheirFinalFrame()
	{
		Animation animation = animation(42, 17, -1);
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(client);

		assertEquals(16, snapper.snapAnimationFrame(42, 41, 17, false));
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

	@Test
	public void successfullyCapturedLivePoseDoesNotRewindToAnOlderBucket()
	{
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(TestProxies.proxy(Client.class,
			TestProxies.method("loadAnimation", animation(42, 10))));
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);
		assertEquals(3, snapper.snapActorAnimationFrame(actor, 42, 5, 3, true, false));
		snapper.commitActorLivePose(actor, 42, 5, -1, -1);
		assertEquals(5, snapper.snapActorAnimationFrame(actor, 42, 5, 3, true, false));
		assertEquals(5, snapper.snapActorAnimationFrame(actor, 42, 6, 3, true, false));
		assertEquals(6, snapper.snapActorAnimationFrame(actor, 42, 7, 3, true, false));
	}

	@Test
	public void actionAndPoseFloorsAreIndependentAndRealLoopsCanRestart()
	{
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(TestProxies.proxy(Client.class,
			TestProxies.method("loadAnimation", animation(42, 10))));
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);
		snapper.snapActorAnimationFrame(actor, 42, 5, 3, true, false);
		snapper.snapActorAnimationFrame(actor, 43, 9, 3, true, true);
		snapper.commitActorLivePose(actor, 42, 5, 43, 9);
		assertEquals(5, snapper.snapActorAnimationFrame(actor, 42, 5, 3, true, false));
		assertEquals(0, snapper.snapActorAnimationFrame(actor, 43, 0, 3, true, true));
		assertEquals(5, snapper.snapActorAnimationFrame(actor, 42, 6, 3, true, false));
		assertEquals(0, snapper.snapActorAnimationFrame(actor, 43, 1, 3, true, true));
	}

	@Test
	public void partialLoopCadenceHoldsTheCapturedPoseUntilTheScheduleCatchesUp()
	{
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(TestProxies.proxy(Client.class,
			TestProxies.method("loadAnimation", animation(42, 36, 24))));
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);
		assertEquals(16, snapper.snapActorAnimationFrame(actor, 42, 17, 3, false, false));
		snapper.commitActorLivePose(actor, 42, 17, -1, -1);
		assertEquals(17, snapper.snapActorAnimationFrame(actor, 42, 18, 3, false, false));
		assertEquals(32, snapper.snapActorAnimationFrame(actor, 42, 32, 3, false, false));
		snapper.snapActorAnimationFrame(actor, 42, 35, 3, false, false);
		snapper.commitActorLivePose(actor, 42, 35, -1, -1);
		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 0, 3, false, false));
		assertEquals(35, snapper.snapActorAnimationFrame(actor, 42, 1, 3, false, false));
		assertEquals(25, snapper.snapActorAnimationFrame(actor, 42, 13, 3, false, false));
	}

	@Test
	public void partialLoopCaptureUsesTheActualPoseEvenWhenItsRawFrameDiffersFromTheAccumulatedTimeline()
	{
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(TestProxies.proxy(Client.class,
			TestProxies.method("loadAnimation", animation(42, 36, 24))));
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);
		snapper.snapActorAnimationFrame(actor, 42, 35, 3, false, false);
		snapper.snapActorAnimationFrame(actor, 42, 0, 3, false, false);
		snapper.commitActorLivePose(actor, 42, 0, -1, -1);
		assertEquals(0, snapper.snapActorAnimationFrame(actor, 42, 1, 3, false, false));
		assertEquals(25, snapper.snapActorAnimationFrame(actor, 42, 13, 3, false, false));
	}

	@Test
	public void animationChangesResetsAndCacheClearDoNotKeepThePreviousPoseFloor()
	{
		AnimationFrameSnapper snapper = new AnimationFrameSnapper(TestProxies.proxy(Client.class,
			TestProxies.method("loadAnimation", animation(42, 10))));
		net.runelite.api.Actor actor = TestProxies.proxy(net.runelite.api.Actor.class);
		snapper.snapActorAnimationFrame(actor, 42, 5, 3, true, false);
		snapper.commitActorLivePose(actor, 42, 5, -1, -1);
		assertEquals(3, snapper.snapActorAnimationFrame(actor, 43, 5, 3, true, false));
		snapper.commitActorLivePose(actor, 43, 5, -1, -1);
		assertEquals(-1, snapper.snapActorAnimationFrame(actor, -1, -1, 3, true, false));
		assertEquals(3, snapper.snapActorAnimationFrame(actor, 43, 5, 3, true, false));
		snapper.commitActorLivePose(actor, 43, 5, -1, -1);
		snapper.clear();
		assertEquals(3, snapper.snapActorAnimationFrame(actor, 43, 5, 3, true, false));
	}

	private static Animation animation(int id, int frameCount)
	{
		return animation(id, frameCount, 0);
	}

	private static Animation animation(int id, int frameCount, int frameStep)
	{
		return TestProxies.proxy(Animation.class,
			TestProxies.method("getId", id),
			TestProxies.method("getNumFrames", frameCount),
			TestProxies.method("getFrameStep", frameStep));
	}
}
