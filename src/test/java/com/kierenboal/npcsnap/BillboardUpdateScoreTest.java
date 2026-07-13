package com.kierenboal.npcsnap;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BillboardUpdateScoreTest
{
	@Test
	public void uncachedAndInteractionTargetsOutrankOrdinaryCachedTargets()
	{
		double cached = score(true, false, false, false, false, false, false, 0);
		double uncached = score(false, false, false, false, false, false, false, 0);
		double interaction = score(true, true, true, false, false, false, false, 0);

		assertTrue(uncached > cached);
		assertTrue(interaction > uncached);
	}

	@Test
	public void priorityActorBeatsLocalPlayerFallbackDuringForcedRedraw()
	{
		double priority = score(true, true, true, false, false, false, false, 0);
		double local = score(true, true, false, true, false, false, false, 0);

		assertTrue(priority > local);
	}

	@Test
	public void ownerAndSpotAnimationBonusesAreAdditive()
	{
		double ordinary = score(true, false, false, false, false, false, false, 0);
		double owned = score(true, false, false, false, true, true, false, 0);
		double ownedSpotAnimation = score(true, false, false, false, true, true, true, 0);

		assertTrue(owned > ordinary);
		assertTrue(ownedSpotAnimation > owned);
	}

	@Test
	public void nearerAndEarlierQueueEntriesWinOtherwiseEqualTies()
	{
		BillboardUpdateState state = new BillboardUpdateState(1d);
		double near = BillboardUpdateScore.compute(true, false, false, false, false, false, false,
			state, snapshot(100d), 10, 0);
		double far = BillboardUpdateScore.compute(true, false, false, false, false, false, false,
			state, snapshot(10_000d), 10, 0);
		double later = BillboardUpdateScore.compute(true, false, false, false, false, false, false,
			state, snapshot(100d), 10, 10);

		assertTrue(near > far);
		assertTrue(near > later);
	}

	@Test
	public void changedModelOutranksUnchangedAnimation()
	{
		BillboardUpdateState unchangedState = new BillboardUpdateState(1d);
		BillboardUpdateState changedState = new BillboardUpdateState(1d);
		UpdateHeuristicSnapshot initial = snapshot(100d);
		unchangedState.observe(initial);
		changedState.observe(initial);

		double unchanged = BillboardUpdateScore.compute(true, false, false, false, false, false, false,
			unchangedState, initial, 10, 0);
		UpdateHeuristicSnapshot changed = new UpdateHeuristicSnapshot(100d, 1, 1, 99, 1, 1, false);
		double modelChanged = BillboardUpdateScore.compute(true, false, false, false, false, false, false,
			changedState, changed, 10, 0);

		assertTrue(modelChanged > unchanged);
	}

	private static double score(
		boolean cached,
		boolean force,
		boolean targetPriority,
		boolean targetLocal,
		boolean ownerLocal,
		boolean ownerPriority,
		boolean spotAnimation,
		int queueIndex)
	{
		return BillboardUpdateScore.compute(
			cached, force, targetPriority, targetLocal, ownerLocal, ownerPriority, spotAnimation,
			new BillboardUpdateState(1d), snapshot(100d), 10, queueIndex);
	}

	private static UpdateHeuristicSnapshot snapshot(double depth)
	{
		return new UpdateHeuristicSnapshot(depth, 1, 1, 1, 1, 1, false);
	}
}
