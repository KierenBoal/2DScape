package com.kierenboal.npcsnap;

final class BillboardUpdatePriority
{
	private BillboardUpdatePriority()
	{
	}

	static double stateChangeScore(BillboardUpdateState state, UpdateHeuristicSnapshot snapshot)
	{
		if (state == null || snapshot == null)
		{
			return 0.0d;
		}

		double score = 0.0d;
		if (state.hasModelChanged(snapshot) || state.hasModelChangedSinceRedraw(snapshot))
		{
			score += 25_000.0d;
		}

		if (state.hasAnimationChanged(snapshot) || state.hasAnimationChangedSinceRedraw(snapshot))
		{
			score += 12_000.0d;
		}

		if (state.hasViewChangedSinceRedraw(snapshot))
		{
			score += 6_000.0d;
		}

		if (state.hasMoved(snapshot))
		{
			score += 1_200.0d;
		}
		else if (state.hasTextureChanged(snapshot) || state.hasTextureChangedSinceRedraw(snapshot) || snapshot.animated)
		{
			score += 450.0d;
		}

		return score;
	}
}

