package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardModelBounds;
import net.runelite.api.Renderable;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class BillboardCaptureStateTest
{
	@Test
	public void missedDeadlinesSkipCatchupBurstsAndKeepTheFractionalPhase()
	{
		BillboardUpdateState state = new BillboardUpdateState(1);
		state.advance(0, 1, 1, snapshot(1, 1, 100), 12.5);
		state.advance(40, 1, 1, snapshot(1, 2, 100), 12.5);
		assertFalse(state.isReady(40, true));
		assertFalse(state.isReady(49, true));
		assertTrue(state.isReady(50, true));
	}

	@Test
	public void forcedCaptureRestartsTheFractionalDeadlineAndCommitsOnlyOnce()
	{
		BillboardUpdateState state = new BillboardUpdateState(1);
		state.advance(0, 1, 1, snapshot(1, 1, 100), 12.5);
		FrameUpdatePlan forced = new FrameUpdatePlan(1, false, true, state, snapshot(1, 2, 160), 3, 1, 1, 12.5);
		forced.markRedrawSucceeded();
		assertFalse(state.isReady(15, true));
		assertTrue(state.isReady(16, true));
		forced.markRedrawSucceeded();
		assertTrue(state.isReady(16, true));
	}

	@Test
	public void multipartFailureAndRepeatedPartSuccessCannotCommitTheGroup()
	{
		BillboardUpdateState state = new BillboardUpdateState(1);
		FrameUpdatePlan plan = new FrameUpdatePlan(1, false, state, snapshot(1, 1, 100), 0, 1, 1, 25);
		Renderable first = proxy(Renderable.class), second = proxy(Renderable.class);
		plan.expectParts(2);
		plan.partSucceeded(first);
		plan.partSucceeded(first);
		assertFalse(plan.isCompleted());
		assertTrue(state.isReady(1, true));
		plan.partSucceeded(second);
		assertTrue(plan.isCompleted());
		assertFalse(state.isReady(1, true));
	}

	@Test
	public void resizeUsesStrictThresholdsAndAlwaysComparesWithTheDisplayedCapture()
	{
		BillboardUpdateState state = new BillboardUpdateState(1);
		state.advance(0, 1, 1, snapshot(1, 1, 100), 25);
		assertFalse(state.hasCaptureException(snapshot(1, 2, 150), 50));
		assertTrue(state.hasCaptureException(snapshot(1, 2, 151), 50));
		assertFalse(state.hasCaptureException(snapshot(1, 2, 200), 0));
		state.observe(snapshot(1, 2, 151));
		assertTrue("Observing a failed or deferred capture must not commit its baseline",
			state.hasCaptureException(snapshot(1, 3, 151), 50));
		assertTrue(state.hasCaptureException(snapshot(2, 3, 100), 0));
	}

	@Test
	public void requestedRatesAboveGameCycleAvailabilityNeverCaptureTwiceInACycle()
	{
		BillboardUpdateState state = new BillboardUpdateState(1);
		state.advance(10, 1, 1, snapshot(1, 1, 100), 50d / 120);
		assertFalse(state.isReady(10, true));
		assertTrue(state.isReady(11, true));
	}

	private static UpdateHeuristicSnapshot snapshot(int identity, int frame, int width)
	{
		return new UpdateHeuristicSnapshot(100, 10, frame, frame, frame, 0, true,
			identity, 7, new BillboardModelBounds(width, 100));
	}
}
