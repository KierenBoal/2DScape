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
		return compute(hasCachedBillboard, forceInteractionRedraw, false, targetIsPriorityActor,
			targetIsLocalPlayer, ownerIsLocalPlayer, ownerIsPriorityActor, actorSpotAnimation,
			state, snapshot, gameCycle, queueIndex);
	}

	public static double compute(
		boolean hasCachedBillboard,
		boolean forceInteractionRedraw,
		boolean forceActorResizeRedraw,
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
		// Separate priority bands keep resize updates above ordinary state changes,
		// and interaction updates above resize updates even when both models change.
		double score = hasCachedBillboard ? 0.0d : 10_000.0d;
		score += forceInteractionRedraw ? 1_000_000.0d : forceActorResizeRedraw ? 100_000.0d : 0.0d;
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
