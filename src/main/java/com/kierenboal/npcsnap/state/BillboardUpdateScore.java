package com.kierenboal.npcsnap.state;

public final class BillboardUpdateScore
{
	private BillboardUpdateScore()
	{
	}

	public static double compute(
		boolean hasCachedBillboard,
		boolean forceInteractionRedraw,
		boolean targetIsPriorityActor,
		boolean targetIsLocalPlayer,
		boolean ownerIsLocalPlayer,
		boolean ownerIsPriorityActor,
		boolean actorSpotAnimation,
		BillboardUpdateState state,
		UpdateHeuristicSnapshot snapshot,
		int gameCycle,
		int queueIndex)
	{
		double score = hasCachedBillboard ? 0.0d : 10_000.0d;
		if (forceInteractionRedraw)
		{
			score += 50_000.0d;
			if (targetIsPriorityActor)
			{
				score += 10_000.0d;
			}
			else if (targetIsLocalPlayer)
			{
				score += 9_000.0d;
			}
		}

		if (ownerIsLocalPlayer)
		{
			score += 6_000.0d;
		}
		if (ownerIsPriorityActor)
		{
			score += 8_000.0d;
		}
		if (actorSpotAnimation)
		{
			score += 2_500.0d;
		}

		score += BillboardUpdatePriority.stateChangeScore(state, snapshot);
		double depth = Math.max(0.0d, snapshot.depth);
		score += 8_000.0d / Math.max(128.0d, depth + 128.0d);
		score += Math.min(4_000.0d, state.cyclesSinceRedraw(gameCycle) * 160.0d);
		score += Math.min(3_000.0d, state.overdueCycles(gameCycle) * 220.0d);
		return score - (queueIndex * 0.01d);
	}
}
